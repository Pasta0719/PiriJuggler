"""Verify deliverables against tests, user audio inputs, and the actual runtime jar hashes."""
import hashlib, json, subprocess, sys, xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile
root=Path(__file__).resolve().parents[1]
subprocess.run([sys.executable,str(root/'docs/verify_phase01_artifacts.py')],check=True)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf-8'))
results={phase:read(root/f'runtime-evidence/{phase}/result.json') for phase in ['PHASE_03','PHASE_03_PHASE01_REGRESSION']}
for result in results.values():
    assert result['passed'],result
    for side,expected in result['buildHashes'].items():assert sha(root/f'{side}/build/libs/piri-juggler-{side}-1.0.0.jar')==expected
counts={}
for module in ['common','paper','fabric','asset-tools']:
    suites=[ET.parse(p).getroot() for p in (root/module/'build/test-results/test').glob('TEST-*.xml')]
    assert suites,module
    assert all(not any(int(s.get(k,0)) for k in ['failures','errors','skipped']) for s in suites)
    counts[module]=sum(int(s.get('tests')) for s in suites)
images=read(root/'asset-tools/src/test/resources/image-sha256.json')
with ZipFile(root/'fabric/build/libs/piri-juggler-fabric-1.0.0.jar') as z:
    for name,expected in images.items():assert hashlib.sha256(z.read('assets/piri/textures/'+name)).hexdigest()==expected
    registry=json.loads(z.read('assets/piri/sounds.json'));assert len(registry)==10
    supplied=[]
    for name in registry:
        assert registry[name]['sounds']==['piri:'+name]
        primary=root/'user-audio'/(name+'.ogg');secondary=root/'fabric/src/main/resources/assets/piri/sounds'/(name+'.ogg')
        source=primary if primary.is_file() else secondary
        target='assets/piri/sounds/'+name+'.ogg'
        if source.is_file():assert z.read(target)==source.read_bytes();supplied.append(name)
        else:assert target not in z.namelist(), 'Removed audio persisted in JAR: '+name
    assert 'piri.mixins.json' in z.namelist()
    assert not any('AudioAssetGenerator' in n or 'vorbis' in n.lower() for n in z.namelist())
for side in ['paper','fabric']:
    assert sha(root/f'dist/piri-juggler-{side}-1.0.0.jar')==sha(root/f'{side}/build/libs/piri-juggler-{side}-1.0.0.jar')
for source in (root/'asset-tools/src').rglob('*.java'):assert 'AudioAssetGenerator' not in source.name
assert 'vorbis' not in (root/'asset-tools/build.gradle.kts').read_text().lower()
assert 'regeneratePiriAudio' not in (root/'build.gradle.kts').read_text()
with ZipFile(root/'asset-tools/build/libs/asset-tools-1.0.0.jar') as z:assert not any('AudioAssetGenerator' in n for n in z.namelist())
summary={'passed':True,'tests':counts,'totalTests':sum(counts.values()),'imageCount':len(images),'soundEventCount':len(registry),'suppliedAudio':supplied,'runtimeAssertions':len(results['PHASE_03']['assertions']),'phase01Regression':True,'buildHashes':results['PHASE_03']['buildHashes']}
(root/'runtime-evidence/PHASE_03/artifact-verification.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(summary,ensure_ascii=False))
