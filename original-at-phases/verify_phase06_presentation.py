#!/usr/bin/env python3
"""Check Phase06 presentation weights against the user-approved machine-readable spec.

Read-only: this does not alter game odds, server state, assets or historical evidence.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RULES = json.loads((ROOT / "original-at-phases/PRESENTATION_RULES_V1.json").read_text(encoding="utf-8"))
JAVA = (ROOT / "paper/src/main/java/jp/pirijuggler/paper/game/hyperdog/HyperDogPresentation.java").read_text(encoding="utf-8")

def table(name):
    match = re.search(r"\b" + re.escape(name) + r"\s*=\s*\{([\s\S]*?)\};", JAVA)
    if not match:
        raise AssertionError(f"Missing implementation matrix: {name}")
    rows = re.findall(r"\{([0-9,\s]+)\}", match.group(1))
    return [[int(x) for x in re.findall(r"\d+", row)] for row in rows]

def main():
    assert RULES["identity"]["machine_type"] == "HYPER_DOG"
    colors = RULES["color_rank"]
    actual_colors = re.search(r"enum Color \{([^}]+)\}", JAVA)
    assert actual_colors and [x.strip() for x in actual_colors.group(1).split(",")] == colors
    actual_min = re.search(r"long\[\] MIN\s*=\s*\{([0-9,\s]+)\}", JAVA)
    assert actual_min
    assert [int(x) for x in re.findall(r"\d+", actual_min.group(1))] == [
        RULES["single_award_minimum_medals"][c] for c in colors
    ]
    expected_colors = [line["values"] for line in RULES["reward_color_distribution_percent"]]
    expected_counts = [
        [row["weights_percent"][k] for k in ("one","two","three")]
        for row in RULES["reward_lamp_count_distribution_percent"]
    ]
    assert table("COLOR_WEIGHTS") == expected_colors, "color odds no longer match approved JSON"
    assert table("COUNT_WEIGHTS") == expected_counts, "lamp count odds no longer match approved JSON"
    assert all(sum(row)==100 for row in expected_colors+expected_counts)
    assert RULES["rainbow_requires_exact_single_award"] == 2000
    for band,weights in zip(RULES["reward_color_distribution_percent"], expected_colors):
        amount=band["min"]
        for color,weight in zip(colors,weights):
            if not weight:
                continue
            assert amount>=RULES["single_award_minimum_medals"][color]
            assert color!="RAINBOW" or band["min"]==band["max"]==2000
    assert RULES["color_progression"]["target_reveal_stage_percent"] == {
        "LEVER_ON":20,"STOP_1":40,"STOP_2":40
    }
    assert RULES["count_progression"]["final_count_one"] == [1,1,1]
    assert RULES["count_progression"]["final_count_three"] == [1,2,3]
    assert RULES["count_progression"]["final_count_two"]["probability_each"]==0.5
    f=RULES["flash"]
    assert (f["on_ms"],f["off_ms"],f["cycles"])==(80,70,4)
    assert (f["same_final_color_probability"],f["attempt_promote_probability"]) == (0.8,0.2)
    assert RULES["results"]["upper_sixth_game_third_stop"]=="TOTAL_FIRST_5_ONLY"
    print("HYPER_DOG_PHASE06_APPROVED_PRESENTATION_PARITY=PASS")

if __name__=="__main__":
    main()
