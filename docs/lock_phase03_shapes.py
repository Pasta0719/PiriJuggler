"""One-time, lossless §121 transcription; Python writes metadata, never pixels."""
import hashlib, json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
old = Path(sys.argv[1]).read_text(encoding='utf-8-sig') if len(sys.argv)>1 else (ROOT/'docs/v3-section-121.txt').read_text(encoding='utf-8')
source = re.search(r'^# 121\. .*?(?=^# 122\.|\Z)', old, re.M | re.S)[0]
(ROOT / 'docs/v3-section-121.txt').write_text(source, encoding='utf-8', newline='\n')
def shape(kind, coordinates, fill=None, stroke=None, width=8):
    return dict(kind=kind, coordinates=coordinates, fill=fill, stroke=stroke, width=width)
def circle(x,y,r,fill,stroke=None,width=8): return shape('ellipse',[x-r,y-r,2*r,2*r],fill,stroke,width)
def polygon(points,fill,stroke=None): return shape('polygon',points,fill,stroke)
def line(points,color,width): return shape('line',points,None,color,width)
shapes = {
 'seven': [polygon([[42,55],[220,55],[220,91],[137,211],[82,211],[161,99],[42,99]],'#E92A31','#711016'),
           polygon([[45,124],[200,104],[189,132],[55,151]],'#F3C743'),line([[68,72],[179,72]],'#FFFFFF',7)],
 'grape': [circle(x,y,25,'#6545C7','#30206B',6) for x,y in [(104,65),(137,65),(84,92),(118,94),(153,94),(72,124),(106,124),(141,124),(91,154),(126,154),(108,183)]] +
          [shape('ellipse',[144,35,66,38],'#39A755'),line([[142,60],[169,40]],'#35783C',10)],
 'cherry': [circle(x,y,43,'#E92A31','#77131A',7) for x,y in [(92,153),(159,159)]] +
           [line([[92,113],[132,52]],'#3D963E',10),line([[159,116],[132,52]],'#3D963E',10),shape('ellipse',[129,42,71,37],'#42A94E')],
 'bell': [shape('path',[['M',75,80],['C',75,48,181,48,181,80],['L',198,171],['C',203,192,53,192,58,171],['Z']],'#F6D33E','#8F6810'),
          line([[58,173],[198,173]],'#8F6810',8),circle(128,197,18,'#D5A51B')],
 'piero': [circle(128,132,72,'#F8E8CF','#4B2520'),polygon([[110,30],[145,30],[151,86],[104,86]],'#E53038'),
           polygon([[58,48],[109,30],[104,88],[69,94]],'#F5F5F5'),polygon([[145,30],[198,48],[187,94],[151,86]],'#F5F5F5'),
           circle(128,139,18,'#E5353E'),circle(101,118,8,'#000000'),circle(155,118,8,'#000000'),
           shape('arc',[94,135,68,46,200,140],None,'#7B2027',7)],
 'replay': [shape('ellipse',[67,102,124,82],'#E9862E'),circle(83,105,45,'#F19A3E'),shape('ellipse',[45,104,65,42],'#F5E1C0'),
            circle(76,91,6,'#000000'),circle(43,119,8,'#000000'),polygon([[57,68],[68,28],[88,73]],'#E9862E'),
            polygon([[87,70],[111,36],[116,88]],'#E9862E'),shape('path',[['M',174,130],['C',229,94,230,184,173,176]],None,'#E9862E',32)]
}
record = {
 'source': 'v3 SPEC.md §121 (verbatim in docs/v3-section-121.txt); imported by v4 SPEC.md §121',
 'sourceSha256': hashlib.sha256(source.encode()).hexdigest(), 'size':[256,256], 'outlineWidth':8, 'subjectBounds':[24,24,232,232],
 'shapes':shapes,
 'pieroCollar': {'y':195,'fills':['#E53038','#F3C743'],'description':'triangles at y=195, fill alternating; v3 specifies no count or x coordinates'},
 'replayTailTip': {'stroke':'#F5E1C0','width':20,'description':'last segment of the given tail curve; v3 specifies no split parameter'}
}
(ROOT/'docs/symbol-shapes-v3.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
