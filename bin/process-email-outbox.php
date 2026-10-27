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

$statusOnly=($argv[1]??'')==='--status';
if(count($argv)>2||(!$statusOnly&&isset($argv[1])&&(!ctype_digit($argv[1])||(int)$argv[1]<1||(int)$argv[1]>100))){fwrite(STDERR,"Usage: php bin/process-email-outbox.php [1..100|--status]\n");exit(2);}
$limit=$statusOnly?20:(isset($argv[1])?(int)$argv[1]:20);

try {
    $guard=\Athar\Infrastructure\MaintenanceGuard::acquire();
    if($guard===null)throw new RuntimeException('maintenance_unavailable');
    $pdo = Database::connect();
    if($statusOnly){fwrite(STDOUT,json_encode(['counts'=>$pdo->query('SELECT category,status,COUNT(*) count FROM email_outbox GROUP BY category,status ORDER BY category,status')->fetchAll()],JSON_THROW_ON_ERROR).PHP_EOL);exit(0);}
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
    error_log('[Athar email worker] Batch unavailable; inspect controlled operational diagnostics.');
    fwrite(STDERR, "Email worker failed.\n");
    exit(1);
}
