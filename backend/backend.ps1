param([ValidateSet('run', 'build', 'test', 'test-db', 'stop')][string]$Action = 'run')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk'
$env:PATH = "$env:JAVA_HOME\bin;$env:ProgramFiles\Docker\Docker\resources\bin;$env:PATH"
$maven = Join-Path $projectRoot '.tools\maven\bin\mvn.cmd'
if (!(Test-Path -LiteralPath $maven) -or !(Test-Path -LiteralPath "$env:JAVA_HOME\bin\java.exe")) {
    & "$projectRoot\project.ps1" setup
}
$localDir = Join-Path $projectRoot '.local'
New-Item -ItemType Directory -Force -Path $localDir | Out-Null

function Invoke-BackendMaven {
    & $maven -f "$PSScriptRoot\pom.xml" "-Dmaven.repo.local=$projectRoot\.tools\m2" @args
    if ($LASTEXITCODE -ne 0) { throw 'Backend Maven build failed' }
}

function Invoke-WebCompose {
    & docker compose --env-file "$projectRoot\.env" -f "$projectRoot\compose.yaml" --profile web @args
    if ($LASTEXITCODE -ne 0) { throw 'Docker command failed. Open Docker Desktop and retry.' }
}

if ($Action -eq 'test') { Invoke-BackendMaven test; return }
if ($Action -eq 'build') { Invoke-BackendMaven package; return }

$envFile = Join-Path $projectRoot '.env'
if (!(Test-Path -LiteralPath $envFile)) {
    $credentialsFile = Join-Path $localDir 'credentials.json'
    if (!(Test-Path -LiteralPath $credentialsFile)) {
        if (Test-Path -LiteralPath (Join-Path $localDir 'pgdata\PG_VERSION')) {
            throw 'Restore .local/credentials.json for the existing database'
        }
        @{ appPassword = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N') } |
            ConvertTo-Json | Set-Content -LiteralPath $credentialsFile -Encoding ascii
    }
    $dbPassword = (Get-Content -LiteralPath $credentialsFile -Raw | ConvertFrom-Json).appPassword
    $randomBytes = New-Object byte[] 48
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($randomBytes) } finally { $generator.Dispose() }
    $jwtSecret = [Convert]::ToBase64String($randomBytes)
    $adminPassword = 'Tmp1!' + [guid]::NewGuid().ToString('N')
    @(
        "DB_PASSWORD=$dbPassword",
        "JWT_SECRET_BASE64=$jwtSecret",
        'BOOTSTRAP_ADMIN_EMAIL=admin@parking.local',
        "BOOTSTRAP_ADMIN_PASSWORD=$adminPassword",
        'CORS_ALLOWED_ORIGINS=http://localhost:8080',
        'COOKIE_SECURE=false',
        'PERMANENT_DELETE_ENABLED=true'
    ) | Set-Content -LiteralPath $envFile -Encoding ascii
    @('http://localhost:8080', 'admin@parking.local', $adminPassword) |
        Set-Content -LiteralPath (Join-Path $localDir 'backend-login.txt') -Encoding ascii
}

if ($Action -eq 'stop') { Invoke-WebCompose stop backend; return }
if ($Action -eq 'test-db') {
    Invoke-WebCompose up -d --wait db
    $env:DB_URL = 'jdbc:postgresql://127.0.0.1:5433/pksjava'
    $env:DB_USER = 'pks_app'
    $passwordLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -like 'DB_PASSWORD=*' } | Select-Object -First 1
    $env:DB_PASSWORD = $passwordLine.Substring('DB_PASSWORD='.Length)
    $env:RUN_POSTGRES_TESTS = 'true'
    try { Invoke-BackendMaven test '-Dtest=PostgresMigrationTest' }
    finally { Remove-Item Env:\RUN_POSTGRES_TESTS -ErrorAction SilentlyContinue }
    return
}
Invoke-BackendMaven package '-DskipTests'
Invoke-WebCompose up -d --wait db
$backup = Join-Path $localDir 'parking-before-kr3.dump'
if (!(Test-Path -LiteralPath $backup)) {
    Invoke-WebCompose exec -T db pg_dump -U pks_app -d pksjava -Fc -f /tmp/parking-before-kr3.dump
    Invoke-WebCompose cp db:/tmp/parking-before-kr3.dump $backup
}
Invoke-WebCompose up -d --build --wait backend
Write-Host 'Parking web: http://localhost:8080'
Write-Host 'Initial login: .local/backend-login.txt (change the temporary password on first login)'
