<?php
declare(strict_types=1);
require dirname(__DIR__) . '/backend/src/Domain/QuranIndex.php';
require dirname(__DIR__) . '/backend/src/Domain/NightPrayer.php';
require dirname(__DIR__) . '/app/helpers.php';
use Athar\Domain\QuranIndex;
use Athar\Domain\NightPrayer;

$checks = 0;
function check(bool $condition, string $name): void { global $checks; $checks++; if (!$condition) throw new RuntimeException('FAIL: ' . $name); }
function rejects(callable $call, string $name): void { try { $call(); } catch (InvalidArgumentException $e) { check(true, $name); return; } check(false, $name); }
$index = QuranIndex::standard();
check(count($index->metadata()['surahs']) === 114 && $index->total() === 6236, 'canonical coverage');
for ($i = 1; $i <= 6236; $i++) { $p = $index->position($i); check($index->globalIndex($p['surah'], $p['ayah']) === $i, 'global roundtrip ' . $i); }
foreach ($index->metadata()['surahs'] as $surah) check(quran_ayah_count($surah['number']) === $surah['ayah_count'], 'legacy uses canonical count');
check($index->target(1, 1, 10)['end'] === ['surah'=>2,'ayah'=>3,'global_index'=>10], '10 cross-surah target');
foreach ([10,100,1000] as $count) check($index->target(1, 1, $count)['verse_count'] === $count, 'inclusive target');
check($index->range(1,7,2,1)['verse_count'] === 2, 'boundary range');
check($index->target(114,6,1)['end']['global_index'] === 6236, 'last ayah');
rejects(fn() => $index->target(114,6,2), 'no wrap');
rejects(fn() => $index->target(1,1,0), 'zero target');
rejects(fn() => $index->range(2,1,1,1), 'reverse range');
foreach ([[0,1],[115,1],[114,7],[1,0]] as [$s,$a]) rejects(fn() => $index->globalIndex($s,$a), 'invalid position');
foreach ([0=>'beginning',9=>'beginning',10=>'ten_verses',99=>'ten_verses',100=>'qanet',999=>'qanet',1000=>'muqantar'] as $count=>$level) check(NightPrayer::milestone($count)['level'] === $level,'milestone '.$count);
$last = NightPrayer::lastThird(new DateTimeImmutable('2026-03-28T18:00:00+01:00'), new DateTimeImmutable('2026-03-29T06:00:00+02:00'));
check($last['night_seconds'] === 39600 && $last['last_third_start'] === '2026-03-29T01:20:00+01:00', 'DST elapsed instants');
$fall = NightPrayer::lastThird(new DateTimeImmutable('2026-10-24T18:00:00+02:00'),new DateTimeImmutable('2026-10-25T06:00:00+01:00'));
check($fall['night_seconds'] === 46800,'DST fall duration');
rejects(fn() => NightPrayer::lastThird(new DateTimeImmutable('2026-01-01T06:00:00Z'), new DateTimeImmutable('2026-01-01T05:00:00Z')), 'invalid bounds');
check(NightPrayer::nightIdentifier(new DateTimeImmutable('2026-10-24T21:30:00Z'),'Asia/Riyadh') === '2026-10-25','night identifier uses saved local context');
$sessions=[['night_date'=>'2026-10-22','verse_count'=>10,'measured_seconds'=>100],['night_date'=>'2026-10-22','verse_count'=>90,'measured_seconds'=>450],['night_date'=>'2026-10-23','verse_count'=>100],['night_date'=>'2026-10-24','verse_count'=>1000,'deleted_at'=>'2026-10-25']];
$nights=NightPrayer::nightlyTotals($sessions);
check(count($nights)===2 && $nights[0]['verse_count']===100 && $nights[0]['session_count']===2,'multiple sessions and deletion');
check(NightPrayer::pace($sessions)['seconds_per_verse']===5.5,'weighted genuine pace');
check(NightPrayer::pace([['verse_count'=>10]])===null,'no fabricated pace');
$streak=NightPrayer::streaks($nights,'2026-10-24',true);
check($streak[100]['current']===2 && $streak[100]['longest']===2,'open-night grace');
check(NightPrayer::streaks($nights,'2026-10-24',false)[100]['current']===0,'closed-night break');
$withGap=[['night_date'=>'2026-10-20','verse_count'=>100],['night_date'=>'2026-10-22','verse_count'=>100]];
check(NightPrayer::streaks($withGap,'2026-10-22',false,['2026-10-21'])[100]['current']===2,'exemption preserves not inflates');
check(NightPrayer::streaks($withGap,'2026-10-22')[100]['current']===1,'no implicit exemption');
echo "PASS: {$checks} canonical Quran, target/range, milestone, DST, pace and streak assertions.\n";
