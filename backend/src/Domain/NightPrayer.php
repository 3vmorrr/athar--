<?php
declare(strict_types=1);

namespace Athar\Domain;

use DateTimeImmutable;
use DateTimeZone;
use InvalidArgumentException;

final class NightPrayer
{
    public static function milestone(int $count): array
    {
        if ($count < 0) throw new InvalidArgumentException('negative_verse_count');
        $level = $count >= 1000 ? 'muqantar' : ($count >= 100 ? 'qanet' : ($count >= 10 ? 'ten_verses' : 'beginning'));
        $next = $count >= 1000 ? null : ($count >= 100 ? 1000 : ($count >= 10 ? 100 : 10));
        return ['level' => $level, 'verse_count' => $count, 'next_target' => $next, 'remaining' => $next === null ? 0 : $next - $count];
    }

    public static function lastThird(DateTimeImmutable $maghrib, DateTimeImmutable $fajr): array
    {
        $seconds = $fajr->getTimestamp() - $maghrib->getTimestamp();
        if ($seconds <= 0 || $seconds > 86400) throw new InvalidArgumentException('invalid_night_boundaries');
        $start = $maghrib->getTimestamp() + (int) floor($seconds * 2 / 3);
        return ['night_seconds' => $seconds, 'last_third_start' => (new DateTimeImmutable('@' . $start))->setTimezone($maghrib->getTimezone())->format(DATE_ATOM)];
    }

    public static function nightIdentifier(DateTimeImmutable $maghrib, string $timezone): string
    {
        return $maghrib->setTimezone(new DateTimeZone($timezone))->format('Y-m-d');
    }

    public static function pace(array $sessions): ?array
    {
        $seconds = 0; $verses = 0;
        foreach ($sessions as $session) {
            if (!empty($session['deleted_at'])) continue;
            $s = $session['measured_seconds'] ?? null; $v = $session['verse_count'] ?? null;
            if (is_numeric($s) && is_numeric($v) && (int) $s > 0 && (int) $v > 0) { $seconds += (int) $s; $verses += (int) $v; }
        }
        return $verses ? ['measured_seconds' => $seconds, 'measured_verses' => $verses, 'seconds_per_verse' => $seconds / $verses] : null;
    }

    public static function nightlyTotals(array $sessions): array
    {
        $nights = [];
        foreach ($sessions as $session) {
            if (!empty($session['deleted_at'])) continue;
            $date = (string) ($session['night_date'] ?? '');
            if (!self::validDate($date)) throw new InvalidArgumentException('invalid_night_date');
            $count = (int) ($session['verse_count'] ?? 0);
            if ($count < 1) throw new InvalidArgumentException('invalid_verse_count');
            $nights[$date] ??= ['night_date' => $date, 'verse_count' => 0, 'session_count' => 0];
            $nights[$date]['verse_count'] += $count; $nights[$date]['session_count']++;
        }
        ksort($nights);
        return array_values(array_map(static fn(array $night): array => $night + ['milestone' => self::milestone($night['verse_count'])], $nights));
    }

    public static function validDate(string $date): bool
    {
        $parsed = DateTimeImmutable::createFromFormat('!Y-m-d', $date, new DateTimeZone('UTC'));
        return $parsed !== false && $parsed->format('Y-m-d') === $date;
    }

    /** Exemptions are a local-domain option; server request DTOs do not accept them. */
    public static function streaks(array $nights, string $currentNight, bool $nightInProgress = false, array $exemptDates = []): array
    {
        if (!self::validDate($currentNight)) throw new InvalidArgumentException('invalid_night_date');
        $totals = []; foreach ($nights as $night) $totals[$night['night_date']] = (int) $night['verse_count'];
        $exempt = array_fill_keys(array_filter($exemptDates, [self::class, 'validDate']), true);
        $result = [];
        foreach ([1, 10, 100, 1000] as $threshold) {
            $qualified = array_filter($totals, static fn(int $count): bool => $count >= $threshold);
            $dates = array_keys($qualified); sort($dates); $longest = 0; $run = 0; $previous = null;
            foreach ($dates as $date) {
                $continuous = $previous !== null;
                if ($previous !== null) {
                    $cursor = (new DateTimeImmutable($previous))->modify('+1 day');
                    while ($cursor->format('Y-m-d') < $date) {
                        if (!isset($exempt[$cursor->format('Y-m-d')])) { $continuous = false; break; }
                        $cursor = $cursor->modify('+1 day');
                    }
                }
                $run = $continuous ? $run + 1 : 1; $longest = max($longest, $run); $previous = $date;
            }
            $cursor = new DateTimeImmutable($currentNight); $current = 0;
            if ($nightInProgress && !isset($qualified[$cursor->format('Y-m-d')])) $cursor = $cursor->modify('-1 day');
            // Bound traversal by the recorded span; exemptions never create completed nights.
            $earliest = $dates[0] ?? $currentNight;
            while ($cursor->format('Y-m-d') >= $earliest) {
                $key = $cursor->format('Y-m-d');
                if (isset($qualified[$key])) $current++;
                elseif (!isset($exempt[$key])) break;
                $cursor = $cursor->modify('-1 day');
            }
            $result[(string) $threshold] = ['current' => $current, 'longest' => $longest];
        }
        return $result;
    }
}
