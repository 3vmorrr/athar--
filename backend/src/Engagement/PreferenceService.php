<?php
declare(strict_types=1);

namespace Athar\Engagement;

use Athar\Http\ApiError;
use DateTimeZone;
use PDO;
use Throwable;

final class PreferenceService
{
    public function __construct(private PDO $db) {}

    public function get(int $userId): array
    {
        $q=$this->db->prepare("SELECT u.email_marketing_opt_in,u.preferences_json,
            p.locale,p.locale_is_explicit,p.push_campaign_opt_in,p.email_campaign_opt_in,p.quiet_start,p.quiet_end,p.timezone,p.updated_at
            FROM users u LEFT JOIN engagement_channel_preferences p ON p.user_id=u.id WHERE u.id=?");
        $q->execute([$userId]);$row=$q->fetch();
        if(!$row)throw new ApiError('not_found',404);
        $profile=json_decode((string)($row['preferences_json']??'{}'),true)?:[];
        $locale=(bool)($row['locale_is_explicit']??false)?$row['locale']:(in_array($profile['language']??null,['ar','en'],true)?$profile['language']:($row['locale']??'ar'));
        return [
            'locale'=>$locale,
            'push_campaign_opt_in'=>(bool)($row['push_campaign_opt_in']??false),
            'email_campaign_opt_in'=>$row['email_campaign_opt_in']===null?(bool)$row['email_marketing_opt_in']:(bool)$row['email_campaign_opt_in'],
            'quiet_hours'=>[
                'start'=>$row['quiet_start']===null?null:substr((string)$row['quiet_start'],0,5),
                'end'=>$row['quiet_end']===null?null:substr((string)$row['quiet_end'],0,5),
                'timezone'=>$row['timezone'],
            ],
            'updated_at'=>$row['updated_at'],
        ];
    }

    public function update(int $userId,array $input): array
    {
        $allowed=['locale','push_campaign_opt_in','email_campaign_opt_in','quiet_hours'];
        foreach(array_keys($input) as $field)if(!in_array($field,$allowed,true))throw new ApiError('invalid_fields',422,[$field=>'unsupported']);
        $owns=!$this->db->inTransaction();if($owns)$this->db->beginTransaction();
        try {
        $lock=$this->db->prepare('SELECT id FROM users WHERE id=? FOR UPDATE');$lock->execute([$userId]);if(!$lock->fetchColumn())throw new ApiError('not_found',404);
        $current=$this->get($userId);
        $locale=$input['locale']??$current['locale'];
        $prior=$this->db->prepare('SELECT locale_is_explicit FROM engagement_channel_preferences WHERE user_id=?');$prior->execute([$userId]);
        $explicitLocale=array_key_exists('locale',$input)?1:(int)$prior->fetchColumn();
        if(!in_array($locale,['ar','en'],true))throw new ApiError('invalid_fields',422,['locale'=>'invalid']);
        $push=array_key_exists('push_campaign_opt_in',$input)?$input['push_campaign_opt_in']:$current['push_campaign_opt_in'];
        $email=array_key_exists('email_campaign_opt_in',$input)?$input['email_campaign_opt_in']:$current['email_campaign_opt_in'];
        if(!is_bool($push)||!is_bool($email))throw new ApiError('invalid_fields',422,['consent'=>'boolean_required']);
        $quiet=$current['quiet_hours'];
        if(array_key_exists('quiet_hours',$input)){
            if(!is_array($input['quiet_hours']))throw new ApiError('invalid_fields',422,['quiet_hours'=>'invalid']);
            $extra=array_diff(array_keys($input['quiet_hours']),['start','end','timezone']);
            if($extra)throw new ApiError('invalid_fields',422,['quiet_hours'=>'unsupported_field']);
            foreach(['start','end','timezone'] as $key)if(array_key_exists($key,$input['quiet_hours']))$quiet[$key]=$input['quiet_hours'][$key];
        }
        $start=$this->time($quiet['start']??null,'start');$end=$this->time($quiet['end']??null,'end');$timezone=$quiet['timezone']??null;
        if(($start===null)!==($end===null))throw new ApiError('invalid_fields',422,['quiet_hours'=>'start_and_end_required']);
        if($timezone!==null){
            if(!is_string($timezone)||mb_strlen($timezone,'UTF-8')>80)throw new ApiError('invalid_fields',422,['timezone'=>'invalid']);
            if(!in_array($timezone,DateTimeZone::listIdentifiers(DateTimeZone::ALL_WITH_BC),true))throw new ApiError('invalid_fields',422,['timezone'=>'invalid']);
        }
        if($start!==null&&$start===$end)throw new ApiError('invalid_fields',422,['quiet_hours'=>'equal_endpoints']);
        if($start!==null&&$timezone===null)throw new ApiError('invalid_fields',422,['timezone'=>'required']);


            $this->db->prepare("INSERT INTO engagement_channel_preferences
                (user_id,locale,locale_is_explicit,push_campaign_opt_in,email_campaign_opt_in,quiet_start,quiet_end,timezone,updated_at)
                VALUES (?,?,?,?,?,?,?,?,UTC_TIMESTAMP())
                ON DUPLICATE KEY UPDATE locale=VALUES(locale),locale_is_explicit=VALUES(locale_is_explicit),push_campaign_opt_in=VALUES(push_campaign_opt_in),
                email_campaign_opt_in=VALUES(email_campaign_opt_in),quiet_start=VALUES(quiet_start),quiet_end=VALUES(quiet_end),
                timezone=VALUES(timezone),updated_at=UTC_TIMESTAMP()")
                ->execute([$userId,$locale,$explicitLocale,(int)$push,(int)$email,$start,$end,$timezone]);
            $this->db->prepare('UPDATE users SET email_marketing_opt_in=? WHERE id=?')->execute([(int)$email,$userId]);
            if(!$email){
                $this->db->prepare("UPDATE engagement_campaign_recipients SET status='skipped',last_error_code='email_opt_out',locked_at=NULL,updated_at=UTC_TIMESTAMP() WHERE user_id=? AND channel='email' AND status='queued'")->execute([$userId]);
                $this->db->prepare("UPDATE email_outbox SET status='cancelled',last_error_code='email_opt_out',locked_at=NULL,updated_at=UTC_TIMESTAMP() WHERE user_id=? AND category='campaign' AND status='pending'")->execute([$userId]);
            }
            if(!$push)$this->db->prepare("UPDATE engagement_campaign_recipients SET status='skipped',last_error_code='push_opt_out',locked_at=NULL,updated_at=UTC_TIMESTAMP() WHERE user_id=? AND channel='push' AND status='queued'")->execute([$userId]);
            $this->db->prepare("INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,'engagement-preferences-updated','user',?)")->execute([$userId,(string)$userId]);
            if($owns)$this->db->commit();
            return $this->get($userId);
        }catch(Throwable $e){if($owns&&$this->db->inTransaction())$this->db->rollBack();throw $e;}
    }

    private function time($value,string $field): ?string
    {
        if($value===null||$value==='')return null;
        if(!is_string($value)||!preg_match('/^(?:[01]\d|2[0-3]):[0-5]\d$/D',$value))throw new ApiError('invalid_fields',422,[$field=>'invalid_time']);
        return $value.':00';
    }
}
