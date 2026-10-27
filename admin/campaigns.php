<?php
declare(strict_types=1);

function admin_campaign_text(array $post,string $key,string $default=''): string
{
    if(!is_string($post[$key]??$default))throw new RuntimeException(admin_locale()==='en'?'Invalid campaign field.':'حقل الحملة غير صالح.');
    return trim($post[$key]??$default);
}

function admin_campaign_action(array $actor,array $post): int
{
    $english=admin_locale()==='en';
    $service=new \Athar\Engagement\CampaignService(db());
    $kind=$post['kind'];
    $id=filter_var($post['campaign_id']??'',FILTER_VALIDATE_INT,['options'=>['min_range'=>1]]);
    $revision=filter_var($post['expected_revision']??'',FILTER_VALIDATE_INT,['options'=>['min_range'=>1,'max_range'=>2147483646]]);
    if($kind!=='engagement-campaign-save'&&(!$id||!$revision))throw new RuntimeException($english?'A campaign and its saved version are required.':'يلزم تحديد الحملة ونسختها المحفوظة.');
    try {
        if($kind==='engagement-campaign-confirm') {
            if(($actor['role']??'')!=='owner')admin_deny_action($actor,'admin-action',$kind);
            $q=db()->prepare('SELECT password_hash FROM users WHERE id=?');$q->execute([$actor['id']]);
            if(!password_verify(admin_campaign_text($post,'owner_password'),(string)$q->fetchColumn()))throw new RuntimeException($english?'The current Owner password is required.':'يلزم إدخال كلمة مرور المالك الحالية.');
            $service->confirmAndSchedule($actor,$id,$revision,admin_campaign_text($post,'confirmation'));
        } elseif($kind==='engagement-campaign-cancel') {
            $service->cancel($actor,$id,$revision);
        } elseif($kind==='engagement-campaign-save') {
            $input=[];
            foreach(['name','channel','audience_type','title_ar','body_ar','title_en','body_en','deep_link','image_url','scheduled_at','schedule_timezone','schedule_offset'] as $field)$input[$field]=admin_campaign_text($post,$field,$field==='schedule_timezone'?'UTC':'');
            $input['audience_locale']=admin_campaign_text($post,'audience_locale')?:null;
            $ref=admin_campaign_text($post,'audience_ref_id');$input['audience_ref_id']=$ref===''?null:filter_var($ref,FILTER_VALIDATE_INT,['options'=>['min_range'=>1]]);
            $ttl=filter_var($post['ttl_hours']??24,FILTER_VALIDATE_INT,['options'=>['min_range'=>1,'max_range'=>168]]);
            if($ttl===false)throw new \Athar\Http\ApiError('invalid_fields',422,['ttl_hours'=>'invalid']);
            $input['ttl_seconds']=$ttl*3600;$input['min_frequency_hours']=filter_var($post['min_frequency_hours']??6,FILTER_VALIDATE_INT);
            $input['respect_quiet_hours']=isset($post['respect_quiet_hours']);
            if(admin_campaign_text($post,'campaign_id')==='')$id=$service->createDraft($actor,$input)['id'];
            else {if(!$id||!$revision)throw new \Athar\Http\ApiError('invalid_fields',422);$input['expected_revision']=$revision;$service->updateDraft($actor,$id,$input);}
        } else throw new \Athar\Http\ApiError('invalid_fields',422);
    }catch(\Athar\Http\ApiError $failure) {
        if($failure->status===403)admin_deny_action($actor,'admin-action',$kind);
        if($failure->status===409)throw new RuntimeException($english?'Campaign state changed or a delivery dependency is unavailable. Review the current preview before confirming.':'تغيرت حالة الحملة أو أن إحدى تبعيات التسليم غير جاهزة. راجع المعاينة الحالية قبل التأكيد.');
        if($failure->status===404)throw new RuntimeException($english?'The campaign is no longer available.':'الحملة لم تعد متاحة.');
        throw new RuntimeException($english?'Check the campaign fields, public destination, local time and UTC offset. No message was sent.':'راجع حقول الحملة والوجهة العامة والموعد المحلي وفرق UTC. لم تُرسل رسالة.');
    }
    return (int)$id;
}
