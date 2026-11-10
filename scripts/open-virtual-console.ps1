$ErrorActionPreference = 'Stop'

$adbCandidates = @(
    "$env:ANDROID_HOME\platform-tools\adb.exe",
    "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe",
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    'C:\Android\Sdk\platform-tools\adb.exe'
) | Where-Object { Test-Path $_ }

$adb = $adbCandidates | Select-Object -First 1
if (-not $adb) {
    throw 'adb.exe를 찾지 못했습니다. 먼저 Android SDK Platform Tools를 설치하세요.'
}

& $adb forward tcp:8088 tcp:8088
Write-Host 'Pleos Virtual Console: http://127.0.0.1:8088/'
Start-Process 'http://127.0.0.1:8088/'

