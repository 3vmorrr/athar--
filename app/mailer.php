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
    if (!preg_match('/^[a-f0-9]{64}$/i', $key)) throw new RuntimeException('يلزم ضبط مفتاح ATHAR_SETTINGS_KEY لحفظ أسرار إعدادات المنصة.');
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
    if (!in_array($code, $expected, true)) throw new RuntimeException('رفض خادم البريد الطلب (' . $code . ').');
    return $reply;
}

function send_platform_email(string $recipient, string $subject, string $body): bool
{
    $host = trim(platform_setting('smtp_host'));
    $port = (int) platform_setting('smtp_port', '587');
    $security = platform_setting('smtp_security', 'tls');
    $username = platform_setting('smtp_username');
    $password = platform_secret('smtp_password');
    $from = trim(platform_setting('smtp_from_email'));
    $fromName = trim(platform_setting('smtp_from_name', 'Athar'));
    if ($host === '' || !preg_match('/^[a-z0-9.-]+$/i', $host) || $port < 1 || $port > 65535 || !in_array($security, ['tls','ssl'], true) || !filter_var($from, FILTER_VALIDATE_EMAIL) || !filter_var($recipient, FILTER_VALIDATE_EMAIL)) return false;
    if (preg_match('/[\r\n]/', $fromName) || preg_match('/[\r\n]/', $subject) || preg_match('/[\r\n]/', $username)) return false;
    $context = stream_context_create(['ssl' => ['verify_peer' => true, 'verify_peer_name' => true, 'allow_self_signed' => false]]);
    $target = ($security === 'ssl' ? 'ssl://' : 'tcp://') . $host . ':' . $port;
    $socket = @stream_socket_client($target, $errorCode, $errorMessage, 12, STREAM_CLIENT_CONNECT, $context);
    if (!is_resource($socket)) { error_log('[Athar] SMTP connection failed (' . (int) $errorCode . ').'); return false; }
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
        $encodedBody = rtrim(chunk_split(base64_encode($body), 76, "\r\n"), "\r\n");
        $message = "Date: " . date(DATE_RFC2822) . "\r\nFrom: {$encodedName} <{$from}>\r\nTo: <{$recipient}>\r\nSubject: {$encodedSubject}\r\nMessage-ID: {$messageId}\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n{$encodedBody}\r\n.\r\n";
        fwrite($socket, $message);
        [$accepted] = smtp_read_response($socket);
        if ($accepted !== 250) throw new RuntimeException('SMTP server rejected the message.');
        smtp_command($socket, 'QUIT', [221]);
        return true;
    } catch (Throwable $exception) {
        error_log('[Athar] SMTP delivery failed: ' . $exception->getMessage());
        return false;
    } finally { fclose($socket); }
}
