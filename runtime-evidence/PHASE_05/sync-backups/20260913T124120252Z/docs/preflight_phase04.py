"""Independent enumeration of the literal §4/5/6/12/13/30 rules."""
import itertools,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
lock=json.loads((root/'docs/spec-lock.json').read_text(encoding='utf-8'))
reels=[lock['reelArrays'][r] for r in ['LEFT_REEL','CENTER_REEL','RIGHT_REEL']]
lines=[(0,0,0),(-1,-1,-1),(1,1,1),(-1,0,1),(1,0,-1)]
patterns={'GRAPE':('GRAPE',)*3,'BELL':('BELL',)*3,'PIERO':('PIERO',)*3,'REPLAY':('REPLAY',)*3,'BIG_ENTRY':('SEVEN',)*3,'REG_ENTRY':('SEVEN','SEVEN','BAR')}
candidates={k:[] for k in [*patterns,'CHERRY','MISS','PREMIUM_B']}
rank={};partial={}
for stops in itertools.product(range(21),repeat=3):
    wins={role:[i for i,line in enumerate(lines) if tuple(reels[r][(stops[r]+line[r])%21] for r in range(3))==pattern] for role,pattern in patterns.items()}
    cherry=[reels[0][(stops[0]+row)%21]=='CHERRY' for row in [-1,0,1]]
    if not any(cherry):
        for role in patterns:
            if len(wins[role])==1 and sum(map(len,wins.values()))==1:candidates[role].append(stops);rank[role,stops]=wins[role][0]
        if not any(wins.values()):candidates['MISS'].append(stops);rank['MISS',stops]=5
    if not any(wins.values()):
        if cherry[0]!=cherry[2] and not cherry[1]:candidates['CHERRY'].append(stops);rank['CHERRY',stops]=1 if cherry[0] else 2
        if cherry[1] and not(cherry[0] or cherry[2]):candidates['PREMIUM_B'].append(stops);rank['PREMIUM_B',stops]=0
counts={role:len(values) for role,values in candidates.items()}
minimum={'GRAPE':750,'BELL':50,'PIERO':20,'REPLAY':525,'CHERRY':1514,'MISS':5250,'BIG_ENTRY':10,'REG_ENTRY':10,'PREMIUM_B':1}
print('Literal strict candidate counts:',json.dumps(counts),flush=True)
print('Minimum checks:',json.dumps({k:counts[k]>=v for k,v in minimum.items()}),flush=True)
failures=[]
for role in ['MISS','CHERRY','PIERO']:
    for order in itertools.permutations(range(3)):
        first,second,_=order
        for pressed1 in range(21):
            choice=min(candidates[role],key=lambda s:((pressed1-s[first])%21,rank[role,s],*s))
            base=[s for s in candidates[role] if s[first]==choice[first]]
            filtered=[s for s in base if not any(reels[first][(s[first]+line[first])%21]=='SEVEN' and reels[second][(s[second]+line[second])%21]=='SEVEN' for line in lines)]
            if not filtered:failures.append({'role':role,'order':order,'firstPressedIndex':pressed1,'firstStop':choice[first],'remainingBeforeFilter':len(base),'remainingCandidates':base})
print('Premium F empty-filter cases:',len(failures),flush=True)
result={'triplets':21**3,'counts':counts,'minimums':minimum,'minimumPass':{k:counts[k]>=v for k,v in minimum.items()},'premiumFCheckedCases':3*6*21*21,'premiumFEmptyFirstStopCases':failures}
out=root/'runtime-evidence/PHASE_04';out.mkdir(parents=True,exist_ok=True)
(out/'preflight-enumeration.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
if not all(result['minimumPass'].values()) or failures:
    raise SystemExit('Phase04 candidate preflight failed; see preflight-enumeration.json')
