<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/backend/bootstrap.php';
require_once dirname(__DIR__) . '/app/helpers.php';
require_once dirname(__DIR__) . '/admin/auth.php';
require_once dirname(__DIR__) . '/admin/authorization.php';
require_once dirname(__DIR__) . '/admin/editorial.php';
require_once dirname(__DIR__) . '/app/backup.php';
require_once dirname(__DIR__) . '/app/mailer.php';
ini_set('display_errors', '0');
set_exception_handler(static function (Throwable $exception): void {
    error_log('[Athar] ' . $exception->getMessage());
    if (!headers_sent()) http_response_code(500);
    exit('حدث خطأ غير متوقع. حاول مرة أخرى لاحقًا.');
});

ini_set('session.use_strict_mode', '1');
$sessionRequestPath = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$adminSessionRequest = admin_is_request_path($sessionRequestPath);
if ($adminSessionRequest) session_name('ATHARADMIN');
session_set_cookie_params([
    'httponly' => true,
    'secure' => (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off'),
    'samesite' => $adminSessionRequest ? 'Strict' : 'Lax',
    'path' => $adminSessionRequest ? '/admin' : '/',
]);
if (session_status() !== PHP_SESSION_ACTIVE) session_start();

header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: strict-origin-when-cross-origin');
header('X-Frame-Options: DENY');
header('Permissions-Policy: camera=(), microphone=(self), geolocation=()');
header("Content-Security-Policy: default-src 'self'; img-src 'self' data: https://i.ytimg.com; media-src 'self' https://cdn.islamic.network https://download.quranicaudio.com https://cdn.jsdelivr.net https://*.mp3quran.net; style-src 'self'; script-src 'self' 'wasm-unsafe-eval' https://www.youtube.com https://s.ytimg.com https://cdn.jsdelivr.net; worker-src 'self' blob:; connect-src 'self' https://api.quranpedia.net https://api.alquran.cloud https://quranicaudio.com https://pray.api.islamic.network https://cdn.jsdelivr.net https://www.youtube.com https://mp3quran.net https://*.mp3quran.net https://raw.githubusercontent.com; frame-src https://api.quranpedia.net https://www.youtube-nocookie.com https://www.youtube.com; object-src 'none'; base-uri 'self'; frame-ancestors 'none'");

$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$method = strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');

if ($path === '/api/v1' || str_starts_with($path, '/api/v1/')) require dirname(__DIR__) . '/backend/api.php';

if ($path === '/email-unsubscribe') {
    $unsubscribeInput = $method === 'POST' ? $_POST : $_GET;
    $userId = filter_var($unsubscribeInput['u'] ?? null, FILTER_VALIDATE_INT);
    $token = (string) ($unsubscribeInput['t'] ?? '');
    $valid = $userId && $userId > 0 && preg_match('/^[a-f0-9]{64}$/i', $token) && hash_equals(email_campaign_unsubscribe_url((int) $userId), platform_email_base_url() . '/email-unsubscribe?u=' . (int) $userId . '&t=' . $token);
    if (!$valid) { http_response_code(400); exit('رابط إلغاء الاشتراك غير صالح.'); }
    if ($method === 'POST') {
        require_csrf();
        db()->prepare('UPDATE users SET email_marketing_opt_in=0 WHERE id=?')->execute([(int) $userId]);
        db()->prepare("UPDATE email_campaign_recipients SET user_id=NULL,email='',status=IF(status IN ('pending','processing'),'skipped',status),failure_note='إلغاء الاشتراك.' WHERE user_id=?")->execute([(int) $userId]);
        db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([null,'email-campaign-unsubscribed','user',(string)$userId]);
        header('Content-Type: text/html; charset=utf-8');
        echo '<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><body style="margin:0;padding:48px 16px;background:#f4f3ed;color:#22332c;font:16px Arial;text-align:center"><main style="max-width:520px;margin:auto;padding:32px;border:1px solid #d8e2da;border-radius:18px;background:#fff"><h1 style="color:#174f40">تم إلغاء الاشتراك</h1><p>لن تصلك رسائل تحديثات أثر بعد الآن. رسائل الحساب الضرورية ستظل مستمرة.</p><a href="/" style="color:#1e6753">العودة إلى أثر</a></main></body></html>'; exit;
    }
    header('Content-Type: text/html; charset=utf-8');
    echo '<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><body style="margin:0;padding:48px 16px;background:#f4f3ed;color:#22332c;font:16px Arial;text-align:center"><main style="max-width:520px;margin:auto;padding:32px;border:1px solid #d8e2da;border-radius:18px;background:#fff"><img src="' . e(platform_email_base_url() . '/assets/images/athar-logo-cropped.png') . '" alt="أثر" width="94"><h1 style="color:#174f40">إدارة رسائل أثر</h1><p>هل تريد إلغاء رسائل الأخبار والتحديثات؟</p><form method="post">' . csrf_field() . '<input type="hidden" name="u" value="' . (int) $userId . '"><input type="hidden" name="t" value="' . e($token) . '"><button style="min-height:46px;padding:10px 20px;border:0;border-radius:10px;background:#1e6753;color:#fff;font-weight:bold">إلغاء الاشتراك</button></form></main></body></html>'; exit;
}
if (
    in_array($path, ['/login','/register','/account','/forgot-password','/reset-password','/verify-email','/series','/lessons'], true)
    || admin_is_request_path($path)
) header('Cache-Control: no-store, private');

if (in_array($path, ['/api/hadith/search', '/api/dorar/search'], true) && $method === 'GET') {
    if (!allow_session_rate('dorar_search', 30, 60) || !allow_shared_dorar_rate(55, 60)) { http_response_code(429); header('Content-Type: application/json; charset=utf-8'); echo json_encode(['error'=>'rate_limited'],JSON_UNESCAPED_UNICODE); exit; }
    $query = trim((string) ($_GET['q'] ?? ''));
    if (mb_strlen($query, 'UTF-8') < 2 || mb_strlen($query, 'UTF-8') > 200) { http_response_code(422); header('Content-Type: application/json; charset=utf-8'); echo json_encode(['error' => 'invalid_query'], JSON_UNESCAPED_UNICODE); exit; }
    $scope = $path === '/api/hadith/search' ? 'hadith' : (string) ($_GET['scope'] ?? 'hadith');
    $endpoints = ['hadith'=>'hadith', 'tafseer'=>'tafseer', 'aqeeda'=>'aqeeda', 'feqhia'=>'feqhia', 'alakhlaq'=>'alakhlaq', 'aadab'=>'aadab', 'history'=>'history'];
    if (!isset($endpoints[$scope])) { http_response_code(422); header('Content-Type: application/json; charset=utf-8'); echo json_encode(['error'=>'invalid_scope'],JSON_UNESCAPED_UNICODE); exit; }
    try { $token = trim(platform_secret('dorar_api_token', 'ATHAR_DORAR_API_TOKEN')); } catch (Throwable $exception) { $token = ''; }
    if ($token === '') { http_response_code(503); header('Content-Type: application/json; charset=utf-8'); echo json_encode(['error' => 'service_not_configured'], JSON_UNESCAPED_UNICODE); exit; }
    $parameters = ['q' => $query, 'limit' => 15, 'match' => in_array($_GET['match'] ?? 'all', ['any','all','phrase'], true) ? (string) ($_GET['match'] ?? 'all') : 'all'];
    if ($scope !== 'hadith') $parameters['in'] = in_array($_GET['in'] ?? 'both', ['title','text','both'], true) ? (string) ($_GET['in'] ?? 'both') : 'both';
    if ($scope === 'tafseer') {
        $sura = filter_var($_GET['sura'] ?? null, FILTER_VALIDATE_INT);
        $category = filter_var($_GET['category'] ?? null, FILTER_VALIDATE_INT);
        if ($sura !== false && $sura !== null) $parameters['sura'] = max(1, min(114, $sura));
        if ($category !== false && $category !== null) $parameters['category'] = max(0, min(19, $category));
    }
    $url = 'https://dorar.net/api/v1/' . $endpoints[$scope] . '/search?' . http_build_query($parameters);
    $context = stream_context_create(['http' => ['method' => 'GET', 'timeout' => 12, 'ignore_errors' => true, 'header' => "Accept: application/json\r\nAuthorization: Bearer {$token}\r\n"]]);
    $payload = @file_get_contents($url, false, $context);
    $statusCode = 0;
    foreach (($http_response_header ?? []) as $responseHeader) if (preg_match('/^HTTP\/\S+\s+(\d{3})/', $responseHeader, $match)) $statusCode = (int) $match[1];
    if (!is_string($payload) || $statusCode < 200 || $statusCode >= 300) { http_response_code($statusCode === 429 ? 429 : 502); header('Content-Type: application/json; charset=utf-8'); echo json_encode(['error' => 'search_unavailable'], JSON_UNESCAPED_UNICODE); exit; }
    $decoded = json_decode($payload, true);
    if (!is_array($decoded) || !is_array($decoded['data'] ?? null)) { http_response_code(502); header('Content-Type: application/json; charset=utf-8'); echo json_encode(['error' => 'invalid_provider_response'], JSON_UNESCAPED_UNICODE); exit; }
    header('Content-Type: application/json; charset=utf-8'); header('Cache-Control: no-store, private'); echo json_encode($decoded, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES); exit;
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
    $urls = ['/', '/quran', '/hadith', '/dorar', '/books', '/lessons', '/adhkar', '/about'];
    try { foreach (db()->query('SELECT id FROM scholars WHERE is_published=1')->fetchAll(PDO::FETCH_COLUMN) as $id) $urls[] = '/scholar?id=' . (int) $id; foreach (db()->query('SELECT id FROM series WHERE is_published=1')->fetchAll(PDO::FETCH_COLUMN) as $id) $urls[] = '/series?id=' . (int) $id; } catch (Throwable $exception) { }
    header('Content-Type: application/xml; charset=utf-8');
    echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n";
    foreach ($urls as $url) echo '<url><loc>' . htmlspecialchars($base . $url, ENT_XML1 | ENT_QUOTES, 'UTF-8') . "</loc></url>\n";
    echo "</urlset>"; exit;
}

function send_account_token(string $email, string $type): bool
{
    $purpose = $type === 'verify_email' ? 'verify_email' : 'password_reset';
    $query = db()->prepare('SELECT id FROM users WHERE email=? AND suspended_at IS NULL LIMIT 1');
    $query->execute([$email]); $id = $query->fetchColumn();
    return $id ? web_identity_otp()->issue((int)$id,$purpose) : false;
}

function allow_session_rate(string $key, int $limit, int $windowSeconds): bool
{
    $identityScopes = ['password_reset_requests'=>'forgot','password_reset_code_attempts'=>'reset','verification_resends'=>'resend','email_verification_attempts'=>'verify'];
    if (isset($identityScopes[$key])) {
        $identity = in_array($key,['verification_resends','email_verification_attempts'],true)
            ? (string)(current_user()['id'] ?? '')
            : strtolower(trim((string)($_SESSION['password_reset_email'] ?? $_POST['email'] ?? '')));
        if ($key === 'password_reset_requests') $identity = strtolower(trim((string)($_POST['email'] ?? '')));
        return web_identity_rate($identityScopes[$key],$identity,$limit,$windowSeconds);
    }
    $now = time(); $attempts = $_SESSION[$key] ?? ['started' => $now, 'count' => 0];
    if (!is_array($attempts) || $now - (int) ($attempts['started'] ?? 0) > $windowSeconds) $attempts = ['started' => $now, 'count' => 0];
    $attempts['count'] = (int) ($attempts['count'] ?? 0) + 1; $_SESSION[$key] = $attempts;
    return $attempts['count'] <= $limit;
}

function allow_shared_dorar_rate(int $limit, int $windowSeconds): bool
{
    $file = @fopen(sys_get_temp_dir() . DIRECTORY_SEPARATOR . 'athar-dorar-rate-' . hash('sha256', dirname(__DIR__)) . '.json', 'c+');
    if ($file === false) return false;
    try {
        if (!flock($file, LOCK_EX)) return false;
        rewind($file);
        $state = json_decode((string) stream_get_contents($file), true);
        $now = time();
        $timestamps = is_array($state) ? array_values(array_filter($state, static fn ($timestamp): bool => is_int($timestamp) && $timestamp > $now - $windowSeconds)) : [];
        if (count($timestamps) >= $limit) return false;
        $timestamps[] = $now;
        rewind($file);
        ftruncate($file, 0);
        if (fwrite($file, json_encode($timestamps)) === false) return false;
        fflush($file);
        return count($timestamps) <= $limit;
    } finally {
        flock($file, LOCK_UN);
        fclose($file);
    }
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
        else {
            try {
                web_identity_auth()->reset($email,$code,$password,(string)($_POST['password_confirmation']??''));
                unset($_SESSION['password_reset_email'],$_SESSION['password_reset_code_attempts']);
                flash('تم تغيير كلمة المرور. سجّل الدخول من جديد.'); redirect('/login');
            } catch (\Athar\Http\ApiError $error) {
                $authError = $error->getMessage()==='invalid_fields' ? 'راجع كلمة المرور وتأكيدها.' : 'الرمز غير صحيح أو انتهت صلاحيته. اطلب رمزًا جديدًا.';
            }
        }
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
    } elseif ($action === 'review') {
        db()->prepare('INSERT INTO account_daily_stats (user_id,activity_date,quran_sessions) VALUES (?,UTC_DATE(),1) ON DUPLICATE KEY UPDATE quran_sessions=LEAST(65535,quran_sessions+1)')->execute([$user['id']]);
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

if ($path === '/scholar-image' && $method === 'GET') {
    $key = (string) ($_GET['key'] ?? '');
    if (!preg_match('/^[a-f0-9]{40}\.(?:jpg|png|webp)$/', $key)) { http_response_code(404); exit; }
    $imagePath = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'scholars' . DIRECTORY_SEPARATOR . $key;
    if (!is_file($imagePath)) { http_response_code(404); exit; }
    $mime = match (pathinfo($key, PATHINFO_EXTENSION)) { 'jpg' => 'image/jpeg', 'png' => 'image/png', 'webp' => 'image/webp', default => 'application/octet-stream' };
    header('Content-Type: ' . $mime); header('Cache-Control: public, max-age=86400, stale-while-revalidate=604800'); header('Content-Length: ' . filesize($imagePath));
    readfile($imagePath); exit;
}

if ($path === '/book-download' && $method === 'GET') {
    $id = filter_var($_GET['id'] ?? null, FILTER_VALIDATE_INT);
    if (!$id || $id < 1) { http_response_code(404); exit('الملف غير موجود.'); }
    $statement = db()->prepare('SELECT download_url FROM books WHERE id=? AND is_published=1 AND can_download=1 LIMIT 1');
    $statement->execute([$id]); $book = $statement->fetch();
    if (!$book || !is_https_url((string) ($book['download_url'] ?? ''))) { http_response_code(404); exit('رابط تنزيل الكتاب غير متاح.'); }
    $downloadUser = current_user();
    if ($downloadUser) db()->prepare('INSERT INTO account_daily_stats (user_id,activity_date,book_downloads) VALUES (?,UTC_DATE(),1) ON DUPLICATE KEY UPDATE book_downloads=LEAST(65535,book_downloads+1)')->execute([(int) $downloadUser['id']]);
    header('Cache-Control: no-store, private'); header('Location: ' . (string) $book['download_url'], true, 302); exit;
}

if ($path === '/admin/backup-download' && $method === 'POST') {
    $owner = admin_require_auth();
    if (!admin_can($owner, 'recovery')) { http_response_code(403); exit('هذه العملية متاحة لمالك المنصة فقط.'); }
    require_csrf();
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
        if ($attempts['count'] > 8 || !web_identity_rate($path === '/register' ? 'register' : 'login',$email,$path === '/register' ? 5 : 10,$path === '/register' ? 3600 : 900)) {
            $authError = 'محاولات كثيرة. انتظر 15 دقيقة ثم حاول مجددًا.';
        } elseif (!filter_var($email, FILTER_VALIDATE_EMAIL) || strlen($email) > 190 || strlen($password) < 10 || strlen($password) > 200) {
            $authError = 'أدخل بريدًا صالحًا وكلمة مرور من 10 أحرف على الأقل.';
        } elseif ($path === '/register' && !hash_equals($password,(string)($_POST['password_confirmation']??''))) {
            $authError = 'كلمتا المرور غير متطابقتين.';
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
                    $id = web_identity_auth()->createAccount(['email'=>$email,'display_name'=>$displayName,'username'=>$username,'password'=>$password,'password_confirmation'=>(string)($_POST['password_confirmation']??'')],isset($_POST['email_marketing_opt_in']));
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
                    $reply = web_identity_auth()->login($email,$password,false);
                    $id = (int)$reply['user']['id'];
                }
                if ($authError === '') {
                    if ($path === '/register') {
                        try { $verificationSent = send_account_token($email, 'verify_email'); }
                        catch (Throwable $mailError) { error_log('[Athar] Verification mail delivery failed.'); $verificationSent = false; }
                    }
                    $statement = db()->prepare('SELECT id,email,display_name,username,role,session_version,email_verified_at,email_marketing_opt_in,preferences_json FROM users WHERE id=?');
                    $statement->execute([$id]);
                    session_regenerate_id(true);
                    $_SESSION['user'] = $statement->fetch();
                    $_SESSION['csrf'] = bin2hex(random_bytes(32));
                    $_SESSION['authenticated_at'] = time();
                    $_SESSION['last_activity'] = time();
                    unset($_SESSION['auth_attempts']);
                    if ($path === '/register') flash($verificationSent ? 'أرسلنا رمز تأكيد إلى بريدك، وصلاحيته 10 دقائق.' : 'الحساب غير مفعّل لأن رمز التأكيد لم يصل. يمكنك إعادة طلب الرمز من صفحة التأكيد بعد حل مشكلة البريد.', $verificationSent ? 'success' : 'error');
                    redirect('/account');
                }
            } catch (\Athar\Http\ApiError $exception) {
                if ($mergeTransaction && db()->inTransaction()) db()->rollBack();
                $authError = $exception->getMessage()==='account_conflict' ? 'البريد الإلكتروني أو اسم المستخدم مستخدم بالفعل.' : ($exception->getMessage()==='invalid_credentials' ? 'تعذر تسجيل الدخول بهذه البيانات.' : 'راجع بيانات الحساب وكلمة المرور وتأكيدها.');
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
    $logoutUser = require_login(); require_csrf();
    db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$logoutUser['id'], 'account-logout', 'user', (string) $logoutUser['id']]);
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
    $needsVerification = empty($user['email_verified_at']) && !in_array(($user['role'] ?? 'user'), ['admin','owner'], true);
    if ($method === 'POST') {
        assert_no_maintenance_lock();
        require_csrf();
        $action = (string) ($_POST['action'] ?? '');
        if ($needsVerification && !in_array($action, ['verify-email-code','resend-verification','delete-account'], true)) {
            flash('أكد بريدك الإلكتروني أولًا لتفعيل مزايا الحساب.', 'error');
            redirect('/account#account-verification');
        } elseif ($action === 'profile-save') {
            $displayName = trim((string) ($_POST['display_name'] ?? ''));
            $username = strtolower(trim((string) ($_POST['username'] ?? '')));
            $password = (string) ($_POST['current_password'] ?? '');
            $reserved = ['admin','administrator','root','support','athar','api','www','owner'];
            if (mb_strlen($displayName, 'UTF-8') < 2 || mb_strlen($displayName, 'UTF-8') > 80 || !preg_match('/^[a-z0-9_]{3,24}$/', $username) || in_array($username, $reserved, true)) $accountError = 'تحقق من الاسم واسم المستخدم (3–24 حرفًا إنجليزيًا صغيرًا أو رقمًا أو _).';
            else {
                $passwordQuery = db()->prepare('SELECT password_hash FROM users WHERE id=?'); $passwordQuery->execute([$user['id']]); $passwordHash = (string) $passwordQuery->fetchColumn();
                if (!password_verify($password, $passwordHash)) $accountError = 'كلمة المرور الحالية غير صحيحة.';
                else {
                    try { db()->prepare('UPDATE users SET display_name=?,username=?,email_marketing_opt_in=? WHERE id=?')->execute([$displayName,$username,isset($_POST['email_marketing_opt_in']) ? 1 : 0,$user['id']]); flash('تم تحديث بيانات الحساب.'); redirect('/account'); }
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
                try { web_identity_auth()->verify((int)$user['id'],$code,false); flash('تم تأكيد البريد الإلكتروني.'); redirect('/account'); }
                catch (\Athar\Http\ApiError $error) { $accountError = 'الرمز غير صحيح أو انتهت صلاحيته. اطلب رمزًا جديدًا.'; }
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
            $check=db()->prepare('SELECT * FROM users WHERE id=?');$check->execute([$user['id']]);$actor=$check->fetch();
            try {
                web_identity_auth()->changePassword($actor,(string)($_POST['current_password']??''),(string)($_POST['new_password']??''),(string)($_POST['new_password_confirmation']??''),false);
                $version=db()->prepare('SELECT session_version FROM users WHERE id=?');$version->execute([$user['id']]);
                session_regenerate_id(true);
                $_SESSION['user']['session_version']=(int)$version->fetchColumn();
                $_SESSION['authenticated_at']=time();$_SESSION['last_activity']=time();
                flash('تم تغيير كلمة المرور وتسجيل الخروج من الجلسات الأخرى.');redirect('/account');
            } catch (\Athar\Http\ApiError $error) {
                $accountError=$error->getMessage()==='invalid_credentials'?'كلمة المرور الحالية غير صحيحة.':'راجع كلمة المرور وتأكيدها.';
            }
        } elseif ($action === 'delete-account') {
            $password = (string) ($_POST['password'] ?? '');
            $statement = db()->prepare('SELECT password_hash FROM users WHERE id=?');
            $statement->execute([$user['id']]);
            $hash = (string) $statement->fetchColumn();
            if ($hash === '' || !password_verify($password, $hash)) $accountError = 'كلمة المرور غير صحيحة.';
            else {
                db()->prepare("UPDATE email_campaign_recipients SET user_id=NULL,email='',status=IF(status IN ('pending','processing'),'skipped',status),failure_note='الحساب حُذف.' WHERE user_id=?")->execute([$user['id']]);
                db()->prepare('DELETE FROM users WHERE id=?')->execute([$user['id']]);
                db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$user['id'], 'account-deleted-minimal-audit-retained', 'user', (string) $user['id']]);
                $_SESSION = []; session_destroy(); redirect('/');
            }
        }
    }
    if (isset($_GET['export']) && $needsVerification) redirect('/account#account-verification');
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
        echo json_encode(['email' => $user['email'], 'email_marketing_opt_in' => (bool) $user['email_marketing_opt_in'], 'bookmarks' => $bookmarks, 'quran_position' => $position, 'khatmah_plan' => $plan, 'video_progress' => $videoProgress, 'favorites' => $favoriteExport], JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE); exit;
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

if ($path === '/admin/login') {
    if (admin_current_user() !== null) {
        try {
            admin_require_auth();
            redirect('/admin');
        } catch (Throwable $ignored) {
            // Invalid retained Admin state falls through to the isolated login form.
        }
    }

    $adminAuthError = '';
    $locale = admin_locale();

    if ($method === 'POST') {
        require_csrf();
        $email = strtolower(trim((string) ($_POST['email'] ?? '')));
        $password = (string) ($_POST['password'] ?? '');
        $attempts = $_SESSION['admin_auth_attempts'] ?? ['started' => time(), 'count' => 0];
        if (!is_array($attempts) || time() - (int) ($attempts['started'] ?? 0) > 900) {
            $attempts = ['started' => time(), 'count' => 0];
        }
        $attempts['count'] = (int) ($attempts['count'] ?? 0) + 1;
        $_SESSION['admin_auth_attempts'] = $attempts;

        if (
            $attempts['count'] > 8
            || !filter_var($email, FILTER_VALIDATE_EMAIL)
            || strlen($email) > 190
            || strlen($password) < 10
            || strlen($password) > 200
            || !web_identity_rate('admin-login', $email, 8, 900)
        ) {
            $adminAuthError = $locale === 'en'
                ? 'Unable to sign in with these credentials.'
                : 'تعذر تسجيل الدخول بهذه البيانات.';
        } else {
            try {
                $result = web_identity_auth()->login($email, $password, false);
                $candidate = is_array($result['user'] ?? null) ? $result['user'] : [];
                if (!in_array((string) ($candidate['role'] ?? ''), ['admin', 'owner'], true)) {
                    if (!empty($candidate['id'])) {
                        db()->prepare(
                            "INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,'admin-login-denied-role','user',?)"
                        )->execute([(int) $candidate['id'], (string) (int) $candidate['id']]);
                    }
                    throw new RuntimeException('Admin role required.');
                }

                $adminActor = admin_start_authenticated_session((int) $candidate['id']);
                db()->prepare(
                    "INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,'admin-login-succeeded','user',?)"
                )->execute([(int) $adminActor['id'], (string) (int) $adminActor['id']]);
                redirect('/admin');
            } catch (Throwable $exception) {
                $adminAuthError = $locale === 'en'
                    ? 'Unable to sign in with these credentials.'
                    : 'تعذر تسجيل الدخول بهذه البيانات.';
            }
        }
    }

    require dirname(__DIR__) . '/admin/Views/login.php';
    exit;
}

if ($path === '/admin/logout' && $method === 'POST') {
    $adminActor = admin_require_auth();
    require_csrf();
    db()->prepare(
        "INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,'admin-logout','user',?)"
    )->execute([(int) $adminActor['id'], (string) (int) $adminActor['id']]);
    admin_destroy_session();
    redirect('/admin/login');
}

if ($path === '/admin') {
    $admin = admin_require_auth();
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
            admin_authorize_kind($admin, $kind);
            if ($kind === 'comment-review') {
                $commentId = filter_var($_POST['comment_id'] ?? null,FILTER_VALIDATE_INT);
                $revision = filter_var($_POST['expected_revision'] ?? null,FILTER_VALIDATE_INT);
                $status = (string)($_POST['status'] ?? '');
                $reason = trim((string)($_POST['reason'] ?? ''));
                if (!$commentId || $commentId < 1 || $revision === false || $revision < 1 || !in_array($status,['visible','hidden'],true)) throw new RuntimeException('بيانات مراجعة التعليق غير صالحة.');
                try {
                    (new \Athar\Learning\DiscussionService(db()))->moderate($admin,(int)$commentId,[
                        'status'=>$status,
                        'expected_revision'=>(int)$revision,
                        'reason'=>$reason,
                    ]);
                } catch (\Athar\Http\ApiError $moderationError) {
                    if ($moderationError->getMessage()==='conflict') throw new RuntimeException('تم تعديل التعليق من جلسة أخرى. حدّث الصفحة ثم أعد المحاولة.');
                    throw new RuntimeException('تعذر مراجعة التعليق: '.$moderationError->getMessage());
                }
                flash($status==='visible'?'تم نشر التعليق بعد مراجعته.':'تم إخفاء التعليق مع تسجيل السبب.'); redirect('/admin#admin-comments');
            } elseif ($kind === 'comment-delete') {
                $commentId = filter_var($_POST['comment_id'] ?? null,FILTER_VALIDATE_INT);
                $revision = filter_var($_POST['expected_revision'] ?? null,FILTER_VALIDATE_INT);
                $reason = trim((string)($_POST['reason'] ?? ''));
                if (!$commentId || $commentId < 1 || $revision === false || $revision < 1) throw new RuntimeException('بيانات حذف التعليق غير صالحة.');
                try {
                    (new \Athar\Learning\DiscussionService(db()))->deleteModerated($admin,(int)$commentId,[
                        'expected_revision'=>(int)$revision,
                        'reason'=>$reason,
                    ]);
                } catch (\Athar\Http\ApiError $moderationError) {
                    if ($moderationError->getMessage()==='conflict') throw new RuntimeException('تم تعديل التعليق من جلسة أخرى. حدّث الصفحة ثم أعد المحاولة.');
                    throw new RuntimeException('تعذر حذف التعليق: '.$moderationError->getMessage());
                }
                flash('تم حذف التعليق مع الاحتفاظ بسجل قرار المراجعة.'); redirect('/admin#admin-comments');
            } elseif ($kind === 'gmail-settings') {
                $gmailEmail = strtolower(trim((string) ($_POST['gmail_email'] ?? '')));
                $smtpPassword = preg_replace('/\s+/', '', (string) ($_POST['smtp_password'] ?? '')) ?? (string) ($_POST['smtp_password'] ?? '');
                $testRecipient = strtolower(trim((string) ($_POST['test_recipient'] ?? '')));
                $clearTestRecipient = isset($_POST['clear_test_recipient']);
                if (!filter_var($gmailEmail, FILTER_VALIDATE_EMAIL) || strlen($gmailEmail) > 190) throw new RuntimeException('أدخل بريد Gmail صالحًا.');
                if ($smtpPassword === '' && platform_setting('smtp_password_cipher') === '') throw new RuntimeException('أدخل App Password الخاصة بالتطبيق.');
                if ($testRecipient !== '' && (!filter_var($testRecipient, FILTER_VALIDATE_EMAIL) || strlen($testRecipient) > 190)) throw new RuntimeException('بريد الاختبار غير صالح.');
                if ($testRecipient !== '' && !isset($_POST['test_recipient_authorized'])) throw new RuntimeException('أكد أن صاحب بريد الاختبار مصرح له باستلام رسائل التطوير.');
                if ($testRecipient !== '' || $clearTestRecipient) {
                    $passwordCheck = db()->prepare('SELECT password_hash FROM users WHERE id=?');
                    $passwordCheck->execute([$admin['id']]);
                    if (!password_verify((string) ($_POST['owner_password'] ?? ''), (string) $passwordCheck->fetchColumn())) throw new RuntimeException('كلمة مرور المالك غير صحيحة.');
                }

                db()->beginTransaction();
                foreach (['smtp_host'=>'smtp.gmail.com','smtp_port'=>'587','smtp_security'=>'tls','smtp_username'=>$gmailEmail,'smtp_from_email'=>$gmailEmail,'smtp_from_name'=>'أثر'] as $settingKey=>$settingValue) save_platform_setting($settingKey,$settingValue,(int)$admin['id']);
                if ($smtpPassword !== '') save_platform_setting('smtp_password_cipher',seal_platform_secret($smtpPassword),(int)$admin['id']);
                if ($clearTestRecipient) {
                    save_platform_setting('smtp_test_recipient_cipher','',(int)$admin['id']);
                    save_platform_setting('smtp_test_recipient_authorized_at','',(int)$admin['id']);
                } elseif ($testRecipient !== '') {
                    save_platform_setting('smtp_test_recipient_cipher',seal_platform_secret($testRecipient),(int)$admin['id']);
                    save_platform_setting('smtp_test_recipient_authorized_at',gmdate('Y-m-d H:i:s'),(int)$admin['id']);
                }
                db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'],'gmail-smtp-settings-updated','platform',null]);
                db()->commit();

                if (isset($_POST['send_test_email'])) {
                    if (!allow_session_rate('smtp_test_email',1,3600)) throw new RuntimeException('أُرسل اختبار بريد خلال الساعة الماضية. حاول لاحقًا.');
                    $authorizedRecipient = platform_secret('smtp_test_recipient');
                    if ($authorizedRecipient === '' || platform_setting('smtp_test_recipient_authorized_at') === '') throw new RuntimeException('حدد بريد اختبار مصرحًا به واحفظه قبل إرسال اختبار.');
                    $smtpFailure = '';
                    if (!send_platform_email($authorizedRecipient,'اختبار إعدادات Gmail في أثر','هذه رسالة اختبار واحدة من لوحة إدارة أثر للتحقق من إعداد البريد.',$smtpFailure)) throw new RuntimeException('حُفظت الإعدادات، لكن تعذّر إرسال رسالة الاختبار: ' . ($smtpFailure !== '' ? $smtpFailure : 'تحقق من Gmail وApp Password.'));
                    flash('تم إرسال رسالة اختبار واحدة إلى المستلم المصرح به: ' . admin_redact_email($authorizedRecipient));
                } else flash('تم حفظ إعدادات Gmail بأمان.');
                redirect('/admin#admin-services');
            } elseif ($kind === 'dorar-settings') {
                db()->beginTransaction();
                $dorarToken = trim((string) ($_POST['dorar_api_token'] ?? ''));
                if ($dorarToken !== '') save_platform_setting('dorar_api_token_cipher',seal_platform_secret($dorarToken),(int)$admin['id']);
                if (isset($_POST['clear_dorar_token'])) save_platform_setting('dorar_api_token_cipher','',(int)$admin['id']);
                db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'],'dorar-api-settings-updated','platform',null]);
                db()->commit();
                flash('تم حفظ إعدادات بحث الدرر السنية.'); redirect('/admin#admin-services');
            } elseif ($kind === 'campaign-create') {
                $subject = trim((string) ($_POST['subject'] ?? ''));
                $body = trim((string) ($_POST['body'] ?? ''));
                if (mb_strlen($subject, 'UTF-8') < 3 || mb_strlen($subject, 'UTF-8') > 180 || mb_strlen($body, 'UTF-8') < 10 || mb_strlen($body, 'UTF-8') > 4000) throw new RuntimeException('اكتب عنوانًا من 3–180 حرفًا ورسالة من 10–4000 حرف.');
                if (platform_setting('smtp_username') === '' || platform_setting('smtp_password_cipher') === '') throw new RuntimeException('اضبط بريد Gmail وApp Password أولًا من إعدادات الخدمات.');
                $eligible = (int) db()->query("SELECT COUNT(*) FROM users WHERE role='user' AND email_verified_at IS NOT NULL AND email_marketing_opt_in=1")->fetchColumn();
                if ($eligible < 1) throw new RuntimeException('لا يوجد مستخدمون أكدوا بريدهم واشتركوا في رسائل التحديثات بعد.');
                db()->beginTransaction();
                db()->prepare("INSERT INTO email_campaigns (subject,body,status,created_by) VALUES (?,?,'draft',?)")->execute([$subject,$body,$admin['id']]);
                $campaignId = (int) db()->lastInsertId();
                db()->prepare("INSERT INTO email_campaign_recipients (campaign_id,user_id,email) SELECT ?,id,email FROM users WHERE role='user' AND email_verified_at IS NOT NULL AND email_marketing_opt_in=1")->execute([$campaignId]);
                db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'],'email-campaign-created','email-campaign',(string)$campaignId]);
                db()->commit();
                flash('تم تجهيز الحملة لـ' . $eligible . ' مشترك. اضغط «بدء الإرسال» مرة واحدة لإكمال الدفعات تلقائيًا.'); redirect('/admin#admin-campaigns');
            } elseif ($kind === 'campaign-batch') {
                $campaignId = filter_var($_POST['campaign_id'] ?? null, FILTER_VALIDATE_INT);
                if (!$campaignId || $campaignId < 1) throw new RuntimeException('الحملة المحددة غير صالحة.');
                $pdo = db(); $pdo->beginTransaction();
                $campaignQuery = $pdo->prepare('SELECT subject,body,status FROM email_campaigns WHERE id=? FOR UPDATE'); $campaignQuery->execute([$campaignId]); $campaign = $campaignQuery->fetch();
                if (!$campaign || !in_array($campaign['status'], ['draft','sending','paused'], true)) throw new RuntimeException('الحملة غير متاحة للإرسال أو اكتملت بالفعل.');
                $pdo->prepare("UPDATE email_campaigns SET status='sending' WHERE id=?")->execute([$campaignId]);
                $pdo->prepare("UPDATE email_campaign_recipients SET status='pending' WHERE campaign_id=? AND status='processing' AND updated_at < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 15 MINUTE)")->execute([$campaignId]);
                $recipientsQuery = $pdo->prepare("SELECT id,user_id,email FROM email_campaign_recipients WHERE campaign_id=? AND status='pending' ORDER BY id LIMIT 3 FOR UPDATE");
                $recipientsQuery->execute([$campaignId]); $recipients = $recipientsQuery->fetchAll();
                foreach ($recipients as $reservedRecipient) $pdo->prepare("UPDATE email_campaign_recipients SET status='processing' WHERE id=?")->execute([$reservedRecipient['id']]);
                $pdo->commit(); $sentNow = 0; $failedNow = 0; $skippedNow = 0; $quotaPaused = false;
                foreach ($recipients as $recipient) {
                    $recipientUser = null;
                    if ($recipient['user_id'] !== null) { $userQuery = $pdo->prepare('SELECT email,display_name,email_marketing_opt_in,email_verified_at FROM users WHERE id=?'); $userQuery->execute([$recipient['user_id']]); $recipientUser = $userQuery->fetch(); }
                    if (!$recipientUser || !$recipientUser['email_marketing_opt_in'] || !$recipientUser['email_verified_at']) {
                        $pdo->prepare("UPDATE email_campaign_recipients SET status='skipped',failure_note='لم يعد المستخدم مشتركًا أو أن بريده غير مؤكد.' WHERE id=? AND status='processing'")->execute([$recipient['id']]); $skippedNow++; continue;
                    }
                    $sentLastDay = (int) $pdo->query("SELECT COUNT(*) FROM email_send_events WHERE sent_at >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 24 HOUR)")->fetchColumn();
                    if ($sentLastDay >= 450) { $quotaPaused = true; $pdo->prepare("UPDATE email_campaign_recipients SET status='pending' WHERE id=? AND status='processing'")->execute([$recipient['id']]); continue; }
                    $unsubscribeUrl = email_campaign_unsubscribe_url((int) $recipient['user_id']);
                    $displayName = trim((string) ($recipientUser['display_name'] ?? ''));
                    $greeting = $displayName !== '' ? "أهلًا {$displayName}،\n\n" : '';
                    $footerText = "\n\nوصلتك هذه الرسالة لأنك اشتركت في تحديثات أثر. لإلغاء الاشتراك: {$unsubscribeUrl}";
                    $htmlBody = ($displayName !== '' ? '<p style="margin:0 0 12px">أهلًا ' . e($displayName) . '،</p>' : '') . nl2br(e((string) $campaign['body']), false);
                    $htmlFooter = 'وصلتك هذه الرسالة لأنك اشتركت في تحديثات أثر. <a href="' . e($unsubscribeUrl) . '" style="color:#1e6753;font-weight:bold">إلغاء الاشتراك</a>';
                    $failure = '';
                    try { $delivered = send_platform_email((string) $recipientUser['email'], (string) $campaign['subject'], $greeting . (string) $campaign['body'] . $footerText, $failure, $htmlBody, $htmlFooter, 'campaign'); }
                    catch (Throwable $mailError) { $failure = 'تعذر تجهيز رسالة البريد. راجع إعدادات Gmail.'; $delivered = false; }
                    if ($delivered) { $pdo->prepare("UPDATE email_campaign_recipients SET status='sent',sent_at=UTC_TIMESTAMP(),failure_note=NULL WHERE id=? AND status='processing'")->execute([$recipient['id']]); $sentNow++; }
                    else { $pdo->prepare("UPDATE email_campaign_recipients SET status='failed',failure_note=? WHERE id=? AND status='processing'")->execute([mb_substr($failure ?: 'فشل إرسال البريد.',0,255,'UTF-8'),$recipient['id']]); $failedNow++; }
                }
                $remainingQuery = $pdo->prepare("SELECT COUNT(*) FROM email_campaign_recipients WHERE campaign_id=? AND status='pending'"); $remainingQuery->execute([$campaignId]); $remaining = (int) $remainingQuery->fetchColumn();
                $processingQuery = $pdo->prepare("SELECT COUNT(*) FROM email_campaign_recipients WHERE campaign_id=? AND status='processing'"); $processingQuery->execute([$campaignId]); $processing = (int) $processingQuery->fetchColumn();
                if ($remaining === 0 && $processing === 0) $pdo->prepare("UPDATE email_campaigns SET status='completed' WHERE id=?")->execute([$campaignId]);
                elseif ($quotaPaused) $pdo->prepare("UPDATE email_campaigns SET status='paused' WHERE id=?")->execute([$campaignId]);
                $pdo->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'],'email-campaign-batch-sent','email-campaign',(string)$campaignId]);
                if (isset($_POST['ajax_campaign_batch'])) {
                    $campaignStats = $pdo->prepare("SELECT COUNT(*) AS total, SUM(status='sent') AS sent, SUM(status IN ('pending','processing')) AS pending, SUM(status='failed') AS failed, SUM(status='skipped') AS skipped FROM email_campaign_recipients WHERE campaign_id=?");
                    $campaignStats->execute([$campaignId]);
                    header('Content-Type: application/json; charset=utf-8');
                    echo json_encode(['ok'=>true,'campaign_id'=>(int)$campaignId,'sent_now'=>$sentNow,'failed_now'=>$failedNow,'skipped_now'=>$skippedNow,'paused'=>$quotaPaused,'stats'=>$campaignStats->fetch()], JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR); exit;
                }
                flash("نتيجة الدفعة: {$sentNow} أُرسلت، {$failedNow} فشلت، {$skippedNow} تم تخطيها، و{$remaining} متبقية." . ($quotaPaused ? ' توقفت الحملة عند حد الإرسال اليومي.' : '')); redirect('/admin#admin-campaigns');
            } elseif ($kind === 'campaign-retry-failed') {
                $campaignId = filter_var($_POST['campaign_id'] ?? null, FILTER_VALIDATE_INT);
                if (!$campaignId || $campaignId < 1) throw new RuntimeException('الحملة المحددة غير صالحة.');
                db()->beginTransaction();
                $campaignCheck = db()->prepare('SELECT id FROM email_campaigns WHERE id=? FOR UPDATE'); $campaignCheck->execute([$campaignId]);
                if (!$campaignCheck->fetchColumn()) throw new RuntimeException('الحملة غير موجودة.');
                db()->prepare("UPDATE email_campaign_recipients SET status='pending',failure_note=NULL WHERE campaign_id=? AND status='failed'")->execute([$campaignId]);
                db()->prepare("UPDATE email_campaigns SET status='sending' WHERE id=?")->execute([$campaignId]);
                db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'],'email-campaign-failures-requeued','email-campaign',(string)$campaignId]);
                db()->commit(); flash('أُعيدت الرسائل الفاشلة إلى قائمة الإرسال.'); redirect('/admin#admin-campaigns');
            } elseif ($kind === 'user-suspension') {
                $targetId = filter_var($_POST['user_id'] ?? null, FILTER_VALIDATE_INT);
                $nextState = (string) ($_POST['state'] ?? '');
                if (!$targetId || $targetId < 1 || !in_array($nextState, ['suspend','restore'], true)) throw new RuntimeException('اختيار الحساب أو الحالة غير صالح.');
                $passwordCheck = db()->prepare('SELECT password_hash FROM users WHERE id=?'); $passwordCheck->execute([$admin['id']]);
                if (!password_verify((string) ($_POST['password'] ?? ''), (string) $passwordCheck->fetchColumn())) throw new RuntimeException('كلمة مرور المدير الحالية غير صحيحة.');
                $pdo = db();
                $pdo->beginTransaction();
                $targetQuery = $pdo->prepare('SELECT id,role,suspended_at FROM users WHERE id=? FOR UPDATE');
                $targetQuery->execute([$targetId]);
                $target = $targetQuery->fetch();
                if (!$target) throw new RuntimeException('الحساب غير موجود.');
                admin_authorize_user_target($admin, $target, 'suspension');
                if ($nextState === 'suspend') {
                    if ($target['suspended_at'] !== null) { $pdo->rollBack(); flash('الحساب موقوف بالفعل.'); redirect('/admin#admin-users'); }
                    $pdo->prepare('UPDATE users SET suspended_at=UTC_TIMESTAMP(),session_version=session_version+1 WHERE id=?')->execute([$targetId]);
                    $pdo->prepare("UPDATE api_sessions SET revoked_at=COALESCE(revoked_at,UTC_TIMESTAMP()),revoked_reason=COALESCE(revoked_reason,'admin-suspended') WHERE user_id=?")->execute([$targetId]);
                    $action = 'admin-user-suspended';
                    $message = 'تم إيقاف الحساب وإبطال جلساته الحالية.';
                } else {
                    if ($target['suspended_at'] === null) { $pdo->rollBack(); flash('الحساب نشط بالفعل.'); redirect('/admin#admin-users'); }
                    $pdo->prepare('UPDATE users SET suspended_at=NULL,session_version=session_version+1 WHERE id=?')->execute([$targetId]);
                    $pdo->prepare("UPDATE api_sessions SET revoked_at=COALESCE(revoked_at,UTC_TIMESTAMP()),revoked_reason=COALESCE(revoked_reason,'admin-restored') WHERE user_id=?")->execute([$targetId]);
                    $action = 'admin-user-restored';
                    $message = 'تمت إعادة تفعيل الحساب. يجب تسجيل الدخول من جديد.';
                }
                $pdo->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'],$action,'user',(string)$targetId]);
                $pdo->commit();
                flash($message);
                redirect('/admin#admin-users');
            } elseif ($kind === 'user-profile-update') {
                $targetId = filter_var($_POST['user_id'] ?? null, FILTER_VALIDATE_INT);
                $email = strtolower(trim((string) ($_POST['email'] ?? '')));
                $displayName = trim((string) ($_POST['display_name'] ?? ''));
                $username = strtolower(trim((string) ($_POST['username'] ?? '')));
                if (!$targetId || $targetId < 1 || !filter_var($email, FILTER_VALIDATE_EMAIL) || strlen($email) > 190 || mb_strlen($displayName, 'UTF-8') < 2 || mb_strlen($displayName, 'UTF-8') > 80 || !preg_match('/^[a-z0-9_]{3,24}$/', $username) || in_array($username, ['admin','administrator','root','support','athar','api','www','owner'], true)) throw new RuntimeException('راجع البريد والاسم واسم المستخدم (3–24 حرفًا إنجليزيًا أو رقمًا أو _).');
                $passwordCheck = db()->prepare('SELECT password_hash FROM users WHERE id=?'); $passwordCheck->execute([$admin['id']]);
                if (!password_verify((string) ($_POST['password'] ?? ''), (string) $passwordCheck->fetchColumn())) throw new RuntimeException('كلمة مرور المدير الحالية غير صحيحة.');
                $targetQuery = db()->prepare('SELECT id,role,email FROM users WHERE id=?'); $targetQuery->execute([$targetId]); $target = $targetQuery->fetch();
                if (!$target) throw new RuntimeException('الحساب غير موجود.');
                admin_authorize_user_target($admin, $target, 'profile');
                $duplicateEmail = db()->prepare('SELECT 1 FROM users WHERE email=? AND id<>? LIMIT 1'); $duplicateEmail->execute([$email, $targetId]);
                if ($duplicateEmail->fetchColumn()) throw new RuntimeException('البريد الإلكتروني مستخدم بالفعل.');
                $duplicate = db()->prepare('SELECT 1 FROM users WHERE username=? AND id<>? LIMIT 1'); $duplicate->execute([$username, $targetId]);
                if ($duplicate->fetchColumn()) throw new RuntimeException('اسم المستخدم مستخدم بالفعل. اختر اسمًا آخر.');
                $emailChanged = !hash_equals((string) $target['email'], $email);
                if ($emailChanged) {
                    db()->prepare("UPDATE email_campaign_recipients SET user_id=NULL,email='',status=IF(status IN ('pending','processing'),'skipped',status),failure_note='تم تغيير البريد.' WHERE user_id=?")->execute([$targetId]);
                    db()->prepare('UPDATE users SET email=?,display_name=?,username=?,email_marketing_opt_in=0,email_verified_at=NULL,session_version=session_version+1 WHERE id=?')->execute([$email,$displayName,$username,$targetId]);
                }
                else db()->prepare('UPDATE users SET display_name=?,username=? WHERE id=?')->execute([$displayName,$username,$targetId]);
                db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'], $emailChanged ? 'admin-user-email-updated' : 'admin-user-profile-updated', 'user', (string) $targetId]);
                flash('تم تحديث بيانات المستخدم.'); redirect('/admin#admin-users');
            } elseif ($kind === 'user-delete') {
                $targetId = filter_var($_POST['user_id'] ?? null, FILTER_VALIDATE_INT);
                if (!$targetId || $targetId < 1 || $targetId === (int) $admin['id']) throw new RuntimeException('اختيار المستخدم غير صالح؛ لا يمكنك حذف حسابك الحالي.');
                $passwordCheck = db()->prepare('SELECT password_hash FROM users WHERE id=?'); $passwordCheck->execute([$admin['id']]);
                if (!password_verify((string) ($_POST['password'] ?? ''), (string) $passwordCheck->fetchColumn())) throw new RuntimeException('كلمة مرور المدير الحالية غير صحيحة.');
                $pdo = db(); $pdo->beginTransaction();
                $targetQuery = $pdo->prepare('SELECT id,email,role FROM users WHERE id=? FOR UPDATE'); $targetQuery->execute([$targetId]); $target = $targetQuery->fetch();
                if (!$target) throw new RuntimeException('الحساب غير موجود.');
                admin_authorize_user_target($admin, $target, 'delete');
                if (!hash_equals((string) $target['email'], trim((string) ($_POST['email_confirmation'] ?? '')))) throw new RuntimeException('تأكيد البريد لا يطابق الحساب المحدد.');
                $pdo->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'], 'admin-user-deleted', 'user', (string) $targetId]);
                $pdo->prepare("UPDATE email_campaign_recipients SET user_id=NULL,email='',status=IF(status IN ('pending','processing'),'skipped',status),failure_note='الحساب حُذف.' WHERE user_id=?")->execute([$targetId]);
                $pdo->prepare('DELETE FROM users WHERE id=?')->execute([$targetId]);
                $pdo->commit();
                flash('تم حذف الحساب وبياناته المرتبطة. بقي سجل التدقيق دون بيانات تسجيل الدخول.'); redirect('/admin#admin-users');
            } elseif ($kind === 'user-role') {
                if (($admin['role'] ?? '') !== 'owner') throw new RuntimeException('إدارة صلاحيات المستخدمين متاحة لمالك المنصة فقط.');
                $targetId = filter_var($_POST['user_id'] ?? null, FILTER_VALIDATE_INT); $nextRole = (string) ($_POST['role'] ?? '');
                if (!$targetId || $targetId < 1 || !in_array($nextRole, ['user','admin'], true)) throw new RuntimeException('اختيار المستخدم أو الصلاحية غير صالح.');
                $passwordCheck = db()->prepare('SELECT password_hash FROM users WHERE id=?'); $passwordCheck->execute([$admin['id']]);
                if (!password_verify((string) ($_POST['password'] ?? ''), (string) $passwordCheck->fetchColumn())) throw new RuntimeException('كلمة مرور المالك غير صحيحة.');
                $targetQuery = db()->prepare('SELECT id,role FROM users WHERE id=?'); $targetQuery->execute([$targetId]); $target = $targetQuery->fetch();
                if (!$target) throw new RuntimeException('الحساب غير موجود.');
                admin_authorize_user_target($admin, $target, 'role');
                $currentRole = (string) $target['role'];
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
                session_regenerate_id(true);
                $fresh = db()->prepare('SELECT id,email,display_name,username,role,session_version,email_verified_at,suspended_at FROM users WHERE id=?');
                $fresh->execute([$admin['id']]);
                $_SESSION['admin_user'] = $fresh->fetch();
                $_SESSION['admin_authenticated_at'] = time();
                $_SESSION['admin_last_activity'] = time();
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
                $fresh = db()->prepare('SELECT id,email,display_name,username,role,session_version,email_verified_at,suspended_at FROM users WHERE id=?');
                $fresh->execute([$admin['id']]);
                $_SESSION['admin_user'] = $fresh->fetch();
                $_SESSION['admin_authenticated_at'] = time();
                $_SESSION['admin_last_activity'] = time();
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
            } elseif ($kind === 'lesson-event') {
                $lessonId = filter_var($_POST['lesson_id'] ?? null, FILTER_VALIDATE_INT);
                $expectedRevision = filter_var($_POST['expected_revision'] ?? null, FILTER_VALIDATE_INT);
                if (!$lessonId || $lessonId < 1 || $expectedRevision === false || $expectedRevision < 0) throw new RuntimeException('الدرس أو نسخة الحدث غير صالحة.');
                $timezone = trim((string) ($_POST['schedule_timezone'] ?? 'UTC'));
                $scheduledLocal = trim((string) ($_POST['scheduled_local'] ?? ''));
                $scheduledAt = null;
                if ($scheduledLocal !== '') {
                    try {
                        $localTime = new DateTimeImmutable($scheduledLocal, new DateTimeZone($timezone));
                        $scheduledAt = $localTime->format('Y-m-d\TH:i:sP');
                    } catch (Throwable $dateError) {
                        throw new RuntimeException('موعد الدرس أو المنطقة الزمنية غير صالح.');
                    }
                }
                $durationRaw = trim((string) ($_POST['estimated_duration_minutes'] ?? ''));
                $duration = $durationRaw === '' ? null : filter_var($durationRaw, FILTER_VALIDATE_INT);
                if ($durationRaw !== '' && ($duration === false || $duration < 1 || $duration > 1440)) throw new RuntimeException('مدة الدرس غير صالحة.');
                $bookRaw = trim((string) ($_POST['related_book_id'] ?? ''));
                $bookId = $bookRaw === '' ? null : filter_var($bookRaw, FILTER_VALIDATE_INT);
                $pageStartRaw = trim((string) ($_POST['related_page_start'] ?? ''));
                $pageEndRaw = trim((string) ($_POST['related_page_end'] ?? ''));
                $pageStart = $pageStartRaw === '' ? null : filter_var($pageStartRaw, FILTER_VALIDATE_INT);
                $pageEnd = $pageEndRaw === '' ? null : filter_var($pageEndRaw, FILTER_VALIDATE_INT);
                if (($bookRaw !== '' && (!$bookId || $bookId < 1)) || ($pageStartRaw !== '' && (!$pageStart || $pageStart < 1)) || ($pageEndRaw !== '' && (!$pageEnd || $pageEnd < 1))) throw new RuntimeException('مرجع الكتاب أو الصفحات غير صالح.');
                $topics = static function (string $value): array {
                    $items = preg_split('/[\r\n,]+/u', $value) ?: [];
                    $items = array_values(array_filter(array_map('trim', $items), static fn(string $item): bool => $item !== ''));
                    return array_slice($items, 0, 12);
                };
                $input = [
                    'expected_revision' => (int) $expectedRevision,
                    'status' => (string) ($_POST['status'] ?? 'draft'),
                    'scheduled_at' => $scheduledAt,
                    'schedule_timezone' => $timezone,
                    'estimated_duration_minutes' => $duration,
                    'is_live_event' => isset($_POST['is_live_event']),
                    'reminders_enabled' => isset($_POST['reminders_enabled']),
                    'replay_available' => isset($_POST['replay_available']),
                    'visibility' => (string) ($_POST['visibility'] ?? 'public'),
                    'title_ar' => trim((string) ($_POST['title_ar'] ?? '')),
                    'description_ar' => trim((string) ($_POST['description_ar'] ?? '')) ?: null,
                    'course_title_ar' => trim((string) ($_POST['course_title_ar'] ?? '')) ?: null,
                    'course_title_en' => trim((string) ($_POST['course_title_en'] ?? '')) ?: null,
                    'topics_ar' => $topics((string) ($_POST['topics_ar'] ?? '')),
                    'topics_en' => $topics((string) ($_POST['topics_en'] ?? '')),
                    'related_book_id' => $bookId ?: null,
                    'related_page_start' => $pageStart ?: null,
                    'related_page_end' => $pageEnd ?: null,
                ];
                $englishTitle = trim((string) ($_POST['title_en'] ?? ''));
                $englishDescription = trim((string) ($_POST['description_en'] ?? ''));
                if ($englishTitle !== '') {
                    $input['title_en'] = $englishTitle;
                    $input['description_en'] = $englishDescription !== '' ? $englishDescription : null;
                } elseif (isset($_POST['clear_english_translation'])) {
                    $input['title_en'] = null;
                }
                try {
                    (new \Athar\Learning\UpcomingLessonService(db()))->save($admin, (int) $lessonId, $input);
                } catch (\Athar\Http\ApiError $eventError) {
                    if ($eventError->getMessage() === 'conflict') throw new RuntimeException('تم تعديل الحدث من جلسة أخرى. حدّث الصفحة ثم أعد المحاولة.');
                    throw new RuntimeException('تعذر حفظ الحدث: ' . $eventError->getMessage());
                }
                flash('تم حفظ حالة الدرس القادم/المباشر وتسجيل التغيير.');
                redirect('/admin#admin-events');
            } elseif ($kind === 'content') {
                $table = ['scholar' => 'scholars', 'series' => 'series', 'lesson' => 'lessons', 'book' => 'books', 'adhkar' => 'adhkar_items'][(string) ($_POST['content_type'] ?? '')] ?? '';
                $id = filter_var($_POST['id'] ?? null, FILTER_VALIDATE_INT);
                if ($table === '' || !$id || $id < 1) throw new RuntimeException('عنصر المحتوى غير صالح.');
                $type = (string) $_POST['content_type'];
                if (($_POST['content_action'] ?? '') === 'update') {
                    $published = isset($_POST['publish']) ? 1 : 0;
                    if ($type === 'scholar') {
                        $name = trim((string) ($_POST['name'] ?? ''));
                        $biography = trim((string) ($_POST['biography'] ?? ''));
                        $language = (string) ($_POST['language'] ?? 'ar');
                        $source = trim((string) ($_POST['source_url'] ?? ''));
                        $rights = trim((string) ($_POST['rights_basis'] ?? ''));
                        $sortOrder = max(-100000, min(100000, (int) ($_POST['sort_order'] ?? 0)));
                        if (mb_strlen($name,'UTF-8') < 2 || mb_strlen($name,'UTF-8') > 180) throw new RuntimeException('اسم العالم يجب أن يكون من 2 إلى 180 حرفًا.');
                        if (mb_strlen($biography,'UTF-8') > 6000 || mb_strlen($rights,'UTF-8') > 500) throw new RuntimeException('السيرة أو بيانات الحقوق أطول من الحد المسموح.');
                        if (!in_array($language,['ar','en','other'],true) || ($source !== '' && !is_https_url($source))) throw new RuntimeException('لغة العالم أو رابط المصدر غير صالح.');
                        $current = db()->prepare('SELECT image_key FROM scholars WHERE id=?'); $current->execute([$id]); $previousImage = $current->fetchColumn();
                        if ($previousImage === false) throw new RuntimeException('العالم غير موجود.');
                        $nextImage = $previousImage ?: null; $imageStorage = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'scholars';
                        if (isset($_FILES['scholar_image']) && ($_FILES['scholar_image']['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_NO_FILE) $nextImage = store_scholar_image($_FILES['scholar_image'], $imageStorage);
                        try {
                            db()->prepare('UPDATE scholars SET name=?,biography=NULLIF(?,\'\'),language=?,source_url=NULLIF(?,\'\'),rights_basis=NULLIF(?,\'\'),image_key=?,is_published=?,sort_order=? WHERE id=?')->execute([$name,$biography,$language,$source,$rights,$nextImage,$published,$sortOrder,$id]);
                        } catch (Throwable $saveFailure) {
                            if ($nextImage !== $previousImage) delete_scholar_image(is_string($nextImage) ? $nextImage : null, $imageStorage);
                            throw $saveFailure;
                        }
                        if ($nextImage !== $previousImage) delete_scholar_image(is_string($previousImage) ? $previousImage : null, $imageStorage);
                    } elseif ($type === 'lesson') {
                        $url = trim((string) ($_POST['source_url'] ?? '')); $videoId = youtube_video_id($url);
                        if (!$videoId) throw new RuntimeException('رابط فيديو YouTube غير صالح.');
                        [$lessonScholarId,$lessonSeriesId] = validated_lesson_scope((int)($_POST['scholar_id'] ?? 0),(int)($_POST['series_id'] ?? 0));
                        db()->prepare('UPDATE lessons SET scholar_id=NULLIF(?,0),series_id=NULLIF(?,0),title=?,description=?,youtube_id=?,source_url=?,is_published=?,sort_order=? WHERE id=?')->execute([$lessonScholarId, $lessonSeriesId, trim((string) $_POST['title']), trim((string) $_POST['description']), $videoId, $url, $published, (int) ($_POST['sort_order'] ?? 0), $id]);
                    } elseif ($type === 'series') {
                        $source = trim((string) ($_POST['source_url'] ?? ''));
                        $title = trim((string) ($_POST['title'] ?? ''));
                        if (mb_strlen($title,'UTF-8') < 2 || mb_strlen($title,'UTF-8') > 220 || ($source !== '' && !is_https_url($source))) throw new RuntimeException('أدخل عنوانًا صالحًا ورابط مصدر HTTPS عند الحاجة.');
                        [$seriesScholarId] = validated_lesson_scope((int) ($_POST['scholar_id'] ?? 0), 0);
                        db()->prepare('UPDATE series SET scholar_id=NULLIF(?,0),title=?,description=?,source_url=?,is_published=?,sort_order=? WHERE id=?')->execute([$seriesScholarId, $title, trim((string) $_POST['description']), $source ?: null, $published, (int) ($_POST['sort_order'] ?? 0), $id]);
                    } elseif ($type === 'book') {
                        $title = trim((string) ($_POST['title'] ?? ''));
                        $author = trim((string) ($_POST['author'] ?? ''));
                        $description = trim((string) ($_POST['description'] ?? ''));
                        $keywords = trim((string) ($_POST['keywords'] ?? ''));
                        $source = trim((string) ($_POST['source_url'] ?? ''));
                        $rights = trim((string) ($_POST['rights_basis'] ?? ''));
                        $rightsHolder = trim((string) ($_POST['rights_holder'] ?? ''));
                        $downloadUrl = trim((string) ($_POST['download_url'] ?? ''));
                        if (mb_strlen($title,'UTF-8') < 2 || mb_strlen($title,'UTF-8') > 240 || mb_strlen($author,'UTF-8') < 2 || mb_strlen($author,'UTF-8') > 180) throw new RuntimeException('عنوان الكتاب أو اسم المؤلف غير صالح.');
                        if (mb_strlen($description,'UTF-8') > 12000 || mb_strlen($keywords,'UTF-8') > 1000 || mb_strlen($rights,'UTF-8') > 500 || mb_strlen($rightsHolder,'UTF-8') > 180) throw new RuntimeException('بيانات الكتاب أطول من الحد المسموح.');
                        if (!is_https_url($source) || $rights === '') throw new RuntimeException('أدخل مصدرًا آمنًا وأساس الحقوق.');
                        if ($downloadUrl !== '' && !is_https_url($downloadUrl)) throw new RuntimeException('رابط التنزيل المباشر يجب أن يبدأ بـ HTTPS عند توفيره.');
                        $language = (string) ($_POST['language'] ?? 'ar'); $yearRaw = trim((string) ($_POST['publication_year'] ?? '')); $year = $yearRaw === '' ? null : filter_var($yearRaw, FILTER_VALIDATE_INT);
                        if (!in_array($language, ['ar','en','other'], true) || ($yearRaw !== '' && ($year === false || $year < 1000 || $year > 2200))) throw new RuntimeException('لغة الكتاب أو سنة النشر غير صالحة.');
                        $legacyFileQuery = db()->prepare('SELECT file_key FROM books WHERE id=?'); $legacyFileQuery->execute([$id]); $legacyFileKey = $legacyFileQuery->fetchColumn();
                        db()->prepare('UPDATE books SET title=?,author=?,description=?,keywords=?,language=?,publication_year=?,rights_holder=NULLIF(?,\'\'),source_url=?,download_url=NULLIF(?,\'\'),rights_basis=?,file_key=NULL,file_size=NULL,can_download=?,is_published=? WHERE id=?')->execute([$title,$author,$description,$keywords ?: null,$language,$year,$rightsHolder,$source,$downloadUrl,$rights,$downloadUrl !== '' ? 1 : 0,$published,$id]);
                        if (is_string($legacyFileKey) && preg_match('/^[a-f0-9]{40}\.pdf$/', $legacyFileKey)) { $legacyPath = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'books' . DIRECTORY_SEPARATOR . $legacyFileKey; if (is_file($legacyPath)) unlink($legacyPath); }
                    } elseif ($type === 'adhkar') {
                        $source = trim((string) ($_POST['source_url'] ?? ''));
                        $title = trim((string) ($_POST['title'] ?? ''));
                        $formula = trim((string) ($_POST['formula'] ?? ''));
                        $reference = trim((string) ($_POST['source_reference'] ?? ''));
                        $category = trim((string) ($_POST['category'] ?? ''));
                        $expectedRevision = filter_var($_POST['expected_revision'] ?? null, FILTER_VALIDATE_INT);
                        if ($expectedRevision === false || $expectedRevision < 1) throw new RuntimeException('نسخة الذكر غير صالحة؛ حدّث الصفحة ثم أعد المحاولة.');
                        if (mb_strlen($title,'UTF-8') < 2 || mb_strlen($title,'UTF-8') > 200 || mb_strlen($formula,'UTF-8') < 2 || mb_strlen($formula,'UTF-8') > 5000 || mb_strlen($reference,'UTF-8') < 2 || mb_strlen($reference,'UTF-8') > 300 || mb_strlen($category,'UTF-8') < 2 || mb_strlen($category,'UTF-8') > 80) throw new RuntimeException('راجع عنوان الذكر ونصه ومرجعه وتصنيفه.');
                        if (!is_https_url($source) || !isset($_POST['reviewed'])) throw new RuntimeException('أدخل مصدرًا آمنًا وأكد المراجعة.');
                        $countRaw = trim((string) ($_POST['repeat_count'] ?? ''));
                        $count = $countRaw === '' ? null : filter_var($countRaw, FILTER_VALIDATE_INT);
                        if ($countRaw !== '' && ($count === false || $count < 1 || $count > 1000)) throw new RuntimeException('عدد التكرار يجب أن يكون من 1 إلى 1000 أو يترك فارغًا.');
                        $pdo = db();
                        $pdo->beginTransaction();
                        $currentQuery = $pdo->prepare('SELECT * FROM adhkar_items WHERE id=? FOR UPDATE');
                        $currentQuery->execute([$id]);
                        $current = $currentQuery->fetch();
                        if (!$current) throw new RuntimeException('الذكر غير موجود.');
                        if ((int) $current['revision'] !== (int) $expectedRevision) throw new RuntimeException('تم تعديل الذكر من جلسة أخرى. حدّث الصفحة ثم أعد المحاولة.');
                        $nextRevision = (int) $current['revision'] + 1;
                        $pdo->prepare('UPDATE adhkar_items SET title=?,formula=?,source_reference=?,source_url=?,repeat_count=?,category=?,is_reviewed=1,is_published=?,sort_order=?,revision=? WHERE id=?')
                            ->execute([$title,$formula,$reference,$source,$count,$category,$published,(int) ($_POST['sort_order'] ?? 0),$nextRevision,$id]);
                        $freshQuery = $pdo->prepare('SELECT * FROM adhkar_items WHERE id=?');
                        $freshQuery->execute([$id]);
                        admin_record_adhkar_revision($pdo, $freshQuery->fetch(), 'updated', (int) $admin['id']);
                        $pdo->commit();
                    }
                    $auditAction = 'update';
                } elseif (($_POST['content_action'] ?? '') === 'toggle') {
                    if ($type === 'adhkar') {
                        $expectedRevision = filter_var($_POST['expected_revision'] ?? null, FILTER_VALIDATE_INT);
                        if ($expectedRevision === false || $expectedRevision < 1) throw new RuntimeException('نسخة الذكر غير صالحة؛ حدّث الصفحة ثم أعد المحاولة.');
                        $pdo = db();
                        $pdo->beginTransaction();
                        $currentQuery = $pdo->prepare('SELECT * FROM adhkar_items WHERE id=? FOR UPDATE');
                        $currentQuery->execute([$id]);
                        $current = $currentQuery->fetch();
                        if (!$current) throw new RuntimeException('الذكر غير موجود.');
                        if ((int) $current['revision'] !== (int) $expectedRevision) throw new RuntimeException('تم تعديل الذكر من جلسة أخرى. حدّث الصفحة ثم أعد المحاولة.');
                        $nextRevision = (int) $current['revision'] + 1;
                        $pdo->prepare('UPDATE adhkar_items SET is_published=1-is_published,revision=? WHERE id=?')->execute([$nextRevision,$id]);
                        $freshQuery = $pdo->prepare('SELECT * FROM adhkar_items WHERE id=?');
                        $freshQuery->execute([$id]);
                        admin_record_adhkar_revision($pdo, $freshQuery->fetch(), 'publication_changed', (int) $admin['id']);
                        $pdo->commit();
                    } else {
                        db()->prepare("UPDATE {$table} SET is_published=1-is_published WHERE id=?")->execute([$id]);
                    }
                    $auditAction = 'toggle-publication';
                } elseif (($_POST['content_action'] ?? '') === 'delete') {
                    if ($type === 'series') {
                        $dependency = db()->prepare('SELECT COUNT(*) FROM lessons WHERE series_id=?');
                        $dependency->execute([$id]);
                        if ((int) $dependency->fetchColumn() > 0) throw new RuntimeException('لا يمكن حذف السلسلة قبل نقل أو حذف دروسها المرتبطة.');
                    }
                    if ($type === 'scholar') {
                        $seriesDependency = db()->prepare('SELECT COUNT(*) FROM series WHERE scholar_id=?');
                        $seriesDependency->execute([$id]);
                        $lessonDependency = db()->prepare('SELECT COUNT(*) FROM lessons WHERE scholar_id=?');
                        $lessonDependency->execute([$id]);
                        if ((int) $seriesDependency->fetchColumn() > 0 || (int) $lessonDependency->fetchColumn() > 0) throw new RuntimeException('لا يمكن حذف العالم قبل نقل السلاسل والدروس المرتبطة به.');
                    }
                    if ($type === 'adhkar') {
                        $expectedRevision = filter_var($_POST['expected_revision'] ?? null, FILTER_VALIDATE_INT);
                        if ($expectedRevision === false || $expectedRevision < 1) throw new RuntimeException('نسخة الذكر غير صالحة؛ حدّث الصفحة ثم أعد المحاولة.');
                        $pdo = db();
                        $pdo->beginTransaction();
                        $currentQuery = $pdo->prepare('SELECT * FROM adhkar_items WHERE id=? FOR UPDATE');
                        $currentQuery->execute([$id]);
                        $current = $currentQuery->fetch();
                        if (!$current) throw new RuntimeException('الذكر غير موجود.');
                        if ((int) $current['revision'] !== (int) $expectedRevision) throw new RuntimeException('تم تعديل الذكر من جلسة أخرى. حدّث الصفحة ثم أعد المحاولة.');
                        $current['revision'] = (int) $current['revision'] + 1;
                        admin_record_adhkar_revision($pdo, $current, 'deleted', (int) $admin['id']);
                        $pdo->prepare('DELETE FROM adhkar_items WHERE id=?')->execute([$id]);
                        $pdo->commit();
                        $auditAction = 'delete';
                        db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'], $auditAction, $type, (string) $id]);
                        flash('تم تحديث المحتوى.'); redirect('/admin#admin-content');
                    }
                    $fileKey = null;
                    if (in_array($type, ['book','lesson'], true)) db()->prepare('DELETE FROM user_favorites WHERE target_type=? AND target_id=?')->execute([$type, $id]);
                    if ($table === 'books') {
                        $fileQuery = db()->prepare('SELECT file_key FROM books WHERE id=?'); $fileQuery->execute([$id]); $fileKey = $fileQuery->fetchColumn();
                    }
                    $scholarImageKey = null;
                    if ($table === 'scholars') { $imageQuery = db()->prepare('SELECT image_key FROM scholars WHERE id=?'); $imageQuery->execute([$id]); $scholarImageKey = $imageQuery->fetchColumn(); }
                    db()->prepare("DELETE FROM {$table} WHERE id=?")->execute([$id]);
                    if ($table === 'scholars') delete_scholar_image(is_string($scholarImageKey) ? $scholarImageKey : null, dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'scholars');
                    if (is_string($fileKey) && preg_match('/^[a-f0-9]{40}\.pdf$/', $fileKey)) {
                        $storedFile = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'books' . DIRECTORY_SEPARATOR . $fileKey;
                        if (is_file($storedFile)) unlink($storedFile);
                    }
                    $auditAction = 'delete';
                } else throw new RuntimeException('الإجراء غير معروف.');
                db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'], $auditAction, $type, (string) $id]);
                flash('تم تحديث المحتوى.'); redirect('/admin');
            } elseif ($kind === 'scholar') {
                $name = trim((string) ($_POST['name'] ?? ''));
                $biography = trim((string) ($_POST['biography'] ?? ''));
                $language = (string) ($_POST['language'] ?? 'ar');
                $source = trim((string) ($_POST['source_url'] ?? ''));
                $rights = trim((string) ($_POST['rights_basis'] ?? ''));
                $sortOrder = max(-100000, min(100000, (int) ($_POST['sort_order'] ?? 0)));
                if (mb_strlen($name,'UTF-8') < 2 || mb_strlen($name,'UTF-8') > 180) throw new RuntimeException('اسم العالم يجب أن يكون من 2 إلى 180 حرفًا.');
                if (mb_strlen($biography,'UTF-8') > 6000 || mb_strlen($rights,'UTF-8') > 500) throw new RuntimeException('السيرة أو بيانات الحقوق أطول من الحد المسموح.');
                if (!in_array($language,['ar','en','other'],true) || ($source !== '' && !is_https_url($source))) throw new RuntimeException('لغة العالم أو رابط المصدر غير صالح.');
                $imageStorage = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'scholars';
                $imageKey = null;
                if (isset($_FILES['scholar_image']) && ($_FILES['scholar_image']['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_NO_FILE) $imageKey = store_scholar_image($_FILES['scholar_image'], $imageStorage);
                try {
                    db()->prepare('INSERT INTO scholars (name,biography,language,source_url,rights_basis,image_key,is_published,sort_order) VALUES (?,NULLIF(?,\'\'),?,NULLIF(?,\'\'),NULLIF(?,\'\'),?,?,?)')->execute([$name,$biography,$language,$source,$rights,$imageKey,isset($_POST['publish']) ? 1 : 0,$sortOrder]);
                } catch (Throwable $saveFailure) {
                    if (is_string($imageKey)) delete_scholar_image($imageKey, $imageStorage);
                    throw $saveFailure;
                }
            } elseif ($kind === 'series') {
                $source = trim((string) ($_POST['source_url'] ?? ''));
                $title = trim((string) ($_POST['title'] ?? ''));
                if (mb_strlen($title,'UTF-8') < 2 || mb_strlen($title,'UTF-8') > 220 || ($source !== '' && !is_https_url($source))) throw new RuntimeException('أدخل عنوانًا صالحًا ورابط مصدر HTTPS عند الحاجة.');
                [$seriesScholarId] = validated_lesson_scope((int) ($_POST['scholar_id'] ?? 0), 0);
                db()->prepare('INSERT INTO series (scholar_id,title,description,source_url,is_published,sort_order) VALUES (NULLIF(?,0),?,?,?,?,?)')->execute([$seriesScholarId, $title, trim((string) $_POST['description']), $source ?: null, isset($_POST['publish']) ? 1 : 0, (int) ($_POST['sort_order'] ?? 0)]);
            } elseif ($kind === 'lesson') {
                $url = trim((string) $_POST['source_url']); $id = youtube_video_id($url);
                if (!$id || strlen(trim((string) $_POST['title'])) < 2) throw new RuntimeException('رابط الفيديو أو العنوان غير صالح.');
                [$lessonScholarId,$lessonSeriesId] = validated_lesson_scope((int)($_POST['scholar_id'] ?? 0),(int)($_POST['series_id'] ?? 0));
                db()->prepare('INSERT INTO lessons (scholar_id,series_id,title,description,youtube_id,source_url,sort_order,is_published) VALUES (NULLIF(?,0),NULLIF(?,0),?,?,?,?,?,?)')->execute([$lessonScholarId, $lessonSeriesId, trim((string) $_POST['title']), trim((string) $_POST['description']), $id, $url, (int) ($_POST['sort_order'] ?? 0), isset($_POST['publish']) ? 1 : 0]);
            } elseif ($kind === 'book') {
                $title = trim((string) ($_POST['title'] ?? ''));
                $author = trim((string) ($_POST['author'] ?? ''));
                $description = trim((string) ($_POST['description'] ?? ''));
                $keywords = trim((string) ($_POST['keywords'] ?? ''));
                $source = trim((string) ($_POST['source_url'] ?? ''));
                $rights = trim((string) ($_POST['rights_basis'] ?? ''));
                $rightsHolder = trim((string) ($_POST['rights_holder'] ?? ''));
                $downloadUrl = trim((string) ($_POST['download_url'] ?? ''));
                if (mb_strlen($title,'UTF-8') < 2 || mb_strlen($title,'UTF-8') > 240 || mb_strlen($author,'UTF-8') < 2 || mb_strlen($author,'UTF-8') > 180) throw new RuntimeException('عنوان الكتاب أو اسم المؤلف غير صالح.');
                if (mb_strlen($description,'UTF-8') > 12000 || mb_strlen($keywords,'UTF-8') > 1000 || mb_strlen($rights,'UTF-8') > 500 || mb_strlen($rightsHolder,'UTF-8') > 180) throw new RuntimeException('بيانات الكتاب أطول من الحد المسموح.');
                if ($rights === '' || !is_https_url($source)) throw new RuntimeException('يلزم توثيق مصدر آمن وأساس حقوق الإتاحة.');
                if ($downloadUrl !== '' && !is_https_url($downloadUrl)) throw new RuntimeException('رابط التنزيل المباشر يجب أن يبدأ بـ HTTPS عند توفيره.');
                $language = (string) ($_POST['language'] ?? 'ar'); $yearRaw = trim((string) ($_POST['publication_year'] ?? '')); $year = $yearRaw === '' ? null : filter_var($yearRaw, FILTER_VALIDATE_INT);
                if (!in_array($language, ['ar','en','other'], true) || ($yearRaw !== '' && ($year === false || $year < 1000 || $year > 2200))) throw new RuntimeException('لغة الكتاب أو سنة النشر غير صالحة.');
                db()->prepare('INSERT INTO books (title,author,description,keywords,language,publication_year,rights_holder,source_url,download_url,rights_basis,file_key,file_size,can_download,is_published) VALUES (?,?,?,?,?,?,?,?,NULLIF(?,\'\'),?,NULL,NULL,?,?)')->execute([$title,$author,$description,$keywords ?: null,$language,$year,$rightsHolder ?: null,$source,$downloadUrl,$rights,$downloadUrl !== '' ? 1 : 0,isset($_POST['publish']) ? 1 : 0]);
            } elseif ($kind === 'adhkar') {
                $title = trim((string) ($_POST['title'] ?? ''));
                $formula = trim((string) ($_POST['formula'] ?? ''));
                $reference = trim((string) ($_POST['source_reference'] ?? ''));
                $source = trim((string) ($_POST['source_url'] ?? ''));
                $category = trim((string) ($_POST['category'] ?? ''));
                $countRaw = trim((string) ($_POST['repeat_count'] ?? ''));
                $count = $countRaw === '' ? null : filter_var($countRaw, FILTER_VALIDATE_INT);
                if (mb_strlen($title,'UTF-8') < 2 || mb_strlen($title,'UTF-8') > 200 || mb_strlen($formula,'UTF-8') < 2 || mb_strlen($formula,'UTF-8') > 5000 || mb_strlen($reference,'UTF-8') < 2 || mb_strlen($reference,'UTF-8') > 300 || mb_strlen($category,'UTF-8') < 2 || mb_strlen($category,'UTF-8') > 80) throw new RuntimeException('راجع عنوان الذكر ونصه ومرجعه وتصنيفه.');
                if (!is_https_url($source) || !isset($_POST['reviewed'])) throw new RuntimeException('لا يمكن حفظ الذكر دون مصدر آمن ومراجعة موثقة.');
                if ($countRaw !== '' && ($count === false || $count < 1 || $count > 1000)) throw new RuntimeException('عدد التكرار يجب أن يكون من 1 إلى 1000 أو يترك فارغًا.');
                $pdo = db();
                $pdo->beginTransaction();
                $pdo->prepare('INSERT INTO adhkar_items (title,formula,source_reference,source_url,repeat_count,category,is_reviewed,is_published,sort_order,revision) VALUES (?,?,?,?,?,?,?,?,?,1)')
                    ->execute([$title,$formula,$reference,$source,$count,$category,1,isset($_POST['publish']) ? 1 : 0,(int) ($_POST['sort_order'] ?? 0)]);
                $createdId = (int) $pdo->lastInsertId();
                $createdQuery = $pdo->prepare('SELECT * FROM adhkar_items WHERE id=?');
                $createdQuery->execute([$createdId]);
                admin_record_adhkar_revision($pdo, $createdQuery->fetch(), 'created', (int) $admin['id']);
                $pdo->commit();
            } else { throw new RuntimeException('نوع المحتوى غير مدعوم.'); }
            $createdTargetId = isset($createdId) && $createdId > 0 ? $createdId : (int) db()->lastInsertId();
            db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$admin['id'], 'create', $kind, (string) $createdTargetId]);
            flash('تم حفظ المحتوى.'); redirect('/admin');
        } catch (Throwable $exception) {
            if (db()->inTransaction()) db()->rollBack();
            $adminError = $exception instanceof RuntimeException ? $exception->getMessage() : 'تعذر حفظ المحتوى. راجع الحقول أو الاتصال بقاعدة البيانات.';
            if (isset($_POST['ajax_campaign_batch'])) { http_response_code(422); header('Content-Type: application/json; charset=utf-8'); echo json_encode(['ok'=>false,'error'=>$adminError], JSON_UNESCAPED_UNICODE); exit; }
        }
    }
    $scholars = db()->query('SELECT * FROM scholars ORDER BY sort_order,id DESC')->fetchAll();
    $seriesItems = db()->query('SELECT * FROM series ORDER BY sort_order,id DESC')->fetchAll();
    $lessons = db()->query('SELECT l.*,s.name AS scholar_name FROM lessons l LEFT JOIN scholars s ON s.id=l.scholar_id ORDER BY l.sort_order,l.id DESC')->fetchAll();
    $books = db()->query('SELECT * FROM books ORDER BY id DESC')->fetchAll();
    $adhkarItems = db()->query('SELECT * FROM adhkar_items ORDER BY sort_order,id DESC')->fetchAll();
    $lessonEventService = new \Athar\Learning\UpcomingLessonService(db());
    $lessonEventPage = $lessonEventService->page(['page'=>1,'per_page'=>50], 'ar', $admin);
    $lessonEvents = [];
    foreach ($lessonEventPage['items'] as $eventSummary) {
        try { $lessonEvents[] = $lessonEventService->detail((int) $eventSummary['id'], 'ar', $admin); }
        catch (Throwable $ignoredEvent) { }
    }
    $auditSearch = trim(mb_substr((string) ($_GET['audit_q'] ?? ''), 0, 80, 'UTF-8'));
    $auditWhere = '';
    $auditParams = [];
    if ($auditSearch !== '') {
        $auditWhere = ' WHERE (a.action LIKE ? OR a.target_type LIKE ? OR COALESCE(a.target_id,\'\') LIKE ?)';
        $auditLike = '%' . $auditSearch . '%';
        $auditParams = [$auditLike, $auditLike, $auditLike];
    }
    $auditCountQuery = db()->prepare('SELECT COUNT(*) FROM audit_logs a' . $auditWhere);
    $auditCountQuery->execute($auditParams);
    $auditCount = (int) $auditCountQuery->fetchColumn();
    $auditPageSize = 100;
    $auditPageCount = max(1, (int) ceil($auditCount / $auditPageSize));
    $auditPage = max(1, min($auditPageCount, (int) (filter_var($_GET['audit_page'] ?? 1, FILTER_VALIDATE_INT) ?: 1)));
    $auditOffset = ($auditPage - 1) * $auditPageSize;
    $auditRowsQuery = db()->prepare("SELECT a.action,a.target_type,a.target_id,a.created_at,u.email AS actor_email,t.email AS target_email FROM audit_logs a LEFT JOIN users u ON u.id=a.actor_id LEFT JOIN users t ON a.target_type='user' AND t.id=CAST(a.target_id AS UNSIGNED)" . $auditWhere . " ORDER BY a.id DESC LIMIT {$auditPageSize} OFFSET {$auditOffset}");
    $auditRowsQuery->execute($auditParams);
    $auditRows = $auditRowsQuery->fetchAll();
    $commentCount = (int) db()->query('SELECT COUNT(*) FROM lesson_comments')->fetchColumn();
    $commentPageSize = 50;
    $commentPageCount = max(1, (int) ceil($commentCount / $commentPageSize));
    $commentPage = max(1, min($commentPageCount, (int) (filter_var($_GET['comments_page'] ?? 1, FILTER_VALIDATE_INT) ?: 1)));
    $commentOffset = ($commentPage - 1) * $commentPageSize;
    $pendingComments = db()->query("SELECT c.id,c.parent_id,c.body,c.status,c.revision,c.moderation_reason,c.created_at,c.moderated_at,l.title AS lesson_title,u.display_name,u.username FROM lesson_comments c JOIN lessons l ON l.id=c.lesson_id JOIN users u ON u.id=c.user_id ORDER BY (c.status='pending') DESC,c.parent_id IS NOT NULL,c.created_at DESC LIMIT {$commentPageSize} OFFSET {$commentOffset}")->fetchAll();
    $dashboardMetrics = [
        'users' => (int) db()->query('SELECT COUNT(*) FROM users')->fetchColumn(),
        'scholars' => (int) db()->query('SELECT COUNT(*) FROM scholars WHERE is_published=1')->fetchColumn(),
        'series' => (int) db()->query('SELECT COUNT(*) FROM series WHERE is_published=1')->fetchColumn(),
        'lessons' => (int) db()->query('SELECT COUNT(*) FROM lessons WHERE is_published=1')->fetchColumn(),
        'books' => (int) db()->query('SELECT COUNT(*) FROM books WHERE is_published=1')->fetchColumn(),
        'pending_comments' => (int) db()->query("SELECT COUNT(*) FROM lesson_comments WHERE status='pending'")->fetchColumn(),
        'active_users_30d' => (int) db()->query('SELECT COUNT(DISTINCT user_id) FROM account_daily_stats WHERE activity_date>=DATE_SUB(CURRENT_DATE,INTERVAL 29 DAY)')->fetchColumn(),
    ];
    // Deliberately exclude worship-specific aggregate counters from Admin analytics.
    // Operational adoption is represented only by privacy-safe active-user counts.
    $managedUserSearch = trim(mb_substr((string) ($_GET['users_q'] ?? ''), 0, 80, 'UTF-8'));
    $managedUserWhere = '';
    $managedUserParams = [];
    if ($managedUserSearch !== '') {
        $managedUserWhere = ' WHERE (u.email LIKE ? OR COALESCE(u.display_name,\'\') LIKE ? OR COALESCE(u.username,\'\') LIKE ?' . (ctype_digit($managedUserSearch) ? ' OR u.id=?' : '') . ')';
        $like = '%' . $managedUserSearch . '%';
        $managedUserParams = [$like, $like, $like];
        if (ctype_digit($managedUserSearch)) $managedUserParams[] = (int) $managedUserSearch;
    }
    $managedUserCountQuery = db()->prepare('SELECT COUNT(*) FROM users u' . $managedUserWhere);
    $managedUserCountQuery->execute($managedUserParams);
    $managedUserTotal = (int) $managedUserCountQuery->fetchColumn();
    $managedUserPageSize = 50;
    $managedUserPageCount = max(1, (int) ceil($managedUserTotal / $managedUserPageSize));
    $managedUserPage = max(1, min($managedUserPageCount, (int) (filter_var($_GET['users_page'] ?? 1, FILTER_VALIDATE_INT) ?: 1)));
    $managedUserOffset = ($managedUserPage - 1) * $managedUserPageSize;
    $managedUsersQuery = db()->prepare("SELECT u.id,u.email,u.display_name,u.username,u.role,u.email_verified_at,u.email_marketing_opt_in,u.suspended_at,u.session_version,u.created_at,u.updated_at,
        (SELECT MAX(a.created_at) FROM audit_logs a WHERE a.actor_id=u.id AND a.action IN ('account-login-succeeded','admin-login-succeeded')) AS last_login
        FROM users u" . $managedUserWhere . " ORDER BY u.id DESC LIMIT {$managedUserPageSize} OFFSET {$managedUserOffset}");
    $managedUsersQuery->execute($managedUserParams);
    $managedUsers = $managedUsersQuery->fetchAll();
    $smtpConfig = ['smtp_host'=>'','smtp_port'=>'587','smtp_security'=>'tls','smtp_username'=>'','smtp_from_email'=>'','smtp_from_name'=>'أثر'];
    foreach ($smtpConfig as $key=>$fallback) $smtpConfig[$key] = platform_setting($key,$fallback);
    $smtpPasswordConfigured = platform_setting('smtp_password_cipher') !== '';
    $smtpTestRecipientConfigured = platform_setting('smtp_test_recipient_cipher') !== '' && platform_setting('smtp_test_recipient_authorized_at') !== '';
    $smtpTestRecipientMasked = 'غير محدد';
    if ($smtpTestRecipientConfigured) {
        try { $smtpTestRecipientMasked = admin_redact_email(platform_secret('smtp_test_recipient')); }
        catch (Throwable $testRecipientError) { $smtpTestRecipientMasked = 'تعذر قراءة الإعداد المشفر'; }
    }
    $serviceDiagnostics = [
        'settings_key_source' => trim((string) (getenv('ATHAR_SETTINGS_KEY') ?: '')) !== '' ? 'متغير بيئة الخادم' : 'ملف محلي داخل storage/secrets',
        'base_url' => platform_email_base_url(),
        'base_url_https' => str_starts_with(platform_email_base_url(), 'https://'),
        'smtp_credentials' => $smtpPasswordConfigured,
        'test_recipient' => $smtpTestRecipientConfigured,
        'dorar_token' => platform_setting('dorar_api_token_cipher') !== '' || trim((string)(getenv('ATHAR_DORAR_API_TOKEN') ?: '')) !== '',
    ];
    $mailingOptInCount = (int) db()->query("SELECT COUNT(*) FROM users WHERE role='user' AND email_verified_at IS NOT NULL AND email_marketing_opt_in=1")->fetchColumn();
    $emailCampaigns = db()->query("SELECT c.*,u.email AS creator_email,
        (SELECT COUNT(*) FROM email_campaign_recipients r WHERE r.campaign_id=c.id) AS recipient_total,
        (SELECT COUNT(*) FROM email_campaign_recipients r WHERE r.campaign_id=c.id AND r.status='sent') AS recipient_sent,
        (SELECT COUNT(*) FROM email_campaign_recipients r WHERE r.campaign_id=c.id AND r.status IN ('pending','processing')) AS recipient_pending,
        (SELECT COUNT(*) FROM email_campaign_recipients r WHERE r.campaign_id=c.id AND r.status='failed') AS recipient_failed,
        (SELECT COUNT(*) FROM email_campaign_recipients r WHERE r.campaign_id=c.id AND r.status='skipped') AS recipient_skipped
        FROM email_campaigns c LEFT JOIN users u ON u.id=c.created_by ORDER BY c.id DESC LIMIT 30")->fetchAll();
    $dorarTokenConfigured = platform_setting('dorar_api_token_cipher') !== '' || trim((string)(getenv('ATHAR_DORAR_API_TOKEN') ?: '')) !== '';
    if (($admin['role'] ?? '') === 'owner') $backupArtifacts = db()->query('SELECT id,schema_version,byte_size,status,expires_at,created_at FROM backup_artifacts ORDER BY id DESC LIMIT 20')->fetchAll();
    $backupGrantReady = !empty($_SESSION['backup_grant_id']) && !empty($_SESSION['backup_grant_token']);
    $title='إدارة أثر';
    $activePath=$path;
    $adminLocale=admin_locale();
    $adminTheme=admin_theme();
    $viewPath=dirname(__DIR__).'/app/Views/pages/admin.php';
    $flashMessage=take_flash();
    ob_start();
    require $viewPath;
    $content=(string) ob_get_clean();
    require dirname(__DIR__).'/admin/Views/layout.php';
    exit;
}

$curatedBooks = $curatedLessons = $curatedSeries = $curatedAdhkar = $curatedScholars = [];
$publicScholar = $publicSeries = null;
$scholarSeries = $scholarLessons = $seriesLessons = $lessonComments = [];
$activeSeriesLesson = null;
$homeLessons = $homeSeries = $homeBooks = [];
$curatedBookTotal = 0; $bookPage = 1; $bookPageCount = 1; $localBookSearch = '';
try {
    if ($path === '/') {
        $homeLessons = db()->query('SELECT l.id,l.title,l.description,l.youtube_id,l.series_id,r.title AS series_title,s.name AS scholar_name FROM lessons l LEFT JOIN series r ON r.id=l.series_id AND r.is_published=1 LEFT JOIN scholars s ON s.id=l.scholar_id AND s.is_published=1 WHERE l.is_published=1 ORDER BY l.id DESC LIMIT 3')->fetchAll();
        $homeSeries = db()->query('SELECT id,title,description FROM series WHERE is_published=1 ORDER BY id DESC LIMIT 3')->fetchAll();
        $homeBooks = db()->query('SELECT id,title,author FROM books WHERE is_published=1 ORDER BY id DESC LIMIT 3')->fetchAll();
    }
    if ($path === '/books') {
        $user = current_user();
        $localBookSearch = trim(mb_substr((string) ($_GET['local_q'] ?? ''), 0, 80, 'UTF-8'));
        $bookPage = max(1, min(10000, (int) ($_GET['book_page'] ?? 1)));
        $where = ' FROM books b WHERE b.is_published=1'; $searchParams = [];
        if ($localBookSearch !== '') { $where .= ' AND (title LIKE ? OR author LIKE ? OR COALESCE(keywords,\'\') LIKE ?)'; $like = '%' . $localBookSearch . '%'; $searchParams = [$like, $like, $like]; }
        $count = db()->prepare('SELECT COUNT(*)' . $where); $count->execute($searchParams); $curatedBookTotal = (int) $count->fetchColumn();
        $bookPageCount = max(1, (int) ceil($curatedBookTotal / 20)); $bookPage = min($bookPage, $bookPageCount);
        $offset = ($bookPage - 1) * 20;
        $query = 'SELECT b.id,b.title,b.author,b.description,b.keywords,b.language,b.publication_year,b.rights_holder,b.source_url,b.download_url,b.can_download,b.file_key,(SELECT COUNT(*) FROM user_favorites f WHERE f.user_id=? AND f.target_type=\'book\' AND f.target_id=b.id) AS is_favorite' . $where . ' ORDER BY b.title LIMIT 20 OFFSET ' . $offset;
        $statement = db()->prepare($query); $statement->execute(array_merge([(int) ($user['id'] ?? 0)], $searchParams)); $curatedBooks = $statement->fetchAll();
    }
    if ($path === '/lessons') {
        $user = current_user();
        $curatedScholars = db()->query('SELECT id,name,image_key FROM scholars WHERE is_published=1 ORDER BY sort_order,id LIMIT 50')->fetchAll();
        $query = 'SELECT l.id,l.title,l.description,l.youtube_id,l.source_url,l.series_id,s.id AS scholar_id,s.name AS scholar_name,r.title AS series_name,(SELECT position_seconds FROM video_progress vp WHERE vp.user_id=? AND vp.lesson_id=l.id) AS position_seconds,(SELECT COUNT(*) FROM user_favorites f WHERE f.user_id=? AND f.target_type=\'lesson\' AND f.target_id=l.id) AS is_favorite FROM lessons l LEFT JOIN scholars s ON s.id=l.scholar_id AND s.is_published=1 LEFT JOIN series r ON r.id=l.series_id AND r.is_published=1 WHERE l.is_published=1 AND l.series_id IS NULL ORDER BY s.sort_order,s.id,l.sort_order,l.id DESC LIMIT 100';
        $statement = db()->prepare($query); $statement->execute([(int) ($user['id'] ?? 0), (int) ($user['id'] ?? 0)]); $curatedLessons = $statement->fetchAll();
        $curatedSeries = db()->query("SELECT r.id,r.title,r.description,r.scholar_id,s.name AS scholar_name,(SELECT COUNT(*) FROM lessons l WHERE l.series_id=r.id AND l.is_published=1) AS lesson_count FROM series r LEFT JOIN scholars s ON s.id=r.scholar_id AND s.is_published=1 WHERE r.is_published=1 ORDER BY s.sort_order,s.id,r.sort_order,r.id DESC LIMIT 100")->fetchAll();
        $selectedLessonId = filter_var($_GET['lesson'] ?? null,FILTER_VALIDATE_INT) ?: 0; $selectedFreeLesson = null;
        foreach ($curatedLessons as $candidate) if ((int)$candidate['id']===$selectedLessonId && empty($candidate['series_id'])) { $selectedFreeLesson=$candidate; break; }
        $freeLessonComments=[];
        if ($selectedFreeLesson) { $commentQuery=db()->prepare("SELECT c.body,c.status,c.created_at,u.display_name,u.username FROM lesson_comments c JOIN users u ON u.id=c.user_id WHERE c.lesson_id=? AND c.parent_id IS NULL AND (c.status='visible' OR (c.user_id=? AND c.status='pending')) ORDER BY c.created_at DESC LIMIT 100"); $commentQuery->execute([$selectedFreeLesson['id'],(int)($user['id'] ?? 0)]); $freeLessonComments=$commentQuery->fetchAll(); }
    }
    if ($path === '/scholar') {
        $id = filter_var($_GET['id'] ?? null, FILTER_VALIDATE_INT);
        if ($id) {
            $query = db()->prepare('SELECT id,name,image_key,biography FROM scholars WHERE id=? AND is_published=1'); $query->execute([$id]); $publicScholar = $query->fetch() ?: null;
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
                    $commentQuery=db()->prepare("SELECT c.id,c.user_id,c.body,c.status,c.created_at,u.display_name,u.username FROM lesson_comments c JOIN users u ON u.id=c.user_id WHERE c.lesson_id=? AND c.parent_id IS NULL AND (c.status='visible' OR (c.user_id=? AND c.status='pending')) ORDER BY c.created_at DESC LIMIT 100");
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
    '/dorar' => ['title' => 'موسوعة الدرر السنية', 'view' => 'pages/dorar.php'],
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
$indexablePaths = ['/', '/quran', '/hadith', '/dorar', '/books', '/lessons', '/adhkar', '/about', '/scholar', '/series'];
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
elseif ($path === '/dorar') $seoDescription = 'ابحث بالعربية في خدمات الدرر السنية للحديث والتفسير والعقيدة والفقه والأخلاق والآداب والتاريخ.';
elseif ($path === '/books') $seoDescription = 'تصفح مكتبة أثر وابحث في الكتب الإسلامية المتاحة للعامة.';
elseif ($path === '/lessons') $seoDescription = 'شاهد دروس العلماء والسلاسل العلمية المرتبة في مكتبة أثر.';
elseif ($path === '/adhkar') $seoDescription = 'تصفح أذكار الصباح والمساء والأدعية والنصوص الموثقة.';
elseif ($path === '/scholar' && !empty($publicScholar['name'])) { $seoTitle = $publicScholar['name'] . ' | علماء أثر'; $seoDescription = mb_substr(trim(strip_tags((string) $publicScholar['biography'])) ?: ('السلاسل والدروس المنشورة للشيخ ' . $publicScholar['name'] . ' في أثر.'), 0, 250, 'UTF-8'); }
elseif ($path === '/series' && !empty($publicSeries['title'])) { $seoTitle = $publicSeries['title'] . ' | سلسلة دروس في أثر'; $seoDescription = mb_substr(trim(strip_tags((string) $publicSeries['description'])) ?: ('تابع دروس سلسلة ' . $publicSeries['title'] . ' مرتبة في أثر.'), 0, 250, 'UTF-8'); }
$loadKnowledgeScript = in_array($path, ['/hadith', '/dorar', '/books', '/lessons', '/series', '/adhkar'], true);
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
