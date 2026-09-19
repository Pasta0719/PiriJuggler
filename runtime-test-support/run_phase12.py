"""Automated real-Minecraft Phase12 runtime acceptance.
Uses the production Paper/Fabric jars plus test-only observer/controller modules.
"""
from pathlib import Path
import datetime, hashlib, json, os, shutil, subprocess, time

ROOT=Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/"runtime-evidence/PHASE_12"
RUN=datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
OUT=EVIDENCE/"attempts"/RUN
SERVER=EVIDENCE/"work"/("server-"+RUN)
JAVA=shutil.which("java")
PAPER=ROOT/"runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar"
FLAGS=subprocess.CREATE_NO_WINDOW if os.name=="nt" else 0
artifacts={side:ROOT/side/f"build/libs/piri-juggler-{side}-1.0.0.jar" for side in ("paper","fabric")}
helpers={side:ROOT/f"runtime-test-support/{module}/build/libs/piri-runtime-test-{side}-1.0.0.jar" for side,module in (("paper","paper"),("client","client"))}
OUT.mkdir(parents=True,exist_ok=True)

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def save(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    tmp=path.with_suffix(".tmp"); tmp.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding="utf-8")
    for i in range(20):
        try: tmp.replace(path); return
        except PermissionError:
            if i==19: raise
            time.sleep(.05)
_cache={}
def load(path):
    try:_cache[path]=json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError,json.JSONDecodeError,PermissionError):pass
    return _cache.get(path,{})
def log(path): return path.read_text(encoding="utf-8",errors="replace") if path.exists() else ""

manifest={"startedAt":datetime.datetime.now(datetime.timezone.utc).isoformat(),"run":RUN,"passed":False,
          "buildHashes":{k:sha(v) for k,v in artifacts.items()},"helperHashes":{k:sha(v) for k,v in helpers.items()},
          "assertions":[],"commands":[],"exits":[]}
server=None; clients={}; handles=[]; server_result=OUT/"server-result.json"

def wait(pred,label,timeout=120):
    until=time.monotonic()+timeout
    while time.monotonic()<until:
        if pred(): return
        if server is not None and server.poll() is not None: raise RuntimeError("Paper exited while "+label)
        for name,(proc,path,_) in list(clients.items()):
            if proc.poll() is not None: raise RuntimeError(f"{name} exited {proc.returncode} while {label}")
            failure=load(path).get("failure")
            if failure: raise RuntimeError(name+": "+str(failure))
        time.sleep(.2)
    raise TimeoutError(label)

def check(name,condition,evidence=None):
    record={"name":name,"passed":bool(condition),"at":datetime.datetime.now(datetime.timezone.utc).isoformat()}
    if evidence is not None: record["evidence"]=evidence
    manifest["assertions"].append(record); print(("PASS " if condition else "FAIL ")+name,flush=True)
    save(EVIDENCE/"result.json",manifest)
    if not condition: raise AssertionError(name)

def state(): return load(server_result)
def client(name): return load(clients[name][1])
def packets(name,kind): return [p["payload"] for p in client(name).get("packets",[]) if p["type"]==kind]
def pcount(name,kind): return len(packets(name,kind))
def remote_counts(name):
    kinds=["REMOTE_MACHINE_SNAPSHOT","REMOTE_MACHINE_SPIN","REMOTE_MACHINE_STOP","REMOTE_MACHINE_NOTICE","REMOTE_MACHINE_BONUS","REMOTE_MACHINE_REMOVE","REMOTE_MACHINE_SOUND"]
    return {k:pcount(name,k) for k in kinds}
def message_count(name,text): return sum(text in m for m in client(name).get("messages",[]))
def action(name,kind,**args):
    proc,path,seq=clients[name]; seq+=1; clients[name]=(proc,path,seq)
    payload={"id":seq,"kind":kind,**args}; save(path.with_name(f"command-{seq}.json"),payload)
    wait(lambda:client(name).get("completed",0)>=seq,name+" action "+kind)
    manifest["commands"].append({"client":name,**payload})
def command(name,text,expected=None):
    before=message_count(name,expected) if expected else 0
    action(name,"command",text=text)
    if expected: wait(lambda:message_count(name,expected)>before,text+" -> "+expected)

def session(player="PiriRuntimeTest"):
    for s in state().get("sessions",[]):
        if s.get("player_uuid") and s.get("lifecycle")=="ACTIVE":
            return s
    return {}
def machine_count(): return len([m for m in state().get("machines",[]) if not m.get("deleted")])

def start_server():
    global server
    plugins=SERVER/"plugins"; plugins.mkdir(parents=True,exist_ok=True)
    shutil.copy2(artifacts["paper"],plugins); shutil.copy2(helpers["paper"],plugins)
    (SERVER/"eula.txt").write_text("eula=true\n",encoding="utf-8")
    (SERVER/"server.properties").write_text("\n".join([
        "server-ip=127.0.0.1","server-port=25590","online-mode=false","enforce-secure-profile=false",
        "enable-query=false","enable-rcon=false","max-players=4","view-distance=4","simulation-distance=4",
        "spawn-protection=0","generate-structures=false","allow-nether=true","level-type=minecraft:normal",
        "motd=Piri Phase12 isolated runtime"])+ "\n",encoding="utf-8")
    path=OUT/"server.log"; h=path.open("w",encoding="utf-8"); handles.append(h)
    cmd=[JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8","-Dpiri.runtime.phase=phase12",
         f"-Dpiri.runtime.serverResult={server_result}","-jar",str(PAPER),"nogui"]
    manifest["commands"].append({"server":"phase12","command":cmd,"cwd":str(SERVER)})
    server=subprocess.Popen(cmd,cwd=SERVER,stdin=subprocess.PIPE,stdout=h,stderr=subprocess.STDOUT,text=True,creationflags=FLAGS)
    wait(lambda:"Done (" in log(path) and state().get("ready"),"Paper ready",300)

def stop_server():
    global server
    if server and server.poll() is None:
        server.stdin.write("stop\n");server.stdin.flush();server.wait(timeout=60)
    if server:
        manifest["exits"].append({"server":server.returncode}); assert server.returncode==0; server=None

def start_client(name):
    folder=OUT/name; folder.mkdir(parents=True,exist_ok=True)
    result=folder/"client-result.json"
    if result.exists(): result.unlink()
    directory=EVIDENCE/"work"/("client-"+name)
    if directory.exists(): shutil.rmtree(directory)
    directory.mkdir(parents=True,exist_ok=True)
    (directory/"options.txt").write_text("version:3953\nlang:en_us\nrenderDistance:4\nsimulationDistance:5\nmaxFps:20\npauseOnLostFocus:false\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\nonboardAccessibility:false\n",encoding="utf-8")
    h=(folder/"client.log").open("a",encoding="utf-8"); handles.append(h)
    cmd=["cmd.exe","/d","/c",str(ROOT/"gradlew.bat"),"-PruntimeAcceptance=true",f"-PruntimeScenario={name}",
         f"-PruntimeRun={RUN}","-PruntimeEvidencePhase=PHASE_12",":runtime-test-client:runClient","--console=plain"]
    manifest["commands"].append({"client":name,"command":cmd,"cwd":str(ROOT)})
    proc=subprocess.Popen(cmd,cwd=ROOT,stdout=h,stderr=subprocess.STDOUT,creationflags=FLAGS)
    clients[name]=(proc,result,0)
    wait(lambda:client(name).get("connected") and client(name).get("handshake"),name+" real Fabric join",600)

def stop_client(name):
    if name not in clients:return
    action(name,"exit");proc,_,_=clients[name];proc.wait(timeout=60)
    manifest["exits"].append({"client":name,"exit":proc.returncode});assert proc.returncode==0
    del clients[name]

def start_mismatch():
    name="mismatch";folder=OUT/name;folder.mkdir(parents=True,exist_ok=True);result=folder/"client-result.json"
    if result.exists():result.unlink()
    h=(folder/"client.log").open("w",encoding="utf-8");handles.append(h)
    cmd=["cmd.exe","/d","/c",str(ROOT/"gradlew.bat"),"-PruntimeAcceptance=true","-PruntimeScenario=mismatch",
         f"-PruntimeRun={RUN}","-PruntimeEvidencePhase=PHASE_12",":runtime-test-client:runClient","--console=plain"]
    proc=subprocess.Popen(cmd,cwd=ROOT,stdout=h,stderr=subprocess.STDOUT,creationflags=FLAGS)
    wait(lambda:load(result).get("passed") is True,"protocol mismatch rejection",600)
    proc.wait(timeout=60);check("old protocol client is rejected",load(result).get("gameplayAllowed") is False,load(result))

def tap(name,key): action(name,"tap",key=key)
def wait_state(expected): wait(lambda:session().get("game_state")==expected,"state "+expected)
def clickpos(name,x,y,z,kind=None):
    before=pcount(name,kind) if kind else 0
    action(name,"clickpos",x=x,y=y,z=z)
    if kind:wait(lambda:pcount(name,kind)>before,name+" receive "+kind)

def create_machine(name,x,z,index):
    command(name,f"tp @s {x+0.5} 65 {z+3.5}")
    time.sleep(.05);action(name,"aimpos",x=x,y=66,z=z);time.sleep(.05)
    before=machine_count();command(name,"piri machine create")
    wait(lambda:machine_count()==before+1,f"machine {index} committed")

def forbidden_remote_payload(value):
    forbidden={"setting","internalRole","internal_role","premiumType","premium_type","stopHints","rng","rngSeed","seed","vault","heldMedals","held_medals"}
    if isinstance(value,dict):
        return bool(forbidden & set(value)) or any(forbidden_remote_payload(v) for v in value.values())
    if isinstance(value,list):return any(forbidden_remote_payload(v) for v in value)
    return False

try:
    start_server()
    start_client("phase12-owner"); owner="phase12-owner"
    coords=[(x,z) for x in range(-3,4) for z in range(-3,3)]
    for i,(x,z) in enumerate(coords,1): create_machine(owner,x,z,i)
    check("42 physical registered machines exist",machine_count()==42,{"machines":machine_count()})

    start_client("phase12-spectator"); spec="phase12-spectator"
    wait(lambda:pcount(spec,"REMOTE_MACHINE_SNAPSHOT")>=42 and client(spec).get("remoteCacheSize")==42,"42 initial remote snapshots")
    time.sleep(2)
    check("spectator initial interest has exactly one snapshot per machine",pcount(spec,"REMOTE_MACHINE_SNAPSHOT")==42,remote_counts(spec))

    idle_before=remote_counts(spec);time.sleep(30);idle_after=remote_counts(spec)
    check("42 idle machines emit zero continuing remote traffic",idle_after==idle_before,{"before":idle_before,"after":idle_after})

    # Range leave/re-entry.
    command(spec,"tp @s 100 65 100")
    wait(lambda:pcount(spec,"REMOTE_MACHINE_REMOVE")>=42 and client(spec).get("remoteCacheSize")==0,"range remove")
    remove_after=pcount(spec,"REMOTE_MACHINE_REMOVE")
    command(spec,"tp @s 0.5 65 3.5")
    wait(lambda:pcount(spec,"REMOTE_MACHINE_SNAPSHOT")>=84 and client(spec).get("remoteCacheSize")==42,"range re-entry")
    check("32-block leave removes and re-entry rebuilds fresh cache",remove_after==42 and client(spec).get("remoteCacheSize")==42,remote_counts(spec))

    # World change rebuild.
    before_remove=pcount(spec,"REMOTE_MACHINE_REMOVE")
    command(spec,"execute in minecraft:the_nether run tp @s 0 65 0")
    wait(lambda:pcount(spec,"REMOTE_MACHINE_REMOVE")>=before_remove+42 and client(spec).get("remoteCacheSize")==0,"world change remove")
    before_snap=pcount(spec,"REMOTE_MACHINE_SNAPSHOT")
    command(spec,"execute in minecraft:overworld run tp @s 0.5 65 3.5")
    wait(lambda:pcount(spec,"REMOTE_MACHINE_SNAPSHOT")>=before_snap+42 and client(spec).get("remoteCacheSize")==42,"world change rebuild")
    check("world change drops old interest and rebuilds overworld interest",True,remote_counts(spec))

    # Reconnect produces a clean cache.
    stop_client(spec);time.sleep(2);start_client(spec)
    wait(lambda:pcount(spec,"REMOTE_MACHINE_SNAPSHOT")==42 and client(spec).get("remoteCacheSize")==42,"reconnect fresh snapshots")
    check("spectator reconnect starts from fresh 42-machine snapshot",pcount(spec,"REMOTE_MACHINE_REMOVE")==0,remote_counts(spec))

    # Real gameplay remote transitions including notice and bonus start/end.
    x,z=coords[0];command(owner,f"tp @s {x+0.5} 65 {z+3.5}");clickpos(owner,x,66,z,"OPEN_MACHINE")
    command(owner,"piritest fund", "TEST_FUNDED")
    action(owner,"close");wait(lambda:not session(),"funded close")
    clickpos(owner,x,66,z,"OPEN_MACHINE");wait(lambda:session().get("credit")==50,"funded session refresh")
    command(owner,"piritest force reg","TEST_FORCE_ARMED")
    base=remote_counts(spec)
    tap(owner,32);wait_state("NORMAL_BETTED");tap(owner,32);wait_state("NORMAL_SPINNING")
    wait(lambda:pcount(spec,"REMOTE_MACHINE_SPIN")>=base["REMOTE_MACHINE_SPIN"]+1,"remote spin")
    for key,mask in [(263,1),(264,3),(262,7)]:
        tap(owner,key);wait(lambda:session().get("stopped_mask")==mask,"normal stop")
    wait_state("BONUS_PENDING_REG")
    wait(lambda:pcount(spec,"REMOTE_MACHINE_STOP")>=base["REMOTE_MACHINE_STOP"]+3,"remote three stops")
    pending_packets=[p for p in client(spec).get("packets",[]) if p["type"].startswith("REMOTE_MACHINE_")]
    check("BONUS_PENDING remote stream contains no hidden role/premium/setting state",not any(forbidden_remote_payload(p["payload"]) for p in pending_packets),{"remoteCounts":remote_counts(spec)})
    check("public notice is mirrored to spectator",pcount(spec,"REMOTE_MACHINE_NOTICE")>=base["REMOTE_MACHINE_NOTICE"]+1,remote_counts(spec))

    tap(owner,32);wait_state("BONUS_ENTRY_BETTED_REG");tap(owner,32);wait_state("BONUS_ENTRY_SPINNING_REG")
    for key in (263,264,262):tap(owner,key)
    wait_state("REG_READY")
    wait(lambda:pcount(spec,"REMOTE_MACHINE_BONUS")>=base["REMOTE_MACHINE_BONUS"]+1,"remote bonus start")
    for i in range(8):
        tap(owner,32);wait_state("REG_BETTED");tap(owner,32);wait_state("REG_SPINNING")
        for key in (263,264,262):tap(owner,key)
        wait_state("SEATED_READY" if i==7 else "REG_READY")
    wait(lambda:pcount(spec,"REMOTE_MACHINE_BONUS")>=base["REMOTE_MACHINE_BONUS"]+2,"remote bonus end")
    bonus_packets=packets(spec,"REMOTE_MACHINE_BONUS")[-2:]
    check("bonus start and end are mirrored only when public",bonus_packets[0].get("active") is True and bonus_packets[-1].get("active") is False,bonus_packets)
    check("real owner SPIN/STOP/NOTICE/BONUS all reached spectator",
          pcount(spec,"REMOTE_MACHINE_SPIN")>=base["REMOTE_MACHINE_SPIN"]+1 and
          pcount(spec,"REMOTE_MACHINE_STOP")>=base["REMOTE_MACHINE_STOP"]+3 and
          pcount(spec,"REMOTE_MACHINE_NOTICE")>=base["REMOTE_MACHINE_NOTICE"]+1 and
          pcount(spec,"REMOTE_MACHINE_BONUS")>=base["REMOTE_MACHINE_BONUS"]+2,remote_counts(spec))

    # Immediate remove/create/redefine reflection.
    action(owner,"close");wait(lambda:not session(),"owner close before admin machine changes")
    before_remove=pcount(spec,"REMOTE_MACHINE_REMOVE")
    command(owner,"piri machine remove 42")
    wait(lambda:pcount(spec,"REMOTE_MACHINE_REMOVE")>=before_remove+1 and client(spec).get("remoteCacheSize")==41,"immediate machine remove")
    rx,rz=coords[-1];command(owner,f"tp @s {rx+0.5} 65 {rz+3.5}");action(owner,"aimpos",x=rx,y=66,z=rz)
    before_snap=pcount(spec,"REMOTE_MACHINE_SNAPSHOT");command(owner,"piri machine create")
    wait(lambda:pcount(spec,"REMOTE_MACHINE_SNAPSHOT")>=before_snap+1 and client(spec).get("remoteCacheSize")==42,"immediate machine create")
    command(owner,"tp @s 4.5 65 5.5");action(owner,"aimpos",x=4,y=66,z=2)
    before_snap=pcount(spec,"REMOTE_MACHINE_SNAPSHOT");command(owner,"piri machine redefine 43")
    wait(lambda:pcount(spec,"REMOTE_MACHINE_SNAPSHOT")>=before_snap+1 and client(spec).get("remoteCacheSize")==42,"immediate redefine snapshot")
    check("create/remove/redefine changes reflect immediately to spectator",True,remote_counts(spec))

    start_mismatch()

    stop_client(spec);stop_client(owner);stop_server()
    manifest["passed"]=True
except Exception as error:
    manifest["failure"]=str(error);print("RUNTIME FAILURE: "+str(error),flush=True)
finally:
    for name,(proc,_,_) in list(clients.items()):
        if proc.poll() is None:
            try:stop_client(name)
            except Exception:subprocess.run(["taskkill","/PID",str(proc.pid),"/T","/F"],capture_output=True)
    if server and server.poll() is None:
        try:stop_server()
        except Exception:subprocess.run(["taskkill","/PID",str(server.pid),"/T","/F"],capture_output=True)
    manifest["finishedAt"]=datetime.datetime.now(datetime.timezone.utc).isoformat()
    save(EVIDENCE/"result.json",manifest);save(OUT/"result.json",manifest)
    report=["# Phase12 Runtime Acceptance","",f"- Run: {RUN}",f"- PASS: {manifest['passed']}","",
            "| Assertion | Result |","|---|---|"]+[f"| {a['name']} | {'PASS' if a['passed'] else 'FAIL'} |" for a in manifest["assertions"]]
    if "failure" in manifest:report+=["",f"Failure: `{manifest['failure']}`"]
    (EVIDENCE/"REPORT.md").write_text("\n".join(report)+"\n",encoding="utf-8")
    for h in handles:
        try:h.close()
        except Exception:pass
if not manifest["passed"]: raise SystemExit(1)
