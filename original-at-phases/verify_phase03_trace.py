#!/usr/bin/env python3
"""Independent phase03 Python oracle: verify Java trace with v5 probability tables.
It recreates the production SplitMix64 stream but independently implements all
normal-stage role/mode/streak/AT precedence transitions. No Java model import.
"""
import json,math,pathlib,sys

ROOT=pathlib.Path(__file__).resolve().parents[1]
MODEL=json.loads((ROOT/"original-at-phases/reference/pirijuggler_new_machine_calibrated_v5.json").read_text())
NORMAL=MODEL["normal_mode"];COMMON=MODEL["normal_and_at_common_role_draw"]
PAY=MODEL["fixed_requirements"]["payout_regular_or_at"]
MODES=NORMAL["names"];ROLES=["MISS","REPLAY","GRAPE","WEAK_CHERRY","WATERMELON","CHANCE","STRONG_CHERRY","STRONG_CHANCE","BAR"]
MASK=(1<<64)-1
class Rng:
    def __init__(self,seed):self.seed=seed
    def next(self):
        self.seed=(self.seed+0x9e3779b97f4a7c15)&MASK
        z=self.seed
        z=((z^(z>>30))*0xbf58476d1ce4e5b9)&MASK
        z=((z^(z>>27))*0x94d049bb133111eb)&MASK
        z^=z>>31
        return (z>>11)*(2.**-53)
    def chance(self,p):return self.next()<p
    def pick(self,weights):
        assert abs(sum(weights.values())-100)<1e-9
        n=self.next()*100;acc=0
        for m in MODES:
            acc+=weights.get(m,0)
            if n<acc:return m
        return MODES[-1]

def role(rng):
    x=rng.next();c=0
    for k in ROLES:
        if k=="MISS":p=COMMON["miss_probability_per_game"]["NORMAL"]
        elif k=="GRAPE":p=COMMON["grape_probability_per_game"]["NORMAL"]
        else:p=COMMON["exclusive_base_role_probability_per_game"][k]
        c+=p
        if x<c:return k
    return "MISS"

def event(name,n):
    if name in ("REPLAY","GRAPE") and n>=3:return f"{name}_STREAK_{min(5,n)}"
    if name=="WEAK_CHERRY":return "WEAK_CHERRY_2PLUS_CONSECUTIVE" if n>=2 else "WEAK_CHERRY_SINGLE"
    if name=="WATERMELON":return "WATERMELON_2PLUS_CONSECUTIVE" if n>=2 else "WATERMELON_SINGLE"
    if name in ("STRONG_CHERRY","CHANCE","STRONG_CHANCE"):return name
    return None

def verify_entry(e,seed,credits):
    setting=e["setting"];rng=Rng(seed)
    mode=rng.pick(NORMAL["startup_initial_mode_weights_percent"])
    assert mode==e["initialMode"],(setting,"startup",mode,e["initialMode"])
    total=0;last=None;streak=0;free=False;hit_counts={v:0 for v in ("SINGLE","LINKED","BAR")}
    for row in e["rounds"]:
        total+=1
        effective_bet=0 if free else 3
        credits-=effective_bet
        free=False
        name=role(rng)
        streak=streak+1 if name==last else 1
        last=name
        trigger=event(name,streak)
        previous=mode
        base=NORMAL["base_single_at_hit_denominator_by_mode"][mode]
        mult=NORMAL["single_hit_probability_multiplier_by_setting"][str(setting)]
        single=rng.chance(min(.95,mult/base))
        linked=trigger is not None and rng.chance(
            NORMAL["role_linked_at_hit_percent"].get(trigger,0)/100.)
        selectors=NORMAL["mode_up_table_selector"]
        if trigger is not None and trigger in selectors:
            if rng.chance(NORMAL["mode_up_roll_percent_by_event"].get(trigger,0)/100.):
                mode=rng.pick(NORMAL["mode_up_destination_weights_percent_by_tier"][selectors[trigger]][mode])
        elif name=="MISS":
            table=NORMAL["miss_mode_drop"][mode]
            if rng.chance(table["probability_on_miss"]):mode=rng.pick(table["destinations"])
        hit="BAR" if name=="BAR" else "SINGLE" if single else "LINKED" if linked else "NONE"
        payout=PAY[name]["NORMAL"]
        data=dict(serial=total,role=name,bet=effective_bet,payout=payout,
                  trigger=trigger or "NONE",modeBefore=previous,modeAfter=mode,
                  single=single,linked=linked,hit=hit,creditsBeforeFinish=credits,
                  creditsAfterFinish=credits+payout)
        for key,value in data.items():
            assert row[key]==value,(setting,total,key,value,row[key])
        credits+=payout
        if hit!="NONE":
            hit_counts[hit]+=1
            free=False;last=None;streak=0
            exited=rng.pick(NORMAL["at_exit_mode_weights_percent_by_saved_at_entry_mode"][mode])
            assert exited==row.get("atExitMode"),(setting,total,"exit",exited,row.get("atExitMode"))
            mode=exited
        else:
            assert "atExitMode" not in row
            free=(name=="REPLAY")
    return dict(setting=setting,games=total,hits=hit_counts,final_mode=mode,final_credit=credits)

def main():
    import argparse
    p=argparse.ArgumentParser();p.add_argument("trace",nargs="?",default="");
    args=p.parse_args()
    if args.trace:input_file=pathlib.Path(args.trace)
    else:
        paths=list((ROOT/"paper/build").rglob("hyperdog-phase03-trace.json"))
        assert len(paths)==1,paths
        input_file=paths[0]
    trace=json.loads(input_file.read_text())
    all_results=[verify_entry(v,trace["seed"],trace["credits"]) for v in trace["settings"]]
    assert len(all_results)==6
    assert all(x["games"]==250 for x in all_results)
    print("HYPER_DOG_PHASE03_INDEPENDENT_PYTHON_V5_MODEL_PARITY=PASS",json.dumps(all_results))
    target=ROOT/"runtime-evidence/HYPER_DOG_PHASE_03"
    target.mkdir(parents=True,exist_ok=True)
    (target/"oracle-verification.json").write_text(json.dumps({"passed":True,"rows":all_results,
        "seed":trace["seed"],"source":str(input_file)},indent=2))
if __name__=="__main__":main()
