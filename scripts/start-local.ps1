param([switch]$LoopbackOnly)
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
$settingsPath=Join-Path $repo 'storage\local-runtime.json'
if(-not (Test-Path -LiteralPath $settingsPath)){throw 'Local runtime configuration is missing. Configure a healthy database and see docs/deployment/LOCAL_RUNTIME.md.'}
$runtime=Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
$config=[IO.Path]::GetFullPath($runtime.mysqlConfig)
if(-not (Test-Path -LiteralPath $config)){throw 'Configured database server file is unavailable.'}
if([int]$runtime.mysqlPort -lt 1024 -or [int]$runtime.mysqlPort -gt 65535){throw 'Invalid development database port.'}
New-Item -ItemType Directory -Path (Join-Path $repo 'storage\engineering') -Force | Out-Null
$listener=Get-NetTCPConnection -State Listen -LocalPort $runtime.mysqlPort -ErrorAction SilentlyContinue | Select-Object -First 1
if($listener){
    $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if(-not $process.CommandLine.Contains($config)){throw 'The selected database port belongs to another server. Nothing was stopped or replaced.'}
}else{
    Start-Process -FilePath $runtime.mysql -ArgumentList @("--defaults-file=`"$config`"",'--bind-address=127.0.0.1',"--port=$($runtime.mysqlPort)",'--console') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $repo 'storage\engineering\local-mysql.stdout.log') -RedirectStandardError (Join-Path $repo 'storage\engineering\local-mysql.stderr.log') | Out-Null
    $ready=$false
    for($attempt=0;$attempt -lt 40;$attempt++){
        if(Get-NetTCPConnection -State Listen -LocalPort $runtime.mysqlPort -ErrorAction SilentlyContinue){$ready=$true;break}
        Start-Sleep -Milliseconds 250
    }
    if(-not $ready){throw 'Database startup failed; inspect the private local server log. No database repair was attempted.'}
}
$router=Join-Path $repo 'public\router.php'
foreach($server in @(@{Port=8081;Bind=$(if($LoopbackOnly){'127.0.0.1'}else{'0.0.0.0'})},@{Port=8002;Bind='127.0.0.1'})){
    $existing=Get-NetTCPConnection -State Listen -LocalPort $server.Port -ErrorAction SilentlyContinue | Select-Object -First 1
    if($existing){
        $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($existing.OwningProcess)"
        if(-not $process.CommandLine.Contains($router)){throw "Port $($server.Port) belongs to another application. Nothing was stopped."}
    }else{
        Start-Process -FilePath $runtime.php -ArgumentList @('-S',"$($server.Bind):$($server.Port)",'-t',(Join-Path $repo 'public'),$router) -WorkingDirectory $repo -WindowStyle Hidden -RedirectStandardOutput (Join-Path $repo "storage\engineering\php-$($server.Port).stdout.log") -RedirectStandardError (Join-Path $repo "storage\engineering\php-$($server.Port).stderr.log") | Out-Null
    }
}
Write-Output 'Website: http://127.0.0.1:8081'
Write-Output 'Android emulator API: http://10.0.2.2:8002/api/v1/'
Write-Output 'Database uses the configured healthy private data directory. No migrations, repair, reset or email were performed.'
