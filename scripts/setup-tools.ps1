# Downloads verified portable tools into this project; PostgreSQL must already be installed.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$projectRoot = Split-Path $PSScriptRoot -Parent
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
. "$PSScriptRoot\env.ps1"
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:MAVEN_HOME\bin\mvn.cmd" -version
