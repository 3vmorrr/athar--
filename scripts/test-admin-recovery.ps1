param(
    [string]$XamppRoot = 'C:\xampp',
    [string]$Python = 'C:\Users\SaWa\AppData\Local\Programs\Python\Python312\python.exe',
    [string]$Node = 'C:\Users\SaWa\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe',
    [string]$Playwright = 'C:\Users\SaWa\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules\playwright',
    [ValidateSet('admin_recovery_http_test.py','admin_scholar_http_test.py','admin_series_http_test.py')][string]$Suite = 'admin_recovery_http_test.py',
    [int]$Port = 3311
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$engineering = [IO.Path]::GetFullPath((Join-Path $repo 'storage/engineering'))
$sandbox = [IO.Path]::GetFullPath((Join-Path $engineering ('recovery-http-' + [Guid]::NewGuid().ToString('N'))))
if (-not $sandbox.StartsWith($engineering + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid test root.' }
$php = Join-Path $XamppRoot 'php/php.exe'
$mysqlBin = Join-Path $XamppRoot 'mysql/bin'
$data = Join-Path $sandbox 'mysql'
if (Get-NetTCPConnection -State Listen -LocalPort $Port,8004 -ErrorAction SilentlyContinue) { throw 'Recovery test ports are already occupied.' }
New-Item -ItemType Directory -Path $sandbox,$data,(Join-Path $sandbox 'storage/engineering'),(Join-Path $sandbox 'storage/sessions') -Force | Out-Null
# Never copy .env, production storage, books, keys or session files.
foreach ($folder in @('app','admin','backend','database','public','tests')) {
    Copy-Item -LiteralPath (Join-Path $repo $folder) -Destination $sandbox -Recurse
}
& (Join-Path $mysqlBin 'mysql_install_db.exe') "--datadir=$data" "--port=$Port" --silent
if ($LASTEXITCODE -ne 0) { throw 'Recovery MariaDB initialization failed.' }
$previousValues=@{}
foreach ($name in @('ATHAR_LOAD_DOTENV','APP_ENV','ATHAR_DB_HOST','ATHAR_DB_PORT','ATHAR_DB_NAME','ATHAR_DB_USER','ATHAR_DB_PASSWORD','ATHAR_TEST_DATA_DIR','ATHAR_BACKUP_KEY','ATHAR_AUTH_KEY','ATHAR_DATA_KEY','ATHAR_RECOVERY_SANDBOX','ATHAR_TEST_SESSION_DIR','ATHAR_TEST_NODE','ATHAR_TEST_PLAYWRIGHT')) { $previousValues[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
$env:ATHAR_LOAD_DOTENV='0'; $env:APP_ENV='local'; $env:ATHAR_DB_HOST='127.0.0.1'; $env:ATHAR_DB_PORT="$Port"; $env:ATHAR_DB_NAME='mysql'; $env:ATHAR_DB_USER='root'; $env:ATHAR_DB_PASSWORD=''; $env:ATHAR_TEST_DATA_DIR=$data
$env:ATHAR_BACKUP_KEY=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
$env:ATHAR_AUTH_KEY=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
$env:ATHAR_DATA_KEY=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
$env:ATHAR_RECOVERY_SANDBOX=$sandbox
$env:ATHAR_TEST_SESSION_DIR=Join-Path $sandbox 'storage/sessions'
$env:ATHAR_TEST_NODE=$Node
$env:ATHAR_TEST_PLAYWRIGHT=$Playwright
$databaseServer=$null; $httpServer=$null; $fixtureCreated=$false
try {
    $databaseServer=Start-Process -FilePath (Join-Path $mysqlBin 'mysqld.exe') -ArgumentList @("--defaults-file=`"$(Join-Path $data 'my.ini')`"",'--bind-address=127.0.0.1',"--port=$Port",'--innodb-buffer-pool-size=33554432','--aria-pagecache-buffer-size=16777216','--key-buffer-size=8388608','--max-connections=16','--console') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $sandbox 'mysql.stdout.log') -RedirectStandardError (Join-Path $sandbox 'mysql.stderr.log')
    $ready=$false
    for ($attempt=0;$attempt -lt 60;$attempt++) {
        if ($databaseServer.HasExited) { throw 'Recovery database exited.' }
        if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { $ready=$true;break }
        Start-Sleep -Milliseconds 250
    }
    if (-not $ready) { throw 'Recovery database did not start.' }
    & $php (Join-Path $sandbox 'tests/native_fixture.php') create
    if ($LASTEXITCODE -ne 0) { throw 'Recovery fixture creation failed.' }
    $fixtureCreated=$true
    $httpServer=Start-Process -FilePath $php -WorkingDirectory $sandbox -ArgumentList @('-d',"session.save_path=`"$(Join-Path $sandbox 'storage/sessions')`"",'-S','127.0.0.1:8004','-t',"`"$(Join-Path $sandbox 'public')`"","`"$(Join-Path $sandbox 'tests/native_router.php')`"") -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $sandbox 'http.stdout.log') -RedirectStandardError (Join-Path $sandbox 'http.stderr.log')
    $ready=$false
    for ($attempt=0;$attempt -lt 40;$attempt++) {
        if ($httpServer.HasExited) { throw 'Recovery HTTP server exited.' }
        if (Get-NetTCPConnection -LocalPort 8004 -State Listen -ErrorAction SilentlyContinue) { $ready=$true;break }
        Start-Sleep -Milliseconds 250
    }
    if (-not $ready) { throw 'Recovery HTTP server did not start.' }
    & $Python (Join-Path $sandbox ('tests/' + $Suite)) *> (Join-Path $sandbox 'assertions.log')
    $testExit=$LASTEXITCODE
    Get-Content -LiteralPath (Join-Path $sandbox 'assertions.log')
    if ($testExit -ne 0) { throw 'Recovery HTTP acceptance failed.' }
} finally {
    if ($httpServer -and -not $httpServer.HasExited) { Stop-Process -Id $httpServer.Id }
    if ($fixtureCreated -and $databaseServer -and -not $databaseServer.HasExited) {
        & $php (Join-Path $sandbox 'tests/native_fixture.php') drop
    }
    if ($databaseServer -and -not $databaseServer.HasExited) { Stop-Process -Id $databaseServer.Id }
    foreach ($name in $previousValues.Keys) { [Environment]::SetEnvironmentVariable($name,$previousValues[$name],'Process') }
    # Preserve isolated logs and files for review; no recursive deletion.
    Write-Output "Recovery acceptance evidence: $sandbox"
}
