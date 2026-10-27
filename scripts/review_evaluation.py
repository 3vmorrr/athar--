#!/usr/bin/env python3
"""Stage and aggregate Athar recitation-review evaluation evidence.

No audio ships in the repository. The manifest pins locally authorized WAV files
by SHA-256. The Android instrumentation runs the same native model/fbank/alignment
as production. This script never grants permissions, clears app data, changes
global settings, uninstalls the app, or fabricates missing results.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import pathlib
import re
import statistics
import subprocess
from collections import Counter, defaultdict

CASE_ID = re.compile(r"^[A-Za-z0-9._-]{1,80}$")
FILE_NAME = re.compile(r"^[A-Za-z0-9._-]{1,120}\.wav$")
SHA256 = re.compile(r"^[a-f0-9]{64}$")
GIT_SHA = re.compile(r"^[a-f0-9]{40}$")
KINDS = {"clean", "substitution", "omission", "repetition", "noisy"}
SPEAKER_CLASSES = {"reference_reciter", "learner"}
EXPECTED = {
    "clean": "CLEAR_MATCH",
    "substitution": "POSSIBLE_ISSUE",
    "omission": "POSSIBLE_ISSUE",
    "repetition": "POSSIBLE_ISSUE",
    "noisy": "ABSTAIN",
}


def run(command: list[str], timeout: int = 120) -> str:
    result = subprocess.run(
        command,
        capture_output=True,
        text=True,
        check=False,
        timeout=timeout,
    )
    if result.returncode != 0:
        raise RuntimeError(
            "command_failed: "
            + " ".join(command)
            + ": "
            + ((result.stderr or result.stdout).strip())
        )
    return result.stdout


def adb(serial: str | None, *args: str, timeout: int = 120) -> str:
    command = ["adb"]
    if serial:
        command += ["-s", serial]
    command += list(args)
    return run(command, timeout=timeout)


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def validate_manifest(
    manifest: dict,
    audio_root: pathlib.Path,
    strict_files: bool = True,
) -> list[dict]:
    if manifest.get("schema_version") != 1:
        raise ValueError("manifest_schema_version")
    purpose = manifest.get("purpose")
    if purpose not in {"development", "acceptance"}:
        raise ValueError("manifest_purpose")
    model_version = manifest.get("model_version")
    if not isinstance(model_version, str) or not re.fullmatch(
        r"[a-z0-9][a-z0-9._-]{2,80}", model_version
    ):
        raise ValueError("manifest_model_version")

    cases = manifest.get("cases")
    if not isinstance(cases, list) or not 1 <= len(cases) <= 500:
        raise ValueError("manifest_cases")
    ids: set[str] = set()
    speakers: dict[str, set[str]] = defaultdict(set)
    counts: Counter[str] = Counter()

    for row in cases:
        if not isinstance(row, dict):
            raise ValueError("case_not_object")
        case_id = row.get("id")
        if not isinstance(case_id, str) or not CASE_ID.fullmatch(case_id):
            raise ValueError("case_id")
        if case_id in ids:
            raise ValueError("duplicate_case_id")
        ids.add(case_id)

        speaker_id = row.get("speaker_id")
        speaker_class = row.get("speaker_class")
        if not isinstance(speaker_id, str) or not CASE_ID.fullmatch(speaker_id):
            raise ValueError("speaker_id")
        if speaker_class not in SPEAKER_CLASSES:
            raise ValueError("speaker_class")
        speakers[speaker_class].add(speaker_id)

        kind = row.get("error_kind")
        if kind not in KINDS:
            raise ValueError("error_kind")
        if row.get("expectation") != EXPECTED[kind]:
            raise ValueError("expectation_kind_mismatch")
        counts[kind] += 1

        surah = row.get("surah")
        ayah = row.get("ayah")
        ayah_end = row.get("ayah_end", ayah)
        if not isinstance(surah, int) or not 1 <= surah <= 114:
            raise ValueError("surah")
        if not isinstance(ayah, int) or ayah < 1:
            raise ValueError("ayah")
        if not isinstance(ayah_end, int) or not ayah <= ayah_end <= ayah + 4:
            raise ValueError("ayah_end")

        wav_file = row.get("wav_file")
        expected_sha = row.get("sha256")
        if not isinstance(wav_file, str) or not FILE_NAME.fullmatch(wav_file):
            raise ValueError("wav_file")
        if not isinstance(expected_sha, str) or not SHA256.fullmatch(expected_sha):
            raise ValueError("wav_sha256")
        if strict_files:
            path = audio_root / wav_file
            if not path.is_file():
                raise ValueError("wav_missing:" + wav_file)
            if sha256(path) != expected_sha:
                raise ValueError("wav_digest:" + wav_file)

    if purpose == "acceptance":
        minimums = {
            "clean": 30,
            "substitution": 20,
            "omission": 20,
            "repetition": 20,
            "noisy": 20,
        }
        for kind, minimum in minimums.items():
            if counts[kind] < minimum:
                raise ValueError(
                    f"acceptance_requires_{minimum}_{kind}_cases"
                )
        for speaker_class in SPEAKER_CLASSES:
            if len(speakers[speaker_class]) < 3:
                raise ValueError(
                    "acceptance_requires_3_distinct_" + speaker_class
                )
    return cases


def percentile(values: list[float], q: float) -> float:
    if not values:
        raise ValueError("empty_values")
    ordered = sorted(values)
    index = max(0, math.ceil(q * len(ordered)) - 1)
    return ordered[index]


def median(values: list[float]) -> float:
    if not values:
        raise ValueError("empty_values")
    return float(statistics.median(values))


def wilson(successes: int, total: int) -> dict[str, float | int]:
    if total <= 0:
        return {"n": 0, "rate": 0.0, "low95": 0.0, "high95": 0.0}
    z = 1.959963984540054
    p = successes / total
    denominator = 1 + z * z / total
    center = (p + z * z / (2 * total)) / denominator
    spread = (
        z
        * math.sqrt(
            p * (1 - p) / total + z * z / (4 * total * total)
        )
        / denominator
    )
    return {
        "n": total,
        "rate": p,
        "low95": max(0.0, center - spread),
        "high95": min(1.0, center + spread),
    }


def aggregate(manifest: dict, results: dict) -> dict:
    cases = validate_manifest(
        manifest,
        pathlib.Path("."),
        strict_files=False,
    )
    if results.get("schema_version") != 1:
        raise ValueError("results_schema")
    rows = results.get("cases")
    if not isinstance(rows, list):
        raise ValueError("results_cases")
    by_result = {row.get("id"): row for row in rows if isinstance(row, dict)}
    if set(by_result) != {row["id"] for row in cases}:
        raise ValueError("result_case_identity_mismatch")

    joined = []
    for case in cases:
        result = by_result[case["id"]]
        observed = result.get("observation")
        if observed not in {"CLEAR_MATCH", "POSSIBLE_ISSUE", "ABSTAIN"}:
            raise ValueError("observation")
        inference = float(result["inference_ms"])
        audio_ms = int(result["audio_ms"])
        finalize = float(result["finalize_ms"])
        peak = int(result["peak_pss_kb"])
        if not math.isfinite(inference) or inference < 0 or audio_ms <= 0:
            raise ValueError("latency")
        if not math.isfinite(finalize) or finalize < 0 or peak <= 0:
            raise ValueError("memory_or_finalize")
        joined.append({**case, **result})

    clean = [r for r in joined if r["expectation"] == "CLEAR_MATCH"]
    issue = [r for r in joined if r["expectation"] == "POSSIBLE_ISSUE"]
    noisy = [r for r in joined if r["expectation"] == "ABSTAIN"]
    if not clean or not issue:
        raise ValueError("clean_and_issue_required")

    false_accept = sum(r["observation"] == "CLEAR_MATCH" for r in issue)
    false_reject = sum(r["observation"] == "POSSIBLE_ISSUE" for r in clean)
    abstain = sum(r["observation"] == "ABSTAIN" for r in joined)
    noisy_decision = sum(r["observation"] != "ABSTAIN" for r in noisy)

    inference = [float(r["inference_ms"]) for r in joined]
    rtfs = [
        float(r["inference_ms"]) / int(r["audio_ms"])
        for r in joined
    ]
    first_compute = [
        float(r["first_token_compute_ms"])
        for r in joined
        if r.get("first_token_compute_ms") is not None
    ]
    first_audio = [
        float(r["first_token_audio_ms"])
        for r in joined
        if r.get("first_token_audio_ms") is not None
    ]

    group_counts = {}
    for kind in sorted(KINDS):
        subset = [r for r in joined if r["error_kind"] == kind]
        group_counts[kind] = {
            "cases": len(subset),
            "clear_match": sum(
                r["observation"] == "CLEAR_MATCH" for r in subset
            ),
            "possible_issue": sum(
                r["observation"] == "POSSIBLE_ISSUE" for r in subset
            ),
            "abstain": sum(
                r["observation"] == "ABSTAIN" for r in subset
            ),
        }

    return {
        "schema_version": 1,
        "status": (
            "MEASURED_ACCEPTANCE_CORPUS"
            if manifest["purpose"] == "acceptance"
            else "DEVELOPMENT_CORPUS_ONLY"
        ),
        "identity": {
            "run_id": results.get("run_id"),
            "git_sha": results.get("git_sha"),
            "apk_sha256": results.get("apk_sha256"),
            "model_version": results.get("model_version"),
            "device_manufacturer": results.get("device_manufacturer"),
            "device_model": results.get("device_model"),
            "device_api": results.get("device_api"),
            "device_fingerprint": results.get("device_fingerprint"),
            "device_abis": results.get("device_abis"),
        },
        "cases": {
            "total": len(joined),
            "clean": len(clean),
            "issue": len(issue),
            "noisy_abstain_expected": len(noisy),
            "by_error_kind": group_counts,
        },
        "accuracy": {
            "false_accept": wilson(false_accept, len(issue)),
            "false_reject": wilson(false_reject, len(clean)),
            "abstention": wilson(abstain, len(joined)),
            "noisy_unsafe_decision": wilson(noisy_decision, len(noisy)),
        },
        "latency": {
            "median_inference_ms": median(inference),
            "p95_inference_ms": percentile(inference, 0.95),
            "median_real_time_factor": median(rtfs),
            "p95_real_time_factor": percentile(rtfs, 0.95),
            "p95_first_token_compute_ms": (
                percentile(first_compute, 0.95) if first_compute else None
            ),
            "p95_first_token_audio_ms": (
                percentile(first_audio, 0.95) if first_audio else None
            ),
            "p95_finalize_ms": percentile(
                [float(r["finalize_ms"]) for r in joined],
                0.95,
            ),
        },
        "memory": {
            "max_sampled_pss_kb": max(int(r["peak_pss_kb"]) for r in joined),
        },
        "claims": {
            "target_accuracy_claim": None,
            "ninety_nine_percent_claim": False,
            "note": (
                "Rates are reported with sample counts and Wilson 95% intervals. "
                "Abstention is separate from false acceptance/rejection."
            ),
        },
    }


def instrumentation_component(serial: str | None, package: str) -> str:
    output = adb(serial, "shell", "pm", "list", "instrumentation")
    pattern = re.compile(r"^instrumentation:(\S+) \(target=(\S+)\)$")
    candidates = []
    for line in output.splitlines():
        match = pattern.match(line.strip())
        if match and match.group(2) == package:
            candidates.append(match.group(1))
    if len(candidates) != 1:
        raise RuntimeError(
            f"expected_one_instrumentation_for_{package}: {candidates}"
        )
    return candidates[0]


def device_run(args: argparse.Namespace) -> None:
    manifest_path = args.manifest.resolve()
    audio_root = args.audio_root.resolve()
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    cases = validate_manifest(manifest, audio_root, strict_files=True)

    git_sha = args.git_sha or run(["git", "rev-parse", "HEAD"], timeout=10).strip()
    if not GIT_SHA.fullmatch(git_sha):
        raise ValueError("git_sha")
    apk_sha = sha256(args.apk)
    run_id = args.run_id
    if not CASE_ID.fullmatch(run_id):
        raise ValueError("run_id")

    remote = (
        f"/sdcard/Android/data/{args.package}/files/review-eval/{run_id}"
    )
    adb(args.serial, "shell", "mkdir", "-p", remote)
    adb(args.serial, "push", str(manifest_path), remote + "/manifest.json")
    for row in cases:
        adb(
            args.serial,
            "push",
            str(audio_root / row["wav_file"]),
            remote + "/" + row["wav_file"],
            timeout=300,
        )

    component = instrumentation_component(args.serial, args.package)
    output = adb(
        args.serial,
        "shell",
        "am",
        "instrument",
        "-w",
        "-e",
        "reviewEvaluation",
        "true",
        "-e",
        "reviewEvalRun",
        run_id,
        "-e",
        "reviewEvalGitSha",
        git_sha,
        "-e",
        "reviewEvalApkSha",
        apk_sha,
        "-e",
        "class",
        "org.athar.app.ReviewEvaluationInstrumentedTest",
        component,
        timeout=args.timeout_seconds,
    )
    if "OK (" not in output:
        raise RuntimeError("instrumentation_did_not_pass:\n" + output)

    args.results.parent.mkdir(parents=True, exist_ok=True)
    adb(
        args.serial,
        "pull",
        remote + "/results.json",
        str(args.results),
        timeout=120,
    )
    results = json.loads(args.results.read_text(encoding="utf-8"))
    report = aggregate(manifest, results)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(args.report)


def self_test() -> None:
    manifest = {
        "schema_version": 1,
        "purpose": "development",
        "model_version": "model-v1",
        "cases": [
            {
                "id": "clean",
                "speaker_id": "s1",
                "speaker_class": "learner",
                "error_kind": "clean",
                "expectation": "CLEAR_MATCH",
                "surah": 1,
                "ayah": 1,
                "ayah_end": 1,
                "wav_file": "clean.wav",
                "sha256": "0" * 64,
            },
            {
                "id": "error",
                "speaker_id": "s1",
                "speaker_class": "learner",
                "error_kind": "substitution",
                "expectation": "POSSIBLE_ISSUE",
                "surah": 1,
                "ayah": 1,
                "ayah_end": 1,
                "wav_file": "error.wav",
                "sha256": "1" * 64,
            },
            {
                "id": "noise",
                "speaker_id": "s2",
                "speaker_class": "reference_reciter",
                "error_kind": "noisy",
                "expectation": "ABSTAIN",
                "surah": 1,
                "ayah": 1,
                "ayah_end": 1,
                "wav_file": "noise.wav",
                "sha256": "2" * 64,
            },
        ],
    }
    results = {
        "schema_version": 1,
        "run_id": "self-test",
        "git_sha": "a" * 40,
        "apk_sha256": "b" * 64,
        "model_version": "model-v1",
        "device_manufacturer": "test",
        "device_model": "test",
        "device_api": 27,
        "device_fingerprint": "test",
        "device_abis": ["armeabi-v7a"],
        "cases": [
            {
                "id": "clean",
                "observation": "CLEAR_MATCH",
                "inference_ms": 100,
                "audio_ms": 1000,
                "finalize_ms": 10,
                "first_token_compute_ms": 20,
                "first_token_audio_ms": 300,
                "peak_pss_kb": 100000,
            },
            {
                "id": "error",
                "observation": "ABSTAIN",
                "inference_ms": 120,
                "audio_ms": 1000,
                "finalize_ms": 12,
                "first_token_compute_ms": 24,
                "first_token_audio_ms": 300,
                "peak_pss_kb": 101000,
            },
            {
                "id": "noise",
                "observation": "ABSTAIN",
                "inference_ms": 80,
                "audio_ms": 1000,
                "finalize_ms": 8,
                "first_token_compute_ms": None,
                "first_token_audio_ms": None,
                "peak_pss_kb": 99000,
            },
        ],
    }
    report = aggregate(manifest, results)
    assert report["accuracy"]["false_accept"]["rate"] == 0
    assert report["accuracy"]["false_reject"]["rate"] == 0
    assert report["accuracy"]["abstention"]["rate"] == 2 / 3
    assert report["latency"]["median_inference_ms"] == 100
    print("PASS: review evaluation manifest/metric self-test.")


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("self-test")

    validate = sub.add_parser("validate")
    validate.add_argument("--manifest", type=pathlib.Path, required=True)
    validate.add_argument("--audio-root", type=pathlib.Path, required=True)

    aggregate_parser = sub.add_parser("aggregate")
    aggregate_parser.add_argument("--manifest", type=pathlib.Path, required=True)
    aggregate_parser.add_argument("--results", type=pathlib.Path, required=True)
    aggregate_parser.add_argument("--output", type=pathlib.Path, required=True)

    device = sub.add_parser("device-run")
    device.add_argument("--serial")
    device.add_argument("--package", default="org.athar.app")
    device.add_argument("--manifest", type=pathlib.Path, required=True)
    device.add_argument("--audio-root", type=pathlib.Path, required=True)
    device.add_argument("--apk", type=pathlib.Path, required=True)
    device.add_argument("--git-sha")
    device.add_argument("--run-id", required=True)
    device.add_argument("--results", type=pathlib.Path, required=True)
    device.add_argument("--report", type=pathlib.Path, required=True)
    device.add_argument("--timeout-seconds", type=int, default=3600)

    args = parser.parse_args()
    if args.command == "self-test":
        self_test()
        return 0
    if args.command == "validate":
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        validate_manifest(manifest, args.audio_root, strict_files=True)
        print("PASS: manifest and WAV digests validated.")
        return 0
    if args.command == "aggregate":
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        results = json.loads(args.results.read_text(encoding="utf-8"))
        report = aggregate(manifest, results)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(args.output)
        return 0

    if not args.apk.is_file():
        device.error("--apk must exist")
    if not 60 <= args.timeout_seconds <= 14400:
        device.error("--timeout-seconds must be 60..14400")
    device_run(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
