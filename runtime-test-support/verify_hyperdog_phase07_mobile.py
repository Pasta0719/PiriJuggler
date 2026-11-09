#!/usr/bin/env python3
"""Static/JS validation of the actual packaged mobile HYPER_DOG data panel."""
from pathlib import Path
import re
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / "paper/src/main/java/jp/pirijuggler/paper/mobile/MobileRemoteGateway.java").read_text("utf-8")
m = re.search(r"private static final String PAGE\s*=\s*\"\"\"(.*?)\"\"\"\s*;", source, re.S)
assert m, "mobile page embedded in production source not found"
html = m.group(1)
tags = re.findall(r'<script(?:\s[^>]*)?>(.*?)</script>', html, re.S)
assert len(tags) == 1, "unexpected mobile script count"
js = tags[0]
for element in ("dBigLabel", "dRegLabel", "dUpperPanel", "dUpper",
                "preHistoryTitle", "dBig", "dReg", "preHistory"):
    assert len(re.findall(r'id="'+element+r'"', html)) == 1, "missing or duplicate "+element
for expression in ('d.machineType==="HYPER_DOG"',
                   'd.atFirstHits||0', 'd.czAttempts||0', 'd.upperEntries||0',
                   'hyperEventLabel(x.type)', '("dUpperPanel").classList.toggle'):
    if expression == '("dUpperPanel").classList.toggle':
        assert '$'+expression in js, "mobile HYPER_DOG upper panel not controlled"
    else:
        assert expression in js, "mobile HYPER_DOG data not wired: "+expression
assert 'name.textContent=hyper?hyperEventLabel(x.type):x.type' in js, "history must be text only"
assert 'value.textContent=hyper?' in js, "history values must be text only"
assert 'currentType==="HYPER_DOG"' not in js[js.index("function startGame(j)"):]
assert shutil.which("node"), "NodeJS is required for mobile script syntax validation"
proc = subprocess.run(["node", "--check"], input=js, text=True, capture_output=True)
assert proc.returncode == 0, "production mobile JS syntax invalid: "+proc.stderr
print("HYPER_DOG_PHASE07_MOBILE_VIEW_JS=PASS")
