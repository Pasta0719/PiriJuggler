#!/usr/bin/env python3
"""Design-level verification for REEL_STRIPS_DRAFT_V1.json. Does not test Minecraft runtime."""
import itertools
import json
from pathlib import Path

PATH = Path(__file__).with_name("REEL_STRIPS_DRAFT_V1.json")
cfg = json.loads(PATH.read_text(encoding="utf-8"))
names = ("LEFT", "CENTER", "RIGHT")
strips = [cfg["reels"][name] for name in names]
n = cfg["length"]
lines = cfg["lines"]
assert n == 21 and all(len(x) == n for x in strips)
assert len(set(cfg["symbols"])) == 7
assert all(x in cfg["symbols"] for reel in strips for x in reel)

def symbol(reel, stop, offset=0):
    return strips[reel][(stop + offset) % n]

def marks(stops):
    found = set()
    for line in lines:
        row = tuple(symbol(reel, stops[reel], line[reel]) for reel in range(3))
        if len(set(row)) == 1 and row[0] in ("GRAPE", "REPLAY", "WATERMELON", "BAR", "RAINBOW7", "CHERRY"):
            found.add(row[0])
    middle = tuple(symbol(reel, stops[reel]) for reel in range(3))
    if middle == ("RED7", "BAR", "RED7"):
        found.add("CHANCE")
    if middle == ("RED7", "BAR", "RAINBOW7"):
        found.add("STRONG_CHANCE")
    if middle[0] == middle[1] == "CHERRY" and middle[2] != "CHERRY":
        found.add("WEAK_CHERRY")
    if middle == ("CHERRY",) * 3:
        found.add("STRONG_CHERRY")
    return found

def legal(role, stops):
    found = marks(stops)
    middle = tuple(symbol(reel, stops[reel]) for reel in range(3))
    if role == "CZ_WIN":
        return found == {"RAINBOW7"} and middle == ("RAINBOW7",) * 3
    if role == "CZ_LOSE":
        return not found and middle[0] == middle[2] == "RAINBOW7" and middle[1] != "RAINBOW7"
    if role == "BAR":
        return found == {"BAR"} and middle == ("BAR",) * 3
    if role == "MISS":
        return not found
    if role == "STRONG_CHERRY":
        return found == {"STRONG_CHERRY", "CHERRY"}
    return found == {role}

expected = {
    "MISS": 7814, "REPLAY": 497, "GRAPE": 504,
    "WATERMELON": 40, "WEAK_CHERRY": 96,
    "STRONG_CHERRY": 15, "CHANCE": 4,
    "STRONG_CHANCE": 2, "BAR": 8,
    "CZ_WIN": 4, "CZ_LOSE": 60
}
all_positions = list(itertools.product(range(n), repeat=3))
assert len(all_positions) == 9261
for role, count in expected.items():
    candidates = [s for s in all_positions if legal(role, s)]
    assert len(candidates) == count, (role, count, len(candidates))
    assert all(legal(role, s) for s in candidates)
    print(f"{role:16} {len(candidates):5} valid final stop triplets; e.g. {candidates[0]}")
    # A full strip rotation means every pressed index can reach any chosen
    # completion with a nonnegative displacement of at most 20 symbols.
    # This is NOT a proof that every stop works with a 4-symbol slip cap.
    for permutation in itertools.permutations(range(3)):
        completion = candidates[0]
        for reel in permutation:
            for pressed in range(n):
                slip = (pressed - completion[reel]) % n
                assert 0 <= slip <= 20

assert tuple(symbol(reel, pos) for reel, pos in enumerate((2, 6, 0))) == ("CHERRY", "CHERRY", "GRAPE")
assert tuple(symbol(reel, pos) for reel, pos in enumerate((2, 6, 9))) == ("CHERRY",) * 3
for role, fixed in (
    ("CHANCE", ("RED7", "BAR", "RED7")),
    ("STRONG_CHANCE", ("RED7", "BAR", "RAINBOW7")),
    ("BAR", ("BAR", "BAR", "BAR")),
    ("CZ_WIN", ("RAINBOW7",) * 3)
):
    assert any(
        tuple(symbol(reel, s[reel]) for reel in range(3)) == fixed
        and legal(role, s) for s in all_positions
    ), role
print("DESIGN_CHECK_PASS: 9261 triplets checked; not a live stop-solver or Minecraft test")
