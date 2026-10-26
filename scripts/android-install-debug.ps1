param([string]$DeviceSerial,[ValidateRange(1024,65535)][int]$ApiPort=8002)
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
$sdkCandidates=@()
$properties=Join-Path $repo 'android\local.properties'
if(Test-Path -LiteralPath $properties){
    $sdkLine=Get-Content -LiteralPath $properties | Where-Object {$_ -match '^sdk\.dir='} | Select-Object -First 1
    if($sdkLine){$sdkCandidates+=($sdkLine.Substring(8).Replace('\:',':').Replace('\\','\'))}
}
$sdkCandidates+=@($env:ANDROID_SDK_ROOT,$env:ANDROID_HOME,(Join-Path $env:LOCALAPPDATA 'Android\Sdk'))
$sdk=$sdkCandidates | Where-Object {$_ -and (Test-Path -LiteralPath (Join-Path $_ 'platform-tools\adb.exe'))} | Select-Object -First 1
if(-not $sdk){throw 'Android SDK platform-tools were not found in project/environment configuration.'}
$adb=Join-Path $sdk 'platform-tools\adb.exe'
function Invoke-Adb([string[]]$Arguments){
    $output=& $adb @Arguments 2>&1
    if($LASTEXITCODE -ne 0){throw ($output | Out-String)}
    return $output
}
$devices=@(Invoke-Adb @('devices') | Select-Object -Skip 1 | Where-Object {$_ -match '^\S+\s+\S+'} | ForEach-Object {
    $fields=$_ -split '\s+'; [pscustomobject]@{Serial=$fields[0];State=$fields[1]}
})
if($DeviceSerial){$device=$devices | Where-Object Serial -eq $DeviceSerial | Select-Object -First 1}
else {
    $physical=@($devices | Where-Object {$_.Serial -notlike 'emulator-*'})
    if($physical.Count -ne 1){throw 'Expected one physical Android device. Specify -DeviceSerial when more than one is connected.'}
    $device=$physical[0]
}
if(-not $device){throw 'Requested Android device was not detected.'}
if($device.State -ne 'device'){throw "Device state is $($device.State). Approve USB debugging on the phone if required; no install was attempted."}
$serial=$device.Serial
Write-Output ('Device model: '+((Invoke-Adb @('-s',$serial,'shell','getprop','ro.product.model')) | Out-String).Trim())
Write-Output ('Android API: '+((Invoke-Adb @('-s',$serial,'shell','getprop','ro.build.version.sdk')) | Out-String).Trim())
$base="http://127.0.0.1:$ApiPort/api/v1/"
$health=Invoke-RestMethod -Uri ($base+'health') -TimeoutSec 10
if($health.data.status -ne 'available'){throw 'Local API health is not available. Start the documented local stack first.'}
if(-not $env:JAVA_HOME -and (Test-Path -LiteralPath 'C:\Program Files\Android\Android Studio\jbr')){$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'}
& (Join-Path $repo 'android\gradlew.bat') -p (Join-Path $repo 'android') :app:assembleDebug "-PATHAR_API_BASE_URL=$base" --max-workers=1
if($LASTEXITCODE -ne 0){throw 'Android Debug build failed. Nothing was installed.'}
$apk=Join-Path $repo 'android\app\build\outputs\apk\debug\app-debug.apk'
if(-not (Test-Path -LiteralPath $apk)){throw 'Debug APK is missing.'}
$existing=Invoke-Adb @('-s',$serial,'reverse','--list')
foreach($mapping in $existing){
    $parts=$mapping -split '\s+'
    if($parts.Count -ge 3 -and $parts[1] -eq "tcp:$ApiPort" -and $parts[2] -ne "tcp:$ApiPort"){
        throw 'The phone port already has another reverse mapping. It was not overwritten.'
    }
}
Invoke-Adb @('-s',$serial,'install','--no-streaming','-r',$apk) | Write-Output
# Configure after update: some older devices reconnect ADB during installation.
Invoke-Adb @('-s',$serial,'reverse',"tcp:$ApiPort","tcp:$ApiPort") | Out-Null
$verifiedReverse=(Invoke-Adb @('-s',$serial,'reverse','--list') | Out-String)
if($verifiedReverse -notmatch "tcp:$ApiPort\s+tcp:$ApiPort"){throw 'USB reverse could not be verified. The app was not launched with an assumed connection.'}
# No uninstall, clear-data, version downgrade, global permission grant or logcat clearing.
Invoke-Adb @('-s',$serial,'shell','am','start','-W','-n','org.athar.app.debug/org.athar.app.MainActivity') | Write-Output
Write-Output "Debug app updated and launch command completed. API uses USB reverse on port $ApiPort. Verify UI/startup logs before reporting acceptance."
