<?php
declare(strict_types=1);

final class AdminAdhkarConflict extends RuntimeException {}

function admin_moderate_comment(array $actor,array $input): void
{
    if (!admin_can($actor,'moderation') || empty($actor['email_verified_at'])) admin_deny_action($actor,'admin-action','comment-review');
    $id=filter_var($input['comment_id'] ?? null,FILTER_VALIDATE_INT,['options'=>['min_range'=>1]]);
    $revision=filter_var($input['expected_revision'] ?? null,FILTER_VALIDATE_INT,['options'=>['min_range'=>1]]);
    $reason=$input['reason'] ?? null;
    if (!$id || !$revision || !is_string($reason)) throw new RuntimeException('بيانات مراجعة التعليق غير صالحة.');
    $service=new \Athar\Learning\DiscussionService(db());
    try {
        if (($input['kind'] ?? '') === 'comment-delete' || ($input['decision'] ?? '') === 'delete') $service->deleteModerated($actor,$id,['expected_revision'=>$revision,'reason'=>$reason]);
        else $service->moderate($actor,$id,['expected_revision'=>$revision,'reason'=>$reason,'status'=>$input['decision'] ?? $input['status'] ?? '']);
    } catch (\Athar\Http\ApiError $failure) {
        if ($failure->status === 403) admin_deny_action($actor,'admin-action','comment-review');
        if (($failure->fields['comment'] ?? '') === 'has_replies') throw new RuntimeException('راجع الردود واحذفها أولًا قبل حذف التعليق الأصلي.');
        throw new RuntimeException(match($failure->status){409=>'تم تعديل التعليق من جلسة أخرى. حدّث الصفحة ثم أعد المحاولة.',404=>'التعليق غير موجود.',default=>'راجع الحالة والسبب: من حرفين إلى 500 حرف.'});
    }
}

function admin_adhkar_fields(array $input): array
{
    $values=[];
    foreach (['title'=>200,'formula'=>5000,'source_reference'=>300,'source_url'=>500,'category'=>80] as $field=>$limit) {
        if (!is_string($input[$field] ?? null)) throw new RuntimeException('راجع عنوان الذكر ونصه ومرجعه وتصنيفه.');
        $values[$field]=trim($input[$field]);
        if (mb_strlen($values[$field],'UTF-8') < 2 || mb_strlen($values[$field],'UTF-8') > $limit) throw new RuntimeException('راجع عنوان الذكر ونصه ومرجعه وتصنيفه.');
    }
    $url=parse_url($values['source_url']);
    if (!is_https_url($values['source_url']) || isset($url['user']) || isset($url['pass']) || !isset($input['reviewed'])) throw new RuntimeException('لا يمكن حفظ الذكر دون مصدر آمن ومراجعة موثقة.');
    $raw=$input['repeat_count'] ?? '';
    if (!is_string($raw)) throw new RuntimeException('عدد التكرار يجب أن يكون من 1 إلى 1000 أو يترك فارغًا.');
    $raw=trim($raw); $count=$raw === '' ? null : filter_var($raw,FILTER_VALIDATE_INT,['options'=>['min_range'=>1,'max_range'=>1000]]);
    if ($count === false) throw new RuntimeException('عدد التكرار يجب أن يكون من 1 إلى 1000 أو يترك فارغًا.');
    $sort=filter_var($input['sort_order'] ?? '0',FILTER_VALIDATE_INT,['options'=>['min_range'=>-100000,'max_range'=>100000]]);
    if ($sort === false) throw new RuntimeException('ترتيب الذكر غير صالح.');
    return array_merge($values,['repeat_count'=>$count,'sort_order'=>$sort,'is_reviewed'=>1,'is_published'=>isset($input['publish']) ? 1 : 0]);
}

function admin_mutate_adhkar(array $actor,array $input): int
{
    if (!admin_can($actor,'content')) admin_deny_action($actor,'admin-action','adhkar');
    $create=($input['kind'] ?? '') === 'adhkar'; $action=$create ? 'create' : ($input['content_action'] ?? '');
    if (!in_array($action,['create','update','toggle','delete'],true)) throw new RuntimeException('الإجراء غير معروف.');
    $id=$create ? 0 : filter_var($input['id'] ?? null,FILTER_VALIDATE_INT,['options'=>['min_range'=>1]]);
    $expected=$create ? 0 : filter_var($input['expected_revision'] ?? null,FILTER_VALIDATE_INT,['options'=>['min_range'=>1]]);
    if (!$create && (!$id || !$expected)) throw new RuntimeException('نسخة الذكر غير صالحة؛ حدّث الصفحة ثم أعد المحاولة.');
    $fields=in_array($action,['create','update'],true) ? admin_adhkar_fields($input) : null;
    $db=db();$db->beginTransaction();
    try {
        if (!$create) {
            $q=$db->prepare('SELECT * FROM adhkar_items WHERE id=? FOR UPDATE');$q->execute([$id]);$current=$q->fetch();
            if (!$current) throw new RuntimeException('الذكر غير موجود.');
            if ((int)$current['revision'] !== $expected) throw new AdminAdhkarConflict('تم تعديل الذكر من جلسة أخرى. حدّث الصفحة ثم أعد المحاولة.');
        }
        $revision=$create ? 1 : $expected+1;
        if ($fields !== null) {
            $columns=array_keys($fields);$values=array_values($fields);$columns[]='revision';$values[]=$revision;
            if ($create) {
                $db->prepare('INSERT INTO adhkar_items('.implode(',',$columns).') VALUES ('.implode(',',array_fill(0,count($columns),'?')).')')->execute($values);$id=(int)$db->lastInsertId();
            } else { $values[]=$id;$db->prepare('UPDATE adhkar_items SET '.implode(',',array_map(static fn(string $key):string=>$key.'=?',$columns)).' WHERE id=?')->execute($values); }
        } elseif ($action === 'toggle') {
            if (!(bool)$current['is_published']) {
                $candidate=array_map(static fn($v)=>$v === null ? '' : (string)$v,$current);
                if ($current['is_reviewed']) $candidate['reviewed']='on';
                admin_adhkar_fields($candidate);
            }
            $db->prepare('UPDATE adhkar_items SET is_published=1-is_published,revision=? WHERE id=?')->execute([$revision,$id]);
        }
        if ($action === 'delete') { $fresh=$current;$fresh['revision']=$revision; }
        else { $q=$db->prepare('SELECT * FROM adhkar_items WHERE id=?');$q->execute([$id]);$fresh=$q->fetch(); }
        admin_record_adhkar_revision($db,$fresh,match($action){'create'=>'created','update'=>'updated','toggle'=>'publication_changed','delete'=>'deleted'},(int)$actor['id']);
        if ($action === 'delete') $db->prepare('DELETE FROM adhkar_items WHERE id=?')->execute([$id]);
        $db->prepare('INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$actor['id'],$action === 'toggle' ? 'toggle-publication' : $action,'adhkar',(string)$id]);
        $db->commit();return (int)$id;
    } catch (Throwable $failure) { if ($db->inTransaction()) $db->rollBack();throw $failure; }
}

function admin_adhkar_snapshot(array $row): array
{
    return [
        'title' => (string) ($row['title'] ?? ''),
        'formula' => (string) ($row['formula'] ?? ''),
        'source_reference' => (string) ($row['source_reference'] ?? ''),
        'source_url' => (string) ($row['source_url'] ?? ''),
        'repeat_count' => $row['repeat_count'] === null ? null : (int) $row['repeat_count'],
        'category' => (string) ($row['category'] ?? ''),
        'is_reviewed' => (bool) ($row['is_reviewed'] ?? false),
        'is_published' => (bool) ($row['is_published'] ?? false),
        'sort_order' => (int) ($row['sort_order'] ?? 0),
    ];
}

function admin_record_adhkar_revision(PDO $db, array $row, string $action, int $actorId): void
{
    $allowed = ['created','updated','publication_changed','deleted'];
    if (!in_array($action, $allowed, true)) throw new InvalidArgumentException('Invalid adhkar revision action.');
    $sourceId = (int) ($row['id'] ?? 0);
    $revision = (int) ($row['revision'] ?? 0);
    if ($sourceId < 1 || $revision < 1) throw new RuntimeException('Invalid adhkar revision snapshot.');
    $statement = $db->prepare(
        'INSERT INTO adhkar_item_revisions (adhkar_id,source_item_id,revision,action,snapshot_json,actor_id)
         VALUES (?,?,?,?,?,?)'
    );
    $statement->execute([
        $sourceId,
        $sourceId,
        $revision,
        $action,
        json_encode(admin_adhkar_snapshot($row), JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
        $actorId,
    ]);
}
