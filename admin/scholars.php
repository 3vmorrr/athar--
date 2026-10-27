<?php
declare(strict_types=1);

function admin_scholar_fields(array $input): array
{
    $fields = [];
    foreach (['name','biography','language','source_url','rights_basis','sort_order'] as $key) {
        $value = $input[$key] ?? ($key === 'language' ? 'ar' : ($key === 'sort_order' ? '0' : ''));
        if (!is_string($value)) throw new RuntimeException('بيانات العالم غير صالحة.');
        $fields[$key] = trim($value);
    }
    if (mb_strlen($fields['name'], 'UTF-8') < 2 || mb_strlen($fields['name'], 'UTF-8') > 180) throw new RuntimeException('اسم العالم يجب أن يكون من 2 إلى 180 حرفًا.');
    if (mb_strlen($fields['biography'], 'UTF-8') > 6000 || mb_strlen($fields['rights_basis'], 'UTF-8') > 500) throw new RuntimeException('السيرة أو بيانات الحقوق أطول من الحد المسموح.');
    $source = $fields['source_url']; $parts = $source === '' ? [] : parse_url($source);
    if (!in_array($fields['language'], ['ar','en','other'], true) || ($source !== '' && (!is_https_url($source) || isset($parts['user']) || isset($parts['pass'])))) throw new RuntimeException('لغة العالم أو رابط المصدر غير صالح.');
    $sortOrder = filter_var($fields['sort_order'], FILTER_VALIDATE_INT, ['options'=>['min_range'=>-100000,'max_range'=>100000]]);
    if ($sortOrder === false) throw new RuntimeException('ترتيب العرض يجب أن يكون عددًا صحيحًا بين -100000 و100000.');
    return [$fields['name'],$fields['biography'],$fields['language'],$source,$fields['rights_basis'],$sortOrder,isset($input['publish']) ? 1 : 0];
}

/** All scholar mutations retain their file/audit/row boundary as one operation. */
function admin_mutate_scholar(array $actor, array $input, array $files): int
{
    if (!admin_can($actor, 'content')) admin_deny_action($actor, 'admin-action', 'scholar');
    $create = ($input['kind'] ?? '') === 'scholar';
    $action = $create ? 'create' : ($input['content_action'] ?? '');
    if (!in_array($action, ['create','update','toggle','delete'], true)) throw new RuntimeException('الإجراء غير معروف.');
    $id = $create ? 0 : filter_var($input['id'] ?? null, FILTER_VALIDATE_INT);
    if (!$create && (!$id || $id < 1)) throw new RuntimeException('عنصر المحتوى غير صالح.');
    $fields = in_array($action, ['create','update'], true) ? admin_scholar_fields($input) : [];
    $db = db(); $storage = dirname(__DIR__) . '/storage/scholars';
    $newImage = null; $previousImage = null;
    $db->beginTransaction();
    try {
        if (!$create) {
            $query = $db->prepare('SELECT image_key FROM scholars WHERE id=? FOR UPDATE'); $query->execute([$id]); $row = $query->fetch();
            if (!$row) throw new RuntimeException('العالم غير موجود.');
            $previousImage = $row['image_key'];
        }
        if (in_array($action, ['create','update'], true)) {
            $nextImage = $previousImage;
            if (isset($files['scholar_image']) && ($files['scholar_image']['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_NO_FILE) $nextImage = $newImage = store_scholar_image($files['scholar_image'], $storage);
            [$name,$bio,$language,$source,$rights,$sort,$published] = $fields;
            if ($create) {
                $db->prepare("INSERT INTO scholars(name,biography,language,source_url,rights_basis,sort_order,is_published,image_key) VALUES (?,NULLIF(?,''),?,NULLIF(?,''),NULLIF(?,''),?,?,?)")->execute([$name,$bio,$language,$source,$rights,$sort,$published,$nextImage]);
                $id = (int) $db->lastInsertId();
            } else {
                $db->prepare("UPDATE scholars SET name=?,biography=NULLIF(?,''),language=?,source_url=NULLIF(?,''),rights_basis=NULLIF(?,''),sort_order=?,is_published=?,image_key=? WHERE id=?")->execute([$name,$bio,$language,$source,$rights,$sort,$published,$nextImage,$id]);
            }
        } elseif ($action === 'toggle') {
            $db->prepare('UPDATE scholars SET is_published=1-is_published WHERE id=?')->execute([$id]);
        } else {
            $dependents = $db->prepare('SELECT (SELECT COUNT(*) FROM series WHERE scholar_id=?)+(SELECT COUNT(*) FROM lessons WHERE scholar_id=?)'); $dependents->execute([$id,$id]);
            if ((int) $dependents->fetchColumn() !== 0) throw new RuntimeException('لا يمكن حذف عالم مرتبط بسلاسل أو دروس. أعد ربط المحتوى أولًا.');
            $db->prepare('DELETE FROM scholars WHERE id=?')->execute([$id]);
        }
        $db->prepare('INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$actor['id'],$action === 'toggle' ? 'toggle-publication' : $action,'scholar',(string)$id]);
        $db->commit();
    } catch (Throwable $failure) {
        if ($db->inTransaction()) $db->rollBack();
        if ($newImage !== null) delete_scholar_image($newImage, $storage);
        throw $failure;
    }
    if ($previousImage !== null && ($action === 'delete' || $newImage !== null)) {
        $references = $db->prepare('SELECT COUNT(*) FROM scholars WHERE image_key=?'); $references->execute([$previousImage]);
        if ((int) $references->fetchColumn() === 0) delete_scholar_image($previousImage, $storage);
    }
    return (int) $id;
}
