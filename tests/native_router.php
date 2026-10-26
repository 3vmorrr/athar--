<?php
declare(strict_types=1);
// Explicit CLI development router. Never serves the live application's schema.
if (PHP_SAPI !== 'cli-server' || ($_SERVER['SERVER_NAME'] ?? '') !== '127.0.0.1' || (int)($_SERVER['SERVER_PORT'] ?? 0) !== 8004) {
    http_response_code(403); exit;
}
$state=json_decode((string)file_get_contents(dirname(__DIR__).'/storage/engineering/native-fixture.json'),true,512,JSON_THROW_ON_ERROR);
if (($state['fixture_only'] ?? false) !== true || !preg_match('/^athar_test_native_[a-f0-9]{12}$/',$state['database'] ?? '') || !preg_match('/^athar_fixture_[a-f0-9]{12}$/',$state['user'] ?? '') || ($state['host'] ?? '') !== '127.0.0.1') {
    http_response_code(503); exit;
}
putenv('APP_ENV=local');putenv('ATHAR_LOAD_DOTENV=0');
foreach(['HOST'=>'host','PORT'=>'port','NAME'=>'database','USER'=>'user','PASSWORD'=>'password'] as $key=>$field) putenv('ATHAR_DB_'.$key.'='.$state[$field]);
return require dirname(__DIR__).'/public/router.php';
