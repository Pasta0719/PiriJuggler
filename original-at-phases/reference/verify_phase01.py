#!/usr/bin/env python3
"""Read-only numerical baseline verifier for HYPER_DOG, no tuning or simulation."""
import hashlib, json, math
from pathlib import Path

root=Path(__file__).resolve().parent
model_file=root/"pirijuggler_new_machine_calibrated_v5.json"
simulator_file=root/"piri_simulate_full_v1.py"
checks=[]
def check(name,passed):
    checks.append((name,bool(passed)))
    if not passed: print("FAIL",name)
def weights_ok(obj):
    return math.isclose(sum(obj.values()),100,rel_tol=0,abs_tol=1e-8)
def visit(name,obj):
    if isinstance(obj,dict):
        if obj and all(type(v) in (int,float) for v in obj.values()) and (
            "weights_percent" in name or name.endswith("/medal_weights") or name.endswith("/destination_percent")):
            check("distribution "+name,weights_ok(obj))
        for key,value in obj.items():visit(name+"/"+str(key),value)
    if isinstance(obj,list):
        for i,value in enumerate(obj):visit(name+"/"+str(i),value)
check("v5 SHA256",hashlib.sha256(model_file.read_bytes()).hexdigest()=="af1ae6f88e6593116692f987534c406673fbbfa73cb25f52615a7f1674c20ab3")
check("simulator SHA256",hashlib.sha256(simulator_file.read_bytes()).hexdigest()=="f8ad162cb844bd31e37058732f960ea9db88c0e7e66290acc061e61f253d0664")
data=json.loads(model_file.read_text(encoding="utf-8"))
f=data["fixed_requirements"]
roles={"MISS":0,"REPLAY":0,"GRAPE":3,"WEAK_CHERRY":1,"WATERMELON":5,"CHANCE":5,"STRONG_CHERRY":1,"STRONG_CHANCE":5,"BAR":15}
check("3 BET",f["bet_per_paid_game"]==3)
check("all nine roles",set(f["payout_regular_or_at"])==set(roles))
check("NORMAL payouts",all(f["payout_regular_or_at"][r]["NORMAL"]==p for r,p in roles.items()))
check("AT GRAPE 30",f["payout_regular_or_at"]["GRAPE"]["LOWER_AT"]==f["payout_regular_or_at"]["GRAPE"]["UPPER_AT"]==30)
check("0BET special",f["special_stage"]["bet"]==f["special_stage"]["payout_all_roles"]==0 and f["special_stage"]["bar_not_eligible"])
check("lower initial",f["lower_initial_games"]==1 and f["lower_initial_default_medals"]==150)
check("upper initial",f["upper_initial_games"]==5 and f["upper_initial_guarantee_medals_per_game"]==100 and f["upper_initial_guarantee_total"]==500)
check("setting targets",f["target_setting_payout_percent"]==[97,99.3,102.5,104.8,107.4,116])
check("CZ 5G",f["upper_cz_max_games"]==5)
cz=data["upper_cz"]
check("CZ 50%",math.isclose(1-(1-cz["independent_p_success_each_game"])**5,.5,rel_tol=0,abs_tol=1e-12))
base=data["normal_and_at_common_role_draw"]
for mode in ("NORMAL","LOWER_AT","UPPER_AT"):
    check("role total "+mode,math.isclose(sum(base["exclusive_base_role_probability_per_game"].values())+base["grape_probability_per_game"][mode]+base["miss_probability_per_game"][mode],1,rel_tol=0,abs_tol=1e-12))
check("BAR 1/65536",base["exclusive_base_role_probability_per_game"]["BAR"]==1/65536)
check("upper miss rewrite 1/32",base["upper_miss_to_rare_role_rewrite"]["conditional_probability_on_miss"]==1/32)
normal=data["normal_mode"]
modes=set(normal["names"])
check("six modes",len(modes)==6)
check("startup total",weights_ok(normal["startup_initial_mode_weights_percent"]) and set(normal["startup_initial_mode_weights_percent"])==modes)
check("six setting multipliers",set(normal["single_hit_probability_multiplier_by_setting"])==set("123456"))
check("normal denominators",set(normal["base_single_at_hit_denominator_by_mode"])==modes and all(v>0 for v in normal["base_single_at_hit_denominator_by_mode"].values()))
check("linked hit odds",all(0<=v<=100 for v in normal["role_linked_at_hit_percent"].values()))
check("mode roll odds",all(0<=v<=100 for v in normal["mode_up_roll_percent_by_event"].values()))
for tier,src in normal["mode_up_destination_weights_percent_by_tier"].items():
    for mode,dist in src.items():check("mode transition "+tier+"/"+mode,weights_ok(dist) and set(dist)<=modes)
for mode,dist in normal["at_exit_mode_weights_percent_by_saved_at_entry_mode"].items():check("AT exit "+mode,weights_ok(dist) and set(dist)<=modes)
for mode,b in normal["miss_mode_drop"].items():
    check("miss drop chance "+mode,0<=b["probability_on_miss"]<=1)
    if b["destinations"]:check("miss drop destinations "+mode,weights_ok(b["destinations"]) and set(b["destinations"])<=modes)
for key in ("lower_at","upper_at","upper_cz"):visit(key,data[key])
check("upper 5x",f["upper_additive_award_scale_from_lower"]==5)
check("strong rare +2000",data["upper_at"]["regular_addons"]["strong_cherry_2000_override_percent"]==2 and data["upper_at"]["regular_addons"]["strong_chance_2000_override_percent"]==5)
come=data["after_at_comeback"]
check("comeback 32G",come["duration_paid_games"]==32)
check("comeback 20%",math.isclose(1-(1-come["independent_extra_at_hit_probability_each_game"])**32,.2,rel_tol=0,abs_tol=1e-12))
fails=[n for n,passed in checks if not passed]
print("HYPER_DOG_PHASE01",len(checks),"checks",len(fails),"failures")
if fails:raise SystemExit(1)
