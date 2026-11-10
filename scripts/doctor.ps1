$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkCandidates = @(
    $env:ANDROID_HOME,
    $env:ANDROID_SDK_ROOT,
    "$env:LOCALAPPDATA\Android\Sdk",
    'C:\Android\Sdk'
) | Where-Object { $_ } | Select-Object -Unique

$studioCandidates = @(
    'C:\Program Files\Android\Android Studio\bin\studio64.exe',
    'C:\Android\Android Studio\bin\studio64.exe'
)

$javaCandidates = @(
    $env:JAVA_HOME,
    'C:\Java\temurin-17.0.20.1+1',
    'C:\Program Files\Eclipse Adoptium\jdk-17*',
    'C:\Program Files\Android\Android Studio\jbr'
) | Where-Object { $_ }

$sdkRoot = $sdkCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
$studio = $studioCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
$javaHome = $javaCandidates | ForEach-Object {
    Get-Item $_ -ErrorAction SilentlyContinue
} | Select-Object -ExpandProperty FullName -First 1
$avdRoot = if ($env:ANDROID_AVD_HOME) { $env:ANDROID_AVD_HOME } else { 'C:\Android\avd' }

$checks = @(
    [pscustomobject]@{ Item = 'Android Studio'; Status = if ($studio) { 'OK' } else { 'MISSING' }; Path = $studio },
    [pscustomobject]@{ Item = 'Java'; Status = if ($javaHome) { 'OK' } else { 'MISSING' }; Path = $javaHome },
    [pscustomobject]@{ Item = 'Android SDK'; Status = if ($sdkRoot) { 'OK' } else { 'MISSING' }; Path = $sdkRoot },
    [pscustomobject]@{ Item = 'API 35 platform'; Status = if ($sdkRoot -and (Test-Path "$sdkRoot\platforms\android-35")) { 'OK' } else { 'MISSING' }; Path = if ($sdkRoot) { "$sdkRoot\platforms\android-35" } },
    [pscustomobject]@{ Item = 'Platform Tools'; Status = if ($sdkRoot -and (Test-Path "$sdkRoot\platform-tools\adb.exe")) { 'OK' } else { 'MISSING' }; Path = if ($sdkRoot) { "$sdkRoot\platform-tools\adb.exe" } },
    [pscustomobject]@{ Item = 'Pleos image'; Status = if ($sdkRoot -and (Test-Path "$sdkRoot\system-images\android-34\android-automotive\x86_64\source.properties")) { 'OK' } else { 'MISSING' }; Path = if ($sdkRoot) { "$sdkRoot\system-images\android-34\android-automotive\x86_64" } },
    [pscustomobject]@{ Item = 'Pleos AVD'; Status = if (Test-Path "$avdRoot\Pleos_Connect_v2_1.avd\config.ini") { 'OK' } else { 'MISSING' }; Path = "$avdRoot\Pleos_Connect_v2_1.avd" },
    [pscustomobject]@{ Item = 'Gradle wrapper'; Status = if (Test-Path "$projectRoot\gradlew.bat") { 'OK' } else { 'MISSING' }; Path = "$projectRoot\gradlew.bat" }
)

$checks | Format-Table -AutoSize

if ($checks.Status -contains 'MISSING') {
    exit 1
}

