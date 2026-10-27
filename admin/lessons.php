<?php
declare(strict_types=1);

function admin_mutate_lesson(array $actor, array $input): int
{
    if (!admin_can($actor, 'content')) admin_deny_action($actor, 'admin-action', 'lesson');
    $create = ($input['kind'] ?? '') === 'lesson';
    $action = $create ? 'create' : ($input['content_action'] ?? '');
    if (!in_array($action, ['create','update','toggle','delete'], true)) throw new RuntimeException('الإجراء غير معروف.');
    $id = $create ? 0 : filter_var($input['id'] ?? null, FILTER_VALIDATE_INT);
    if (!$create && (!$id || $id < 1)) throw new RuntimeException('عنصر المحتوى غير صالح.');
    if (in_array($action, ['create','update'], true)) {
        foreach (['title','description','source_url'] as $key) if (!is_string($input[$key] ?? '')) throw new RuntimeException('بيانات الدرس غير صالحة.');
        $title = trim($input['title'] ?? ''); $description = trim($input['description'] ?? ''); $source = trim($input['source_url'] ?? '');
        if (mb_strlen($title, 'UTF-8') < 2 || mb_strlen($title, 'UTF-8') > 220 || mb_strlen($description, 'UTF-8') > 4000) throw new RuntimeException('راجع عنوان الدرس ووصفه.');
        $parts = parse_url($source); $video = youtube_video_id($source);
        if (!$video || isset($parts['user']) || isset($parts['pass'])) throw new RuntimeException('أدخل رابط فيديو YouTube معتمدًا يبدأ بـ HTTPS دون بيانات دخول.');
        $scholarId = filter_var($input['scholar_id'] ?? '0', FILTER_VALIDATE_INT, ['options'=>['min_range'=>0]]);
        $seriesId = filter_var($input['series_id'] ?? '0', FILTER_VALIDATE_INT, ['options'=>['min_range'=>0]]);
        $sort = filter_var($input['sort_order'] ?? '0', FILTER_VALIDATE_INT, ['options'=>['min_range'=>-100000,'max_range'=>100000]]);
        if ($scholarId === false || $seriesId === false || $sort === false) throw new RuntimeException('راجع العالم والسلسلة وترتيب العرض.');
    }
    $db = db(); $db->beginTransaction();
    try {
        if (!$create) {
            $query = $db->prepare('SELECT id,youtube_id FROM lessons WHERE id=? FOR UPDATE'); $query->execute([$id]);
            $existing = $query->fetch();
            if (!$existing) throw new RuntimeException('الدرس غير موجود.');
        }
        if (in_array($action, ['create','update'], true)) {
            if (!$create && $existing['youtube_id'] !== $video) {
                $events = $db->prepare('SELECT COUNT(*) FROM lesson_events WHERE lesson_id=?'); $events->execute([$id]);
                if ((int)$events->fetchColumn() > 0) throw new RuntimeException('أنشئ درسًا منفصلًا لفيديو جديد عند وجود سجل مواعيد مرتبط.');
            }
            if ($seriesId > 0) {
                $parent = $db->prepare('SELECT scholar_id FROM series WHERE id=? LOCK IN SHARE MODE'); $parent->execute([$seriesId]);
                $parentScholar = $parent->fetchColumn();
                if ($parentScholar === false) throw new RuntimeException('السلسلة المحددة غير موجودة.');
                if ($scholarId > 0 && (int)$parentScholar > 0 && $scholarId !== (int)$parentScholar) throw new RuntimeException('العالم المحدد لا يطابق عالم هذه السلسلة.');
                if ($scholarId === 0) $scholarId = (int)$parentScholar;
            }
            if ($scholarId > 0) {
                $parent = $db->prepare('SELECT id FROM scholars WHERE id=? LOCK IN SHARE MODE'); $parent->execute([$scholarId]);
                if (!$parent->fetchColumn()) throw new RuntimeException('العالم المحدد غير موجود.');
            }
            $values = [$scholarId,$seriesId,$title,$description,$video,$source,$sort,isset($input['publish']) ? 1 : 0];
            if ($create) {
                $db->prepare('INSERT INTO lessons(scholar_id,series_id,title,description,youtube_id,source_url,sort_order,is_published) VALUES (NULLIF(?,0),NULLIF(?,0),?,?,?,?,?,?)')->execute($values);
                $id = (int)$db->lastInsertId();
            } else {
                $values[] = $id;
                $db->prepare('UPDATE lessons SET scholar_id=NULLIF(?,0),series_id=NULLIF(?,0),title=?,description=?,youtube_id=?,source_url=?,sort_order=?,is_published=? WHERE id=?')->execute($values);
            }
        } elseif ($action === 'toggle') {
            $db->prepare('UPDATE lessons SET is_published=1-is_published WHERE id=?')->execute([$id]);
        } else {
            $events = $db->prepare('SELECT COUNT(*) FROM lesson_events WHERE lesson_id=?'); $events->execute([$id]);
            if ((int)$events->fetchColumn() > 0) throw new RuntimeException('لهذا الدرس سجل مواعيد مرتبط، ولا يمكن حذفه.');
            $db->prepare("DELETE FROM user_favorites WHERE target_type='lesson' AND target_id=?")->execute([$id]);
            $db->prepare('DELETE FROM lessons WHERE id=?')->execute([$id]);
        }
        $db->prepare('INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$actor['id'],$action === 'toggle' ? 'toggle-publication' : $action,'lesson',(string)$id]);
        $db->commit(); return (int)$id;
    } catch (Throwable $failure) {
        if ($db->inTransaction()) $db->rollBack();
        if ($failure instanceof PDOException && (int)($failure->errorInfo[1] ?? 0) === 1062) throw new RuntimeException('هذا الفيديو مسجل في درس آخر.');
        throw $failure;
    }
}
