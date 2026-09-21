. "$PSScriptRoot\env.ps1"
& "$PSScriptRoot\db.ps1" start
$env:PGPASSWORD = $env:DB_PASSWORD
try {
    & "$pgBin\psql.exe" -X -h 127.0.0.1 -p 5433 -U pks_app -d pksjava -v ON_ERROR_STOP=1 -f "$projectRoot\sql\seed.sql"
    if ($LASTEXITCODE -ne 0) { throw 'Seed failed' }
} finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
