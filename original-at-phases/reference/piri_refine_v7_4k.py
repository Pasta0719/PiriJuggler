#!/usr/bin/env python3
"""Robust independent validation of v7 upper-4k and six payout targets (proposal).
No existing v6 files or live game code are changed by this computation.
"""
import json,pathlib,numpy as np
ROOT=pathlib.Path(__file__).resolve().parent
src=(ROOT/'audit_upper_entry_v6.py').read_text()
old='    return b*5,False'
patch=('    if role==WEAK or role==WM: return min(2000,b*8),False\n'
       '    if role==CHANCE: return min(2000,b*9),False\n'
       '    if role==STRONG or role==SC: return min(2000,b*10),False\n'
       '    return 0,False')
assert src.count(old)==1
ns={'__name__':'_refined_v7_','__file__':str(ROOT/'audit_upper_entry_v6.py')}
exec(compile(src.replace(old,patch),str(ROOT/'audit_upper_entry_v6.py'),'exec'),ns)
cfg=json.loads((ROOT/'pirijuggler_new_machine_calibrated_v6.json').read_text())
p0=list(ns['make_params'](cfg))
p0[17]=p0[17].copy();p0[17][6:]*=0.85
current=[.03759765625,.05322265625,.09912109375,.14111328125,.17919921875,.28759765625]
steps=[.015,.018,.020,.025,.030,.045]
targets=cfg['fixed_requirements']['target_setting_payout_percent']
out=[]
def trial(setting,mult,nframes,seeds):
    p=list(p0);p[7]=p0[7].copy();p[7][setting-1]=mult
    paid=inn=outs=episodes=upper_gain=initial=normal=0
    for seed in seeds:
        x=ns['simulate'](setting,nframes,seed,tuple(p))
        paid+=int(sum(x[2]));inn+=int(x[0].sum());outs+=int(x[1].sum())
        episodes+=int(x[33]);upper_gain+=int(x[34])
        initial+=int(x[9]);normal+=int(x[2][0])
    return {'return_percent':100*outs/inn,
            'upper_net_medals_mean':upper_gain/episodes,
            'upper_episodes':episodes,'frames':paid,
            'normal_first_at_1_in':normal/initial,
            'upper_entry_1_in_normal':normal/episodes}
for setting in range(1,7):
    orig=current[setting-1];step=steps[setting-1]
    candidates=[max(0.0,orig+step*k) for k in (-2,-1,0,1,2)]
    search_seeds=[310002+setting*683,310034+setting*683]
    measurements=[]
    for multiplier in candidates:
        result=trial(setting,multiplier,20000000,search_seeds)
        measurements.append({'mult':multiplier,'ret':result['return_percent']})
    x=np.array([q['mult'] for q in measurements]);y=np.array([q['ret'] for q in measurements])
    slope,intercept=np.polyfit(x,y,1)
    proposed=float(np.clip((targets[setting-1]-intercept)/slope,0.,1.))
    hold=trial(setting,proposed,60000000,[852642+setting*691,852979+setting*691])
    corrected=float(np.clip(proposed+(targets[setting-1]-hold['return_percent'])/slope,0.,1.))
    final=trial(setting,corrected,120000000,[2140379+setting*18071,2140660+setting*18071])
    row={'setting':setting,'target_percent':targets[setting-1],
         'single_multiplier':round(corrected,6),
         'rare_role_hit_scale':0.85,
         'calibration_points':measurements,
         'slope_pp_per_multiplier':float(slope),
         'holdout':hold,'final':final,
         'difference_pp':final['return_percent']-targets[setting-1]}
    print('V7_REFINED '+json.dumps({k:v for k,v in row.items() if k!='calibration_points'},ensure_ascii=False),flush=True)
    out.append(row)
file=pathlib.Path('runtime-evidence/HYPER_DOG_V7_4K_REFINED/validation.json')
file.parent.mkdir(parents=True,exist_ok=True)
file.write_text(json.dumps({'description':'Proposed v7, not applied; upper role scales weak=8 chance=9 strong=10, 0.85 normal rare-linked AT odds; streak overrides, bar, CZ, bet, upper grape unchanged.',
    'settings':out},ensure_ascii=False,indent=2)+'\n')
