param([switch]$PrepareOnly)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$demoEnv = Join-Path $projectRoot '.local/demo.env'
$keys = @('DEMO_DB_PASSWORD', 'DEMO_ACCOUNT_PASSWORD', 'DEMO_DB_PORT', 'DEMO_SERVER_PORT')
$previous = @{}

Push-Location -LiteralPath $projectRoot
try {
    if (-not (Test-Path -LiteralPath $demoEnv)) {
        New-Item -ItemType Directory -Path (Join-Path $projectRoot '.local') -Force | Out-Null
        $content = @(
            'DEMO_DB_PASSWORD=' + [Guid]::NewGuid().ToString('N')
            'DEMO_ACCOUNT_PASSWORD=Demo-' + [Guid]::NewGuid().ToString('N')
            'DEMO_DB_PORT=5440'
            'DEMO_SERVER_PORT=8081'
        )
        [IO.File]::WriteAllLines($demoEnv, $content, (New-Object System.Text.UTF8Encoding($false)))
    }
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $demoEnv -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }
        if ($line -notmatch '^([A-Z_]+)=([A-Za-z0-9_-]+)$' -or $keys -notcontains $Matches[1]) {
            throw 'Formato invalido em .local/demo.env. Use as quatro chaves geradas, sem aspas ou espacos.'
        }
        if ($values.ContainsKey($Matches[1])) { throw 'Chave duplicada em .local/demo.env.' }
        $values[$Matches[1]] = $Matches[2]
    }
    foreach ($key in $keys) {
        if (-not $values.ContainsKey($key)) { throw "Preencha $key em .local/demo.env." }
    }
    foreach ($key in @('DEMO_DB_PORT', 'DEMO_SERVER_PORT')) {
        $portNumber = 0
        if (-not [int]::TryParse($values[$key], [ref]$portNumber) -or $portNumber -lt 1024 -or $portNumber -gt 65535) {
            throw "Porta invalida em $key. Use de 1024 a 65535."
        }
    }
    if ($values.DEMO_ACCOUNT_PASSWORD.Length -lt 12 -or $values.DEMO_ACCOUNT_PASSWORD.Length -gt 64) {
        throw 'DEMO_ACCOUNT_PASSWORD deve ter de 12 a 64 caracteres.'
    }
    foreach ($key in $keys) {
        $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process')
    }
    # O ambiente normal e seu .env nao sao importados. Projeto/volume exclusivos da demo.
    & docker compose -p equiprent-demo -f compose.demo.yaml --env-file $demoEnv up -d --wait postgres
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao iniciar o PostgreSQL da demonstracao.' }
    Write-Output "Demo: http://localhost:$($values.DEMO_SERVER_PORT)"
    Write-Output 'Contas: admin@equiprent.example, marina@equiprent.example e rafael@equiprent.example'
    Write-Output 'Senha das contas ficticias: variavel DEMO_ACCOUNT_PASSWORD em .local/demo.env.'
    if (-not $PrepareOnly) {
        # PowerShell 5 trata stderr nativo como erro; avisos do Maven nao devem interromper o build.
        $ErrorActionPreference = 'Continue'
        & (Join-Path $projectRoot 'mvnw.cmd') '-Dspring-boot.run.profiles=demo' spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw 'A demonstracao terminou com erro. Consulte o log acima.' }
    }
}
finally {
    foreach ($key in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process')
    }
    Pop-Location
}
