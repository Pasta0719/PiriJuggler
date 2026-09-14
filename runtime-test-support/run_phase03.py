"""Real-client Phase03 acceptance. Built production jars are loaded by Paper/Fabric.
The optional helpers control actual Minecraft inputs and observe committed production state.
"""
from pathlib import Path
import datetime, hashlib, json, os, shutil, sqlite3, subprocess, time

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "runtime-evidence/PHASE_03"
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
    (SERVER/"server.properties").write_text("\n".join(["server-ip=127.0.0.1","server-port=25587","online-mode=false","enforce-secure-profile=false","enable-query=false","enable-rcon=false","max-players=2","view-distance=2","simulation-distance=2","spawn-protection=0","generate-structures=false","level-type=minecraft:normal","motd=Piri Phase03 isolated runtime"])+"\n",encoding="utf-8")
    folder=OUT/round; folder.mkdir(parents=True,exist_ok=True); server_result=folder/"server-result.json"
    path=folder/"server.log"; handle=path.open("w",encoding="utf-8"); handles.append(handle)
    cmd=[JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8","-Dpiri.runtime.phase=phase03",f"-Dpiri.runtime.serverResult={server_result}","-jar",str(PAPER),"nogui"]
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
    cmd=["cmd.exe","/d","/c",str(ROOT/"gradlew.bat"),"-PruntimeAcceptance=true",f"-PruntimeScenario={name}",f"-PruntimeRun={RUN}", "-PruntimeEvidencePhase=PHASE_03",":runtime-test-client:runClient","--console=plain"]
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

def incoming(kind): return [p for p in state().get("inbound",[]) if p["type"]==kind]
def key(code, action_type=1): action(a,"key",key=code,action=action_type)
def tap(code): key(code); key(code,0)
def fixture(kind,body):
    seq=state().get("fixtureCompleted",0)+1
    save(server_result.with_name(f"fixture-{seq}.json"),{"type":kind,"payload":body})
    count=len(packets(a,kind))
    wait(lambda:state().get("fixtureCompleted",0)>=seq and len(packets(a,kind))>count,"S2C display fixture "+kind)
def capture(label):
    action(a,"capture",label=label)
    path=EVIDENCE/"work"/("client-"+a)/"screenshots"/("phase03-"+label+".png")
    wait(lambda:path.exists() and path.stat().st_size>1000,"actual screenshot "+label)
    (OUT/"screenshots").mkdir(exist_ok=True);shutil.copy2(path,OUT/"screenshots"/path.name)
    if label!="chat-only-on-t":
        audit=subprocess.run([JAVA,str(ROOT/"runtime-test-support/ScreenshotAudit.java"),str(path)],capture_output=True,text=True,creationflags=FLAGS)
        (OUT/"screenshots"/(label+"-audit.txt")).write_text(audit.stdout+audit.stderr,encoding="utf-8")
        check("actual screenshot contains all three reel symbol columns: "+label,audit.returncode==0,str(path))

try:
    start_server("ui");start_client("phase03-ui");a="phase03-ui"
    action(a,"aim",x=0);command(a,"piri machine create","MACHINE_CREATED 1")
    click(a,0,"OPEN_MACHINE")
    wait(lambda:client(a).get("screen")=="SlotScreen" and client(a).get("publicState") is not None and len(state().get("sessions",[]))==1,"actual Slot Screen and authoritative state")
    capture("open-off")
    check("actual production SlotScreen opens from real Button session",client(a)["screen"]=="SlotScreen" and client(a)["productionSession"]==state()["sessions"][0]["session_id"],snapshot("open"))
    check("HUD hidden and mouse cursor visible",client(a)["hudHidden"] and not client(a)["cursorLocked"] and client(a)["hudLeaks"]==0 and client(a)["hudWorldCalls"]>0,snapshot("hud"))
    check("all 10 SoundEvents registered with user audio absent",len(client(a)["sounds"])==10 and all(s["registered"] and not s["available"] for s in client(a)["sounds"].values()),snapshot("optional-audio"))
    for code,kind in [(32,"SPACE_ACTION"),(263,"STOP_LEFT"),(264,"STOP_CENTER"),(262,"STOP_RIGHT"),(76,"LOAN"),(73,"INSERT_MEDALS"),(82,"CASH_OUT")]:
        count=len(incoming(kind));key(code);key(code,2);key(code,2);key(code,0)
        wait(lambda:len(incoming(kind))>count,"real key -> "+kind)
        check("real Keyboard.onKey edge/repeat -> "+kind,len(incoming(kind))==count+1,snapshot("key-"+kind.lower()))
    action(a,"rebind",key=65);count=len(incoming("STOP_LEFT"));tap(65)
    wait(lambda:len(incoming("STOP_LEFT"))==count+1,"rebound STOP_LEFT")
    check("Minecraft KeyBinding rebound LEFT to A",len(incoming("STOP_LEFT"))==count+1,snapshot("rebind"));action(a,"rebind",key=263)
    count=len(incoming("STOP_CENTER"));action(a,"mouse",x=1080,y=920,button=1)
    check("right mouse button ignored",len(incoming("STOP_CENTER"))==count)
    action(a,"mouse",x=1080,y=920,button=0);wait(lambda:len(incoming("STOP_CENTER"))==count+1,"Mouse.onMouseButton -> STOP_CENTER")
    check("actual mouse event hits logical stop rectangle",len(incoming("STOP_CENTER"))==count+1,snapshot("mouse"))
    close_count=len(incoming("CLOSE_REQUEST"));tap(84);wait(lambda:client(a).get("screen")=="SlotChatScreen","T opens chat")
    capture("chat-only-on-t");tap(256);wait(lambda:client(a).get("screen")=="SlotScreen","chat Escape returns")
    check("T opens chat; chat Escape returns without CLOSE_REQUEST",len(incoming("CLOSE_REQUEST"))==close_count and client(a)["hudLeaks"]==0,snapshot("chat"))
    sid=client(a)["productionSession"];mid=packets(a,"OPEN_MACHINE")[-1]["machineId"]
    public={"sessionId":sid,"machineId":mid,"gameState":"SEATED_READY","lifecycle":"ACTIVE","expectedNextClientSequence":100,"credit":32,"heldMedals":442,"bet":3,"pay":14,"bonusCount":0,"lampOn":True,"displayStops":{"left":3,"center":3,"right":3},"stoppedMask":7}
    fixture("PUBLIC_STATE",public)
    fixture("DATA_LAMP",{"machineId":mid,"totalGames":3500,"bigCount":14,"regCount":11,"currentGames":72,"todayDifference":320,"todayMaxDifference":880,"piriChain":True,"history":[],"graph":[]})
    wait(lambda:client(a).get("publicState",{}).get("credit")==32,"fixture PUBLIC_STATE rendered")
    capture("on-reels-status")
    check("S2C display fixture reaches public model without mutating server assets",client(a)["publicState"]==public and state()["sessions"][0]["credit"]==0 and state()["sessions"][0]["held_medals"]==0,snapshot("public-fixture"))
    spin="00000000-0000-0000-0000-000000000003"
    fixture("SPIN_START",{"sessionId":sid,"machineId":mid,"spinId":spin,"mode":"NORMAL","animation":"REVERSE_500MS","startPhase":{"left":8.0,"center":3.0,"right":12.0},"stopEnableAfterMs":400})
    fixture("NOTICE",{"spinId":spin,"lamp":"ON","pattern":"FAST_BLINK_1S","sound":"NOTICE_X5"})
    for reel,index in [("LEFT",14),("CENTER",3),("RIGHT",3)]:
        slip=6 if reel=="LEFT" else 0
        fixture("REEL_STOP",{"spinId":spin,"reel":reel,"pressedIndex":(index+slip)%21,"stopIndex":index,"slip":slip,"durationMs":min(1200,80+50*slip)})
    fixture("TENPAI_SOUND",{"spinId":spin});fixture("BONUS_START",{"bonusType":"BIG","count":0});fixture("BONUS_END",{"bonusType":"BIG","finalCount":280})
    capture("packet-animation")
    check("SPIN_START REEL_STOP NOTICE and missing-audio replay path stay healthy",client(a).get("failure") is None and client(a)["screen"]=="SlotScreen" and client(a)["hudLeaks"]==0,snapshot("animation"))
    action(a,"resize",width=1100,height=700);wait(lambda:client(a).get("width")==1100 and client(a).get("height")==700,"non-16:9 window")
    capture("letterbox")
    count=len(incoming("STOP_CENTER"));action(a,"mouse",x=1080,y=920,button=0);wait(lambda:len(incoming("STOP_CENTER"))==count+1,"letterboxed hit testing")
    check("aspect-preserving viewport and mouse mapping after resize",len(incoming("STOP_CENTER"))==count+1,snapshot("letterbox"))
    before=len(incoming("CLOSE_REQUEST"));tap(256)
    wait(lambda:len(packets(a,"SESSION_END"))>0 and client(a).get("screen")=="none" and client(a).get("productionSession") is None and not state()["sessions"],"real Escape close acknowledged")
    check("Escape sends one CLOSE_REQUEST and closes on server SESSION_END",len(incoming("CLOSE_REQUEST"))==before+1,snapshot("close"))
    gameplay=[p for p in state()["inbound"] if p["type"]!="HELLO"]
    sequences=[p["payload"]["clientSequence"] for p in gameplay]
    check("every real action has only identity and strictly increasing sequence",all(set(p["payload"])=={"sessionId","machineId","clientSequence"} for p in gameplay) and sequences==sorted(set(sequences)),snapshot("wire-contract"))
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
