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
header("Content-Security-Policy: default-src 'self'; img-src 'self' data: https://i.ytimg.com; media-src 'self' https://cdn.islamic.network https://cdn.jsdelivr.net; style-src 'self'; script-src 'self'; connect-src 'self' https://api.quranpedia.net https://api.alquran.cloud https://pray.api.islamic.network https://ummahapi.com https://cdn.jsdelivr.net; frame-src https://api.quranpedia.net https://www.youtube-nocookie.com https://www.youtube.com; object-src 'none'; base-uri 'self'; frame-ancestors 'none'");

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
        header('Content-Type: application/json; charset=utf-8');
        header('Content-Disposition: attachment; filename="athar-account-export.json"');
        echo json_encode(['email' => $user['email'], 'bookmarks' => $bookmarks, 'quran_position' => $position, 'khatmah_plan' => $plan], JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE); exit;
    }
    $statement = db()->prepare('SELECT id,surah,ayah,label,created_at FROM quran_bookmarks WHERE user_id=? ORDER BY created_at DESC');
    $statement->execute([$user['id']]); $bookmarks = $statement->fetchAll();
    $statement = db()->prepare('SELECT surah,ayah,updated_at FROM quran_positions WHERE user_id=?');
    $statement->execute([$user['id']]); $position = $statement->fetch() ?: null;
    $statement = db()->prepare('SELECT * FROM khatmah_plans WHERE user_id=? ORDER BY id DESC LIMIT 1');
    $statement->execute([$user['id']]); $plan = $statement->fetch() ?: null;
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
                $table = ['scholar' => 'scholars', 'lesson' => 'lessons', 'book' => 'books', 'adhkar' => 'adhkar_items'][(string) ($_POST['content_type'] ?? '')] ?? '';
                $id = filter_var($_POST['id'] ?? null, FILTER_VALIDATE_INT);
                if ($table === '' || !$id || $id < 1) throw new RuntimeException('عنصر المحتوى غير صالح.');
                if (($_POST['content_action'] ?? '') === 'toggle') {
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
                db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'], $auditAction, (string) $_POST['content_type'], (string) $id]);
                flash('تم تحديث المحتوى.'); redirect('/admin');
            } elseif ($kind === 'scholar') {
                if (!is_https_url(trim((string) $_POST['source_url']))) throw new RuntimeException('يجب إدخال رابط مصدر آمن يبدأ بـ HTTPS.');
                db()->prepare('INSERT INTO scholars (name,biography,language,source_url,rights_basis,is_published,sort_order) VALUES (?,?,?,?,?,?,?)')->execute([trim((string) $_POST['name']), trim((string) $_POST['biography']), 'ar', trim((string) $_POST['source_url']), trim((string) $_POST['rights_basis']), isset($_POST['publish']) ? 1 : 0, (int) ($_POST['sort_order'] ?? 0)]);
            } elseif ($kind === 'lesson') {
                $url = trim((string) $_POST['source_url']); $host = strtolower((string) parse_url($url, PHP_URL_HOST)); $id = '';
                if (in_array($host, ['www.youtube.com','youtube.com','m.youtube.com'], true) && parse_url($url, PHP_URL_PATH) === '/watch') $id = (string) (parse_url($url, PHP_URL_QUERY) ? (function ($q) { parse_str($q, $p); return $p['v'] ?? ''; })(parse_url($url, PHP_URL_QUERY)) : '');
                if ($host === 'youtu.be') $id = trim((string) parse_url($url, PHP_URL_PATH), '/');
                if (!preg_match('/^[A-Za-z0-9_-]{11}$/', $id) || strlen(trim((string) $_POST['title'])) < 2) throw new RuntimeException('رابط الفيديو أو العنوان غير صالح.');
                db()->prepare('INSERT INTO lessons (scholar_id,title,description,youtube_id,source_url,sort_order,is_published) VALUES (NULLIF(?,0),?,?,?,?,?,?)')->execute([(int) ($_POST['scholar_id'] ?? 0), trim((string) $_POST['title']), trim((string) $_POST['description']), $id, $url, (int) ($_POST['sort_order'] ?? 0), isset($_POST['publish']) ? 1 : 0]);
            } elseif ($kind === 'book') {
                if (trim((string) $_POST['rights_basis']) === '' || !is_https_url(trim((string) $_POST['source_url']))) throw new RuntimeException('يلزم توثيق مصدر آمن وأساس حقوق الإتاحة.');
                $fileKey = null; $fileSize = null;
                $storage = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'books';
                if (isset($_FILES['book_pdf']) && ($_FILES['book_pdf']['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_NO_FILE) {
                    $file = $_FILES['book_pdf'];
                    if ($file['error'] !== UPLOAD_ERR_OK || $file['size'] > 15 * 1024 * 1024 || !is_uploaded_file($file['tmp_name'])) throw new RuntimeException('تعذر قبول الملف. الحد الأقصى 15 ميجابايت.');
                    $mime = (new finfo(FILEINFO_MIME_TYPE))->file($file['tmp_name']);
                    $signature = file_get_contents($file['tmp_name'], false, null, 0, 5);
                    if ($mime !== 'application/pdf' || $signature !== '%PDF-') throw new RuntimeException('الملف المرفوع ليس PDF صالحًا.');
                    if (!is_dir($storage) && !mkdir($storage, 0700, true) && !is_dir($storage)) throw new RuntimeException('تعذر إعداد التخزين الخاص.');
                    $fileKey = bin2hex(random_bytes(20)) . '.pdf'; $fileSize = (int) $file['size'];
                    if (!move_uploaded_file($file['tmp_name'], $storage . DIRECTORY_SEPARATOR . $fileKey)) throw new RuntimeException('تعذر حفظ الملف.');
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
    $lessons = db()->query('SELECT l.*,s.name AS scholar_name FROM lessons l LEFT JOIN scholars s ON s.id=l.scholar_id ORDER BY l.sort_order,l.id DESC')->fetchAll();
    $books = db()->query('SELECT * FROM books ORDER BY id DESC')->fetchAll();
    $adhkarItems = db()->query('SELECT * FROM adhkar_items ORDER BY sort_order,id DESC')->fetchAll();
    $title='إدارة أثر'; $activePath=$path; $viewPath=dirname(__DIR__).'/app/Views/pages/admin.php'; $flashMessage=take_flash();
    ob_start(); require $viewPath; $content=(string) ob_get_clean(); require dirname(__DIR__).'/app/Views/layout.php'; exit;
}

$curatedBooks = $curatedLessons = $curatedAdhkar = [];
try {
    if ($path === '/books') $curatedBooks = db()->query('SELECT id,title,author,description,source_url,can_download,file_key FROM books WHERE is_published=1 ORDER BY title LIMIT 50')->fetchAll();
    if ($path === '/lessons') $curatedLessons = db()->query('SELECT l.title,l.description,l.youtube_id,l.source_url,s.name AS scholar_name FROM lessons l LEFT JOIN scholars s ON s.id=l.scholar_id WHERE l.is_published=1 ORDER BY l.sort_order,l.id DESC LIMIT 50')->fetchAll();
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
