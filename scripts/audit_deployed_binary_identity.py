#!/usr/bin/env python3
"""Audit byte/content identity of compiled PiriJuggler JARs against deployed 2026-11-11 pair.

Diagnostic ONLY: this never bypasses, alters or approves the production JAR release gate.
"""
from __future__ import annotations
import hashlib
import json
import sys
import zipfile
from pathlib import Path

REFERENCE = {
    "paper": {
        "archive_sha256": "fa6fbc0caa12949ee6bd0f7a47177dd75f8ab116c78fb5082c2de43d5d887344",
        "entries": 1039,
        "all_contents_sha256": "9528d5d3d321baae33e19bbdbc3fcd3b5118d336e3dbd742b8c61997704244c3",
        "all_class_contents_sha256": "4322226e1c2c8ed315e52503dae2b293d21d7853879fa774a4ad8412d1ac6f3b",
    },
    "fabric": {
        "archive_sha256": "c2622815fb92d2f4ffacc4dbe4c3e5c57e9017536a3290c527db10c530dc8bff",
        "entries": 213,
        "all_contents_sha256": "0f465b4c53c1a6431311770609fc2050c6943b8504d927517f5ed6e464a12f98",
        "all_class_contents_sha256": "c8855bd2698491c5f2b20f7c514815bd22a9242224aead9f26fabb897a676978",
    },
}

def digest(entries: dict[str, str]) -> str:
    h = hashlib.sha256()
    for name in sorted(entries):
        h.update(name.encode("utf-8"))
        h.update(b"\0")
        h.update(bytes.fromhex(entries[name]))
        h.update(b"\n")
    return h.hexdigest()

def audit(side: str, jar_path: Path, report_dir: Path) -> bool:
    with zipfile.ZipFile(jar_path) as jar:
        members = {item.filename: hashlib.sha256(jar.read(item.filename)).hexdigest() for item in jar.infolist()}
        classes = {key: value for key, value in members.items() if key.endswith(".class")}
    actual = {
        "archive_sha256": hashlib.sha256(jar_path.read_bytes()).hexdigest(),
        "entries": len(members),
        "all_contents_sha256": digest(members),
        "all_class_contents_sha256": digest(classes),
    }
    print(f"===== {side.upper()} {jar_path} =====")
    for key in REFERENCE[side]:
        print(f"{key}: match={actual[key] == REFERENCE[side][key]} expected={REFERENCE[side][key]} actual={actual[key]}")
    report_dir.mkdir(parents=True, exist_ok=True)
    (report_dir / f"{side}-entries.json").write_text(json.dumps(members, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (report_dir / f"{side}-summary.json").write_text(json.dumps(actual, indent=2) + "\n", encoding="utf-8")
    return all(actual[key] == REFERENCE[side][key] for key in REFERENCE[side])

if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("usage: audit_deployed_binary_identity.py <paper.jar> <fabric.jar> <report-dir>")
    paper_ok = audit("paper", Path(sys.argv[1]), Path(sys.argv[3]))
    fabric_ok = audit("fabric", Path(sys.argv[2]), Path(sys.argv[3]))
    if not paper_ok or not fabric_ok:
        raise SystemExit("The supplied JAR pair differs from the owner-approved deployed reference")
    print("APPROVED: both JARs match the exact owner-deployed SHA256 and content digests")
