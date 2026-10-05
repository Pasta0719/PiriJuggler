"""Pachinko Phase10 real Paper/Fabric acceptance.

Production Paper/Fabric jars are exercised over real Minecraft networking. The
test-only helper only prepares persisted pachinko states so reconnect/restart and
all production transition branches can be verified deterministically.
"""
from pathlib import Path
import datetime, hashlib, json, os, shutil, subprocess, time

ROOT=Path(__file__).resolve().parents[1]
E=ROOT/"runtime-evidence/PACHINKO_PHASE_10"
RUN=datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
OUT=E/"attempts"/RUN
SERVER=E/"work"/("server-"+RUN)
JAVA=shutil.which("java")
PAPER=ROOT/"runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar"
FLAGS=subprocess.CREATE_NO_WINDOW if os.name=="nt" else 0
GRADLE=["cmd.exe","/d","/c",str(ROOT/"gradlew.bat")] if os.name=="nt" else [str(ROOT/"gradlew")]
prod={s:ROOT/s/f"build/libs/piri-juggler-{s}-1.0.0.jar" for s in ("paper","fabric")}
helper=ROOT/"runtime-test-support/paper/build/libs/piri-runtime-test-paper-1.0.0.jar"
OUT.mkdir(parents=True,exist_ok=True)

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def save(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    tmp=path.with_suffix(".tmp");tmp.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding="utf-8")
    for i in range(20):
        try:tmp.replace(path);return
        except PermissionError:
            if i==19:raise
            time.sleep(.05)
_cache={}
def load(path):
    try:_cache[path]=json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError,json.JSONDecodeError,PermissionError):pass
    return _cache.get(path,{})
def log(path):return path.read_text(encoding="utf-8",errors="replace") if path.exists() else ""

manifest={"run":RUN,"startedAt":datetime.datetime.now(datetime.timezone.utc).isoformat(),"passed":False,
          "buildHashes":{k:sha(v) for k,v in prod.items()},"helperHash":sha(helper),"assertions":[],"commands":[],"exits":[]}
server=None;clients={};handles=[];server_result=OUT/"server-result.json"

def wait(pred,label,timeout=180):
    until=time.monotonic()+timeout
    while time.monotonic()<until:
        if pred():return
        if server is not None and server.poll() is not None:
            raise RuntimeError("Paper exited while "+label+" exit="+str(server.returncode)+"\n"+log(OUT/"server.log")[-12000:])
        for name,(proc,path,_) in list(clients.items()):
            if proc.poll() is not None:raise RuntimeError(f"{name} exited {proc.returncode} while {label}\n"+log(path.with_name("client.log"))[-8000:])
            failure=load(path).get("failure")
            if failure:raise RuntimeError(name+": "+str(failure))
        time.sleep(.15)
    raise TimeoutError(label)

def check(name,condition,evidence=None):
    row={"name":name,"passed":bool(condition),"at":datetime.datetime.now(datetime.timezone.utc).isoformat()}
    if evidence is not None:row["evidence"]=evidence
    manifest["assertions"].append(row);save(E/"result.json",manifest)
    print(("PASS " if condition else "FAIL ")+name,flush=True)
    if not condition:raise AssertionError(name)

def state():return load(server_result)
def client(name):return load(clients[name][1])
def packets(name,kind):return [p["payload"] for p in client(name).get("packets",[]) if p.get("type")==kind]
def pcount(name,kind):return len(packets(name,kind))
def message_count(name,text):return sum(text in m for m in client(name).get("messages",[]))
def action(name,kind,**args):
    proc,path,seq=clients[name];seq+=1;clients[name]=(proc,path,seq)
    request={"id":seq,"kind":kind,**args};save(path.with_name(f"command-{seq}.json"),request)
    wait(lambda:client(name).get("completed",0)>=seq,name+" action "+kind,120)
    manifest["commands"].append({"client":name,**request})
def command(name,text,expected=None):
    before=message_count(name,expected) if expected else 0
    action(name,"command",text=text)
    if expected:wait(lambda:message_count(name,expected)>before,text+" -> "+expected,120)
def click(name,x):
    before=pcount(name,"OPEN_MACHINE");action(name,"clickpos",x=x,y=66,z=0)
    wait(lambda:pcount(name,"OPEN_MACHINE")>before,name+" OPEN_MACHINE",120)
def event_rows(name,machine=None,side=None):
    rows=packets(name,"PACHINKO_EVENT")
    if machine is not None:rows=[r for r in rows if r.get("machineId")==machine]
    if side is not None:rows=[r for r in rows if r.get("side")==side]
    return rows
def remote(name,machine):
    return client(name).get("remotePresentation",{}).get(str(machine),{})
def remote_machine(name,machine):
    return client(name).get("remoteMachines",{}).get(str(machine),{})
def machine(mid):
    return next((m for m in state().get("machines",[]) if m.get("id")==mid and not m.get("deleted")),None)
def machine_runtime(mid):
    m=machine(mid)
    if not m:return {}
    raw=m.get("runtimeJson")
    return json.loads(raw) if isinstance(raw,str) and raw else (raw or {})
def session_for(name):
    sid=client(name).get("productionSession")
    if not sid:return None
    return next((s for s in state().get("sessions",[]) if s.get("session_id")==sid),None)
def session_runtime(name):
    s=session_for(name)
    if not s:return {}
    raw=s.get("machine_state_json")
    return json.loads(raw) if isinstance(raw,str) and raw else (raw or {})
def machine_count():
    return len([m for m in state().get("machines",[]) if not m.get("deleted")])

def start_server():
    global server
    _cache.pop(server_result,None)
    if server_result.exists(): server_result.unlink()
    plugins=SERVER/"plugins";plugins.mkdir(parents=True,exist_ok=True)
    shutil.copy2(prod["paper"],plugins);shutil.copy2(helper,plugins)
    (SERVER/"eula.txt").write_text("eula=true\n",encoding="utf-8")
    (SERVER/"server.properties").write_text("\n".join([
        "server-ip=127.0.0.1","server-port=25604","online-mode=false","enforce-secure-profile=false",
        "enable-query=false","enable-rcon=false","max-players=4","view-distance=4","simulation-distance=4",
        "spawn-protection=0","generate-structures=false","allow-nether=true","level-type=minecraft:normal",
        "motd=Piri Pachinko Phase10 runtime"])+ "\n",encoding="utf-8")
    h=(OUT/"server.log").open("a",encoding="utf-8");handles.append(h)
    cmd=[JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8","-Dpiri.runtime.phase=pachinko10",
         f"-Dpiri.runtime.serverResult={server_result}","-jar",str(PAPER),"nogui"]
    manifest["commands"].append({"server":"pachinko10","command":cmd,"cwd":str(SERVER)})
    server=subprocess.Popen(cmd,cwd=SERVER,stdin=subprocess.PIPE,stdout=h,stderr=subprocess.STDOUT,text=True,creationflags=FLAGS)
    wait(lambda:"Done (" in log(OUT/"server.log") and state().get("ready"),"Paper ready",300)

def stop_server():
    global server
    if server and server.poll() is None:
        server.stdin.write("stop\n");server.stdin.flush();server.wait(timeout=90)
    if server:
        manifest["exits"].append({"server":server.returncode});check("Paper exits cleanly",server.returncode==0,server.returncode);server=None

def start_client(name,scenario):
    folder=OUT/scenario;folder.mkdir(parents=True,exist_ok=True)
    result=folder/"client-result.json"
    _cache.pop(result,None)
    if result.exists():result.unlink()
    for stale in folder.glob("command-*.json"): stale.unlink()
    run_dir=E/"work"/("client-"+scenario)
    if run_dir.exists():shutil.rmtree(run_dir)
    run_dir.mkdir(parents=True,exist_ok=True)
    (run_dir/"options.txt").write_text("version:3953\nlang:en_us\nrenderDistance:4\nsimulationDistance:5\nmaxFps:30\npauseOnLostFocus:false\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\nonboardAccessibility:false\n",encoding="utf-8")
    h=(folder/"client.log").open("a",encoding="utf-8");handles.append(h)
    cmd=GRADLE+["-PruntimeAcceptance=true",f"-PruntimeScenario={scenario}",f"-PruntimeRun={RUN}",
                "-PruntimeEvidencePhase=PACHINKO_PHASE_10",":runtime-test-client:runClient","--console=plain"]
    manifest["commands"].append({"client":name,"scenario":scenario,"command":cmd})
    proc=subprocess.Popen(cmd,cwd=ROOT,stdout=h,stderr=subprocess.STDOUT,creationflags=FLAGS)
    clients[name]=(proc,result,0)
    wait(lambda:client(name).get("connected") and client(name).get("handshake"),name+" real Fabric join",600)

def stop_client(name):
    if name not in clients:return
    action(name,"exit");proc,_,_=clients[name];proc.wait(timeout=90)
    manifest["exits"].append({"client":name,"exit":proc.returncode});check(name+" exits cleanly",proc.returncode==0,proc.returncode)
    del clients[name]

def create_machine(name,x,mtype):
    command(name,f"tp @s {x+.5} 65 2.5")
    action(name,"aimpos",x=x,y=66,z=0)
    before=machine_count();command(name,f"piri machine create {mtype}","MACHINE_CREATED")
    wait(lambda:machine_count()==before+1,f"create {mtype} at {x}")

def fixture(name,mode):
    before=message_count(name,"PACHINKO_FIXTURE_READY")
    command(name,f"piripachinko {mode}")
    wait(lambda:message_count(name,"PACHINKO_FIXTURE_READY")>before,"fixture "+mode,120)
    def reflected():
        s=session_for(name)
        if not s:return False
        r=machine_runtime(s.get("machine_id"))
        return {
            "normal": lambda: r.get("mode")=="NORMAL" and r.get("presentation")=="IDLE" and r.get("rushActive") is False,
            "left_v": lambda: r.get("presentation")=="LEFT_KURUN" and r.get("initialHitCommitted") is True,
            "left_out": lambda: r.get("presentation")=="LEFT_KURUN" and r.get("initialHitCommitted") is False,
            "rush": lambda: r.get("mode")=="RUSH" and r.get("presentation")=="IDLE" and r.get("rushActive") is True,
            "right_out": lambda: r.get("presentation")=="RIGHT_KURUN" and r.get("rightOutcome")=="OUT",
            "right_1500": lambda: r.get("presentation")=="RIGHT_KURUN" and r.get("rightOutcome")=="WIN_1500",
            "right_3000": lambda: r.get("presentation")=="RIGHT_KURUN" and r.get("rightOutcome")=="WIN_3000",
        }[mode]()
    wait(reflected,"fixture reflected "+mode,10)

def send_packet(name,ptype):
    action(name,"packet",type=ptype)

def reopen(name,x):
    if client(name).get("productionSession"):
        action(name,"close")
        wait(lambda:client(name).get("productionSession") is None,name+" close",120)
    click(name,x)
    wait(lambda:client(name).get("screen")=="PachinkoScreen",name+" PachinkoScreen",120)

def restart_client_at(name,scenario,x):
    stop_client(name);time.sleep(.5);start_client(name,scenario);click(name,x)
    wait(lambda:client(name).get("screen")=="PachinkoScreen",name+" reconnect PachinkoScreen",120)

try:
    start_server()
    start_client("owner","pachinko10-owner")
    create_machine("owner",0,"PACHINKO")
    create_machine("owner",2,"PACHINKO")
    create_machine("owner",3,"JUGGLER")
    check("two pachinko plus one slot machines created",machine_count()==3,[m.get("type") for m in state().get("machines",[])])

    start_client("peer","pachinko10-peer")
    wait(lambda:len(client("peer").get("remoteMachines",{}))>=3,"peer receives machine snapshots",120)

    click("owner",0)
    wait(lambda:client("owner").get("screen")=="PachinkoScreen","owner pachinko controls")
    check("production client routes PACHINKO to transparent dedicated screen",client("owner").get("screen")=="PachinkoScreen",client("owner").get("screen"))

    # Owner + observer same committed left V, including reconnect reconstruction.
    fixture("owner","left_v")
    reopen("owner",0)
    wait(lambda:len(event_rows("owner",1,"LEFT"))>=1 and len(event_rows("peer",1,"LEFT"))>=1,"owner and peer left event",120)
    oe=event_rows("owner",1,"LEFT")[-1];pe=event_rows("peer",1,"LEFT")[-1]
    keys=("machineId","ballSequenceId","side","outcome","seed","startTime")
    check("owner and observer receive identical left V identity/outcome",all(oe.get(k)==pe.get(k) for k in keys) and oe.get("outcome")=="V",{"owner":oe,"peer":pe})
    wait(lambda:remote("owner",1).get("pachinkoSide") in ("LEFT","LEFT_KURUN") and remote("peer",1).get("pachinkoSide") in ("LEFT","LEFT_KURUN"),"left visual reconstruction")
    check("owner and observer both have renderable left-kurun state",
          remote("owner",1).get("pachinkoOutcome")=="V" and remote("peer",1).get("pachinkoOutcome")=="V",
          {"owner":remote("owner",1),"peer":remote("peer",1)})

    # Mid-sequence observer join reconstructs current committed event instead of rerolling.
    committed=dict(remote("peer",1))
    stop_client("peer");start_client("peer","pachinko10-peer")
    wait(lambda:remote("peer",1).get("pachinkoSide") in ("LEFT","LEFT_KURUN"),"peer mid-sequence snapshot",120)
    rebuilt=remote("peer",1)
    check("mid-sequence observer rebuild keeps sequence/outcome/seed/start time",
          rebuilt.get("pachinkoOutcome")=="V" and rebuilt.get("pachinkoSequence")==committed.get("pachinkoSequence")
          and rebuilt.get("pachinkoSeed")==committed.get("pachinkoSeed") and rebuilt.get("pachinkoStartTime")==committed.get("pachinkoStartTime"),
          {"before":committed,"after":rebuilt})

    # Production 40:60 allocation over real client packet path. Unit/stat tests in the
    # workflow enforce tight probability tolerance; runtime also proves both branches.
    initial=[]
    for i in range(60):
        fixture("owner","left_v")
        before=len(event_rows("owner",1,"INITIAL_PAYOUT"));send_packet("owner","PACHINKO_PRESENTATION")
        wait(lambda:len(event_rows("owner",1,"INITIAL_PAYOUT"))>before,"initial allocation "+str(i),30)
        initial.append(event_rows("owner",1,"INITIAL_PAYOUT")[-1].get("outcome"))
    rush_count=sum(v=="RUSH_1500" for v in initial);normal_count=sum(v=="NORMAL_450" for v in initial)
    check("real runtime exposes both 450 normal and 1500 RUSH initial branches",rush_count>0 and normal_count>0,{"normal450":normal_count,"rush1500":rush_count})
    check("real runtime initial allocation is directionally consistent with 40:60 while unit stats enforce exact spec",abs(rush_count/len(initial)-.60)<.22,{"rushRate":rush_count/len(initial),"n":len(initial)})

    # Reconnect after a committed initial payout cannot reroll/duplicate.
    fixture("owner","left_v")
    before=len(event_rows("owner",1,"INITIAL_PAYOUT"));send_packet("owner","PACHINKO_PRESENTATION")
    wait(lambda:len(event_rows("owner",1,"INITIAL_PAYOUT"))>before,"committed initial payout",30)
    initial_runtime=session_runtime("owner");initial_outcome=initial_runtime.get("initialOutcome");initial_cumulative=initial_runtime.get("cumulativePayout")
    restart_client_at("owner","pachinko10-owner",0)
    restored=session_runtime("owner")
    check("disconnect/reconnect preserves committed initial payout without duplication",
          restored.get("initialOutcome")==initial_outcome and restored.get("cumulativePayout")==initial_cumulative,
          {"before":initial_runtime,"after":restored})

    # Normal reconnect.
    fixture("owner","normal");normal_before=machine_runtime(1);restart_client_at("owner","pachinko10-owner",0);normal_after=session_runtime("owner")
    check("disconnect/reconnect covers normal pachinko state",normal_after.get("mode")=="NORMAL" and normal_after.get("ballSequenceId")==normal_before.get("ballSequenceId"),{"before":normal_before,"after":normal_after})

    # Left reconnect with deterministic event.
    fixture("owner","left_out");left_before=machine_runtime(1);restart_client_at("owner","pachinko10-owner",0)
    wait(lambda:event_rows("owner",1,"LEFT") and event_rows("owner",1,"LEFT")[-1].get("ballSequenceId")==left_before.get("ballSequenceId"),"left reconnect event",120)
    check("disconnect/reconnect covers left-kurun committed OUT",event_rows("owner",1,"LEFT")[-1].get("outcome")=="OUT",event_rows("owner",1,"LEFT")[-1])

    # RUSH reconnect.
    fixture("owner","rush");rush_before=machine_runtime(1);restart_client_at("owner","pachinko10-owner",0);rush_after=session_runtime("owner")
    check("disconnect/reconnect covers RUSH state",rush_after.get("mode")=="RUSH" and rush_after.get("rushActive") is True and rush_after.get("ballSequenceId")==rush_before.get("ballSequenceId"),{"before":rush_before,"after":rush_after})

    # Real 81% continuation / 97:3 allocation decisions over production FIRE path.
    right=[]
    for i in range(120):
        fixture("owner","rush")
        before=len(event_rows("owner",1,"RIGHT"));send_packet("owner","PACHINKO_FIRE")
        wait(lambda:len(event_rows("owner",1,"RIGHT"))>before,"right decision "+str(i),30)
        right.append(event_rows("owner",1,"RIGHT")[-1].get("outcome"))
    wins=[v for v in right if v in ("WIN_1500","WIN_3000")]
    win3000=sum(v=="WIN_3000" for v in wins)
    check("real runtime exposes RUSH OUT and winning continuation branches",0<len(wins)<len(right),{"wins":len(wins),"total":len(right)})
    check("real runtime continuation is directionally consistent with 81% while unit stats enforce exact spec",abs(len(wins)/len(right)-.81)<.13,{"rate":len(wins)/len(right),"n":len(right)})
    check("real runtime reaches winning right allocation; forced production resolution covers both 1500 and 3000",any(v=="WIN_1500" for v in wins) or win3000>0,{"win1500":sum(v=="WIN_1500" for v in wins),"win3000":win3000})

    # Forced committed right states are resolved by the production presentation path.
    for mode,payout,active in (("right_1500",1500,True),("right_3000",3000,True),("right_out",0,False)):
        fixture("owner",mode);before_seq=session_runtime("owner").get("ballSequenceId");send_packet("owner","PACHINKO_PRESENTATION")
        wait(lambda:session_runtime("owner").get("presentation")=="IDLE" and session_runtime("owner").get("ballSequenceId")==before_seq,"resolve "+mode,30)
        rr=session_runtime("owner")
        check("production resolution "+mode,rr.get("currentPayout")==payout and rr.get("rushActive")==active,rr)

    # Right-kurun reconnect resumes exactly the committed sequence/outcome.
    fixture("owner","right_3000");right_before=machine_runtime(1);restart_client_at("owner","pachinko10-owner",0)
    wait(lambda:event_rows("owner",1,"RIGHT") and event_rows("owner",1,"RIGHT")[-1].get("ballSequenceId")==right_before.get("ballSequenceId"),"right reconnect event",120)
    check("disconnect/reconnect covers right-kurun committed 3000",event_rows("owner",1,"RIGHT")[-1].get("outcome")=="WIN_3000",event_rows("owner",1,"RIGHT")[-1])

    # Persist a right presentation across an actual Paper shutdown/restart.
    persisted_before=machine_runtime(1)
    stop_client("peer");stop_client("owner");stop_server()
    start_server()
    wait(lambda:machine_count()==3,"machines restored after restart",120)
    persisted_after=machine_runtime(1)
    check("server restart preserves committed pachinko runtime",persisted_after.get("ballSequenceId")==persisted_before.get("ballSequenceId")
          and persisted_after.get("presentation")==persisted_before.get("presentation") and persisted_after.get("rightOutcome")==persisted_before.get("rightOutcome"),
          {"before":persisted_before,"after":persisted_after})

    start_client("owner","pachinko10-owner");start_client("peer","pachinko10-peer")
    click("owner",0)
    wait(lambda:event_rows("owner",1,"RIGHT") and event_rows("owner",1,"RIGHT")[-1].get("ballSequenceId")==persisted_before.get("ballSequenceId"),"restart resume right event",120)
    check("real client reconstructs persisted right-kurun after server restart",event_rows("owner",1,"RIGHT")[-1].get("outcome")=="WIN_3000",event_rows("owner",1,"RIGHT")[-1])

    # Two pachinko machines hold distinct live states simultaneously.
    click("peer",2)
    fixture("owner","left_v");fixture("peer","right_1500")
    p1=machine_runtime(1);p2=machine_runtime(2)
    check("two pachinko machines run independently",p1.get("presentation")=="LEFT_KURUN" and p1.get("initialHitCommitted") is True
          and p2.get("presentation")=="RIGHT_KURUN" and p2.get("rightOutcome")=="WIN_1500"
          and p1.get("ballSequenceId")!=p2.get("ballSequenceId"),
          {"machine1":p1,"machine2":p2})

    # Keep pachinko machine 1 active while peer plays the ordinary slot machine.
    p1_before=json.dumps(machine_runtime(1),sort_keys=True)
    action("peer","close");wait(lambda:client("peer").get("productionSession") is None,"peer leaves pachinko 2",120)
    click("peer",3)
    command("peer","piritest fund","TEST_FUNDED")
    action("peer","close");wait(lambda:client("peer").get("productionSession") is None,"peer refresh after fund",120)
    click("peer",3)
    wait(lambda:(session_for("peer") or {}).get("credit")==50,"slot funded",120)
    action("peer","tap",key=32);wait(lambda:(session_for("peer") or {}).get("game_state")=="NORMAL_BETTED","slot bet",30)
    action("peer","tap",key=32);wait(lambda:(session_for("peer") or {}).get("game_state")=="NORMAL_SPINNING","slot lever",30)
    wait(lambda:client("peer").get("stopEnabled") is True,"slot stop enabled",10)
    for key,mask in ((263,1),(264,3),(262,7)):
        action("peer","tap",key=key);wait(lambda:(session_for("peer") or {}).get("stopped_mask")==mask,"slot stop "+str(mask),30)
    p1_after=json.dumps(machine_runtime(1),sort_keys=True)
    check("slot gameplay runs simultaneously without pachinko shared-state contamination",p1_after==p1_before,{"pachinko1":json.loads(p1_after),"slotState":session_for("peer")})

    stop_client("peer");stop_client("owner");stop_server()
    manifest["passed"]=True
except Exception as error:
    manifest["failure"]=str(error);print("PACHINKO_PHASE10_RUNTIME_FAILURE "+str(error),flush=True)
finally:
    for name,(proc,_,_) in list(clients.items()):
        if proc.poll() is None:
            try:stop_client(name)
            except Exception:
                if os.name=="nt":subprocess.run(["taskkill","/PID",str(proc.pid),"/T","/F"],capture_output=True)
                else:proc.kill()
    if server and server.poll() is None:
        try:stop_server()
        except Exception:
            if os.name=="nt":subprocess.run(["taskkill","/PID",str(server.pid),"/T","/F"],capture_output=True)
            else:server.kill()
    manifest["finishedAt"]=datetime.datetime.now(datetime.timezone.utc).isoformat()
    save(E/"result.json",manifest);save(OUT/"result.json",manifest)
    report=["# Pachinko Phase10 Runtime Acceptance","",f"- Run: {RUN}",f"- PASS: {manifest['passed']}","",
            "| Assertion | Result |","|---|---|"]+[f"| {a['name']} | {'PASS' if a['passed'] else 'FAIL'} |" for a in manifest["assertions"]]
    if "failure" in manifest:report+=["",f"Failure: `{manifest['failure']}`"]
    (E/"REPORT.md").write_text("\n".join(report)+"\n",encoding="utf-8")
    for h in handles:
        try:h.close()
        except Exception:pass
if not manifest["passed"]:raise SystemExit(1)
