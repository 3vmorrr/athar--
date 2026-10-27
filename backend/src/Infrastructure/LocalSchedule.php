<?php
declare(strict_types=1);
namespace Athar\Infrastructure;

use Athar\Http\ApiError;
use DateTimeImmutable;
use DateTimeZone;

/** Strict wall-clock scheduling: reject normalized dates, gaps and unresolved folds. */
final class LocalSchedule
{
    public static function utc(string $local,string $zone,string $offset=''): string
    {
        if(!in_array($zone,DateTimeZone::listIdentifiers(DateTimeZone::ALL_WITH_BC),true))throw new ApiError('invalid_fields',422,['schedule_timezone'=>'invalid']);
        if(!preg_match('/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?$/D',$local))throw new ApiError('invalid_fields',422,['scheduled_at'=>'invalid']);
        $wall=strlen($local)===16?$local.':00':$local;
        $parsed=DateTimeImmutable::createFromFormat('!Y-m-d\TH:i:s',$wall,new DateTimeZone('UTC'));
        $errors=DateTimeImmutable::getLastErrors();
        if(!$parsed||($errors!==false&&($errors['warning_count']||$errors['error_count']))||$parsed->format('Y-m-d\TH:i:s')!==$wall||(int)$parsed->format('Y')<2020||(int)$parsed->format('Y')>2100)throw new ApiError('invalid_fields',422,['scheduled_at'=>'invalid']);
        $tz=new DateTimeZone($zone);$epoch=$parsed->getTimestamp();$candidates=[];
        foreach($tz->getTransitions($epoch-172800,$epoch+172800)?:[] as $transition){
            $instant=(new DateTimeImmutable('@'.($epoch-(int)$transition['offset'])))->setTimezone($tz);
            if($instant->format('Y-m-d\TH:i:s')===$wall)$candidates[$instant->format('P')]=$instant;
        }
        if($offset==='Z')$offset='+00:00';
        if($offset!==''&&!isset($candidates[$offset]))throw new ApiError('invalid_fields',422,['schedule_offset'=>'invalid']);
        if(!$candidates||($offset===''&&count($candidates)!==1))throw new ApiError('invalid_fields',422,['scheduled_at'=>'gap_or_ambiguous']);
        return ($offset!==''?$candidates[$offset]:reset($candidates))->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s');
    }
}
