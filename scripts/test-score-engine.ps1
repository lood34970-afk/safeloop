$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Java\temurin-17.0.20.1+1' }
$java = Join-Path $javaHome 'bin\java.exe'
$moduleCache = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'

function Find-DependencyJar {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Filter
    )

    $jar = Get-ChildItem -LiteralPath $Root -Filter $Filter -Recurse |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $jar) { throw "Dependency not found: $Filter" }
    return $jar
}

$env:JAVA_HOME = $javaHome
Push-Location $projectRoot
try {
    & .\gradlew.bat :app:compileDebugUnitTestKotlin
    if ($LASTEXITCODE -ne 0) { throw "Unit test compilation failed: $LASTEXITCODE" }

    $junit = Find-DependencyJar "$moduleCache\junit\junit\4.13.2" 'junit-4.13.2.jar'
    $hamcrest = Find-DependencyJar "$moduleCache\org.hamcrest\hamcrest-core\1.3" 'hamcrest-core-1.3.jar'
    $kotlin = Find-DependencyJar "$moduleCache\org.jetbrains.kotlin\kotlin-stdlib" 'kotlin-stdlib-*.jar'
    $classPath = @(
        "$projectRoot\app\build\tmp\kotlin-classes\debug",
        "$projectRoot\app\build\tmp\kotlin-classes\debugUnitTest",
        $junit,
        $hamcrest,
        $kotlin
    ) -join ';'

    & $java -cp $classPath org.junit.runner.JUnitCore com.example.pleoschallenge.TripScoreEngineTest
    if ($LASTEXITCODE -ne 0) { throw "Score engine tests failed: $LASTEXITCODE" }
} finally {
    Pop-Location
}
