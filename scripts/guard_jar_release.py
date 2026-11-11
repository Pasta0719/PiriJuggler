#!/usr/bin/env python3
"""Fail-closed release gate for approved PiriJuggler Paper + Fabric JAR pairs.

Inputs: immutable, user-provided baseline ZIP hashes and a narrow, reviewed list
of class files allowed to change. EVERYTHING ELSE must retain both exact ZIP
local-record bytes (metadata, header and compressed payload) and central record
attributes. Merely passing Java tests cannot substitute for this check.

The policy is pinned to an approved baseline and must not be regenerated
implicitly. Changes require explicit user authorization and review.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
import zipfile
from pathlib import Path

DEFAULT_POLICY = Path(__file__).resolve().parent.parent / "release-policies/hyperdog-20261111.json"


class RejectedRelease(Exception):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RejectedRelease(message)


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def names_and_infos(archive: zipfile.ZipFile) -> dict[str, zipfile.ZipInfo]:
    infos = archive.infolist()
    names = [item.filename for item in infos]
    require(len(names) == len(set(names)), "duplicate ZIP entry names are prohibited")
    require(all(not info.flag_bits & 1 for info in infos), "encrypted ZIP entries prohibited")
    require(all(name and not name.startswith("/") and ".." not in name.split("/")
                for name in names), "unsafe ZIP entry path")
    failed = archive.testzip()
    require(failed is None, f"invalid JAR member CRC: {failed}")
    return {i.filename: i for i in infos}


def raw_local_record(archive: zipfile.ZipFile, info: zipfile.ZipInfo) -> bytes:
    """Read the original local header PLUS original compressed bytes, not re-zipped data."""
    stream = archive.fp
    require(stream is not None, "JAR stream closed")
    stream.seek(info.header_offset)
    head = stream.read(30)
    require(len(head) == 30, f"truncated ZIP local header: {info.filename}")
    (signature, _ver, _flags, _comp, _time, _date, _crc, _csize, _usize,
     name_len, extra_len) = struct.unpack("<IHHHHHIIIHH", head)
    require(signature == 0x04034B50, f"bad ZIP local header: {info.filename}")
    payload = stream.read(name_len + extra_len + info.compress_size)
    require(len(payload) == name_len + extra_len + info.compress_size,
            f"truncated ZIP payload: {info.filename}")
    return head + payload


def central_metadata(info: zipfile.ZipInfo) -> tuple:
    # Offset/order can move when approved class members change. Everything else
    # belonging to an unchanged entry remains immutable.
    return (info.CRC, info.file_size, info.compress_size, info.compress_type,
            info.flag_bits, info.date_time, info.external_attr, info.internal_attr,
            info.create_system, info.create_version, info.extract_version,
            info.extra.hex(), info.comment.hex())


def fingerprint(archive: zipfile.ZipFile, names: set[str]) -> str:
    infos = names_and_infos(archive)
    require(names <= infos.keys(), f"missing protected entries: {sorted(names-infos.keys())[:8]}")
    h = hashlib.sha256()
    for name in sorted(names):
        info = infos[name]
        h.update(name.encode("utf-8"))
        h.update(b"\0")
        h.update(hashlib.sha256(raw_local_record(archive, info)).digest())
        h.update(b"\0")
        h.update(str(central_metadata(info)).encode("utf-8"))
        h.update(b"\n")
    return h.hexdigest()


def verify_one(side: str, path: Path, rule: dict, *, baseline: bool) -> dict:
    require(path.is_file(), f"{side}: JAR missing: {path}")
    if not baseline:
        require(path.name == rule["filename"],
                f"{side}: unauthorized JAR filename: {path.name}; expected {rule['filename']}")
    digest = sha256_file(path)
    with zipfile.ZipFile(path) as jar:
        infos = names_and_infos(jar)
        if baseline:
            require(digest == rule["baseline_sha256"],
                    f"{side}: this is NOT the user-approved baseline")
            require(len(infos) == rule["baseline_entries"], f"{side}: baseline entry count mismatch")
            return {"side": side, "verified_baseline_sha256": digest, "entries": len(infos)}
        changed = set(rule["allowed_modified"])
        added = set(rule["allowed_added"])
        require(not changed & added, f"{side}: conflicting allowlists")
        require(len(infos) == rule["baseline_entries"] + len(added),
                f"{side}: missing/extra JAR members; expected {rule['baseline_entries']+len(added)}, got {len(infos)}")
        require(changed | added <= infos.keys(),
                f"{side}: mandatory allowed classes absent: {sorted((changed|added)-infos.keys())}")
        expected_originals = rule["modified_original_sha256"]
        require(set(expected_originals) == changed,
                f"{side}: modified class baseline signatures missing")
        for name in sorted(changed):
            require(hashlib.sha256(jar.read(name)).hexdigest() != expected_originals[name],
                    f"{side}: listed class was not changed: {name}")
        protected = set(infos) - changed - added
        require(len(protected) == rule["protected_entries"],
                f"{side}: unauthorized added/deleted file(s) outside the class allowlist")
        calculated = fingerprint(jar, protected)
        require(calculated == rule["protected_fingerprint"],
                f"{side}: PROTECTED FILE CHANGED (resource, class, ZIP header or compressed payload): {calculated}")
    return {"side": side, "filename": path.name, "sha256": digest,
            "baseline_sha256": rule["baseline_sha256"],
            "verified_protected_members": len(protected),
            "authorized_modified_classes": len(changed),
            "authorized_added_classes": len(added), "removed_files": 0,
            "unexpected_changes": 0}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("verify-release", "verify-baseline"))
    parser.add_argument("--paper", type=Path, required=True)
    parser.add_argument("--fabric", type=Path, required=True)
    parser.add_argument("--policy", type=Path, default=DEFAULT_POLICY)
    args = parser.parse_args(argv)
    try:
        policy = json.loads(args.policy.read_text(encoding="utf-8"))
        require(policy.get("schema") == 1, "unsupported release policy schema")
        require(set(policy["jars"]) == {"paper", "fabric"}, "both JARs are mandatory")
        reports = [verify_one(side, path, policy["jars"][side],
                              baseline=args.mode == "verify-baseline")
                   for side, path in (("paper", args.paper), ("fabric", args.fabric))]
    except (RejectedRelease, OSError, ValueError, KeyError, zipfile.BadZipFile) as exc:
        print(f"RELEASE REJECTED: {exc}", file=sys.stderr)
        return 1
    print(json.dumps({"result": "APPROVED", "policy_sha256": sha256_file(args.policy),
                      "mode": args.mode, "jars": reports}, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())