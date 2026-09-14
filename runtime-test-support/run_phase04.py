"""Real-client Phase04 acceptance. Built production jars are loaded by Paper/Fabric.
The optional helpers control actual Minecraft inputs and observe committed production state.
"""
from pathlib import Path
import datetime, hashlib, json, os, shutil, sqlite3, subprocess, time

ROOT = Path(__file__).resolve().parents[1]
PHASE = os.environ.get("PIRI_PHASE04_EVIDENCE", "PHASE_04")
assert PHASE in ("PHASE_04", "PHASE_05_REEL_REGRESSION")
EVIDENCE = ROOT / "runtime-evidence" / PHASE
RUN = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
OUT = EVIDENCE / "attempts" / RUN
SERVER = EVIDENCE / "work" / ("server-" + RUN)
JAVA = shutil.which("java")
PAPER = ROOT / "runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar"
assert hashlib.sha256(PAPER.read_bytes()).hexdigest() == "ab9bb1afc3cea6978a0c03ce8448aa654fe8a9c4dddf341e7cbda1b0edaa73f5"
FLAGS = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
OUT.mkdir(parents=True)
artifacts = {side: ROOT / side / f"build/libs/piri-juggler-{side}-1.0.0.jar" for side in ("common","paper","fabric")}
helpers = {side: ROOT / f"runtime-test-support/{module}/build/libs/piri-runtime-test-{side}-1.0.0.jar" for side,module in (("paper","paper"),("client","client"))}
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def save(path, value):
    path.parent.mkdir(parents=True,exist_ok=True)
    temp = path.with_suffix(".tmp")
    temp.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding="utf-8")
    for attempt in range(10):
        try: temp.replace(path); return
        except PermissionError:
            if attempt == 9: raise
            time.sleep(.05)
read_cache = {}
def load(path):
    try: read_cache[path] = json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError,json.JSONDecodeError,PermissionError): pass
    return read_cache.get(path,{})
def log(path): return path.read_text(encoding="utf-8",errors="replace") if path.exists() else ""
manifest = {"startedAt":datetime.datetime.now(datetime.timezone.utc).isoformat(),"run":RUN,"passed":False,
    "versions":{"minecraft":"1.21","paper":"1.21-130","fabricLoader":"0.16.14","fabricApi":"0.102.0+1.21","yarn":"1.21+build.9","java":JAVA},
    "buildHashes":{k:sha(v) for k,v in artifacts.items()},"helperHashes":{k:sha(v) for k,v in helpers.items()},"assertions":[],"commands":[],"exits":[]}
server = None
clients = {}
handles = []
server_result = None
def wait(predicate, label, timeout=90):
    until = time.monotonic()+timeout
    while time.monotonic()<until:
        if predicate(): return
        if server is not None and server.poll() is not None: raise RuntimeError("Paper exited while " + label)
        for name,(proc,path,_) in clients.items():
            if proc.poll() is not None: raise RuntimeError(name + " exited " + str(proc.returncode) + " while " + label)
            failure = load(path).get("failure")
            if failure: raise RuntimeError(name + ": " + failure)
        time.sleep(.25)
    raise TimeoutError(label)
def check(name, condition, details=None):
    record = {"name":name,"passed":bool(condition),"at":datetime.datetime.now(datetime.timezone.utc).isoformat()}
    if details is not None: record["evidence"] = details
    manifest["assertions"].append(record); print(("PASS " if condition else "FAIL ")+name,flush=True)
    save(EVIDENCE/"result.json",manifest)
    if not condition: raise AssertionError(name)
def state(): return load(server_result)
def client(name): return load(clients[name][1])
def packets(name, kind): return [p["payload"] for p in client(name).get("packets",[]) if p["type"]==kind]
def message_count(name, text): return sum(text in m for m in client(name).get("messages",[]))
def action(name, kind, **args):
    proc,path,seq = clients[name]; seq += 1; clients[name] = (proc,path,seq)
    payload = {"id":seq,"kind":kind,**args}; save(path.with_name(f"command-{seq}.json"),payload)
    wait(lambda:client(name).get("completed",0)>=seq,name+" action "+kind)
    manifest["commands"].append({"client":name,**payload})
def command(name,text, expected=None):
    before = message_count(name,expected) if expected else 0
    action(name,"command",text=text)
    if expected: wait(lambda:message_count(name,expected)>before,text+" -> "+expected)
def click(name,x,kind=None):
    count = len(packets(name,kind)) if kind else 0
    action(name,"aim",x=x); action(name,"click",x=x)
    if kind: wait(lambda:len(packets(name,kind))>count,name+" receive "+kind)
def start_server(round):
    global server,server_result
    plugins = SERVER/"plugins"; plugins.mkdir(parents=True,exist_ok=True)
    shutil.copy2(artifacts["paper"],plugins); shutil.copy2(helpers["paper"],plugins)
    (SERVER/"eula.txt").write_text("eula=true\n",encoding="utf-8")
    (SERVER/"server.properties").write_text("\n".join(["server-ip=127.0.0.1","server-port=25588","online-mode=false","enforce-secure-profile=false","enable-query=false","enable-rcon=false","max-players=2","view-distance=2","simulation-distance=2","spawn-protection=0","generate-structures=false","level-type=minecraft:normal","motd=Piri Phase04 isolated runtime"])+"\n",encoding="utf-8")
    folder=OUT/round; folder.mkdir(parents=True,exist_ok=True); server_result=folder/"server-result.json"
    path=folder/"server.log"; handle=path.open("w",encoding="utf-8"); handles.append(handle)
    cmd=[JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8","-Dpiri.runtime.phase=phase04",f"-Dpiri.runtime.serverResult={server_result}","-jar",str(PAPER),"nogui"]
    manifest["commands"].append({"server":round,"command":cmd,"cwd":str(SERVER)})
    server=subprocess.Popen(cmd,cwd=SERVER,stdin=subprocess.PIPE,stdout=handle,stderr=subprocess.STDOUT,text=True,creationflags=FLAGS)
    wait(lambda:'Done (' in log(path) and state().get("ready"),"Paper ready",300)
    print("Paper ready: "+round,flush=True)
def stop_server():
    global server
    if server and server.poll() is None:
        server.stdin.write("stop\n"); server.stdin.flush(); server.wait(timeout=60)
    if server:
        manifest["exits"].append({"server":server.returncode}); assert server.returncode==0; server=None
def start_client(name):
    folder=OUT/name; folder.mkdir(parents=True,exist_ok=True)
    directory=EVIDENCE/"work"/("client-"+name); directory.mkdir(parents=True,exist_ok=True)
    (directory/"options.txt").write_text("version:3953\nlang:en_us\nrenderDistance:2\nsimulationDistance:5\nmaxFps:30\npauseOnLostFocus:false\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\nonboardAccessibility:false\n",encoding="utf-8")
    handle=(folder/"client.log").open("w",encoding="utf-8"); handles.append(handle)
    cmd=["cmd.exe","/d","/c",str(ROOT/"gradlew.bat"),"-PruntimeAcceptance=true",f"-PruntimeScenario={name}",f"-PruntimeRun={RUN}", f"-PruntimeEvidencePhase={PHASE}",":runtime-test-client:runClient","--console=plain"]
    manifest["commands"].append({"client":name,"command":cmd,"cwd":str(ROOT)})
    proc=subprocess.Popen(cmd,cwd=ROOT,stdout=handle,stderr=subprocess.STDOUT,creationflags=FLAGS)
    clients[name]=(proc,folder/"client-result.json",0)
    wait(lambda:client(name).get("connected") and client(name).get("handshake"),name+" real Fabric join",600)
    print("Fabric joined: "+name,flush=True)
def stop_client(name):
    action(name,"exit"); proc,_,_=clients[name]; proc.wait(timeout=60)
    manifest["exits"].append({"client":name,"exit":proc.returncode}); assert proc.returncode==0
    del clients[name]
def snapshot(name):
    path=OUT/(name+"-snapshot.json"); save(path,{"server":state(),"clients":{n:client(n) for n in clients}}); return str(path.relative_to(EVIDENCE))

def capture(label):
    action(a,"capture",label=label)
    path=EVIDENCE/"work"/("client-"+a)/"screenshots"/("phase04-"+label+".png")
    wait(lambda:path.exists() and path.stat().st_size>1000,"actual screenshot "+label)
    (OUT/"screenshots").mkdir(exist_ok=True);shutil.copy2(path,OUT/"screenshots"/path.name)
    if label!="chat-only-on-t":
        audit=subprocess.run([JAVA,str(ROOT/"runtime-test-support/ScreenshotAudit.java"),str(path)],capture_output=True,text=True,creationflags=FLAGS)
        (OUT/"screenshots"/(label+"-audit.txt")).write_text(audit.stdout+audit.stderr,encoding="utf-8")
        check("actual screenshot contains all three reel symbol columns: "+label,audit.returncode==0,str(path))

def rounds():return state().get("phase04",{}).get("rounds",[])
def tap(code):action(a,"key",key=code,action=1);action(a,"key",key=code,action=0)

try:
    import itertools
    start_server("reels");start_client("phase04-reels");a="phase04-reels"
    action(a,"aim",x=0);command(a,"piri machine create","MACHINE_CREATED 1");click(a,0,"OPEN_MACHINE")
    wait(lambda:client(a).get("screen")=="SlotScreen" and len(state().get("sessions",[]))==1,"production SlotScreen/session")
    check("production session and HUD",client(a)["productionSession"]==state()["sessions"][0]["session_id"] and client(a)["hudHidden"] and client(a)["hudLeaks"]==0,snapshot("open"))
    verification=state()["phase04"]["startup"]
    check("startup verifies 9261 candidates and exhaustive solver",verification["allSequences"]==555660 and verification["premiumFSequences"]==166698 and verification["premiumFSecondChecks"]==7938,verification)
    roles=["GRAPE","BELL","PIERO","REPLAY","CHERRY","MISS","BONUS","BIG_ENTRY","REG_ENTRY","PREMIUM_B"]
    orders=list(itertools.permutations(["LEFT","CENTER","RIGHT"]))
    profiles=["NORMAL","REVERSE_500MS","RESUME_NORMAL"]
    keys={"LEFT":263,"CENTER":264,"RIGHT":262}
    cases=[(role,False,order) for role in roles for order in orders]+[(role,True,order) for role in ["BONUS","CHERRY","PIERO"] for order in orders]
    observed=[]
    for index,(role,premium,order) in enumerate(cases,1):
        label=("premium-f-" if premium else "")+role.lower()+"-"+"".join(r[0].lower() for r in order)
        save(server_result.with_name(f"round-{index}.json"),{"role":role,"premiumF":premium,"profile":profiles[(index-1)%3],"order":order,"label":label})
        wait(lambda:len(rounds())>=index and any(p["spinId"]==rounds()[index-1]["spinId"] for p in packets(a,"SPIN_START")) and client(a).get("stopEnabled"),"spin starts "+label)
        if index<=3:
            spin=rounds()[index-1]["spinId"]
            wait(lambda:client(a).get("motionSpin")==spin and any(s["elapsed"]>=1.25 for s in client(a).get("motionTrace",[])),"actual motion trace "+label)
            trace=client(a)["motionTrace"]
            def steps(lo,hi):
                pairs=[(x,y) for x,y in zip(trace,trace[1:]) if lo<=x["elapsed"]<y["elapsed"]<=hi and y["elapsed"]-x["elapsed"]<.2]
                return [(y["phases"][r]-x["phases"][r]+10.5)%21-10.5 for x,y in pairs for r in range(3)]
            early=steps(.18,.45);late=steps(.85,1.25)
            reverse=profiles[index-1]=="REVERSE_500MS"
            check("actual client rotation direction: "+profiles[index-1],bool(early) and bool(late) and all(d>0 if reverse else d<0 for d in early) and all(d<0 for d in late),{"spinId":spin,"earlyDeltas":early,"lateDeltas":late,"trace":trace})
        for count,reel in enumerate(order,1):
            tap(keys[reel])
            wait(lambda:len(rounds()[index-1]["stops"])>=count,"real STOP "+label+" "+reel)
            assert rounds()[index-1]["stops"][-1]["accepted"],rounds()[index-1]
        wait(lambda:rounds()[index-1].get("complete"),"server round complete "+label)
        row=rounds()[index-1];final=[row["finalStops"][r] for r in ["left","center","right"]]
        wait(lambda:len(client(a).get("displayPhases",[]))==3 and all(abs(x-y)<1e-7 for x,y in zip(client(a)["displayPhases"],final)),"client final positions "+label)
        stops=[next(p["payload"] for p in s["responses"] if p["type"]=="REEL_STOP") for s in row["stops"]]
        observed.extend(stops)
        correct=all(p["reel"]==r and p["spinId"]==row["spinId"] and p["stopIndex"]==row["finalStops"][r.lower()] and 0<=p["pressedIndex"]<21 and p["slip"]==(p["pressedIndex"]-p["stopIndex"])%21 and p["durationMs"]==min(1200,80+50*p["slip"]) for p,r in zip(stops,order))
        if premium:correct=correct and row["stops"][1]["actualTenpaiLines"]==0 and row["stops"][1]["tenpaiSound"]
        check("real STOP / strict candidate / display agreement: "+label,row["strictValid"] and correct,{"server":row,"clientPhases":client(a)["displayPhases"]})
        if order==orders[0]:capture(label)
    check("5+ symbol slip observed in real packets",any(p["slip"]>=5 for p in observed),[p for p in observed if p["slip"]>=5][:5])
    check("78 runtime rounds cover every role and stop order",len(rounds())==78 and len(observed)==234)
    requests=[s["request"] for r in rounds() for s in r["stops"]]
    sequences=[p["clientSequence"] for p in requests]
    check("STOP wire contains only server identity and monotonic sequence",all(set(p)=={"sessionId","machineId","clientSequence"} for p in requests) and sequences==sorted(set(sequences)),snapshot("wire-contract"))
    check("user audio remains optional and all 10 SoundEvents are registered",len(client(a)["sounds"])==10 and all(s["registered"] for s in client(a)["sounds"].values()) and client(a).get("failure") is None,snapshot("audio"))
    check("test reel selection did not change production financial state",all(s["credit"]==0 and s["held_medals"]==0 for s in state()["sessions"]),snapshot("assets"))
    tap(256);wait(lambda:client(a).get("screen")=="none" and not state()["sessions"],"production close releases session")
    check("production close works after all reel rounds",client(a).get("productionSession") is None,snapshot("close"))
    stop_client(a);stop_server();manifest["passed"]=True

except Exception as error:
    manifest["failure"]=str(error);print("RUNTIME FAILURE: "+str(error),flush=True)
finally:
    for name,(proc,_,_) in list(clients.items()):
        if proc.poll() is None:
            try:stop_client(name)
            except Exception:subprocess.run(["taskkill","/PID",str(proc.pid),"/T","/F"],capture_output=True)
    if server and server.poll() is None:
        try:stop_server()
        except Exception:server.terminate();server.wait(timeout=20)
    for handle in handles:handle.close()
    unexpected=[]
    for path in OUT.rglob("*.log"):
        for line in log(path).splitlines():
            if any(marker in line for marker in ("Exception in thread","Unreported exception thrown","Encountered an unexpected exception","Error occurred while enabling","Could not pass event","Mixin apply for mod","Gameplay disabled: database","Database operation failed")):unexpected.append({"log":str(path),"line":line})
    manifest["uncaughtExceptions"]=unexpected;manifest["passed"]=manifest["passed"] and not unexpected
    manifest["finishedAt"]=datetime.datetime.now(datetime.timezone.utc).isoformat()
    for side in ("server","client"):(EVIDENCE/(side+".log")).write_text("\n".join(str(p.relative_to(OUT))+"\n"+log(p) for p in OUT.rglob(side+".log")),encoding="utf-8")
    for picture in (OUT/"screenshots").glob("*.png"):
        (EVIDENCE/"screenshots").mkdir(exist_ok=True);shutil.copy2(picture,EVIDENCE/"screenshots"/picture.name)
    save(OUT/"result.json",manifest);save(EVIDENCE/"result.json",manifest)
print("RUNTIME_ACCEPTANCE="+("PASS" if manifest["passed"] else "FAIL"),flush=True)
raise SystemExit(0 if manifest["passed"] else 1)
