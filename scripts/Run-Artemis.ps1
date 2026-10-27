param(
    [string]$DeviceSerial = 'emulator-5554',
    [string]$ArtemisRoot = 'D:\Tools\artemis',
    [string]$SdkRoot = 'D:\Dev\AndroidSdk'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$apk = Join-Path $projectRoot 'app\build\outputs\apk\preview\app-preview.apk'
$adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
$python = Join-Path $ArtemisRoot '.venv\Scripts\python.exe'
if (!(Test-Path -LiteralPath $apk)) { throw 'Build assemblePreview before running device acceptance.' }
if (!(Test-Path -LiteralPath $python)) { throw 'Google Artemis installation not found at ArtemisRoot.' }
& $adb -s $DeviceSerial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
& $adb -s $DeviceSerial shell am start -n com.datapulse.app/.MainActivity
$goal = @'
Test DataPulse (package com.datapulse.app) on this dedicated emulator. Stay within DataPulse except for its Usage Access settings. Verify the initial permission-denied experience is clear, then open Set up my plan. Try Save my plan with empty inputs and verify an error. Fill Test carrier, daily data 1.5 GB, price 299 rupees, 28 days, and leave the prefilled current start date. Save. Visit Overview, Trends, Apps, My plan, Settings. Verify no crashes, readable layouts, and meaningful empty states. Toggle quiet hours, leave Settings and return to verify persistence. Open Delete all local records and cancel. Do not share any files or delete records. Report each observed pass/fail and capture screenshots. Do not claim carrier accuracy from emulator counters.
'@
Push-Location $ArtemisRoot
try { & $python -m artemis run $goal --profile flash --device-serial $DeviceSerial --standalone }
finally { Pop-Location }
