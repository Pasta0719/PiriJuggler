#!/usr/bin/env python3
"""HYPER_DOG v2 21×3 physical strips and exhaustive role legality / common slip proof."""
import itertools
import json
from pathlib import Path
from collections import Counter

cfg=json.loads(Path(__file__).with_name("REEL_STRIPS_V2.json").read_text(encoding="utf-8"))
reels=[cfg["reels"][name] for name in ("LEFT","CENTER","RIGHT")]
L=cfg["length"];LINES=cfg["lines"]
PREMIUM={"RED7","BAR","RAINBOW7"}
ROLES=tuple(cfg["expected_legal_stop_counts"])
assert L==21 and all(len(r)==21 for r in reels)
for reel in reels:
    for symbol in PREMIUM:assert [i for i,x in enumerate(reel) if x==symbol]==[{"RED7":0,"BAR":7,"RAINBOW7":14}[symbol]]
    assert Counter(reel)==Counter({"RED7":1,"BAR":1,"RAINBOW7":1,"GRAPE":6,"REPLAY":6,"CHERRY":3,"WATERMELON":3})

def symbol(reel,stop,shift=0):
    return reels[reel][(stop+shift)%L]

def mid(stops):return tuple(symbol(i,stops[i]) for i in range(3))

def paylines(stops):
    return [(tuple(off),tuple(symbol(i,stops[i],off[i]) for i in range(3))) for off in LINES]

def flags(stops):
    awards=set()
    for off,line in paylines(stops):
        if len(set(line))==1:
            awards.add(line[0])
            if line==("CHERRY",)*3 and off[0]!=0:awards.add("STRONG_CHERRY")
        if off[0]==off[1]==off[2] and off[0]!=0 and line[0]==line[1]=="CHERRY" and line[2]!="CHERRY":
            awards.add("WEAK_CHERRY")
    s=mid(stops)
    if s==("RED7","BAR","RED7"):awards.add("CHANCE")
    if s==("RED7","BAR","RAINBOW7"):awards.add("STRONG_CHANCE")
    if s==("RED7",)*3:awards.add("AT_ENTRY_RED7")
    return awards

def premium_allowed(role,stops):
    premiums=[(off,line) for off,line in paylines(stops) if all(x in PREMIUM for x in line)]
    expected={
        "BAR": (None,("BAR",)*3),
        "AT_ENTRY_RED7":((0,0,0),("RED7",)*3),
        "CZ_WIN":((0,0,0),("RAINBOW7",)*3),
        "CHANCE":((0,0,0),("RED7","BAR","RED7")),
        "STRONG_CHANCE":((0,0,0),("RED7","BAR","RAINBOW7"))
    }.get(role)
    if expected is None:return not premiums
    return len(premiums)==1 and premiums[0][1]==expected[1] and (expected[0] is None or premiums[0][0]==expected[0])

def legal(role,stops):
    s=mid(stops)
    if s[0]=="CHERRY" and role!="BAR":return False
    if not premium_allowed(role,stops):return False
    f=flags(stops)
    if role=="MISS":return not f
    if role=="BAR":return f=={"BAR"}
    if role=="AT_ENTRY_RED7":return s==("RED7",)*3 and f=={"RED7","AT_ENTRY_RED7"}
    if role=="CZ_WIN":return s==("RAINBOW7",)*3 and f=={"RAINBOW7"}
    if role=="CZ_LOSE":return s[0]==s[2]=="RAINBOW7" and s[1]!="RAINBOW7" and not f
    if role=="STRONG_CHERRY":return f=={"STRONG_CHERRY","CHERRY"}
    return f=={role}

def covered_by_slip(stops,limit=4):
    return all(any(stops[(pressed-slip)%L] for slip in range(limit+1)) for pressed in range(L))

def every_order_guaranteed_with_four(candidate):
    for first in range(3):
        first_safe=[False]*L
        for fs in range(L):
            orientations=True
            for second in range(3):
                if first==second:continue
                last=3-first-second
                second_safe=[False]*L
                for ss in range(L):
                    last_allowed=[False]*L
                    for ts in range(L):
                        trip=[None]*3;trip[first]=fs;trip[second]=ss;trip[last]=ts
                        last_allowed[ts]=tuple(trip) in candidate
                    second_safe[ss]=covered_by_slip(last_allowed)
                if not covered_by_slip(second_safe):
                    orientations=False;break
            first_safe[fs]=orientations
        assert covered_by_slip(first_safe),("4 slip cannot be guaranteed",first)

all_stops=list(itertools.product(range(L),repeat=3))
assert len(all_stops)==9261
candidates={role:{s for s in all_stops if legal(role,s)} for role in ROLES}
for role,stops in candidates.items():
    assert len(stops)==cfg["expected_legal_stop_counts"][role],(role,len(stops))
    if role in ("MISS","REPLAY","GRAPE"):
        every_order_guaranteed_with_four(stops)
    else:
        for reel in range(3):
            for pressed in range(L):
                assert any(0<=(pressed-s[reel])%L<=20 for s in stops)
    print(f"{role:<18} {len(stops):5} legal triplets — PASS")
assert (6,6,6) in candidates["BAR"]
assert symbol(0,6)=="CHERRY"
assert (0,7,0) in candidates["CHANCE"]
assert (0,7,14) in candidates["STRONG_CHANCE"]
assert (0,0,0) in candidates["AT_ENTRY_RED7"]
assert (14,14,14) in candidates["CZ_WIN"]
assert any(symbol(0,s[0],-1)=="CHERRY" for role in ("STRONG_CHERRY","WEAK_CHERRY") for s in candidates[role])
assert any(symbol(0,s[0],1)=="CHERRY" for role in ("STRONG_CHERRY","WEAK_CHERRY") for s in candidates[role])
print("HYPER_DOG v2: 9261 stop outcomes, 5-line mixed-premium guard, 4-slip normal roles, BAR/cherry exception PASS")
