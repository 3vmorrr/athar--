<?php
declare(strict_types=1);
namespace Athar\NightPrayer;
use Athar\Http\ApiError;
use Athar\Infrastructure\HttpsUrl;
use PDO;

/** Public suggestions only: never reads or changes personal prayer records. */
final class PublicNightPrayerDefaults
{
    public const METHODS=['EGYPTIAN','MUSLIM_WORLD_LEAGUE','UMM_AL_QURA','KARACHI','DUBAI','MOON_SIGHTING_COMMITTEE'];
    public const RULES=['MIDDLE_OF_THE_NIGHT','SEVENTH_OF_THE_NIGHT','TWILIGHT_ANGLE'];
    private const KEYS=[
        'enabled'=>'night_prayer_enabled','revision'=>'night_prayer_public_revision',
        'calculation_method'=>'night_prayer_default_calculation_method','madhab'=>'night_prayer_default_madhab',
        'high_latitude_rule'=>'night_prayer_default_high_latitude_rule','target_verses'=>'night_prayer_default_target_verses',
        'source_name_ar'=>'night_prayer_source_name_ar','source_name_en'=>'night_prayer_source_name_en','source_url'=>'night_prayer_source_url',
        'intro_ar'=>'night_prayer_intro_ar','intro_en'=>'night_prayer_intro_en',
    ];
    public static function load(PDO $db): array
    {
        $values=['enabled'=>'1','revision'=>'1','calculation_method'=>'MUSLIM_WORLD_LEAGUE','madhab'=>'standard','high_latitude_rule'=>'MIDDLE_OF_THE_NIGHT','target_verses'=>'100',
            'source_name_ar'=>'حساب محلي بمكتبة Adhan','source_name_en'=>'Local Adhan calculation','source_url'=>'https://github.com/batoulapps/adhan-java/',
            'intro_ar'=>'قيام الليل عبادة تطوعية، وهذه الأدوات للمساعدة في التنظيم دون حكم على العبادة.',
            'intro_en'=>'Night prayer is voluntary worship. These tools support planning without judging worship.'];
        $keys=array_values(self::KEYS);
        $q=$db->prepare('SELECT setting_key,setting_value FROM platform_settings WHERE setting_key IN ('.implode(',',array_fill(0,count($keys),'?')).')');$q->execute($keys);$stored=$q->fetchAll(PDO::FETCH_KEY_PAIR);
        foreach(self::KEYS as $field=>$key)if(array_key_exists($key,$stored))$values[$field]=$stored[$key];
        try{return self::validate($values);}catch(ApiError $failure){throw new ApiError('service_unavailable',503,['configuration'=>'unreadable']);}
    }
    public static function validate(array $values): array
    {
        foreach(self::KEYS as $field=>$_)if(!is_string($values[$field]??null))throw new ApiError('invalid_fields',422,[$field=>'invalid']);
        $methodAliases=['MuslimWorldLeague'=>'MUSLIM_WORLD_LEAGUE','Egyptian'=>'EGYPTIAN','UmmAlQura'=>'UMM_AL_QURA','Karachi'=>'KARACHI','Dubai'=>'DUBAI','MoonsightingCommittee'=>'MOON_SIGHTING_COMMITTEE'];
        $ruleAliases=['middle_of_night'=>'MIDDLE_OF_THE_NIGHT','seventh_of_night'=>'SEVENTH_OF_THE_NIGHT','angle_based'=>'TWILIGHT_ANGLE'];
        $values['calculation_method']=$methodAliases[$values['calculation_method']]??$values['calculation_method'];
        $values['high_latitude_rule']=$ruleAliases[$values['high_latitude_rule']]??$values['high_latitude_rule'];
        if(!in_array($values['enabled'],['0','1'],true))throw new ApiError('invalid_fields',422,['enabled'=>'invalid']);
        $revision=filter_var($values['revision'],FILTER_VALIDATE_INT,['options'=>['min_range'=>1,'max_range'=>2147483647]]);
        $target=filter_var($values['target_verses'],FILTER_VALIDATE_INT,['options'=>['min_range'=>1,'max_range'=>6236]]);
        if($revision===false||$target===false)throw new ApiError('invalid_fields',422,['revision_or_target'=>'invalid']);
        if(!in_array($values['calculation_method'],self::METHODS,true)||!in_array($values['high_latitude_rule'],self::RULES,true)||!in_array($values['madhab'],['standard','hanafi'],true))throw new ApiError('invalid_fields',422,['calculation_method'=>'invalid']);
        foreach(['source_name_ar'=>120,'source_name_en'=>120,'intro_ar'=>1200,'intro_en'=>1200] as $field=>$limit){$values[$field]=trim($values[$field]);if(mb_strlen($values[$field],'UTF-8')>$limit||(str_starts_with($field,'source_name')&&mb_strlen($values[$field],'UTF-8')<2))throw new ApiError('invalid_fields',422,[$field=>'invalid']);}
        if(!HttpsUrl::valid($values['source_url']))throw new ApiError('invalid_fields',422,['source_url'=>'invalid']);
        return $values;
    }
    public static function storage(array $values): array
    {
        $stored=[];foreach(self::KEYS as $field=>$key)$stored[$key]=$values[$field];return $stored;
    }
    public static function response(array $values): array
    {
        return ['enabled'=>$values['enabled']==='1','revision'=>(int)$values['revision'],
            'defaults'=>['calculation_method'=>$values['calculation_method'],'madhab'=>$values['madhab'],'high_latitude_rule'=>$values['high_latitude_rule'],'target_verses'=>(int)$values['target_verses']],
            'source'=>['name_ar'=>$values['source_name_ar'],'name_en'=>$values['source_name_en'],'url'=>$values['source_url']],
            'intro'=>['ar'=>$values['intro_ar'],'en'=>$values['intro_en']]];
    }
}
