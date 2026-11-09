#!/usr/bin/env python3
"""Non-mutating exploratory upper award simulations; read v6 game engine source only."""
import json
import pathlib
import numpy as np

HERE=pathlib.Path(__file__).resolve().parent
base=(HERE/'audit_upper_entry_v6.py').read_text()
model=json.loads((HERE/'pirijuggler_new_machine_calibrated_v6.json').read_text())
CASES=[
 ("v6",5,5,5),
 ("all_8x",8,8,8),
 ("all_9x",9,9,9),
 ("weak8_chance8_strong9",8,8,9),
 ("weak9_chance8_strong8",9,8,8),
 ("weak8_chance9_strong10",8,9,10),
 ("weak7_chance10_strong11",7,10,11),
]
for label,weak,chance,strong in CASES:
    instr='    return b*5,False'
    patch=(f'    if role==WEAK or role==WM: return min(2000,b*{weak}),False\n'
           f'    if role==CHANCE: return min(2000,b*{chance}),False\n'
           f'    if role==STRONG or role==SC: return min(2000,b*{strong}),False\n'
           '    return 0,False')
    source=base.replace(instr,patch)
    assert source!=base
    namespace={'__name__':'_exploratory_','__file__':str(HERE/'audit_upper_entry_v6.py')}
    exec(compile(source,str(HERE/'audit_upper_entry_v6.py'),'exec'),namespace)
    params=namespace['make_params'](model)
    for setting in (1,6):
        for solo in ("v6","zero"):
            p=list(params)
            p[7]=params[7].copy()
            if solo=="zero":p[7][:]=0
            total_paid=0;upper_sessions=0;upper_net=0;at_starts=0;total_in=0;total_out=0;normal_g=0
            for rep in range(2):
                x=namespace['simulate'](setting,5000000,808041+setting*771+rep*503,tuple(p))
                total_paid+=int(sum(x[2]));upper_sessions+=int(x[33])
                upper_net+=int(x[34]);at_starts+=int(x[9]);total_in+=int(sum(x[0]));total_out+=int(sum(x[1]));normal_g+=int(x[2][0])
            assert upper_sessions>0
            print("EXPLORE "+json.dumps({
                "case":label,"weak_x":weak,"chance_x":chance,"strong_x":strong,
                "setting":setting,"solo":solo,
                "upper_episodes":upper_sessions,"upper_mean_net":upper_net/upper_sessions,
                "gross_return_percent":100*total_out/total_in,
                "AT_starts":at_starts,"upper_entries_per_normal_g":upper_sessions/normal_g
            }),flush=True)
