#!/usr/bin/env python3
"""Fail-closed entry-level scope check for a patch to an existing production JAR.

Example:
 python scripts/verify_jar_patch.py --before original/piri.jar --after fixed/piri.jar \
   --allow-changed jp/example/OnlyChanged.class --report patch-evidence.json

This does not establish functional correctness or authorize changes.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import zipfile


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def entries(path: Path) -> dict[str, bytes]:
    if not path.is_file():
        raise ValueError(f"JAR does not exist: {path}")
    result = {}
    try:
        with zipfile.ZipFile(path) as jar:
            corrupt = jar.testzip()
            if corrupt:
                raise ValueError(f"Bad CRC: {corrupt}")
            for info in jar.infolist():
                name = info.filename
                if name.startswith("/") or "\\" in name or any(
                    part in ("", ".", "..") for part in name.rstrip("/").split("/")
                ):
                    raise ValueError(f"Unsafe entry name: {name!r}")
                if name in result:
                    raise ValueError(f"Duplicate entry: {name}")
                if not info.is_dir():
                    result[name] = jar.read(info)
    except (zipfile.BadZipFile, OSError) as error:
        raise ValueError(f"Invalid JAR {path}: {error}") from error
    return result


def verify(before: Path, after: Path, allowed_changed: set[str], allowed_added: set[str],
           before_sha256: str | None = None, preserve_name: bool = True) -> dict:
    if preserve_name and before.name != after.name:
        raise ValueError(f"JAR filename changed: {before.name} -> {after.name}")
    baseline_hash = digest(before.read_bytes())
    if before_sha256 and baseline_hash != before_sha256.lower():
        raise ValueError(f"Baseline hash mismatch: expected {before_sha256}, got {baseline_hash}")
    old, new = entries(before), entries(after)
    removed = sorted(old.keys() - new.keys())
    added = sorted(new.keys() - old.keys())
    changed = sorted(name for name in old.keys() & new.keys() if old[name] != new[name])
    unexpected_changed = sorted(set(changed) - allowed_changed)
    unexpected_added = sorted(set(added) - allowed_added)
    if removed or unexpected_changed or unexpected_added:
        raise ValueError("OUT-OF-SCOPE JAR PATCH: " + json.dumps({
            "removed": removed, "unexpected_changed": unexpected_changed,
            "unexpected_added": unexpected_added}, ensure_ascii=False))
    if not changed and not added:
        raise ValueError("No payload change was detected")
    return {
        "baseline_filename": before.name,
        "baseline_sha256": baseline_hash,
        "output_filename": after.name,
        "output_sha256": digest(after.read_bytes()),
        "baseline_entries": len(old),
        "output_entries": len(new),
        "changed": [{"path": name, "before_sha256": digest(old[name]),
                     "after_sha256": digest(new[name])} for name in changed],
        "added": [{"path": name, "sha256": digest(new[name])} for name in added],
        "removed": [],
        "unchanged_count": len(old) - len(changed),
        "limitations": "Checks file scope/integrity only, not game behavior, UI or test results.",
    }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before", type=Path, required=True)
    parser.add_argument("--after", type=Path, required=True)
    parser.add_argument("--allow-changed", action="append", default=[])
    parser.add_argument("--allow-added", action="append", default=[])
    parser.add_argument("--before-sha256")
    parser.add_argument("--allow-rename", action="store_true")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args(argv)
    if not args.allow_changed and not args.allow_added:
        parser.error("Specify an explicit --allow-changed and/or --allow-added scope")
    try:
        evidence = verify(args.before, args.after, set(args.allow_changed), set(args.allow_added),
                          args.before_sha256, preserve_name=not args.allow_rename)
        if args.report:
            args.report.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n",
                                   encoding="utf-8")
        print(json.dumps(evidence, ensure_ascii=False, indent=2))
        return 0
    except (ValueError, OSError) as error:
        print(f"BLOCKED: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
