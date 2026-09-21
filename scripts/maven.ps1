. "$PSScriptRoot\env.ps1"
if (!(Test-Path "$env:JAVA_HOME\bin\javac.exe") -or !(Test-Path "$env:MAVEN_HOME\bin\mvn.cmd")) {
    throw 'Run scripts/setup-tools.ps1 first to install the project JDK and Maven.'
}
Push-Location $projectRoot
try {
    & "$env:MAVEN_HOME\bin\mvn.cmd" "-Dmaven.repo.local=$projectRoot\.tools\m2" @args
    if ($LASTEXITCODE -ne 0) { throw "Maven failed: $LASTEXITCODE" }
} finally { Pop-Location }
