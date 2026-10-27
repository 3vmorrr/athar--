<?php
declare(strict_types=1);

function admin_mutate_series(array $actor, array $input): int
{
    if (!admin_can($actor, 'content')) admin_deny_action($actor, 'admin-action', 'series');
    $create = ($input['kind'] ?? '') === 'series';
    $action = $create ? 'create' : ($input['content_action'] ?? '');
    if (!in_array($action, ['create','update','toggle','delete'], true)) throw new RuntimeException('الإجراء غير معروف.');
    $id = $create ? 0 : filter_var($input['id'] ?? null, FILTER_VALIDATE_INT);
    if (!$create && (!$id || $id < 1)) throw new RuntimeException('عنصر المحتوى غير صالح.');
    if (in_array($action, ['create','update'], true)) {
        foreach (['title','description','source_url'] as $key) {
            if (!is_string($input[$key] ?? '')) throw new RuntimeException('بيانات السلسلة غير صالحة.');
            $$key = trim($input[$key] ?? '');
        }
        if (mb_strlen($title, 'UTF-8') < 2 || mb_strlen($title, 'UTF-8') > 220 || mb_strlen($description, 'UTF-8') > 4000) throw new RuntimeException('راجع عنوان السلسلة ووصفها.');
        $parts = $source_url === '' ? [] : parse_url($source_url);
        if ($source_url !== '' && (!is_https_url($source_url) || isset($parts['user']) || isset($parts['pass']))) throw new RuntimeException('أدخل رابط مصدر HTTPS صالحًا دون بيانات دخول.');
        $scholarId = filter_var($input['scholar_id'] ?? '0', FILTER_VALIDATE_INT, ['options'=>['min_range'=>0]]);
        $sort = filter_var($input['sort_order'] ?? '0', FILTER_VALIDATE_INT, ['options'=>['min_range'=>-100000,'max_range'=>100000]]);
        if ($scholarId === false || $sort === false) throw new RuntimeException('راجع العالم وترتيب العرض.');
    }
    $db = db(); $db->beginTransaction();
    try {
        if (!$create) {
            $query = $db->prepare('SELECT id FROM series WHERE id=? FOR UPDATE'); $query->execute([$id]);
            if (!$query->fetchColumn()) throw new RuntimeException('السلسلة غير موجودة.');
        }
        if (in_array($action, ['create','update'], true)) {
            if ($scholarId > 0) {
                $scholar = $db->prepare('SELECT id FROM scholars WHERE id=? LOCK IN SHARE MODE'); $scholar->execute([$scholarId]);
                if (!$scholar->fetchColumn()) throw new RuntimeException('العالم المحدد غير موجود.');
                if (!$create) {
                    $conflicts = $db->prepare('SELECT COUNT(*) FROM lessons WHERE series_id=? AND scholar_id IS NOT NULL AND scholar_id<>?'); $conflicts->execute([$id,$scholarId]);
                    if ((int)$conflicts->fetchColumn() > 0) throw new RuntimeException('توجد دروس مرتبطة بعالم آخر. أعد ربطها قبل تغيير عالم السلسلة.');
                }
            }
            $values = [$scholarId,$title,$description,$source_url,isset($input['publish']) ? 1 : 0,$sort];
            if ($create) {
                $db->prepare("INSERT INTO series(scholar_id,title,description,source_url,is_published,sort_order) VALUES (NULLIF(?,0),?,?,NULLIF(?,''),?,?)")->execute($values);
                $id = (int)$db->lastInsertId();
            } else {
                $values[] = $id;
                $db->prepare("UPDATE series SET scholar_id=NULLIF(?,0),title=?,description=?,source_url=NULLIF(?,''),is_published=?,sort_order=? WHERE id=?")->execute($values);
            }
        } elseif ($action === 'toggle') {
            $db->prepare('UPDATE series SET is_published=1-is_published WHERE id=?')->execute([$id]);
        } else {
            $dependents = $db->prepare('SELECT COUNT(*) FROM lessons WHERE series_id=?'); $dependents->execute([$id]);
            if ((int)$dependents->fetchColumn() > 0) throw new RuntimeException('لا يمكن حذف السلسلة قبل نقل أو حذف دروسها المرتبطة.');
            $db->prepare('DELETE FROM series WHERE id=?')->execute([$id]);
        }
        $db->prepare('INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$actor['id'],$action === 'toggle' ? 'toggle-publication' : $action,'series',(string)$id]);
        $db->commit(); return (int)$id;
    } catch (Throwable $failure) { if ($db->inTransaction()) $db->rollBack(); throw $failure; }
}
