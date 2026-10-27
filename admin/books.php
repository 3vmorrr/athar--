<?php
declare(strict_types=1);

final class AdminBookConflict extends RuntimeException {}

function admin_book_version(array $row): string
{
    $state=[];
    foreach (['id','title','author','description','keywords','language','publication_year','rights_holder','source_url','download_url','rights_basis','is_published','can_download','file_key','file_size'] as $key) $state[$key]=isset($row[$key]) ? (string)$row[$key] : null;
    return hash_hmac('sha256',json_encode($state,JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR),\Athar\Infrastructure\SecretKey::authentication());
}

function admin_book_fields(array $input): array
{
    $values = [];
    foreach (['title'=>240,'author'=>180,'description'=>12000,'keywords'=>1000,'language'=>12,'publication_year'=>4,'rights_holder'=>180,'source_url'=>500,'download_url'=>500,'rights_basis'=>500] as $key=>$limit) {
        $raw = $input[$key] ?? ($key === 'language' ? 'ar' : '');
        if (!is_string($raw)) throw new RuntimeException('بيانات الكتاب غير صالحة.');
        $values[$key] = trim($raw);
        if (mb_strlen($values[$key],'UTF-8') > $limit) throw new RuntimeException('بيانات الكتاب أطول من الحد المسموح.');
    }
    if (mb_strlen($values['title'],'UTF-8') < 2 || mb_strlen($values['author'],'UTF-8') < 2) throw new RuntimeException('عنوان الكتاب أو اسم المؤلف غير صالح.');
    if (!\Athar\Catalog\BookAccess::https($values['source_url']) || $values['rights_basis'] === '') throw new RuntimeException('يلزم توثيق مصدر آمن وأساس حقوق الإتاحة.');
    if ($values['download_url'] !== '' && !\Athar\Catalog\BookAccess::https($values['download_url'])) throw new RuntimeException('رابط التنزيل المباشر يجب أن يبدأ بـ HTTPS دون بيانات دخول عند توفيره.');
    $year = $values['publication_year'] === '' ? null : filter_var($values['publication_year'], FILTER_VALIDATE_INT, ['options'=>['min_range'=>1000,'max_range'=>2200]]);
    if (!in_array($values['language'], ['ar','en','other'], true) || $year === false) throw new RuntimeException('لغة الكتاب أو سنة النشر غير صالحة.');
    $values['publication_year'] = $year;
    return $values;
}

/** Metadata edits preserve registered local assets; only explicit deletion retires them. */
function admin_mutate_book(array $actor, array $input): array
{
    if (!admin_can($actor, 'content')) admin_deny_action($actor, 'admin-action', 'book');
    $create = ($input['kind'] ?? '') === 'book';
    $action = $create ? 'create' : ($input['content_action'] ?? '');
    if (!in_array($action, ['create','update','toggle','delete'], true)) throw new RuntimeException('الإجراء غير معروف.');
    $id = $create ? 0 : filter_var($input['id'] ?? null, FILTER_VALIDATE_INT, ['options'=>['min_range'=>1]]);
    if (!$create && !$id) throw new RuntimeException('عنصر المحتوى غير صالح.');
    $fields = in_array($action, ['create','update'], true) ? admin_book_fields($input) : null;
    $db = db(); $fileKey = null; $db->beginTransaction();
    try {
        if (!$create) {
            $q = $db->prepare('SELECT * FROM books WHERE id=? FOR UPDATE'); $q->execute([$id]); $existing = $q->fetch();
            if (!$existing) throw new RuntimeException('الكتاب غير موجود.');
            if (!is_string($input['expected_state'] ?? null) || !hash_equals(admin_book_version($existing),$input['expected_state'])) throw new AdminBookConflict('تغيّر الكتاب في جلسة أخرى. أعد تحميله قبل الحفظ أو الحذف.');
        }
        if ($fields !== null) {
            $columns = array_keys($fields); $values = array_values($fields);
            $columns[]='can_download'; $values[]=$fields['download_url'] !== '' ? 1 : 0;
            $columns[]='is_published'; $values[]=isset($input['publish']) ? 1 : 0;
            foreach (['description','keywords','rights_holder','download_url'] as $key) if ($fields[$key] === '') $values[array_search($key,$columns,true)] = null;
            if ($create) {
                $db->prepare('INSERT INTO books('.implode(',',$columns).') VALUES ('.implode(',',array_fill(0,count($columns),'?')).')')->execute($values);
                $id=(int)$db->lastInsertId();
            } else {
                $values[]=$id;
                $db->prepare('UPDATE books SET '.implode(',',array_map(static fn(string $column):string=>$column.'=?',$columns)).' WHERE id=?')->execute($values);
            }
        } elseif ($action === 'toggle') {
            // Old invalid metadata must not become public through the shortcut.
            if (!(bool)$existing['is_published']) admin_book_fields(array_map(static fn($value)=>$value === null ? '' : (string)$value,$existing));
            $db->prepare('UPDATE books SET is_published=1-is_published WHERE id=?')->execute([$id]);
        } else {
            $q=$db->prepare('SELECT COUNT(*) FROM lesson_event_context WHERE related_book_id=?'); $q->execute([$id]);
            if ((int)$q->fetchColumn() > 0) throw new RuntimeException('أزل مراجع الكتاب من الأحداث قبل حذفه.');
            $fileKey=$existing['file_key'];
            $db->prepare("DELETE FROM user_favorites WHERE target_type='book' AND target_id=?")->execute([$id]);
            $db->prepare('DELETE FROM books WHERE id=?')->execute([$id]);
        }
        $db->prepare('INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$actor['id'],$action === 'toggle' ? 'toggle-publication' : $action,'book',(string)$id]);
        $db->commit();
    } catch (Throwable $failure) { if ($db->inTransaction()) $db->rollBack(); throw $failure; }
    $cleanupPending = false;
    if (is_string($fileKey) && $fileKey !== '') {
        try {
            if (!preg_match('/^[a-f0-9]{40}\.pdf$/D',$fileKey)) throw new RuntimeException('Invalid registered key.');
            $q=$db->prepare('SELECT COUNT(*) FROM books WHERE file_key=?'); $q->execute([$fileKey]);
            if ((int)$q->fetchColumn() === 0) {
                $directory=dirname(__DIR__).'/storage/books'; $path=$directory.'/'.$fileKey;
                if (is_link($directory) || is_link($path)) throw new RuntimeException('Linked asset preserved.');
                if (is_file($path) && !@unlink($path)) throw new RuntimeException('Asset cleanup failed.');
            }
        } catch (Throwable $cleanupFailure) { $cleanupPending=true; }
    }
    return ['id'=>(int)$id,'cleanup_pending'=>$cleanupPending];
}
