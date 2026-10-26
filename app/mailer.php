<?php
declare(strict_types=1);

function platform_setting(string $key, string $default = ''): string
{
    $statement = db()->prepare('SELECT setting_value FROM platform_settings WHERE setting_key=? LIMIT 1');
    $statement->execute([$key]);
    $value = $statement->fetchColumn();
    return $value === false ? $default : (string) $value;
}

function save_platform_setting(string $key, string $value, ?int $actorId): void
{
    db()->prepare('INSERT INTO platform_settings (setting_key,setting_value,updated_by) VALUES (?,?,?) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value),updated_by=VALUES(updated_by)')->execute([$key, $value, $actorId]);
}

function setting_cipher_key(): string
{
    $key = trim((string) (getenv('ATHAR_SETTINGS_KEY') ?: ''));
    if ($key !== '' && !preg_match('/^[a-f0-9]{64}$/i', $key)) throw new RuntimeException('قيمة ATHAR_SETTINGS_KEY غير صالحة؛ يجب أن تكون 64 خانة hex.');
    if ($key === '') {
        $directory = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'secrets';
        $path = $directory . DIRECTORY_SEPARATOR . 'settings.key';
        if (!is_dir($directory) && !@mkdir($directory, 0700, true) && !is_dir($directory)) throw new RuntimeException('تعذر إنشاء مجلد مفاتيح التشفير داخل storage.');
        if (is_file($path)) {
            $key = trim((string) @file_get_contents($path));
        } else {
            $generated = bin2hex(random_bytes(32));
            $handle = @fopen($path, 'x');
            if ($handle !== false) {
                $written = fwrite($handle, $generated);
                fflush($handle);
                fclose($handle);
                if ($written !== strlen($generated)) { @unlink($path); throw new RuntimeException('تعذر حفظ مفتاح التشفير المحلي. تحقق من صلاحيات مجلد storage.'); }
                @chmod($path, 0600);
                $key = $generated;
            } else {
                // Another PHP request may have created the key concurrently.
                $key = trim((string) @file_get_contents($path));
            }
        }
        if (!preg_match('/^[a-f0-9]{64}$/i', $key)) throw new RuntimeException('ملف مفتاح التشفير المحلي غير صالح أو غير قابل للقراءة.');
    }
    return hex2bin($key) ?: throw new RuntimeException('مفتاح إعدادات المنصة غير صالح.');
}

function seal_platform_secret(string $secret): string
{
    $iv = random_bytes(12); $tag = '';
    $ciphertext = openssl_encrypt($secret, 'aes-256-gcm', setting_cipher_key(), OPENSSL_RAW_DATA, $iv, $tag);
    if ($ciphertext === false || strlen($tag) !== 16) throw new RuntimeException('تعذر تشفير إعداد سري.');
    return base64_encode($iv . $tag . $ciphertext);
}

function open_platform_secret(string $sealed): string
{
    $packed = base64_decode($sealed, true);
    if (!is_string($packed) || strlen($packed) < 29) throw new RuntimeException('إعداد سري محفوظ بصيغة غير صالحة.');
    $plain = openssl_decrypt(substr($packed, 28), 'aes-256-gcm', setting_cipher_key(), OPENSSL_RAW_DATA, substr($packed, 0, 12), substr($packed, 12, 16));
    if (!is_string($plain)) throw new RuntimeException('تعذر فك تشفير إعداد سري. تحقق من مفتاح ATHAR_SETTINGS_KEY.');
    return $plain;
}

function platform_email_base_url(): string
{
    $configured = rtrim(trim((string) (getenv('ATHAR_BASE_URL') ?: '')), '/');
    if ($configured !== '' && filter_var($configured, FILTER_VALIDATE_URL) && in_array(strtolower((string) parse_url($configured, PHP_URL_SCHEME)), ['http', 'https'], true)) return $configured;
    $host = trim((string) ($_SERVER['HTTP_HOST'] ?? ''));
    if ($host !== '' && preg_match('/^[a-z0-9.-]+(?::[0-9]{1,5})?$/i', $host)) {
        $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
        return $scheme . '://' . $host;
    }
    return 'http://127.0.0.1:8081';
}

function email_campaign_unsubscribe_url(int $userId): string
{
    $token = hash_hmac('sha256', 'athar-email-unsubscribe:' . $userId, setting_cipher_key());
    return platform_email_base_url() . '/email-unsubscribe?u=' . $userId . '&t=' . $token;
}

function platform_email_html(string $subject, string $body, string $footerHtml = '', ?string $customBodyHtml = null, string $locale = 'ar'): string
{
    $locale = $locale === 'en' ? 'en' : 'ar';
    $direction = $locale === 'en' ? 'ltr' : 'rtl';
    $brand = $locale === 'en' ? 'Athar' : 'منصة أثر';
    $tagline = $locale === 'en'
        ? 'Athar · A calm space for Quran, knowledge, and worship'
        : 'أثر · مساحة هادئة للقرآن والمعرفة والعبادة';
    $logoUrl = platform_email_base_url() . '/assets/images/athar-logo-cropped.png';
    $escapedBody = nl2br(htmlspecialchars($body, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'), false);
    return '<!doctype html><html lang="' . $locale . '" dir="' . $direction . '"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>'
        . '<body style="margin:0;padding:24px 12px;background:#f4f3ed;color:#22332c;font-family:Arial,Tahoma,sans-serif;direction:' . $direction . '">'
        . '<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:620px;margin:0 auto;border-collapse:collapse"><tr><td style="padding:8px 0 18px;text-align:center">'
        . '<img src="' . htmlspecialchars($logoUrl, ENT_QUOTES, 'UTF-8') . '" width="104" height="96" alt="Athar" style="display:block;width:104px;height:auto;max-height:96px;object-fit:contain;margin:0 auto">'
        . '</td></tr><tr><td style="overflow:hidden;border:1px solid #d8e2da;border-radius:18px;background:#fff">'
        . '<div style="height:7px;background:#1e6753"></div><div style="padding:30px 28px 26px;text-align:' . ($locale === 'en' ? 'left' : 'right') . '">'
        . '<div style="margin:0 0 10px;color:#a66b31;font-size:12px;font-weight:bold;letter-spacing:.08em">' . htmlspecialchars($brand, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') . '</div>'
        . '<h1 style="margin:0 0 20px;color:#174f40;font-size:23px;line-height:1.6">' . htmlspecialchars($subject, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') . '</h1>'
        . '<div style="color:#34443d;font-size:16px;line-height:2">' . ($customBodyHtml ?? $escapedBody) . '</div>'
        . ($footerHtml !== '' ? '<div style="margin-top:24px;padding-top:18px;border-top:1px solid #e3e9e4;color:#65756c;font-size:13px;line-height:1.9">' . $footerHtml . '</div>' : '')
        . '</div></td></tr><tr><td style="padding:18px 8px;text-align:center;color:#68756e;font-size:12px;line-height:1.8">' . htmlspecialchars($tagline, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') . '</td></tr></table></body></html>';
}

function platform_secret(string $key, string $environmentName = ''): string
{
    $sealed = platform_setting($key . '_cipher');
    if ($sealed !== '') return open_platform_secret($sealed);
    return $environmentName !== '' ? (string) (getenv($environmentName) ?: '') : '';
}

function smtp_read_response($socket): array
{
    $lines = [];
    while (($line = fgets($socket, 2048)) !== false) {
        $lines[] = rtrim($line, "\r\n");
        if (preg_match('/^\d{3} /', $line)) break;
    }
    if (!$lines) throw new RuntimeException('لم يستجب خادم البريد.');
    return [(int) substr($lines[count($lines) - 1], 0, 3), implode("\n", $lines)];
}

function smtp_command($socket, string $command, array $expected): string
{
    if (str_contains($command, "\r") || str_contains($command, "\n")) throw new RuntimeException('طلب بريد غير صالح.');
    fwrite($socket, $command . "\r\n");
    [$code, $reply] = smtp_read_response($socket);
    if (!in_array($code, $expected, true)) {
        $diagnostic = preg_replace('/[\r\n]+/', ' ', $reply) ?? '';
        throw new RuntimeException('رفض خادم البريد الطلب (' . $code . '): ' . substr($diagnostic, 0, 220));
    }
    return $reply;
}

function send_platform_email(string $recipient, string $subject, string $body, ?string &$failureReason = null, ?string $htmlBody = null, string $htmlFooter = '', string $category = 'transactional', string $locale = 'ar'): bool
{
    $failureReason = null;
    $host = trim(platform_setting('smtp_host'));
    $port = (int) platform_setting('smtp_port', '587');
    $security = platform_setting('smtp_security', 'tls');
    $username = platform_setting('smtp_username');
    $password = platform_secret('smtp_password');
    $from = trim(platform_setting('smtp_from_email'));
    $fromName = trim(platform_setting('smtp_from_name', 'Athar'));
    if ($host === '' || !preg_match('/^[a-z0-9.-]+$/i', $host) || $port < 1 || $port > 65535 || !in_array($security, ['tls','ssl'], true) || !filter_var($from, FILTER_VALIDATE_EMAIL) || !filter_var($recipient, FILTER_VALIDATE_EMAIL)) { $failureReason = 'إعدادات البريد المحفوظة غير مكتملة.'; return false; }
    if (preg_match('/[\r\n]/', $fromName) || preg_match('/[\r\n]/', $subject) || preg_match('/[\r\n]/', $username)) { $failureReason = 'أحد حقول إعداد البريد يحتوي على قيمة غير صالحة.'; return false; }
    $context = stream_context_create(['ssl' => ['verify_peer' => true, 'verify_peer_name' => true, 'allow_self_signed' => false]]);
    $target = ($security === 'ssl' ? 'ssl://' : 'tcp://') . $host . ':' . $port;
    $socket = @stream_socket_client($target, $errorCode, $errorMessage, 12, STREAM_CLIENT_CONNECT, $context);
    if (!is_resource($socket)) { $failureReason = 'تعذر الوصول إلى خادم Gmail على المنفذ المحفوظ (' . (int) $errorCode . '). تحقق من اتصال الخادم وإتاحة المنفذ 587.'; error_log('[Athar] SMTP connection failed (' . (int) $errorCode . ').'); return false; }
    stream_set_timeout($socket, 12);
    try {
        [$greeting] = smtp_read_response($socket);
        if ($greeting !== 220) throw new RuntimeException('SMTP greeting rejected.');
        smtp_command($socket, 'EHLO athar.local', [250]);
        if ($security === 'tls') {
            smtp_command($socket, 'STARTTLS', [220]);
            if (stream_socket_enable_crypto($socket, true, STREAM_CRYPTO_METHOD_TLS_CLIENT) !== true) throw new RuntimeException('SMTP TLS negotiation failed.');
            smtp_command($socket, 'EHLO athar.local', [250]);
        }
        if ($username !== '') {
            smtp_command($socket, 'AUTH LOGIN', [334]);
            smtp_command($socket, base64_encode($username), [334]);
            smtp_command($socket, base64_encode($password), [235]);
        }
        smtp_command($socket, 'MAIL FROM:<' . $from . '>', [250]);
        smtp_command($socket, 'RCPT TO:<' . $recipient . '>', [250, 251]);
        smtp_command($socket, 'DATA', [354]);
        $encodedName = '=?UTF-8?B?' . base64_encode($fromName) . '?=';
        $encodedSubject = '=?UTF-8?B?' . base64_encode($subject) . '?=';
        $messageId = '<' . bin2hex(random_bytes(16)) . '@' . $host . '>';
        $body = preg_replace("/(?<!\r)\n|\r(?!\n)/", "\r\n", $body) ?? $body;
        $body = preg_replace('/^\./m', '..', $body) ?? $body;
        $boundary = 'athar_' . bin2hex(random_bytes(16));
        $plainPart = rtrim(chunk_split(base64_encode($body), 76, "\r\n"), "\r\n");
        $html = platform_email_html($subject, $body, $htmlFooter, $htmlBody, $locale);
        $htmlPart = rtrim(chunk_split(base64_encode($html), 76, "\r\n"), "\r\n");
        $message = "Date: " . date(DATE_RFC2822) . "\r\nFrom: {$encodedName} <{$from}>\r\nTo: <{$recipient}>\r\nSubject: {$encodedSubject}\r\nMessage-ID: {$messageId}\r\nMIME-Version: 1.0\r\nContent-Type: multipart/alternative; boundary=\"{$boundary}\"\r\n\r\n"
            . "--{$boundary}\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n{$plainPart}\r\n"
            . "--{$boundary}\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n{$htmlPart}\r\n--{$boundary}--\r\n.\r\n";
        fwrite($socket, $message);
        [$accepted] = smtp_read_response($socket);
        if ($accepted !== 250) throw new RuntimeException('SMTP server rejected the message.');
        try { smtp_command($socket, 'QUIT', [221]); } catch (Throwable $quitError) { error_log('[Athar] SMTP quit failed after message acceptance.'); }
        if (!in_array($category, ['transactional','campaign'], true)) $category = 'transactional';
        try { db()->prepare('INSERT INTO email_send_events (category,sent_at) VALUES (?,UTC_TIMESTAMP())')->execute([$category]); }
        catch (Throwable $logError) { error_log('[Athar] Email send event could not be recorded.'); }
        return true;
    } catch (Throwable $exception) {
        $failureReason = preg_replace('/[\r\n]+/', ' ', $exception->getMessage()) ?? 'فشل اتصال البريد.';
        error_log('[Athar] SMTP delivery failed: ' . $failureReason);
        return false;
    } finally { fclose($socket); }
}
