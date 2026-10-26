<?php
declare(strict_types=1);
require dirname(__DIR__).'/backend/bootstrap.php';
use Athar\Infrastructure\Database;
use Athar\Infrastructure\MigrationRunner;

if(PHP_SAPI!=='cli')exit(1);
$name=(string)getenv('ATHAR_DB_NAME');
$expected=realpath((string)getenv('ATHAR_TEST_DATA_DIR'));
if(!preg_match('/^athar_test_control_[a-f0-9]{12}$/',$name)||!$expected)throw new RuntimeException('Unsafe test environment.');
$db=Database::connect('mysql');
$actual=realpath((string)$db->query('SELECT @@datadir')->fetchColumn());
if(!$actual||strcasecmp(str_replace('\\','/',$expected),str_replace('\\','/',$actual))!==0)throw new RuntimeException('Connected server is not the dedicated test data directory.');
$action=$argv[1]??'';
if($action==='create'){$db->exec('CREATE DATABASE `'.$name.'` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');MigrationRunner::apply(Database::connect($name),dirname(__DIR__).'/database/migrations');}
elseif($action==='drop')$db->exec('DROP DATABASE IF EXISTS `'.$name.'`');
else throw new RuntimeException('Expected create or drop.');
