#!/usr/bin/env python3
"""Reproducible, non-destructive Athar cold/warm startup measurement harness.

The script never uninstalls, clears app data, changes global settings, grants permissions,
or changes locale/theme. It measures the already-installed benchmark/release-like build and
records enough build/device metadata to make a result reviewable.
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
import sys
import time
from datetime import datetime, timezone
from typing import Iterable

DEFAULT_PACKAGE = "org.athar.app"
DEFAULT_ACTIVITY = "org.athar.app.MainActivity"

TIMING_RE = re.compile(r"^(ThisTime|TotalTime|WaitTime):\s*(\d+)\s*$", re.MULTILINE)


def run(command: list[str], *, timeout: int = 30) -> str:
    result = subprocess.run(
        command,
        check=False,
        capture_output=True,
        text=True,
        timeout=timeout,
    )
    if result.returncode != 0:
        message = (result.stderr or result.stdout).strip()
        raise RuntimeError("command_failed: " + " ".join(command) + ": " + message)
    return result.stdout


def adb(serial: str | None, *args: str, timeout: int = 30) -> str:
    command = ["adb"]
    if serial:
        command += ["-s", serial]
    command += list(args)
    return run(command, timeout=timeout)


def parse_start_wait(output: str) -> dict[str, int]:
    if "Status: ok" not in output:
        raise ValueError("am_start_not_ok")
    values = {name: int(value) for name, value in TIMING_RE.findall(output)}
    if "TotalTime" not in values or "WaitTime" not in values:
        raise ValueError("am_start_missing_timings")
    if any(value < 0 or value > 120_000 for value in values.values()):
        raise ValueError("am_start_timing_out_of_range")
    return {
        "this_time_ms": values.get("ThisTime", 0),
        "total_time_ms": values["TotalTime"],
        "wait_time_ms": values["WaitTime"],
    }


def percentile95(values: Iterable[int]) -> int:
    ordered = sorted(values)
    if not ordered:
        raise ValueError("empty_measurements")
    index = max(0, math.ceil(0.95 * len(ordered)) - 1)
    return ordered[index]


def summary(samples: list[dict[str, int]]) -> dict[str, int | float]:
    totals = [sample["total_time_ms"] for sample in samples]
    waits = [sample["wait_time_ms"] for sample in samples]
    return {
        "iterations": len(samples),
        "median_total_time_ms": statistics.median(totals),
        "p95_total_time_ms": percentile95(totals),
        "median_wait_time_ms": statistics.median(waits),
        "p95_wait_time_ms": percentile95(waits),
        "min_total_time_ms": min(totals),
        "max_total_time_ms": max(totals),
    }


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def property_value(serial: str | None, key: str) -> str:
    return adb(serial, "shell", "getprop", key).strip()


def component(package: str, activity: str) -> str:
    if activity.startswith("."):
        return package + "/" + package + activity
    return package + "/" + activity


def start_once(serial: str | None, target: str) -> dict[str, int]:
    output = adb(
        serial,
        "shell",
        "am",
        "start",
        "-W",
        "-n",
        target,
        timeout=45,
    )
    return parse_start_wait(output)


def measure_cold(
    serial: str | None,
    package: str,
    target: str,
    iterations: int,
    settle_seconds: float,
) -> list[dict[str, int]]:
    samples = []
    for _ in range(iterations):
        adb(serial, "shell", "am", "force-stop", package)
        time.sleep(settle_seconds)
        samples.append(start_once(serial, target))
        time.sleep(settle_seconds)
    return samples


def measure_warm(
    serial: str | None,
    target: str,
    iterations: int,
    settle_seconds: float,
) -> list[dict[str, int]]:
    start_once(serial, target)
    time.sleep(settle_seconds)
    samples = []
    for _ in range(iterations):
        adb(serial, "shell", "input", "keyevent", "KEYCODE_HOME")
        time.sleep(settle_seconds)
        samples.append(start_once(serial, target))
        time.sleep(settle_seconds)
    return samples


def ensure_installed(serial: str | None, package: str) -> None:
    path = adb(serial, "shell", "pm", "path", package).strip()
    if not path.startswith("package:"):
        raise RuntimeError("package_not_installed: " + package)


def current_git_sha() -> str | None:
    try:
        return run(["git", "rev-parse", "HEAD"], timeout=5).strip()
    except Exception:
        return None


def self_test() -> None:
    fixture = """Status: ok
LaunchState: COLD
Activity: org.athar.app/.MainActivity
ThisTime: 123
TotalTime: 145
WaitTime: 151
Complete
"""
    parsed = parse_start_wait(fixture)
    assert parsed == {
        "this_time_ms": 123,
        "total_time_ms": 145,
        "wait_time_ms": 151,
    }
    values = [
        {"this_time_ms": 1, "total_time_ms": x, "wait_time_ms": x + 2}
        for x in [100, 120, 110, 150, 130]
    ]
    result = summary(values)
    assert result["median_total_time_ms"] == 120
    assert result["p95_total_time_ms"] == 150
    assert component("org.athar.app", ".MainActivity") == (
        "org.athar.app/org.athar.app.MainActivity"
    )
    print("PASS: startup harness parser/summary/component self-test.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial")
    parser.add_argument("--package", default=DEFAULT_PACKAGE)
    parser.add_argument("--activity", default=DEFAULT_ACTIVITY)
    parser.add_argument("--iterations", type=int, default=10)
    parser.add_argument("--settle-seconds", type=float, default=1.0)
    parser.add_argument("--state-label", default="existing-retained-state")
    parser.add_argument("--apk", type=pathlib.Path)
    parser.add_argument("--git-sha")
    parser.add_argument("--output", type=pathlib.Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        self_test()
        return 0

    if not (3 <= args.iterations <= 50):
        parser.error("--iterations must be between 3 and 50")
    if not (0.2 <= args.settle_seconds <= 10):
        parser.error("--settle-seconds must be between 0.2 and 10")
    if len(args.state_label.strip()) == 0 or len(args.state_label) > 120:
        parser.error("--state-label must be 1..120 characters")
    if args.apk is not None and not args.apk.is_file():
        parser.error("--apk must point to an existing file")

    ensure_installed(args.serial, args.package)
    target = component(args.package, args.activity)

    device = {
        "serial": args.serial or adb(None, "get-serialno").strip(),
        "manufacturer": property_value(args.serial, "ro.product.manufacturer"),
        "model": property_value(args.serial, "ro.product.model"),
        "api": int(property_value(args.serial, "ro.build.version.sdk")),
        "fingerprint": property_value(args.serial, "ro.build.fingerprint"),
    }

    cold = measure_cold(
        args.serial,
        args.package,
        target,
        args.iterations,
        args.settle_seconds,
    )
    warm = measure_warm(
        args.serial,
        target,
        args.iterations,
        args.settle_seconds,
    )

    evidence = {
        "schema_version": 1,
        "recorded_at_utc": datetime.now(timezone.utc).isoformat(),
        "measurement": {
            "variant": "benchmark_or_release_like",
            "package": args.package,
            "activity": args.activity,
            "state_label": args.state_label,
            "iterations": args.iterations,
            "settle_seconds": args.settle_seconds,
            "destructive_actions": {
                "uninstall": False,
                "clear_app_data": False,
                "change_global_settings": False,
            },
        },
        "build": {
            "git_sha": args.git_sha or current_git_sha(),
            "apk_path": str(args.apk) if args.apk else None,
            "apk_sha256": sha256_file(args.apk) if args.apk else None,
        },
        "device": device,
        "cold": {
            "samples": cold,
            "summary": summary(cold),
        },
        "warm": {
            "samples": warm,
            "summary": summary(warm),
        },
    }

    encoded = json.dumps(evidence, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded, encoding="utf-8")
        print(str(args.output))
    else:
        print(encoded, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
