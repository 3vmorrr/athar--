<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli' || $argc !== 2 || !filter_var($argv[1], FILTER_VALIDATE_EMAIL)) {
    fwrite(STDERR, "Usage: php bin/promote-admin.php admin@example.com\n");
    exit(2);
}

require dirname(__DIR__) . '/app/helpers.php';
$email = strtolower($argv[1]);
fwrite(STDOUT, "Type PROMOTE {$email} to grant the admin role: ");
$confirmation = trim((string) fgets(STDIN));
if (!hash_equals("PROMOTE {$email}", $confirmation)) {
    fwrite(STDERR, "Confirmation did not match. No changes were made.\n");
    exit(1);
}

$statement = db()->prepare("UPDATE users SET role='admin' WHERE email=?");
$statement->execute([$email]);
if ($statement->rowCount() !== 1) {
    fwrite(STDERR, "No matching account was changed. Create the account first.\n");
    exit(1);
}
$actor = db()->prepare('SELECT id FROM users WHERE email=?');
$actor->execute([$email]);
$id = (int) $actor->fetchColumn();
db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$id, 'grant-admin-role', 'user', (string) $id]);
fwrite(STDOUT, "Admin role granted to {$email}.\n");
