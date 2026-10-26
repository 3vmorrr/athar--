<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
putenv('ATHAR_LOAD_DOTENV=0');
require dirname(__DIR__).'/backend/bootstrap.php';
$root=dirname(__DIR__);
$state=json_decode((string)file_get_contents($root.'/storage/engineering/native-fixture.json'),true,512,JSON_THROW_ON_ERROR);
$receipt=json_decode((string)file_get_contents($root.'/storage/engineering/os-lifecycle-device.json'),true,512,JSON_THROW_ON_ERROR);
$actor=json_decode((string)file_get_contents($root.'/storage/engineering/loss-fixture.json'),true,512,JSON_THROW_ON_ERROR);
if(($state['fixture_only']??false)!==true||!preg_match('/^athar_test_native_[a-f0-9]{12}$/D',$state['database']??'')||($state['host']??'')!=='127.0.0.1'||(int)($state['port']??0)!==3309)throw new RuntimeException('Generated local fixture only.');
if(($receipt['fixture_only']??false)!==true||($actor['fixture_only']??false)!==true||($receipt['email']??'')!==($actor['email']??'')||!preg_match('/^native\.loss\.[a-f0-9]{12}@example\.test$/D',$receipt['email']??'')||!preg_match('/^[a-f0-9-]{36}$/D',$receipt['night_id']??''))throw new RuntimeException('Unexpected lifecycle fixture receipt.');
foreach(['HOST'=>'host','PORT'=>'port','NAME'=>'database','USER'=>'user','PASSWORD'=>'password'] as $key=>$field)putenv('ATHAR_DB_'.$key.'='.$state[$field]);
$db=\Athar\Infrastructure\Database::connect();
$actual=realpath((string)$db->query('SELECT @@datadir')->fetchColumn());
if(!$actual||strcasecmp($actual,(string)$state['data_directory'])!==0)throw new RuntimeException('Unexpected fixture database server.');
$q=$db->prepare('SELECT id FROM users WHERE email=?');$q->execute([$receipt['email']]);$owner=(int)$q->fetchColumn();
if($owner<=0||$owner!==($receipt['owner']??null))throw new RuntimeException('Unexpected fixture actor.');
$query=static function(string $sql,array $values)use($db){$q=$db->prepare($sql);$q->execute($values);return $q->fetch(PDO::FETCH_ASSOC);};
$reading=$query('SELECT surah,ayah,revision FROM quran_positions WHERE user_id=?',[$owner]);
$counter=$query("SELECT current_count,revision FROM adhkar_progress WHERE user_id=? AND item_key='curated:1'",[$owner]);
$lesson=$query('SELECT position_seconds,duration_seconds,is_complete FROM video_progress WHERE user_id=? AND lesson_id=2',[$owner]);
$night=$query('SELECT verse_count,revision,is_deleted FROM night_prayer_sessions WHERE user_id=? AND id=?',[$owner,$receipt['night_id']]);
$q=$db->prepare('SELECT COUNT(*) FROM activity_events WHERE user_id=?');$q->execute([$owner]);$events=(int)$q->fetchColumn();
$checks=[
    'reading'=>$reading&&(int)$reading['surah']===1&&(int)$reading['ayah']===2&&(int)$reading['revision']===1,
    'counter'=>$counter&&(int)$counter['current_count']===1&&(int)$counter['revision']===1,
    'lesson'=>$lesson&&(int)$lesson['position_seconds']===30&&(int)$lesson['duration_seconds']===100&&(int)$lesson['is_complete']===0,
    'night'=>$night&&(int)$night['verse_count']===12&&(int)$night['revision']===1&&(int)$night['is_deleted']===0,
    'no_inferred_activity'=>$events===0,
];
$empty=!$reading&&!$counter&&!$lesson&&!$night&&$events===0;
echo json_encode(['fixture_only'=>true,'stage'=>$receipt['stage'],'state'=>!in_array(false,$checks,true)?'delivered':($empty?'pending':'partial'),'checks'=>$checks],JSON_THROW_ON_ERROR).PHP_EOL;
