param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    if (!(Test-Path -LiteralPath '.env')) {
        throw '.env bulunamadı. Önce Copy-Item .env.example .env komutunu çalıştırın.'
    }
    foreach ($configLine in Get-Content -LiteralPath '.env') {
        $trimmedLine = $configLine.Trim()
        if ($trimmedLine -and !$trimmedLine.StartsWith('#')) {
            $pair = $trimmedLine.Split('=', 2)
            if ($pair.Length -ne 2 -or $pair[0] -notmatch '^[A-Z_][A-Z0-9_]*$') {
                throw 'Geçersiz .env satırı. KEY=value biçimini kullanın.'
            }
            [Environment]::SetEnvironmentVariable($pair[0], $pair[1], 'Process')
        }
    }
    & .\mvnw.cmd -B -ntp -Pruntime verify
    if ($LASTEXITCODE -ne 0) { throw 'Maven doğrulaması başarısız.' }
    & java -jar target/runtime/payara-micro.jar --addlibs target/runtime/postgresql.jar `
        --deploy target/ticket-reservation-system.war --contextroot / --port $Port --disablephonehome --nocluster
    if ($LASTEXITCODE -ne 0) { throw 'Payara başlatılamadı.' }
} finally {
    Pop-Location
}
