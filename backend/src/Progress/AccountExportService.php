<?php
declare(strict_types=1);
namespace Athar\Progress;

use Athar\Identity\AuthService;
use Athar\NightPrayer\NightPrayerService;
use PDO;
use Throwable;

/** Fixed projections prevent future credential/internal columns leaking through SELECT *. */
final class AccountExportService
{
    private PDO $db;
    private AuthService $auth;
    private ?NightPrayerService $night;
    public function __construct(PDO $db,AuthService $auth,?NightPrayerService $night=null){$this->db=$db;$this->auth=$auth;$this->night=$night;}
    public function read(int $owner):array
    {
        $owns=!$this->db->inTransaction();if($owns)$this->db->beginTransaction();
        try {
            $profile=$this->auth->profile($owner);
            $q=$this->db->prepare('SELECT preferences_json FROM users WHERE id=?');$q->execute([$owner]);
            $raw=$q->fetchColumn();
            if($raw===false)throw new \UnexpectedValueException('Account snapshot unavailable.');
            $preferences=json_decode($raw===null?'{}':$raw,true,512,JSON_THROW_ON_ERROR);
            if(!is_array($preferences))throw new \UnexpectedValueException('Unreadable account preferences.');
            $preferences=array_intersect_key($preferences,array_flip(['theme','language','quran_font_size']));
            foreach($preferences as $key=>$value){
                if(($key==='theme'&&!in_array($value,['light','dark','system'],true))||($key==='language'&&!in_array($value,['ar','en'],true))||($key==='quran_font_size'&&(!is_int($value)||$value<18||$value>48)))throw new \UnexpectedValueException('Unreadable account preferences.');
            }
            $profile['preferences']=(object)$preferences;
            $result=['profile'=>$profile];
            $fields=[
                'quran_bookmarks'=>'id,user_id,surah,ayah,label,created_at',
                'quran_positions'=>'user_id,surah,ayah,revision,updated_at',
                'khatmah_plans'=>'id,user_id,start_date,target_date,total_pages,completed_pages,status,created_at,updated_at',
                'user_favorites'=>'id,user_id,target_type,target_id,created_at',
                'video_progress'=>'user_id,lesson_id,position_seconds,duration_seconds,is_complete,updated_at',
                'account_daily_stats'=>'user_id,activity_date,quran_sessions,lesson_completions,adhkar_completions,book_downloads,book_completions',
                'adhkar_progress'=>'user_id,item_key,current_count,repeat_count,revision,updated_at',
                'activity_events'=>'user_id,event_id,activity_type,activity_date,created_at',
            ];
            foreach($fields as $table=>$columns){$q=$this->db->prepare('SELECT '.$columns.' FROM '.$table.' WHERE user_id=?');$q->execute([$owner]);$result[$table]=$q->fetchAll(PDO::FETCH_ASSOC);}
            if($this->night){
                $all=[];$page=1;
                do{$part=$this->night->sessions($owner,['page'=>$page++,'per_page'=>50]);$all=array_merge($all,$part['items']);}while($part['pagination']['has_more']);
                $preferences=$this->night->preferences($owner);
                $values=array_intersect_key((array)$preferences['values'],array_flip(['timezone','city','latitude','longitude','calculation_method','madhab','high_latitude_rule']));
                foreach($values as $key=>$value){
                    if(in_array($key,['latitude','longitude'],true)){
                        if((!is_int($value)&&!is_float($value))||!is_finite((float)$value)||abs($value)>($key==='latitude'?90:180))throw new \UnexpectedValueException('Unreadable night preferences.');
                    }elseif(!is_string($value)||mb_strlen($value,'UTF-8')>80)throw new \UnexpectedValueException('Unreadable night preferences.');
                }
                $preferences['values']=(object)$values;
                $result['night_prayer_sessions']=$all;$result['night_prayer_preferences']=$preferences;
            }
            if($owns)$this->db->commit();return $result;
        }catch(Throwable $error){if($owns&&$this->db->inTransaction())$this->db->rollBack();throw $error;}
    }
}
