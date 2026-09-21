$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
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
