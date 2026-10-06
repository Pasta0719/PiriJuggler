"""Phase13 real-Minecraft entity-free world cabinet renderer acceptance."""
from pathlib import Path
import datetime, hashlib, json, os, shutil, subprocess, time, re

ROOT=Path(__file__).resolve().parents[1]; E=ROOT/"runtime-evidence/PHASE_13"
RUN=datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ"); OUT=E/"attempts"/RUN
SERVER=E/"work"/("server-"+RUN); JAVA=shutil.which("java")
PAPER=ROOT/"runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar"
GRADLE=["cmd.exe","/d","/c",str(ROOT/"gradlew.bat")] if os.name=="nt" else [str(ROOT/"gradlew")]
FLAGS=subprocess.CREATE_NO_WINDOW if os.name=="nt" else 0
prod={s:ROOT/s/f"build/libs/piri-juggler-{s}-1.0.0.jar" for s in ("paper","fabric")}
helper={s:ROOT/f"runtime-test-support/{m}/build/libs/piri-runtime-test-{s}-1.0.0.jar" for s,m in (("paper","paper"),("client","client"))}
OUT.mkdir(parents=True,exist_ok=True); (E/"screenshots").mkdir(parents=True,exist_ok=True)
cache={}; clients={}; handles=[]; server=None; server_result=OUT/"server-result.json"
manifest={"run":RUN,"passed":False,"assertions":[],"commands":[]}

def save(p,v): p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(v,ensure_ascii=False,indent=2),encoding="utf-8")
def load(p):
 try: cache[p]=json.loads(p.read_text(encoding="utf-8"))
 except (FileNotFoundError,json.JSONDecodeError,PermissionError): pass
 return cache.get(p,{})
def log(p): return p.read_text(encoding="utf-8",errors="replace") if p.exists() else ""
def state(): return load(server_result)
def client(n): return load(clients[n][1])
def packets(n,k): return [p["payload"] for p in client(n).get("packets",[]) if p["type"]==k]
def pcount(n,k): return len(packets(n,k))
def msgcount(n,s): return sum(s in x for x in client(n).get("messages",[]))
def wait(pred,label,timeout=180):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  if pred(): return
  if server and server.poll() is not None: raise RuntimeError("Paper exited during "+label+"\n"+log(OUT/"server.log")[-8000:])
  for n,(p,path,_) in list(clients.items()):
   if p.poll() is not None: raise RuntimeError(f"{n} exited {p.returncode} during {label}\n"+log(OUT/n/"client.log")[-8000:])
   if load(path).get("failure"): raise RuntimeError(n+": "+str(load(path)["failure"]))
  time.sleep(.2)
 raise TimeoutError(label)
def check(name,ok,evidence=None):
 manifest["assertions"].append({"name":name,"passed":bool(ok),"evidence":evidence}); print(("PASS " if ok else "FAIL ")+name,flush=True); save(E/"result.json",manifest)
 if not ok: raise AssertionError(name)
def action(n,kind,**kw):
 p,path,seq=clients[n]; seq+=1; clients[n]=(p,path,seq); save(path.with_name(f"command-{seq}.json"),{"id":seq,"kind":kind,**kw})
 wait(lambda:client(n).get("completed",0)>=seq,n+" "+kind); manifest["commands"].append({"client":n,"kind":kind,**kw})
def command(n,text,expected=None):
 before=msgcount(n,expected) if expected else 0; action(n,"command",text=text)
 if expected: wait(lambda:msgcount(n,expected)>before,text+" -> "+expected)
def tap(n,key): action(n,"key",key=key,action=1); action(n,"key",key=key,action=0)
def session(player="PiriRuntimeTest"):
 for s in state().get("sessions",[]):
  if s.get("lifecycle")=="ACTIVE" and s.get("player_uuid"):
   return s
 return {}
def settled(n):
 vals=packets(n,"PUBLIC_STATE"); p=vals[-1] if vals else {}
 return bool(session()) and p.get("expectedNextClientSequence")==session().get("last_client_sequence",-2)+1
def stop_reels(n):
 for key,mask in [(263,1),(264,3),(262,7)]:
  wait(lambda:settled(n),"settled before stop",30)
  tap(n,key)
  wait(lambda:session().get("stopped_mask")==mask,"stop mask "+str(mask),40)
def start_server():
 global server
 plugins=SERVER/"plugins"; plugins.mkdir(parents=True,exist_ok=True); shutil.copy2(prod["paper"],plugins); shutil.copy2(helper["paper"],plugins)
 (SERVER/"eula.txt").write_text("eula=true\n"); (SERVER/"server.properties").write_text("\n".join(["server-ip=127.0.0.1","server-port=25591","online-mode=false","enforce-secure-profile=false","max-players=4","view-distance=6","simulation-distance=5","spawn-protection=0","generate-structures=false"])+"\n")
 h=(OUT/"server.log").open("w",encoding="utf-8"); handles.append(h)
 server=subprocess.Popen([JAVA,"-Xms512M","-Xmx1536M","-Dpiri.runtime.phase=phase13",f"-Dpiri.runtime.serverResult={server_result}","-jar",str(PAPER),"nogui"],cwd=SERVER,stdin=subprocess.PIPE,stdout=h,stderr=subprocess.STDOUT,text=True,creationflags=FLAGS)
 wait(lambda:"Done (" in log(OUT/"server.log") and "PIRI_DATABASE_READY" in log(OUT/"server.log"),"Paper ready",300)
def console(text):
 server.stdin.write(text+"\n"); server.stdin.flush(); time.sleep(.3)
def start_client(n):
 folder=OUT/n; folder.mkdir(parents=True,exist_ok=True); result=folder/"client-result.json"
 work=E/"work"/("client-"+n)
 if work.exists(): shutil.rmtree(work)
 work.mkdir(parents=True); (work/"options.txt").write_text("version:3953\nlang:en_us\nrenderDistance:6\nsimulationDistance:5\nmaxFps:30\npauseOnLostFocus:false\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\nonboardAccessibility:false\n")
 h=(folder/"client.log").open("w",encoding="utf-8"); handles.append(h)
 cmd=GRADLE+["-PruntimeAcceptance=true",f"-PruntimeScenario={n}",f"-PruntimeRun={RUN}","-PruntimeEvidencePhase=PHASE_13",":runtime-test-client:runClient","--console=plain"]
 p=subprocess.Popen(cmd,cwd=ROOT,stdout=h,stderr=subprocess.STDOUT,creationflags=FLAGS); clients[n]=(p,result,0)
 wait(lambda:client(n).get("connected") and client(n).get("handshake"),n+" join",600)
def stop_all():
 for n,(p,_,_) in list(clients.items()):
  if p.poll() is None:
   try: action(n,"exit"); p.wait(timeout=60)
   except Exception: p.kill()
 if server and server.poll() is None:
  server.stdin.write("stop\n"); server.stdin.flush(); server.wait(timeout=60)
def entity_count(n):
 before=msgcount(n,"TEST_ENTITY_COUNT"); command(n,"piritest entitycount","TEST_ENTITY_COUNT")
 msgs=[x for x in client(n).get("messages",[]) if "TEST_ENTITY_COUNT" in x]; m=re.search(r"total=(\d+) displays=(\d+) armorstands=(\d+)",msgs[-1])
 return tuple(map(int,m.groups()))
def capture(n,label):
 action(n,"capture",label=label); time.sleep(1)
 src=E/"work"/("client-"+n)/"screenshots"/("phase13-"+label+".png")
 wait(lambda:src.exists(),label+" screenshot",30); shutil.copy2(src,E/"screenshots"/src.name)
def remote(n):
 return client(n).get("remotePresentation",{})
def machine_view(n,mid):
 r=remote(n); return r.get(str(mid),r.get(mid,{}))
def forbidden(v):
 bad={"setting","internalRole","internal_role","premiumType","premium_type","rng","rngSeed","seed","stopHints"}
 if isinstance(v,dict): return bool(bad & set(v)) or any(forbidden(x) for x in v.values())
 if isinstance(v,list): return any(forbidden(x) for x in v)
 return False

try:
 start_server(); start_client("phase13-owner"); owner="phase13-owner"; console("op PiriRuntimeTest"); start_client("phase13-spectator"); spec="phase13-spectator"; console("op PiriRuntimeTest2")
 command(owner,"tp @s 0.5 100 0.5"); command(spec,"tp @s 0.5 100 0.5")
 command(owner,"tp @s 0.5 100 0.5"); command(spec,"tp @s 0.5 100 0.5"); baseline=entity_count(owner)
 command(owner,"piritest phase13grid","TEST_PHASE13_GRID")
 wait(lambda:len(state().get("machines",[]))==42,"42 direct runtime machines committed",60)
 command(spec,"tp @s 0.5 99 0.5")
 wait(lambda:pcount(spec,"REMOTE_MACHINE_SNAPSHOT")>=42 and client(spec).get("remoteCacheSize")==42,"42 remote machines",120); after=entity_count(owner)
 check("feature creates no renderer entities",after[1:]==baseline[1:],{"before":baseline,"after":after})
 check("no Display or ArmorStand renderer entities",after[1]==0 and after[2]==0,{"before":baseline,"after":after})
 views=remote(spec); check("42 cabinets cached simultaneously",len(views)==42,{"count":len(views)})
 ids=sorted(int(k) for k in views.keys()); expected=["NORTH","SOUTH","EAST","WEST","UP","DOWN"]
 check("six facing snapshots are deterministic",[machine_view(spec,ids[i]).get("facing") for i in range(6)]==expected,[machine_view(spec,ids[i]).get("facing") for i in range(6)])
 # Screenshots from each cabinet front, including above/below for vertical facings.
 cams=[(-8.5,100,-12.5),(-5.5,100,-5.5),(0.5,100,-8.5),(-3.5,100,-8.5),(3.5,104,-8.5),(6.5,96,-8.5)]
 for i,(cx,cy,cz) in enumerate(cams):
  command(spec,f"tp @s {cx} {cy} {cz}"); action(spec,"aimpos",x=(i-3)*3,y=100,z=-9); time.sleep(.5); capture(spec,"facing-"+expected[i].lower())
 # Physical owner fixture overlays machine #2 (SOUTH); owner is re-positioned before each reopen.
 mid=ids[1]; command(owner,"setblock -6 100 -10 stone"); command(owner,"setblock -6 100 -9 stone_button[face=wall,facing=south]")
 command(owner,"tp @s -5.5 99 -6.5"); action(owner,"aimpos",x=-6,y=100,z=-9); action(owner,"clickpos",x=-6,y=100,z=-9)
 wait(lambda:pcount(owner,"OPEN_MACHINE")>0 and bool(session()),"owner open"); command(owner,"piritest fund","TEST_FUNDED"); action(owner,"close"); wait(lambda:not session(),"fund close"); action(owner,"aimpos",x=-6,y=100,z=-9); action(owner,"clickpos",x=-6,y=100,z=-9); wait(lambda:session().get("credit")==50,"funded reopen",60)
 command(owner,"piritest force reg","TEST_FORCE_ARMED"); tap(owner,32); wait(lambda:session().get("game_state")=="NORMAL_BETTED","bet"); tap(owner,32)
 wait(lambda:session().get("game_state")=="NORMAL_SPINNING" and pcount(owner,"SPIN_START")>0,"spin"); time.sleep(1)
 stop_reels(owner); wait(lambda:session().get("game_state")=="BONUS_PENDING_REG","pending reg")
 authoritative=[int(session()["display_left_stop"]),int(session()["display_center_stop"]),int(session()["display_right_stop"])]
 wait(lambda:machine_view(spec,mid).get("stoppedMask")==7,"spectator stopped",30); world=machine_view(spec,mid).get("displayStops")
 check("owner authoritative stop indexes equal external cabinet",world==authoritative,{"owner":authoritative,"external":world})
 check("hidden bonus internals absent before public",not forbidden([p for p in client(spec).get("packets",[]) if p["type"].startswith("REMOTE_MACHINE_")]))
 capture(owner,"owner-stop"); command(spec,"tp @s -5.5 100 -5.5"); action(spec,"aimpos",x=-6,y=100,z=-9); capture(spec,"external-stop")
 tap(owner,32); wait(lambda:session().get("game_state")=="BONUS_ENTRY_BETTED_REG","entry bet"); tap(owner,32); wait(lambda:"BONUS_ENTRY_SPINNING_REG"==session().get("game_state"),"entry spin"); time.sleep(1); stop_reels(owner)
 wait(lambda:session().get("game_state")=="REG_READY","REG ready"); wait(lambda:machine_view(spec,mid).get("bonusMode")=="REG","public REG external",30)
 check("public REG mode/count reaches cabinet",machine_view(spec,mid).get("bonusMode")=="REG" and machine_view(spec,mid).get("bonusCount") is not None,machine_view(spec,mid))
 # Lamp acceptance uses the production premium-B NOTICE path (plain REG does not guarantee NOTICE).
 # Finish the forced REG first, then exercise one deterministic public NOTICE.
 for _ in range(8):
  tap(owner,32); wait(lambda:session().get("game_state")=="REG_BETTED","REG bet")
  tap(owner,32); wait(lambda:session().get("game_state")=="REG_SPINNING","REG spin")
  time.sleep(1); stop_reels(owner)
  wait(lambda:session().get("game_state") in ("REG_READY","SEATED_READY"),"REG settle")
 notice_before=pcount(spec,"REMOTE_MACHINE_NOTICE")
 command(owner,"piritest force B","TEST_FORCE_ARMED"); tap(owner,32); wait(lambda:session().get("game_state")=="NORMAL_BETTED","notice bet"); tap(owner,32)
 wait(lambda:session().get("game_state")=="NORMAL_SPINNING","notice spin"); time.sleep(1)
 wait(lambda:settled(owner) and client(owner).get("stopEnabled"),"notice stoppable",30); tap(owner,263)
 wait(lambda:pcount(spec,"REMOTE_MACHINE_NOTICE")>notice_before,"external public notice",30)
 samples=[]
 for _ in range(12):
  samples.append(bool(machine_view(spec,mid).get("lampVisible"))); time.sleep(.1)
 check("external lamp ON/blink follows public NOTICE",True in samples and False in samples,{"samples":samples})
 # Redefine to a new EAST-facing button and verify placement update.
 action(owner,"close"); wait(lambda:not session(),"close before redefine"); command(owner,"setblock 15 100 0 stone"); command(owner,"setblock 14 100 0 stone_button[face=wall,facing=east]")
 command(owner,"tp @s 17.5 99 0.5"); action(owner,"aimpos",x=14,y=100,z=0); before=pcount(spec,"REMOTE_MACHINE_SNAPSHOT"); command(owner,f"piri machine redefine {mid}")
 wait(lambda:pcount(spec,"REMOTE_MACHINE_SNAPSHOT")>before and machine_view(spec,mid).get("facing")=="EAST","redefine external",60)
 check("redefine immediately moves/rotates cabinet",machine_view(spec,mid).get("facing")=="EAST",machine_view(spec,mid))
 time.sleep(10); check("42-machine renderer remains alive",clients[spec][0].poll() is None and not client(spec).get("failure"))
 manifest["passed"]=True
except Exception as e:
 manifest["failure"]=str(e); print("RUNTIME FAILURE "+str(e),flush=True)
finally:
 stop_all(); manifest["finishedAt"]=datetime.datetime.now(datetime.timezone.utc).isoformat(); save(E/"result.json",manifest); save(OUT/"result.json",manifest)
 report=["# Phase13 Runtime Acceptance","",f"- Run: {RUN}",f"- PASS: {manifest['passed']}","","| Assertion | Result |","|---|---|"]+[f"| {a['name']} | {'PASS' if a['passed'] else 'FAIL'} |" for a in manifest["assertions"]]
 if manifest.get("failure"): report+=["",f"Failure: `{manifest['failure']}`"]
 (E/"REPORT.md").write_text("\n".join(report)+"\n",encoding="utf-8")
 for h in handles:
  try:h.close()
  except:pass
if not manifest["passed"]: raise SystemExit(1)
