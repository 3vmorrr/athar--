"""Create controlled ayah-level audio perturbations from the MP3Quran pilot.

This is an evaluation/stress-test set, not learner speech and not ASR training data.
It only tests clean, omitted, repeated, and substituted ayah segments.
"""

from __future__ import annotations

import json
import math
import os
from collections import defaultdict
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly


ROOT = Path(os.environ.get("ATHAR_TRAINING_ROOT", Path.home() / ".athar-training"))
PILOT = Path(os.environ.get("ATHAR_PILOT_DIR", ROOT / "pilot"))
OUT = Path(os.environ.get("ATHAR_SYNTHETIC_DIR", ROOT / "synthetic-pilot"))
TARGET_RATE = 16_000
SEPARATOR_SECONDS = 0.12


def read_audio(path: Path) -> np.ndarray:
    samples, rate = sf.read(path, dtype="float32", always_2d=True)
    mono = samples.mean(axis=1)
    if rate != TARGET_RATE:
        divisor = math.gcd(TARGET_RATE, rate)
        mono = resample_poly(mono, TARGET_RATE // divisor, rate // divisor).astype(np.float32)
    return mono


def main() -> None:
    manifest = json.loads((PILOT / "manifest.json").read_text(encoding="utf-8"))
    groups: dict[str, list[dict]] = defaultdict(list)
    for row in manifest["samples"]:
        groups[row["audio_file"]].append(row)

    pcm_dir = OUT / "pcm"
    pcm_dir.mkdir(parents=True, exist_ok=True)
    separator = np.zeros(round(SEPARATOR_SECONDS * TARGET_RATE), dtype=np.float32)
    cases: list[dict] = []
    skipped_sources: list[str] = []

    for source_index, (audio_file, rows) in enumerate(sorted(groups.items())):
        rows.sort(key=lambda row: row["ayah"])
        # Prefer the first three ayahs: the pilot is intentionally short and
        # the API timing rows are ayah-level, not word-level.
        triplet = next(
            (rows[i : i + 3] for i in range(len(rows) - 2)
             if rows[i + 1]["ayah"] == rows[i]["ayah"] + 1
             and rows[i + 2]["ayah"] == rows[i + 1]["ayah"] + 1),
            None,
        )
        if triplet is None:
            skipped_sources.append(audio_file)
            continue

        source = read_audio(PILOT / audio_file)
        duration_ms = len(source) * 1000 / TARGET_RATE
        ayahs: dict[int, np.ndarray] = {}
        for row in rows:
            start = max(0, round(row["start_ms"] * TARGET_RATE / 1000))
            end = min(len(source), round(row["end_ms"] * TARGET_RATE / 1000))
            if end > start:
                ayahs[row["ayah"]] = source[start:end]
        if any(row["ayah"] not in ayahs for row in triplet):
            skipped_sources.append(audio_file)
            continue

        first, middle, last = (row["ayah"] for row in triplet)
        alternate = next((ayah for ayah in ayahs if ayah not in (first, middle, last)), None)
        if alternate is None:
            skipped_sources.append(audio_file)
            continue

        recipes = {
            "clean": [first, middle, last],
            "omission": [first, last],
            "repetition": [first, middle, middle, last],
            "ayah_substitution": [first, alternate, last],
        }
        for kind, sequence in recipes.items():
            pieces: list[np.ndarray] = []
            for index, ayah in enumerate(sequence):
                if index:
                    pieces.append(separator)
                pieces.append(ayahs[ayah])
            pcm = np.concatenate(pieces).astype("<f4", copy=False)
            file_name = f"case-{source_index:02d}-{kind}.f32"
            (pcm_dir / file_name).write_bytes(pcm.tobytes())
            cases.append({
                "case_id": f"{source_index:02d}-{kind}",
                "kind": kind,
                "read_id": triplet[0]["read_id"],
                "reciter": triplet[0]["reciter"],
                "source_audio": audio_file,
                "surah": triplet[0]["surah"],
                "ayah_start": first,
                "ayah_end": last,
                "expected_ayahs": [first, middle, last],
                "spoken_ayahs": sequence,
                "changed_ayah": middle if kind != "clean" else None,
                "substitute_ayah": alternate if kind == "ayah_substitution" else None,
                "pcm_file": f"pcm/{file_name}",
                "sample_rate": TARGET_RATE,
                "duration_seconds": round(len(pcm) / TARGET_RATE, 3),
                "source_duration_seconds": round(duration_ms / 1000, 3),
            })

    payload = {
        "purpose": "Controlled ayah-level stress test; not real learner speech or model training data.",
        "source_manifest": str(PILOT / "manifest.json"),
        "sample_rate": TARGET_RATE,
        "cases": cases,
        "skipped_sources": skipped_sources,
    }
    (OUT / "cases.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({
        "case_count": len(cases),
        "source_recordings": len(groups),
        "skipped_sources": skipped_sources,
        "cases_by_kind": {kind: sum(item["kind"] == kind for item in cases)
                          for kind in ("clean", "omission", "repetition", "ayah_substitution")},
        "output": str(OUT),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
