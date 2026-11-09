#!/usr/bin/env python3
"""Fail CI if an active HYPER_DOG specification reverts to obsolete numeric sources.

Completed historical Phase01-05 reports and v5/v6 audit artifacts are not rewritten.
This guard checks document pointers, not simulator/gameplay correctness.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CANONICAL = "original-at-phases/reference/pirijuggler_new_machine_calibrated_v7.json"
DOCS = [
    "CODEX_START.md",
    "original-at-phases/README.md",
    "original-at-phases/SPEC_LOCK.md",
    "original-at-phases/IMPLEMENTATION_DECISIONS_V1.md",
    "original-at-phases/PHASE_06_PRESENTATION.md",
    "original-at-phases/PHASE_07_ECOSYSTEM.md",
    "original-at-phases/PHASE_08_FINAL_ACCEPTANCE.md",
    "original-at-phases/POST_PHASE05_UPPER_4K_V7.md"
]
STALE = (
    "が今後の正本",
    "v5 JSONを固定",
    "v5 JSON全項目を正として",
    "数値基準v5は実装用に固定",
    "v5数値の実装照合",
    "v5全役確率・6設定の長期シミュレーション結果",
    "v5数値正本とPaper",
)

def main():
    errors = []
    phase_map = json.loads((ROOT / "original-at-phases/PHASE_MAP.json").read_text(encoding="utf-8"))
    if phase_map.get("machine_type") != "HYPER_DOG" or phase_map.get("numeric_reference") != CANONICAL:
        errors.append("PHASE_MAP.json must reference HYPER_DOG v7")
    model = json.loads((ROOT / CANONICAL).read_text(encoding="utf-8"))
    if model.get("meta", {}).get("version") != "calibrated_v7_upper_4k_target":
        errors.append("approved v7 JSON version mismatch")
    for path in DOCS:
        content = (ROOT / path).read_text(encoding="utf-8")
        if "CURRENT_NUMERIC_REFERENCE / HYPER_DOG" not in content:
            errors.append(f"{path}: current numeric reference declaration missing")
        if "pirijuggler_new_machine_calibrated_v7.json" not in content:
            errors.append(f"{path}: v7 source not referenced")
        if "v5/v6" not in content:
            errors.append(f"{path}: historical version boundary missing")
        for obsolete in STALE:
            if obsolete in content:
                errors.append(f"{path}: obsolete active reference {obsolete}")
    if errors:
        for error in errors:
            print("HYPER_DOG_V7_REFERENCE_GUARD_ERROR:", error)
        raise SystemExit(1)
    print(f"HYPER_DOG_V7_REFERENCE_GUARD=PASS ({len(DOCS)} active docs; {CANONICAL})")

if __name__ == "__main__":
    main()
