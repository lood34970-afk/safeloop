$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$javaHome = 'C:\Java\temurin-17.0.20.1+1'
$sdkRoot = 'C:\Android\Sdk'
$adb = "$sdkRoot\platform-tools\adb.exe"
$apk = "$projectRoot\app\build\outputs\apk\debug\app-debug.apk"

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_AVD_HOME = 'C:\Android\avd'
$env:ADB_VENDOR_KEYS = "$env:USERPROFILE\.android"

$deviceState = (& $adb get-state 2>&1 | Out-String).Trim()
if ($deviceState -ne 'device') {
    throw 'Pleos Emulator에서 최초 USB 디버깅 허용을 누른 뒤 다시 실행하세요.'
}

Push-Location $projectRoot
try {
    & .\gradlew.bat assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed: $LASTEXITCODE" }

    & $adb install -r $apk
    if ($LASTEXITCODE -ne 0) { throw "APK install failed: $LASTEXITCODE" }

    & $adb shell am start -n 'com.example.pleoschallenge/.MainActivity'
    if ($LASTEXITCODE -ne 0) { throw "App launch failed: $LASTEXITCODE" }
} finally {
    Pop-Location
}

