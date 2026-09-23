"""NEXT Phase 05 final real Paper+Fabric audit for JUGGLER_GOD."""
from pathlib import Path
import datetime,json,os,shutil,sqlite3,subprocess,time
ROOT=Path(__file__).resolve().parents[1]; E=ROOT/'runtime-evidence/NEXT_PHASE_05'; RUN=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'); OUT=E/'attempts'/RUN; SERVER=E/'work'/('server-'+RUN); OUT.mkdir(parents=True,exist_ok=True); SERVER.mkdir(parents=True,exist_ok=True)
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
def session(active=True):
 rows=state().get('sessions',[])
 return next((s for s in rows if (not active or s.get('lifecycle')=='ACTIVE')),None)
def settled():
 s=session(); p=cli().get('publicState') or {}; return bool(s) and p.get('expectedNextClientSequence')==s.get('last_client_sequence',-2)+1
def wait(pred,label,timeout=150):
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
def tap(k):
 # Match the already-proven Phase 02 production input path: distinct physical key edges.
 action('key',key=k,action=1)
 action('key',key=k,action=0)
def command(text,expected):
 before=sum(expected in m for m in cli().get('messages',[])); action('command',text=text); wait(lambda:sum(expected in m for m in cli().get('messages',[]))>before,text)
def click():
 before=sum(p.get('type')=='OPEN_MACHINE' for p in cli().get('packets',[])); action('aim',x=0); action('click',x=0); wait(lambda:sum(p.get('type')=='OPEN_MACHINE' for p in cli().get('packets',[]))>before,'open')
def start_server(label):
 global server,sr
 plugins=SERVER/'plugins';plugins.mkdir(parents=True,exist_ok=True);shutil.copy2(prod['paper'],plugins);shutil.copy2(helper,plugins)
 (SERVER/'eula.txt').write_text('eula=true\n');(SERVER/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nenforce-secure-profile=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\ngenerate-structures=false\n')
 sr=OUT/f'server-{label}.json';h=(OUT/f'server-{label}.log').open('w',encoding='utf-8');handles.append(h);server=subprocess.Popen([JAVA,'-Xms512M','-Xmx1536M','-Dfile.encoding=UTF-8','-Dpiri.runtime.phase=next02',f'-Dpiri.runtime.serverResult={sr}','-jar',str(PAPER),'nogui'],cwd=SERVER,stdin=subprocess.PIPE,stdout=h,stderr=subprocess.STDOUT,text=True,creationflags=FLAGS);wait(lambda:'Done (' in log(OUT/f'server-{label}.log') and state().get('ready'),'Paper',180)
def stop_server():
 global server
 server.stdin.write('stop\n');server.stdin.flush();server.wait(timeout=60);check('Paper exits cleanly',server.returncode==0,server.returncode);server=None
try:
 start_server('first')
 cdir=E/'work'/'client-next05-game';cdir.mkdir(parents=True,exist_ok=True);(cdir/'options.txt').write_text('version:3953\nlang:en_us\nrenderDistance:2\nmaxFps:30\npauseOnLostFocus:false\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\nonboardAccessibility:false\n')
 cr=OUT/'client-result.json';h=(OUT/'client.log').open('w',encoding='utf-8');handles.append(h);client=subprocess.Popen(GRADLE+['-PruntimeAcceptance=true','-PruntimeScenario=next05-game',f'-PruntimeRun={RUN}','-PruntimeEvidencePhase=NEXT_PHASE_05',':runtime-test-client:runClient','--console=plain'],cwd=ROOT,stdout=h,stderr=subprocess.STDOUT,creationflags=FLAGS);wait(lambda:cli().get('connected') and cli().get('handshake'),'Fabric',90)
 action('aim',x=0);command('piri machine create JUGGLER_GOD','MACHINE_CREATED 1');click();wait(lambda:settled() and session()['game_state']=='SEATED_READY','seat');check('production successor opens',cli().get('screen')=='SlotScreen' and cli().get('machineType')=='JUGGLER_GOD')
 command('piritest fund','TEST_FUNDED');action('close');wait(lambda:not session(),'close');click();wait(lambda:settled() and session()['credit']==50,'reopen')
 command('piritest force god','TEST_FORCE_ARMED GOD');tap(32);wait(lambda:session()['game_state']=='NORMAL_BETTED','bet');tap(32);wait(lambda:settled() and session()['game_state']=='NORMAL_SPINNING','lever')
 check('Paper/Fabric agree on GOD freeze',session()['internal_role']=='GOD' and cli().get('godFreeze') is True)
 for k,m in [(263,1),(264,3)]:
  wait(lambda:cli().get('stopEnabled') is True,'stop enabled')
  tap(k);wait(lambda:settled() and session()['stopped_mask']==m,'stop')
 tap(262);wait(lambda:settled() and session()['game_state']=='BIG_READY','BIG ready');check('GOD payout/result agreement',session()['pay_display']==15 and session()['bonus_type']=='BIG' and (cli().get('publicState') or {}).get('gameState')=='BIG_READY')
 action('close');wait(lambda:not session(),'safe close');saved=state()['sessions'][0];check('successor state suspended for recovery',saved['game_state']=='BIG_READY',saved)
 action('exit');client.wait(timeout=60);client=None;stop_server()
 start_server('restart');rows=state().get('sessions',[]);check('restart safely recovers persisted successor session',any(s.get('machine_id')==1 and s.get('lifecycle')=='SUSPENDED_SAFE' and s.get('game_state')=='SEATED_READY' for s in rows),rows)
 with sqlite3.connect(SERVER/'plugins/PiriJuggler/piri.db') as db:
  row=db.execute('SELECT machine_type FROM machines WHERE id=1').fetchone()
 check('machine persistence keeps JUGGLER_GOD type',row and row[0]=='JUGGLER_GOD',row)
 manifest['passed']=True
except Exception as e: manifest['failure']=str(e);print('NEXT_PHASE05_RUNTIME_FAILURE '+str(e),flush=True)
finally:
 if client and client.poll() is None:
  try: action('exit');client.wait(timeout=45)
  except Exception: client.terminate()
 if server and server.poll() is None:
  try: stop_server()
  except Exception: server.terminate()
 for h in handles:h.close()
 save(OUT/'result.json',manifest);save(E/'result.json',manifest)
print('NEXT_PHASE05_RUNTIME='+('PASS' if manifest['passed'] else 'FAIL'),flush=True);raise SystemExit(0 if manifest['passed'] else 1)
