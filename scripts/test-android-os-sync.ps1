param(
    [Parameter(Mandatory=$true)][string]$DeviceSerial,
    [Parameter(Mandatory=$true)][ValidateSet('prepare','retained','verify')][string]$Action,
    [ValidateSet('process','reconnect','reboot')][string]$Stage='process',
    [ValidateRange(30,300)][int]$DelaySeconds=90,
    [ValidateSet('ar','en')][string]$Language='ar'
)
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if(-not(Test-Path -LiteralPath $adb)){throw 'Android SDK platform-tools are required.'}
if($DeviceSerial -notmatch '^[A-Za-z0-9._:-]+$'){throw 'Invalid selected device serial.'}
$evidence=Join-Path $repo 'storage\engineering'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
Push-Location $repo
try{
    $state=& $adb -s $DeviceSerial get-state
    if($LASTEXITCODE -ne 0 -or $state -ne 'device'){throw 'The selected development device is unavailable.'}
    & $adb -s $DeviceSerial reverse tcp:8004 tcp:8004
    if($LASTEXITCODE -ne 0){throw 'Could not establish the fixture transport.'}
    $method=switch($Action){'prepare'{'prepare'} 'retained'{'retainedWithoutNetwork'} 'verify'{'verifyDelivered'}}
    $arguments=@('-s',$DeviceSerial,'shell','am','instrument','-w','-e','nativeApiSmoke','true','-e','osLifecycle','true','-e','nativeFixtureHost','127.0.0.1','-e','nativeLanguage',$Language)
    if($Action -eq 'prepare'){
        & 'C:\xampp\php\php.exe' tests/seed_loss_fixture.php
        if($LASTEXITCODE -ne 0){throw 'The isolated fixture seed failed.'}
        $actor=Get-Content -LiteralPath (Join-Path $evidence 'loss-fixture.json') -Raw | ConvertFrom-Json
        if($actor.fixture_only -ne $true -or $actor.email -notmatch '^native\.loss\.[a-f0-9]{12}@example\.test$'){throw 'Unexpected fixture actor.'}
        $arguments+=@('-e','lossFixtureEmail',$actor.email,'-e','lifecycleStage',$Stage,'-e','lifecycleDelaySeconds',"$DelaySeconds")
    }else{
        $receipt=Get-Content -LiteralPath (Join-Path $evidence 'os-lifecycle-device.json') -Raw | ConvertFrom-Json
        if($receipt.fixture_only -ne $true -or $receipt.stage -notin @('process','reconnect','reboot')){throw 'Unexpected lifecycle receipt.'}
        $Stage=$receipt.stage
    }
    $arguments+=@('-e','class',"org.athar.app.OsSyncLifecycleTest#$method",'org.athar.app.debug.test/org.athar.app.AtharTestRunner')
    $log=Join-Path $evidence "os-lifecycle-$Stage-$Action.log"
    & $adb @arguments *> $log
    $output=Get-Content -LiteralPath $log -Raw
    if($LASTEXITCODE -ne 0 -or $output -notmatch 'OK \(1 test\)' -or $output -match 'FAILURES!!!|INSTRUMENTATION_FAILED'){throw "Lifecycle $Action failed; inspect $log"}
    if($Action -eq 'prepare'){
        $captured=& $adb -s $DeviceSerial exec-out run-as org.athar.app.debug cat files/os-lifecycle-fixture.json
        if($LASTEXITCODE -ne 0){throw 'The generated device receipt is unavailable.'}
        $json=$captured -join "`n"
        [IO.File]::WriteAllText((Join-Path $evidence 'os-lifecycle-device.json'),$json,[Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText((Join-Path $evidence "os-lifecycle-$Stage-device.json"),$json,[Text.UTF8Encoding]::new($false))
        $initial=& 'C:\xampp\php\php.exe' tests/check_os_lifecycle_fixture.php
        if($LASTEXITCODE -ne 0 -or ($initial | ConvertFrom-Json).state -ne 'pending'){throw 'The generated server actor was not empty immediately after preparation.'}
        [IO.File]::WriteAllText((Join-Path $evidence "os-lifecycle-$Stage-initial-server.json"),($initial -join "`n"),[Text.UTF8Encoding]::new($false))
    }
    if($Action -eq 'verify'){
        $captured=& $adb -s $DeviceSerial exec-out run-as org.athar.app.debug cat files/os-lifecycle-result.json
        if($LASTEXITCODE -ne 0){throw 'The generated device result is unavailable.'}
        [IO.File]::WriteAllText((Join-Path $evidence "os-lifecycle-$Stage-result.json"),($captured -join "`n"),[Text.UTF8Encoding]::new($false))
    }
    Write-Output "PASS: lifecycle $Stage $Action (one actual-device test)."
}finally{Pop-Location}
