#!/usr/bin/env python3
"""Independent large-scale v6 upper comeback calibration validation."""
import hashlib,json,pathlib,sys
from piri_simulate_upper_comeback_v6 import make_params,simulate,validate
ROOT=pathlib.Path(__file__).resolve().parent
PATH=ROOT/"pirijuggler_new_machine_calibrated_v6.json"
d=json.loads(PATH.read_text());validate(d)
p=make_params(d);total=120_000_000
if len(sys.argv)>1:total=int(sys.argv[1])
results=[]
print("HYPER_DOG_V6_SHA256",hashlib.sha256(PATH.read_bytes()).hexdigest(),flush=True)
for setting in range(1,7):
    per=[]
    for rep in range(2):
        x=simulate(setting,total,935731+setting*13571+rep*97103,p)
        i,o=x[0].sum(),x[1].sum()
        per.append({"setting":setting,"replicate":rep,"paid_games":int(x[23]),
          "return_percent":float(o/i*100),"normal_only_games":int(x[28]),
          "normal_only_hits":int(x[29]),"upper_comeback_hits":int(x[24]),
          "lower_comeback_hits":int(x[25]),"upper_comeback_windows":int(x[26]),
          "lower_comeback_windows":int(x[27]),"comeback_zero_bet_games":int(x[30]),
          "comeback_paid_games":int(x[31]),"in":int(i),"out":int(o)})
    total_in=sum(q["in"] for q in per);total_out=sum(q["out"] for q in per)
    ng=sum(q["normal_only_games"] for q in per);hits=sum(q["normal_only_hits"] for q in per)
    upper_hits=sum(q["upper_comeback_hits"] for q in per)
    upper_windows=sum(q["upper_comeback_windows"] for q in per)
    low_hits=sum(q["lower_comeback_hits"] for q in per)
    low_windows=sum(q["lower_comeback_windows"] for q in per)
    row={"setting":setting,"single_multiplier":float(p[7][setting-1]),
        "target_percent":d["fixed_requirements"]["target_setting_payout_percent"][setting-1],
        "return_percent":100*total_out/total_in,
        "normal_only_initial_1_in":ng/hits if hits else None,
        "upper_return_success_rate":upper_hits/upper_windows if upper_windows else None,
        "lower_return_success_rate":low_hits/low_windows if low_windows else None,
        "upper_returns":upper_hits,"lower_returns":low_hits,
        "comeback_zero_bet_games":sum(q["comeback_zero_bet_games"] for q in per),
        "paid_games":sum(q["paid_games"] for q in per),
        "replicates":per}
    results.append(row)
    print("HYPER_DOG_V6_VALIDATION",json.dumps({k:v for k,v in row.items() if k!="replicates"}),flush=True)
dest=pathlib.Path("runtime-evidence/HYPER_DOG_V6_CALIBRATION/verification.json")
dest.parent.mkdir(parents=True,exist_ok=True)
dest.write_text(json.dumps({"model_sha256":hashlib.sha256(PATH.read_bytes()).hexdigest(),
"model":"v6 upper-origin comeback", "rows":results},ensure_ascii=False,indent=2)+"\n")
print("HYPER_DOG_V6_VALIDATION_COMPLETE",len(results),flush=True)
