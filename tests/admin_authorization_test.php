<?php
declare(strict_types=1);
require dirname(__DIR__) . '/admin/authorization.php';
$checks = 0;
$check = static function (bool $ok, string $label) use (&$checks): void {
    if (!$ok) throw new RuntimeException($label);
    $checks++;
};
$grants = [
    'owner' => ['overview','content','moderation','users','roles','campaigns','services','audit','recovery','night_prayer_defaults'],
    'admin' => ['overview','content','moderation','users','campaigns','audit'],
    'user' => [], 'unknown' => [], '' => [],
];
foreach ($grants as $role => $allowed) {
    foreach (['overview','content','moderation','users','roles','campaigns','services','audit','recovery','night_prayer_defaults','unknown',''] as $capability) {
        $check(admin_can(['id'=>1,'role'=>$role], $capability) === in_array($capability, $allowed, true), "Capability $role/$capability");
    }
}
// Explicit contract table, then exhaustive unknown/self/role combinations.
$targets = [
    'owner' => ['profile'=>['user','admin'], 'delete'=>['user','admin'], 'suspension'=>['user','admin'], 'role'=>['user','admin']],
    'admin' => ['profile'=>['user'], 'delete'=>['user'], 'suspension'=>['user'], 'role'=>[]],
];
foreach (array_keys($grants) as $actorRole) {
    foreach (array_keys($grants) as $targetRole) {
        foreach (['profile','delete','suspension','role','unknown',''] as $operation) {
            foreach ([1,2,0,-1] as $targetId) {
                $expected = $targetId === 2 && in_array($targetRole, $targets[$actorRole][$operation] ?? [], true);
                $check(admin_can_manage_user(['id'=>1,'role'=>$actorRole], ['id'=>$targetId,'role'=>$targetRole], $operation) === $expected, "Target $actorRole/$targetRole/$operation/$targetId");
            }
        }
    }
}
foreach (['scholar','series','lesson','book','adhkar','content'] as $kind) $check(admin_kind_capability($kind)==='content', $kind);
foreach (['comment-review','comment-delete'] as $kind) $check(admin_kind_capability($kind)==='moderation', $kind);
foreach (['user-profile-update','user-delete','user-suspension'] as $kind) $check(admin_kind_capability($kind)==='users', $kind);
foreach (['backup-create','backup-grant','backup-restore','local-reset'] as $kind) $check(admin_kind_capability($kind)==='recovery', $kind);
foreach (['gmail-settings','dorar-settings'] as $kind) $check(admin_kind_capability($kind)==='services', $kind);
$check(admin_kind_capability('user-role')==='roles','role mapping');
$check(admin_kind_capability('night-prayer-defaults')==='night_prayer_defaults','defaults mapping');
foreach (['','unknown',str_repeat('a',10000)] as $kind) $check(admin_kind_capability($kind)===null,'unknown action fails closed');
$check(!admin_can_manage_user(['id'=>0,'role'=>'owner'],['id'=>2,'role'=>'user'],'profile'),'invalid actor ID');
echo "PASS: $checks Admin capability/target authorization assertions.\n";
