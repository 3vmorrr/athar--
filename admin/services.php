<?php
declare(strict_types=1);

final class AdminNightDefaultsConflict extends RuntimeException {}

function admin_save_night_defaults(array $actor,array $input): void
{
    if (!admin_can($actor,'night_prayer_defaults')) admin_deny_action($actor,'admin-action','night-prayer-defaults');
    $expected=filter_var($input['expected_revision'] ?? null,FILTER_VALIDATE_INT,['options'=>['min_range'=>1,'max_range'=>2147483646]]);
    if ($expected === false) throw new RuntimeException('نسخة الإعدادات العامة غير صالحة.');
    $allowed=['_csrf','kind','expected_revision','enabled','calculation_method','madhab','high_latitude_rule','target_verses','source_name_ar','source_name_en','source_url','intro_ar','intro_en'];
    if (array_diff(array_keys($input),$allowed)) throw new RuntimeException('هذه الشاشة تقبل الإعدادات العامة فقط.');
    $values=['enabled'=>isset($input['enabled']) ? '1' : '0','revision'=>(string)($expected+1)];
    foreach (['calculation_method','madhab','high_latitude_rule','target_verses','source_name_ar','source_name_en','source_url','intro_ar','intro_en'] as $field) $values[$field]=admin_service_text($input,$field,str_starts_with($field,'intro_') ? 1200 : 500);
    try {$values=\Athar\NightPrayer\PublicNightPrayerDefaults::validate($values);}catch(\Athar\Http\ApiError $failure){throw new RuntimeException('راجع طريقة الحساب والمذهب والقاعدة والهدف والمصدر والنصوص.');}
    $db=admin_begin_settings($actor,$input,false);
    try {
        $current=\Athar\NightPrayer\PublicNightPrayerDefaults::load($db);
        if ((int)$current['revision'] !== $expected) throw new AdminNightDefaultsConflict('تغيّرت الإعدادات العامة من جلسة أخرى. أعد تحميلها قبل الحفظ.');
        foreach (\Athar\NightPrayer\PublicNightPrayerDefaults::storage($values) as $key=>$value) save_platform_setting($key,$value,(int)$actor['id']);
        $db->prepare('INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$actor['id'],'night-prayer-public-defaults-updated','platform',(string)($expected+1)]);$db->commit();
    } catch (Throwable $failure) {if($db->inTransaction())$db->rollBack();if($failure instanceof \Athar\Http\ApiError)throw new RuntimeException('تعذرت قراءة الإعدادات العامة. احتُفظ بالقيم الحالية.');throw $failure;}
}

function admin_service_text(array $input,string $key,int $limit=500): string
{
    $value=$input[$key] ?? '';
    if (!is_string($value) || mb_strlen($value,'UTF-8') > $limit) throw new RuntimeException('حقول إعدادات الخدمة غير صالحة أو أطول من الحد المسموح.');
    return trim($value);
}

/** Serialize shared configuration writes before reading their current values. */
function admin_begin_settings(array $actor,array $input,bool $passwordRequired=true): PDO
{
    if (!admin_can($actor,'services')) admin_deny_action($actor,'admin-action','gmail-settings');
    $db=db();$db->beginTransaction();
    try {
        $db->exec("INSERT INTO platform_settings(setting_key,setting_value) VALUES ('admin_settings_write_lock','1') ON DUPLICATE KEY UPDATE setting_value=setting_value");
        $q=$db->prepare("SELECT password_hash FROM users WHERE id=? AND role='owner' AND suspended_at IS NULL FOR UPDATE");$q->execute([$actor['id']]);$password=$q->fetchColumn();
        if (!$password || ($passwordRequired && !password_verify(admin_service_text($input,'owner_password'),$password))) throw new RuntimeException('كلمة مرور المالك غير صحيحة.');
        return $db;
    } catch (Throwable $failure) {if($db->inTransaction())$db->rollBack();throw $failure;}
}

function admin_save_gmail(array $actor,array $input,?callable $testSender=null): array
{
    if (!admin_can($actor,'services')) admin_deny_action($actor,'admin-action','gmail-settings');
    $email=strtolower(admin_service_text($input,'gmail_email',190));
    $password=preg_replace('/\s+/u','',admin_service_text($input,'smtp_password'));
    $recipient=strtolower(admin_service_text($input,'test_recipient',190));
    $clear=isset($input['clear_test_recipient']);$send=isset($input['send_test_email']);
    if (!filter_var($email,FILTER_VALIDATE_EMAIL) || $password === null) throw new RuntimeException('أدخل بريد Gmail صالحًا.');
    if (!$clear && $recipient !== '' && (!filter_var($recipient,FILTER_VALIDATE_EMAIL) || !isset($input['test_recipient_authorized']))) throw new RuntimeException('أدخل بريد اختبار صالحًا وأكد تصريح صاحبه.');
    $db=admin_begin_settings($actor,$input);
    try {
        $currentEmail=platform_setting('smtp_username');
        if ($password === '') {
            if ($currentEmail !== '' && $currentEmail !== $email) throw new RuntimeException('أدخل App Password جديدة عند تغيير حساب البريد.');
            if (platform_secret('smtp_password') === '') throw new RuntimeException('أدخل App Password الخاصة بالتطبيق.');
        }
        $authorized=$clear ? '' : ($recipient !== '' ? $recipient : (platform_setting('smtp_test_recipient_authorized_at') !== '' ? platform_secret('smtp_test_recipient') : ''));
        if ($send && $authorized === '') throw new RuntimeException('حدد مستلم اختبار مصرحًا به قبل إرسال اختبار.');
        foreach (['smtp_host'=>'smtp.gmail.com','smtp_port'=>'587','smtp_security'=>'tls','smtp_username'=>$email,'smtp_from_email'=>$email,'smtp_from_name'=>'أثر'] as $key=>$value) save_platform_setting($key,$value,(int)$actor['id']);
        if ($password !== '') save_platform_setting('smtp_password_cipher',seal_platform_secret($password),(int)$actor['id']);
        if ($clear) {
            save_platform_setting('smtp_test_recipient_cipher','',(int)$actor['id']);save_platform_setting('smtp_test_recipient_authorized_at','',(int)$actor['id']);
        } elseif ($recipient !== '') {
            save_platform_setting('smtp_test_recipient_cipher',seal_platform_secret($recipient),(int)$actor['id']);save_platform_setting('smtp_test_recipient_authorized_at',gmdate('Y-m-d H:i:s'),(int)$actor['id']);
        }
        $db->prepare('INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,?,?,NULL)')->execute([$actor['id'],'gmail-smtp-settings-updated','platform']);
        $db->commit();
    } catch (Throwable $failure) {if($db->inTransaction())$db->rollBack();throw $failure;}
    if (!$send) return ['test'=>'not_requested'];
    $rate=new \Athar\Identity\RateLimiter($db,\Athar\Infrastructure\SecretKey::authentication());
    if (!$rate->consume('smtp-admin-test',(string)$actor['id'],1,3600)) throw new RuntimeException('حُفظت الإعدادات؛ انتهت حصة الاختبار لهذه الساعة.');
    try {
        $accepted=$testSender !== null ? $testSender($authorized) : send_platform_email($authorized,'اختبار إعدادات أثر','هذه رسالة اختبار واحدة إلى المستلم المصرح به.');
    } catch (Throwable $sendFailure) {$accepted=false;}
    $db->prepare('INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,?,?,NULL)')->execute([$actor['id'],$accepted ? 'smtp-test-accepted' : 'smtp-test-failed','platform']);
    if (!$accepted) throw new RuntimeException('حُفظت الإعدادات؛ تعذر إكمال اختبار SMTP. راجع الاتصال وبيانات الحساب والمستلم.');
    return ['test'=>'accepted'];
}

function admin_save_dorar(array $actor,array $input): void
{
    $token=admin_service_text($input,'dorar_api_token');$clear=isset($input['clear_dorar_token']);
    $db=admin_begin_settings($actor,$input);
    try {
        if ($clear) save_platform_setting('dorar_api_token_cipher','',(int)$actor['id']);
        elseif ($token !== '') save_platform_setting('dorar_api_token_cipher',seal_platform_secret($token),(int)$actor['id']);
        elseif (platform_setting('dorar_api_token_cipher') !== '') platform_secret('dorar_api_token');
        $db->prepare('INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,?,?,NULL)')->execute([$actor['id'],'dorar-api-settings-updated','platform']);$db->commit();
    } catch (Throwable $failure) {if($db->inTransaction())$db->rollBack();throw $failure;}
}

function admin_service_diagnostics(array $actor): array
{
    if (!admin_can($actor,'services')) return [];
    $result=['smtp'=>'missing','recipient'=>'missing','dorar'=>'missing','recipient_masked'=>'','base_url'=>'','base_url_https'=>false,'base_url_state'=>'unconfigured','key_source'=>trim((string)getenv('ATHAR_SETTINGS_KEY')) !== '' ? 'environment' : 'local'];
    foreach (['smtp'=>'smtp_password','recipient'=>'smtp_test_recipient','dorar'=>'dorar_api_token'] as $state=>$key) {
        if (platform_setting($key.'_cipher') === '') continue;
        try {
            $value=platform_secret($key);
            $result[$state]=$value !== '' ? 'readable' : 'missing';
            if ($state === 'recipient') {
                if (platform_setting('smtp_test_recipient_authorized_at') === '' || !filter_var($value,FILTER_VALIDATE_EMAIL)) $result[$state]='unauthorized';
                else $result['recipient_masked']=admin_redact_email($value);
            }
        } catch (Throwable $failure) {$result[$state]='unreadable';}
    }
    if ($result['dorar'] === 'missing' && trim((string)getenv('ATHAR_DORAR_API_TOKEN')) !== '') $result['dorar']='environment';
    try {$result['base_url']=platform_email_base_url();$result['base_url_https']=str_starts_with($result['base_url'],'https://');$result['base_url_state']='configured';}catch(Throwable $failure){}
    return $result;
}
