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
        db()->prepare(
            "INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,'admin-action-denied','admin-action',?)"
        )->execute([(int) ($actor['id'] ?? 0), $kind !== '' ? $kind : null]);
        http_response_code(403);
        exit('غير مصرح بتنفيذ هذه العملية.');
    }
}

function admin_redact_email(?string $email): string
{
    $email = trim((string) $email);
    if ($email === '' || !str_contains($email, '@')) return $email === '' ? '—' : '***';
    [$local, $domain] = array_pad(explode('@', $email, 2), 2, '');
    $visible = mb_substr($local, 0, min(2, max(1, mb_strlen($local, 'UTF-8'))), 'UTF-8');
    return $visible . '***@' . $domain;
}

function admin_authorize_user_target(array $actor, array $target, string $operation): void
{
    $actorId = (int) ($actor['id'] ?? 0);
    $targetId = (int) ($target['id'] ?? 0);
    $actorRole = (string) ($actor['role'] ?? '');
    $targetRole = (string) ($target['role'] ?? '');

    if ($targetId < 1 || $actorId < 1) {
        http_response_code(403);
        exit('غير مصرح بتنفيذ هذه العملية.');
    }

    if ($operation === 'role') {
        if ($actorRole !== 'owner' || $targetRole === 'owner' || $targetId === $actorId) {
            http_response_code(403);
            exit('غير مصرح بتنفيذ هذه العملية.');
        }
        return;
    }

    // Owner accounts are immutable from ordinary Admin user-management flows.
    if ($targetRole === 'owner' || $targetId === $actorId) {
        http_response_code(403);
        exit('غير مصرح بتنفيذ هذه العملية.');
    }

    // Admins may manage normal users only. Owners may manage users/admins except themselves/owner.
    if ($actorRole === 'admin' && $targetRole !== 'user') {
        http_response_code(403);
        exit('غير مصرح بتنفيذ هذه العملية.');
    }
}
