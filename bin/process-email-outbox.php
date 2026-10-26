<?php
declare(strict_types=1);

use Athar\Engagement\EmailOutboxService;
use Athar\Infrastructure\Database;

require_once dirname(__DIR__) . '/backend/bootstrap.php';
require_once dirname(__DIR__) . '/app/helpers.php';
require_once dirname(__DIR__) . '/app/mailer.php';

if (PHP_SAPI !== 'cli') {
    fwrite(STDERR, "CLI only.\n");
    exit(2);
}

$limit = isset($argv[1]) && ctype_digit((string)$argv[1]) ? max(1,min(100,(int)$argv[1])) : 20;

try {
    $pdo = Database::connect();
    $outbox = new EmailOutboxService($pdo);
    $result = $outbox->processBatch(
        static function(
            string $recipient,
            string $subject,
            string $plainBody,
            ?string $htmlBody,
            string $htmlFooter,
            string $category,
            string $locale
        ): array {
            $failure = null;
            $sent = send_platform_email(
                $recipient,
                $subject,
                $plainBody,
                $failure,
                $htmlBody,
                $htmlFooter,
                $category,
                $locale
            );
            return [$sent, $failure ?: null];
        },
        $limit
    );
    fwrite(STDOUT, json_encode($result, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR) . PHP_EOL);
    exit(0);
} catch (Throwable $e) {
    error_log('[Athar email worker] ' . $e->getMessage());
    fwrite(STDERR, "Email worker failed.\n");
    exit(1);
}
