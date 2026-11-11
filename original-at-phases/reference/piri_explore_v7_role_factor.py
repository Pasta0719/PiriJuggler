#!/usr/bin/env python3
"""v7 what-if only: identify smallest adjustment to normal rare-role AT overlaps.
Does not touch v6, runtime code, or any accepted gameplay.
"""
import json,pathlib,numpy as np
ROOT=pathlib.Path(__file__).resolve().parent
src=(ROOT/'audit_upper_entry_v6.py').read_text()
anchor='    return b*5,False'
patch=('    if role==WEAK or role==WM: return min(2000,b*8),False\n'
       '    if role==CHANCE: return min(2000,b*9),False\n'
       '    if role==STRONG or role==SC: return min(2000,b*10),False\n'
       '    return 0,False')
assert src.count(anchor)==1
ns={'__file__':str(ROOT/'audit_upper_entry_v6.py'),'__name__':'_v7_lower_bound_'}
exec(compile(src.replace(anchor,patch),str(ROOT/'audit_upper_entry_v6.py'),'exec'),ns)
d=json.loads((ROOT/'pirijuggler_new_machine_calibrated_v6.json').read_text())
p0=ns['make_params'](d)
for rare_factor in (1.0,0.95,0.9,0.85,0.8,0.75,0.7):
    for setting in (1,6):
        p=list(p0)
        # Preserve user-fixed replay/grape streak win chances (indices 0..5).
        p[17]=p0[17].copy(); p[17][6:]*=rare_factor
        p[7]=np.zeros(6)
        inn=out=episodes=amount=0
        for rep in range(2):
            x=ns['simulate'](setting,12000000,771190+rep*811+setting*297,tuple(p))
            inn+=int(x[0].sum());out+=int(x[1].sum())
            episodes+=int(x[33]);amount+=int(x[34])
        print('ROLE_FACTOR_FLOOR '+json.dumps({
         'factor':rare_factor,'setting':setting,
         'zero_solo_return_percent':out/inn*100,
         'upper_net_mean':amount/episodes,'upper_episodes':episodes}),flush=True)
