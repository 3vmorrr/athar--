<?php
declare(strict_types=1);

/**
 * Central server-side Admin capability matrix.
 *
 * UI visibility may use these helpers, but authorization never depends on the UI.
 */
function admin_capabilities(array $actor): array
{
    $role = (string) ($actor['role'] ?? '');
    if ($role === 'owner') {
        return [
            'overview' => true,
            'content' => true,
            'moderation' => true,
            'users' => true,
            'roles' => true,
            'campaigns' => true,
            'services' => true,
            'audit' => true,
            'recovery' => true,
            'night_prayer_defaults' => true,
        ];
    }
    if ($role === 'admin') {
        return [
            'overview' => true,
            'content' => true,
            'moderation' => true,
            'users' => true,
            'roles' => false,
            'campaigns' => true,
            'services' => false,
            'audit' => true,
            'recovery' => false,
            'night_prayer_defaults' => false,
        ];
    }
    return [];
}

function admin_can(array $actor, string $capability): bool
{
    return (bool) (admin_capabilities($actor)[$capability] ?? false);
}

function admin_kind_capability(string $kind): ?string
{
    return match ($kind) {
        'scholar', 'series', 'lesson', 'book', 'adhkar', 'content', 'lesson-event' => 'content',
        'comment-review', 'comment-delete' => 'moderation',
        'engagement-campaign-save','engagement-campaign-confirm','engagement-campaign-cancel',
        'engagement-campaign-test-email',
        'campaign-create', 'campaign-batch', 'campaign-retry-failed',
        'push-campaign-create', 'push-campaign-update', 'push-campaign-cancel',
        'email-campaign-create', 'email-campaign-update', 'email-campaign-cancel' => 'campaigns',
        'user-profile-update', 'user-delete', 'user-suspension' => 'users',
        'user-role' => 'roles',
        'gmail-settings', 'dorar-settings' => 'services',
        'night-prayer-defaults' => 'night_prayer_defaults',
        'backup-create', 'backup-grant', 'backup-restore', 'local-reset' => 'recovery',
        default => null,
    };
}

function admin_authorize_kind(array $actor, string $kind): void
{
    $capability = admin_kind_capability($kind);
    if ($capability === null || !admin_can($actor, $capability)) {
        // Only canonical action identifiers may enter audit storage.
        admin_deny_action($actor, 'admin-action', $capability === null ? 'unknown' : $kind);
    }
}

function admin_deny_action(array $actor, string $targetType, string $targetId): never
{
    // Target checks can happen while a row is locked. Persist denials only after
    // rolling back so a transaction rollback cannot discard the security audit.
    if (db()->inTransaction()) db()->rollBack();
    db()->prepare(
        "INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,'admin-action-denied',?,?)"
    )->execute([(int) ($actor['id'] ?? 0), $targetType, $targetId]);
    http_response_code(403);
    exit(admin_locale() === 'en' ? 'You are not authorized to perform this action.' : 'غير مصرح بتنفيذ هذه العملية.');
}

function admin_redact_email(?string $email): string
{
    $email = trim((string) $email);
    if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) return $email === '' ? '—' : '***';
    [$local, $domain] = array_pad(explode('@', $email, 2), 2, '');
    $visible = mb_substr($local, 0, min(2, max(1, mb_strlen($local, 'UTF-8'))), 'UTF-8');
    return $visible . '***@' . $domain;
}

/** Only operational metadata is eligible for the Admin journal. */
function admin_audit_actions(): array
{
    return [
        'account-registered','account-login-succeeded','account-logout','account-deleted-minimal-audit-retained',
        'api-login-succeeded','api-email-verified','api-refresh-replay-revoked','api-logout','api-password-reset','api-password-changed','api-profile-updated','api-account-deleted',
        'admin-login-denied-role','admin-login-succeeded','admin-logout','admin-action-denied',
        'admin-user-suspended','admin-user-restored','admin-user-profile-updated','admin-user-email-updated','admin-user-deleted',
        'user-role-changed-to-admin','user-role-changed-to-user',
        'comment-visible','comment-hidden','comment-deleted',
        'gmail-smtp-settings-updated','dorar-api-settings-updated','smtp-test-accepted','smtp-test-failed','night-prayer-public-defaults-updated',
        'email-campaign-created','email-campaign-batch-sent','email-campaign-failures-requeued','email-campaign-unsubscribed',
        'backup-created-and-verified','backup-expired-and-deleted','backup-restored','backup-download-started','backup-downloaded','backup-download-grant-issued',
        'update','toggle-publication','delete','create',
        'comment-published',
        'lesson-event-draft','lesson-event-scheduled','lesson-event-live','lesson-event-published','lesson-event-cancelled',
    ];
}

function admin_audit_target_types(): array
{
    return ['user','admin-user','admin-action','platform','backup','email-campaign','lesson-comment','scholar','series','lesson','book','adhkar','lesson_event','comment'];
}

/** The view never receives raw emails or unconstrained historical target text. */
function admin_audit_projection(array $row): array
{
    $targetId = (string) ($row['target_id'] ?? '');
    $safeTarget = preg_match('/^[0-9]{1,20}$/D', $targetId) === 1
        || (($row['target_type'] ?? '') === 'admin-action' && (admin_kind_capability($targetId) !== null || in_array($targetId, ['unknown','backup-download'], true)));
    return [
        'id' => (int) ($row['id'] ?? 0),
        'action' => in_array($row['action'] ?? '', admin_audit_actions(), true) ? $row['action'] : 'other-operation',
        'target_type' => in_array($row['target_type'] ?? '', admin_audit_target_types(), true) ? $row['target_type'] : 'other',
        'target_id' => $safeTarget ? $targetId : '—',
        'created_at' => (string) ($row['created_at'] ?? ''),
        'actor_email' => admin_redact_email($row['actor_email'] ?? null),
        'target_email' => admin_redact_email($row['target_email'] ?? null),
    ];
}

function admin_can_manage_user(array $actor, array $target, string $operation): bool
{
    $actorId = (int) ($actor['id'] ?? 0);
    $targetId = (int) ($target['id'] ?? 0);
    $actorRole = (string) ($actor['role'] ?? '');
    $targetRole = (string) ($target['role'] ?? '');

    if (
        $targetId < 1 || $actorId < 1
        || !in_array($actorRole, ['owner', 'admin'], true)
        || !in_array($targetRole, ['owner', 'admin', 'user'], true)
        || !in_array($operation, ['profile', 'delete', 'suspension', 'role'], true)
        || $targetRole === 'owner' || $targetId === $actorId
    ) {
        return false;
    }

    if ($operation === 'role') {
        return $actorRole === 'owner';
    }

    // Admins may manage normal users only. Owners may manage users/admins except themselves/owner.
    return $actorRole === 'owner' || $targetRole === 'user';
}

function admin_authorize_user_target(array $actor, array $target, string $operation): void
{
    if (!admin_can_manage_user($actor, $target, $operation)) {
        admin_deny_action($actor, 'admin-user', (string) max(0, (int) ($target['id'] ?? 0)));
    }
}
