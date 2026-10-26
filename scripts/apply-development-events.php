<?php
declare(strict_types=1);
// Explicit local-only migration command. Never called from HTTP/startup.
if(PHP_SAPI!=='cli'||!in_array($argv[1]??'', ['application','fixture'],true))throw new RuntimeException('Expected application or fixture CLI target.');
putenv('ATHAR_LOAD_DOTENV=0');
require dirname(__DIR__).'/backend/bootstrap.php';
use Athar\Infrastructure\MigrationRunner;

$repo=dirname(__DIR__);
$runtime=json_decode((string)file_get_contents($repo.'/storage/local-runtime.json'),true,512,JSON_THROW_ON_ERROR);
$pointer=json_decode((string)file_get_contents($repo.'/storage/engineering/recovery-pointer.json'),true,512,JSON_THROW_ON_ERROR);
$expected=realpath((string)$pointer['HealthyDataDirectory']);$privateRoot=realpath((string)$pointer['RecoveryRoot']);
$normalize=static fn(string $path):string=>strtolower(str_replace('\\','/',$path));
if(!$expected||!$privateRoot||$normalize($expected)!==$normalize($privateRoot.'/healthy')||str_starts_with($normalize($privateRoot),$normalize($repo).'/'))throw new RuntimeException('Private healthy data/backup directory guard failed.');
$port=(int)$runtime['mysqlPort'];
if($port!==3309)throw new RuntimeException('Only the documented healthy development port is accepted.');
$user=getenv('ATHAR_MIGRATION_USER');$password=getenv('ATHAR_MIGRATION_PASSWORD');
if(!$user||$password===false)throw new RuntimeException('Set explicit local migration credentials in the process environment.');
$schema='athar';
if($argv[1]==='fixture'){
    $fixture=json_decode((string)file_get_contents($repo.'/storage/engineering/native-fixture.json'),true,512,JSON_THROW_ON_ERROR);
    if(($fixture['fixture_only']??false)!==true||!preg_match('/^athar_test_native_[a-f0-9]{12}$/D',$fixture['database']??'')||$normalize($fixture['data_directory']??'')!==$normalize($expected))throw new RuntimeException('Generated fixture guard failed.');
    $schema=$fixture['database'];
}
$db=new PDO('mysql:host=127.0.0.1;port='.$port.';dbname='.$schema.';charset=utf8mb4',$user,$password,[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
$actual=realpath((string)$db->query('SELECT @@datadir')->fetchColumn());
if(!$actual||$normalize($actual)!==$normalize($expected))throw new RuntimeException('Unexpected database server. Nothing was migrated.');
$db->exec("SET time_zone='+00:00'");
$versions=$db->query('SELECT version FROM schema_migrations')->fetchAll(PDO::FETCH_COLUMN);
$pending=[];foreach(glob($repo.'/database/migrations/*.sql')?:[] as $file){$version=basename($file,'.sql');if(!in_array($version,$versions,true))$pending[]=$version;}sort($pending);
if(!$pending){echo "Development event migration already applied. No changes.\n";exit;}
if($pending!==['014_lesson_events'])throw new RuntimeException('Unexpected pending migrations. Review them separately; nothing was migrated.');
$tables=$db->query("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name")->fetchAll(PDO::FETCH_COLUMN);
$backup=$privateRoot.'/before-events-014-'.($argv[1]).'-'.gmdate('Ymd-His').'-'.bin2hex(random_bytes(3)).'.sql';
$dump=dirname((string)$runtime['mysql']).'/mysqldump.exe';
if(!is_file($dump))throw new RuntimeException('Configured local dump executable is unavailable.');
$environment=getenv();$environment['MYSQL_PWD']=$password;
$process=proc_open([$dump,'--no-defaults','--host=127.0.0.1','--port='.$port,'--user='.$user,'--single-transaction','--skip-lock-tables','--default-character-set=utf8mb4','--result-file='.$backup,$schema],[0=>['pipe','r'],1=>['file',$backup.'.diagnostic','a'],2=>['file',$backup.'.diagnostic','a']],$pipes,$repo,$environment);
if(!is_resource($process))throw new RuntimeException('Could not start a private logical backup.');
fclose($pipes[0]);$result=proc_close($process);
if($result!==0||!is_file($backup)||filesize($backup)<100)throw new RuntimeException('Private logical backup failed; no migration applied.');
$dumpText=file_get_contents($backup);
if(!is_string($dumpText)||strpos($dumpText,'-- Dump completed on ')===false||preg_match_all('/^CREATE TABLE `/m',$dumpText)!==count($tables))throw new RuntimeException('Backup completeness validation failed; no migration applied.');
$hash=hash_file('sha256',$backup);
if(!$hash||strlen($hash)!==64)throw new RuntimeException('Backup checksum verification failed.');
$applied=MigrationRunner::apply($db,$repo.'/database/migrations');
if($applied!==['014_lesson_events'])throw new RuntimeException('Unexpected migration result. Inspect the private version ledger.');
foreach(['lesson_events','lesson_translations','lesson_event_changes'] as $table){
    $check=$db->query('CHECK TABLE `'.$table.'`')->fetchAll();
    if(!$check||end($check)['Msg_text']!=='OK')throw new RuntimeException('New table check failed. The private logical snapshot is retained.');
}
$proof=['target'=>$argv[1],'migration'=>$applied[0],'backup_path'=>$backup,'sha256'=>$hash,'source_table_count'=>count($tables),'new_tables_checked'=>3,'restore_tested'=>false];
file_put_contents($repo.'/storage/engineering/events-014-'.$argv[1].'-proof.json',json_encode($proof,JSON_PRETTY_PRINT|JSON_THROW_ON_ERROR));
echo "Private logical backup checksum/table-coverage verified; migration014 applied to the explicit development target;3 new tables checked. Full restore acceptance is separate.\n";
