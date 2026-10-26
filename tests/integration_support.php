<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/backend/bootstrap.php';
use Athar\Infrastructure\Database;
use Athar\Infrastructure\MigrationRunner;

function isolated_database(callable $test):void
{
    $controlName=(string)(getenv('ATHAR_DB_NAME')?:'');
    if(!preg_match('/^athar_test_[a-z0-9_]+$/',$controlName))throw new RuntimeException('Integration tests require a dedicated athar_test_* control database.');
    $control=Database::connect();$schema='athar_test_'.bin2hex(random_bytes(6));
    if(!preg_match('/^athar_test_[a-f0-9]{12}$/',$schema))throw new RuntimeException('Unsafe test database name.');
    $control->exec('CREATE DATABASE `'.$schema.'` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');
    try{$db=Database::connect($schema);MigrationRunner::apply($db,dirname(__DIR__).'/database/migrations');$test($db);}
    finally{$db=null;if(!preg_match('/^athar_test_[a-f0-9]{12}$/',$schema))throw new RuntimeException('Unsafe test cleanup.');$control->exec('DROP DATABASE `'.$schema.'`');}
}
function test_uuid():string{$bytes=random_bytes(16);$bytes[6]=chr((ord($bytes[6])&15)|64);$bytes[8]=chr((ord($bytes[8])&63)|128);$hex=bin2hex($bytes);return substr($hex,0,8).'-'.substr($hex,8,4).'-'.substr($hex,12,4).'-'.substr($hex,16,4).'-'.substr($hex,20);}
