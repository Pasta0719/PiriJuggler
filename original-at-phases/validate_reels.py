#!/usr/bin/env python3
"""Design-only verifier for original AT 21x3 reel strips.
No Minecraft engine implementation is claimed by these checks.
"""
import itertools
import json
from pathlib import Path

cfg = json.loads(Path(__file__).with_name("REEL_STRIPS_DRAFT_V1.json").read_text(encoding="utf-8"))
names = ("LEFT", "CENTER", "RIGHT")
reels = tuple(cfg["reels"][name] for name in names)
n = cfg["length"]
assert n == 21 and all(len(reel) == n for reel in reels)
assert all(symbol in cfg["symbols"] for reel in reels for symbol in reel)
LINES = cfg["lines"]

def cell(reel, stop, offset=0):
    return reels[reel][(stop + offset) % n]

def middle(stops):
    return tuple(cell(reel, stops[reel]) for reel in range(3))

def visible_awards(stops):
    """All visual lines count to reject accidental bonus symbols, even off the middle row."""
    found = set()
    for offsets in LINES:
        line = tuple(cell(reel, stops[reel], offsets[reel]) for reel in range(3))
        if len(set(line)) == 1 and line[0] in (
            "GRAPE", "REPLAY", "WATERMELON", "CHERRY", "BAR", "RAINBOW7", "RED7"
        ):
            found.add(line[0])
    mid = middle(stops)
    if mid == ("RED7", "BAR", "RED7"):
        found.add("CHANCE")
    if mid == ("RED7", "BAR", "RAINBOW7"):
        found.add("STRONG_CHANCE")
    if mid[0] == mid[1] == "CHERRY" and mid[2] != "CHERRY":
        found.add("WEAK_CHERRY")
    if mid == ("CHERRY",) * 3:
        found.add("STRONG_CHERRY")
    if mid == ("RED7",) * 3:
        found.add("AT_ENTRY_RED7")
    return found

def legal(role, stops):
    awards = visible_awards(stops)
    mid = middle(stops)
    if role == "BAR":
        return mid == ("BAR",) * 3 and awards == {"BAR"}
    if role == "AT_ENTRY_RED7":
        return mid == ("RED7",) * 3 and awards == {"RED7", "AT_ENTRY_RED7"}
    if role == "CZ_WIN":
        return mid == ("RAINBOW7",) * 3 and awards == {"RAINBOW7"}
    if role == "CZ_LOSE":
        return mid[0] == mid[2] == "RAINBOW7" and mid[1] != "RAINBOW7" and not awards
    if role == "STRONG_CHERRY":
        return awards == {"CHERRY", "STRONG_CHERRY"}
    if role == "MISS":
        return not awards
    return awards == {role}

def covered_by_slip(allowed_stops, limit):
    """Every possible pressed index can reach an allowed stop within 0..limit slips."""
    return all(
        any(allowed_stops[(pressed - delta) % n] for delta in range(limit + 1))
        for pressed in range(n)
    )

def every_order_guaranteed_with_four(candidate):
    """First button cannot anticipate the order/index of the next two buttons.
    Check adversarial first press, either next reel, any subsequent presses."""
    for first_reel in range(3):
        first_safe = [False] * n
        for first_stop in range(n):
            good_for_any_second_reel = True
            for second_reel in range(3):
                if second_reel == first_reel:
                    continue
                last_reel = 3 - first_reel - second_reel
                second_safe = [False] * n
                for second_stop in range(n):
                    last_allowed = [False] * n
                    for last_stop in range(n):
                        stops = [0, 0, 0]
                        stops[first_reel] = first_stop
                        stops[second_reel] = second_stop
                        stops[last_reel] = last_stop
                        last_allowed[last_stop] = tuple(stops) in candidate
                    second_safe[second_stop] = covered_by_slip(last_allowed, 4)
                if not covered_by_slip(second_safe, 4):
                    good_for_any_second_reel = False
                    break
            first_safe[first_stop] = good_for_any_second_reel
        assert covered_by_slip(first_safe, 4), (
            "Four-slip guarantee failed", first_reel
        )

expected = {
    "MISS": 7904,
    "REPLAY": 363,
    "GRAPE": 362,
    "WATERMELON": 52,
    "WEAK_CHERRY": 103,
    "STRONG_CHERRY": 16,
    "CHANCE": 6,
    "STRONG_CHANCE": 6,
    "BAR": 6,
    "CZ_WIN": 7,
    "CZ_LOSE": 65,
    "AT_ENTRY_RED7": 8,
}
all_positions = list(itertools.product(range(n), repeat=3))
assert len(all_positions) == n ** 3 == 9261
for role, count in expected.items():
    candidates = {stops for stops in all_positions if legal(role, stops)}
    assert len(candidates) == count, (role, count, len(candidates))
    print(f"{role:17} {count:5} legal positions, e.g. {min(candidates)}")
    if role in ("MISS", "REPLAY", "GRAPE"):
        every_order_guaranteed_with_four(candidates)
        print(f"  4-SLIP ALL ORDER/ALL PRESSES: PASS")
    else:
        # Guaranteed special-role completion uses an independent 0..20 rotation,
        # so for any press a fixed legal final triplet is reachable.
        completion = min(candidates)
        for reel in range(3):
            for pressed in range(n):
                slip = (pressed - completion[reel]) % n
                assert 0 <= slip <= 20

for role, target in (
    ("WEAK_CHERRY", ("CHERRY", "CHERRY", "GRAPE")),
    ("STRONG_CHERRY", ("CHERRY", "CHERRY", "CHERRY")),
    ("CHANCE", ("RED7", "BAR", "RED7")),
    ("STRONG_CHANCE", ("RED7", "BAR", "RAINBOW7")),
    ("BAR", ("BAR", "BAR", "BAR")),
    ("CZ_WIN", ("RAINBOW7", "RAINBOW7", "RAINBOW7")),
    ("AT_ENTRY_RED7", ("RED7", "RED7", "RED7")),
):
    assert any(middle(stops) == target and legal(role, stops) for stops in all_positions), role

print("REEL DESIGN PASS: 9261 triplets; 4-slip common roles; guaranteed special alignments")
print("This is a static design verification, NOT a running server stop-solver acceptance.")
