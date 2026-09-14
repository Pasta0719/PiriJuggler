"""Extract SPEC.md values. Run from the repository: python docs/extract_spec_lock.py."""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
text = (ROOT / "SPEC.md").read_text(encoding="utf-8-sig").replace("\r\n", "\n")
chapters = {}
for match in re.finditer(r"^# (\d+)\. .*?(?=^# \d+\. |\Z)", text, re.M | re.S):
    chapters[int(match[1])] = match[0]

def block(number, language="text"):
    return re.findall(r"```" + language + r"\n(.*?)```", chapters[number], re.S)

settings = {}
for setting, body in re.findall(r"## Setting (\d)\n(.*?)(?=## Setting |\Z)", chapters[20], re.S):
    settings[setting] = {name.lower(): int(value) for name, value in re.findall(r"^([A-Z_]+)\s+(\d+)\s*$", body, re.M) if name != "TOTAL"}
    assert len(settings[setting]) == 12 and sum(settings[setting].values()) == 1_000_000_000

config = block(124, "yaml")[0].rstrip() + "\n\nprobabilities:\n  settings:\n"
for setting, weights in settings.items():
    config += f"    '{setting}':\n"
    config += "".join(f"      {key}: {value}\n" for key, value in weights.items())
config_path = ROOT / "paper/src/main/resources/config.yml"
config_path.parent.mkdir(parents=True, exist_ok=True)
config_path.write_text(config, encoding="utf-8", newline="\n")

packet_ids = {name: int(value) for number in (86, 87) for name, value in re.findall(r"^([A-Z_0-9]+)=(\d+)\s*$", chapters[number], re.M)}
reels = {name: re.findall(r"[A-Z]+", body) for name, body in re.findall(r"(LEFT_REEL|CENTER_REEL|RIGHT_REEL) = \[(.*?)\];", chapters[4], re.S)}
paylines = {name: [part.strip() for part in body.split(",")] for name, body in re.findall(r"^(L\d_[A-Z]+)\s*= \[(.*?)\]", chapters[6], re.M)}
payouts = {key: int(value) for key, value in re.findall(r"^([A-Z]+)\s*= (\d+)", chapters[7], re.M)}
ui = {label.strip(): {key: int(value) for key, value in re.findall(r"([xywh])(\d+)", body)} for label, body in re.findall(r"^([^\n]+):\n```text\n([xywh][^`]+)```", chapters[54], re.M)}
events = {}
for name, body in re.findall(r"## (normal|light|strong)\n(.*?)(?=## |\Z)", chapters[72], re.S):
    events[name] = {setting: int(value) for setting, value in re.findall(r"^S(\d) (\d+)%", body, re.M)}
schemas = re.findall(r"```sql\n(.*?)```", chapters[94], re.S)
(ROOT / "paper/src/main/resources/schema-v4.sql").write_text("\n\n".join(schemas), encoding="utf-8", newline="\n")
fixed = {}
for key, pattern in {
    "creditMax": r"CREDIT max(\d+)", "normalBet": r"normal BET(\d+)",
    "entryBet": r"entry BET(\d+)", "bonusBet": r"bonus BET(\d+)",
    "bigThreshold": r"BIG threshold(\d+)", "bigPayout": r"BIG threshold\d+ / gross payout(\d+)",
    "regThreshold": r"REG threshold(\d+)", "regPayout": r"REG threshold\d+ / gross payout(\d+)",
    "maxMedalBundle": r"max virtual medal token(\d+)"
}.items():
    fixed[key] = int(re.search(pattern, chapters[103])[1])

locked_chapters = (1, 2, 3, 4, 6, 7, 20, 24, 37, 38, 43, 46, 48, 54, 72, 73, 74, 75, 76, 77, 84, 85, 86, 87, 88, 94, 103, 104, 113, 114, 115, 119, 124, 125, 126, 130, 137)
locked_chapters = tuple(sorted(set(locked_chapters) | {18, 42, 63, 64, 65, 66, 67, 68, 69, 70, 71, 78, 79, 80, 81, 82, 92, 95, 97, 127, 135}))
locked_chapters = tuple(sorted(set(locked_chapters) | {23,32,51,52,53,55,56,57,89,90,91,109,112,120,121,122,129,132,138}))
locked_chapters = tuple(sorted(set(locked_chapters) | {5,8,9,10,11,12,13,14,15,26,30,31,33,34,106}))
locked_chapters = tuple(sorted(set(locked_chapters) | {16,17,19,21,22,40,41,59,61,100,101,105,128,131}))
lock = {
    "source": "SPEC.md", "protocolVersion": int(re.search(r"protocol: unsigned uint16 big-endian, value(\d+)", chapters[84])[1]),
    "channel": block(84)[0].strip(), "magicHex": "50495249", "maxPayloadBytes": int(re.search(r"payload max(\d+)", chapters[84])[1]),
    "modVersion": json.loads(block(85, "json")[0])["modVersion"],
    "serverVersion": json.loads(block(85, "json")[1])["serverVersion"],
    "packetIds": packet_ids, "errorCodes": block(115)[0].split(), "fixedConstants": fixed,
    "reelArrays": reels, "paylines": paylines, "payouts": payouts, "settingWeights": settings,
    "uiCoordinates": ui, "uiCanvas": list(map(int, re.search(r"(\d+)x(\d+)", chapters[54]).groups())),
    "uiColors": dict(re.findall(r"^([A-Z_]+)\s+(#[0-9A-F]{6})$", chapters[119], re.M)),
    "eventDistributions": events,
    "database": {"schemaVersion": int(re.search(r"metadata.schema_version`初期値`(\d+)`", chapters[94])[1]), "schemaVersionSource": "SPEC.md chapter 94; Phase 01 performs no schema operations.", "schemas": schemas},
    "defaultConfig": config,
    "medal": {"item": "minecraft:iron_nugget", "stackCount": 1, "itemVersion": 1, "maxAmount": fixed["maxMedalBundle"]},
    "prizes": {"small": {"medalCost": 50, "vaultValue": 1000}, "medium": {"medalCost": 200, "vaultValue": 4000}, "large": {"medalCost": 450, "vaultValue": 9000}},
    "sectionSha256": {str(n): hashlib.sha256(chapters[n].encode()).hexdigest() for n in locked_chapters}
}
(ROOT / "docs/spec-lock.json").write_text(json.dumps(lock, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
lock['symbolShapes'] = json.loads((ROOT / 'docs/symbol-shapes-v3.json').read_text(encoding='utf-8'))
lock['bitmapGlyphs'] = {letter: rows.splitlines() for letter,rows in re.findall(r'^([PIRCHANE])\n((?:[01]{5}\n){7})', chapters[138], re.M)}
lock['bitmapSpacing'] = {'letterColumns':1, 'wordColumns':4, 'lampScale':5, 'lampWordPixels':20}
lock['publicGameStateMapping'] = {internal:public for left,public in re.findall(r'^\| (BONUS_[A-Z_]+ / BONUS_[A-Z_]+) \| (BONUS_[A-Z_]+) \|$',chapters[92],re.M) for internal in left.split(' / ')}
lock['strictCandidateMinimums'] = {name:int(value) for name,value in re.findall(r'^([A-Z_]+) >= (\d+)$',chapters[12],re.M)}
(ROOT / "docs/spec-lock.json").write_text(json.dumps(lock, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
print(f"Extracted {len(packet_ids)} packet IDs, {len(settings)} settings, {len(schemas)} tables and {len(locked_chapters)} section hashes.")
client_ui = ROOT / 'fabric/src/main/resources/assets/piri/client-ui.json'
client_ui.parent.mkdir(parents=True, exist_ok=True)
client_ui.write_text(json.dumps({'colors':lock['uiColors'], 'reels':lock['reelArrays']},indent=2)+'\n',encoding='utf-8',newline='\n')
