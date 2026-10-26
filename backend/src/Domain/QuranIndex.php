<?php
declare(strict_types=1);

namespace Athar\Domain;

use InvalidArgumentException;
use RuntimeException;

final class QuranIndex
{
    private array $metadata;
    private array $surahs;

    public function __construct(array $metadata)
    {
        $surahs = $metadata['surahs'] ?? [];
        $offset = 1;
        if (count($surahs) !== 114) throw new RuntimeException('The canonical index must contain 114 surahs.');
        foreach ($surahs as $i => $surah) {
            if (($surah['number'] ?? null) !== $i + 1 || ($surah['start_index'] ?? null) !== $offset || !is_int($surah['ayah_count']) || $surah['ayah_count'] < 1) throw new RuntimeException('Invalid canonical Quran metadata.');
            $offset += $surah['ayah_count'];
        }
        if ($offset !== 6237 || ($metadata['total_ayahs'] ?? null) !== 6236) throw new RuntimeException('The canonical index must contain 6236 ayahs.');
        $this->metadata = $metadata;
        $this->surahs = $surahs;
    }

    public static function standard(): self
    {
        static $instance;
        if (!$instance) {
            $path = dirname(__DIR__, 3) . '/shared/quran-index.json';
            $raw = file_get_contents($path);
            if (!is_string($raw)) throw new RuntimeException('Quran metadata is unavailable.');
            $instance = new self(json_decode($raw, true, 512, JSON_THROW_ON_ERROR));
        }
        return $instance;
    }

    public function metadata(): array { return $this->metadata; }
    public function total(): int { return 6236; }
    public function ayahCount(int $surah): int { return (int) ($this->surahs[$surah - 1]['ayah_count'] ?? 0); }

    public function globalIndex(int $surah, int $ayah): int
    {
        $count = $this->ayahCount($surah);
        if ($surah < 1 || $surah > 114 || $ayah < 1 || $ayah > $count) throw new InvalidArgumentException('invalid_quran_position');
        return $this->surahs[$surah - 1]['start_index'] + $ayah - 1;
    }

    public function position(int $index): array
    {
        if ($index < 1 || $index > $this->total()) throw new InvalidArgumentException('invalid_global_index');
        $low = 0; $high = 113;
        while ($low <= $high) {
            $mid = intdiv($low + $high, 2); $surah = $this->surahs[$mid];
            if ($index < $surah['start_index']) $high = $mid - 1;
            elseif ($index >= $surah['start_index'] + $surah['ayah_count']) $low = $mid + 1;
            else return ['surah' => $mid + 1, 'ayah' => $index - $surah['start_index'] + 1, 'global_index' => $index];
        }
        throw new RuntimeException('Canonical index lookup failed.');
    }

    public function range(int $startSurah, int $startAyah, int $endSurah, int $endAyah): array
    {
        $start = $this->globalIndex($startSurah, $startAyah); $end = $this->globalIndex($endSurah, $endAyah);
        if ($end < $start) throw new InvalidArgumentException('range_end_before_start');
        return ['start' => $this->position($start), 'end' => $this->position($end), 'verse_count' => $end - $start + 1];
    }

    public function target(int $surah, int $ayah, int $count): array
    {
        $start = $this->globalIndex($surah, $ayah);
        if ($count < 1 || $count > $this->total() - $start + 1) throw new InvalidArgumentException('target_exceeds_remaining_verses');
        return ['start' => $this->position($start), 'end' => $this->position($start + $count - 1), 'verse_count' => $count, 'remaining_available' => $this->total() - $start + 1];
    }
}
