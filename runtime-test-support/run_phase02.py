"""Real-client Phase02 acceptance. Built production jars are loaded by Paper/Fabric.
The optional helpers control actual Minecraft inputs and observe committed production state.
"""
from pathlib import Path
import datetime, hashlib, json, os, shutil, sqlite3, subprocess, time

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "runtime-evidence/PHASE_02"
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
    (SERVER/"server.properties").write_text("\n".join(["server-ip=127.0.0.1","server-port=25586","online-mode=false","enforce-secure-profile=false","enable-query=false","enable-rcon=false","max-players=2","view-distance=2","simulation-distance=2","spawn-protection=0","generate-structures=false","level-type=minecraft:normal","motd=Piri Phase02 isolated runtime"])+"\n",encoding="utf-8")
    folder=OUT/round; folder.mkdir(parents=True,exist_ok=True); server_result=folder/"server-result.json"
    path=folder/"server.log"; handle=path.open("w",encoding="utf-8"); handles.append(handle)
    cmd=[JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8","-Dpiri.runtime.phase=phase02",f"-Dpiri.runtime.serverResult={server_result}","-jar",str(PAPER),"nogui"]
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
    (directory/"options.txt").write_text("version:3953\nlang:en_us\nrenderDistance:2\nsimulationDistance:5\nmaxFps:20\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\nonboardAccessibility:false\n",encoding="utf-8")
    handle=(folder/"client.log").open("w",encoding="utf-8"); handles.append(handle)
    cmd=["cmd.exe","/d","/c",str(ROOT/"gradlew.bat"),"-PruntimeAcceptance=true",f"-PruntimeScenario={name}",f"-PruntimeRun={RUN}",":runtime-test-client:runClient","--console=plain"]
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

try:
    start_server("before-restart"); start_client("phase02-create"); a="phase02-create"
    check("real world Buttons placed",all(b["type"]=="STONE_BUTTON" for b in state()["buttons"]),snapshot("buttons"))
    action(a,"aim",x=0); command(a,"piri machine create","MACHINE_CREATED 1")
    wait(lambda:len(state().get("machines",[]))==1,"machine committed")
    check("actual player raytrace command registered Button",state()["commands"][-1]["rayTarget"]=="STONE_BUTTON:0",snapshot("created"))
    command(a,"piri machine create","LOCATION_ALREADY_REGISTERED")
    check("duplicate location rejected",len(state()["machines"])==1,snapshot("duplicate"))
    action(a,"aim",x=2); command(a,"piri machine create","MACHINE_CREATED 2")
    click(a,0,"OPEN_MACHINE"); wait(lambda:len(packets(a,"PUBLIC_STATE"))>0 and len(state()["sessions"])==1,"public state and durable session")
    session=state()["sessions"][0]; original_session=session["session_id"]; old_period=state()["period"]
    check("real session plus OPEN_MACHINE and PUBLIC_STATE",packets(a,"OPEN_MACHINE")[-1]["sessionId"]==original_session and client(a)["productionSession"]==original_session and session["credit"]==0 and session["held_medals"]==0,snapshot("opened"))
    check("registered Button vanilla activation canceled",any(c["x"]==0 and c["denied"] for c in state()["clicks"]) and not state()["buttons"][0]["powered"],snapshot("cancel"))
    before=message_count(a,"RECOVERY_REQUIRED"); click(a,2); wait(lambda:message_count(a,"RECOVERY_REQUIRED")>before,"second session rejection")
    check("same player second session rejected",len(state()["sessions"])==1,snapshot("second-session"))
    command(a,"piri machine remove 1","MACHINE_OCCUPIED")
    command(a,"piri key give","KEY_GIVEN"); action(a,"slot",slot=0); click(a,0,"ADMIN_STATE")
    check("machine key opens admin entry even while busy",packets(a,"ADMIN_STATE")[-1]["busy"] and "adminSessionId" in packets(a,"ADMIN_STATE")[-1],snapshot("admin"))
    action(a,"slot",slot=8); action(a,"screenshot"); stop_client(a)
    wait(lambda:state()["sessions"][0]["lifecycle"]=="SUSPENDED_GRACE","disconnect persisted")
    check("disconnect grace persists lock snapshot",state()["sessions"][0]["session_id"]==original_session,snapshot("grace"))
    stop_server()
    with sqlite3.connect(SERVER/"plugins/PiriJuggler/piri.db") as db:
        rows=db.execute("SELECT machine_id,world_uuid,x,y,z FROM machines ORDER BY machine_id").fetchall()
        check("registration durable on disk after process exit",len(rows)==2,{"databaseRows":rows})
    start_server("after-restart")
    check("true JVM restart preserves registration and creates new period",state()["period"]!=old_period and [m["id"] for m in state()["machines"]]==[1,2],snapshot("restart"))
    check("restart retains session as SAFE before new period",state()["sessions"][0]["session_id"]==original_session and state()["sessions"][0]["lifecycle"]=="SUSPENDED_SAFE" and state()["sessions"][0]["source_business_period_id"]==old_period,snapshot("restart-session"))
    start_client("phase02-owner"); a="phase02-owner"; click(a,0,"OPEN_MACHINE")
    wait(lambda:state()["sessions"][0]["lifecycle"]=="ACTIVE","resume persisted")
    check("SAFE resume reuses session and current period",packets(a,"OPEN_MACHINE")[-1]["sessionId"]==original_session and state()["sessions"][0]["source_business_period_id"]==state()["period"],snapshot("resume"))
    start_client("phase02-other"); b="phase02-other"; click(b,0,"ERROR")
    check("second actual Fabric client occupied click rejected",packets(b,"ERROR")[-1]["errorCode"]=="MACHINE_OCCUPIED" and len(state()["sessions"])==1 and not packets(b,"OPEN_MACHINE") and state()["sessions"][0]["lifecycle"]=="ACTIVE" and clients[a][0].poll() is None,snapshot("occupied"))
    action(a,"close"); wait(lambda:len(packets(a,"SESSION_END"))>0 and len(state()["sessions"])==0,"empty session closed")
    click(b,0,"OPEN_MACHINE"); action(b,"close"); wait(lambda:len(state()["sessions"])==0 and len(packets(b,"SESSION_END"))==1 and client(b).get("productionSession") is None,"second player close acknowledged")
    check("close deletes empty session and releases lock for other player",len(packets(b,"SESSION_END"))==1 and set(packets(b,"SESSION_END")[0])=={"sessionId"} and client(b).get("productionSession") is None,snapshot("close"))
    old_machine=state()["machines"][0]
    action(a,"aim",x=3); command(a,"piri machine redefine 1","MACHINE_REDEFINED 1")
    wait(lambda:state()["machines"][0]["location"]["x"]==3,"redefine persisted")
    check("redefine changes real Button location preserving machine identity",state()["machines"][0]["id"]==1 and state()["machines"][0]["setting"]==old_machine["setting"] and state()["commands"][-1]["rayTarget"]=="STONE_BUTTON:3",snapshot("redefine"))
    action(a,"aim",x=2); command(a,"piri machine redefine 1","LOCATION_ALREADY_REGISTERED")
    click(a,3,"OPEN_MACHINE"); action(a,"close"); wait(lambda:len(state()["sessions"])==0,"redefined machine close")
    command(a,"piri machine remove 1","MACHINE_REMOVED 1")
    wait(lambda:state()["machines"][0]["deleted"],"remove persisted")
    check("remove soft-deletes registration while real Button remains",not state()["machines"][0]["enabled"] and all(b["type"]=="STONE_BUTTON" for b in state()["buttons"]),snapshot("remove"))
    action(a,"aim",x=3); command(a,"piri machine create","MACHINE_CREATED 3")
    wait(lambda:len(state()["machines"])==3,"new machine registration observed")
    check("deleted machine ID is never reused",[m["id"] for m in state()["machines"]]==[1,2,3],snapshot("id-not-reused"))
    action(a,"screenshot"); action(b,"screenshot"); stop_client(b); stop_client(a); stop_server()
    with sqlite3.connect(SERVER/"plugins/PiriJuggler/piri.db") as db:
        check("old period stats/history retained after redefine/remove",db.execute("SELECT count(*) FROM machine_period_stats WHERE machine_id=1").fetchone()[0]==2 and db.execute("SELECT count(*) FROM setting_history WHERE machine_id=1").fetchone()[0]==1)
        check("SQLite integrity and foreign keys",db.execute("PRAGMA integrity_check").fetchone()[0]=="ok" and not db.execute("PRAGMA foreign_key_check").fetchall())
    manifest["passed"]=True
except Exception as error:
    manifest["failure"]=str(error); print("RUNTIME FAILURE: "+str(error),flush=True)
finally:
    for name,(proc,_,_) in list(clients.items()):
        if proc.poll() is None:
            try: stop_client(name)
            except Exception: subprocess.run(["taskkill","/PID",str(proc.pid),"/T","/F"],capture_output=True)
    if server and server.poll() is None:
        try: stop_server()
        except Exception: server.terminate(); server.wait(timeout=20)
    for handle in handles: handle.close()
    unexpected=[]
    for path in OUT.rglob("*.log"):
        for line in log(path).splitlines():
            if any(marker in line for marker in ("Exception in thread","Unreported exception thrown","Encountered an unexpected exception","Error occurred while enabling","Could not pass event","Mixin apply for mod","Gameplay disabled: database","Database operation failed")): unexpected.append({"log":str(path),"line":line})
    manifest["uncaughtExceptions"]=unexpected
    manifest["passed"]=manifest["passed"] and not unexpected
    manifest["finishedAt"]=datetime.datetime.now(datetime.timezone.utc).isoformat()
    for side in ("server","client"):
        (EVIDENCE/(side+".log")).write_text("\n".join(str(p.relative_to(OUT))+"\n"+log(p) for p in OUT.rglob(side+".log")),encoding="utf-8")
    for image in (EVIDENCE/"work").glob("client-*/screenshots/phase02-*.png"):
        (EVIDENCE/"screenshots").mkdir(exist_ok=True); shutil.copy2(image,EVIDENCE/"screenshots"/image.name)
    save(OUT/"result.json",manifest); save(EVIDENCE/"result.json",manifest)
print("RUNTIME_ACCEPTANCE="+("PASS" if manifest["passed"] else "FAIL"),flush=True)
raise SystemExit(0 if manifest["passed"] else 1)
