"""Phase13 real-Minecraft entity-free world cabinet renderer acceptance."""
from pathlib import Path
import datetime, hashlib, json, os, shutil, subprocess, time, re

ROOT=Path(__file__).resolve().parents[1]; E=ROOT/"runtime-evidence/HYPER_DOG_PHASE_07"
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
  wait(lambda:settled(n) and client(n).get("stopEnabled"),"stoppable before stop",30)
  tap(n,key)
  wait(lambda:settled(n) and session().get("stopped_mask")==mask,"stop mask "+str(mask),40)
def start_server():
 global server
 plugins=SERVER/"plugins"; plugins.mkdir(parents=True,exist_ok=True); shutil.copy2(prod["paper"],plugins); shutil.copy2(helper["paper"],plugins)
 (SERVER/"eula.txt").write_text("eula=true\n"); (SERVER/"server.properties").write_text("\n".join(["server-ip=127.0.0.1","server-port=25612","online-mode=false","enforce-secure-profile=false","max-players=4","view-distance=6","simulation-distance=5","spawn-protection=0","generate-structures=false","level-type=minecraft:flat"])+"\n")
 h=(OUT/"server.log").open("w",encoding="utf-8"); handles.append(h)
 server=subprocess.Popen([JAVA,"-Xms512M","-Xmx1536M","-Dpiri.runtime.phase=hyperdog07-live",f"-Dpiri.runtime.serverResult={server_result}",f"-Dpiri.runtime.phase07AudioTrigger={OUT / 'audio-trigger.txt'}","-jar",str(PAPER),"nogui"],cwd=SERVER,stdin=subprocess.PIPE,stdout=h,stderr=subprocess.STDOUT,text=True,creationflags=FLAGS)
 wait(lambda:"Done (" in log(OUT/"server.log") and "PIRI_DATABASE_READY" in log(OUT/"server.log"),"Paper ready",300)
def console(text):
 server.stdin.write(text+"\n"); server.stdin.flush(); time.sleep(.3)
def start_client(n):
 folder=OUT/n; folder.mkdir(parents=True,exist_ok=True); result=folder/"client-result.json"
 work=E/"work"/("client-"+n)
 if work.exists(): shutil.rmtree(work)
 work.mkdir(parents=True); (work/"options.txt").write_text("version:3953\nlang:en_us\nrenderDistance:6\nsimulationDistance:5\nmaxFps:30\npauseOnLostFocus:false\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\nonboardAccessibility:false\n")
 h=(folder/"client.log").open("w",encoding="utf-8"); handles.append(h)
 cmd=GRADLE+["-PruntimeAcceptance=true",f"-PruntimeScenario={n}",f"-PruntimeRun={RUN}","-PruntimeEvidencePhase=HYPER_DOG_PHASE_07",":runtime-test-client:runClient","--console=plain"]
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


import urllib.request,urllib.error,math

def request(method,path,token=None):
 req=urllib.request.Request("http://127.0.0.1:10271"+path,method=method)
 if token: req.add_header("X-Piri-Token",token)
 try:
  with urllib.request.urlopen(req,timeout=15) as response:
   return response.status,json.loads(response.read().decode("utf-8"))
 except urllib.error.HTTPError as e:
  return e.code,json.loads(e.read().decode("utf-8"))

try:
 start_server()
 start_client("phase13-owner"); owner="phase13-owner"
 console("op PiriRuntimeTest")
 start_client("phase13-spectator"); spectator="phase13-spectator"
 console("op PiriRuntimeTest2")
 # Do not let generated-world water drown/respawn the actual observer clients.
 console("gamemode creative PiriRuntimeTest")
 console("gamemode creative PiriRuntimeTest2")
 # A flat, daylight scene and solid platform keep both real viewers in front of
 # the north-facing virtual cabinets rather than buried in random treetops.
 console("time set day")
 console("fill -4 98 -2 5 98 5 minecraft:glass")
 # Keep the viewers on independent sight lines: a player occupying the
 # camera voxel otherwise covers every virtual cabinet in the screenshot.
 command(owner,"tp @s 0.5 99 1.5 180 0")
 command(spectator,"tp @s 4.5 99 1.5 180 0")
 wait(lambda:state().get("ready") is True,"test-only HYPER_DOG fixture created",150)
 hyper=state()["hyperMachine"]; legacy=state()["legacyMachine"]
 check("two actual Minecraft clients coexist with test-only HYPER_DOG and Juggler",
       state()["playerCount"]>=2 and hyper!=legacy and hyper>=1 and legacy>=1,state())
 wait(lambda:client(owner).get("remoteCacheSize",0)>=2 and client(spectator).get("remoteCacheSize",0)>=2,
      "both Fabric clients received world cabinet snapshots",100)
 wait(lambda:machine_view(spectator,hyper).get("atFirstHits")==4 and
      machine_view(spectator,hyper).get("czAttempts")==3 and
      machine_view(spectator,hyper).get("upperEntries")==5,"spectator rendered HYPER_DOG three counters",60)
 check("spectator actual world renderer receives 4 AT/3 CZ/5 upper",
       all(machine_view(spectator,hyper).get(k)==v for k,v in
           [("atFirstHits",4),("czAttempts",3),("upperEntries",5)]),
       machine_view(spectator,hyper))
 check("second player gets identical authoritative HYPER_DOG public view",
       all(machine_view(owner,hyper).get(k)==machine_view(spectator,hyper).get(k) for k in
           ("atFirstHits","czAttempts","upperEntries","machineType")),machine_view(owner,hyper))
 check("legacy JUGGLER remains visible as separate machine without HYPER_DOG counters",
       machine_view(spectator,legacy).get("machineType")=="JUGGLER" and
       machine_view(spectator,legacy).get("atFirstHits",0)==0, machine_view(spectator,legacy))
 # Ask the test-only Paper fixture to send a fresh real SPIN_START now that
 # both Fabric clients have confirmed subscription. The production audio
 # broadcast remains untouched; a missing sound still fails this acceptance.
 (OUT/"audio-trigger.txt").write_text("subscribed\n",encoding="utf-8")
 wait(lambda:state().get("audioTriggered") is True,
      "Paper production routing received post-subscription spin trigger",30)
 history=[x for x in client(spectator).get("packets",[]) if x["type"].startswith("REMOTE_MACHINE_")]
 check("production remote packets never forward private RNG, settings or premium info",
       bool(history) and not forbidden(history),{"packets":len(history)})
 wait(lambda:any(p.get("sound")=="lever" and p.get("machineId")==hyper
                 for p in packets(spectator,"REMOTE_MACHINE_SOUND")),
      "real Fabric spectator received HYPER_DOG lever sound via production remote routing",40)
 check("actual two Fabric clients remain alive as stable world observers",
       all(not any("drowned" in msg for msg in client(n).get("messages",[]))
           for n in (owner,spectator)))
 check("HYPER_DOG sound leaves no additional loops active",
       client(spectator).get("hallAudioLoopCount",0)==0 and
       client(spectator).get("remoteLoopCount",0)==0,
       {"loops":client(spectator).get("hallAudioLoopCount"),
        "remoteLoops":client(spectator).get("remoteLoopCount")})
 check("remote renders exactly two virtual cabinets and stays connected",
       client(spectator).get("remoteCacheSize")==2 and
       clients[spectator][0].poll() is None,client(spectator).get("remoteCacheSize"))
 capture(spectator,"hyperdog-phase07-spectator")
 capture(owner,"hyperdog-phase07-owner")
 for n in (owner,spectator):
  img=E/"screenshots"/("phase13-hyperdog-phase07-"+("owner" if n==owner else "spectator")+".png")
  check(n+" actual Minecraft screenshot captured",
        img.exists() and img.read_bytes()[:8]==b"\x89PNG\r\n\x1a\n",str(img))

 check("real world renderer reports finite performance counters",
       math.isfinite(float(client(spectator).get("cabinetFrameMs",0))) and
       math.isfinite(float(client(spectator).get("cabinetFps",0))) and
       float(client(spectator).get("heapMiB",0))<1800,
       {"frameMs":client(spectator).get("cabinetFrameMs"),
        "fps":client(spectator).get("cabinetFps"),"heapMiB":client(spectator).get("heapMiB")})

 status,no_token=request("GET","/api/data?id="+str(hyper))
 check("unauthenticated mobile HYPER_DOG data request rejected",
       status==401 and no_token.get("error")=="AUTH_REQUIRED",
       {"status":status,"error":no_token.get("error")})
 command(owner,"piri mobile pair","スマホ接続コード:")
 msg=next(x for x in reversed(client(owner).get("messages",[])) if "スマホ接続コード:" in x)
 code=re.search(r"(\d{6})",msg)
 check("real player obtained a six-digit mobile pairing code",code is not None)
 status,paired=request("POST","/api/pair?code="+code.group(1))
 check("real player mobile HTTP pairing authenticates",
       status==200 and bool(paired.get("token")),{"status":status})
 token=paired["token"]
 status,data=request("GET","/api/data?id="+str(hyper),token)
 check("real mobile HTTP shows same 4/3/5 counters as both Fabric players",
       status==200 and data.get("machineType")=="HYPER_DOG" and
       [data.get(k) for k in ("atFirstHits","czAttempts","upperEntries")]==[4,3,5],
       {"status":status,"counters":[data.get(k) for k in ("atFirstHits","czAttempts","upperEntries")]})
 check("mobile HYPER_DOG preserves 350 games and +1200 medal graph",
       data.get("totalGames")==350 and data.get("todayDifference")==1200 and
       len(data.get("graph",[]))>=1 and len(data.get("history",[]))==12,
       {"games":data.get("totalGames"),"diff":data.get("todayDifference"),
        "points":len(data.get("graph",[])),"events":len(data.get("history",[]))})
 check("mobile HYPER_DOG has no invented BIG or REG and no private game state",
       data.get("bigCount")==0 and data.get("regCount")==0 and not forbidden(data))
 status,old=request("GET","/api/data?id="+str(legacy),token)
 check("legacy mobile BIG/REG 1/2 remain unchanged",
       status==200 and old.get("bigCount")==1 and old.get("regCount")==2 and
       "atFirstHits" not in old,{"status":status,"big":old.get("bigCount"),
                                      "reg":old.get("regCount")})
 status,machines=request("GET","/api/machines",token)
 item=next((x for x in machines.get("machines",[]) if x.get("id")==hyper),{})
 check("HYPER_DOG remains unavailable for mobile play until Phase08 public release",
       status==200 and item.get("supported") is False,item)
 status,denied=request("POST","/api/seat?id="+str(hyper),token)
 check("mobile cannot bypass unreleased HYPER_DOG seat guard",
       status in (400,403,409) and denied.get("error") is not None,
       {"status":status,"error":denied.get("error")})
 manifest["passed"]=True
except Exception as e:
 manifest["failure"]=str(e)
 import traceback
 manifest["traceback"]=traceback.format_exc(limit=14)
 print("HYPER_DOG_PHASE07_LIVE_FAIL",repr(e),flush=True)
finally:
 stop_all()
 manifest["finishedAt"]=datetime.datetime.now(datetime.timezone.utc).isoformat()
 save(E/"result.json",manifest);save(OUT/"result.json",manifest)
 (E/"REPORT.md").write_text(
  "# HYPER_DOG Phase07 — genuine Paper + two Fabric clients + mobile HTTP read-only view\n\n"+
  ("PASS" if manifest["passed"] else "FAIL")+"\n"+
  "\n".join(("- PASS " if x["passed"] else "- FAIL ")+x["name"] for x in manifest["assertions"])+
  ("\nFailure: "+manifest.get("failure","") if not manifest["passed"] else "")+
  "\nScope: test-only HYPER_DOG placement and counters; public HYPER_DOG gameplay remains Phase08-gated.\n",
  encoding="utf-8")
 for h in handles:
  try:h.close()
  except Exception:pass
print("HYPER_DOG_PHASE07_LIVE="+("PASS" if manifest["passed"] else "FAIL"),flush=True)
raise SystemExit(0 if manifest["passed"] else 1)
