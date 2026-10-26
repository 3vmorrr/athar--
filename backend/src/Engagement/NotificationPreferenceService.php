<?php
declare(strict_types=1);

namespace Athar\Engagement;

use Athar\Http\ApiError;
use PDO;
use Throwable;

final class NotificationPreferenceService
{
    public const SCHEMA_VERSION = 1;
    public const CONSENT_VERSION = 1;

    private const CATEGORY_COLUMNS = [
        'lesson_reminders' => 'lesson_reminders',
        'following_updates' => 'following_updates',
        'quran_routine' => 'quran_routine',
        'adhkar_routine' => 'adhkar_routine',
        'salawat_friday' => 'salawat_friday',
        'custom_routines' => 'custom_routines',
        'khatmah' => 'khatmah',
        'night_prayer' => 'night_prayer',
    ];

    public function __construct(private PDO $db) {}

    public function get(int $userId): array
    {
        $q=$this->db->prepare('SELECT revision,master_enabled,consent_version,consented_at,
            lesson_reminders,following_updates,quran_routine,adhkar_routine,salawat_friday,
            custom_routines,khatmah,night_prayer,updated_at
            FROM notification_preferences WHERE user_id=?');
        $q->execute([$userId]);
        $row=$q->fetch();
        if(!$row){
            $exists=$this->db->prepare('SELECT id FROM users WHERE id=?');
            $exists->execute([$userId]);
            if(!$exists->fetchColumn())throw new ApiError('not_found',404);
        }
        return $this->payload($row?:null);
    }

    public function update(int $userId,array $input): array
    {
        $allowed=['expected_revision','master_enabled','consent_version','categories'];
        foreach(array_keys($input) as $field){
            if(!in_array($field,$allowed,true))throw new ApiError('invalid_fields',422,[$field=>'unsupported']);
        }
        if(!array_key_exists('expected_revision',$input)||!is_int($input['expected_revision'])||$input['expected_revision']<0){
            throw new ApiError('invalid_fields',422,['expected_revision'=>'required']);
        }

        $this->db->beginTransaction();
        try{
            $userLock=$this->db->prepare('SELECT id FROM users WHERE id=? FOR UPDATE');
            $userLock->execute([$userId]);
            if(!$userLock->fetchColumn())throw new ApiError('not_found',404);

            $locked=$this->db->prepare('SELECT revision,master_enabled,consent_version,consented_at,
                lesson_reminders,following_updates,quran_routine,adhkar_routine,salawat_friday,
                custom_routines,khatmah,night_prayer,updated_at
                FROM notification_preferences WHERE user_id=? FOR UPDATE');
            $locked->execute([$userId]);
            $row=$locked->fetch()?:null;
            $current=$this->payload($row);
            if($input['expected_revision']!==$current['revision']){
                throw new ApiError('conflict',409,['revision'=>$current['revision']]);
            }

            $master=array_key_exists('master_enabled',$input)?$input['master_enabled']:$current['master_enabled'];
            if(!is_bool($master))throw new ApiError('invalid_fields',422,['master_enabled'=>'boolean_required']);

            $consent=array_key_exists('consent_version',$input)?$input['consent_version']:$current['consent']['accepted_version'];
            if(!is_int($consent)||!in_array($consent,[0,self::CONSENT_VERSION],true)){
                throw new ApiError('invalid_fields',422,['consent_version'=>'unsupported']);
            }
            if($consent===0)$master=false;
            if($master&&$consent!==self::CONSENT_VERSION){
                throw new ApiError('invalid_fields',422,['consent_version'=>'current_consent_required']);
            }

            $categories=$current['categories'];
            if(array_key_exists('categories',$input)){
                if(!is_array($input['categories']))throw new ApiError('invalid_fields',422,['categories'=>'invalid']);
                foreach($input['categories'] as $key=>$value){
                    if(!array_key_exists((string)$key,self::CATEGORY_COLUMNS)){
                        throw new ApiError('invalid_fields',422,['categories'=>'unsupported_category']);
                    }
                    if(!is_bool($value))throw new ApiError('invalid_fields',422,['categories'=>'boolean_required']);
                    $categories[(string)$key]=$value;
                }
            }

            $consentedAt=$current['consent']['accepted_at'];
            if($consent===0)$consentedAt=null;
            elseif($current['consent']['accepted_version']!==self::CONSENT_VERSION)$consentedAt=gmdate('Y-m-d H:i:s');

            $revision=$current['revision']+1;
            $this->db->prepare('INSERT INTO notification_preferences
                (user_id,revision,master_enabled,consent_version,consented_at,
                 lesson_reminders,following_updates,quran_routine,adhkar_routine,salawat_friday,
                 custom_routines,khatmah,night_prayer,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP())
                ON DUPLICATE KEY UPDATE revision=VALUES(revision),master_enabled=VALUES(master_enabled),
                consent_version=VALUES(consent_version),consented_at=VALUES(consented_at),
                lesson_reminders=VALUES(lesson_reminders),following_updates=VALUES(following_updates),
                quran_routine=VALUES(quran_routine),adhkar_routine=VALUES(adhkar_routine),
                salawat_friday=VALUES(salawat_friday),custom_routines=VALUES(custom_routines),
                khatmah=VALUES(khatmah),night_prayer=VALUES(night_prayer),updated_at=UTC_TIMESTAMP()')
                ->execute([
                    $userId,$revision,(int)$master,$consent,$consentedAt,
                    (int)$categories['lesson_reminders'],(int)$categories['following_updates'],
                    (int)$categories['quran_routine'],(int)$categories['adhkar_routine'],
                    (int)$categories['salawat_friday'],(int)$categories['custom_routines'],
                    (int)$categories['khatmah'],(int)$categories['night_prayer'],
                ]);

            (new LessonReminderService($this->db))->reconcileUser($userId);
            $this->db->prepare("INSERT INTO audit_logs(actor_id,action,target_type,target_id)
                VALUES (?,'notification-preferences-updated','user',?)")->execute([$userId,(string)$userId]);
            $this->db->commit();
            return $this->get($userId);
        }catch(Throwable $e){
            if($this->db->inTransaction())$this->db->rollBack();
            throw $e;
        }
    }

    private function payload(?array $row): array
    {
        $accepted=(int)($row['consent_version']??0);
        $currentConsent=$accepted===self::CONSENT_VERSION;
        $master=(bool)($row['master_enabled']??false);
        $categories=[];
        foreach(self::CATEGORY_COLUMNS as $key=>$column)$categories[$key]=(bool)($row[$column]??false);
        $effective=[];
        foreach($categories as $key=>$enabled)$effective[$key]=$master&&$currentConsent&&$enabled;

        return [
            'schema_version'=>self::SCHEMA_VERSION,
            'revision'=>(int)($row['revision']??0),
            'master_enabled'=>$master,
            'consent'=>[
                'required_version'=>self::CONSENT_VERSION,
                'accepted_version'=>$accepted,
                'current'=>$currentConsent,
                'accepted_at'=>$row['consented_at']??null,
            ],
            'categories'=>$categories,
            'effective_categories'=>$effective,
            'updated_at'=>$row['updated_at']??null,
        ];
    }
}
