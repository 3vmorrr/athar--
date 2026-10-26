<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
putenv('ATHAR_LOAD_DOTENV=0');
require dirname(__DIR__).'/backend/bootstrap.php';
$repo=dirname(__DIR__);
$state=json_decode((string)file_get_contents($repo.'/storage/engineering/native-fixture.json'),true,512,JSON_THROW_ON_ERROR);
if(($state['fixture_only']??false)!==true||!preg_match('/^athar_test_native_[a-f0-9]{12}$/D',$state['database']??'')||($state['host']??'')!=='127.0.0.1'||(int)($state['port']??0)!==3309)throw new RuntimeException('Generated local fixture only.');
foreach(['HOST'=>'host','PORT'=>'port','NAME'=>'database','USER'=>'user','PASSWORD'=>'password'] as $key=>$field)putenv('ATHAR_DB_'.$key.'='.$state[$field]);
$db=\Athar\Infrastructure\Database::connect();
$actual=realpath((string)$db->query('SELECT @@datadir')->fetchColumn());
if(!$actual||strcasecmp($actual,(string)$state['data_directory'])!==0)throw new RuntimeException('Unexpected fixture database server.');
$q=$db->query("SELECT id FROM users WHERE email='native.sync@example.test'");
if($q->fetchColumn()===false)$db->prepare("INSERT INTO users(email,username,display_name,password_hash,role,email_verified_at) VALUES('native.sync@example.test','native_sync','Automated sync fixture',?,'user',UTC_TIMESTAMP())")->execute([password_hash('NativeFixture123!',PASSWORD_DEFAULT)]);
echo "Dedicated generated sync fixture ready. No real account, password or email delivery changed.\n";
