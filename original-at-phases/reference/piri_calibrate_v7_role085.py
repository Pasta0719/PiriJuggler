#!/usr/bin/env python3
"""Noninvasive v7 numerical candidate: upper 8/9/10x, rare-role AT overlap 85% of v6.
Current v6 specs and runtime are unchanged. Print recommended six solo hit multipliers.
"""
import json,pathlib,numpy as np
ROOT=pathlib.Path(__file__).resolve().parent
SOURCE=(ROOT/'audit_upper_entry_v6.py').read_text()
PATCH=('    if role==WEAK or role==WM: return min(2000,b*8),False\n'
       '    if role==CHANCE: return min(2000,b*9),False\n'
       '    if role==STRONG or role==SC: return min(2000,b*10),False\n'
       '    return 0,False')
assert SOURCE.count('    return b*5,False')==1
ns={'__file__':str(ROOT/'audit_upper_entry_v6.py'),'__name__':'_v7_calibration_'}
exec(compile(SOURCE.replace('    return b*5,False',PATCH),
             str(ROOT/'audit_upper_entry_v6.py'),'exec'),ns)
d=json.loads((ROOT/'pirijuggler_new_machine_calibrated_v6.json').read_text())
base=list(ns['make_params'](d))
linked=base[17].copy();linked[6:]*=0.85
base[17]=linked
TARGET=d['fixed_requirements']['target_setting_payout_percent']
results=[]
def eval_setting(setting,mult,frames,seeds):
    p=list(base);p[7]=base[7].copy();p[7][setting-1]=mult
    inn=out=net=episodes=at_start=normal=0
    for seed in seeds:
        x=ns['simulate'](setting,frames,seed,tuple(p))
        inn+=int(x[0].sum());out+=int(x[1].sum())
        episodes+=int(x[33]);net+=int(x[34])
        at_start+=int(x[9]);normal+=int(x[2][0])
    return {'return_percent':100*out/inn,
            'upper_mean_net':net/episodes,
            'upper_count':episodes,'normal_to_at_1_in':normal/at_start,
            'upper_1_in_normal':normal/episodes}
for s in range(1,7):
    lo=0.;hi=1.0
    seeds=[20261119+s*9991,20261119+s*9991+353]
    for step in range(10):
        mid=(lo+hi)*0.5
        row=eval_setting(s,mid,12000000,seeds)
        if row['return_percent']>TARGET[s-1]:hi=mid
        else:lo=mid
    choose=(lo+hi)*0.5
    result=eval_setting(s,choose,60000000,
                        [20261201+s*14563,20261201+s*14563+917])
    result.update(setting=s,estimated_single_multiplier=choose,
                  normal_role_at_scale=0.85,
                  target_percent=TARGET[s-1],
                  difference_pp=result['return_percent']-TARGET[s-1])
    print('V7_CALIBRATION '+json.dumps(result,ensure_ascii=False),flush=True)
    results.append(result)
out=pathlib.Path('runtime-evidence/HYPER_DOG_V7_4K_CANDIDATE/result.json')
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps({'model':'v7 what-if not applied to gameplay',
                           'upper_role_scales':{'weak':8,'chance':9,'strong':10},
                           'normal_role_at_scale':0.85,'rows':results},
                           ensure_ascii=False,indent=2)+'\n')
