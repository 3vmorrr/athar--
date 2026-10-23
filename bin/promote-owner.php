<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli' || $argc !== 2 || !filter_var($argv[1], FILTER_VALIDATE_EMAIL)) {
    fwrite(STDERR, "Usage: php bin/promote-owner.php owner@example.com\n");
    exit(2);
}

require dirname(__DIR__) . '/app/helpers.php';
$email = strtolower($argv[1]);
fwrite(STDOUT, "Type GRANT OWNER {$email} to grant the platform-owner role: ");
if (!hash_equals("GRANT OWNER {$email}", trim((string) fgets(STDIN)))) {
    fwrite(STDERR, "Confirmation did not match. No changes were made.\n");
    exit(1);
}

$statement = db()->prepare("UPDATE users SET role='owner',session_version=session_version+1 WHERE email=?");
$statement->execute([$email]);
if ($statement->rowCount() !== 1) {
    fwrite(STDERR, "No matching account was changed. Create the account first.\n");
    exit(1);
}
$query = db()->prepare('SELECT id FROM users WHERE email=?');
$query->execute([$email]);
$id = (int) $query->fetchColumn();
db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$id, 'grant-owner-role-cli', 'user', (string) $id]);
fwrite(STDOUT, "Owner role granted to {$email}.\n");
