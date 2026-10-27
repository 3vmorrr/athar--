<?php
declare(strict_types=1);
require dirname(__DIR__) . '/app/helpers.php';
require dirname(__DIR__) . '/admin/events.php';
$checks = 0;
function event_time_check(bool $ok, string $label): void { global $checks; if (!$ok) throw new RuntimeException($label); $checks++; }
function event_time_denied(string $time, string $zone, string $offset, string $label): void {
    try { admin_event_instant($time,$zone,$offset); } catch (RuntimeException $failure) { event_time_check(true,$label); return; }
    event_time_check(false,$label);
}
event_time_check(admin_event_instant('', 'UTC') === null,'Empty draft time remains genuinely absent');
event_time_check(admin_event_instant('2026-12-01T12:34','UTC') === '2026-12-01T12:34:00.000+00:00','Minute input is explicit UTC, not server timezone');
event_time_check(admin_event_instant('2026-12-01T12:34:56.123','UTC') === '2026-12-01T12:34:56.123+00:00','Seconds and milliseconds survive');
event_time_check(admin_event_instant('2026-12-01T12:34:56.1','UTC','Z') === '2026-12-01T12:34:56.100+00:00','Fraction is padded without losing precision');
event_time_denied('2026-02-30T12:00','UTC','','Calendar overflow is rejected');
event_time_denied('tomorrow','UTC','','Relative dates are rejected');
event_time_denied('2026-12-01T12:00:00.1234','UTC','','Excess precision is rejected rather than truncated');
event_time_denied('2019-12-01T12:00','UTC','','Canonical year bounds apply');
event_time_denied('2026-12-01T12:00','Invalid/Zone','','Invalid timezone is rejected');
event_time_denied('2026-03-08T02:30','America/New_York','','Nonexistent DST wall time is rejected');
event_time_denied('2026-11-01T01:30','America/New_York','','Repeated DST wall time requires an explicit choice');
$early = admin_event_instant('2026-11-01T01:30','America/New_York','-04:00');
$late = admin_event_instant('2026-11-01T01:30','America/New_York','-05:00');
event_time_check((new DateTimeImmutable($late))->getTimestamp() - (new DateTimeImmutable($early))->getTimestamp() === 3600,'Both explicit overlap occurrences remain distinct');
event_time_denied('2026-11-01T01:30','America/New_York','-03:00','Invalid overlap offset is rejected');
event_time_denied('2026-12-01T12:00','UTC','+01:00','Mismatched unique-time offset is rejected');
event_time_check(admin_event_instant('2026-12-01T12:00','Asia/Kathmandu') === '2026-12-01T12:00:00.000+05:45','Non-hour offsets remain exact');
echo 'PASS: ' . $checks . " strict local-time/DST event assertions.\n";
