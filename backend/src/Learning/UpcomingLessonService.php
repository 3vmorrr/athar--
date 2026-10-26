<?php
declare(strict_types=1);
namespace Athar\Learning;

use Athar\Http\ApiError;
use DateTimeImmutable;
use DateTimeZone;
use PDO;
use Throwable;

/** Public lesson scheduling facts only. Personal worship records are never queried here. */
final class UpcomingLessonService
{
    private PDO $db;
    private const STATES = ['draft','scheduled','live','published','cancelled'];
    private const TRANSITIONS = [
        'draft'=>['scheduled','live','published','cancelled'],
        'scheduled'=>['draft','live','published','cancelled'],
        'live'=>['published','cancelled'],
        'published'=>['draft'],
        'cancelled'=>['draft','scheduled'],
    ];
    public function __construct(PDO $db) { $this->db=$db; }

    private function publicScope(): string
    {
        return "e.visibility='public' AND e.status<>'draft'
            AND (l.scholar_id IS NULL OR s.is_published=1)
            AND (l.series_id IS NULL OR (se.is_published=1 AND (se.scholar_id IS NULL OR ss.is_published=1)))";
    }
    private function from(): string
    {
        return ' FROM lesson_events e JOIN lessons l ON l.id=e.lesson_id
            LEFT JOIN scholars s ON s.id=l.scholar_id LEFT JOIN series se ON se.id=l.series_id
            LEFT JOIN scholars ss ON ss.id=se.scholar_id ';
    }
    private function select(): string
    {
        return 'SELECT e.*,l.youtube_id,l.is_published,l.series_id,se.title series_title,
            COALESCE(l.scholar_id,se.scholar_id) scholar_id,COALESCE(s.name,ss.name) scholar_name,
            COALESCE(t.title,l.title) title,
            CASE WHEN t.lesson_id IS NULL THEN l.description ELSE t.description END description,
            CASE WHEN t.lesson_id IS NULL THEN \'ar\' ELSE t.locale END content_language';
    }
    private function locale(string $language): string { return $language==='en'?'en':'ar'; }

    public function page(array $params=[],string $language='ar',?array $admin=null): array
    {
        if($admin!==null)$this->admin($admin);
        $page=max(1,min(10000,(int)($params['page']??1)));
        $size=max(1,min(50,(int)($params['per_page']??20)));
        $where=$admin!==null?'1=1':$this->publicScope();$bindings=[];
        if(isset($params['status'])){
            $status=$params['status'];
            if(!is_string($status)||!in_array($status,$admin!==null?self::STATES:['scheduled','live'],true))throw new ApiError('invalid_fields',422,['status'=>'invalid']);
            $where.=' AND e.status=?';$bindings[]=$status;
        } elseif($admin===null) $where.=" AND e.status IN ('scheduled','live')";
        $q=$this->db->prepare('SELECT COUNT(*)'.$this->from().' WHERE '.$where);$q->execute($bindings);$total=(int)$q->fetchColumn();
        $q=$this->db->prepare($this->select().$this->from().' LEFT JOIN lesson_translations t ON t.lesson_id=l.id AND t.locale=? WHERE '.$where." ORDER BY (e.status='live') DESC,e.scheduled_at ASC,e.id ASC LIMIT $size OFFSET ".(($page-1)*$size));
        $q->execute(array_merge([$this->locale($language)],$bindings));
        return ['items'=>array_map([$this,'present'],$q->fetchAll()),'server_now'=>gmdate('Y-m-d\TH:i:s\Z'),
            'pagination'=>['page'=>$page,'per_page'=>$size,'total'=>$total,'has_more'=>$page*$size<$total]];
    }
    public function detail(int $lesson,string $language='ar',?array $admin=null): array
    {
        if($admin!==null)$this->admin($admin);
        $q=$this->db->prepare($this->select().$this->from().' LEFT JOIN lesson_translations t ON t.lesson_id=l.id AND t.locale=? WHERE e.lesson_id=?'.($admin===null?' AND '.$this->publicScope():''));
        $q->execute([$this->locale($language),$lesson]);$row=$q->fetch();
        if(!$row)throw new ApiError('not_found',404);
        $result=$this->present($row);
        $result['server_now']=gmdate('Y-m-d\TH:i:s\Z');
        if($admin!==null){
            $q=$this->db->prepare("SELECT title,description FROM lesson_translations WHERE lesson_id=? AND locale='en'");$q->execute([$lesson]);
            $result['translations']=['ar'=>['title'=>$row['title'],'description'=>$row['description']],'en'=>$q->fetch()?:null];
        }
        return $result;
    }
    private function present(array $row): array
    {
        return ['id'=>(int)$row['lesson_id'],'event_id'=>(int)$row['id'],'title'=>$row['title'],'description'=>$row['description'],
            'content_language'=>$row['content_language'],'status'=>$row['status'],'scheduled_at'=>$this->instant($row['scheduled_at']),
            'schedule_timezone'=>$row['schedule_timezone'],'estimated_duration_minutes'=>$row['estimated_duration_minutes']===null?null:(int)$row['estimated_duration_minutes'],
            'is_live_event'=>(bool)$row['is_live_event'],'reminders_enabled'=>(bool)$row['reminders_enabled'],'replay_available'=>(bool)$row['replay_available'],
            'visibility'=>$row['visibility'],'revision'=>(int)$row['revision'],'scholar_id'=>$row['scholar_id']===null?null:(int)$row['scholar_id'],
            'scholar_name'=>$row['scholar_name'],'series_id'=>$row['series_id']===null?null:(int)$row['series_id'],'series_title'=>$row['series_title'],
            'youtube_id'=>$row['youtube_id'],'live_source'=>$row['live_source'],'live_confirmed_at'=>$this->instant($row['live_confirmed_at']),
            'can_watch'=>(bool)$row['is_published']&&$row['visibility']==='public'&&in_array($row['status'],['live','published'],true)];
    }
    private function instant(?string $value): ?string
    {
        return $value===null?null:(new DateTimeImmutable($value,new DateTimeZone('UTC')))->format('Y-m-d\TH:i:s.v\Z');
    }
    private function parseInstant($value): ?string
    {
        if($value===null)return null;
        if(!is_string($value)||!preg_match('/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?(?:Z|[+-]\d{2}:\d{2})$/D',$value))throw new ApiError('invalid_fields',422,['scheduled_at'=>'explicit_offset_required']);
        try{$date=new DateTimeImmutable($value);$errors=DateTimeImmutable::getLastErrors();}
        catch(\Exception $e){throw new ApiError('invalid_fields',422,['scheduled_at'=>'invalid']);}
        if($errors!==false&&($errors['warning_count']||$errors['error_count']))throw new ApiError('invalid_fields',422,['scheduled_at'=>'invalid']);
        if((int)$date->format('Y')<2020||(int)$date->format('Y')>2100)throw new ApiError('invalid_fields',422,['scheduled_at'=>'out_of_range']);
        return $date->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s.v');
    }
    private function text($value,string $field,int $max,bool $nullable=false): ?string
    {
        if($nullable&&$value===null)return null;
        if(!is_string($value)||mb_strlen(trim($value),'UTF-8')> $max||(!$nullable&&mb_strlen(trim($value),'UTF-8')<2))throw new ApiError('invalid_fields',422,[$field=>'invalid']);
        return trim($value);
    }
    private function admin(array $actor): void
    {
        if(!in_array($actor['role']??'', ['admin','owner'],true)||empty($actor['email_verified_at'])||!empty($actor['suspended_at']))throw new ApiError('forbidden',403);
    }

    public function save(array $actor,int $lesson,array $input): array
    {
        $this->admin($actor);
        $allowed=['expected_revision','status','scheduled_at','schedule_timezone','estimated_duration_minutes','is_live_event','reminders_enabled','replay_available','visibility','title_ar','description_ar','title_en','description_en'];
        if(array_diff(array_keys($input),$allowed))throw new ApiError('invalid_fields',422,['input'=>'unsupported_field']);
        $expected=$input['expected_revision']??null;
        if(!is_int($expected)||$expected<0)throw new ApiError('invalid_fields',422,['expected_revision'=>'required']);
        $this->db->beginTransaction();
        try{
            // The parent row also serializes concurrent creation of its unique event.
            $q=$this->db->prepare('SELECT * FROM lessons WHERE id=? FOR UPDATE');$q->execute([$lesson]);$parent=$q->fetch();
            if(!$parent)throw new ApiError('not_found',404);
            $q=$this->db->prepare('SELECT * FROM lesson_events WHERE lesson_id=? FOR UPDATE');$q->execute([$lesson]);$old=$q->fetch()?:null;
            if(($old===null?0:(int)$old['revision'])!==$expected)throw new ApiError('conflict',409);
            $status=array_key_exists('status',$input)?$input['status']:($old['status']??'draft');
            if(!is_string($status)||!in_array($status,self::STATES,true))throw new ApiError('invalid_fields',422,['status'=>'invalid']);
            $previous=$old['status']??'draft';
            if($previous!==$status&&!in_array($status,self::TRANSITIONS[$previous],true))throw new ApiError('invalid_fields',422,['status'=>'invalid_transition']);
            $start=array_key_exists('scheduled_at',$input)?$this->parseInstant($input['scheduled_at']):($old['scheduled_at']??null);
            if(in_array($status,['scheduled','live','published'],true)&&$start===null)throw new ApiError('invalid_fields',422,['scheduled_at'=>'required']);
            $zone=array_key_exists('schedule_timezone',$input)?$input['schedule_timezone']:($old['schedule_timezone']??'UTC');
            if(!is_string($zone)||!in_array($zone,DateTimeZone::listIdentifiers(DateTimeZone::ALL_WITH_BC),true))throw new ApiError('invalid_fields',422,['schedule_timezone'=>'invalid']);
            $oldDuration=$old['estimated_duration_minutes']??null;
            $duration=$oldDuration===null?null:(int)$oldDuration;
            if(array_key_exists('estimated_duration_minutes',$input))$duration=$input['estimated_duration_minutes'];
            if($duration!==null&&(!is_int($duration)||$duration<1||$duration>1440))throw new ApiError('invalid_fields',422,['estimated_duration_minutes'=>'invalid']);
            $flags=[];
            foreach(['is_live_event','reminders_enabled','replay_available'] as $key){
                $flags[$key]=array_key_exists($key,$input)?$input[$key]:(bool)($old[$key]??false);
                if(!is_bool($flags[$key]))throw new ApiError('invalid_fields',422,[$key=>'invalid']);
            }
            if(($status==='live'&&!$flags['is_live_event'])||($flags['replay_available']&&$status!=='published'))throw new ApiError('invalid_fields',422,['status'=>'inconsistent_media_state']);
            if(in_array($status,['live','published'],true)&&!preg_match('/^[A-Za-z0-9_-]{11}$/D',$parent['youtube_id']))throw new ApiError('invalid_fields',422,['youtube_id'=>'required']);
            $visibility=array_key_exists('visibility',$input)?$input['visibility']:($old['visibility']??'public');
            if(!in_array($visibility,['public','private'],true))throw new ApiError('invalid_fields',422,['visibility'=>'invalid']);
            $title=array_key_exists('title_ar',$input)?$this->text($input['title_ar'],'title_ar',220):$parent['title'];
            $description=array_key_exists('description_ar',$input)?$this->text($input['description_ar'],'description_ar',20000,true):$parent['description'];
            $q=$this->db->prepare("SELECT title,description FROM lesson_translations WHERE lesson_id=? AND locale='en'");$q->execute([$lesson]);$english=$q->fetch()?:null;
            if(array_key_exists('title_en',$input)||array_key_exists('description_en',$input)){
                if(array_key_exists('title_en',$input)&&$input['title_en']===null){
                    $this->db->prepare("DELETE FROM lesson_translations WHERE lesson_id=? AND locale='en'")->execute([$lesson]);
                }else{
                    $enTitle=$this->text($input['title_en']??($english['title']??null),'title_en',220);
                    $enDescription=$this->text(array_key_exists('description_en',$input)?$input['description_en']:($english['description']??null),'description_en',20000,true);
                    $this->db->prepare("INSERT INTO lesson_translations(lesson_id,locale,title,description) VALUES (?,'en',?,?) ON DUPLICATE KEY UPDATE title=VALUES(title),description=VALUES(description)")->execute([$lesson,$enTitle,$enDescription]);
                }
            }
            $published=$visibility==='public'&&($status==='live'||($status==='published'&&(!$flags['is_live_event']||$flags['replay_available'])));
            $this->db->prepare('UPDATE lessons SET title=?,description=?,is_published=? WHERE id=?')->execute([$title,$description,(int)$published,$lesson]);
            $confirmed=$status==='live'?($previous==='live'?($old['live_confirmed_at']??gmdate('Y-m-d H:i:s')):gmdate('Y-m-d H:i:s')):($old['live_confirmed_at']??null);
            $source=$confirmed!==null?'admin':null;$revision=$expected+1;
            $values=[$status,$start,$zone,$duration,(int)$flags['is_live_event'],(int)$flags['reminders_enabled'],(int)$flags['replay_available'],$visibility,$confirmed,$source,$revision,(int)$actor['id']];
            if($old===null){
                $this->db->prepare('INSERT INTO lesson_events(status,scheduled_at,schedule_timezone,estimated_duration_minutes,is_live_event,reminders_enabled,replay_available,visibility,live_confirmed_at,live_source,revision,updated_by,created_by,lesson_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP())')->execute(array_merge($values,[(int)$actor['id'],$lesson]));
                $event=(int)$this->db->lastInsertId();
            }else{
                $event=(int)$old['id'];
                $this->db->prepare('UPDATE lesson_events SET status=?,scheduled_at=?,schedule_timezone=?,estimated_duration_minutes=?,is_live_event=?,reminders_enabled=?,replay_available=?,visibility=?,live_confirmed_at=?,live_source=?,revision=?,updated_by=?,updated_at=UTC_TIMESTAMP() WHERE id=?')->execute(array_merge($values,[$event]));
            }
            $change=$old===null?'created':($previous!==$status?'status_changed':($old['scheduled_at']!==$start?'rescheduled':'metadata_changed'));
            $payload=['lesson_id'=>$lesson,'status'=>$status,'scheduled_at'=>$this->instant($start),'previous_status'=>$old['status']??null,'previous_scheduled_at'=>$this->instant($old['scheduled_at']??null),'visibility'=>$visibility,'reminders_enabled'=>$flags['reminders_enabled']];
            $this->db->prepare('INSERT INTO lesson_event_changes(event_id,revision,change_type,payload_json,created_at) VALUES (?,?,?,?,UTC_TIMESTAMP())')->execute([$event,$revision,$change,json_encode($payload,JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR)]);
            $this->db->prepare('INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$actor['id'],'lesson-event-'.$status,'lesson_event',(string)$event]);
            $this->db->commit();
            return $this->detail($lesson,'ar',$actor);
        }catch(Throwable $failure){if($this->db->inTransaction())$this->db->rollBack();throw $failure;}
    }
}
