#!/usr/bin/env python3
"""Verify Athar Android release artifacts without reading signing secrets.

The script verifies:
- APK signature/certificate using apksigner;
- AAB JAR signature using jarsigner;
- package/version/debuggable fields using apkanalyzer;
- canonical ZIP payload digests for reproducibility comparison.

Canonical digests intentionally exclude signature metadata. Signed-container byte equality is
reported separately when two artifacts are compared, but is not required because JAR/signature
container metadata may differ between valid signing toolchains.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import zipfile
from datetime import datetime, timezone

SIGNATURE_ENTRY = re.compile(
    r"^META-INF/[^/]+\.(SF|RSA|DSA|EC)$|^META-INF/MANIFEST\.MF$",
    re.IGNORECASE,
)
HEX64 = re.compile(r"^[0-9a-f]{64}$")
SEMVER = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?$")


def run(args: list[str], timeout: int = 120) -> str:
    result = subprocess.run(
        args,
        capture_output=True,
        text=True,
        check=False,
        timeout=timeout,
    )
    output = (result.stdout or "") + (result.stderr or "")
    if result.returncode != 0:
        raise RuntimeError("command_failed: " + " ".join(args) + "\n" + output.strip())
    return output


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def canonical_zip_digest(path: pathlib.Path) -> dict:
    digest = hashlib.sha256()
    entries = 0
    total_uncompressed = 0
    with zipfile.ZipFile(path, "r") as archive:
        names = sorted(
            info.filename
            for info in archive.infolist()
            if not info.is_dir() and not SIGNATURE_ENTRY.match(info.filename)
        )
        if not names:
            raise ValueError("artifact_has_no_payload_entries")
        for name in names:
            data = archive.read(name)
            name_bytes = name.encode("utf-8")
            digest.update(len(name_bytes).to_bytes(4, "big"))
            digest.update(name_bytes)
            digest.update(len(data).to_bytes(8, "big"))
            digest.update(hashlib.sha256(data).digest())
            entries += 1
            total_uncompressed += len(data)
    return {
        "sha256": digest.hexdigest(),
        "entries": entries,
        "uncompressed_bytes": total_uncompressed,
    }


def sdk_tool(name: str) -> str:
    direct = shutil.which(name)
    if direct:
        return direct

    roots = [
        os.environ.get("ANDROID_SDK_ROOT"),
        os.environ.get("ANDROID_HOME"),
    ]
    candidates: list[pathlib.Path] = []
    for raw in roots:
        if not raw:
            continue
        root = pathlib.Path(raw)
        if name in {"apkanalyzer", "android"}:
            candidates.extend([
                root / "cmdline-tools" / "latest" / "bin" / name,
                root / "tools" / "bin" / name,
            ])
        else:
            build_tools = root / "build-tools"
            if build_tools.is_dir():
                candidates.extend(
                    sorted(
                        (directory / name for directory in build_tools.iterdir()),
                        reverse=True,
                    )
                )
    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)
    raise RuntimeError("tool_not_found: " + name)


def jarsigner_tool() -> str:
    direct = shutil.which("jarsigner")
    if direct:
        return direct
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = pathlib.Path(java_home) / "bin" / "jarsigner"
        if candidate.is_file():
            return str(candidate)
    raise RuntimeError("tool_not_found: jarsigner")


def android_cli_metadata(artifact: pathlib.Path) -> dict:
    tool = sdk_tool("android")
    raw = run([
        tool,
        "analyze",
        "--analysis=basic",
        "--format=json",
        str(artifact),
    ])
    root = json.loads(raw)
    results = root.get("results")
    if not isinstance(results, list) or len(results) != 1:
        raise RuntimeError("android_analyze_result_shape_invalid")
    app_info = (
        results[0].get("basic", {})
        .get("appInfo", {})
    )
    package_name = app_info.get("packageName")
    version_name = app_info.get("versionName")
    version_code = app_info.get("versionCode")
    if not isinstance(package_name, str) or not package_name:
        raise RuntimeError("android_analyze_package_missing")
    if not isinstance(version_name, str) or not version_name:
        raise RuntimeError("android_analyze_version_name_missing")
    try:
        parsed_version_code = int(version_code)
    except (TypeError, ValueError) as failure:
        raise RuntimeError("android_analyze_version_code_invalid") from failure
    return {
        "application_id": package_name,
        "version_code": parsed_version_code,
        "version_name": version_name,
    }


def apk_debuggable(apk: pathlib.Path) -> bool:
    tool = sdk_tool("apkanalyzer")
    value = run([tool, "manifest", "debuggable", str(apk)]).strip().lower()
    return value in {"true", "1", "yes"}


def apk_signer_certificate(apk: pathlib.Path) -> dict:
    tool = sdk_tool("apksigner")
    output = run([tool, "verify", "--verbose", "--print-certs", str(apk)])
    digest_match = re.search(
        r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F:]+)",
        output,
    )
    if not digest_match:
        raise RuntimeError("apk_signer_certificate_digest_missing")
    digest = digest_match.group(1).replace(":", "").lower()
    if not HEX64.fullmatch(digest):
        raise RuntimeError("apk_signer_certificate_digest_invalid")
    return {
        "certificate_sha256": digest,
        "verified_v1": "Verified using v1 scheme (JAR signing): true" in output,
        "verified_v2": "Verified using v2 scheme (APK Signature Scheme v2): true" in output,
        "verified_v3": "Verified using v3 scheme (APK Signature Scheme v3): true" in output,
        "verified_v4": "Verified using v4 scheme (APK Signature Scheme v4): true" in output,
    }


def verify_aab_signature(aab: pathlib.Path) -> dict:
    output = run([jarsigner_tool(), "-verify", "-strict", "-certs", str(aab)])
    lower = output.lower()
    if "jar verified" not in lower:
        raise RuntimeError("aab_signature_not_verified")
    return {"jar_signature_verified": True}


def validate_expected(
    metadata: dict,
    expected_package: str,
    expected_version_code: int,
    expected_version_name: str,
) -> None:
    if metadata["application_id"] != expected_package:
        raise ValueError("application_id_mismatch")
    if metadata["version_code"] != expected_version_code:
        raise ValueError("version_code_mismatch")
    if metadata["version_name"] != expected_version_name:
        raise ValueError("version_name_mismatch")
    if metadata.get("debuggable") is True:
        raise ValueError("release_artifact_is_debuggable")


def verify_artifacts(
    apk: pathlib.Path,
    aab: pathlib.Path,
    expected_package: str,
    expected_version_code: int,
    expected_version_name: str,
    expected_cert_sha256: str | None,
) -> dict:
    if not apk.is_file() or not aab.is_file():
        raise FileNotFoundError("APK and AAB must both exist.")
    if expected_version_code <= 0:
        raise ValueError("expected_version_code_invalid")
    if not SEMVER.fullmatch(expected_version_name):
        raise ValueError("expected_version_name_invalid")

    apk_metadata = android_cli_metadata(apk)
    apk_metadata["debuggable"] = apk_debuggable(apk)
    aab_metadata = android_cli_metadata(aab)
    validate_expected(
        apk_metadata,
        expected_package,
        expected_version_code,
        expected_version_name,
    )
    validate_expected(
        aab_metadata,
        expected_package,
        expected_version_code,
        expected_version_name,
    )
    if {
        key: apk_metadata[key]
        for key in ("application_id", "version_code", "version_name")
    } != aab_metadata:
        raise ValueError("apk_aab_manifest_metadata_mismatch")

    signer = apk_signer_certificate(apk)
    if expected_cert_sha256 is not None:
        expected = expected_cert_sha256.replace(":", "").lower()
        if not HEX64.fullmatch(expected):
            raise ValueError("expected_cert_sha256_invalid")
        if signer["certificate_sha256"] != expected:
            raise ValueError("signer_certificate_mismatch")

    return {
        "schema_version": 1,
        "verified_at_utc": datetime.now(timezone.utc).isoformat(),
        "metadata": {
            "apk": apk_metadata,
            "aab": aab_metadata,
        },
        "apk": {
            "path": str(apk),
            "sha256": sha256_file(apk),
            "canonical_payload": canonical_zip_digest(apk),
            "signing": signer,
        },
        "aab": {
            "path": str(aab),
            "sha256": sha256_file(aab),
            "canonical_payload": canonical_zip_digest(aab),
            "signing": verify_aab_signature(aab),
        },
    }


def compare_artifacts(first: pathlib.Path, second: pathlib.Path) -> dict:
    if not first.is_file() or not second.is_file():
        raise FileNotFoundError("Both artifacts must exist.")
    first_payload = canonical_zip_digest(first)
    second_payload = canonical_zip_digest(second)
    return {
        "schema_version": 1,
        "first": {
            "path": str(first),
            "sha256": sha256_file(first),
            "canonical_payload": first_payload,
        },
        "second": {
            "path": str(second),
            "sha256": sha256_file(second),
            "canonical_payload": second_payload,
        },
        "raw_bytes_equal": sha256_file(first) == sha256_file(second),
        "canonical_payload_equal": first_payload["sha256"] == second_payload["sha256"],
    }


def write_json(output: pathlib.Path | None, value: dict) -> None:
    encoded = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
    if output:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(encoded, encoding="utf-8")
        print(output)
    else:
        print(encoded, end="")


def self_test() -> None:
    import tempfile

    with tempfile.TemporaryDirectory() as raw:
        root = pathlib.Path(raw)
        first = root / "first.apk"
        second = root / "second.apk"
        with zipfile.ZipFile(first, "w") as archive:
            archive.writestr("classes.dex", b"dex")
            archive.writestr("res/a.txt", b"a")
            archive.writestr("META-INF/SIGNER.SF", b"signature-one")
        with zipfile.ZipFile(second, "w") as archive:
            archive.writestr("META-INF/SIGNER.SF", b"signature-two")
            archive.writestr("res/a.txt", b"a")
            archive.writestr("classes.dex", b"dex")

        compared = compare_artifacts(first, second)
        assert compared["raw_bytes_equal"] is False
        assert compared["canonical_payload_equal"] is True

        with zipfile.ZipFile(second, "a") as archive:
            archive.writestr("assets/change.txt", b"changed")
        changed = compare_artifacts(first, second)
        assert changed["canonical_payload_equal"] is False

    assert SEMVER.fullmatch("1.2.3")
    assert SEMVER.fullmatch("1.2.3-rc.1")
    assert not SEMVER.fullmatch("1.2")
    print("PASS: Android release artifact verifier self-test.")


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)

    verify = sub.add_parser("verify")
    verify.add_argument("--apk", type=pathlib.Path, required=True)
    verify.add_argument("--aab", type=pathlib.Path, required=True)
    verify.add_argument("--package", default="org.athar.app")
    verify.add_argument("--version-code", type=int, required=True)
    verify.add_argument("--version-name", required=True)
    verify.add_argument("--cert-sha256")
    verify.add_argument("--output", type=pathlib.Path)

    compare = sub.add_parser("compare")
    compare.add_argument("--first", type=pathlib.Path, required=True)
    compare.add_argument("--second", type=pathlib.Path, required=True)
    compare.add_argument("--output", type=pathlib.Path)

    sub.add_parser("self-test")
    args = parser.parse_args()

    if args.command == "self-test":
        self_test()
        return 0
    if args.command == "compare":
        write_json(args.output, compare_artifacts(args.first, args.second))
        return 0

    write_json(
        args.output,
        verify_artifacts(
            apk=args.apk,
            aab=args.aab,
            expected_package=args.package,
            expected_version_code=args.version_code,
            expected_version_name=args.version_name,
            expected_cert_sha256=args.cert_sha256,
        ),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
