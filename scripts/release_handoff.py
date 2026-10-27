#!/usr/bin/env python3
"""Build a reviewable Athar release handoff from tracked source and verified artifacts.

This script refuses dirty source, packages server/Admin runtime source with git archive,
verifies APK/AAB through android_release_artifacts.py, rejects prohibited secret-like entries,
and emits checksums plus a deterministic outer handoff ZIP.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from datetime import datetime, timezone

SEMVER = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?$")
HEX40 = re.compile(r"^[0-9a-f]{40}$")
HEX64 = re.compile(r"^[0-9a-f]{64}$")
SERVER_PATHS = [
    ".env.example",
    "README.md",
    "admin",
    "app",
    "backend",
    "bin",
    "database",
    "public",
    "shared",
]
GUIDES = [
    "docs/deployment/ANDROID_RELEASE.md",
    "docs/deployment/BACKUP_AND_RECOVERY.md",
    "docs/deployment/DEPLOYMENT_GUIDE.md",
    "docs/deployment/ENVIRONMENT_VARIABLES.md",
    "docs/deployment/HOSTED_ACCEPTANCE.md",
    "docs/deployment/RELEASE_CHECKLIST.md",
    "docs/operations/USER_GUIDE.md",
    "docs/operations/ADMIN_GUIDE.md",
    "docs/operations/MAINTENANCE_RUNBOOK.md",
    "docs/operations/KNOWN_LIMITS.md",
]
PROHIBITED = [
    re.compile(r"(^|/)\.env$", re.IGNORECASE),
    re.compile(r"(^|/)config/local\.php$", re.IGNORECASE),
    re.compile(r"(^|/)local\.properties$", re.IGNORECASE),
    re.compile(r"\.(jks|keystore|p12|pfx|pem|key)$", re.IGNORECASE),
    re.compile(r"google-services\.json$", re.IGNORECASE),
    re.compile(r"service-account.*\.json$", re.IGNORECASE),
    re.compile(r"(^|/)storage/", re.IGNORECASE),
]


def run(args: list[str], *, cwd: pathlib.Path, timeout: int = 300) -> str:
    result = subprocess.run(
        args,
        cwd=cwd,
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


def require_clean_commit(root: pathlib.Path, requested: str) -> str:
    sha = run(["git", "rev-parse", requested], cwd=root).strip().lower()
    if not HEX40.fullmatch(sha):
        raise ValueError("git_sha_invalid")
    head = run(["git", "rev-parse", "HEAD"], cwd=root).strip().lower()
    if head != sha:
        raise ValueError("requested_git_sha_is_not_current_head")
    status = run(
        ["git", "status", "--porcelain", "--untracked-files=all"],
        cwd=root,
    )
    if status.strip():
        raise ValueError("release_source_worktree_not_clean")
    return sha


def prohibited_name(name: str) -> bool:
    normalized = name.replace("\\", "/")
    if normalized.endswith("/.env.example") or normalized == ".env.example":
        return False
    return any(pattern.search(normalized) for pattern in PROHIBITED)


def audit_zip_names(path: pathlib.Path) -> None:
    with zipfile.ZipFile(path, "r") as archive:
        bad = sorted(name for name in archive.namelist() if prohibited_name(name))
    if bad:
        raise ValueError("prohibited_release_entries:" + ",".join(bad[:10]))


def server_archive(root: pathlib.Path, sha: str, output: pathlib.Path) -> None:
    command = [
        "git",
        "archive",
        "--format=zip",
        "--prefix=athar-server/",
        "-o",
        str(output),
        sha,
        *SERVER_PATHS,
    ]
    run(command, cwd=root)
    audit_zip_names(output)


def copy_verified_android(
    root: pathlib.Path,
    apk: pathlib.Path,
    aab: pathlib.Path,
    version_code: int,
    version_name: str,
    cert_sha256: str | None,
    staging: pathlib.Path,
) -> dict:
    verifier = root / "scripts" / "android_release_artifacts.py"
    evidence = staging / "android-release-verification.json"
    command = [
        sys.executable,
        str(verifier),
        "verify",
        "--apk",
        str(apk),
        "--aab",
        str(aab),
        "--version-code",
        str(version_code),
        "--version-name",
        version_name,
        "--output",
        str(evidence),
    ]
    if cert_sha256:
        command += ["--cert-sha256", cert_sha256]
    run(command, cwd=root, timeout=600)

    apk_target = staging / (
        "athar-android-" + version_name + "-vc" + str(version_code) + ".apk"
    )
    aab_target = staging / (
        "athar-android-" + version_name + "-vc" + str(version_code) + ".aab"
    )
    shutil.copy2(apk, apk_target)
    shutil.copy2(aab, aab_target)
    return {
        "apk": apk_target,
        "aab": aab_target,
        "verification": evidence,
    }


def copy_guides(root: pathlib.Path, staging: pathlib.Path) -> list[pathlib.Path]:
    target_root = staging / "docs"
    copied: list[pathlib.Path] = []
    for relative in GUIDES:
        source = root / relative
        if not source.is_file():
            raise FileNotFoundError("missing_handoff_guide:" + relative)
        target = target_root / pathlib.Path(relative).name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        copied.append(target)
    return copied


def blocked_tasks(root: pathlib.Path) -> list[dict[str, str]]:
    queue = json.loads(
        (root / "docs" / "progress" / "TASK_QUEUE.json").read_text(encoding="utf-8")
    )
    return [
        {"id": task["id"], "name": task["name"]}
        for task in queue.get("tasks", [])
        if task.get("state") == "BLOCKED"
    ]


def relative_artifact(path: pathlib.Path, staging: pathlib.Path) -> dict:
    return {
        "name": path.relative_to(staging).as_posix(),
        "size_bytes": path.stat().st_size,
        "sha256": sha256_file(path),
    }


def write_manifest(
    root: pathlib.Path,
    staging: pathlib.Path,
    sha: str,
    version_code: int,
    version_name: str,
    cert_sha256: str | None,
) -> pathlib.Path:
    files = sorted(
        (
            path
            for path in staging.rglob("*")
            if path.is_file()
            and path.name not in {"release-manifest.json", "SHA256SUMS.txt"}
        ),
        key=lambda value: value.relative_to(staging).as_posix(),
    )
    manifest = {
        "schema_version": 1,
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "source": {
            "git_sha": sha,
            "clean_worktree_required": True,
        },
        "android": {
            "application_id": "org.athar.app",
            "version_code": version_code,
            "version_name": version_name,
            "signer_certificate_sha256": cert_sha256,
        },
        "server": {
            "source": "tracked git archive only",
            "paths": SERVER_PATHS,
        },
        "blocked_external_or_physical_gates_at_packaging_time": blocked_tasks(root),
        "artifacts": [relative_artifact(path, staging) for path in files],
        "secrets_included": False,
    }
    path = staging / "release-manifest.json"
    path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return path


def write_checksums(staging: pathlib.Path) -> pathlib.Path:
    files = sorted(
        (
            path
            for path in staging.rglob("*")
            if path.is_file() and path.name != "SHA256SUMS.txt"
        ),
        key=lambda value: value.relative_to(staging).as_posix(),
    )
    lines = [
        sha256_file(path) + "  " + path.relative_to(staging).as_posix()
        for path in files
    ]
    target = staging / "SHA256SUMS.txt"
    target.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return target


def deterministic_handoff_zip(staging: pathlib.Path, output: pathlib.Path) -> None:
    files = sorted(
        (path for path in staging.rglob("*") if path.is_file()),
        key=lambda value: value.relative_to(staging).as_posix(),
    )
    with zipfile.ZipFile(
        output,
        "w",
        compression=zipfile.ZIP_DEFLATED,
        compresslevel=9,
    ) as archive:
        for path in files:
            relative = path.relative_to(staging).as_posix()
            info = zipfile.ZipInfo("athar-release-handoff/" + relative)
            info.date_time = (1980, 1, 1, 0, 0, 0)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, path.read_bytes())
    audit_zip_names(output)


def self_test() -> None:
    assert prohibited_name("athar-server/config/local.php")
    assert prohibited_name("athar-server/release.jks")
    assert not prohibited_name("athar-server/.env.example")
    with tempfile.TemporaryDirectory() as raw:
        root = pathlib.Path(raw)
        staging = root / "stage"
        staging.mkdir()
        (staging / "a.txt").write_text("a", encoding="utf-8")
        first = root / "first.zip"
        second = root / "second.zip"
        deterministic_handoff_zip(staging, first)
        deterministic_handoff_zip(staging, second)
        assert sha256_file(first) == sha256_file(second)
    print("PASS: release handoff deterministic packaging self-test.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--repo-root", type=pathlib.Path, default=pathlib.Path("."))
    parser.add_argument("--git-sha", default="HEAD")
    parser.add_argument("--apk", type=pathlib.Path)
    parser.add_argument("--aab", type=pathlib.Path)
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--version-name")
    parser.add_argument("--cert-sha256")
    parser.add_argument("--output-dir", type=pathlib.Path)
    args = parser.parse_args()

    if args.self_test:
        self_test()
        return 0

    root = args.repo_root.resolve()
    if not (root / ".git").exists():
        parser.error("--repo-root must be a Git working tree")
    if args.apk is None or args.aab is None:
        parser.error("--apk and --aab are required")
    if args.version_code is None or args.version_code <= 0:
        parser.error("--version-code must be positive")
    if args.version_name is None or not SEMVER.fullmatch(args.version_name):
        parser.error("--version-name must be semantic-version form")
    if args.cert_sha256:
        normalized = args.cert_sha256.replace(":", "").lower()
        if not HEX64.fullmatch(normalized):
            parser.error("--cert-sha256 must be a SHA-256 fingerprint")
        args.cert_sha256 = normalized
    if args.output_dir is None:
        parser.error("--output-dir is required")

    sha = require_clean_commit(root, args.git_sha)
    output_root = args.output_dir.resolve()
    final_dir = output_root / (
        "athar-" + args.version_name + "-" + sha[:12]
    )
    final_zip = output_root / (
        "athar-" + args.version_name + "-" + sha[:12] + "-handoff.zip"
    )
    if final_dir.exists() or final_zip.exists():
        raise FileExistsError("release_handoff_output_already_exists")

    with tempfile.TemporaryDirectory(prefix="athar-release-") as raw:
        staging = pathlib.Path(raw) / "handoff"
        staging.mkdir(parents=True)

        android = copy_verified_android(
            root,
            args.apk.resolve(),
            args.aab.resolve(),
            args.version_code,
            args.version_name,
            args.cert_sha256,
            staging,
        )
        server = staging / (
            "athar-server-" + args.version_name + "-" + sha[:12] + ".zip"
        )
        server_archive(root, sha, server)
        copy_guides(root, staging)
        write_manifest(
            root,
            staging,
            sha,
            args.version_code,
            args.version_name,
            args.cert_sha256,
        )
        write_checksums(staging)

        output_root.mkdir(parents=True, exist_ok=True)
        shutil.copytree(staging, final_dir)
        deterministic_handoff_zip(staging, final_zip)

    print(
        json.dumps(
            {
                "directory": str(final_dir),
                "handoff_zip": str(final_zip),
                "handoff_zip_sha256": sha256_file(final_zip),
                "git_sha": sha,
                "version_code": args.version_code,
                "version_name": args.version_name,
                "secrets_included": False,
            },
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
