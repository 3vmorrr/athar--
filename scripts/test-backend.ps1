param(
    [string]$XamppRoot = 'C:\xampp',
    [ValidateRange(1024,65535)][int]$Port = 3308,
    [string[]]$Tests = @()
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$php = Join-Path $XamppRoot 'php\php.exe'
$mysqlBin = Join-Path $XamppRoot 'mysql\bin'
$testData = [IO.Path]::GetFullPath((Join-Path $repo 'storage\engineering\integration-mariadb'))
$expectedParent = [IO.Path]::GetFullPath((Join-Path $repo 'storage\engineering')) + [IO.Path]::DirectorySeparatorChar
if (-not $testData.StartsWith($expectedParent,[StringComparison]::OrdinalIgnoreCase)) { throw 'Test data directory must remain under storage/engineering.' }
foreach ($executable in @($php,(Join-Path $mysqlBin 'mysql_install_db.exe'),(Join-Path $mysqlBin 'mysqld.exe'))) {
    if (-not (Test-Path -LiteralPath $executable)) { throw "Missing executable: $executable" }
}
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw "Port $Port is already in use. Select an unused port; this runner never connects to an existing server." }
if (-not (Test-Path -LiteralPath (Join-Path $testData 'my.ini'))) {
    New-Item -ItemType Directory -Path $testData -Force | Out-Null
    & (Join-Path $mysqlBin 'mysql_install_db.exe') "--datadir=$testData" "--port=$Port" --silent
    if ($LASTEXITCODE -ne 0) { throw 'Dedicated test database initialization failed.' }
}
$oldValues = @{}
foreach ($name in @('ATHAR_LOAD_DOTENV','ATHAR_DB_HOST','ATHAR_DB_PORT','ATHAR_DB_NAME','ATHAR_DB_USER','ATHAR_DB_PASSWORD','ATHAR_TEST_DATA_DIR')) { $oldValues[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
$schema = 'athar_test_control_' + [Guid]::NewGuid().ToString('N').Substring(0,12)
$env:ATHAR_LOAD_DOTENV='0'; $env:ATHAR_DB_HOST='127.0.0.1'; $env:ATHAR_DB_PORT="$Port"; $env:ATHAR_DB_NAME=$schema; $env:ATHAR_DB_USER='root'; $env:ATHAR_DB_PASSWORD=''; $env:ATHAR_TEST_DATA_DIR=$testData
$ownedServer = $null
Push-Location $repo
try {
    $ownedServer = Start-Process -FilePath (Join-Path $mysqlBin 'mysqld.exe') -ArgumentList @("--defaults-file=`"$(Join-Path $testData 'my.ini')`"",'--bind-address=127.0.0.1',"--port=$Port",'--innodb-buffer-pool-size=33554432','--aria-pagecache-buffer-size=16777216','--key-buffer-size=8388608','--max-connections=16','--console') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testData 'runner.stdout.log') -RedirectStandardError (Join-Path $testData 'runner.stderr.log')
    $ready = $false
    for ($attempt=0; $attempt -lt 40; $attempt++) {
        if ($ownedServer.HasExited) { throw 'Dedicated MariaDB exited; inspect storage/engineering/integration-mariadb/runner.stderr.log.' }
        if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { $ready = $true; break }
        Start-Sleep -Milliseconds 250
    }
    if (-not $ready) { throw 'Dedicated test server did not start.' }
    & $php scripts/test-control.php create
    if ($LASTEXITCODE -ne 0) { throw 'Test control database safety check/setup failed.' }
    $availableTests = @('tests/campaign_test.php','tests/domain_test.php','tests/quran_text_test.php','tests/backup_assets_test.php','tests/provider_identity_migration_test.php','tests/admin_event_time_test.php','tests/recovery_restore_test.php','tests/media_provider_test.php','tests/tafsir_provider_test.php','tests/api_identity_test.php','tests/catalog_progress_test.php','tests/learning_test.php','tests/upcoming_lessons_test.php','tests/night_prayer_api_test.php','tests/admin_authorization_test.php')
    if ($Tests.Count -eq 0) { $Tests = $availableTests }
    foreach ($test in $Tests) {
        if ($test -notin $availableTests) { throw 'Unsupported test selection.' }
        & $php $test
        if ($LASTEXITCODE -ne 0) { throw "Failed: $test" }
    }
} finally {
    if ($ownedServer -and -not $ownedServer.HasExited) {
        & $php scripts/test-control.php drop
        Stop-Process -Id $ownedServer.Id -ErrorAction SilentlyContinue
    }
    Pop-Location
    foreach ($name in $oldValues.Keys) { [Environment]::SetEnvironmentVariable($name,$oldValues[$name],'Process') }
}
