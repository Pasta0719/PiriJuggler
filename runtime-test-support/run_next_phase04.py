"""NEXT Phase 04 real Paper+Fabric presentation acceptance for JUGGLER_GOD."""
from pathlib import Path
import datetime,json,os,shutil,subprocess,time
ROOT=Path(__file__).resolve().parents[1]; E=ROOT/'runtime-evidence/NEXT_PHASE_04'; RUN=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'); OUT=E/'attempts'/RUN; SERVER=E/'work'/('server-'+RUN); OUT.mkdir(parents=True,exist_ok=True); SERVER.mkdir(parents=True,exist_ok=True)
JAVA=shutil.which('java'); PAPER=ROOT/'runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar'; FLAGS=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0; GRADLE=['cmd.exe','/d','/c',str(ROOT/'gradlew.bat')] if os.name=='nt' else [str(ROOT/'gradlew')]
prod={s:ROOT/s/f'build/libs/piri-juggler-{s}-1.0.0.jar' for s in ('paper','fabric')}; helper=ROOT/'runtime-test-support/paper/build/libs/piri-runtime-test-paper-1.0.0.jar'; manifest={'run':RUN,'passed':False,'assertions':[]}; cache={}; server=client=None; sr=cr=None; handles=[]; seq=0
def save(p,v): p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(v,ensure_ascii=False,indent=2),encoding='utf-8')
def load(p):
 try: cache[p]=json.loads(p.read_text(encoding='utf-8'))
 except Exception: pass
 return cache.get(p,{})
def log(p): return p.read_text(encoding='utf-8',errors='replace') if p.exists() else ''
def cli(): return load(cr)
def state(): return load(sr)
def session(): return next((s for s in state().get('sessions',[]) if s.get('lifecycle')=='ACTIVE'),None)
def settled():
 s=session(); p=cli().get('publicState') or {}; return bool(s) and p.get('expectedNextClientSequence')==s.get('last_client_sequence',-2)+1
def wait(pred,label,timeout=120):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  if pred(): return
  if server and server.poll() is not None: raise RuntimeError('Paper exited: '+label)
  if client and client.poll() is not None: raise RuntimeError('Fabric exited: '+label)
  f=cli().get('failure') if cr else None
  if f: raise RuntimeError(f)
  time.sleep(.2)
 raise TimeoutError(label)
def check(name,ok,evidence=None):
 manifest['assertions'].append({'name':name,'passed':bool(ok),'evidence':evidence}); print(('PASS ' if ok else 'FAIL ')+name,flush=True); save(E/'result.json',manifest)
 if not ok: raise AssertionError(name)
def action(kind,**kw):
 global seq
 seq+=1; save(cr.with_name(f'command-{seq}.json'),{'id':seq,'kind':kind,**kw}); wait(lambda:cli().get('completed',0)>=seq,'action '+kind)
def tap(key): action('key',key=key,action=1); action('key',key=key,action=0)
def command(text,expected):
 before=sum(expected in m for m in cli().get('messages',[])); action('command',text=text); wait(lambda:sum(expected in m for m in cli().get('messages',[]))>before,text)
def click():
 before=sum(p.get('type')=='OPEN_MACHINE' for p in cli().get('packets',[])); action('aim',x=0); action('click',x=0); wait(lambda:sum(p.get('type')=='OPEN_MACHINE' for p in cli().get('packets',[]))>before,'open')
def capture(label): action('capture',label=label); time.sleep(1)
try:
 plugins=SERVER/'plugins'; plugins.mkdir(parents=True,exist_ok=True); shutil.copy2(prod['paper'],plugins); shutil.copy2(helper,plugins); (SERVER/'eula.txt').write_text('eula=true\n'); (SERVER/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25598\nonline-mode=false\nenforce-secure-profile=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\ngenerate-structures=false\n')
 sr=OUT/'server-result.json'; sh=(OUT/'server.log').open('w',encoding='utf-8'); handles.append(sh); server=subprocess.Popen([JAVA,'-Xms512M','-Xmx1536M','-Dfile.encoding=UTF-8','-Dpiri.runtime.phase=next02',f'-Dpiri.runtime.serverResult={sr}','-jar',str(PAPER),'nogui'],cwd=SERVER,stdin=subprocess.PIPE,stdout=sh,stderr=subprocess.STDOUT,text=True,creationflags=FLAGS); wait(lambda:'Done (' in log(OUT/'server.log') and state().get('ready'),'Paper',180)
 cdir=E/'work'/'client-next04-main'; cdir.mkdir(parents=True,exist_ok=True); (cdir/'options.txt').write_text('version:3953\nlang:en_us\nrenderDistance:2\nmaxFps:30\npauseOnLostFocus:false\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\nonboardAccessibility:false\n')
 cr=OUT/'client-result.json'; ch=(OUT/'client.log').open('w',encoding='utf-8'); handles.append(ch); client=subprocess.Popen(GRADLE+['-PruntimeAcceptance=true','-PruntimeScenario=next04-main',f'-PruntimeRun={RUN}','-PruntimeEvidencePhase=NEXT_PHASE_04',':runtime-test-client:runClient','--console=plain'],cwd=ROOT,stdout=ch,stderr=subprocess.STDOUT,creationflags=FLAGS); wait(lambda:cli().get('connected') and cli().get('handshake'),'Fabric',90)
 action('aim',x=0); command('piri machine create JUGGLER_GOD','MACHINE_CREATED 1'); click(); wait(lambda:settled() and session()['game_state']=='SEATED_READY','seat'); check('production SlotScreen opens successor',cli().get('screen')=='SlotScreen' and cli().get('machineType')=='JUGGLER_GOD',{'screen':cli().get('screen'),'machineType':cli().get('machineType')}); capture('open')
 command('piritest fund','TEST_FUNDED'); action('close'); wait(lambda:not session() or session().get('lifecycle')!='ACTIVE','close'); click(); wait(lambda:settled() and session()['credit']==50,'reopen'); command('piritest force god','TEST_FORCE_ARMED GOD'); tap(32); wait(lambda:session()['game_state']=='NORMAL_BETTED','bet'); tap(32); wait(lambda:settled() and session()['game_state']=='NORMAL_SPINNING' and cli().get('stopEnabled'),'GOD lever'); check('authoritative GOD blackout state reaches client',cli().get('godFreeze') is True and cli().get('godRevealed')==[False,False,False],{'godFreeze':cli().get('godFreeze'),'godRevealed':cli().get('godRevealed')}); capture('god-blackout')
 tap(263); wait(lambda:cli().get('godRevealed',[False]*3)[0] is True,'left reveal'); check('first stop reveals only first GOD reel',cli().get('godRevealed')==[True,False,False],cli().get('godRevealed')); capture('god-left-reveal'); tap(264); wait(lambda:cli().get('godRevealed',[False]*3)[:2]==[True,True],'center reveal'); capture('god-two-reveal'); tap(262); wait(lambda:settled() and session()['game_state']=='BIG_READY','GOD settle'); capture('god-big-ready')
 public=cli().get('publicState') or {}; check('GOD presentation did not choose result',session().get('bonus_type')=='BIG' and public.get('bonusType')=='BIG',{'serverBonus':session().get('bonus_type'),'clientBonus':public.get('bonusType'),'gameState':session().get('game_state')}); manifest['passed']=True
except Exception as e: manifest['failure']=str(e); print('NEXT_PHASE04_RUNTIME_FAILURE '+str(e),flush=True)
finally:
 if client and client.poll() is None:
  try: action('exit'); client.wait(timeout=45)
  except Exception: client.terminate()
 if server and server.poll() is None:
  try: server.stdin.write('stop\n');server.stdin.flush();server.wait(timeout=45)
  except Exception: server.terminate()
 for h in handles: h.close()
 shots=cdir/'screenshots' if 'cdir' in globals() else None
 if shots and shots.exists():
  for p in shots.glob('next04-*.png'): shutil.copy2(p,OUT/p.name)
 save(OUT/'result.json',manifest); save(E/'result.json',manifest)
print('NEXT_PHASE04_RUNTIME='+('PASS' if manifest['passed'] else 'FAIL'),flush=True); raise SystemExit(0 if manifest['passed'] else 1)
