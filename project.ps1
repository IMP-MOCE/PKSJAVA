# One entry point for local setup, launch and checks.
param([ValidateSet('run','console','test','build','seed','setup','db-start','db-stop','db-status','db-shell')]
      [string]$Action = 'run')

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk'
$env:MAVEN_HOME = Join-Path $projectRoot '.tools\maven'
$env:PATH = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:ProgramFiles\Docker\Docker\resources\bin;$env:PATH"
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5433/pksjava'
$env:DB_USER = 'pks_app'
$localDir = Join-Path $projectRoot '.local'

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

function Invoke-Compose {
    & docker compose -f "$projectRoot\compose.yaml" @args
    if ($LASTEXITCODE -ne 0) { throw 'Docker command failed. Check that Docker Desktop is running.' }
}

function Invoke-Database {
    param([ValidateSet('start','stop','status','shell')][string]$Action = 'start')
    New-Item -ItemType Directory -Force $localDir | Out-Null
    if (!(Test-Path "$localDir\credentials.json")) {
        if (Test-Path "$localDir\pgdata\PG_VERSION") { throw 'Restore .local/credentials.json for the existing database.' }
        @{ appPassword = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N') } |
            ConvertTo-Json | Set-Content "$localDir\credentials.json" -Encoding ascii
    }
    $credentials = Get-Content "$localDir\credentials.json" -Raw | ConvertFrom-Json
    $env:DB_PASSWORD = $credentials.appPassword
    switch ($Action) {
        'stop' { Invoke-Compose stop db; return }
        'status' { Invoke-Compose ps db; return }
        'shell' { Invoke-Compose exec db psql -X -U pks_app -d pksjava; return }
    }
    Invoke-Compose up -d --wait db
    Invoke-Compose exec -T db psql -X -U pks_app -d pksjava -v ON_ERROR_STOP=1 -f /sql/schema.sql
    Write-Host 'Docker PostgreSQL ready: 127.0.0.1:5433 / pksjava / pks_app'
}

function Invoke-Maven {
    & "$env:MAVEN_HOME\bin\mvn.cmd" "-Dmaven.repo.local=$projectRoot\.tools\m2" @args
    if ($LASTEXITCODE -ne 0) { throw "Maven failed: $LASTEXITCODE" }
}

Push-Location $projectRoot
try {
    if ($Action -in @('run', 'console', 'test', 'build', 'setup')) { Install-Tools }
    switch ($Action) {
        'setup' {
            & "$env:JAVA_HOME\bin\java.exe" -version
            Invoke-Maven -version
        }
        'build' { Invoke-Maven -q package '-DskipTests' }
        'test' {
            Invoke-Database start
            $previous = $env:RUN_DB_TESTS
            $previousFx = $env:RUN_FX_TESTS
            try {
                $env:RUN_DB_TESTS = 'true'
                $env:RUN_FX_TESTS = 'true'
                Invoke-Maven verify
                Invoke-Maven -q test '-Dtest=PostgresIntegrationTest' "-DpackagedJar=$projectRoot\target\parking-1.0-SNAPSHOT.jar"
            } finally { $env:RUN_DB_TESTS = $previous; $env:RUN_FX_TESTS = $previousFx }
        }
        'seed' {
            Invoke-Database start
            Invoke-Compose exec -T db psql -X -U pks_app -d pksjava -v ON_ERROR_STOP=1 -f /sql/seed.sql
        }
        'db-start' { Invoke-Database start }
        'db-stop' { Invoke-Database stop }
        'db-status' { Invoke-Database status }
        'db-shell' { Invoke-Database shell }
        { $_ -in @('run', 'console') } {
            Invoke-Database start
            Invoke-Maven -q package '-DskipTests'
            # A private copy protects a running application from subsequent builds.
            $runtimeJar = Join-Path $localDir ("parking-run-" + [guid]::NewGuid().ToString('N') + '.jar')
            Copy-Item -LiteralPath "$projectRoot\target\parking-1.0-SNAPSHOT.jar" -Destination $runtimeJar
            $appArgs = if ($Action -eq 'console') { @('--console') } else { @() }
            try {
                & "$env:JAVA_HOME\bin\java.exe" '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -jar $runtimeJar @appArgs
                if ($LASTEXITCODE -ne 0) { throw 'Application failed' }
            } finally { Remove-Item -LiteralPath $runtimeJar -ErrorAction SilentlyContinue }
        }
    }
} finally { Pop-Location }
