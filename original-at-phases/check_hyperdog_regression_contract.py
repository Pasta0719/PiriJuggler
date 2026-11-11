#!/usr/bin/env python3
"""Fail-closed regression guard: protect existing-machine graph, audio and supplied art.

This manifest is pinned to the verified HYPER_DOG development head on 2026-11-10.
Do NOT update a checksum automatically: a deliberate legacy behavior/artwork
change requires human review and an explicit checksum update in the same PR.
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOCKED_BLOBS = {
    "fabric/src/main/java/jp/pirijuggler/fabric/ui/SlotScreen.java": "4a17b8edf15754d40405c763b6f930d3fa20fe07",
    "fabric/src/main/java/jp/pirijuggler/fabric/ui/SlotLayout.java": "3f06996561589377a764de28f7e93dc430093c23",
    "fabric/src/main/java/jp/pirijuggler/fabric/ui/PiriSounds.java": "7ba55935d04ebbb1edcee555cccd44306438e1e6",
    "fabric/src/main/java/jp/pirijuggler/fabric/ui/HallAudio.java": "715b9400cce1c9fd8510273f03be91c36e12ab45",
    "fabric/src/main/resources/assets/piri/sounds.json": "c4b501c2abdae6f4a51aeb16dc7a4d903a69f83b",
    "user-audio/juggler_god_god_big_bgm.ogg": "ec71f5a9c7782c61a888d9fc8e3aba63089d2154",
    "user-audio/juggler_god_god_bonus_end.ogg": "d21badda699519c86d1021ccf7a3725d415d266d",
    "user-audio/juggler_god_god_bonus_start.ogg": "547326c56b8a0f36e1eda5e731afb96afcee4606",
    "user-audio/juggler_god_god_freeze.ogg": "0a8c08ea3d8512a8c04db06a2751b4140f1748dc",
    "user-audio/juggler_god_god_stop_1.ogg": "b92aee388231f82187f777958b5666dbde1e7660",
    "user-audio/juggler_god_god_stop_2.ogg": "b92aee388231f82187f777958b5666dbde1e7660",
    "user-audio/juggler_god_god_stop_3.ogg": "b92aee388231f82187f777958b5666dbde1e7660",
    "fabric/src/main/resources/assets/piri/textures/gui/original_at/symbols/bar.png": "33a5c2c036fb20a6a3cc46b565f0c25960a68174",
    "fabric/src/main/resources/assets/piri/textures/gui/original_at/symbols/cherry.png": "395bb4c58dca2dc96210f19c53bcec7dc4d968c1",
    "fabric/src/main/resources/assets/piri/textures/gui/original_at/symbols/grape.png": "3802a833cca49766735138f8ddda6aeaa9792987",
    "fabric/src/main/resources/assets/piri/textures/gui/original_at/symbols/rainbow7.png": "f91deeebc14febd09caf9b682784aca381990ab5",
    "fabric/src/main/resources/assets/piri/textures/gui/original_at/symbols/red7.png": "d5a354614d5ebc6e86d5193c29df91e71b83aa21",
    "fabric/src/main/resources/assets/piri/textures/gui/original_at/symbols/replay.png": "ee2c567bdf95091f4b30940e3cd632a9a339c7b0",
    "fabric/src/main/resources/assets/piri/textures/gui/original_at/symbols/watermelon.png": "5f2db5ef7b5a6fa2d2baf5ae47c78875cab17f6e"
}
GRAPH_SOURCE = ROOT / "fabric/src/main/java/jp/pirijuggler/fabric/ui/HyperDogCabinetScreen.java"


def checksum(path: str) -> str:
    result = subprocess.run(["git", "hash-object", "--", path],
                            cwd=ROOT, capture_output=True, text=True, check=True)
    return result.stdout.strip()


def mismatches(expected: dict[str, str], actual: dict[str, str]) -> list[str]:
    return [f"{path}: expected {sha}, got {actual.get(path, 'MISSING')}"
            for path, sha in expected.items() if actual.get(path) != sha]


def graph_contract() -> list[str]:
    source = GRAPH_SOURCE.read_text(encoding="utf-8")
    expected = (
        'var graph=data!=null&&data.has("graph")?data.getAsJsonArray("graph")',
        'SlotScreen.drawGraph(c,graph,55,88,825,146,total);',
    )
    errors = [f"HYPER_DOG graph no longer reuses existing data+SlotScreen: {fragment}"
              for fragment in expected if fragment not in source]
    if source.count("SlotScreen.drawGraph(") != 1:
        errors.append("HYPER_DOG must draw the authoritative graph exactly once")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        sample = {"legacy-audio": "good"}
        if mismatches(sample, {"legacy-audio": "modified"}) != [
            "legacy-audio: expected good, got modified"
        ]:
            print("FAIL: negative-regression probe did not detect changed asset")
            return 1
        print("PASS: intentionally changed legacy asset is rejected")
    actual = {}
    errors = []
    for path in LOCKED_BLOBS:
        try:
            actual[path] = checksum(path)
        except (OSError, subprocess.CalledProcessError) as exc:
            errors.append(f"{path}: cannot read git blob: {exc}")
    errors += mismatches(LOCKED_BLOBS, actual)
    errors += graph_contract()
    if errors:
        print("FAIL: HYPER_DOG changed a protected legacy contract:")
        for problem in errors:
            print(" -", problem)
        return 1
    print(f"PASS: {len(LOCKED_BLOBS)} approved legacy/audio/art files unchanged")
    print("PASS: HYPER_DOG uses the existing authoritative SlotScreen graph exactly once")
    return 0


if __name__ == "__main__":
    sys.exit(main())
