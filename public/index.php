<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/app/helpers.php';
require_once dirname(__DIR__) . '/app/backup.php';
require_once dirname(__DIR__) . '/app/mailer.php';
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
header("Content-Security-Policy: default-src 'self'; img-src 'self' data: https://i.ytimg.com; media-src 'self' https://cdn.islamic.network https://cdn.jsdelivr.net; style-src 'self'; script-src 'self' https://www.youtube.com https://s.ytimg.com; connect-src 'self' https://api.quranpedia.net https://api.alquran.cloud https://pray.api.islamic.network https://cdn.jsdelivr.net https://www.youtube.com; frame-src https://api.quranpedia.net https://www.youtube-nocookie.com https://www.youtube.com; object-src 'none'; base-uri 'self'; frame-ancestors 'none'");

$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$method = strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');
if (in_array($path, ['/login','/register','/account','/admin','/forgot-password','/reset-password','/verify-email','/series','/lessons'], true)) header('Cache-Control: no-store, private');

if ($path === '/api/hadith/search' && $method === 'GET') {
    if (!allow_session_rate('dorar_hadith_search',30,60)) { http_response_code(429); header('Content-Type: application/json; charset=utf-8'); echo json_encode(['error'=>'rate_limited'],JSON_UNESCAPED_UNICODE); exit; }
    $query = trim((string) ($_GET['q'] ?? ''));
    if (mb_strlen($query, 'UTF-8') < 2 || mb_strlen($query, 'UTF-8') > 200) { http_response_code(422); header('Content-Type: application/json; charset=utf-8'); echo json_encode(['error' => 'invalid_query'], JSON_UNESCAPED_UNICODE); exit; }
    try { $token = trim(platform_secret('dorar_api_token', 'ATHAR_DORAR_API_TOKEN')); } catch (Throwable $exception) { $token = ''; }
    if ($token === '') { http_response_code(503); header('Content-Type: application/json; charset=utf-8'); echo json_encode(['error' => 'service_not_configured'], JSON_UNESCAPED_UNICODE); exit; }
    $url = 'https://dorar.net/api/v1/hadith/search?' . http_build_query(['q' => $query, 'limit' => 15, 'match' => 'all']);
    $context = stream_context_create(['http' => ['method' => 'GET', 'timeout' => 10, 'ignore_errors' => true, 'header' => "Accept: application/json\r\nAuthorization: Bearer {$token}\r\n"]]);
    $payload = @file_get_contents($url, false, $context);
    $statusCode = 0;
    foreach (($http_response_header ?? []) as $responseHeader) if (preg_match('/^HTTP\/\S+\s+(\d{3})/', $responseHeader, $match)) $statusCode = (int) $match[1];
    if (!is_string($payload) || $statusCode < 200 || $statusCode >= 300) { http_response_code($statusCode === 429 ? 429 : 502); header('Content-Type: application/json; charset=utf-8'); echo json_encode(['error' => 'search_unavailable'], JSON_UNESCAPED_UNICODE); exit; }
    $decoded = json_decode($payload, true);
    if (!is_array($decoded) || !is_array($decoded['data'] ?? null)) { http_response_code(502); header('Content-Type: application/json; charset=utf-8'); echo json_encode(['error' => 'invalid_provider_response'], JSON_UNESCAPED_UNICODE); exit; }
    header('Content-Type: application/json; charset=utf-8'); header('Cache-Control: private, max-age=0, must-revalidate'); echo json_encode($decoded, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES); exit;
}

if ($path === '/robots.txt' && $method === 'GET') {
    $base = rtrim((string) (getenv('ATHAR_BASE_URL') ?: ''), '/');
    header('Content-Type: text/plain; charset=utf-8');
    echo "User-agent: *\nAllow: /\nDisallow: /account\nDisallow: /admin\nDisallow: /login\nDisallow: /register\nDisallow: /forgot-password\nDisallow: /reset-password\nDisallow: /verify-email\n";
    if ($base !== '' && is_https_url($base . '/')) echo "Sitemap: {$base}/sitemap.xml\n";
    exit;
}

if ($path === '/sitemap.xml' && $method === 'GET') {
    $base = rtrim((string) (getenv('ATHAR_BASE_URL') ?: ''), '/');
    if ($base === '' || !is_https_url($base . '/')) { http_response_code(404); exit; }
    $urls = ['/', '/quran', '/hadith', '/books', '/lessons', '/adhkar', '/about'];
    try { foreach (db()->query('SELECT id FROM scholars WHERE is_published=1')->fetchAll(PDO::FETCH_COLUMN) as $id) $urls[] = '/scholar?id=' . (int) $id; foreach (db()->query('SELECT id FROM series WHERE is_published=1')->fetchAll(PDO::FETCH_COLUMN) as $id) $urls[] = '/series?id=' . (int) $id; } catch (Throwable $exception) { }
    header('Content-Type: application/xml; charset=utf-8');
    echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n";
    foreach ($urls as $url) echo '<url><loc>' . htmlspecialchars($base . $url, ENT_XML1 | ENT_QUOTES, 'UTF-8') . "</loc></url>\n";
    echo "</urlset>"; exit;
}

function send_account_token(string $email, string $type): bool
{
    $raw = (string) random_int(100000, 999999);
    $tokenType = $type === 'verify_email' ? 'verify_email' : 'password_reset';
    db()->prepare('DELETE FROM auth_tokens WHERE user_id=(SELECT id FROM users WHERE email=?) AND token_type=?')->execute([$email, $tokenType]);
    $userQuery = db()->prepare('SELECT id FROM users WHERE email=? LIMIT 1'); $userQuery->execute([$email]); $userId = $userQuery->fetchColumn();
    if (!$userId) return false;
    $minutes = 10;
    db()->prepare('INSERT INTO auth_tokens (user_id,token_type,token_hash,expires_at) VALUES (?,?,?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL ? MINUTE))')->execute([$userId, $tokenType, hash('sha256', $raw), $minutes]);
    $subject = $tokenType === 'verify_email' ? 'رمز تأكيد البريد الإلكتروني في أثر' : 'رمز إعادة تعيين كلمة مرور أثر';
    $body = "رمز التحقق الخاص بك: {$raw}\n\nتنتهي صلاحية الرمز خلال 10 دقائق. لا تشاركه مع أي شخص.\n\nAthar | أثر";
    return send_platform_email($email, $subject, $body);
}

function allow_session_rate(string $key, int $limit, int $windowSeconds): bool
{
    $now = time(); $attempts = $_SESSION[$key] ?? ['started' => $now, 'count' => 0];
    if (!is_array($attempts) || $now - (int) ($attempts['started'] ?? 0) > $windowSeconds) $attempts = ['started' => $now, 'count' => 0];
    $attempts['count'] = (int) ($attempts['count'] ?? 0) + 1; $_SESSION[$key] = $attempts;
    return $attempts['count'] <= $limit;
}

if ($path === '/verify-email') redirect(current_user() ? '/account' : '/login');

if ($path === '/forgot-password') {
    $authError = ''; $authNotice = '';
    if ($method === 'POST') { require_csrf(); $email = strtolower(trim((string) ($_POST['email'] ?? ''))); if (allow_session_rate('password_reset_requests', 3, 900) && filter_var($email, FILTER_VALIDATE_EMAIL) && strlen($email) <= 190) { try { send_account_token($email, 'password_reset'); } catch (Throwable $exception) { error_log('[Athar] Password reset email delivery failed.'); } } $_SESSION['password_reset_email'] = $email; flash('إذا كان البريد مسجلًا، ستصلك رسالة برمز صالح لمدة 10 دقائق.'); redirect('/reset-password'); }
    $title = 'استعادة كلمة المرور'; $activePath = $path; ob_start(); require dirname(__DIR__) . '/app/Views/pages/auth.php'; $content=(string)ob_get_clean(); require dirname(__DIR__) . '/app/Views/layout.php'; exit;
}

if ($path === '/reset-password') {
    $authError = ''; $authNotice = ''; $rawToken = '';
    if ($method === 'POST') { require_csrf(); $password=(string)($_POST['password']??''); $code=trim((string)($_POST['code']??'')); $email=strtolower(trim((string)($_SESSION['password_reset_email']??$_POST['email']??'')));
        if (!preg_match('/^\d{6}$/',$code) || strlen($password)<10 || strlen($password)>200 || !filter_var($email,FILTER_VALIDATE_EMAIL)) $authError='أدخل رمزًا من 6 أرقام وكلمة مرور من 10 إلى 200 حرف.';
        elseif (!allow_session_rate('password_reset_code_attempts', 5, 900)) $authError='محاولات كثيرة. اطلب رمزًا جديدًا بعد 15 دقيقة.';
        else { $pdo=db(); $pdo->beginTransaction(); try { $q=$pdo->prepare("SELECT t.id,t.user_id FROM auth_tokens t JOIN users u ON u.id=t.user_id WHERE u.email=? AND t.token_hash=? AND t.token_type='password_reset' AND t.failed_attempts<5 AND t.consumed_at IS NULL AND t.expires_at>UTC_TIMESTAMP() FOR UPDATE"); $q->execute([$email,hash('sha256',$code)]); $record=$q->fetch(); if (!$record) { $pdo->prepare("UPDATE auth_tokens t JOIN users u ON u.id=t.user_id SET t.failed_attempts=LEAST(5,t.failed_attempts+1),t.consumed_at=IF(t.failed_attempts+1>=5,UTC_TIMESTAMP(),t.consumed_at) WHERE u.email=? AND t.token_type='password_reset' AND t.consumed_at IS NULL")->execute([$email]); $pdo->commit(); throw new RuntimeException('الرمز غير صحيح أو انتهت صلاحيته.'); } $pdo->prepare('UPDATE users SET password_hash=?,session_version=session_version+1 WHERE id=?')->execute([password_hash($password,PASSWORD_DEFAULT),$record['user_id']]); $pdo->prepare("UPDATE auth_tokens SET consumed_at=UTC_TIMESTAMP() WHERE user_id=? AND token_type='password_reset' AND consumed_at IS NULL")->execute([$record['user_id']]); $pdo->prepare("INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)")->execute([$record['user_id'],'password-reset','user',(string)$record['user_id']]); $pdo->commit(); unset($_SESSION['password_reset_email'],$_SESSION['password_reset_code_attempts']); flash('تم تغيير كلمة المرور. سجّل الدخول من جديد.'); redirect('/login'); } catch(Throwable $exception) { if($pdo->inTransaction())$pdo->rollBack(); $authError=$exception instanceof RuntimeException?$exception->getMessage():'تعذر إعادة تعيين كلمة المرور.'; } }
    }
    $title = 'تعيين كلمة مرور جديدة'; $activePath = $path; ob_start(); require dirname(__DIR__) . '/app/Views/pages/auth.php'; $content=(string)ob_get_clean(); require dirname(__DIR__) . '/app/Views/layout.php'; exit;
}

if ($path === '/api/quran/progress' && $method === 'POST') {
    assert_no_maintenance_lock();
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
        db()->prepare('INSERT INTO account_daily_stats (user_id,activity_date,quran_sessions) VALUES (?,UTC_DATE(),1) ON DUPLICATE KEY UPDATE quran_sessions=LEAST(65535,quran_sessions+1)')->execute([$user['id']]);
    }
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['ok' => true], JSON_UNESCAPED_UNICODE);
    exit;
}

if ($path === '/api/favorites' && $method === 'POST') {
    assert_no_maintenance_lock();
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

if ($path === '/api/adhkar/completion' && $method === 'POST') {
    $user = require_login(); require_csrf();
    if (!allow_session_rate('adhkar_completions',60,3600)) { http_response_code(429); header('Content-Type: application/json; charset=utf-8'); echo json_encode(['error'=>'rate_limited']); exit; }
    db()->prepare('INSERT INTO account_daily_stats (user_id,activity_date,adhkar_completions) VALUES (?,UTC_DATE(),1) ON DUPLICATE KEY UPDATE adhkar_completions=LEAST(65535,adhkar_completions+1)')->execute([$user['id']]);
    header('Content-Type: application/json; charset=utf-8'); echo json_encode(['ok'=>true]); exit;
}

if ($path === '/api/lesson/progress' && $method === 'POST') {
    assert_no_maintenance_lock();
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
    $wasCompleteQuery = db()->prepare('SELECT is_complete FROM video_progress WHERE user_id=? AND lesson_id=?'); $wasCompleteQuery->execute([$user['id'], $lessonId]); $wasComplete = (bool) $wasCompleteQuery->fetchColumn();
    db()->prepare('INSERT INTO video_progress (user_id,lesson_id,position_seconds,duration_seconds,is_complete) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE position_seconds=VALUES(position_seconds),duration_seconds=VALUES(duration_seconds),is_complete=VALUES(is_complete)')->execute([$user['id'], $lessonId, $position, $duration, $complete ? 1 : 0]);
    if ($complete && !$wasComplete) db()->prepare('INSERT INTO account_daily_stats (user_id,activity_date,lesson_completions) VALUES (?,UTC_DATE(),1) ON DUPLICATE KEY UPDATE lesson_completions=LEAST(65535,lesson_completions+1)')->execute([$user['id']]);
    header('Content-Type: application/json; charset=utf-8'); echo json_encode(['ok' => true]); exit;
}

if ($path === '/lesson-comment' && $method === 'POST') {
    $user = require_login(); require_csrf();
    if (!allow_session_rate('lesson_comment_posts',3,3600)) { flash('أرسلت عدة تعليقات مؤخرًا. حاول بعد ساعة.','error'); redirect('/lessons'); }
    $lessonId = filter_var($_POST['lesson_id'] ?? null,FILTER_VALIDATE_INT);
    $seriesId = filter_var($_POST['series_id'] ?? 0,FILTER_VALIDATE_INT) ?: 0;
    $body = trim((string)($_POST['body'] ?? ''));
    if (!$lessonId || mb_strlen($body,'UTF-8') < 2 || mb_strlen($body,'UTF-8') > 2000) { flash('اكتب تعليقًا من حرفين إلى 2000 حرف.','error'); redirect('/lessons'); }
    if ($seriesId) { $available = db()->prepare('SELECT l.id FROM lessons l JOIN series s ON s.id=l.series_id WHERE l.id=? AND s.id=? AND l.is_published=1 AND s.is_published=1'); $available->execute([$lessonId,$seriesId]); }
    else { $available = db()->prepare('SELECT id FROM lessons WHERE id=? AND series_id IS NULL AND is_published=1'); $available->execute([$lessonId]); }
    if (!$available->fetchColumn()) { http_response_code(404); exit('الدرس غير متاح.'); }
    db()->prepare("INSERT INTO lesson_comments (lesson_id,user_id,body,status) VALUES (?,?,?,'pending')")->execute([$lessonId,$user['id'],$body]);
    flash('وصل تعليقك، وسيظهر بعد مراجعته.'); redirect($seriesId ? '/series?id='.(int)$seriesId.'&lesson='.(int)$lessonId : '/lessons?lesson='.(int)$lessonId);
}

if ($path === '/book-download' && $method === 'GET') {
    $id = filter_var($_GET['id'] ?? null, FILTER_VALIDATE_INT);
    if (!$id || $id < 1) { http_response_code(404); exit('الملف غير موجود.'); }
    $statement = db()->prepare('SELECT title,file_key,file_size,download_url FROM books WHERE id=? AND is_published=1 AND can_download=1 LIMIT 1');
    $statement->execute([$id]); $book = $statement->fetch();
    if ($book && !$book['file_key'] && is_https_url((string)($book['download_url'] ?? ''))) {
        $downloadUser = current_user();
        if ($downloadUser) db()->prepare('INSERT INTO account_daily_stats (user_id,activity_date,book_downloads) VALUES (?,UTC_DATE(),1) ON DUPLICATE KEY UPDATE book_downloads=LEAST(65535,book_downloads+1)')->execute([(int)$downloadUser['id']]);
        header('Cache-Control: no-store, private'); header('Location: '.(string)$book['download_url'],true,302); exit;
    }
    if (!$book || !is_string($book['file_key']) || !preg_match('/^[a-f0-9]{40}\.pdf$/', $book['file_key'])) { http_response_code(404); exit('الملف غير متاح للتنزيل.'); }
    $filePath = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'books' . DIRECTORY_SEPARATOR . $book['file_key'];
    if (!is_file($filePath)) { http_response_code(404); exit('الملف غير موجود.'); }
    $downloadUser = current_user();
    if ($downloadUser) db()->prepare('INSERT INTO account_daily_stats (user_id,activity_date,book_downloads) VALUES (?,UTC_DATE(),1) ON DUPLICATE KEY UPDATE book_downloads=LEAST(65535,book_downloads+1)')->execute([(int) $downloadUser['id']]);
    header('Content-Type: application/pdf'); header('X-Content-Type-Options: nosniff');
    header('Content-Disposition: attachment; filename="athar-book-' . (int) $book['id'] . '.pdf"');
    header('Content-Length: ' . filesize($filePath)); readfile($filePath); exit;
}

if ($path === '/backup-download' && $method === 'POST') {
    $owner = require_owner(); require_csrf();
    $grantId = (int) ($_SESSION['backup_grant_id'] ?? 0);
    $rawToken = (string) ($_SESSION['backup_grant_token'] ?? '');
    unset($_SESSION['backup_grant_id'], $_SESSION['backup_grant_token']);
    if (!$grantId || !preg_match('/^[a-f0-9]{64}$/', $rawToken)) { http_response_code(403); exit('لا يوجد إذن تنزيل صالح.'); }
    $pdo = db();
    $statement = $pdo->prepare("SELECT g.id,g.backup_id,g.token_hash,a.storage_key,a.sha256,a.byte_size,a.status FROM backup_download_grants g JOIN backup_artifacts a ON a.id=g.backup_id WHERE g.id=? AND g.owner_id=? AND g.expires_at>UTC_TIMESTAMP() AND g.consumed_at IS NULL AND a.status='ready' AND a.expires_at>UTC_TIMESTAMP() LIMIT 1");
    $statement->execute([$grantId, $owner['id']]); $grant = $statement->fetch();
    if (!$grant || !hash_equals((string) $grant['token_hash'], hash('sha256', $rawToken))) { http_response_code(403); exit('انتهت صلاحية إذن التنزيل أو استُخدم بالفعل.'); }
    $claim = $pdo->prepare('UPDATE backup_download_grants SET consumed_at=UTC_TIMESTAMP() WHERE id=? AND owner_id=? AND consumed_at IS NULL AND expires_at>UTC_TIMESTAMP()');
    $claim->execute([$grantId, $owner['id']]);
    if ($claim->rowCount() !== 1) { http_response_code(409); exit('إذن التنزيل استُخدم بالفعل.'); }
    $key = (string) $grant['storage_key'];
    if (!preg_match('/^[a-f0-9]{40}\.enc$/', $key)) { http_response_code(404); exit('النسخة غير متاحة.'); }
    $file = backup_directory() . DIRECTORY_SEPARATOR . $key;
    if (!is_file($file) || !hash_equals((string) $grant['sha256'], hash_file('sha256', $file) ?: '')) { http_response_code(404); exit('تعذر التحقق من النسخة الاحتياطية.'); }
    $pdo->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$owner['id'], 'backup-download-started', 'backup', (string) $grant['backup_id']]);
    $downloadSize = filesize($file);
    session_write_close();
    header('Content-Type: application/octet-stream'); header('X-Content-Type-Options: nosniff'); header('Cache-Control: no-store, private');
    header('Content-Disposition: attachment; filename="athar-backup-' . (int) $grant['backup_id'] . '.athar.enc"'); header('Content-Length: ' . $downloadSize);
    $sent = readfile($file);
    if ($sent === $downloadSize) {
        $pdo->prepare("UPDATE backup_artifacts SET status='downloaded',downloaded_at=UTC_TIMESTAMP() WHERE id=?")->execute([$grant['backup_id']]);
        $pdo->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$owner['id'], 'backup-downloaded', 'backup', (string) $grant['backup_id']]);
    }
    exit;
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
        $displayName = trim((string) ($_POST['display_name'] ?? ''));
        $username = strtolower(trim((string) ($_POST['username'] ?? '')));
        if ($attempts['count'] > 8) {
            $authError = 'محاولات كثيرة. انتظر 15 دقيقة ثم حاول مجددًا.';
        } elseif (!filter_var($email, FILTER_VALIDATE_EMAIL) || strlen($email) > 190 || strlen($password) < 10 || strlen($password) > 200) {
            $authError = 'أدخل بريدًا صالحًا وكلمة مرور من 10 أحرف على الأقل.';
        } elseif ($path === '/register' && (mb_strlen($displayName, 'UTF-8') < 2 || mb_strlen($displayName, 'UTF-8') > 80 || !preg_match('/^[a-z0-9_]{3,24}$/', $username) || in_array($username, ['admin','administrator','root','support','athar','api','www','owner'], true))) {
            $authError = 'أدخل اسمًا من حرفين إلى 80 حرفًا واسم مستخدم من 3 إلى 24 حرفًا إنجليزيًا صغيرًا أو رقمًا أو _.';
        } else {
            $guestMerge = null;
            if ($path === '/register' && ($_POST['merge_guest_progress'] ?? '') === '1') {
                $rawProgress = (string) ($_POST['guest_progress_json'] ?? '');
                try {
                    if (strlen($rawProgress) > 65536) throw new RuntimeException('بيانات التقدم المحلي أكبر من الحد المسموح.');
                    $decoded = json_decode($rawProgress, true, 32, JSON_THROW_ON_ERROR);
                    if (!is_array($decoded)) throw new RuntimeException('بيانات التقدم المحلي غير صالحة.');
                    $guestMerge = ['bookmarks' => [], 'position' => null];
                    foreach (array_slice(is_array($decoded['bookmarks'] ?? null) ? $decoded['bookmarks'] : [], 0, 1000) as $item) {
                        $s = filter_var($item['surah'] ?? null, FILTER_VALIDATE_INT); $a = filter_var($item['ayah'] ?? null, FILTER_VALIDATE_INT);
                        if ($s && $s >= 1 && $s <= 114 && $a && $a >= 1 && $a <= quran_ayah_count($s)) $guestMerge['bookmarks'][] = [$s, $a];
                    }
                    $position = $decoded['position'] ?? null;
                    if (is_array($position)) {
                        $s = filter_var($position['surah'] ?? null, FILTER_VALIDATE_INT); $a = filter_var($position['ayah'] ?? null, FILTER_VALIDATE_INT); $stamp = filter_var($position['updatedAt'] ?? null, FILTER_VALIDATE_INT);
                        if ($s && $s >= 1 && $s <= 114 && $a && $a >= 1 && $a <= quran_ayah_count($s) && $stamp && $stamp >= (time() - 7776000) * 1000 && $stamp <= (time() + 300) * 1000) $guestMerge['position'] = [$s, $a, gmdate('Y-m-d H:i:s', (int) floor($stamp / 1000))];
                    }
                } catch (Throwable $mergeError) { $guestMerge = null; $authError = 'تعذر التحقق من بيانات القرآن المحلية. راجعها أو أزل اختيار الدمج.'; }
            }
            if ($authError === '') {
            $mergeTransaction = false;
            try {
                if ($path === '/register') {
                    if (is_array($guestMerge)) { db()->beginTransaction(); $mergeTransaction = true; }
                    $statement = db()->prepare('INSERT INTO users (email,display_name,username,password_hash) VALUES (?,?,?,?)');
                    $statement->execute([$email, $displayName, $username, password_hash($password, PASSWORD_DEFAULT)]);
                    $id = (int) db()->lastInsertId();
                    if (is_array($guestMerge)) {
                        $pdo = db();
                        foreach ($guestMerge['bookmarks'] as [$surah, $ayah]) $pdo->prepare('INSERT IGNORE INTO quran_bookmarks (user_id,surah,ayah) VALUES (?,?,?)')->execute([$id, $surah, $ayah]);
                        if ($guestMerge['position']) {
                            [$surah, $ayah, $updatedAt] = $guestMerge['position'];
                            $pdo->prepare('INSERT INTO quran_positions (user_id,surah,ayah,updated_at) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE surah=IF(VALUES(updated_at)>updated_at,VALUES(surah),surah),ayah=IF(VALUES(updated_at)>updated_at,VALUES(ayah),ayah),updated_at=GREATEST(updated_at,VALUES(updated_at))')->execute([$id, $surah, $ayah, $updatedAt]);
                        }
                        $pdo->commit(); $mergeTransaction = false; $_SESSION['guest_merge_complete'] = true;
                    }
                } else {
                    $statement = db()->prepare('SELECT id,email,password_hash,role,session_version FROM users WHERE email=? LIMIT 1');
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
                    if ($path === '/register') {
                        try { $verificationSent = send_account_token($email, 'verify_email'); }
                        catch (Throwable $mailError) { error_log('[Athar] Verification mail delivery failed.'); $verificationSent = false; }
                    }
                    $statement = db()->prepare('SELECT id,email,display_name,username,role,session_version,email_verified_at,preferences_json FROM users WHERE id=?');
                    $statement->execute([$id]);
                    session_regenerate_id(true);
                    $_SESSION['user'] = $statement->fetch();
                    $_SESSION['csrf'] = bin2hex(random_bytes(32));
                    $_SESSION['authenticated_at'] = time();
                    $_SESSION['last_activity'] = time();
                    unset($_SESSION['auth_attempts']);
                    if ($path === '/register') flash($verificationSent ? 'أرسلنا رمز تأكيد إلى بريدك، وصلاحيته 10 دقائق.' : 'تم إنشاء الحساب، لكن تعذر إرسال رمز التأكيد. اطلب من الإدارة ضبط البريد في لوحة التحكم.', $verificationSent ? 'success' : 'error');
                    redirect('/account');
                }
            } catch (PDOException $exception) {
                if ($mergeTransaction && db()->inTransaction()) db()->rollBack();
                $authError = $path === '/register' && $exception->getCode() === '23000'
                    ? 'تعذر إنشاء الحساب بهذه البيانات.'
                    : 'تعذر الاتصال بقاعدة البيانات. راجع إعدادات التشغيل.';
            }
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
    $clearGuestProgress = !empty($_SESSION['guest_merge_complete']);
    unset($_SESSION['guest_merge_complete']);
    $accountError = '';
    if ($method === 'POST') {
        assert_no_maintenance_lock();
        require_csrf();
        $action = (string) ($_POST['action'] ?? '');
        if ($action === 'profile-save') {
            $displayName = trim((string) ($_POST['display_name'] ?? ''));
            $username = strtolower(trim((string) ($_POST['username'] ?? '')));
            $password = (string) ($_POST['current_password'] ?? '');
            $reserved = ['admin','administrator','root','support','athar','api','www','owner'];
            if (mb_strlen($displayName, 'UTF-8') < 2 || mb_strlen($displayName, 'UTF-8') > 80 || !preg_match('/^[a-z0-9_]{3,24}$/', $username) || in_array($username, $reserved, true)) $accountError = 'تحقق من الاسم واسم المستخدم (3–24 حرفًا إنجليزيًا صغيرًا أو رقمًا أو _).';
            else {
                $passwordQuery = db()->prepare('SELECT password_hash FROM users WHERE id=?'); $passwordQuery->execute([$user['id']]); $passwordHash = (string) $passwordQuery->fetchColumn();
                if (!password_verify($password, $passwordHash)) $accountError = 'كلمة المرور الحالية غير صحيحة.';
                else {
                    try { db()->prepare('UPDATE users SET display_name=?,username=? WHERE id=?')->execute([$displayName,$username,$user['id']]); flash('تم تحديث بيانات الحساب.'); redirect('/account'); }
                    catch (PDOException $exception) { if ($exception->getCode() === '23000') $accountError = 'اسم المستخدم مستخدم بالفعل. اختر اسمًا آخر.'; else throw $exception; }
                }
            }
        } elseif ($action === 'plan-status') {
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
        } elseif ($action === 'resend-verification') {
            $check = db()->prepare('SELECT email_verified_at FROM users WHERE id=?'); $check->execute([$user['id']]);
            if ($check->fetchColumn()) flash('تم التحقق من البريد مسبقًا.');
            elseif (!allow_session_rate('verification_resends', 3, 900)) flash('محاولات كثيرة. انتظر 15 دقيقة قبل طلب رابط جديد.', 'error');
            else { try { $sent = send_account_token((string) $user['email'], 'verify_email'); } catch (Throwable $mailError) { error_log('[Athar] Verification email delivery failed.'); $sent = false; } flash($sent ? 'أرسلنا رمز تحقق جديدًا إلى بريدك، وصلاحيته 10 دقائق.' : 'تعذر إرسال البريد الآن. راجع إعدادات البريد في لوحة الإدارة.', $sent ? 'success' : 'error'); }
            redirect('/account');
        } elseif ($action === 'verify-email-code') {
            $code = trim((string) ($_POST['code'] ?? ''));
            if (!preg_match('/^\d{6}$/', $code) || !allow_session_rate('email_verification_attempts', 5, 900)) $accountError = 'أدخل الرمز الصحيح المكوّن من 6 أرقام. انتظر 15 دقيقة إذا تجاوزت عدد المحاولات.';
            else {
                $query = db()->prepare("SELECT id FROM auth_tokens WHERE user_id=? AND token_type='verify_email' AND token_hash=? AND failed_attempts<5 AND consumed_at IS NULL AND expires_at>UTC_TIMESTAMP() LIMIT 1"); $query->execute([$user['id'], hash('sha256', $code)]); $tokenId = $query->fetchColumn();
                if (!$tokenId) $accountError = 'الرمز غير صحيح أو انتهت صلاحيته. اطلب رمزًا جديدًا.';
                else { db()->prepare('UPDATE users SET email_verified_at=UTC_TIMESTAMP() WHERE id=?')->execute([$user['id']]); db()->prepare('UPDATE auth_tokens SET consumed_at=UTC_TIMESTAMP() WHERE id=?')->execute([$tokenId]); flash('تم تأكيد البريد الإلكتروني.'); redirect('/account'); }
                if (!$tokenId) db()->prepare("UPDATE auth_tokens SET failed_attempts=LEAST(5,failed_attempts+1),consumed_at=IF(failed_attempts+1>=5,UTC_TIMESTAMP(),consumed_at) WHERE user_id=? AND token_type='verify_email' AND consumed_at IS NULL")->execute([$user['id']]);
            }
        } elseif ($action === 'save-preferences') {
            $themePreference = (string) ($_POST['theme'] ?? 'light');
            if (!in_array($themePreference, ['light','dark'], true)) $accountError = 'اختيار المظهر غير صالح.';
            else {
                $preferences = json_decode((string) ($user['preferences_json'] ?? '{}'), true); if (!is_array($preferences)) $preferences = [];
                $preferences['theme'] = $themePreference;
                db()->prepare('UPDATE users SET preferences_json=? WHERE id=?')->execute([json_encode($preferences, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR), $user['id']]);
                flash('تم حفظ تفضيلات الحساب.'); redirect('/account');
            }
        } elseif ($action === 'change-password') {
            $currentPassword = (string) ($_POST['current_password'] ?? '');
            $newPassword = (string) ($_POST['new_password'] ?? '');
            $check = db()->prepare('SELECT password_hash FROM users WHERE id=?'); $check->execute([$user['id']]); $hash = (string) $check->fetchColumn();
            if (!password_verify($currentPassword, $hash)) $accountError = 'كلمة المرور الحالية غير صحيحة.';
            elseif (strlen($newPassword) < 10 || strlen($newPassword) > 200) $accountError = 'كلمة المرور الجديدة يجب أن تكون بين 10 و200 حرفًا.';
            else {
                db()->prepare('UPDATE users SET password_hash=?,session_version=session_version+1 WHERE id=?')->execute([password_hash($newPassword, PASSWORD_DEFAULT), $user['id']]);
                $_SESSION['user']['session_version'] = (int) $_SESSION['user']['session_version'] + 1;
                $_SESSION['authenticated_at'] = time();
                db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$user['id'], 'password-changed-and-other-sessions-revoked', 'user', (string) $user['id']]);
                flash('تم تغيير كلمة المرور وتسجيل الخروج من الجلسات الأخرى.'); redirect('/account');
            }
        } elseif ($action === 'delete-account') {
            $password = (string) ($_POST['password'] ?? '');
            $statement = db()->prepare('SELECT password_hash FROM users WHERE id=?');
            $statement->execute([$user['id']]);
            $hash = (string) $statement->fetchColumn();
            if ($hash === '' || !password_verify($password, $hash)) $accountError = 'كلمة المرور غير صحيحة.';
            else {
                db()->prepare('DELETE FROM users WHERE id=?')->execute([$user['id']]);
                db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$user['id'], 'account-deleted-minimal-audit-retained', 'user', (string) $user['id']]);
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
    $userPreferences = json_decode((string) ($user['preferences_json'] ?? '{}'), true); if (!is_array($userPreferences)) $userPreferences = [];
    $themePreference = in_array(($userPreferences['theme'] ?? 'light'), ['light','dark'], true) ? $userPreferences['theme'] : 'light';
    $statsQuery = db()->prepare('SELECT COALESCE(SUM(quran_sessions),0) AS quran_sessions,COALESCE(SUM(lesson_completions),0) AS lesson_completions,COALESCE(SUM(adhkar_completions),0) AS adhkar_completions,COALESCE(SUM(book_downloads),0) AS book_downloads,COUNT(*) AS active_days FROM account_daily_stats WHERE user_id=?');
    $statsQuery->execute([$user['id']]); $accountStats = $statsQuery->fetch();
    $title = 'حسابي'; $activePath = $path; $viewPath = dirname(__DIR__) . '/app/Views/pages/account.php';
    $flashMessage = take_flash();
    ob_start(); require $viewPath; $content = (string) ob_get_clean(); require dirname(__DIR__) . '/app/Views/layout.php'; exit;
}

if ($path === '/admin') {
    $admin = require_admin();
    $adminError = '';
    $backupArtifacts = [];
    if (($admin['role'] ?? '') === 'owner') {
        try { expire_backup_artifacts(); } catch (Throwable $exception) { $adminError = 'تعذر تنظيف النسخ المنتهية. راجع مساحة التخزين.'; }
    }
    if ($method === 'POST') {
        if (!in_array((string) ($_POST['kind'] ?? ''), ['backup-restore','local-reset'], true)) assert_no_maintenance_lock();
        require_csrf();
        $kind = (string) ($_POST['kind'] ?? '');
        try {
            if ($kind === 'comment-review') {
                $commentId = filter_var($_POST['comment_id'] ?? null,FILTER_VALIDATE_INT); $status = (string)($_POST['status'] ?? '');
                if (!$commentId || !in_array($status,['visible','hidden'],true)) throw new RuntimeException('حالة التعليق غير صالحة.');
                db()->prepare('UPDATE lesson_comments SET status=?,moderated_at=UTC_TIMESTAMP(),moderated_by=? WHERE id=?')->execute([$status,$admin['id'],$commentId]);
                db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'],'comment-'.$status,'lesson-comment',(string)$commentId]);
                flash($status==='visible'?'تم نشر التعليق بعد مراجعته.':'تم إخفاء التعليق.'); redirect('/admin');
            } elseif ($kind === 'platform-settings') {
                db()->beginTransaction();
                $smtpHost = trim((string) ($_POST['smtp_host'] ?? ''));
                $smtpPort = filter_var($_POST['smtp_port'] ?? null, FILTER_VALIDATE_INT);
                $smtpSecurity = (string) ($_POST['smtp_security'] ?? '');
                $smtpUsername = trim((string) ($_POST['smtp_username'] ?? ''));
                $smtpFrom = trim((string) ($_POST['smtp_from_email'] ?? ''));
                $smtpFromName = trim((string) ($_POST['smtp_from_name'] ?? ''));
                if (!preg_match('/^[a-z0-9.-]{1,190}$/i', $smtpHost) || $smtpPort === false || $smtpPort < 1 || $smtpPort > 65535 || !in_array($smtpSecurity, ['tls','ssl'], true) || !filter_var($smtpFrom, FILTER_VALIDATE_EMAIL) || mb_strlen($smtpFromName, 'UTF-8') > 100) throw new RuntimeException('راجع عنوان خادم SMTP والمنفذ ونوع التشفير والبريد المرسل.');
                foreach (['smtp_host'=>$smtpHost,'smtp_port'=>(string)$smtpPort,'smtp_security'=>$smtpSecurity,'smtp_username'=>$smtpUsername,'smtp_from_email'=>$smtpFrom,'smtp_from_name'=>$smtpFromName] as $settingKey=>$settingValue) save_platform_setting($settingKey,$settingValue,(int)$admin['id']);
                $smtpPassword = (string) ($_POST['smtp_password'] ?? '');
                if ($smtpPassword !== '') save_platform_setting('smtp_password_cipher',seal_platform_secret($smtpPassword),(int)$admin['id']);
                elseif (isset($_POST['clear_smtp_password'])) save_platform_setting('smtp_password_cipher','',(int)$admin['id']);
                $dorarToken = trim((string) ($_POST['dorar_api_token'] ?? ''));
                if ($dorarToken !== '') save_platform_setting('dorar_api_token_cipher',seal_platform_secret($dorarToken),(int)$admin['id']);
                if (isset($_POST['clear_dorar_token'])) save_platform_setting('dorar_api_token_cipher','',(int)$admin['id']);
                db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'],'platform-settings-updated','platform',null]);
                db()->commit();
                if (isset($_POST['send_test_email'])) {
                    if (!allow_session_rate('smtp_test_email',1,3600)) throw new RuntimeException('أُرسل اختبار بريد خلال الساعة الماضية. حاول لاحقًا.');
                    if (!send_platform_email((string)$admin['email'],'اختبار إعدادات البريد في أثر','تم إرسال رسالة الاختبار بنجاح.')) throw new RuntimeException('حُفظت الإعدادات، لكن تعذّر تسليم رسالة الاختبار. تحقق من بيانات SMTP وسجلات الخادم.');
                    flash('حُفظت الإعدادات ووصل اختبار البريد إلى حسابك.');
                } else flash('تم حفظ إعدادات البريد والبحث.');
                redirect('/admin');
            } elseif ($kind === 'user-role') {
                if (($admin['role'] ?? '') !== 'owner') throw new RuntimeException('إدارة صلاحيات المستخدمين متاحة لمالك المنصة فقط.');
                $targetId = filter_var($_POST['user_id'] ?? null, FILTER_VALIDATE_INT); $nextRole = (string) ($_POST['role'] ?? '');
                if (!$targetId || $targetId < 1 || !in_array($nextRole, ['user','admin'], true)) throw new RuntimeException('اختيار المستخدم أو الصلاحية غير صالح.');
                $passwordCheck = db()->prepare('SELECT password_hash FROM users WHERE id=?'); $passwordCheck->execute([$admin['id']]);
                if (!password_verify((string) ($_POST['password'] ?? ''), (string) $passwordCheck->fetchColumn())) throw new RuntimeException('كلمة مرور المالك غير صحيحة.');
                $target = db()->prepare('SELECT role FROM users WHERE id=?'); $target->execute([$targetId]); $currentRole = $target->fetchColumn();
                if (!is_string($currentRole) || $currentRole === 'owner') throw new RuntimeException('الحساب غير موجود أو أن دور المالك لا يتغير من هذه الشاشة.');
                if ($currentRole === $nextRole) { flash('الصلاحية المحددة مطبقة بالفعل.'); redirect('/admin'); }
                db()->prepare("UPDATE users SET role=?,session_version=session_version+1 WHERE id=? AND role<>'owner'")->execute([$nextRole, $targetId]);
                db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'], 'user-role-changed-to-' . $nextRole, 'user', (string) $targetId]);
                flash('تم تحديث الصلاحية وإبطال جلسات الحساب.'); redirect('/admin');
            } elseif ($kind === 'backup-create') {
                if (($admin['role'] ?? '') !== 'owner') throw new RuntimeException('إنشاء النسخ متاح لمالك المنصة فقط.');
                $passwordCheck = db()->prepare('SELECT password_hash FROM users WHERE id=?'); $passwordCheck->execute([$admin['id']]);
                if (!password_verify((string) ($_POST['password'] ?? ''), (string) $passwordCheck->fetchColumn())) throw new RuntimeException('كلمة المرور غير صحيحة.');
                if ((string) ($_POST['manifest_confirmation'] ?? '') !== 'CREATE FULL BACKUP') throw new RuntimeException('اكتب عبارة التأكيد كما تظهر في نموذج النسخة.');
                $artifact = build_backup_archive((int) $admin['id']);
                flash('أُنشئت نسخة كاملة مشفرة وتحققنا من محتوياتها. اطلب إذن تنزيل منفصلًا أدناه.'); redirect('/admin');
            } elseif ($kind === 'backup-restore') {
                if (($admin['role'] ?? '') !== 'owner') throw new RuntimeException('الاستعادة متاحة لمالك المنصة فقط.');
                $passwordCheck = db()->prepare('SELECT password_hash FROM users WHERE id=?'); $passwordCheck->execute([$admin['id']]);
                if (!password_verify((string) ($_POST['password'] ?? ''), (string) $passwordCheck->fetchColumn())) throw new RuntimeException('كلمة المرور غير صحيحة.');
                if ((string) ($_POST['restore_confirmation'] ?? '') !== 'RESTORE FULL BACKUP') throw new RuntimeException('اكتب عبارة الاستعادة كما تظهر في النموذج.');
                $backupId = filter_var($_POST['backup_id'] ?? null, FILTER_VALIDATE_INT); if (!$backupId || $backupId < 1) throw new RuntimeException('اختر نسخة صالحة.');
                append_owner_audit(['action' => 'backup-restore-started', 'actor_id' => (int) $admin['id'], 'backup_id' => $backupId]);
                acquire_maintenance_lock();
                try { restore_backup_archive($backupId, (int) $admin['id']); } finally { release_maintenance_lock(); }
                session_regenerate_id(true); $fresh = db()->prepare('SELECT id,email,role,session_version FROM users WHERE id=?'); $fresh->execute([$admin['id']]); $_SESSION['user'] = $fresh->fetch();
                flash('اكتملت الاستعادة وتحققنا من ملفاتها. أُبطلت جلسات الدخول الأخرى.'); redirect('/admin');
            } elseif ($kind === 'local-reset') {
                if (($admin['role'] ?? '') !== 'owner') throw new RuntimeException('إعادة الضبط متاحة لمالك المنصة فقط.');
                $environment = strtolower((string) (getenv('APP_ENV') ?: 'production'));
                if (!in_array($environment, ['local','development'], true)) throw new RuntimeException('إعادة الضبط موقوفة على هذه البيئة؛ يجب أن تكون APP_ENV=local أو development.');
                $passwordCheck = db()->prepare('SELECT password_hash FROM users WHERE id=?'); $passwordCheck->execute([$admin['id']]);
                if (!password_verify((string) ($_POST['password'] ?? ''), (string) $passwordCheck->fetchColumn())) throw new RuntimeException('كلمة المرور غير صحيحة.');
                if ((string) ($_POST['environment_confirmation'] ?? '') !== $environment || (string) ($_POST['reset_confirmation'] ?? '') !== 'RESET ATHAR DATA') throw new RuntimeException('أكد البيئة المحلية وعبارة الحذف حرفيًا.');
                $recent = db()->query("SELECT id FROM backup_artifacts WHERE status IN ('ready','downloaded') AND verified_at>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 24 HOUR) ORDER BY verified_at DESC LIMIT 1")->fetchColumn();
                if (!$recent) throw new RuntimeException('أنشئ نسخة كاملة تم التحقق منها خلال آخر 24 ساعة قبل إعادة الضبط.');
                append_owner_audit(['action' => 'local-reset-started', 'actor_id' => (int) $admin['id'], 'environment' => $environment, 'backup_id' => (int) $recent]);
                acquire_maintenance_lock();
                try {
                    $pdo = db(); $pdo->exec('SET FOREIGN_KEY_CHECKS=0');
                    try {
                        $keep = ['users','schema_migrations','backup_artifacts'];
                        $database = getenv('ATHAR_DB_NAME') ?: 'athar';
                        $tables = $pdo->query("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=" . $pdo->quote($database) . " AND TABLE_TYPE='BASE TABLE'")->fetchAll(PDO::FETCH_COLUMN);
                        foreach ($tables as $table) if (!in_array($table, $keep, true)) $pdo->exec('DELETE FROM `' . $table . '`');
                        $pdo->prepare('DELETE FROM users WHERE id<>?')->execute([$admin['id']]);
                        $pdo->prepare('UPDATE users SET session_version=session_version+1 WHERE id=?')->execute([$admin['id']]);
                    } finally { $pdo->exec('SET FOREIGN_KEY_CHECKS=1'); }
                    $assets = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'books';
                    foreach (glob($assets . DIRECTORY_SEPARATOR . '*.pdf') ?: [] as $pdf) if (preg_match('/^[a-f0-9]{40}\.pdf$/', basename($pdf))) @unlink($pdf);
                    append_owner_audit(['action' => 'local-reset-completed', 'actor_id' => (int) $admin['id'], 'environment' => $environment, 'backup_id' => (int) $recent]);
                } finally { release_maintenance_lock(); }
                $fresh = db()->prepare('SELECT id,email,role,session_version FROM users WHERE id=?'); $fresh->execute([$admin['id']]); $_SESSION['user'] = $fresh->fetch();
                flash('اكتملت إعادة الضبط المحلية. بقي المخطط والمالك والنسخ الاحتياطية.'); redirect('/admin');
            } elseif ($kind === 'backup-grant') {
                if (($admin['role'] ?? '') !== 'owner') throw new RuntimeException('إذن التنزيل متاح لمالك المنصة فقط.');
                $passwordCheck = db()->prepare('SELECT password_hash FROM users WHERE id=?'); $passwordCheck->execute([$admin['id']]);
                if (!password_verify((string) ($_POST['password'] ?? ''), (string) $passwordCheck->fetchColumn())) throw new RuntimeException('كلمة المرور غير صحيحة.');
                $backupId = filter_var($_POST['backup_id'] ?? null, FILTER_VALIDATE_INT);
                $artifactQuery = db()->prepare("SELECT id FROM backup_artifacts WHERE id=? AND status='ready' AND expires_at>UTC_TIMESTAMP()"); $artifactQuery->execute([$backupId]);
                if (!$backupId || !$artifactQuery->fetchColumn()) throw new RuntimeException('النسخة غير متاحة أو انتهت صلاحيتها.');
                $token = bin2hex(random_bytes(32));
                $grant = db()->prepare('INSERT INTO backup_download_grants (backup_id,owner_id,token_hash,expires_at) VALUES (?,?,?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 15 MINUTE))');
                $grant->execute([$backupId, $admin['id'], hash('sha256', $token)]);
                $_SESSION['backup_grant_id'] = (int) db()->lastInsertId(); $_SESSION['backup_grant_token'] = $token;
                db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'], 'backup-download-grant-issued', 'backup', (string) $backupId]);
                flash('إذن تنزيل أحادي الاستخدام جاهز لمدة 15 دقيقة. نزّل النسخة الآن من الزر التالي.'); redirect('/admin');
            } elseif ($kind === 'content') {
                $table = ['scholar' => 'scholars', 'series' => 'series', 'lesson' => 'lessons', 'book' => 'books', 'adhkar' => 'adhkar_items'][(string) ($_POST['content_type'] ?? '')] ?? '';
                $id = filter_var($_POST['id'] ?? null, FILTER_VALIDATE_INT);
                if ($table === '' || !$id || $id < 1) throw new RuntimeException('عنصر المحتوى غير صالح.');
                $type = (string) $_POST['content_type'];
                if (($_POST['content_action'] ?? '') === 'update') {
                    $published = isset($_POST['publish']) ? 1 : 0;
                    if ($type === 'scholar') {
                        $source = trim((string) ($_POST['source_url'] ?? ''));
                        if (!is_https_url($source) || trim((string) ($_POST['rights_basis'] ?? '')) === '') throw new RuntimeException('أدخل مصدرًا آمنًا وأساس الحقوق.');
                        db()->prepare('UPDATE scholars SET name=?,biography=?,source_url=?,rights_basis=?,is_published=?,sort_order=? WHERE id=?')->execute([trim((string) $_POST['name']), trim((string) $_POST['biography']), $source, trim((string) $_POST['rights_basis']), $published, (int) ($_POST['sort_order'] ?? 0), $id]);
                    } elseif ($type === 'lesson') {
                        $url = trim((string) ($_POST['source_url'] ?? '')); $videoId = youtube_video_id($url);
                        if (!$videoId) throw new RuntimeException('رابط فيديو YouTube غير صالح.');
                        [$lessonScholarId,$lessonSeriesId] = validated_lesson_scope((int)($_POST['scholar_id'] ?? 0),(int)($_POST['series_id'] ?? 0));
                        db()->prepare('UPDATE lessons SET scholar_id=NULLIF(?,0),series_id=NULLIF(?,0),title=?,description=?,youtube_id=?,source_url=?,is_published=?,sort_order=? WHERE id=?')->execute([$lessonScholarId, $lessonSeriesId, trim((string) $_POST['title']), trim((string) $_POST['description']), $videoId, $url, $published, (int) ($_POST['sort_order'] ?? 0), $id]);
                    } elseif ($type === 'series') {
                        $source = trim((string) ($_POST['source_url'] ?? ''));
                        if ($source !== '' && !is_https_url($source)) throw new RuntimeException('رابط المصدر يجب أن يبدأ بـ HTTPS.');
                        db()->prepare('UPDATE series SET scholar_id=NULLIF(?,0),title=?,description=?,source_url=?,is_published=?,sort_order=? WHERE id=?')->execute([(int) ($_POST['scholar_id'] ?? 0), trim((string) $_POST['title']), trim((string) $_POST['description']), $source ?: null, $published, (int) ($_POST['sort_order'] ?? 0), $id]);
                    } elseif ($type === 'book') {
                        $source = trim((string) ($_POST['source_url'] ?? '')); $rights = trim((string) ($_POST['rights_basis'] ?? '')); $downloadUrl = trim((string)($_POST['download_url'] ?? ''));
                        if (!is_https_url($source) || $rights === '') throw new RuntimeException('أدخل مصدرًا آمنًا وأساس الحقوق.');
                        if ($downloadUrl !== '' && !is_https_url($downloadUrl)) throw new RuntimeException('رابط التنزيل يجب أن يكون HTTPS صالحًا.');
                        $language = (string) ($_POST['language'] ?? 'ar'); $yearRaw = trim((string) ($_POST['publication_year'] ?? '')); $year = $yearRaw === '' ? null : filter_var($yearRaw, FILTER_VALIDATE_INT);
                        if (!in_array($language, ['ar','en','other'], true) || ($yearRaw !== '' && ($year === false || $year < 1000 || $year > 2200))) throw new RuntimeException('لغة الكتاب أو سنة النشر غير صالحة.');
                        $current = db()->prepare('SELECT file_key FROM books WHERE id=?'); $current->execute([$id]); $previousKey = $current->fetchColumn();
                        if ($previousKey === false) throw new RuntimeException('الكتاب غير موجود.');
                        $nextKey = $previousKey ?: null; $nextSize = null; $storage = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'books';
                        if (isset($_FILES['book_pdf']) && ($_FILES['book_pdf']['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_NO_FILE) {
                            $uploaded = store_book_pdf($_FILES['book_pdf'], $storage); $nextKey = $uploaded['key']; $nextSize = $uploaded['size'];
                        }
                        $canDownload = isset($_POST['can_download']) && ($nextKey || $downloadUrl !== '') ? 1 : 0;
                        try {
                            db()->prepare('UPDATE books SET title=?,author=?,description=?,keywords=?,language=?,publication_year=?,rights_holder=NULLIF(?,\'\'),source_url=?,download_url=NULLIF(?,\'\'),rights_basis=?,file_key=?,file_size=COALESCE(?,file_size),can_download=?,is_published=? WHERE id=?')->execute([trim((string) $_POST['title']), trim((string) $_POST['author']), trim((string) $_POST['description']), trim((string) ($_POST['keywords'] ?? '')) ?: null, $language, $year, trim((string) ($_POST['rights_holder'] ?? '')), $source, $downloadUrl, $rights, $nextKey, $nextSize, $canDownload, $published, $id]);
                        } catch (Throwable $saveFailure) {
                            if ($nextKey !== $previousKey && is_string($nextKey)) { $newPath = $storage . DIRECTORY_SEPARATOR . $nextKey; if (is_file($newPath)) unlink($newPath); }
                            throw $saveFailure;
                        }
                        if ($nextKey !== $previousKey && is_string($previousKey) && preg_match('/^[a-f0-9]{40}\.pdf$/', $previousKey)) { $oldPath = $storage . DIRECTORY_SEPARATOR . $previousKey; if (is_file($oldPath)) unlink($oldPath); }
                    } elseif ($type === 'adhkar') {
                        $source = trim((string) ($_POST['source_url'] ?? ''));
                        if (!is_https_url($source) || trim((string) ($_POST['source_reference'] ?? '')) === '' || !isset($_POST['reviewed'])) throw new RuntimeException('أدخل مصدرًا آمنًا ومرجعًا وأكد المراجعة.');
                        $count = ($_POST['repeat_count'] ?? '') === '' ? null : max(1, min(1000, (int) $_POST['repeat_count']));
                        db()->prepare('UPDATE adhkar_items SET title=?,formula=?,source_reference=?,source_url=?,repeat_count=?,category=?,is_reviewed=1,is_published=?,sort_order=? WHERE id=?')->execute([trim((string) $_POST['title']), trim((string) $_POST['formula']), trim((string) $_POST['source_reference']), $source, $count, trim((string) $_POST['category']), $published, (int) ($_POST['sort_order'] ?? 0), $id]);
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
                [$lessonScholarId,$lessonSeriesId] = validated_lesson_scope((int)($_POST['scholar_id'] ?? 0),(int)($_POST['series_id'] ?? 0));
                db()->prepare('INSERT INTO lessons (scholar_id,series_id,title,description,youtube_id,source_url,sort_order,is_published) VALUES (NULLIF(?,0),NULLIF(?,0),?,?,?,?,?,?)')->execute([$lessonScholarId, $lessonSeriesId, trim((string) $_POST['title']), trim((string) $_POST['description']), $id, $url, (int) ($_POST['sort_order'] ?? 0), isset($_POST['publish']) ? 1 : 0]);
            } elseif ($kind === 'book') {
                if (trim((string) $_POST['rights_basis']) === '' || !is_https_url(trim((string) $_POST['source_url']))) throw new RuntimeException('يلزم توثيق مصدر آمن وأساس حقوق الإتاحة.');
                $downloadUrl = trim((string)($_POST['download_url'] ?? ''));
                if ($downloadUrl !== '' && !is_https_url($downloadUrl)) throw new RuntimeException('رابط التنزيل يجب أن يكون HTTPS صالحًا.');
                $language = (string) ($_POST['language'] ?? 'ar'); $yearRaw = trim((string) ($_POST['publication_year'] ?? '')); $year = $yearRaw === '' ? null : filter_var($yearRaw, FILTER_VALIDATE_INT);
                if (!in_array($language, ['ar','en','other'], true) || ($yearRaw !== '' && ($year === false || $year < 1000 || $year > 2200))) throw new RuntimeException('لغة الكتاب أو سنة النشر غير صالحة.');
                $fileKey = null; $fileSize = null;
                $storage = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'books';
                if (isset($_FILES['book_pdf']) && ($_FILES['book_pdf']['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_NO_FILE) {
                    $uploaded = store_book_pdf($_FILES['book_pdf'], $storage); $fileKey = $uploaded['key']; $fileSize = $uploaded['size'];
                }
                $canDownload = isset($_POST['can_download']) && ($fileKey || $downloadUrl !== '') ? 1 : 0;
                try {
                    db()->prepare('INSERT INTO books (title,author,description,keywords,language,publication_year,rights_holder,source_url,download_url,rights_basis,file_key,file_size,can_download,is_published) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)')->execute([trim((string) $_POST['title']), trim((string) $_POST['author']), trim((string) $_POST['description']), trim((string) ($_POST['keywords'] ?? '')) ?: null, $language, $year, trim((string) ($_POST['rights_holder'] ?? '')) ?: null, trim((string) $_POST['source_url']), $downloadUrl ?: null, trim((string) $_POST['rights_basis']), $fileKey, $fileSize, $canDownload, isset($_POST['publish']) ? 1 : 0]);
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
        } catch (Throwable $exception) { if (db()->inTransaction()) db()->rollBack(); $adminError = $exception instanceof RuntimeException ? $exception->getMessage() : 'تعذر حفظ المحتوى. راجع الحقول أو الاتصال بقاعدة البيانات.'; }
    }
    $scholars = db()->query('SELECT id,name,is_published FROM scholars ORDER BY sort_order,id DESC')->fetchAll();
    $seriesItems = db()->query('SELECT * FROM series ORDER BY sort_order,id DESC')->fetchAll();
    $lessons = db()->query('SELECT l.*,s.name AS scholar_name FROM lessons l LEFT JOIN scholars s ON s.id=l.scholar_id ORDER BY l.sort_order,l.id DESC')->fetchAll();
    $books = db()->query('SELECT * FROM books ORDER BY id DESC')->fetchAll();
    $adhkarItems = db()->query('SELECT * FROM adhkar_items ORDER BY sort_order,id DESC')->fetchAll();
    $auditRows = db()->query('SELECT a.action,a.target_type,a.target_id,a.created_at,u.email AS actor_email FROM audit_logs a LEFT JOIN users u ON u.id=a.actor_id ORDER BY a.id DESC LIMIT 100')->fetchAll();
    $pendingComments = db()->query("SELECT c.id,c.body,c.created_at,l.title AS lesson_title,u.display_name,u.username FROM lesson_comments c JOIN lessons l ON l.id=c.lesson_id JOIN users u ON u.id=c.user_id WHERE c.status='pending' ORDER BY c.created_at LIMIT 100")->fetchAll();
    $dashboardMetrics = [
        'users' => (int) db()->query('SELECT COUNT(*) FROM users')->fetchColumn(),
        'scholars' => (int) db()->query('SELECT COUNT(*) FROM scholars WHERE is_published=1')->fetchColumn(),
        'series' => (int) db()->query('SELECT COUNT(*) FROM series WHERE is_published=1')->fetchColumn(),
        'lessons' => (int) db()->query('SELECT COUNT(*) FROM lessons WHERE is_published=1')->fetchColumn(),
        'books' => (int) db()->query('SELECT COUNT(*) FROM books WHERE is_published=1')->fetchColumn(),
    ];
    $managedUsers = [];
    if (($admin['role'] ?? '') === 'owner') $managedUsers = db()->query('SELECT id,email,role,email_verified_at,created_at FROM users ORDER BY id DESC LIMIT 100')->fetchAll();
    $smtpConfig = ['smtp_host'=>'','smtp_port'=>'587','smtp_security'=>'tls','smtp_username'=>'','smtp_from_email'=>'','smtp_from_name'=>'أثر'];
    foreach ($smtpConfig as $key=>$fallback) $smtpConfig[$key] = platform_setting($key,$fallback);
    $smtpPasswordConfigured = platform_setting('smtp_password_cipher') !== '';
    $dorarTokenConfigured = platform_setting('dorar_api_token_cipher') !== '' || trim((string)(getenv('ATHAR_DORAR_API_TOKEN') ?: '')) !== '';
    if (($admin['role'] ?? '') === 'owner') $backupArtifacts = db()->query('SELECT id,schema_version,byte_size,status,expires_at,created_at FROM backup_artifacts ORDER BY id DESC LIMIT 20')->fetchAll();
    $backupGrantReady = !empty($_SESSION['backup_grant_id']) && !empty($_SESSION['backup_grant_token']);
    $title='إدارة أثر'; $activePath=$path; $viewPath=dirname(__DIR__).'/app/Views/pages/admin.php'; $flashMessage=take_flash();
    ob_start(); require $viewPath; $content=(string) ob_get_clean(); require dirname(__DIR__).'/app/Views/layout.php'; exit;
}

$curatedBooks = $curatedLessons = $curatedSeries = $curatedAdhkar = $curatedScholars = [];
$publicScholar = $publicSeries = null;
$scholarSeries = $scholarLessons = $seriesLessons = $lessonComments = [];
$activeSeriesLesson = null;
$homeLessons = $homeSeries = $homeBooks = [];
$curatedBookTotal = 0; $bookPage = 1; $bookPageCount = 1; $localBookSearch = '';
try {
    if ($path === '/') {
        $homeLessons = db()->query('SELECT l.id,l.title,l.description,l.series_id,r.title AS series_title FROM lessons l LEFT JOIN series r ON r.id=l.series_id AND r.is_published=1 WHERE l.is_published=1 ORDER BY l.id DESC LIMIT 4')->fetchAll();
        $homeSeries = db()->query('SELECT id,title,description FROM series WHERE is_published=1 ORDER BY id DESC LIMIT 3')->fetchAll();
        $homeBooks = db()->query('SELECT id,title,author FROM books WHERE is_published=1 ORDER BY id DESC LIMIT 3')->fetchAll();
    }
    if ($path === '/books') {
        $user = current_user();
        $localBookSearch = trim(mb_substr((string) ($_GET['local_q'] ?? ''), 0, 80, 'UTF-8'));
        $bookPage = max(1, min(10000, (int) ($_GET['book_page'] ?? 1)));
        $where = ' FROM books WHERE is_published=1'; $searchParams = [];
        if ($localBookSearch !== '') { $where .= ' AND (title LIKE ? OR author LIKE ? OR COALESCE(keywords,\'\') LIKE ?)'; $like = '%' . $localBookSearch . '%'; $searchParams = [$like, $like, $like]; }
        $count = db()->prepare('SELECT COUNT(*)' . $where); $count->execute($searchParams); $curatedBookTotal = (int) $count->fetchColumn();
        $bookPageCount = max(1, (int) ceil($curatedBookTotal / 20)); $bookPage = min($bookPage, $bookPageCount);
        $offset = ($bookPage - 1) * 20;
        $query = 'SELECT b.id,b.title,b.author,b.description,b.keywords,b.language,b.publication_year,b.rights_holder,b.source_url,b.download_url,b.can_download,b.file_key,(SELECT COUNT(*) FROM user_favorites f WHERE f.user_id=? AND f.target_type=\'book\' AND f.target_id=b.id) AS is_favorite' . $where . ' ORDER BY b.title LIMIT 20 OFFSET ' . $offset;
        $statement = db()->prepare($query); $statement->execute(array_merge([(int) ($user['id'] ?? 0)], $searchParams)); $curatedBooks = $statement->fetchAll();
    }
    if ($path === '/lessons') {
        $user = current_user();
        $curatedScholars = db()->query('SELECT id,name,biography FROM scholars WHERE is_published=1 ORDER BY sort_order,id LIMIT 50')->fetchAll();
        $query = 'SELECT l.id,l.title,l.description,l.youtube_id,l.source_url,l.series_id,s.id AS scholar_id,s.name AS scholar_name,r.title AS series_name,(SELECT position_seconds FROM video_progress vp WHERE vp.user_id=? AND vp.lesson_id=l.id) AS position_seconds,(SELECT COUNT(*) FROM user_favorites f WHERE f.user_id=? AND f.target_type=\'lesson\' AND f.target_id=l.id) AS is_favorite FROM lessons l LEFT JOIN scholars s ON s.id=l.scholar_id AND s.is_published=1 LEFT JOIN series r ON r.id=l.series_id AND r.is_published=1 WHERE l.is_published=1 AND l.series_id IS NULL ORDER BY s.sort_order,s.id,l.sort_order,l.id DESC LIMIT 100';
        $statement = db()->prepare($query); $statement->execute([(int) ($user['id'] ?? 0), (int) ($user['id'] ?? 0)]); $curatedLessons = $statement->fetchAll();
        $curatedSeries = db()->query("SELECT r.id,r.title,r.description,r.scholar_id,s.name AS scholar_name,(SELECT COUNT(*) FROM lessons l WHERE l.series_id=r.id AND l.is_published=1) AS lesson_count FROM series r LEFT JOIN scholars s ON s.id=r.scholar_id AND s.is_published=1 WHERE r.is_published=1 ORDER BY s.sort_order,s.id,r.sort_order,r.id DESC LIMIT 100")->fetchAll();
        $selectedLessonId = filter_var($_GET['lesson'] ?? null,FILTER_VALIDATE_INT) ?: 0; $selectedFreeLesson = null;
        foreach ($curatedLessons as $candidate) if ((int)$candidate['id']===$selectedLessonId && empty($candidate['series_id'])) { $selectedFreeLesson=$candidate; break; }
        $freeLessonComments=[];
        if ($selectedFreeLesson) { $commentQuery=db()->prepare("SELECT c.body,c.status,c.created_at,u.display_name,u.username FROM lesson_comments c JOIN users u ON u.id=c.user_id WHERE c.lesson_id=? AND (c.status='visible' OR c.user_id=?) ORDER BY c.created_at DESC LIMIT 100"); $commentQuery->execute([$selectedFreeLesson['id'],(int)($user['id'] ?? 0)]); $freeLessonComments=$commentQuery->fetchAll(); }
    }
    if ($path === '/adhkar') $curatedAdhkar = db()->query('SELECT title,formula,source_reference,source_url,repeat_count,category FROM adhkar_items WHERE is_published=1 AND is_reviewed=1 ORDER BY category,sort_order,id DESC LIMIT 100')->fetchAll();
    if ($path === '/scholar') {
        $id = filter_var($_GET['id'] ?? null, FILTER_VALIDATE_INT);
        if ($id) {
            $query = db()->prepare('SELECT id,name,biography,source_url FROM scholars WHERE id=? AND is_published=1'); $query->execute([$id]); $publicScholar = $query->fetch() ?: null;
            if ($publicScholar) {
                $query = db()->prepare('SELECT r.id,r.title,r.description,COUNT(l.id) AS lesson_count FROM series r LEFT JOIN lessons l ON l.series_id=r.id AND l.is_published=1 WHERE r.scholar_id=? AND r.is_published=1 GROUP BY r.id ORDER BY r.sort_order,r.id'); $query->execute([$id]); $scholarSeries = $query->fetchAll();
                $query = db()->prepare('SELECT id,title,description FROM lessons WHERE scholar_id=? AND series_id IS NULL AND is_published=1 ORDER BY sort_order,id'); $query->execute([$id]); $scholarLessons = $query->fetchAll();
            }
        }
    }
    if ($path === '/series') {
        $id = filter_var($_GET['id'] ?? null, FILTER_VALIDATE_INT);
        if ($id) {
            $query = db()->prepare('SELECT r.id,r.title,r.description,r.source_url,r.scholar_id,s.name AS scholar_name FROM series r LEFT JOIN scholars s ON s.id=r.scholar_id AND s.is_published=1 WHERE r.id=? AND r.is_published=1'); $query->execute([$id]); $publicSeries = $query->fetch() ?: null;
            if ($publicSeries) {
                $query = db()->prepare('SELECT l.id,l.title,l.description,l.youtube_id,l.source_url,(SELECT position_seconds FROM video_progress vp WHERE vp.user_id=? AND vp.lesson_id=l.id) AS position_seconds FROM lessons l WHERE l.series_id=? AND l.is_published=1 ORDER BY l.sort_order,l.id');
                $query->execute([(int)(current_user()['id'] ?? 0),$id]); $seriesLessons = $query->fetchAll();
                $activeSeriesLessonId = (int)($_GET['lesson'] ?? 0);
                $activeSeriesLesson = null;
                foreach ($seriesLessons as $candidate) if ((int)$candidate['id']===$activeSeriesLessonId) { $activeSeriesLesson=$candidate; break; }
                if (!$activeSeriesLesson) $activeSeriesLesson=$seriesLessons[0] ?? null;
                $lessonComments=[];
                if ($activeSeriesLesson) {
                    $commentQuery=db()->prepare("SELECT c.id,c.user_id,c.body,c.status,c.created_at,u.display_name,u.username FROM lesson_comments c JOIN users u ON u.id=c.user_id WHERE c.lesson_id=? AND (c.status='visible' OR c.user_id=?) ORDER BY c.created_at DESC LIMIT 100");
                    $commentQuery->execute([$activeSeriesLesson['id'],(int)(current_user()['id'] ?? 0)]); $lessonComments=$commentQuery->fetchAll();
                }
            }
        }
    }
} catch (Throwable $exception) { /* The live provider pages remain available before local database setup. */ }

$pages = [
    '/' => ['title' => 'الرئيسية', 'view' => 'pages/home.php'],
    '/quran' => ['title' => 'القرآن الكريم', 'view' => 'pages/quran.php'],
    '/hadith' => ['title' => 'الحديث', 'view' => 'pages/hadith.php'],
    '/books' => ['title' => 'الكتب', 'view' => 'pages/books.php'],
    '/lessons' => ['title' => 'الدروس', 'view' => 'pages/lessons.php'],
    '/scholar' => ['title' => 'العالم', 'view' => 'pages/scholar.php'],
    '/series' => ['title' => 'السلسلة', 'view' => 'pages/series.php'],
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
$siteBaseUrl = rtrim((string) (getenv('ATHAR_BASE_URL') ?: ''), '/');
$indexablePaths = ['/', '/quran', '/hadith', '/books', '/lessons', '/adhkar', '/about', '/scholar', '/series'];
$privatePaths = ['/login', '/register', '/account', '/admin', '/forgot-password', '/reset-password', '/verify-email'];
$isPublicCanonical = in_array($path, $indexablePaths, true) && !in_array($path, $privatePaths, true) && $page['view'] !== 'pages/not-found.php' && $siteBaseUrl !== '' && is_https_url($siteBaseUrl . '/');
if ($path === '/scholar' && empty($publicScholar['id'])) $isPublicCanonical = false;
if ($path === '/series' && empty($publicSeries['id'])) $isPublicCanonical = false;
$canonicalPath = $path;
if (in_array($path, ['/scholar', '/series'], true) && isset($_GET['id']) && ctype_digit((string) $_GET['id'])) $canonicalPath .= '?id=' . (int) $_GET['id'];
$canonicalUrl = $isPublicCanonical ? $siteBaseUrl . $canonicalPath : '';
$seoRobots = (!$isPublicCanonical || (($_GET && !in_array($path, ['/scholar', '/series'], true))) || ($page['view'] === 'pages/not-found.php')) ? 'noindex,nofollow' : 'index,follow,max-image-preview:large';
$seoTitle = $title . ' | أثر';
$seoDescription = 'اقرأ القرآن وابحث في الحديث واستكشف الدروس والكتب والأذكار في منصة أثر العربية.';
if ($path === '/') $seoDescription = 'اقرأ القرآن، وابحث عن الحديث، وتابع دروس العلماء، واستكشف الكتب والأذكار في منصة أثر العربية.';
elseif ($path === '/quran') $seoDescription = 'اقرأ القرآن الكريم وابحث في الآيات واستمع إلى التلاوة واحفظ موضع قراءتك.';
elseif ($path === '/hadith') $seoDescription = 'ابحث بالعربية في الموسوعة الحديثية، مع نص الحديث وخلاصة حكم المحدّث ورابط المرجع.';
elseif ($path === '/books') $seoDescription = 'تصفح مكتبة أثر وابحث في الكتب الإسلامية المتاحة للعامة.';
elseif ($path === '/lessons') $seoDescription = 'شاهد دروس العلماء والسلاسل العلمية المرتبة في مكتبة أثر.';
elseif ($path === '/adhkar') $seoDescription = 'تصفح أذكار الصباح والمساء والأدعية والنصوص الموثقة.';
elseif ($path === '/scholar' && !empty($publicScholar['name'])) { $seoTitle = $publicScholar['name'] . ' | علماء أثر'; $seoDescription = mb_substr(trim(strip_tags((string) $publicScholar['biography'])) ?: ('السلاسل والدروس المنشورة للشيخ ' . $publicScholar['name'] . ' في أثر.'), 0, 250, 'UTF-8'); }
elseif ($path === '/series' && !empty($publicSeries['title'])) { $seoTitle = $publicSeries['title'] . ' | سلسلة دروس في أثر'; $seoDescription = mb_substr(trim(strip_tags((string) $publicSeries['description'])) ?: ('تابع دروس سلسلة ' . $publicSeries['title'] . ' مرتبة في أثر.'), 0, 250, 'UTF-8'); }
    $loadKnowledgeScript = in_array($path, ['/hadith', '/books', '/lessons', '/series', '/adhkar'], true);
$structuredData = $isPublicCanonical ? [
    '@context' => 'https://schema.org',
    '@graph' => [
        ['@type' => 'Organization', '@id' => $siteBaseUrl . '/#organization', 'name' => 'أثر', 'url' => $siteBaseUrl . '/', 'logo' => $siteBaseUrl . '/assets/images/athar-logo-cropped.png'],
        ['@type' => 'WebSite', '@id' => $siteBaseUrl . '/#website', 'url' => $siteBaseUrl . '/', 'name' => 'أثر', 'inLanguage' => 'ar', 'publisher' => ['@id' => $siteBaseUrl . '/#organization']],
        ['@type' => 'WebPage', '@id' => $canonicalUrl . '#webpage', 'url' => $canonicalUrl, 'name' => $seoTitle, 'description' => $seoDescription, 'inLanguage' => 'ar', 'isPartOf' => ['@id' => $siteBaseUrl . '/#website']],
        ['@type' => 'BreadcrumbList', 'itemListElement' => [
            ['@type' => 'ListItem', 'position' => 1, 'name' => 'الرئيسية', 'item' => $siteBaseUrl . '/'],
            ['@type' => 'ListItem', 'position' => 2, 'name' => $path === '/scholar' && !empty($publicScholar['name']) ? $publicScholar['name'] : ($path === '/series' && !empty($publicSeries['title']) ? $publicSeries['title'] : $title), 'item' => $canonicalUrl],
        ]],
    ],
] : null;
$viewPath = dirname(__DIR__) . '/app/Views/' . $page['view'];

ob_start();
require $viewPath;
$content = (string) ob_get_clean();

require dirname(__DIR__) . '/app/Views/layout.php';
