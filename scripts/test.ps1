. "$PSScriptRoot\env.ps1"
& "$PSScriptRoot\db.ps1" start
$previous = $env:RUN_DB_TESTS
try {
    $env:RUN_DB_TESTS = 'true'
    & "$PSScriptRoot\maven.ps1" verify
    & "$PSScriptRoot\maven.ps1" -q test '-Dtest=PostgresIntegrationTest' "-DpackagedJar=$projectRoot\target\parking-1.0-SNAPSHOT.jar"
} finally { $env:RUN_DB_TESTS = $previous }
