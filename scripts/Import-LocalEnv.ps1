param(
    [string]$Path = (Join-Path $PSScriptRoot '..\.env')
)

$ErrorActionPreference = 'Stop'
$allowedKeys = @('DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USERNAME', 'DB_PASSWORD', 'SERVER_PORT')
$values = @{}

foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) {
        continue
    }
    if ($line -notmatch '^([A-Z_]+)=(.*)$' -or $allowedKeys -notcontains $Matches[1]) {
        throw 'Formato invalido: use somente as chaves de .env.example, no formato CHAVE=valor.'
    }
    $values[$Matches[1]] = $Matches[2]
}

foreach ($key in $allowedKeys) {
    if (-not $values.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($values[$key])) {
        throw "Preencha $key no arquivo de ambiente."
    }
}

foreach ($key in $allowedKeys) {
    [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process')
}

Write-Output 'Variaveis carregadas nesta sessao do PowerShell. Nenhum valor foi exibido.'
