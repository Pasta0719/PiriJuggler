#!/usr/bin/env python3
"""Recalibrate only setting-specific independent AT hit multipliers after upper-return comeback.
The production v5 role odds, modes, CZ and all awards remain frozen. This is an evidence job.
"""
import argparse, json, pathlib, time
import numpy as np
from piri_simulate_full_v1 import make_params, simulate, validate

ROOT=pathlib.Path(__file__).resolve().parent

def measure(setting,multiplier,paid,seed,source):
    p=list(source)
    values=p[7].copy()
    values[setting-1]=multiplier
    p[7]=values
    r=simulate(setting,paid,seed,tuple(p))
    inns,outs,games=r[:3]
    starts=int(r[9]);n=int(r[23]);upper,lower,win_u,win_l=r[24:]
    return {
        "setting":setting,"multiplier":multiplier,"paid_games":int(n),
        "return_percent":float(100*outs.sum()/inns.sum()),
        "initial_at_count":int(starts),"normal_games":int(games[0]),
        "upper_comeback_returns":int(upper),"lower_comeback_returns":int(lower),
        "upper_comeback_windows":int(win_u),"lower_comeback_windows":int(win_l),
        "raw_in":int(inns.sum()),"raw_out":int(outs.sum())
    }

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--coarse-paid",type=int,default=1200000)
    parser.add_argument("--verify-paid",type=int,default=5000000)
    parser.add_argument("--iterations",type=int,default=9)
    parser.add_argument("--seed",type=int,default=820624)
    parser.add_argument("--output",default="runtime-evidence/HYPER_DOG_CALIBRATION/fit.json")
    args=parser.parse_args()
    data=json.loads((ROOT/"pirijuggler_new_machine_calibrated_v5.json").read_text())
    validate(data)
    params=make_params(data)
    targets=data["fixed_requirements"]["target_setting_payout_percent"]
    output={"model":"upper-origin comeback -> upper initial; lower-origin comeback -> lower initial; 32 paid G, 0BET has an additional roll",
      "baseline_v5_multipliers":data["normal_mode"]["single_hit_probability_multiplier_by_setting"],
      "coarse_paid_per_trial":args.coarse_paid,"validation_paid_per_trial":args.verify_paid,
      "settings":[]}
    for s in range(1,7):
        orig=float(params[7][s-1]);target=float(targets[s-1])
        seed=args.seed+s*10003
        # Common random numbers per candidate, and independent validation seed.
        lo,hi=0.0,orig
        floor=measure(s,lo,args.coarse_paid,seed,params)
        old=measure(s,hi,args.coarse_paid,seed,params)
        print("BASELINE",s,"target",target,"zero",round(floor["return_percent"],3),
              "old",round(old["return_percent"],3),flush=True)
        record={"setting":s,"target_percent":target,"zero_multiplier":floor,"old_multiplier":old,"trace":[]}
        if floor["return_percent"]>target:
            record["status"]="NOT_FITTABLE_WITH_NONNEGATIVE_SINGLE_HIT_ONLY"
            record["candidate_multiplier"]=0.
        elif old["return_percent"]<target:
            record["status"]="NEEDS_HIGHER_THAN_OLD_MULTIPLIER"
            record["candidate_multiplier"]=orig
        else:
            for i in range(args.iterations):
                m=(lo+hi)/2
                row=measure(s,m,args.coarse_paid,seed,params)
                record["trace"].append(row)
                if row["return_percent"]<target:lo=m
                else:hi=m
            record["status"]="CANDIDATE"
            record["candidate_multiplier"]=(lo+hi)/2
        record["heldout"]=measure(s,record["candidate_multiplier"],args.verify_paid,seed+17991,params)
        output["settings"].append(record)
        print("CANDIDATE",s,"m",round(record["candidate_multiplier"],7),
              "heldout_return",round(record["heldout"]["return_percent"],4),
              "target",target,"status",record["status"],flush=True)
    path=pathlib.Path(args.output);path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(output,ensure_ascii=False,indent=2)+"\n")
    print("CALIBRATION_MODEL_EVALUATED",path,flush=True)

if __name__=="__main__":main()
