"""Check publication documents without executing examples or contacting a device."""

import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
FILES = {
    ".gitignore", "LICENSE", "README.md", "SKILL.md",
    "references/adb-commands.md", "references/troubleshooting.md",
    "docs/droidclaw-notes.md", "docs/testing.md", "scripts/check_docs.py",
}
# Heuristics only: public URLs and ~/ installation templates are intentional.
PRIVATE_PATTERNS = [
    r"\b(?:\d{1,3}\.){3}\d{1,3}\b",
    r"[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}",
    r"/(?:Users|home)/[A-Za-z0-9_.-]+/",
    r"\bgh[pousr]_[A-Za-z0-9]{20,}",
    r"\bgithub_pat_[A-Za-z0-9_]{20,}",
    r"\bsk-[A-Za-z0-9_-]{20,}",
    r"-----BEGIN [A-Z ]*PRIVATE KEY-----",
]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def check():
    for name in FILES:
        path = ROOT / name
        require(path.is_file() and not path.is_symlink(), f"{name}: missing or symlinked file")
        require(path.resolve().is_relative_to(ROOT), f"{name}: path escapes repository")
    result = subprocess.run(
        ["git", "-C", str(ROOT), "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        check=True, capture_output=True, text=True,
    )
    require(set(result.stdout.rstrip("\0").split("\0")) == FILES, "Publication file allowlist mismatch")

    skill = (ROOT / "SKILL.md").read_text()
    header = re.match(r"\A---\n(.*?)\n---\n", skill, re.S)
    require(header is not None, "SKILL.md: missing frontmatter")
    fields = dict(re.findall(r"^([a-z-]+): (.+)$", header[1], re.M))
    for field, limit in (("name", 64), ("description", 1024), ("compatibility", 500)):
        require(0 < len(fields.get(field, "")) <= limit, f"SKILL.md: invalid {field}")
    require(fields["name"] == ROOT.name == "android-adb-control", "SKILL.md: name mismatch")
    require(fields.get("license") == "MIT", "SKILL.md: license must be MIT")
    license_text = (ROOT / "LICENSE").read_text()
    require("MIT License" in license_text and "范凯 and Agent (Friday)" in license_text,
            "LICENSE: missing license title or author attribution")

    links = blocks = 0
    env = {key: value for key, value in os.environ.items() if key not in {"BASH_ENV", "ENV"}}
    for name in sorted(FILES):
        path = ROOT / name
        content = path.read_text()
        require(content.endswith("\n"), f"{name}: missing final newline")
        for pattern in PRIVATE_PATTERNS:
            require(not re.search(pattern, content), f"{name}: possible private data; inspect locally")
        if path.suffix != ".md":
            continue
        require(sum(line.startswith("```") for line in content.splitlines()) % 2 == 0,
                f"{name}: unpaired code fence")
        for target in re.findall(r"\]\(([^)]+)\)", content):
            if target.startswith(("https://", "http://", "#")):
                continue
            destination = (path.parent / target.split("#", 1)[0]).resolve()
            require(destination.is_relative_to(ROOT) and destination.is_file(),
                    f"{name}: broken or escaping local link")
            links += 1
        for block in re.findall(r"```bash\n(.*?)\n```", content, re.S):
            parsed = subprocess.run(["bash", "--noprofile", "--norc", "-n"], input=block,
                                    text=True, capture_output=True, env=env, timeout=10)
            require(parsed.returncode == 0, f"{name}: Bash syntax error")
            blocks += 1
            for line in block.splitlines():
                for match in re.finditer(r"\badb[ \t]+", line):
                    command = line[match.start():]
                    require(command.startswith('adb -s "$ANDROID_SERIAL" ') or
                            re.match(r"adb (?:version|devices|pair|connect|disconnect)(?:\s|$)", command),
                            f"{name}: unscoped device command")
                    if re.search(r"\binstall\b|shell (?:ime|settings|am broadcast)\b", command):
                        require('--user "$ANDROID_USER_ID"' in command,
                                f"{name}: setup command missing explicit Android user")
    print(f"PASS: {len(FILES)} files, {links} local links, {blocks} Bash blocks; "
          "metadata, target/user scoping, and privacy heuristics checked.")
    print("No ADB commands executed. Device behavior and external links are not tested.")


if __name__ == "__main__":
    try:
        check()
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
