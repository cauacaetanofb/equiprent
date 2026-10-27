$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

Push-Location -LiteralPath $projectRoot
try {
    # Importa a configuração atual antes de iniciar o processo Java.
    & (Join-Path $PSScriptRoot 'Import-LocalEnv.ps1')
    & (Join-Path $projectRoot 'mvnw.cmd') spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw 'A aplicacao nao iniciou ou terminou com erro. Consulte a causa indicada no log acima.'
    }
}
finally {
    Pop-Location
}
