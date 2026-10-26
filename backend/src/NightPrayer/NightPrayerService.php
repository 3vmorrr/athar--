<?php
declare(strict_types=1);
namespace Athar\NightPrayer;

use Athar\Domain\NightPrayer;
use Athar\Domain\QuranIndex;
use Athar\Http\ApiError;
use DateTimeImmutable;
use DateTimeZone;
use PDO;
use Throwable;

final class NightPrayerService
{
    private PDO $db;
    private string $key;
    public function __construct(PDO $db,string $key) { $this->db=$db;$this->key=$key; }

    public function publicConfig(): array
    {
        $defaults=[
            'night_prayer_enabled'=>'1',
            'night_prayer_public_revision'=>'1',
            'night_prayer_default_calculation_method'=>'MuslimWorldLeague',
            'night_prayer_default_madhab'=>'standard',
            'night_prayer_default_high_latitude_rule'=>'middle_of_night',
            'night_prayer_default_target_verses'=>'100',
            'night_prayer_source_name_ar'=>'مصدر مواقيت الصلاة',
            'night_prayer_source_name_en'=>'Prayer times source',
            'night_prayer_source_url'=>'https://pray.api.islamic.network',
            'night_prayer_intro_ar'=>'قيام الليل عبادة تطوعية، وهذه الأدوات للمساعدة في التنظيم دون حكم على العبادة.',
            'night_prayer_intro_en'=>'Night prayer is voluntary worship. These tools support planning without judging worship.',
        ];
        $keys=array_keys($defaults);
        $placeholders=implode(',',array_fill(0,count($keys),'?'));
        $q=$this->db->prepare('SELECT setting_key,setting_value FROM platform_settings WHERE setting_key IN ('.$placeholders.')');
        $q->execute($keys);
        $stored=[];foreach($q->fetchAll() as $row)$stored[(string)$row['setting_key']]=(string)$row['setting_value'];
        $value=static fn(string $key):string=>$stored[$key]??$defaults[$key];
        return [
            'enabled'=>$value('night_prayer_enabled')!=='0',
            'revision'=>max(1,(int)$value('night_prayer_public_revision')),
            'defaults'=>[
                'calculation_method'=>$value('night_prayer_default_calculation_method'),
                'madhab'=>$value('night_prayer_default_madhab'),
                'high_latitude_rule'=>$value('night_prayer_default_high_latitude_rule'),
                'target_verses'=>max(1,min(6236,(int)$value('night_prayer_default_target_verses'))),
            ],
            'source'=>[
                'name_ar'=>$value('night_prayer_source_name_ar'),
                'name_en'=>$value('night_prayer_source_name_en'),
                'url'=>$value('night_prayer_source_url'),
            ],
            'intro'=>[
                'ar'=>$value('night_prayer_intro_ar'),
                'en'=>$value('night_prayer_intro_en'),
            ],
        ];
    }

    public function write(int $user,string $id,array $input,bool $delete=false): array
    {
        $id=$this->uuid($id);$mutation=$this->uuid((string)($input['mutation_id']??''));
        if(isset($input['id'])&&(!is_string($input['id'])||$this->uuid($input['id'])!==$id))throw new ApiError('invalid_fields',422,['id'=>'does_not_match_path']);
        $expected=$input['expected_revision']??null;
        if(!is_int($expected)||$expected<0)throw new ApiError('invalid_fields',422,['expected_revision'=>'invalid']);
        $data=$delete?[]:$this->validate($input);
        if($delete)foreach(array_keys($input) as $field)if(!in_array($field,['mutation_id','expected_revision'],true))throw new ApiError('invalid_fields',422,[$field=>'unsupported']);
        $hash=hash('sha256',json_encode(['id'=>$id,'delete'=>$delete,'expected'=>$expected,'data'=>$data],JSON_THROW_ON_ERROR));
        $this->db->beginTransaction();
        try {
            $q=$this->db->prepare('SELECT request_hash,result_json FROM night_prayer_mutations WHERE user_id=? AND mutation_id=? FOR UPDATE');$q->execute([$user,$mutation]);$previous=$q->fetch();
            if($previous){if(!hash_equals($previous['request_hash'],$hash))throw new ApiError('conflict',409,['mutation_id'=>'reused']);$this->db->commit();return json_decode($previous['result_json'],true,512,JSON_THROW_ON_ERROR);}
            $q=$this->db->prepare('SELECT * FROM night_prayer_sessions WHERE user_id=? AND id=? FOR UPDATE');$q->execute([$user,$id]);$current=$q->fetch();
            if(($current?(int)$current['revision']:0)!==$expected)throw new ApiError('conflict',409,['current'=>$current?$this->dto($current):null]);
            if($delete&&!$current)throw new ApiError('not_found',404);
            if($current&&(bool)$current['is_deleted']&&!$delete)throw new ApiError('conflict',409,['current'=>$this->dto($current)]);
            if(!$delete){$enabled=$this->db->query("SELECT setting_value FROM platform_settings WHERE setting_key='night_prayer_enabled'")->fetchColumn();if($enabled==='0')throw new ApiError('service_unavailable',503);}
            $revision=$expected+1;
            if($delete){
                $this->db->prepare('UPDATE night_prayer_sessions SET night_date=NULL,timezone=NULL,boundary_source=NULL,calculation_method=NULL,maghrib_at=NULL,fajr_at=NULL,started_at=NULL,ended_at=NULL,entry_type=NULL,start_index=NULL,end_index=NULL,verse_count=0,measured_seconds=NULL,note_cipher=NULL,is_deleted=1,revision=?,updated_at=UTC_TIMESTAMP() WHERE user_id=? AND id=?')->execute([$revision,$user,$id]);
            }else{
                $values=[$data['night_date'],$data['timezone'],$data['boundary_source'],$data['calculation_method'],$data['maghrib_at'],$data['fajr_at'],$data['started_at'],$data['ended_at'],$data['entry_type'],$data['start_index'],$data['end_index'],$data['verse_count'],$data['measured_seconds'],$data['note']===''?null:$this->seal($user,$data['note'])];
                if(!$current)$this->db->prepare('INSERT INTO night_prayer_sessions (user_id,id,night_date,timezone,boundary_source,calculation_method,maghrib_at,fajr_at,started_at,ended_at,entry_type,start_index,end_index,verse_count,measured_seconds,note_cipher,revision,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP())')->execute(array_merge([$user,$id],$values,[$revision]));
                else $this->db->prepare('UPDATE night_prayer_sessions SET night_date=?,timezone=?,boundary_source=?,calculation_method=?,maghrib_at=?,fajr_at=?,started_at=?,ended_at=?,entry_type=?,start_index=?,end_index=?,verse_count=?,measured_seconds=?,note_cipher=?,revision=?,updated_at=UTC_TIMESTAMP() WHERE user_id=? AND id=?')->execute(array_merge($values,[$revision,$user,$id]));
            }
            $this->db->prepare('INSERT INTO night_prayer_changes (user_id,session_id,revision,changed_at) VALUES (?,?,?,UTC_TIMESTAMP())')->execute([$user,$id,$revision]);
            // Replay records intentionally contain no note, range, date or private session context.
            $result=['id'=>$id,'revision'=>$revision,'deleted'=>$delete];
            $this->db->prepare('INSERT INTO night_prayer_mutations (user_id,mutation_id,request_hash,result_json,created_at) VALUES (?,?,?,?,UTC_TIMESTAMP())')->execute([$user,$mutation,$hash,json_encode($result,JSON_THROW_ON_ERROR)]);
            $this->db->commit();return $result;
        }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }

    public function get(int $user,string $id): array
    {
        $q=$this->db->prepare('SELECT * FROM night_prayer_sessions WHERE user_id=? AND id=?');$q->execute([$user,$this->uuid($id)]);$row=$q->fetch();if(!$row)throw new ApiError('not_found',404);return $this->dto($row);
    }

    public function sessions(int $user,array $params): array
    {
        $page=max(1,min(10000,(int)($params['page']??1)));$size=max(1,min(50,(int)($params['per_page']??20)));$where='user_id=? AND is_deleted=0';$values=[$user];
        foreach(['from'=>'>=','to'=>'<='] as $field=>$operator)if(isset($params[$field])){if(!NightPrayer::validDate((string)$params[$field]))throw new ApiError('invalid_fields',422,[$field=>'invalid']);$where.=' AND night_date'.$operator.'?';$values[]=$params[$field];}
        $q=$this->db->prepare('SELECT COUNT(*) FROM night_prayer_sessions WHERE '.$where);$q->execute($values);$total=(int)$q->fetchColumn();
        $q=$this->db->prepare('SELECT * FROM night_prayer_sessions WHERE '.$where.' ORDER BY night_date DESC,COALESCE(ended_at,started_at,created_at) DESC,id LIMIT '.$size.' OFFSET '.(($page-1)*$size));$q->execute($values);
        return ['items'=>array_map([$this,'dto'],$q->fetchAll()),'pagination'=>['page'=>$page,'per_page'=>$size,'total'=>$total,'has_more'=>$page*$size<$total]];
    }

    public function changes(int $user,int $cursor,int $limit=100): array
    {
        if($cursor<0)throw new ApiError('invalid_fields',422,['cursor'=>'invalid']);$limit=max(1,min(100,$limit));
        $q=$this->db->prepare('SELECT sequence_id,session_id FROM night_prayer_changes WHERE user_id=? AND sequence_id>? ORDER BY sequence_id LIMIT '.($limit+1));$q->execute([$user,$cursor]);$rows=$q->fetchAll();$more=count($rows)>$limit;$rows=array_slice($rows,0,$limit);$items=[];$next=$cursor;
        foreach($rows as $row){$next=(int)$row['sequence_id'];$items[$row['session_id']]=$this->get($user,$row['session_id']);}
        return ['changes'=>array_values($items),'next_cursor'=>$next,'has_more'=>$more];
    }

    public function sync(int $user,array $input): array
    {
        foreach(array_keys($input) as $field)if(!in_array($field,['cursor','operations'],true))throw new ApiError('invalid_fields',422,[$field=>'unsupported']);
        $operations=$input['operations']??[];$cursor=$input['cursor']??0;if(!is_array($operations)||count($operations)>50||!is_int($cursor)||$cursor<0)throw new ApiError('invalid_fields',422);
        $results=[];
        foreach($operations as $operation){if(!is_array($operation))throw new ApiError('invalid_fields',422,['operations'=>'invalid']);foreach(array_keys($operation) as $field)if(!in_array($field,['id','operation','data'],true))throw new ApiError('invalid_fields',422,['operations'=>'unsupported_field']);$id=(string)($operation['id']??'');$kind=$operation['operation']??'';$body=$operation['data']??[];if(!in_array($kind,['put','delete'],true)||!is_array($body))throw new ApiError('invalid_fields',422,['operations'=>'invalid']);
            try{$result=$this->write($user,$id,$body,$kind==='delete');$results[]=['id'=>$id,'status'=>'applied','result'=>$result];}
            catch(ApiError $e){$results[]=['id'=>$id,'status'=>'rejected','error'=>['code'=>$e->getMessage(),'status'=>$e->status,'fields'=>(object)$e->fields]];}
        }
        return ['results'=>$results]+$this->changes($user,$cursor);
    }

    public function analytics(int $user,?string $currentNight=null,bool $nightInProgress=false): array
    {
        if($currentNight!==null&&!NightPrayer::validDate($currentNight))throw new ApiError('invalid_fields',422,['current_night'=>'invalid']);
        $q=$this->db->prepare('SELECT night_date,SUM(verse_count) verse_count,COUNT(*) session_count FROM night_prayer_sessions WHERE user_id=? AND is_deleted=0 GROUP BY night_date ORDER BY night_date');$q->execute([$user]);$nights=$q->fetchAll();$sum=0;$sessions=0;$best=null;
        foreach($nights as &$night){$night['verse_count']=(int)$night['verse_count'];$night['session_count']=(int)$night['session_count'];$night['milestone']=NightPrayer::milestone($night['verse_count']);$sum+=$night['verse_count'];$sessions+=$night['session_count'];if($best===null||$night['verse_count']>$best['verse_count'])$best=$night;}unset($night);
        $streaks=NightPrayer::streaks($nights,$currentNight??($nights?end($nights)['night_date']:gmdate('Y-m-d')),$nightInProgress);
        if($currentNight===null)foreach($streaks as &$streak)$streak['current']=null;unset($streak);
        $q=$this->db->prepare(
            'SELECT verse_count,measured_seconds
             FROM night_prayer_sessions
             WHERE user_id=?
               AND is_deleted=0
               AND measured_seconds>0'
        );
        $q->execute([$user]);
        $pace=$this->paceInsight($q->fetchAll());
        $q=$this->db->prepare("SELECT end_index FROM night_prayer_sessions WHERE user_id=? AND is_deleted=0 AND entry_type='range' ORDER BY COALESCE(ended_at,started_at,created_at) DESC,id DESC LIMIT 1");$q->execute([$user]);$end=$q->fetchColumn();$continuation=$end===false?null:((int)$end>=6236?['restart_required'=>true,'next'=>null]:['restart_required'=>false,'next'=>QuranIndex::standard()->position((int)$end+1)]);
        $tonight=null;if($currentNight!==null){$tonight=['night_date'=>$currentNight,'verse_count'=>0,'session_count'=>0];foreach($nights as $night)if($night['night_date']===$currentNight)$tonight=$night;$tonight['milestone']=NightPrayer::milestone($tonight['verse_count']);}
        return ['total_nights'=>count($nights),'total_sessions'=>$sessions,'total_verses'=>$sum,'average_verses_per_active_night'=>$nights?$sum/count($nights):0,'best_night'=>$best,'tonight'=>$tonight,'streaks'=>$streaks,'reading_pace'=>$pace,'continuation'=>$continuation,'nights'=>$nights,'night_context_required'=>$currentNight===null];
    }

    public function preferences(int $user,?array $input=null): array
    {
        $q=$this->db->prepare('SELECT preferences_json,revision FROM night_prayer_preferences WHERE user_id=?');$q->execute([$user]);$row=$q->fetch();$current=$row?json_decode($row['preferences_json'],true,512,JSON_THROW_ON_ERROR):[];$revision=$row?(int)$row['revision']:0;
        if($input!==null){$expected=$input['expected_revision']??null;if(!is_int($expected)||$expected!==$revision)throw new ApiError('conflict',409,['current_revision'=>$revision]);
            $allowed=['timezone','city','latitude','longitude','calculation_method','madhab','high_latitude_rule'];
            foreach($input as $field=>$value){if($field==='expected_revision')continue;if(!in_array($field,$allowed,true))throw new ApiError('invalid_fields',422,[$field=>'unsupported']);if(in_array($field,['latitude','longitude'],true)){if((!is_float($value)&&!is_int($value))||!is_finite((float)$value)||abs($value)>($field==='latitude'?90:180))throw new ApiError('invalid_fields',422,[$field=>'invalid']);}elseif(!is_string($value)||mb_strlen($value,'UTF-8')>80)throw new ApiError('invalid_fields',422,[$field=>'invalid']);if($field==='timezone')$this->timezone($value);$current[$field]=$value;}
            $q=$this->db->prepare('INSERT INTO night_prayer_preferences (user_id,preferences_json,revision,updated_at) VALUES (?,?,1,UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE preferences_json=IF(revision=?,VALUES(preferences_json),preferences_json),updated_at=IF(revision=?,UTC_TIMESTAMP(),updated_at),revision=IF(revision=?,revision+1,revision)');$q->execute([$user,json_encode($current,JSON_THROW_ON_ERROR),$expected,$expected,$expected]);
            if(!$q->rowCount())throw new ApiError('conflict',409);$revision++;
        }
        return ['values'=>(object)$current,'revision'=>$revision];
    }

    public function dto(array $row): array
    {
        $result=['id'=>$row['id'],'revision'=>(int)$row['revision'],'deleted'=>(bool)$row['is_deleted']];if($result['deleted'])return $result;
        foreach(['night_date','timezone','boundary_source','calculation_method','entry_type'] as $field)$result[$field]=$row[$field];
        foreach(['start_index','end_index','measured_seconds'] as $field)$result[$field]=$row[$field]===null?null:(int)$row[$field];$result['verse_count']=(int)$row['verse_count'];
        foreach(['maghrib_at','fajr_at','started_at','ended_at','created_at','updated_at'] as $field)$result[$field]=$row[$field]===null?null:str_replace(' ','T',$row[$field]).'Z';
        $result['note']=$row['note_cipher']===null?'':$this->open((int)$row['user_id'],$row['note_cipher']);return $result;
    }

    private function paceInsight(array $rows):?array
    {
        $valid=[];
        foreach($rows as $row){
            $verses=(int)($row['verse_count']??0);
            $seconds=(int)($row['measured_seconds']??0);
            if($verses<1||$verses>100000||$seconds<1||$seconds>86400)continue;
            $valid[]=[
                'verses'=>$verses,
                'seconds'=>$seconds,
                'rate'=>$seconds/$verses,
            ];
        }
        $sampleCount=count($valid);
        if($sampleCount<2)return null;

        $used=$valid;
        if($sampleCount>=5){
            $rates=array_column($valid,'rate');
            sort($rates,SORT_NUMERIC);
            $percentile=static function(array $values,float $fraction):float{
                $last=count($values)-1;
                $index=$last*$fraction;
                $lower=(int)floor($index);
                $upper=(int)ceil($index);
                if($lower===$upper)return (float)$values[$lower];
                $weight=$index-$lower;
                return (float)$values[$lower]*(1.0-$weight)
                    +(float)$values[$upper]*$weight;
            };
            $q1=$percentile($rates,0.25);
            $q3=$percentile($rates,0.75);
            $iqr=max(0.0,$q3-$q1);
            $low=$q1-1.5*$iqr;
            $high=$q3+1.5*$iqr;
            $filtered=array_values(array_filter(
                $valid,
                static fn(array $row):bool=>$row['rate']>=$low&&$row['rate']<=$high
            ));
            if(count($filtered)>=2)$used=$filtered;
        }

        $seconds=0;
        $verses=0;
        foreach($used as $row){
            $seconds+=(int)$row['seconds'];
            $verses+=(int)$row['verses'];
        }
        if($seconds<=0||$verses<=0)return null;

        return [
            'sample_count'=>$sampleCount,
            'used_sample_count'=>count($used),
            'measured_seconds'=>$seconds,
            'measured_verses'=>$verses,
            'seconds_per_verse'=>$seconds/$verses,
        ];
    }

    private function validate(array $input): array
    {
        $allowed=['id','mutation_id','expected_revision','night_date','timezone','boundary_source','calculation_method','maghrib_at','fajr_at','started_at','ended_at','entry_type','start_index','end_index','verse_count','measured_seconds','note'];
        foreach(array_keys($input) as $field)if(!in_array($field,$allowed,true))throw new ApiError('invalid_fields',422,[$field=>'unsupported']);
        $date=$input['night_date']??'';$timezone=$input['timezone']??'';if(!is_string($date)||!NightPrayer::validDate($date)||$date<'1900-01-01'||$date>gmdate('Y-m-d',time()+2*86400))throw new ApiError('invalid_fields',422,['night_date'=>'invalid']);if(!is_string($timezone))throw new ApiError('invalid_fields',422,['timezone'=>'invalid']);$zone=$this->timezone($timezone);
        if($date>(new DateTimeImmutable('now',$zone))->format('Y-m-d'))throw new ApiError('invalid_fields',422,['night_date'=>'future']);
        $source=$input['boundary_source']??'manual';if(!in_array($source,['manual','calculated'],true))throw new ApiError('invalid_fields',422,['boundary_source'=>'invalid']);$method=$input['calculation_method']??null;if($method!==null&&(!is_string($method)||strlen($method)>40))throw new ApiError('invalid_fields',422,['calculation_method'=>'invalid']);
        $times=[];foreach(['maghrib_at','fajr_at','started_at','ended_at'] as $field)$times[$field]=$this->instant($input[$field]??null,$field);
        if(($times['maghrib_at']===null)!==($times['fajr_at']===null)||($source==='calculated'&&$times['maghrib_at']===null))throw new ApiError('invalid_fields',422,['boundaries'=>'missing']);
        if($times['maghrib_at']){NightPrayer::lastThird($times['maghrib_at'],$times['fajr_at']);if($times['maghrib_at']->setTimezone($zone)->format('Y-m-d')!==$date)throw new ApiError('invalid_fields',422,['night_date'=>'does_not_match_maghrib']);}
        if($times['ended_at']&&!$times['started_at'])throw new ApiError('invalid_fields',422,['started_at'=>'required']);
        if($times['started_at']&&$times['started_at']->getTimestamp()>time()+300)throw new ApiError('invalid_fields',422,['started_at'=>'future']);
        if($times['ended_at']&&($times['ended_at']<$times['started_at']||$times['ended_at']->getTimestamp()>time()+300||$times['ended_at']->getTimestamp()-$times['started_at']->getTimestamp()>86400))throw new ApiError('invalid_fields',422,['ended_at'=>'invalid']);
        if($times['maghrib_at']&&$times['started_at']&&($times['started_at']<$times['maghrib_at']||$times['started_at']>$times['fajr_at']||($times['ended_at']&&$times['ended_at']>$times['fajr_at'])))throw new ApiError('invalid_fields',422,['session_time'=>'outside_selected_night']);
        $type=$input['entry_type']??'';$start=null;$end=null;
        if($type==='range'){$start=$this->integer($input,'start_index',1,6236);$end=$this->integer($input,'end_index',$start,6236);$count=$end-$start+1;if(isset($input['verse_count'])&&$input['verse_count']!==$count)throw new ApiError('invalid_fields',422,['verse_count'=>'range_mismatch']);}
        elseif($type==='manual'){$count=$this->integer($input,'verse_count',1,100000);if(isset($input['start_index'])||isset($input['end_index']))throw new ApiError('invalid_fields',422,['range'=>'not_applicable']);}
        else throw new ApiError('invalid_fields',422,['entry_type'=>'invalid']);
        $seconds=isset($input['measured_seconds'])?$this->integer($input,'measured_seconds',1,86400):null;
        if($seconds!==null&&$times['ended_at']&&$seconds>$times['ended_at']->getTimestamp()-$times['started_at']->getTimestamp()+5)throw new ApiError('invalid_fields',422,['measured_seconds'=>'exceeds_elapsed_time']);
        $note=$input['note']??'';if(!is_string($note)||mb_strlen($note,'UTF-8')>1000)throw new ApiError('invalid_fields',422,['note'=>'invalid']);
        $result=['night_date'=>$date,'timezone'=>$timezone,'boundary_source'=>$source,'calculation_method'=>$method];foreach($times as $field=>$time)$result[$field]=$time?$time->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s'):null;
        return $result+['entry_type'=>$type,'start_index'=>$start,'end_index'=>$end,'verse_count'=>$count,'measured_seconds'=>$seconds,'note'=>$note];
    }
    private function integer(array $data,string $key,int $min,int $max):int{$value=$data[$key]??null;if(!is_int($value)||$value<$min||$value>$max)throw new ApiError('invalid_fields',422,[$key=>'invalid']);return $value;}
    private function uuid(string $id):string{if(!preg_match('/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i',$id))throw new ApiError('invalid_fields',422,['id'=>'invalid_uuid']);return strtolower($id);}
    private function timezone(string $name):DateTimeZone{static $zones;$zones??=array_fill_keys(DateTimeZone::listIdentifiers(),true);if($name!=='UTC'&&!isset($zones[$name]))throw new ApiError('invalid_fields',422,['timezone'=>'invalid']);return new DateTimeZone($name);}
    private function instant($value,string $field):?DateTimeImmutable
    {
        if($value===null)return null;if(!is_string($value)||!preg_match('/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?(?:Z|[+-]\d{2}:\d{2})$/',$value))throw new ApiError('invalid_fields',422,[$field=>'invalid_timestamp']);
        try{$time=new DateTimeImmutable($value);$errors=DateTimeImmutable::getLastErrors();if($errors&&($errors['warning_count']||$errors['error_count']))throw new \RuntimeException('Invalid timestamp.');return $time;}catch(Throwable $e){throw new ApiError('invalid_fields',422,[$field=>'invalid_timestamp']);}
    }
    private function userKey(int $user):string{return hash_hmac('sha256','night-prayer-note:'.$user,$this->key,true);}
    private function seal(int $user,string $note):string{$iv=random_bytes(12);$tag='';$cipher=openssl_encrypt($note,'aes-256-gcm',$this->userKey($user),OPENSSL_RAW_DATA,$iv,$tag,'night-prayer');if($cipher===false)throw new \RuntimeException('Private note encryption failed.');return base64_encode($iv.$tag.$cipher);}
    private function open(int $user,string $sealed):string{$raw=base64_decode($sealed,true);if(!is_string($raw)||strlen($raw)<29)throw new \RuntimeException('Private note unavailable.');$plain=openssl_decrypt(substr($raw,28),'aes-256-gcm',$this->userKey($user),OPENSSL_RAW_DATA,substr($raw,0,12),substr($raw,12,16),'night-prayer');if(!is_string($plain))throw new \RuntimeException('Private note unavailable.');return $plain;}
}
