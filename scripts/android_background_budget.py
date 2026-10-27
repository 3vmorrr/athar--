#!/usr/bin/env python3
"""Read-only Android background budget evidence collector for Athar.

This script never toggles Doze, changes standby buckets, changes global settings, clears app
data, kills unrelated apps, or resets batterystats/netstats. Capture naturally occurring
before/after states and compare them.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import subprocess
from datetime import datetime, timezone

PACKAGE = "org.athar.app"

UID_RE = re.compile(r"\buid:(\d+)\b")
NET_RE = re.compile(
    r"\buid=(\d+)\b.*?\brxBytes=(\d+)\b.*?\btxBytes=(\d+)\b"
)
JOB_RE = re.compile(r"JOB #")
WORK_NAME_RE = re.compile(r"athar-[A-Za-z0-9_.:-]+")


def command(args: list[str], timeout: int = 30) -> tuple[int, str]:
    result = subprocess.run(
        args,
        capture_output=True,
        text=True,
        timeout=timeout,
        check=False,
    )
    return result.returncode, (result.stdout or "") + (result.stderr or "")


def adb(serial: str | None, *args: str, timeout: int = 30) -> tuple[int, str]:
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += list(args)
    return command(cmd, timeout=timeout)


def required(serial: str | None, *args: str) -> str:
    code, output = adb(serial, *args)
    if code != 0:
        raise RuntimeError("adb_failed: " + " ".join(args) + ": " + output.strip())
    return output


def optional(serial: str | None, *args: str) -> str | None:
    code, output = adb(serial, *args)
    return output if code == 0 else None


def package_uid(serial: str | None, package: str) -> int:
    output = required(serial, "shell", "pm", "list", "packages", "-U", package)
    for line in output.splitlines():
        if package in line:
            match = UID_RE.search(line)
            if match:
                return int(match.group(1))
    raise RuntimeError("package_uid_not_found")


def property_value(serial: str | None, key: str) -> str:
    return required(serial, "shell", "getprop", key).strip()


def aggregate_network(raw: str | None, uid: int) -> dict[str, int | bool]:
    if raw is None:
        return {"available": False, "rx_bytes": 0, "tx_bytes": 0, "rows": 0}
    rx = tx = rows = 0
    for line in raw.splitlines():
        match = NET_RE.search(line)
        if match and int(match.group(1)) == uid:
            rx += int(match.group(2))
            tx += int(match.group(3))
            rows += 1
    return {
        "available": rows > 0,
        "rx_bytes": rx,
        "tx_bytes": tx,
        "rows": rows,
    }


def relevant_lines(raw: str | None, package: str, uid: int, limit: int = 250) -> list[str]:
    if raw is None:
        return []
    needle_uid = str(uid)
    selected = []
    for line in raw.splitlines():
        lower = line.lower()
        if package in line or "athar-" in lower or f",{needle_uid}," in line or f"uid={needle_uid}" in line:
            selected.append(line[:600])
            if len(selected) >= limit:
                break
    return selected


def digest(raw: str | None) -> str | None:
    if raw is None:
        return None
    return hashlib.sha256(raw.encode("utf-8", errors="replace")).hexdigest()


def idle_state(serial: str | None) -> dict[str, str | None]:
    deep = optional(serial, "shell", "dumpsys", "deviceidle", "get", "deep")
    light = optional(serial, "shell", "dumpsys", "deviceidle", "get", "light")
    return {
        "deep": deep.strip() if deep else None,
        "light": light.strip() if light else None,
    }


def capture(serial: str | None, package: str, scenario: str) -> dict:
    uid = package_uid(serial, package)
    jobs_raw = optional(serial, "shell", "dumpsys", "jobscheduler", package)
    if jobs_raw is None:
        jobs_raw = optional(serial, "shell", "dumpsys", "jobscheduler")
    net_raw = optional(serial, "shell", "dumpsys", "netstats", "detail")
    battery_raw = optional(serial, "shell", "dumpsys", "batterystats", "--checkin")
    services_raw = optional(serial, "shell", "dumpsys", "activity", "services", package)
    jobs_lines = relevant_lines(jobs_raw, package, uid)

    return {
        "schema_version": 1,
        "recorded_at_utc": datetime.now(timezone.utc).isoformat(),
        "scenario": scenario,
        "package": package,
        "uid": uid,
        "device": {
            "serial": serial or required(None, "get-serialno").strip(),
            "manufacturer": property_value(serial, "ro.product.manufacturer"),
            "model": property_value(serial, "ro.product.model"),
            "api": int(property_value(serial, "ro.build.version.sdk")),
            "fingerprint": property_value(serial, "ro.build.fingerprint"),
            "idle": idle_state(serial),
        },
        "jobs": {
            "matched_job_headers": sum(1 for line in jobs_lines if JOB_RE.search(line)),
            "athar_work_names": sorted(set(WORK_NAME_RE.findall("\n".join(jobs_lines)))),
            "relevant_lines": jobs_lines,
            "source_sha256": digest(jobs_raw),
        },
        "network": aggregate_network(net_raw, uid),
        "battery": {
            "relevant_lines": relevant_lines(battery_raw, package, uid),
            "source_sha256": digest(battery_raw),
        },
        "media_service": {
            "running": bool(services_raw and "PlaybackService" in services_raw),
            "relevant_lines": relevant_lines(services_raw, package, uid),
            "source_sha256": digest(services_raw),
        },
        "collector_guarantees": {
            "doze_toggled": False,
            "standby_bucket_changed": False,
            "global_settings_changed": False,
            "app_data_cleared": False,
            "batterystats_reset": False,
            "netstats_reset": False,
        },
    }


def compare(before: dict, after: dict) -> dict:
    if before["package"] != after["package"] or before["uid"] != after["uid"]:
        raise ValueError("snapshot_identity_mismatch")
    bnet, anet = before["network"], after["network"]
    network = {
        "available": bool(bnet["available"] and anet["available"]),
        "rx_bytes_delta": max(0, int(anet["rx_bytes"]) - int(bnet["rx_bytes"])),
        "tx_bytes_delta": max(0, int(anet["tx_bytes"]) - int(bnet["tx_bytes"])),
    }
    return {
        "schema_version": 1,
        "package": before["package"],
        "uid": before["uid"],
        "before_recorded_at_utc": before["recorded_at_utc"],
        "after_recorded_at_utc": after["recorded_at_utc"],
        "before_scenario": before["scenario"],
        "after_scenario": after["scenario"],
        "network_delta": network,
        "jobs": {
            "before_matched_job_headers": before["jobs"]["matched_job_headers"],
            "after_matched_job_headers": after["jobs"]["matched_job_headers"],
            "before_work_names": before["jobs"]["athar_work_names"],
            "after_work_names": after["jobs"]["athar_work_names"],
        },
        "media_service": {
            "before_running": before["media_service"]["running"],
            "after_running": after["media_service"]["running"],
        },
        "idle": {
            "before": before["device"]["idle"],
            "after": after["device"]["idle"],
        },
        "note": (
            "Battery checkin lines are retained as evidence only; this harness does not "
            "pretend to infer joules from OEM-specific batterystats formats."
        ),
    }


def load_json(path: pathlib.Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: pathlib.Path | None, value: dict) -> None:
    encoded = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
    if path:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(encoded, encoding="utf-8")
        print(path)
    else:
        print(encoded, end="")


def self_test() -> None:
    raw = (
        "iface=wlan0 uid=10123 set=DEFAULT rxBytes=100 txBytes=50\n"
        "iface=rmnet0 uid=10123 set=DEFAULT rxBytes=20 txBytes=10\n"
        "iface=wlan0 uid=10124 set=DEFAULT rxBytes=999 txBytes=999\n"
    )
    value = aggregate_network(raw, 10123)
    assert value["available"] is True
    assert value["rx_bytes"] == 120
    assert value["tx_bytes"] == 60

    before = {
        "package": PACKAGE,
        "uid": 10123,
        "recorded_at_utc": "a",
        "scenario": "before",
        "network": {"available": True, "rx_bytes": 100, "tx_bytes": 50},
        "jobs": {"matched_job_headers": 2, "athar_work_names": ["athar-a"]},
        "media_service": {"running": False},
        "device": {"idle": {"deep": "ACTIVE", "light": "ACTIVE"}},
    }
    after = {
        "package": PACKAGE,
        "uid": 10123,
        "recorded_at_utc": "b",
        "scenario": "after",
        "network": {"available": True, "rx_bytes": 180, "tx_bytes": 70},
        "jobs": {"matched_job_headers": 1, "athar_work_names": ["athar-b"]},
        "media_service": {"running": True},
        "device": {"idle": {"deep": "IDLE", "light": "IDLE"}},
    }
    delta = compare(before, after)
    assert delta["network_delta"]["rx_bytes_delta"] == 80
    assert delta["network_delta"]["tx_bytes_delta"] == 20
    print("PASS: background budget evidence parser self-test.")


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)

    capture_parser = sub.add_parser("capture")
    capture_parser.add_argument("--serial")
    capture_parser.add_argument("--package", default=PACKAGE)
    capture_parser.add_argument("--scenario", required=True)
    capture_parser.add_argument("--output", type=pathlib.Path, required=True)

    compare_parser = sub.add_parser("compare")
    compare_parser.add_argument("--before", type=pathlib.Path, required=True)
    compare_parser.add_argument("--after", type=pathlib.Path, required=True)
    compare_parser.add_argument("--output", type=pathlib.Path)

    sub.add_parser("self-test")
    args = parser.parse_args()

    if args.command == "self-test":
        self_test()
        return 0
    if args.command == "capture":
        if not (1 <= len(args.scenario.strip()) <= 120):
            capture_parser.error("--scenario must be 1..120 characters")
        write_json(args.output, capture(args.serial, args.package, args.scenario))
        return 0

    write_json(args.output, compare(load_json(args.before), load_json(args.after)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
