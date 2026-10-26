param([ValidateRange(10,300)][int]$TimeoutSeconds=120)
$ErrorActionPreference='Stop'
# Intentionally fixed: never select or mutate an attached physical device.
$serial='emulator-5554'
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if(-not (Test-Path -LiteralPath $adb)){throw 'Android platform-tools are unavailable.'}
$elapsed=[Diagnostics.Stopwatch]::StartNew()
while($elapsed.Elapsed.TotalSeconds -lt $TimeoutSeconds){
    try {
        $boot=(& $adb -s $serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
        if($boot -eq '1'){
            $package=(& $adb -s $serial shell cmd package path android 2>$null | Out-String).Trim()
            if($package.StartsWith('package:',[StringComparison]::Ordinal)){
                Write-Output 'Dedicated QA emulator is ready (boot completed and package service available).'
                exit 0
            }
        }
    } catch { # Transport and services can be temporarily unavailable during cold boot.
    }
    Start-Sleep -Seconds 2
}
throw 'Dedicated QA emulator did not become ready. No installation or device mutation was attempted.'
