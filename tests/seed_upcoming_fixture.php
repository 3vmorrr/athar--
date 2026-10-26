<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
putenv('ATHAR_LOAD_DOTENV=0');
require dirname(__DIR__).'/backend/bootstrap.php';
$repo=dirname(__DIR__);
$state=json_decode((string)file_get_contents($repo.'/storage/engineering/native-fixture.json'),true,512,JSON_THROW_ON_ERROR);
if(($state['fixture_only']??false)!==true||!preg_match('/^athar_test_native_[a-f0-9]{12}$/D',$state['database']??'')||($state['host']??'')!=='127.0.0.1'||(int)($state['port']??0)!==3309)throw new RuntimeException('Only the generated local fixture is accepted.');
foreach(['HOST'=>'host','PORT'=>'port','NAME'=>'database','USER'=>'user','PASSWORD'=>'password'] as $key=>$field)putenv('ATHAR_DB_'.$key.'='.$state[$field]);
$db=\Athar\Infrastructure\Database::connect();
$actual=realpath((string)$db->query('SELECT @@datadir')->fetchColumn());
if(!$actual||strcasecmp($actual,(string)$state['data_directory'])!==0)throw new RuntimeException('Fixture server guard failed.');
$q=$db->query("SELECT id FROM users WHERE email='native.admin@example.test'");$adminId=$q->fetchColumn();
if($adminId===false){
    $db->prepare("INSERT INTO users(email,username,display_name,password_hash,role,email_verified_at) VALUES('native.admin@example.test','native_admin','Interface test admin',?,'admin',UTC_TIMESTAMP())")->execute([password_hash('NativeFixture123!',PASSWORD_DEFAULT)]);
    $adminId=(int)$db->lastInsertId();
}
$q=$db->query("SELECT id FROM lessons WHERE youtube_id='Event000005'");$lesson=$q->fetchColumn();
if($lesson===false){
    $db->exec("INSERT INTO lessons(scholar_id,series_id,title,description,youtube_id,source_url,sort_order,is_published) VALUES(1,1,'درس اختباري قادم','بيانات اختبار وليست إعلانًا عن درس حقيقي.','Event000005','https://example.invalid/source',1000,0)");
    $lesson=(int)$db->lastInsertId();
}
$actor=['id'=>(int)$adminId,'role'=>'admin','email_verified_at'=>'2026-01-01','suspended_at'=>null];
$service=new \Athar\Learning\UpcomingLessonService($db);$revision=0;
try{
    $old=$service->detail((int)$lesson,'ar',$actor);$revision=$old['revision'];
    if(in_array($old['status'],['live','published'],true)){
        $step=$old['status']==='live'?'cancelled':'draft';
        $old=$service->save($actor,(int)$lesson,['expected_revision'=>$revision,'status'=>$step,'replay_available'=>false]);$revision=$old['revision'];
    }
}catch(\Athar\Http\ApiError $e){if($e->status!==404)throw $e;}
$row=$service->save($actor,(int)$lesson,['expected_revision'=>$revision,'status'=>'scheduled','scheduled_at'=>gmdate('Y-m-d\TH:i:s\Z',time()+15*60),'schedule_timezone'=>'UTC','is_live_event'=>true,'reminders_enabled'=>true,'replay_available'=>false,'visibility'=>'public','title_en'=>'Upcoming interface fixture','description_en'=>'Development fixture, not a real broadcast.']);
$book=$db->query("SELECT id FROM books WHERE title='Interface test book' AND is_published=1")->fetchColumn();
if($book===false)throw new RuntimeException('Expected generated book fixture, not real catalogue data.');
$row=$service->save($actor,(int)$lesson,['expected_revision'=>$row['revision'],'course_title_ar'=>'دورة واجهة تجريبية','course_title_en'=>'Interface fixture course','topics_ar'=>['موضوع اختباري'],'topics_en'=>['Fixture topic'],'related_book_id'=>(int)$book,'related_page_start'=>10,'related_page_end'=>20]);
file_put_contents($repo.'/storage/engineering/upcoming-fixture.json',json_encode(['lesson_id'=>$row['id'],'admin_id'=>(int)$adminId,'revision'=>$row['revision'],'fixture_only'=>true],JSON_PRETTY_PRINT|JSON_THROW_ON_ERROR));
echo "Seeded only the generated upcoming fixture; no real lesson, recipient or campaign changed.\n";
