$ErrorActionPreference = "Stop"

$adbCandidates = @()

if ($env:ANDROID_HOME) {
    $adbCandidates += Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
}

if ($env:ANDROID_SDK_ROOT) {
    $adbCandidates += Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe"
}

$adbCandidates += "C:\Android\sdk\platform-tools\adb.exe"
$adbCandidates += "adb"

$adb = $adbCandidates |
    Where-Object { ($_ -eq "adb") -or (Test-Path -LiteralPath $_) } |
    Select-Object -First 1

if (-not $adb) {
    throw "adb not found. Add Android platform-tools to PATH or set ANDROID_HOME."
}

& $adb shell am start -n "com.kanime.app/.MainActivity"

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
