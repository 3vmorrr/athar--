<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
require dirname(__DIR__).'/backend/bootstrap.php';
use Athar\Infrastructure\Database;
use Athar\Infrastructure\MigrationRunner;

$action=$argv[1]??'';
if(!in_array($action,['create','drop'],true))throw new RuntimeException('Expected create or drop.');
if(!in_array(getenv('APP_ENV'),['local','development'],true)||(getenv('ATHAR_DB_HOST')?:'127.0.0.1')!=='127.0.0.1')throw new RuntimeException('Native fixtures require an explicit local development connection.');
$expected=realpath((string)getenv('ATHAR_TEST_DATA_DIR'));
if(!$expected)throw new RuntimeException('Set ATHAR_TEST_DATA_DIR to the intended local server data directory.');
$control=Database::connect('mysql');$actual=realpath((string)$control->query('SELECT @@datadir')->fetchColumn());
if(!$actual||strcasecmp(str_replace('\\','/',$expected),str_replace('\\','/',$actual))!==0)throw new RuntimeException('Unexpected database server; no fixture mutation performed.');
$statePath=dirname(__DIR__).'/storage/engineering/native-fixture.json';
if($action==='drop'){
    if(!is_file($statePath))throw new RuntimeException('No fixture state is available.');
    $state=json_decode(file_get_contents($statePath),true,512,JSON_THROW_ON_ERROR);
    if(!preg_match('/^athar_test_native_[a-f0-9]{12}$/',$state['database']??'')||!preg_match('/^athar_fixture_[a-f0-9]{12}$/',$state['user']??'')||strcasecmp($expected,$state['data_directory']??'')!==0)throw new RuntimeException('Fixture cleanup safety check failed.');
    $control->exec('DROP DATABASE `'.$state['database'].'`');
    $control->exec('DROP USER '.$control->quote($state['user'])."@'127.0.0.1'");
    unlink($statePath);echo "Removed only the generated native test schema/account.\n";exit;
}
if(is_file($statePath))throw new RuntimeException('A fixture is already registered. Do not overwrite it.');
$suffix=bin2hex(random_bytes(6));$schema='athar_test_native_'.$suffix;$username='athar_fixture_'.$suffix;$password=bin2hex(random_bytes(24));
$control->exec('CREATE DATABASE `'.$schema.'` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');
try {
$db=Database::connect($schema);MigrationRunner::apply($db,dirname(__DIR__).'/database/migrations');
$db->prepare("INSERT INTO users(email,username,display_name,password_hash,role,email_verified_at) VALUES ('native.reader@example.test','native_fixture','Interface test reader',?,'user',UTC_TIMESTAMP())")->execute([password_hash('NativeFixture123!',PASSWORD_DEFAULT)]);
$db->prepare("INSERT INTO users(email,username,display_name,password_hash,role,email_verified_at) VALUES ('native.other@example.test','other_fixture','Other interface reader',?,'user',UTC_TIMESTAMP())")->execute([password_hash('NativeFixture123!',PASSWORD_DEFAULT)]);
$db->exec("INSERT INTO scholars(name,biography,source_url,is_published) VALUES ('Interface test scholar','Automated interface fixture, not published religious content.','https://example.invalid/source',1)");
$db->exec("INSERT INTO series(scholar_id,title,description,is_published) VALUES (1,'Interface test series','Automated ordered-playlist fixture.',1)");
foreach([1,2,3] as $number)$db->prepare("INSERT INTO lessons(scholar_id,series_id,title,description,youtube_id,source_url,sort_order,is_published) VALUES (1,1,?,'Automated interface fixture.',?,'https://example.invalid/source',?,1)")->execute(['Interface test lesson '.$number,sprintf('!test%06d',$number),$number]);
$db->exec("INSERT INTO lesson_comments(lesson_id,user_id,body,status) VALUES(2,2,'Visible interface comment','visible'),(2,2,'Hidden pending fixture','pending')");
$db->exec("INSERT INTO lesson_comments(lesson_id,user_id,parent_id,body,status) VALUES(2,1,1,'Visible interface reply','visible')");
$db->exec("INSERT INTO lessons(scholar_id,title,description,youtube_id,source_url,sort_order,is_published) VALUES(1,'Official YouTube IFrame sample','Non-religious official provider test fixture.','M7lc1UVf-VE','https://developers.google.com/youtube/iframe_api_reference',99,1)");
$db->exec("INSERT INTO books(title,author,description,source_url,rights_basis,can_download,is_published) VALUES ('Interface test book','Interface test author','Automated interface fixture, not an actual published book.','https://example.invalid/source','Test fixture only',0,1)");
$db->exec("INSERT INTO adhkar_items(title,formula,source_reference,source_url,repeat_count,category,is_reviewed,is_published) VALUES ('Interface counter fixture','Interface counter fixture: not a supplication.','Automated test only','https://example.invalid/source',3,'Interface test',1,1)");
$db->exec("INSERT INTO adhkar_items(title,formula,source_reference,source_url,repeat_count,category,is_reviewed,is_published) VALUES ('Second counter fixture','Second interface fixture: not a supplication.','Automated test only','https://example.invalid/source',1,'Interface test',1,1),('Unspecified count fixture','Interface fixture without a source count.','Automated test only','https://example.invalid/source',NULL,'Other section',1,1)");
$control->exec('CREATE USER '.$control->quote($username)."@'127.0.0.1' IDENTIFIED BY ".$control->quote($password));
$control->exec('GRANT SELECT,INSERT,UPDATE,DELETE ON `'.$schema.'`.* TO '.$control->quote($username)."@'127.0.0.1'");
$state=['database'=>$schema,'user'=>$username,'password'=>$password,'host'=>'127.0.0.1','port'=>(int)(getenv('ATHAR_DB_PORT')?:3306),'data_directory'=>$expected,'fixture_only'=>true];
file_put_contents($statePath,json_encode($state,JSON_PRETTY_PRINT|JSON_THROW_ON_ERROR),LOCK_EX);@chmod($statePath,0600);
echo "Created isolated native interface fixtures; no existing user rows or email delivery touched.\n";
} catch (Throwable $error) {
    // These identifiers were generated in this invocation, never supplied by a caller.
    $control->exec('DROP DATABASE `'.$schema.'`');
    $control->exec('DROP USER IF EXISTS '.$control->quote($username)."@'127.0.0.1'");
    throw $error;
}
