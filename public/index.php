<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/app/helpers.php';
ini_set('display_errors', '0');
set_exception_handler(static function (Throwable $exception): void {
    error_log('[Athar] ' . $exception->getMessage());
    if (!headers_sent()) http_response_code(500);
    exit('حدث خطأ غير متوقع. حاول مرة أخرى لاحقًا.');
});

ini_set('session.use_strict_mode', '1');
session_set_cookie_params([
    'httponly' => true,
    'secure' => (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off'),
    'samesite' => 'Lax',
    'path' => '/',
]);
if (session_status() !== PHP_SESSION_ACTIVE) session_start();

header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: strict-origin-when-cross-origin');
header('X-Frame-Options: DENY');
header('Permissions-Policy: camera=(), microphone=(), geolocation=()');
header("Content-Security-Policy: default-src 'self'; img-src 'self' data: https://i.ytimg.com; media-src 'self' https://cdn.islamic.network https://cdn.jsdelivr.net; style-src 'self'; script-src 'self' https://www.youtube.com https://s.ytimg.com; connect-src 'self' https://api.quranpedia.net https://api.alquran.cloud https://pray.api.islamic.network https://ummahapi.com https://cdn.jsdelivr.net https://www.youtube.com; frame-src https://api.quranpedia.net https://www.youtube-nocookie.com https://www.youtube.com; object-src 'none'; base-uri 'self'; frame-ancestors 'none'");

$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$method = strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');
if (in_array($path, ['/login','/register','/account','/admin'], true)) header('Cache-Control: no-store, private');

if ($path === '/api/quran/progress' && $method === 'POST') {
    $user = require_login();
    require_csrf();
    $surah = filter_var($_POST['surah'] ?? null, FILTER_VALIDATE_INT);
    $ayah = filter_var($_POST['ayah'] ?? null, FILTER_VALIDATE_INT);
    $action = (string) ($_POST['action'] ?? 'position');
    if (!$surah || $surah < 1 || $surah > 114 || !$ayah || $ayah < 1 || $ayah > quran_ayah_count((int) $surah)) {
        http_response_code(422); exit('بيانات الآية غير صالحة.');
    }
    if ($action === 'bookmark') {
        db()->prepare('INSERT IGNORE INTO quran_bookmarks (user_id,surah,ayah) VALUES (?,?,?)')->execute([$user['id'], $surah, $ayah]);
    } else {
        db()->prepare('INSERT INTO quran_positions (user_id,surah,ayah) VALUES (?,?,?) ON DUPLICATE KEY UPDATE surah=VALUES(surah), ayah=VALUES(ayah)')->execute([$user['id'], $surah, $ayah]);
    }
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['ok' => true], JSON_UNESCAPED_UNICODE);
    exit;
}

if ($path === '/api/favorites' && $method === 'POST') {
    $user = require_login(); require_csrf();
    $type = (string) ($_POST['type'] ?? ''); $id = filter_var($_POST['id'] ?? null, FILTER_VALIDATE_INT);
    if (!in_array($type, ['book','lesson'], true) || !$id || $id < 1) { http_response_code(422); exit('بيانات العنصر غير صالحة.'); }
    $table = $type === 'book' ? 'books' : 'lessons';
    $exists = db()->prepare("SELECT id FROM {$table} WHERE id=? AND is_published=1 LIMIT 1"); $exists->execute([$id]);
    if (!$exists->fetchColumn()) { http_response_code(404); exit('المحتوى غير متاح.'); }
    $saved = db()->prepare('SELECT id FROM user_favorites WHERE user_id=? AND target_type=? AND target_id=?'); $saved->execute([$user['id'], $type, $id]);
    if ($saved->fetchColumn()) { db()->prepare('DELETE FROM user_favorites WHERE user_id=? AND target_type=? AND target_id=?')->execute([$user['id'], $type, $id]); $isSaved = false; }
    else { db()->prepare('INSERT IGNORE INTO user_favorites (user_id,target_type,target_id) VALUES (?,?,?)')->execute([$user['id'], $type, $id]); $isSaved = true; }
    header('Content-Type: application/json; charset=utf-8'); echo json_encode(['ok' => true, 'saved' => $isSaved]); exit;
}

if ($path === '/api/lesson/progress' && $method === 'POST') {
    $user = require_login(); require_csrf();
    $lessonId = filter_var($_POST['lesson_id'] ?? null, FILTER_VALIDATE_INT);
    $position = filter_var($_POST['position'] ?? null, FILTER_VALIDATE_INT);
    $duration = filter_var($_POST['duration'] ?? null, FILTER_VALIDATE_INT);
    $complete = ($_POST['complete'] ?? '') === '1';
    if (!$lessonId || $lessonId < 1 || $position === false || $duration === false || $position < 0 || $duration < 0 || $position > 86400 || $duration > 86400 || ($duration > 0 && $position > $duration + 5)) { http_response_code(422); exit('بيانات التقدم غير صالحة.'); }
    $lesson = db()->prepare('SELECT id FROM lessons WHERE id=? AND is_published=1'); $lesson->execute([$lessonId]);
    if (!$lesson->fetchColumn()) { http_response_code(404); exit('الدرس غير متاح.'); }
    $complete = $complete && $duration > 0 && $position >= $duration - 10;
    $last = db()->prepare('SELECT UNIX_TIMESTAMP(updated_at) FROM video_progress WHERE user_id=? AND lesson_id=?'); $last->execute([$user['id'], $lessonId]);
    $lastSaved = $last->fetchColumn();
    if (!$complete && $lastSaved && time() - (int) $lastSaved < 25) { header('Content-Type: application/json; charset=utf-8'); echo json_encode(['ok' => true, 'throttled' => true]); exit; }
    db()->prepare('INSERT INTO video_progress (user_id,lesson_id,position_seconds,duration_seconds,is_complete) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE position_seconds=VALUES(position_seconds),duration_seconds=VALUES(duration_seconds),is_complete=VALUES(is_complete)')->execute([$user['id'], $lessonId, $position, $duration, $complete ? 1 : 0]);
    header('Content-Type: application/json; charset=utf-8'); echo json_encode(['ok' => true]); exit;
}

if ($path === '/book-download' && $method === 'GET') {
    $id = filter_var($_GET['id'] ?? null, FILTER_VALIDATE_INT);
    if (!$id || $id < 1) { http_response_code(404); exit('الملف غير موجود.'); }
    $statement = db()->prepare('SELECT title,file_key,file_size FROM books WHERE id=? AND is_published=1 AND can_download=1 LIMIT 1');
    $statement->execute([$id]); $book = $statement->fetch();
    if (!$book || !is_string($book['file_key']) || !preg_match('/^[a-f0-9]{40}\.pdf$/', $book['file_key'])) { http_response_code(404); exit('الملف غير متاح للتنزيل.'); }
    $filePath = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'books' . DIRECTORY_SEPARATOR . $book['file_key'];
    if (!is_file($filePath)) { http_response_code(404); exit('الملف غير موجود.'); }
    header('Content-Type: application/pdf'); header('X-Content-Type-Options: nosniff');
    header('Content-Disposition: attachment; filename="athar-book-' . (int) $book['id'] . '.pdf"');
    header('Content-Length: ' . filesize($filePath)); readfile($filePath); exit;
}

if (in_array($path, ['/register', '/login'], true)) {
    $authError = '';
    if ($method === 'POST') {
        require_csrf();
        $now = time();
        $attempts = $_SESSION['auth_attempts'] ?? ['started' => $now, 'count' => 0];
        if (!is_array($attempts) || $now - (int) ($attempts['started'] ?? 0) > 900) $attempts = ['started' => $now, 'count' => 0];
        $attempts['count'] = (int) ($attempts['count'] ?? 0) + 1;
        $_SESSION['auth_attempts'] = $attempts;
        $email = strtolower(trim((string) ($_POST['email'] ?? '')));
        $password = (string) ($_POST['password'] ?? '');
        if ($attempts['count'] > 8) {
            $authError = 'محاولات كثيرة. انتظر 15 دقيقة ثم حاول مجددًا.';
        } elseif (!filter_var($email, FILTER_VALIDATE_EMAIL) || strlen($email) > 190 || strlen($password) < 10 || strlen($password) > 200) {
            $authError = 'أدخل بريدًا صالحًا وكلمة مرور من 10 أحرف على الأقل.';
        } else {
            try {
                if ($path === '/register') {
                    $statement = db()->prepare('INSERT INTO users (email,password_hash) VALUES (?,?)');
                    $statement->execute([$email, password_hash($password, PASSWORD_DEFAULT)]);
                    $id = (int) db()->lastInsertId();
                } else {
                    $statement = db()->prepare('SELECT id,email,password_hash,role FROM users WHERE email=? LIMIT 1');
                    $statement->execute([$email]);
                    $record = $statement->fetch();
                    if (!$record || !password_verify($password, $record['password_hash'])) {
                        $authError = 'تعذر تسجيل الدخول بهذه البيانات.';
                    } else {
                        if (password_needs_rehash($record['password_hash'], PASSWORD_DEFAULT)) {
                            db()->prepare('UPDATE users SET password_hash=? WHERE id=?')->execute([password_hash($password, PASSWORD_DEFAULT), $record['id']]);
                        }
                        $id = (int) $record['id'];
                    }
                }
                if ($authError === '') {
                    $statement = db()->prepare('SELECT id,email,role FROM users WHERE id=?');
                    $statement->execute([$id]);
                    session_regenerate_id(true);
                    $_SESSION['user'] = $statement->fetch();
                    $_SESSION['csrf'] = bin2hex(random_bytes(32));
                    $_SESSION['authenticated_at'] = time();
                    $_SESSION['last_activity'] = time();
                    unset($_SESSION['auth_attempts']);
                    redirect('/account');
                }
            } catch (PDOException $exception) {
                $authError = $path === '/register' && $exception->getCode() === '23000'
                    ? 'تعذر إنشاء الحساب بهذه البيانات.'
                    : 'تعذر الاتصال بقاعدة البيانات. راجع إعدادات التشغيل.';
            }
        }
    }
    $title = $path === '/login' ? 'تسجيل الدخول' : 'إنشاء حساب';
    $activePath = $path;
    ob_start();
    require dirname(__DIR__) . '/app/Views/pages/auth.php';
    $content = (string) ob_get_clean();
    require dirname(__DIR__) . '/app/Views/layout.php';
    exit;
}

if ($path === '/logout' && $method === 'POST') {
    require_login(); require_csrf();
    $_SESSION = [];
    if (ini_get('session.use_cookies')) {
        $params = session_get_cookie_params();
        setcookie(session_name(), '', time() - 42000, $params['path'], '', $params['secure'], $params['httponly']);
    }
    session_destroy();
    redirect('/');
}

if ($path === '/account') {
    $user = require_login();
    $accountError = '';
    if ($method === 'POST') {
        require_csrf();
        $action = (string) ($_POST['action'] ?? '');
        if ($action === 'plan-status') {
            $status = (string) ($_POST['status'] ?? '');
            if (!in_array($status, ['active','paused','finished'], true)) $accountError = 'حالة الخطة غير صالحة.';
            else { db()->prepare('UPDATE khatmah_plans SET status=? WHERE user_id=?')->execute([$status, $user['id']]); flash('تم تحديث حالة الخطة.'); redirect('/account'); }
        } elseif ($action === 'plan') {
            $target = (string) ($_POST['target_date'] ?? '');
            $completed = filter_var($_POST['completed_pages'] ?? 0, FILTER_VALIDATE_INT);
            $date = DateTimeImmutable::createFromFormat('!Y-m-d', $target);
            if (!$date || $date <= new DateTimeImmutable('today') || $completed === false || $completed < 0 || $completed > 604) {
                $accountError = 'اختر تاريخًا مستقبليًا وكمية صفحات بين 0 و604.';
            } else {
                db()->prepare("INSERT INTO khatmah_plans (user_id,start_date,target_date,completed_pages,status) VALUES (?,?,?,?, 'active') ON DUPLICATE KEY UPDATE target_date=VALUES(target_date),completed_pages=VALUES(completed_pages),status='active'")->execute([$user['id'], date('Y-m-d'), $target, $completed]);
                flash('تم تحديث خطة الختمة.'); redirect('/account');
            }
        } elseif ($action === 'bookmark-delete') {
            db()->prepare('DELETE FROM quran_bookmarks WHERE id=? AND user_id=?')->execute([(int) ($_POST['id'] ?? 0), $user['id']]);
            flash('تم حذف العلامة.'); redirect('/account');
        } elseif ($action === 'delete-account') {
            $password = (string) ($_POST['password'] ?? '');
            $statement = db()->prepare('SELECT password_hash FROM users WHERE id=?');
            $statement->execute([$user['id']]);
            $hash = (string) $statement->fetchColumn();
            if ($hash === '' || !password_verify($password, $hash)) $accountError = 'كلمة المرور غير صحيحة.';
            else {
                db()->prepare('DELETE FROM users WHERE id=?')->execute([$user['id']]);
                $_SESSION = []; session_destroy(); redirect('/');
            }
        }
    }
    if (isset($_GET['export'])) {
        $statement = db()->prepare('SELECT surah,ayah,label,created_at FROM quran_bookmarks WHERE user_id=? ORDER BY created_at');
        $statement->execute([$user['id']]); $bookmarks = $statement->fetchAll();
        $statement = db()->prepare('SELECT surah,ayah,updated_at FROM quran_positions WHERE user_id=?');
        $statement->execute([$user['id']]); $position = $statement->fetch() ?: null;
        $statement = db()->prepare('SELECT start_date,target_date,total_pages,completed_pages,status FROM khatmah_plans WHERE user_id=?');
        $statement->execute([$user['id']]); $plan = $statement->fetch() ?: null;
        $statement = db()->prepare('SELECT l.title,l.youtube_id,v.position_seconds,v.duration_seconds,v.is_complete,v.updated_at FROM video_progress v JOIN lessons l ON l.id=v.lesson_id WHERE v.user_id=? ORDER BY v.updated_at DESC');
        $statement->execute([$user['id']]); $videoProgress = $statement->fetchAll();
        $statement = db()->prepare("SELECT f.target_type,f.created_at,b.title FROM user_favorites f JOIN books b ON f.target_id=b.id WHERE f.user_id=? AND f.target_type='book' UNION ALL SELECT f.target_type,f.created_at,l.title FROM user_favorites f JOIN lessons l ON f.target_id=l.id WHERE f.user_id=? AND f.target_type='lesson'");
        $statement->execute([$user['id'], $user['id']]); $favoriteExport = $statement->fetchAll();
        header('Content-Type: application/json; charset=utf-8');
        header('Content-Disposition: attachment; filename="athar-account-export.json"');
        echo json_encode(['email' => $user['email'], 'bookmarks' => $bookmarks, 'quran_position' => $position, 'khatmah_plan' => $plan, 'video_progress' => $videoProgress, 'favorites' => $favoriteExport], JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE); exit;
    }
    $statement = db()->prepare('SELECT id,surah,ayah,label,created_at FROM quran_bookmarks WHERE user_id=? ORDER BY created_at DESC');
    $statement->execute([$user['id']]); $bookmarks = $statement->fetchAll();
    $statement = db()->prepare('SELECT surah,ayah,updated_at FROM quran_positions WHERE user_id=?');
    $statement->execute([$user['id']]); $position = $statement->fetch() ?: null;
    $statement = db()->prepare('SELECT * FROM khatmah_plans WHERE user_id=? ORDER BY id DESC LIMIT 1');
    $statement->execute([$user['id']]); $plan = $statement->fetch() ?: null;
    $statement = db()->prepare('SELECT l.id,l.title,l.youtube_id,v.position_seconds,v.duration_seconds,v.is_complete,v.updated_at FROM video_progress v JOIN lessons l ON l.id=v.lesson_id WHERE v.user_id=? ORDER BY v.updated_at DESC');
    $statement->execute([$user['id']]); $videoProgress = $statement->fetchAll();
    $statement = db()->prepare("SELECT b.id,b.title,b.author,'book' AS target_type FROM user_favorites f JOIN books b ON f.target_id=b.id WHERE f.user_id=? AND f.target_type='book' AND b.is_published=1 UNION ALL SELECT l.id,l.title,s.name AS author,'lesson' AS target_type FROM user_favorites f JOIN lessons l ON f.target_id=l.id LEFT JOIN scholars s ON s.id=l.scholar_id WHERE f.user_id=? AND f.target_type='lesson' AND l.is_published=1 ORDER BY title");
    $statement->execute([$user['id'], $user['id']]); $favorites = $statement->fetchAll();
    $title = 'حسابي'; $activePath = $path; $viewPath = dirname(__DIR__) . '/app/Views/pages/account.php';
    $flashMessage = take_flash();
    ob_start(); require $viewPath; $content = (string) ob_get_clean(); require dirname(__DIR__) . '/app/Views/layout.php'; exit;
}

if ($path === '/admin') {
    $admin = require_admin();
    $adminError = '';
    if ($method === 'POST') {
        require_csrf();
        $kind = (string) ($_POST['kind'] ?? '');
        try {
            if ($kind === 'content') {
                $table = ['scholar' => 'scholars', 'series' => 'series', 'lesson' => 'lessons', 'book' => 'books', 'adhkar' => 'adhkar_items'][(string) ($_POST['content_type'] ?? '')] ?? '';
                $id = filter_var($_POST['id'] ?? null, FILTER_VALIDATE_INT);
                if ($table === '' || !$id || $id < 1) throw new RuntimeException('عنصر المحتوى غير صالح.');
                $type = (string) $_POST['content_type'];
                if (($_POST['content_action'] ?? '') === 'update') {
                    $published = isset($_POST['publish']) ? 1 : 0;
                    if ($type === 'scholar') {
                        $source = trim((string) ($_POST['source_url'] ?? ''));
                        if (!is_https_url($source) || trim((string) ($_POST['rights_basis'] ?? '')) === '') throw new RuntimeException('أدخل مصدرًا آمنًا وأساس الحقوق.');
                        db()->prepare('UPDATE scholars SET name=?,biography=?,source_url=?,rights_basis=?,is_published=? WHERE id=?')->execute([trim((string) $_POST['name']), trim((string) $_POST['biography']), $source, trim((string) $_POST['rights_basis']), $published, $id]);
                    } elseif ($type === 'lesson') {
                        $url = trim((string) ($_POST['source_url'] ?? '')); $videoId = youtube_video_id($url);
                        if (!$videoId) throw new RuntimeException('رابط فيديو YouTube غير صالح.');
                        db()->prepare('UPDATE lessons SET scholar_id=NULLIF(?,0),series_id=NULLIF(?,0),title=?,description=?,youtube_id=?,source_url=?,is_published=? WHERE id=?')->execute([(int) ($_POST['scholar_id'] ?? 0), (int) ($_POST['series_id'] ?? 0), trim((string) $_POST['title']), trim((string) $_POST['description']), $videoId, $url, $published, $id]);
                    } elseif ($type === 'series') {
                        $source = trim((string) ($_POST['source_url'] ?? ''));
                        if ($source !== '' && !is_https_url($source)) throw new RuntimeException('رابط المصدر يجب أن يبدأ بـ HTTPS.');
                        db()->prepare('UPDATE series SET scholar_id=NULLIF(?,0),title=?,description=?,source_url=?,is_published=? WHERE id=?')->execute([(int) ($_POST['scholar_id'] ?? 0), trim((string) $_POST['title']), trim((string) $_POST['description']), $source ?: null, $published, $id]);
                    } elseif ($type === 'book') {
                        $source = trim((string) ($_POST['source_url'] ?? '')); $rights = trim((string) ($_POST['rights_basis'] ?? ''));
                        if (!is_https_url($source) || $rights === '') throw new RuntimeException('أدخل مصدرًا آمنًا وأساس الحقوق.');
                        $current = db()->prepare('SELECT file_key FROM books WHERE id=?'); $current->execute([$id]); $previousKey = $current->fetchColumn();
                        if ($previousKey === false) throw new RuntimeException('الكتاب غير موجود.');
                        $nextKey = $previousKey ?: null; $nextSize = null; $storage = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'books';
                        if (isset($_FILES['book_pdf']) && ($_FILES['book_pdf']['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_NO_FILE) {
                            $uploaded = store_book_pdf($_FILES['book_pdf'], $storage); $nextKey = $uploaded['key']; $nextSize = $uploaded['size'];
                        }
                        $canDownload = isset($_POST['can_download']) && $nextKey ? 1 : 0;
                        try {
                            db()->prepare('UPDATE books SET title=?,author=?,description=?,source_url=?,rights_basis=?,file_key=?,file_size=COALESCE(?,file_size),can_download=?,is_published=? WHERE id=?')->execute([trim((string) $_POST['title']), trim((string) $_POST['author']), trim((string) $_POST['description']), $source, $rights, $nextKey, $nextSize, $canDownload, $published, $id]);
                        } catch (Throwable $saveFailure) {
                            if ($nextKey !== $previousKey && is_string($nextKey)) { $newPath = $storage . DIRECTORY_SEPARATOR . $nextKey; if (is_file($newPath)) unlink($newPath); }
                            throw $saveFailure;
                        }
                        if ($nextKey !== $previousKey && is_string($previousKey) && preg_match('/^[a-f0-9]{40}\.pdf$/', $previousKey)) { $oldPath = $storage . DIRECTORY_SEPARATOR . $previousKey; if (is_file($oldPath)) unlink($oldPath); }
                    } elseif ($type === 'adhkar') {
                        $source = trim((string) ($_POST['source_url'] ?? ''));
                        if (!is_https_url($source) || trim((string) ($_POST['source_reference'] ?? '')) === '' || !isset($_POST['reviewed'])) throw new RuntimeException('أدخل مصدرًا آمنًا ومرجعًا وأكد المراجعة.');
                        $count = ($_POST['repeat_count'] ?? '') === '' ? null : max(1, min(1000, (int) $_POST['repeat_count']));
                        db()->prepare('UPDATE adhkar_items SET title=?,formula=?,source_reference=?,source_url=?,repeat_count=?,category=?,is_reviewed=1,is_published=? WHERE id=?')->execute([trim((string) $_POST['title']), trim((string) $_POST['formula']), trim((string) $_POST['source_reference']), $source, $count, trim((string) $_POST['category']), $published, $id]);
                    }
                    $auditAction = 'update';
                } elseif (($_POST['content_action'] ?? '') === 'toggle') {
                    db()->prepare("UPDATE {$table} SET is_published=1-is_published WHERE id=?")->execute([$id]);
                    $auditAction = 'toggle-publication';
                } elseif (($_POST['content_action'] ?? '') === 'delete') {
                    $fileKey = null;
                    if ($table === 'books') {
                        $fileQuery = db()->prepare('SELECT file_key FROM books WHERE id=?'); $fileQuery->execute([$id]); $fileKey = $fileQuery->fetchColumn();
                    }
                    db()->prepare("DELETE FROM {$table} WHERE id=?")->execute([$id]);
                    if (is_string($fileKey) && preg_match('/^[a-f0-9]{40}\.pdf$/', $fileKey)) {
                        $storedFile = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'books' . DIRECTORY_SEPARATOR . $fileKey;
                        if (is_file($storedFile)) unlink($storedFile);
                    }
                    $auditAction = 'delete';
                } else throw new RuntimeException('الإجراء غير معروف.');
                db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'], $auditAction, $type, (string) $id]);
                flash('تم تحديث المحتوى.'); redirect('/admin');
            } elseif ($kind === 'scholar') {
                if (!is_https_url(trim((string) $_POST['source_url']))) throw new RuntimeException('يجب إدخال رابط مصدر آمن يبدأ بـ HTTPS.');
                db()->prepare('INSERT INTO scholars (name,biography,language,source_url,rights_basis,is_published,sort_order) VALUES (?,?,?,?,?,?,?)')->execute([trim((string) $_POST['name']), trim((string) $_POST['biography']), 'ar', trim((string) $_POST['source_url']), trim((string) $_POST['rights_basis']), isset($_POST['publish']) ? 1 : 0, (int) ($_POST['sort_order'] ?? 0)]);
            } elseif ($kind === 'series') {
                $source = trim((string) ($_POST['source_url'] ?? ''));
                if (strlen(trim((string) $_POST['title'])) < 2 || ($source !== '' && !is_https_url($source))) throw new RuntimeException('أدخل عنوانًا ورابط مصدر HTTPS صالحًا عند الحاجة.');
                db()->prepare('INSERT INTO series (scholar_id,title,description,source_url,is_published,sort_order) VALUES (NULLIF(?,0),?,?,?,?,?)')->execute([(int) ($_POST['scholar_id'] ?? 0), trim((string) $_POST['title']), trim((string) $_POST['description']), $source ?: null, isset($_POST['publish']) ? 1 : 0, (int) ($_POST['sort_order'] ?? 0)]);
            } elseif ($kind === 'lesson') {
                $url = trim((string) $_POST['source_url']); $id = youtube_video_id($url);
                if (!$id || strlen(trim((string) $_POST['title'])) < 2) throw new RuntimeException('رابط الفيديو أو العنوان غير صالح.');
                db()->prepare('INSERT INTO lessons (scholar_id,series_id,title,description,youtube_id,source_url,sort_order,is_published) VALUES (NULLIF(?,0),NULLIF(?,0),?,?,?,?,?,?)')->execute([(int) ($_POST['scholar_id'] ?? 0), (int) ($_POST['series_id'] ?? 0), trim((string) $_POST['title']), trim((string) $_POST['description']), $id, $url, (int) ($_POST['sort_order'] ?? 0), isset($_POST['publish']) ? 1 : 0]);
            } elseif ($kind === 'book') {
                if (trim((string) $_POST['rights_basis']) === '' || !is_https_url(trim((string) $_POST['source_url']))) throw new RuntimeException('يلزم توثيق مصدر آمن وأساس حقوق الإتاحة.');
                $fileKey = null; $fileSize = null;
                $storage = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'books';
                if (isset($_FILES['book_pdf']) && ($_FILES['book_pdf']['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_NO_FILE) {
                    $uploaded = store_book_pdf($_FILES['book_pdf'], $storage); $fileKey = $uploaded['key']; $fileSize = $uploaded['size'];
                }
                $canDownload = isset($_POST['can_download']) && $fileKey ? 1 : 0;
                try {
                    db()->prepare('INSERT INTO books (title,author,description,source_url,rights_basis,file_key,file_size,can_download,is_published) VALUES (?,?,?,?,?,?,?,?,?)')->execute([trim((string) $_POST['title']), trim((string) $_POST['author']), trim((string) $_POST['description']), trim((string) $_POST['source_url']), trim((string) $_POST['rights_basis']), $fileKey, $fileSize, $canDownload, isset($_POST['publish']) ? 1 : 0]);
                } catch (Throwable $saveFailure) {
                    if ($fileKey && is_file($storage . DIRECTORY_SEPARATOR . $fileKey)) unlink($storage . DIRECTORY_SEPARATOR . $fileKey);
                    throw $saveFailure;
                }
            } elseif ($kind === 'adhkar') {
                if (trim((string) $_POST['source_reference']) === '' || !is_https_url(trim((string) $_POST['source_url'])) || !isset($_POST['reviewed'])) throw new RuntimeException('لا يمكن نشر الذكر دون مصدر آمن ومراجعة موثقة.');
                db()->prepare('INSERT INTO adhkar_items (title,formula,source_reference,source_url,repeat_count,category,is_reviewed,is_published,sort_order) VALUES (?,?,?,?,?,?,?,?,?)')->execute([trim((string) $_POST['title']), trim((string) $_POST['formula']), trim((string) $_POST['source_reference']), trim((string) $_POST['source_url']), ($_POST['repeat_count'] ?? '') === '' ? null : (int) $_POST['repeat_count'], trim((string) $_POST['category']), 1, isset($_POST['publish']) ? 1 : 0, (int) ($_POST['sort_order'] ?? 0)]);
            } else { throw new RuntimeException('نوع المحتوى غير مدعوم.'); }
            db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'], 'create', $kind, (string) db()->lastInsertId()]);
            flash('تم حفظ المحتوى.'); redirect('/admin');
        } catch (Throwable $exception) { $adminError = $exception instanceof RuntimeException ? $exception->getMessage() : 'تعذر حفظ المحتوى. راجع الحقول أو الاتصال بقاعدة البيانات.'; }
    }
    $scholars = db()->query('SELECT id,name,is_published FROM scholars ORDER BY sort_order,id DESC')->fetchAll();
    $seriesItems = db()->query('SELECT * FROM series ORDER BY sort_order,id DESC')->fetchAll();
    $lessons = db()->query('SELECT l.*,s.name AS scholar_name FROM lessons l LEFT JOIN scholars s ON s.id=l.scholar_id ORDER BY l.sort_order,l.id DESC')->fetchAll();
    $books = db()->query('SELECT * FROM books ORDER BY id DESC')->fetchAll();
    $adhkarItems = db()->query('SELECT * FROM adhkar_items ORDER BY sort_order,id DESC')->fetchAll();
    $title='إدارة أثر'; $activePath=$path; $viewPath=dirname(__DIR__).'/app/Views/pages/admin.php'; $flashMessage=take_flash();
    ob_start(); require $viewPath; $content=(string) ob_get_clean(); require dirname(__DIR__).'/app/Views/layout.php'; exit;
}

$curatedBooks = $curatedLessons = $curatedAdhkar = [];
try {
    if ($path === '/books') {
        $user = current_user();
        $query = 'SELECT b.id,b.title,b.author,b.description,b.source_url,b.can_download,b.file_key,(SELECT COUNT(*) FROM user_favorites f WHERE f.user_id=? AND f.target_type=\'book\' AND f.target_id=b.id) AS is_favorite FROM books b WHERE b.is_published=1 ORDER BY b.title LIMIT 50';
        $statement = db()->prepare($query); $statement->execute([(int) ($user['id'] ?? 0)]); $curatedBooks = $statement->fetchAll();
    }
    if ($path === '/lessons') {
        $user = current_user();
        $query = 'SELECT l.id,l.title,l.description,l.youtube_id,l.source_url,l.series_id,s.name AS scholar_name,r.title AS series_name,(SELECT position_seconds FROM video_progress vp WHERE vp.user_id=? AND vp.lesson_id=l.id) AS position_seconds,(SELECT COUNT(*) FROM user_favorites f WHERE f.user_id=? AND f.target_type=\'lesson\' AND f.target_id=l.id) AS is_favorite FROM lessons l LEFT JOIN scholars s ON s.id=l.scholar_id LEFT JOIN series r ON r.id=l.series_id AND r.is_published=1 WHERE l.is_published=1 ORDER BY r.sort_order,r.id,l.sort_order,l.id DESC LIMIT 50';
        $statement = db()->prepare($query); $statement->execute([(int) ($user['id'] ?? 0), (int) ($user['id'] ?? 0)]); $curatedLessons = $statement->fetchAll();
    }
    if ($path === '/adhkar') $curatedAdhkar = db()->query('SELECT title,formula,source_reference,source_url,repeat_count,category FROM adhkar_items WHERE is_published=1 AND is_reviewed=1 ORDER BY category,sort_order,id DESC LIMIT 100')->fetchAll();
} catch (Throwable $exception) { /* The live provider pages remain available before local database setup. */ }

$pages = [
    '/' => ['title' => 'الرئيسية', 'view' => 'pages/home.php'],
    '/quran' => ['title' => 'القرآن الكريم', 'view' => 'pages/quran.php'],
    '/hadith' => ['title' => 'الحديث', 'view' => 'pages/hadith.php'],
    '/books' => ['title' => 'الكتب', 'view' => 'pages/books.php'],
    '/lessons' => ['title' => 'الدروس', 'view' => 'pages/lessons.php'],
    '/adhkar' => ['title' => 'الأذكار', 'view' => 'pages/adhkar.php'],
    '/about' => ['title' => 'عن أثر', 'view' => 'pages/about.php'],
];

$page = $pages[$path] ?? null;

if ($page === null) {
    http_response_code(404);
    $page = ['title' => 'الصفحة غير موجودة', 'view' => 'pages/not-found.php'];
}

$title = $page['title'];
$activePath = $path;
$user = current_user();
$csrfToken = csrf_token();
$flashMessage = take_flash();
$viewPath = dirname(__DIR__) . '/app/Views/' . $page['view'];

ob_start();
require $viewPath;
$content = (string) ob_get_clean();

require dirname(__DIR__) . '/app/Views/layout.php';
