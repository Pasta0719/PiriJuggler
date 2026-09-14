"""Real-client Phase05 acceptance. Built production jars are loaded by Paper/Fabric.
The optional helpers control actual Minecraft inputs and observe committed production state.
"""
from pathlib import Path
import datetime, hashlib, json, os, shutil, sqlite3, subprocess, time

ROOT = Path(__file__).resolve().parents[1]
BONUS = os.environ["PIRI_BAR_BONUS"]
assert BONUS in ("BIG","REG")
PHASE = "PHASE_05_BAR_"+BONUS
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
    import re
    config=(ROOT/"paper/src/main/resources/config.yml").read_text(encoding="utf-8")
    config=config.split("probabilities:")[0]+"probabilities:\n  settings:\n"
    source=json.loads((ROOT/"docs/spec-lock.json").read_text(encoding="utf-8"))["settingWeights"]
    for setting,row in source.items():
        selected=dict(row)
        selected={k:(1000000000 if k==BONUS.lower() else 0) for k in row}
        config+=f"    '{setting}':\n"+"".join(f"      {k}: {v}\n" for k,v in selected.items())
    (plugins/"PiriJuggler").mkdir(exist_ok=True);(plugins/"PiriJuggler/config.yml").write_text(config,encoding="utf-8")
    (OUT/"test-config.yml").write_text(config,encoding="utf-8")
    (SERVER/"eula.txt").write_text("eula=true\n",encoding="utf-8")
    (SERVER/"server.properties").write_text("\n".join(["server-ip=127.0.0.1","server-port=25589","online-mode=false","enforce-secure-profile=false","enable-query=false","enable-rcon=false","max-players=2","view-distance=2","simulation-distance=2","spawn-protection=0","generate-structures=false","level-type=minecraft:normal","motd=Piri Phase05 isolated runtime"])+"\n",encoding="utf-8")
    folder=OUT/round; folder.mkdir(parents=True,exist_ok=True); server_result=folder/"server-result.json"
    path=folder/"server.log"; handle=path.open("w",encoding="utf-8"); handles.append(handle)
    cmd=[JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8","-Dpiri.runtime.phase=phase05",f"-Dpiri.runtime.serverResult={server_result}","-jar",str(PAPER),"nogui"]
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
    path=EVIDENCE/"work"/("client-"+a)/"screenshots"/("phase05-"+label+".png")
    wait(lambda:path.exists() and path.stat().st_size>1000,"actual screenshot "+label)
    (OUT/"screenshots").mkdir(exist_ok=True);shutil.copy2(path,OUT/"screenshots"/path.name)
    if label!="chat-only-on-t":
        audit=subprocess.run([JAVA,str(ROOT/"runtime-test-support/ScreenshotAudit.java"),str(path)],capture_output=True,text=True,creationflags=FLAGS)
        (OUT/"screenshots"/(label+"-audit.txt")).write_text(audit.stdout+audit.stderr,encoding="utf-8")
        check("actual screenshot contains all three reel symbol columns: "+label,audit.returncode==0,str(path))

def tap(code):action(a,"tap",key=code)
def session():return next((s for s in state().get("sessions",[]) if s["lifecycle"]=="ACTIVE"),{})
def public():return client(a).get("publicState",{}) or {}
def dbrows(sql):
    with sqlite3.connect(f"file:{SERVER / 'plugins/PiriJuggler/piri.db'}?mode=ro",uri=True) as db:
        db.row_factory=sqlite3.Row;return [dict(r) for r in db.execute(sql)]
def settled():
    s=session();p=public()
    return s and p.get("expectedNextClientSequence")==s["last_client_sequence"]+1 and p.get("credit")==s["credit"] and p.get("heldMedals")==s["held_medals"]
def wait_state(name):wait(lambda:settled() and session()["game_state"]==name,"durable "+name)
def seed(bonus):
    number=state().get("phase05",{}).get("funded",0)+1
    save(server_result.with_name(f"fund-{number}.json"),{"bonus":bonus})
    wait(lambda:state().get("phase05",{}).get("funded",0)>=number or state().get("phase05",{}).get("failure"),"test fixture")
    assert not state()["phase05"].get("failure"),state()["phase05"]

try:
    start_server("bar-"+BONUS.lower());start_client("phase05-game");a="phase05-game"
    action(a,"aim",x=0);command(a,"piri machine create","MACHINE_CREATED 1");click(a,0,"OPEN_MACHINE");wait_state("SEATED_READY")
    seed(False);click(a,0,"OPEN_MACHINE");wait(lambda:settled() and session()["credit"]==2,"funded session")
    tap(32);wait_state("NORMAL_BETTED");tap(32)
    wait(lambda:settled() and session()["game_state"]=="NORMAL_SPINNING" and client(a).get("stopEnabled"),"real bonus LEVER")
    check("real "+BONUS+" draw stays private before BAR stops",session()["internal_role"]==BONUS and public()["gameState"]=="NORMAL_SPINNING",snapshot("private-draw"))
    # Actual keyboard edges are timed from the client view. No stopIndex or phase is sent to Paper.
    for reel,key,phase,mask in [(2,262,5,4),(0,263,10,5),(1,264,12,7)]:
        action(a,"tap_at_phase",reel=reel,key=key,phase=phase+.72)
        wait(lambda:settled() and session()["stopped_mask"]==mask,"timed real STOP")
        stop=packets(a,"REEL_STOP")[-1]
        check("server chose timed BAR candidate reel "+str(reel),stop["stopIndex"]==phase,stop)
    wait_state("BONUS_PENDING_"+BONUS)
    wait(lambda:client(a).get("displayPhases")==[10.0,12.0,5.0],"exact BAR screen stop positions")
    p=public();s=session()
    check("BAR confirmation pays zero, lights Piri Chance and hides "+BONUS,p["gameState"]=="BONUS_PENDING" and p["pay"]==0 and p["lampOn"] and p["bonusCount"]==0 and s["bonus_type"]==BONUS and s["credit"]+s["held_medals"]==799 and not packets(a,"BONUS_START") and not packets(a,"PAYOUT"),snapshot("bar-confirmation"))
    capture("bar-confirmation-"+BONUS.lower())
    forbidden={"internalRole","internal_role","setting","seed","premiumType","premium_type","bonusType"}
    def secret(value):
        if isinstance(value,dict):return bool(forbidden&set(value)) or any(secret(v) for v in value.values())
        if isinstance(value,list):return any(secret(v) for v in value)
        return isinstance(value,str) and value in {"BIG","REG","BONUS_PENDING_BIG","BONUS_PENDING_REG","BONUS_ENTRY_BETTED_BIG","BONUS_ENTRY_BETTED_REG","BONUS_ENTRY_SPINNING_BIG","BONUS_ENTRY_SPINNING_REG"}
    check("all BAR client packets keep bonus type private",not any(secret(p["payload"]) for p in client(a)["packets"]),snapshot("no-leaks"))
    check("BAR test input uses only identity and sequence",all(set(p["payload"])=={"sessionId","machineId","clientSequence"} for p in state()["inbound"] if p["type"]!="HELLO"))
    tap(256);wait(lambda:client(a).get("screen")=="none" and state()["sessions"][0]["lifecycle"]=="SUSPENDED_GRACE","BAR rights retained on close")
    check("BAR confirmation preserves pending "+BONUS+" rights on close",state()["sessions"][0]["game_state"]=="BONUS_PENDING_"+BONUS,snapshot("close"))
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
