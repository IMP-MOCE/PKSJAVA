. "$PSScriptRoot\env.ps1"
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
if (!(Test-Path "$env:JAVA_HOME\bin\javac.exe") -or !(Test-Path "$env:MAVEN_HOME\bin\mvn.cmd")) {
    & "$PSScriptRoot\setup-tools.ps1"
}
& "$PSScriptRoot\db.ps1" start
& "$PSScriptRoot\maven.ps1" -q package -DskipTests
# Каждый процесс получает свою копию: пересборка target не меняет открытый JAR.
$runtimeJar = Join-Path $localDir ("parking-run-" + [guid]::NewGuid().ToString('N') + '.jar')
Copy-Item -LiteralPath "$projectRoot\target\parking-1.0-SNAPSHOT.jar" -Destination $runtimeJar
Push-Location $projectRoot
try {
    & "$env:JAVA_HOME\bin\java.exe" '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -jar $runtimeJar
    if ($LASTEXITCODE -ne 0) { throw 'Application failed' }
} finally {
    Pop-Location
    Remove-Item -LiteralPath $runtimeJar -ErrorAction SilentlyContinue
}
