# One entry point for local setup, launch and checks.
param([ValidateSet('run','test','build','seed','setup','db-start','db-stop','db-status','db-shell')]
      [string]$Action = 'run')

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk'
$env:MAVEN_HOME = Join-Path $projectRoot '.tools\maven'
$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
$env:PATH = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$pgBin;$env:PATH"
$env:PGCLIENTENCODING = 'UTF8'
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5433/pksjava'
$env:DB_USER = 'pks_app'
$localDir = Join-Path $projectRoot '.local'
if (Test-Path "$localDir\credentials.json") {
    $credentials = Get-Content "$localDir\credentials.json" -Raw | ConvertFrom-Json
    $env:DB_PASSWORD = $credentials.appPassword
}

[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding

function Install-Tools {
    $toolsDir = Join-Path $projectRoot '.tools'
    New-Item -ItemType Directory -Force $toolsDir | Out-Null
    function Download([string]$Url, [string]$Destination) {
        Invoke-WebRequest -UseBasicParsing -TimeoutSec 900 -Uri $Url -OutFile $Destination
    }
    if (!(Test-Path "$toolsDir\jdk\bin\javac.exe")) {
        Download 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse' "$toolsDir\release.json"
        $release = Get-Content "$toolsDir\release.json" -Raw | ConvertFrom-Json
        $package = $release[0].binary.package
        Download $package.link "$toolsDir\jdk.zip"
        if ((Get-FileHash "$toolsDir\jdk.zip" -Algorithm SHA256).Hash.ToLower() -ne $package.checksum) { throw 'JDK checksum mismatch' }
        Expand-Archive "$toolsDir\jdk.zip" $toolsDir -Force
        $extracted = @(Get-ChildItem $toolsDir -Directory -Filter 'jdk-*' | Where-Object {
            Test-Path (Join-Path $_.FullName 'bin\javac.exe')
        })
        if ($extracted.Count -ne 1) { throw 'Expected exactly one extracted JDK' }
        Rename-Item -LiteralPath $extracted[0].FullName -NewName jdk
    }
    if (!(Test-Path "$toolsDir\maven\bin\mvn.cmd")) {
        $url = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip'
        Download $url "$toolsDir\maven.zip"
        Download "$url.sha512" "$toolsDir\maven.sha512"
        $expected = (Get-Content "$toolsDir\maven.sha512" -Raw).Trim().Split(' ')[0]
        if ((Get-FileHash "$toolsDir\maven.zip" -Algorithm SHA512).Hash.ToLower() -ne $expected.ToLower()) { throw 'Maven checksum mismatch' }
        Expand-Archive "$toolsDir\maven.zip" $toolsDir -Force
        Rename-Item "$toolsDir\apache-maven-3.9.16" maven
    }
}

function Invoke-Database {
    param([ValidateSet('start','stop','status','shell')][string]$Action = 'start')
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
}

function Invoke-Maven {
    & "$env:MAVEN_HOME\bin\mvn.cmd" "-Dmaven.repo.local=$projectRoot\.tools\m2" @args
    if ($LASTEXITCODE -ne 0) { throw "Maven failed: $LASTEXITCODE" }
}

Push-Location $projectRoot
try {
    if ($Action -in @('run', 'test', 'build', 'setup')) { Install-Tools }
    switch ($Action) {
        'setup' {
            & "$env:JAVA_HOME\bin\java.exe" -version
            Invoke-Maven -version
        }
        'build' { Invoke-Maven -q package '-DskipTests' }
        'test' {
            Invoke-Database start
            $previous = $env:RUN_DB_TESTS
            try {
                $env:RUN_DB_TESTS = 'true'
                Invoke-Maven verify
                Invoke-Maven -q test '-Dtest=PostgresIntegrationTest' "-DpackagedJar=$projectRoot\target\parking-1.0-SNAPSHOT.jar"
            } finally { $env:RUN_DB_TESTS = $previous }
        }
        'seed' {
            Invoke-Database start
            $env:PGPASSWORD = $env:DB_PASSWORD
            try {
                & "$pgBin\psql.exe" -X -h 127.0.0.1 -p 5433 -U pks_app -d pksjava -v ON_ERROR_STOP=1 -f "$projectRoot\sql\seed.sql"
                if ($LASTEXITCODE -ne 0) { throw 'Seed failed' }
            } finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
        }
        'db-start' { Invoke-Database start }
        'db-stop' { Invoke-Database stop }
        'db-status' { Invoke-Database status }
        'db-shell' { Invoke-Database shell }
        'run' {
            Invoke-Database start
            Invoke-Maven -q package '-DskipTests'
            # A private copy protects a running application from subsequent builds.
            $runtimeJar = Join-Path $localDir ("parking-run-" + [guid]::NewGuid().ToString('N') + '.jar')
            Copy-Item -LiteralPath "$projectRoot\target\parking-1.0-SNAPSHOT.jar" -Destination $runtimeJar
            try {
                & "$env:JAVA_HOME\bin\java.exe" '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -jar $runtimeJar
                if ($LASTEXITCODE -ne 0) { throw 'Application failed' }
            } finally { Remove-Item -LiteralPath $runtimeJar -ErrorAction SilentlyContinue }
        }
    }
} finally { Pop-Location }
