#!/usr/bin/env python3
"""Consent-safe hosted acceptance probe for Athar.

The probe never stores response bodies, bearer tokens, passwords, OTPs, admin cookies or
private profile fields. It records only status/timing/content-type/security-header summaries
and explicitly bounded structural checks.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import re
import socket
import ssl
import statistics
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from typing import Any

REQUEST_ID = re.compile(r"^[a-f0-9]{8,64}$", re.IGNORECASE)


def validate_https_base(value: str, *, api: bool) -> str:
    parsed = urllib.parse.urlparse(value)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
    ):
        raise ValueError("base_url_must_be_clean_https")
    normalized = value.rstrip("/") + "/"
    if api and not normalized.endswith("/api/v1/"):
        raise ValueError("api_base_must_end_in_api_v1")
    return normalized


def percentile95(values: list[float]) -> float:
    if not values:
        raise ValueError("empty_samples")
    ordered = sorted(values)
    index = max(0, int((0.95 * len(ordered) + 0.999999)) - 1)
    return ordered[index]


def latency_summary(values: list[float]) -> dict[str, float | int]:
    if not values:
        raise ValueError("empty_samples")
    return {
        "samples": len(values),
        "median_ms": round(float(statistics.median(values)), 2),
        "p95_ms": round(float(percentile95(values)), 2),
        "min_ms": round(float(min(values)), 2),
        "max_ms": round(float(max(values)), 2),
    }


def safe_headers(headers: Any) -> dict[str, Any]:
    cookies = headers.get_all("Set-Cookie") or []
    lowered = [cookie.lower() for cookie in cookies]
    return {
        "content_type": headers.get("Content-Type"),
        "cache_control": headers.get("Cache-Control"),
        "hsts_present": bool(headers.get("Strict-Transport-Security")),
        "x_content_type_options": headers.get("X-Content-Type-Options"),
        "content_security_policy_present": bool(headers.get("Content-Security-Policy")),
        "set_cookie_present": bool(cookies),
        "cookie_secure": all("secure" in cookie for cookie in lowered) if cookies else None,
        "cookie_httponly": all("httponly" in cookie for cookie in lowered) if cookies else None,
        "cookie_samesite": all("samesite=" in cookie for cookie in lowered) if cookies else None,
    }


def request_once(
    url: str,
    *,
    timeout: float,
    bearer: str | None = None,
    accept: str = "application/json",
) -> tuple[int, bytes, Any, float]:
    headers = {
        "Accept": accept,
        "User-Agent": "AtharHostedAcceptance/1",
    }
    if bearer:
        headers["Authorization"] = "Bearer " + bearer
    request = urllib.request.Request(url, headers=headers, method="GET")
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read(2 * 1024 * 1024 + 1)
            if len(body) > 2 * 1024 * 1024:
                raise ValueError("response_body_exceeds_probe_limit")
            return (
                int(response.status),
                body,
                response.headers,
                (time.perf_counter() - started) * 1000,
            )
    except urllib.error.HTTPError as failure:
        body = failure.read(256 * 1024 + 1)
        if len(body) > 256 * 1024:
            body = b""
        return (
            int(failure.code),
            body,
            failure.headers,
            (time.perf_counter() - started) * 1000,
        )


def parse_json(body: bytes) -> dict:
    decoded = json.loads(body.decode("utf-8"))
    if not isinstance(decoded, dict):
        raise ValueError("json_root_not_object")
    return decoded


def health_probe(api_base: str, samples: int, timeout: float) -> dict:
    latencies: list[float] = []
    header_summary: dict[str, Any] | None = None
    request_ids: set[str] = set()
    for _ in range(samples):
        status, body, headers, elapsed = request_once(
            urllib.parse.urljoin(api_base, "health"),
            timeout=timeout,
        )
        if status != 200:
            raise RuntimeError("health_status_" + str(status))
        payload = parse_json(body)
        data = payload.get("data")
        meta = payload.get("meta")
        if not isinstance(data, dict) or not isinstance(meta, dict):
            raise RuntimeError("health_contract_invalid")
        if data.get("api_version") != 1 or data.get("status") != "available":
            raise RuntimeError("health_contract_invalid")
        request_id = meta.get("request_id")
        if not isinstance(request_id, str) or not REQUEST_ID.fullmatch(request_id):
            raise RuntimeError("health_request_id_invalid")
        request_ids.add(request_id.lower())
        latencies.append(elapsed)
        header_summary = safe_headers(headers)
    return {
        "passed": True,
        "latency": latency_summary(latencies),
        "headers": header_summary,
        "unique_request_ids": len(request_ids),
    }


def page_probe(url: str, timeout: float) -> dict:
    status, body, headers, elapsed = request_once(
        url,
        timeout=timeout,
        accept="text/html,application/xhtml+xml",
    )
    if status != 200:
        raise RuntimeError("page_status_" + str(status))
    content_type = (headers.get("Content-Type") or "").lower()
    if "text/html" not in content_type:
        raise RuntimeError("page_not_html")
    if len(body) == 0:
        raise RuntimeError("page_empty")
    return {
        "passed": True,
        "status": status,
        "latency_ms": round(elapsed, 2),
        "headers": safe_headers(headers),
        "body_bytes": len(body),
    }


def authenticated_probe(
    api_base: str,
    bearer: str | None,
    timeout: float,
) -> dict:
    if not bearer:
        return {
            "status": "SKIPPED_NO_AUTHORIZED_BEARER",
            "token_recorded": False,
        }
    checks = [
        "me/profile",
        "me/notification-preferences",
        "me/notification-inbox?per_page=1",
    ]
    results: list[dict[str, Any]] = []
    for relative in checks:
        status, body, headers, elapsed = request_once(
            urllib.parse.urljoin(api_base, relative),
            timeout=timeout,
            bearer=bearer,
        )
        if status != 200:
            raise RuntimeError("authenticated_check_failed_" + relative + "_" + str(status))
        payload = parse_json(body)
        if "data" not in payload or "meta" not in payload:
            raise RuntimeError("authenticated_contract_invalid_" + relative)
        results.append({
            "path": relative.split("?")[0],
            "status": status,
            "latency_ms": round(elapsed, 2),
            "content_type": headers.get("Content-Type"),
            "response_body_recorded": False,
        })
    return {
        "status": "PASSED",
        "token_recorded": False,
        "checks": results,
    }


def tls_summary(base: str, timeout: float) -> dict:
    parsed = urllib.parse.urlparse(base)
    host = parsed.hostname
    if not host:
        raise ValueError("tls_host_missing")
    port = parsed.port or 443
    context = ssl.create_default_context()
    with socket.create_connection((host, port), timeout=timeout) as raw:
        with context.wrap_socket(raw, server_hostname=host) as wrapped:
            certificate_binary = wrapped.getpeercert(binary_form=True)
            certificate = wrapped.getpeercert()
            cipher = wrapped.cipher()
    return {
        "host": host,
        "port": port,
        "certificate_sha256": hashlib.sha256(certificate_binary).hexdigest(),
        "not_after": certificate.get("notAfter"),
        "protocol": wrapped.version() if False else None,
        "cipher": cipher[0] if cipher else None,
        "hostname_verified": True,
    }


def run_adb(serial: str | None, *args: str) -> str:
    command = ["adb"]
    if serial:
        command += ["-s", serial]
    command += list(args)
    result = subprocess.run(
        command,
        capture_output=True,
        text=True,
        check=False,
        timeout=30,
    )
    if result.returncode != 0:
        raise RuntimeError("adb_failed: " + " ".join(args))
    return result.stdout


def prop(serial: str | None, name: str) -> str:
    return run_adb(serial, "shell", "getprop", name).strip()


def device_evidence(serial: str | None, package: str) -> dict:
    package_dump = run_adb(serial, "shell", "dumpsys", "package", package)
    if ("Package [" + package + "]") not in package_dump and ("Package{" not in package_dump):
        raise RuntimeError("package_not_installed")
    version_name_match = re.search(r"\bversionName=([^\s]+)", package_dump)
    version_code_match = re.search(r"\bversionCode=(\d+)", package_dump)
    installer_match = re.search(r"\binstallerPackageName=([^\s]+)", package_dump)
    return {
        "schema_version": 1,
        "recorded_at_utc": datetime.now(timezone.utc).isoformat(),
        "package": package,
        "version_name": version_name_match.group(1) if version_name_match else None,
        "version_code": int(version_code_match.group(1)) if version_code_match else None,
        "installer_package": installer_match.group(1) if installer_match else None,
        "device": {
            "serial": serial or run_adb(None, "get-serialno").strip(),
            "manufacturer": prop(serial, "ro.product.manufacturer"),
            "model": prop(serial, "ro.product.model"),
            "api": int(prop(serial, "ro.build.version.sdk")),
            "fingerprint": prop(serial, "ro.build.fingerprint"),
        },
        "private_app_data_recorded": False,
        "device_settings_changed": False,
    }


def write_json(path: pathlib.Path | None, value: dict) -> None:
    encoded = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
    if path:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(encoded, encoding="utf-8")
        print(path)
    else:
        print(encoded, end="")


def self_test() -> None:
    assert validate_https_base("https://example.org/api/v1/", api=True) == (
        "https://example.org/api/v1/"
    )
    assert validate_https_base("https://example.org", api=False) == "https://example.org/"
    assert latency_summary([10.0, 20.0, 30.0, 40.0, 50.0])["median_ms"] == 30.0
    try:
        validate_https_base("http://example.org/api/v1/", api=True)
        raise AssertionError("HTTP base accepted")
    except ValueError:
        pass
    fake = {
        "Content-Type": "text/html; charset=utf-8",
        "Cache-Control": "no-store",
        "Strict-Transport-Security": "max-age=31536000",
    }
    class Headers:
        def get(self, key: str):
            return fake.get(key)
        def get_all(self, key: str):
            if key == "Set-Cookie":
                return ["sid=redacted; Secure; HttpOnly; SameSite=Strict"]
            return []
    safe = safe_headers(Headers())
    assert safe["set_cookie_present"] is True
    assert "sid" not in json.dumps(safe)
    print("PASS: hosted acceptance consent-safe parser self-test.")


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)

    probe = sub.add_parser("probe")
    probe.add_argument("--api-base", required=True)
    probe.add_argument("--site-base", required=True)
    probe.add_argument("--samples", type=int, default=5)
    probe.add_argument("--timeout", type=float, default=10.0)
    probe.add_argument(
        "--bearer-env",
        default="ATHAR_HOSTED_BEARER_TOKEN",
        help="Environment variable containing an authorized test-user bearer token.",
    )
    probe.add_argument("--output", type=pathlib.Path)

    device = sub.add_parser("device")
    device.add_argument("--serial")
    device.add_argument("--package", default="org.athar.app")
    device.add_argument("--output", type=pathlib.Path)

    sub.add_parser("self-test")
    args = parser.parse_args()

    if args.command == "self-test":
        self_test()
        return 0

    if args.command == "device":
        write_json(args.output, device_evidence(args.serial, args.package))
        return 0

    if not (3 <= args.samples <= 30):
        probe.error("--samples must be between 3 and 30")
    if not (1.0 <= args.timeout <= 30.0):
        probe.error("--timeout must be between 1 and 30 seconds")

    api_base = validate_https_base(args.api_base, api=True)
    site_base = validate_https_base(args.site_base, api=False)
    bearer = os.environ.get(args.bearer_env)
    evidence = {
        "schema_version": 1,
        "recorded_at_utc": datetime.now(timezone.utc).isoformat(),
        "api_origin": urllib.parse.urlparse(api_base).netloc,
        "site_origin": urllib.parse.urlparse(site_base).netloc,
        "tls": {
            "api": tls_summary(api_base, args.timeout),
            "site": tls_summary(site_base, args.timeout),
        },
        "public": {
            "health": health_probe(api_base, args.samples, args.timeout),
            "site_home": page_probe(site_base, args.timeout),
            "admin_login": page_probe(
                urllib.parse.urljoin(site_base, "admin/login"),
                args.timeout,
            ),
        },
        "authenticated_api": authenticated_probe(api_base, bearer, args.timeout),
        "privacy": {
            "bearer_token_recorded": False,
            "password_recorded": False,
            "otp_recorded": False,
            "admin_cookie_value_recorded": False,
            "response_bodies_recorded": False,
        },
    }
    write_json(args.output, evidence)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
