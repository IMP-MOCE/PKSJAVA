param([ValidateSet('start','stop','status','shell')][string]$Action = 'start')
. "$PSScriptRoot\env.ps1"
$dataDir = Join-Path $localDir 'pgdata'
if (!(Test-Path "$pgBin\pg_ctl.exe")) { throw "PostgreSQL not found: $pgBin" }
if ($Action -eq 'stop') {
    & "$pgBin\pg_ctl.exe" -D $dataDir -m fast -w stop
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL stop failed' }
    return
}
if ($Action -eq 'status') {
    & "$pgBin\pg_ctl.exe" -D $dataDir status
    return
}
if ($Action -eq 'shell') {
    $env:PGPASSWORD = $env:DB_PASSWORD
    try { & "$pgBin\psql.exe" -X -h 127.0.0.1 -p 5433 -U pks_app -d pksjava }
    finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
    return
}
New-Item -ItemType Directory -Force $localDir | Out-Null
if (!(Test-Path "$localDir\credentials.json")) {
    if (Test-Path "$dataDir\PG_VERSION") { throw 'Existing cluster has no credentials file; restore credentials.json.' }
    $credentials = [pscustomobject]@{
        adminPassword = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
        appPassword = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    }
    $credentials | ConvertTo-Json | Set-Content "$localDir\credentials.json" -Encoding ascii
}
$credentials = Get-Content "$localDir\credentials.json" -Raw | ConvertFrom-Json
if (!(Test-Path "$dataDir\PG_VERSION")) {
    $pwFile = Join-Path $localDir 'init-password.tmp'
    try {
        Set-Content $pwFile $credentials.adminPassword -Encoding ascii
        & "$pgBin\initdb.exe" -D $dataDir -U pks_admin --auth=scram-sha-256 --encoding=UTF8 --locale=C "--pwfile=$pwFile"
        if ($LASTEXITCODE -ne 0) { throw 'initdb failed' }
    } finally { Remove-Item -LiteralPath $pwFile -ErrorAction SilentlyContinue }
    Add-Content "$dataDir\postgresql.conf" "`nlisten_addresses = '127.0.0.1'`nport = 5433`n" -Encoding ascii
}
& "$pgBin\pg_ctl.exe" -D $dataDir status *> $null
if ($LASTEXITCODE -ne 0) {
    # Hidden window, with logs redirected by pg_ctl.
    $startArgs = '-D "' + $dataDir + '" -l "' + (Join-Path $localDir 'postgres.log') + '" -w start'
    $process = Start-Process -FilePath "$pgBin\pg_ctl.exe" -ArgumentList $startArgs -WindowStyle Hidden -PassThru
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "PostgreSQL start failed; see .local/postgres.log" }
}
$env:PGPASSWORD = $credentials.adminPassword
$psqlArgs = @('-X','-h','127.0.0.1','-p','5433','-U','pks_admin','-d','postgres','-v','ON_ERROR_STOP=1')
try {
    $role = & "$pgBin\psql.exe" @psqlArgs -tAc "SELECT 1 FROM pg_roles WHERE rolname='pks_app'"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot connect to project PostgreSQL' }
    if ($role -ne '1') {
        "CREATE ROLE pks_app LOGIN PASSWORD '$($credentials.appPassword)';" | & "$pgBin\psql.exe" @psqlArgs
        if ($LASTEXITCODE -ne 0) { throw 'Role creation failed' }
    }
    $db = & "$pgBin\psql.exe" @psqlArgs -tAc "SELECT 1 FROM pg_database WHERE datname='pksjava'"
    if ($LASTEXITCODE -ne 0) { throw 'Database check failed' }
    if ($db -ne '1') {
        & "$pgBin\psql.exe" @psqlArgs -c 'CREATE DATABASE pksjava OWNER pks_app'
        if ($LASTEXITCODE -ne 0) { throw 'Database creation failed' }
    }
    $env:PGPASSWORD = $credentials.appPassword
    & "$pgBin\psql.exe" -X -h 127.0.0.1 -p 5433 -U pks_app -d pksjava -v ON_ERROR_STOP=1 -f "$projectRoot\sql\schema.sql"
    if ($LASTEXITCODE -ne 0) { throw 'Schema initialization failed' }
} finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
$env:DB_PASSWORD = $credentials.appPassword
Write-Host 'PostgreSQL ready: 127.0.0.1:5433 / pksjava / pks_app'
