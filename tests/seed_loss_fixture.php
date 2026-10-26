<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
putenv('ATHAR_LOAD_DOTENV=0');
require dirname(__DIR__).'/backend/bootstrap.php';
$state=json_decode((string)file_get_contents(dirname(__DIR__).'/storage/engineering/native-fixture.json'),true,512,JSON_THROW_ON_ERROR);
if(($state['fixture_only']??false)!==true||!preg_match('/^athar_test_native_[a-f0-9]{12}$/D',$state['database']??'')||($state['host']??'')!=='127.0.0.1'||(int)($state['port']??0)!==3309)throw new RuntimeException('Generated local fixture only.');
foreach(['HOST'=>'host','PORT'=>'port','NAME'=>'database','USER'=>'user','PASSWORD'=>'password'] as $key=>$field)putenv('ATHAR_DB_'.$key.'='.$state[$field]);
$db=\Athar\Infrastructure\Database::connect();
$actual=realpath((string)$db->query('SELECT @@datadir')->fetchColumn());
if(!$actual||strcasecmp($actual,(string)$state['data_directory'])!==0)throw new RuntimeException('Unexpected fixture database server.');
$suffix=bin2hex(random_bytes(6));$email='native.loss.'.$suffix.'@example.test';
$db->prepare("INSERT INTO users(email,username,display_name,password_hash,role,email_verified_at) VALUES(?,?,?,?,'user',UTC_TIMESTAMP())")->execute([$email,'loss_'.$suffix,'Automated lost-response fixture',password_hash('NativeFixture123!',PASSWORD_DEFAULT)]);
file_put_contents(dirname(__DIR__).'/storage/engineering/loss-fixture.json',json_encode(['fixture_only'=>true,'email'=>$email],JSON_THROW_ON_ERROR),LOCK_EX);
echo "Fresh generated lost-response fixture ready; no real account or mail changed.\n";
