<?php
declare(strict_types=1);

function admin_adhkar_snapshot(array $row): array
{
    return [
        'title' => (string) ($row['title'] ?? ''),
        'formula' => (string) ($row['formula'] ?? ''),
        'source_reference' => (string) ($row['source_reference'] ?? ''),
        'source_url' => (string) ($row['source_url'] ?? ''),
        'repeat_count' => $row['repeat_count'] === null ? null : (int) $row['repeat_count'],
        'category' => (string) ($row['category'] ?? ''),
        'is_reviewed' => (bool) ($row['is_reviewed'] ?? false),
        'is_published' => (bool) ($row['is_published'] ?? false),
        'sort_order' => (int) ($row['sort_order'] ?? 0),
    ];
}

function admin_record_adhkar_revision(PDO $db, array $row, string $action, int $actorId): void
{
    $allowed = ['created','updated','publication_changed','deleted'];
    if (!in_array($action, $allowed, true)) throw new InvalidArgumentException('Invalid adhkar revision action.');
    $sourceId = (int) ($row['id'] ?? 0);
    $revision = (int) ($row['revision'] ?? 0);
    if ($sourceId < 1 || $revision < 1) throw new RuntimeException('Invalid adhkar revision snapshot.');
    $statement = $db->prepare(
        'INSERT INTO adhkar_item_revisions (adhkar_id,source_item_id,revision,action,snapshot_json,actor_id)
         VALUES (?,?,?,?,?,?)'
    );
    $statement->execute([
        $sourceId,
        $sourceId,
        $revision,
        $action,
        json_encode(admin_adhkar_snapshot($row), JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
        $actorId,
    ]);
}
