param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Z0-9][A-Z0-9_-]{0,49}$')][string]$Code,
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$AdminName,
    [Parameter(Mandatory = $true)][string]$AdminEmail,
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env')
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$taskKeys = @('COMPANY_CODE', 'COMPANY_NAME', 'COMPANY_ADMIN_NAME', 'COMPANY_ADMIN_EMAIL', 'COMPANY_ADMIN_PASSWORD')
$envKeys = $taskKeys + @('DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USERNAME', 'DB_PASSWORD', 'SERVER_PORT',
    'BOOTSTRAP_ADMIN_ENABLED', 'BOOTSTRAP_ADMIN_NAME', 'BOOTSTRAP_ADMIN_EMAIL', 'BOOTSTRAP_ADMIN_PASSWORD', 'INITIAL_COMPANY_NAME')
$previous = @{}
foreach ($key in $envKeys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
$secretPointer = [IntPtr]::Zero
Push-Location $projectRoot
try {
    & (Join-Path $PSScriptRoot 'Import-LocalEnv.ps1') -Path $EnvFile
    $password = Read-Host 'Senha do novo administrador (12 a 64 caracteres)' -AsSecureString
    $secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($password)
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)
    if ($plainPassword.Length -lt 12 -or $plainPassword.Length -gt 64) { throw 'A senha deve ter de 12 a 64 caracteres.' }
    $values = @($Code, $Name, $AdminName, $AdminEmail, $plainPassword)
    for ($index = 0; $index -lt $taskKeys.Count; $index++) {
        [Environment]::SetEnvironmentVariable($taskKeys[$index], $values[$index], 'Process')
    }
    $ErrorActionPreference = 'Continue'
    & .\mvnw.cmd '-Dspring-boot.run.arguments=--spring.profiles.active=default --spring.main.web-application-type=none --equiprent.bootstrap.enabled=false --equiprent.company.registration.enabled=true' spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw 'Cadastro nao concluido. Consulte o erro acima; nenhuma senha foi exibida pelo script.' }
    Write-Output 'Empresa e administrador cadastrados. Use o login informado na aplicacao normal.'
} finally {
    if ($secretPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer) }
    $plainPassword = $null
    $values = $null
    if ($password) { $password.Dispose() }
    foreach ($key in $envKeys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    Pop-Location
}
