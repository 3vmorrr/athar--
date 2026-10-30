<?php
declare(strict_types=1);
namespace Athar\Progress;

use Athar\Domain\NightPrayer;
use Athar\Http\ApiError;
use DateTimeImmutable;
use PDO;
use Throwable;

/** One contract for web/API writers. Dates and workload use inclusive UTC days. */
final class Khatmah
{
    private PDO $db;
    public function __construct(PDO $db) { $this->db=$db; }

    public function get(int $owner):?array
    {
        $q=$this->db->prepare('SELECT start_date,target_date,total_pages,completed_pages,status,revision,updated_at FROM khatmah_plans WHERE user_id=?');
        $q->execute([$owner]);$row=$q->fetch();return $row?self::owned($owner,$row):null;
    }

    public static function fingerprint(int $owner,?array $row):string
    {
        $values=$row?[$owner,$row['start_date'],$row['target_date'],604,(int)$row['completed_pages'],$row['status'],(int)$row['revision']]:[$owner,'absent'];
        return hash('sha256',implode('|',$values));
    }
    private static function owned(int $owner,array $row):array { return array_merge(self::decorate($row),['plan_hash'=>self::fingerprint($owner,$row)]); }

    public static function decorate(array $row,?string $today=null):array
    {
        $today=$today??gmdate('Y-m-d');
        if(!NightPrayer::validDate($row['start_date'])||!NightPrayer::validDate($row['target_date'])||(int)$row['completed_pages']<0||!in_array($row['status'],['active','paused','finished'],true)||($row['status']==='finished'&&(int)$row['completed_pages']!==604))throw new ApiError('khatmah_data_unavailable',503);
        $start=new DateTimeImmutable($row['start_date'].'T00:00:00Z');
        $target=new DateTimeImmutable($row['target_date'].'T00:00:00Z');
        $now=new DateTimeImmutable($today.'T00:00:00Z');
        $totalDays=(int)$start->diff($target)->format('%r%a')+1;
        if($totalDays<1||(int)$row['total_pages']!==604||(int)$row['completed_pages']>604)throw new ApiError('khatmah_data_unavailable',503);
        $completed=(int)$row['completed_pages'];$remaining=604-$completed;
        $elapsed=$now<$start?0:($now>$target?$totalDays:(int)$start->diff($now)->format('%a')+1);
        $left=$now<$start?$totalDays:($now>$target?0:(int)$now->diff($target)->format('%a')+1);
        $expected=intdiv(604*$elapsed,$totalDays);
        return array_merge($row,[
            'total_pages'=>604,'completed_pages'=>$completed,'revision'=>(int)$row['revision'],
            'today_utc'=>$today,'remaining_pages'=>$remaining,'total_days'=>$totalDays,'elapsed_days'=>$elapsed,
            'days_remaining'=>$left,'starts_in_days'=>$now<$start?(int)$now->diff($start)->format('%a'):0,
            'daily_pages'=>$row['status']==='active'&&$now>=$start&&$left>0?intdiv($remaining+$left-1,$left):0,
            'planned_daily_pages'=>intdiv(604+$totalDays-1,$totalDays),
            'expected_pages_today'=>$expected,'pages_behind'=>max(0,$expected-$completed),'pages_ahead'=>max(0,$completed-$expected),
            'progress_percent'=>intdiv($completed*100,604),
            'overdue'=>$now>$target&&$remaining>0&&$row['status']!=='finished',
        ]);
    }

    public static function integer(array $data,string $key,int $min,int $max):int
    {
        $value=$data[$key]??null;
        if(!is_int($value)||$value<$min||$value>$max)throw new ApiError('invalid_fields',422,[$key=>'invalid']);
        return $value;
    }

    private static function dates(string $start,string $target,bool $unchanged):void
    {
        if(!NightPrayer::validDate($start)||!NightPrayer::validDate($target))throw new ApiError('invalid_fields',422,['date'=>'invalid']);
        $s=new DateTimeImmutable($start.'T00:00:00Z');$t=new DateTimeImmutable($target.'T00:00:00Z');
        $now=new DateTimeImmutable(gmdate('Y-m-d').'T00:00:00Z');
        // Identical historical dates remain editable for explicit progress corrections.
        // Changing either date must pass the full current schedule contract.
        if($t<$s||$t>$s->modify('+1830 days')||(!$unchanged&&($s<$now->modify('-1830 days')||$s>$now->modify('+366 days')||$t<$now)))
            throw new ApiError('invalid_fields',422,['date'=>'invalid']);
    }

    public function save(int $owner,array $data):array { return $this->mutate($owner,$data,false); }
    public function action(int $owner,array $data):array { return $this->mutate($owner,$data,true); }

    private function mutate(int $owner,array $data,bool $action):array
    {
        $allowed=$action?['expected_revision','expected_plan_hash','action','start_date','target_date']:['expected_revision','expected_plan_hash','start_date','target_date','completed_pages','status'];
        if(array_diff(array_keys($data),$allowed))throw new ApiError('invalid_fields',422);
        if(array_key_exists('expected_plan_hash',$data)&&(!is_string($data['expected_plan_hash'])||!preg_match('/^[a-f0-9]{64}$/D',$data['expected_plan_hash'])))throw new ApiError('invalid_fields',422,['expected_plan_hash'=>'invalid']);
        $expected=self::integer($data,'expected_revision',0,2147483646);
        $this->db->beginTransaction();
        try{
            // Serializes first creation and deletion with every canonical identity writer.
            $q=$this->db->prepare('SELECT id,suspended_at FROM users WHERE id=? FOR UPDATE');$q->execute([$owner]);$actor=$q->fetch();
            if(!$actor)throw new ApiError('not_found',404);
            if($actor['suspended_at']!==null)throw new ApiError('forbidden',403);
            $q=$this->db->prepare('SELECT start_date,target_date,total_pages,completed_pages,status,revision,updated_at FROM khatmah_plans WHERE user_id=? FOR UPDATE');
            $q->execute([$owner]);$current=$q->fetch()?:null;$revision=$current?(int)$current['revision']:0;
            if($current)self::decorate($current);
            $kind=$action?($data['action']??null):'save';
            if(!is_string($kind)||!in_array($kind,['save','pause','resume','finish','restart'],true))throw new ApiError('invalid_fields',422,['action'=>'invalid']);
            if($action&&!$current)throw new ApiError('not_found',404);
            if($revision!==$expected){
                // An unchanged exact save replay can acknowledge a committed lost reply.
                // Lifecycle actions have their own semantics; they never use this shortcut.
                if(!$action&&$revision===$expected+1&&$current&&$this->matches($current,$data)){
                    $this->db->commit();return self::owned($owner,$current);
                }
                if($action&&$revision===$expected+1&&$current&&$this->actionReplay($current,$data,$kind)){
                    $this->db->commit();return self::owned($owner,$current);
                }
                throw new ApiError('conflict',409,['current'=>$current?self::owned($owner,$current):null]);
            }
            // Bind the observed baseline as well as its revision: restore/ABA cannot reuse
            // an old revision with different plan values and silently overwrite them.
            if(isset($data['expected_plan_hash'])&&!hash_equals(self::fingerprint($owner,$current),$data['expected_plan_hash']))throw new ApiError('conflict',409,['current'=>$current?self::owned($owner,$current):null]);
            $start=array_key_exists('start_date',$data)?$data['start_date']:($current['start_date']??gmdate('Y-m-d'));
            $target=array_key_exists('target_date',$data)?$data['target_date']:($current['target_date']??null);
            $completed=$action?(int)$current['completed_pages']:self::integer($data,'completed_pages',0,604);
            $status=$current['status']??'active';
            if($kind==='restart'){$start=array_key_exists('start_date',$data)?$data['start_date']:gmdate('Y-m-d');$completed=0;$status='active';}
            elseif($kind==='pause'){$status='paused';}
            elseif($kind==='resume'){$status='active';if($target<gmdate('Y-m-d'))throw new ApiError('invalid_fields',422,['target_date'=>'past']);}
            elseif($kind==='finish'){if($completed!==604)throw new ApiError('invalid_fields',422,['completed_pages'=>'requires_604']);$status='finished';}
            if(!is_string($start)||!is_string($target))throw new ApiError('invalid_fields',422,['date'=>'invalid']);
            if(!$action&&array_key_exists('status',$data)&&$data['status']!==$status)throw new ApiError('invalid_fields',422,['status'=>'use_explicit_action']);
            $unchanged=$current&&$start===$current['start_date']&&$target===$current['target_date'];
            self::dates($start,$target,$unchanged&&$kind!=='restart');
            if($current&&$current['status']==='finished'&&$kind!=='restart'){
                if($kind!=='save'||!$this->matches($current,$data))throw new ApiError('invalid_fields',422,['status'=>'restart_required']);
            }
            $same=$current&&$start===$current['start_date']&&$target===$current['target_date']&&$completed===(int)$current['completed_pages']&&$status===$current['status'];
            if(!$same||$kind==='restart'){
                $this->db->prepare('INSERT INTO khatmah_plans(user_id,start_date,target_date,total_pages,completed_pages,status,revision) VALUES(?,?,?,604,?,?,1) ON DUPLICATE KEY UPDATE start_date=VALUES(start_date),target_date=VALUES(target_date),completed_pages=VALUES(completed_pages),status=VALUES(status),revision=revision+1,updated_at=UTC_TIMESTAMP()')->execute([$owner,$start,$target,$completed,$status]);
            }
            $result=$this->get($owner);$this->db->commit();return $result;
        }catch(Throwable $failure){if($this->db->inTransaction())$this->db->rollBack();throw $failure;}
    }

    private function matches(array $current,array $data):bool
    {
        return (array_key_exists('start_date',$data)?$data['start_date']:$current['start_date'])===$current['start_date']&&($data['target_date']??null)===$current['target_date']
            &&($data['completed_pages']??null)===(int)$current['completed_pages']&&(!array_key_exists('status',$data)||$data['status']===$current['status']);
    }

    private function actionReplay(array $current,array $data,string $kind):bool
    {
        $state=['pause'=>'paused','resume'=>'active','finish'=>'finished','restart'=>'active'][$kind]??null;
        if($current['status']!==$state)return false;
        if($kind!=='restart')return true;
        return (int)$current['completed_pages']===0&&(array_key_exists('start_date',$data)?$data['start_date']:gmdate('Y-m-d'))===$current['start_date']&&($data['target_date']??null)===$current['target_date'];
    }
}
