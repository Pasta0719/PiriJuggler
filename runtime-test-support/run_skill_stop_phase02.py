"""SKILL STOP Phase 02 real Paper+Fabric control acceptance."""
from pathlib import Path
import datetime, hashlib, json, os, shutil, sqlite3, subprocess, time

ROOT=Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/"runtime-evidence/SKILL_STOP_PHASE_02"
RUN=datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
OUT=EVIDENCE/"attempts"/RUN
SERVER=EVIDENCE/"work"/("server-"+RUN)
JAVA=shutil.which("java")
PAPER=ROOT/"runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar"
FLAGS=subprocess.CREATE_NO_WINDOW if os.name=="nt" else 0
GRADLE_CMD=["cmd.exe","/d","/c",str(ROOT/"gradlew.bat")] if os.name=="nt" else [str(ROOT/"gradlew")]
OUT.mkdir(parents=True,exist_ok=True)
artifacts={side:ROOT/side/f"build/libs/piri-juggler-{side}-1.0.0.jar" for side in ("common","paper","fabric")}
helpers={side:ROOT/f"runtime-test-support/{module}/build/libs/piri-runtime-test-{side}-1.0.0.jar" for side,module in (("paper","paper"),("client","client"))}

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def save(p,v):
    p.parent.mkdir(parents=True,exist_ok=True); t=p.with_suffix(".tmp")
    t.write_text(json.dumps(v,ensure_ascii=False,indent=2),encoding="utf-8")
    for i in range(10):
        try: t.replace(p); return
        except PermissionError:
            if i==9: raise
            time.sleep(.05)
cache={}
def progress(stage, **details):
    payload={"stage":stage,"at":datetime.datetime.now(datetime.timezone.utc).isoformat(),**details}
    print("SKILL02_PROGRESS "+json.dumps(payload,ensure_ascii=False),flush=True)
    try: save(EVIDENCE/"live-progress.json",payload)
    except Exception: pass

def progress_signature():
    sig=snapshot()
    for p in (OUT/"server.log", OUT/"client.log"):
        try:
            sig[str(p.name)+"_size"]=p.stat().st_size
        except Exception:
            sig[str(p.name)+"_size"]=-1
    return json.dumps(sig,sort_keys=True,default=str)

def snapshot():
    try:
        s=session() if 'session' in globals() and server_result else None
        return {
            "game_state": None if not s else s.get("game_state"),
            "role": None if not s else s.get("internal_role"),
            "stopped_mask": None if not s else s.get("stopped_mask"),
            "sequence": None if not s else s.get("last_client_sequence"),
            "client_completed": None if not client_result else cli().get("completed"),
            "client_connected": None if not client_result else cli().get("connected")
        }
    except Exception:
        return {}

def load(p):
    try: cache[p]=json.loads(p.read_text(encoding="utf-8"))
    except (FileNotFoundError,json.JSONDecodeError,PermissionError): pass
    return cache.get(p,{})
def log(p): return p.read_text(encoding="utf-8",errors="replace") if p.exists() else ""

manifest={"startedAt":datetime.datetime.now(datetime.timezone.utc).isoformat(),"run":RUN,"passed":False,
          "buildHashes":{k:sha(v) for k,v in artifacts.items()},"helperHashes":{k:sha(v) for k,v in helpers.items()},
          "assertions":[],"commands":[]}
server=None; client_proc=None; client_result=None; server_result=None; handles=[]; seq=0

def wait(pred,label,timeout=120):
    started=time.monotonic(); end=started+timeout
    last_sig=progress_signature(); last_progress=started
    progress("WAIT_START",label=label,timeout=timeout,**snapshot())
    while time.monotonic()<end:
        if pred():
            progress("WAIT_OK",label=label,elapsed=round(time.monotonic()-started,1),**snapshot())
            return
        if server and server.poll() is not None: raise RuntimeError("Paper exited while "+label)
        if client_proc and client_proc.poll() is not None: raise RuntimeError("Fabric exited while "+label)
        if client_result:
            failure=load(client_result).get("failure")
            if failure: raise RuntimeError(failure)
        now=time.monotonic()
        sig=progress_signature()
        if sig!=last_sig:
            last_sig=sig; last_progress=now
        elif now-last_progress>=20:
            progress("STALL_OBSERVED",label=label,stalled_for=round(now-last_progress,1),**snapshot())
            last_progress=now
        time.sleep(.2)
    progress("WAIT_TIMEOUT",label=label,elapsed=round(time.monotonic()-started,1),**snapshot())
    raise TimeoutError(label)
def check(name,ok,evidence=None):
    progress("ASSERT",name=name,passed=bool(ok),**snapshot())
    manifest["assertions"].append({"name":name,"passed":bool(ok),"evidence":evidence})
    print(("PASS " if ok else "FAIL ")+name,flush=True)
    save(EVIDENCE/"result.json",manifest)
    if not ok: raise AssertionError(name)

def state(): return load(server_result)
def cli(): return load(client_result)
def sessions(): return state().get("sessions",[])
def session(): return next((s for s in sessions() if s.get("lifecycle")=="ACTIVE"),None)
def packets(kind): return [p["payload"] for p in cli().get("packets",[]) if p["type"]==kind]
def msgcount(text): return sum(text in m for m in cli().get("messages",[]))
def action(kind,**kw):
    global seq
    seq+=1; payload={"id":seq,"kind":kind,**kw}
    progress("ACTION_SEND",id=seq,kind=kind,args=kw,**snapshot())
    save(client_result.with_name(f"command-{seq}.json"),payload)
    wait(lambda:cli().get("completed",0)>=seq,"client "+kind)
    progress("ACTION_DONE",id=seq,kind=kind,**snapshot())
    manifest["commands"].append(payload)
def command(text,expected):
    progress("COMMAND",text=text,expected=expected,**snapshot())
    before=msgcount(expected);action("command",text=text);wait(lambda:msgcount(expected)>before,text+" -> "+expected)
def click(x):
    before=len(packets("OPEN_MACHINE"));action("aim",x=x);action("click",x=x);wait(lambda:len(packets("OPEN_MACHINE"))>before,"open machine")
def tap(key):
    # Runtime acceptance must use distinct physical key edges. A same-tick
    # press+release can be coalesced by Minecraft's keyboard/screen path.
    action("key",key=key,action=1)
    action("key",key=key,action=0)
def dbrows(sql):
    with sqlite3.connect(f"file:{SERVER/'plugins/PiriJuggler/piri.db'}?mode=ro",uri=True) as db:
        db.row_factory=sqlite3.Row;return [dict(r) for r in db.execute(sql)]
def settled():
    s=session();p=cli().get("publicState",{}) or {}
    return bool(s) and p.get("expectedNextClientSequence")==s["last_client_sequence"]+1
def wait_state(name): wait(lambda:settled() and session()["game_state"]==name,"state "+name)

try:
    progress("START",run=RUN)
    plugins=SERVER/"plugins";plugins.mkdir(parents=True,exist_ok=True)
    shutil.copy2(artifacts["paper"],plugins);shutil.copy2(helpers["paper"],plugins)
    (SERVER/"eula.txt").write_text("eula=true\n",encoding="utf-8")
    (SERVER/"server.properties").write_text("\n".join([
        "server-ip=127.0.0.1","server-port=25600","online-mode=false","enforce-secure-profile=false",
        "max-players=2","view-distance=2","simulation-distance=2","spawn-protection=0",
        "generate-structures=false","level-type=minecraft:normal","motd=Piri SKILL STOP Phase02 runtime"] )+"\n",encoding="utf-8")
    server_result=OUT/"server-result.json"
    sh=(OUT/"server.log").open("w",encoding="utf-8");handles.append(sh)
    server=subprocess.Popen([JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8","-Dpiri.runtime.phase=skill02",
        f"-Dpiri.runtime.serverResult={server_result}","-jar",str(PAPER),"nogui"],cwd=SERVER,
        stdin=subprocess.PIPE,stdout=sh,stderr=subprocess.STDOUT,text=True,creationflags=FLAGS)
    wait(lambda:"Done (" in log(OUT/"server.log") and state().get("ready"),"Paper ready",180)
    progress("PAPER_READY",**snapshot())

    cdir=EVIDENCE/"work"/"client-skill02-main";cdir.mkdir(parents=True,exist_ok=True)
    (cdir/"options.txt").write_text(
        "version:3953\nlang:en_us\nrenderDistance:2\nsimulationDistance:5\nmaxFps:30\n"
        "pauseOnLostFocus:false\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\n"
        "onboardAccessibility:false\n",
        encoding="utf-8")
    client_result=OUT/"client-result.json"
    ch=(OUT/"client.log").open("w",encoding="utf-8");handles.append(ch)
    client_proc=subprocess.Popen(GRADLE_CMD+["-PruntimeAcceptance=true",
        "-PruntimeScenario=skill02-main",f"-PruntimeRun={RUN}","-PruntimeEvidencePhase=SKILL_STOP_PHASE_02",
        ":runtime-test-client:runClient","--console=plain"],cwd=ROOT,stdout=ch,stderr=subprocess.STDOUT,creationflags=FLAGS)
    wait(lambda:cli().get("connected") and cli().get("handshake"),"Fabric join",90)
    progress("FABRIC_JOINED",**snapshot())

    action("aim",x=0)
    command("piri machine create SKILL_STOP","MACHINE_CREATED 1")
    click(0);wait_state("SEATED_READY")
    check("SKILL_STOP opens the existing slot screen",cli().get("machineType")=="SKILL_STOP" and cli().get("screen")=="SlotScreen",cli())
    command("piritest fund","TEST_FUNDED")
    action("close");wait(lambda:not session() or session().get("lifecycle")!="ACTIVE","fund refresh close")
    click(0);wait(lambda:settled() and session()["credit"]==50,"fund refresh reopen")

    def forced_spin(role):
        action("close");wait(lambda:not session() or session().get("lifecycle")!="ACTIVE","force role close")
        command("piri skillrole 1 "+role+" NONE","SKILL_ROLE_READY id=1")
        click(0);wait_state("SEATED_READY")
        tap(32);wait_state("NORMAL_BETTED")
        before=len(packets("SPIN_START"));tap(32)
        wait(lambda:len(packets("SPIN_START"))>before and settled() and session()["game_state"]=="NORMAL_SPINNING" and cli().get("stopEnabled"),"forced "+role+" lever")
        check("server drew "+role,session()["internal_role"]==role,session())

    def stop_top(reel,top,mask):
        index=(22-top)%21
        action("tap_at_phase",reel=reel,phase=index,key=[263,264,262][reel])
        wait(lambda:settled() and session()["stopped_mask"]==mask,"timed stop mask "+str(mask),40)
        packet=packets("REEL_STOP")[-1]
        check("screen press samples requested input",packet["pressedIndex"]==index,packet)

    def finish_top(reel,top,target_state,pay):
        stop_top(reel,top,7);wait_state(target_state)
        check("actual payout "+str(pay),session()["pay_display"]==pay,session())
        expected=[session()["display_"+r+"_stop"] for r in ("left","center","right")]
        wait(lambda:len(cli().get("displayPhases",[]))==3 and all(abs(a-b)<.05 for a,b in zip(cli()["displayPhases"],expected)),"visible reels agree with committed stops")
        check("public packets keep internal draw private",all("internalRole" not in p and "premiumType" not in p for p in packets("PUBLIC_STATE")))
        action("capture",label=target_state+"-"+str(pay))

    # A real MISS spin warms the client's first key/class initialization paths.
    # Subsequent precise presses use real Keyboard.onKey and the production protocol.
    forced_spin("MISS")
    for key,mask in [(264,2),(263,3)]:
        tap(key);wait(lambda:settled() and session()["stopped_mask"]==mask,"initial MISS stop")
    tap(262);wait_state("SEATED_READY")
    check("initial MISS has no payout",session()["pay_display"]==0,session())

    forced_spin("GRAPE")
    stop_top(1,20,2)
    check("middle seven upper input pulls grape upper in four symbols",session()["display_center_stop"]==20,session())
    stop_top(0,2,3);finish_top(2,4,"SEATED_READY",8)

    forced_spin("CHERRY")
    stop_top(1,20,2);stop_top(0,5,3)
    check("ordinary middle cherry slips to lower",session()["display_left_stop"]==16,session())
    finish_top(2,1,"SEATED_READY",4)

    forced_spin("PIERO")
    stop_top(1,20,2)
    check("middle seven upper input pulls piero upper in four symbols",session()["display_center_stop"]==19,session())
    stop_top(0,11,3);finish_top(2,3,"SEATED_READY",10)

    forced_spin("ONE_CD")
    stop_top(1,20,2);stop_top(0,10,3);finish_top(2,20,"BIG_READY",0)
    check("one-medal draw protects correct bonus bit entry",session()["bonus_type"]=="BIG" and session()["lamp_on"]==1,session())
    stop_packets=packets("REEL_STOP")
    check("real stop packets carry bounded slips",len(stop_packets)==15 and all(0<=p["slip"]<=4 for p in stop_packets),stop_packets)
    check("normal bet uses three medals",all(p.get("mode")=="NORMAL" for p in packets("SPIN_START")),packets("SPIN_START"))
    progress("ACCEPTANCE_COMPLETE",**snapshot())
    manifest["passed"]=True
except Exception as error:
    manifest["failure"]=str(error);print("SKILL_STOP_PHASE02_RUNTIME_FAILURE "+str(error),flush=True)
finally:
    if client_proc and client_proc.poll() is None:
        try: action("exit");client_proc.wait(timeout=60)
        except Exception:
            if os.name=="nt":
                subprocess.run(["taskkill","/PID",str(client_proc.pid),"/T","/F"],capture_output=True)
            else:
                client_proc.terminate()
                try: client_proc.wait(timeout=15)
                except Exception: client_proc.kill()
    if server and server.poll() is None:
        try: server.stdin.write("stop\n");server.stdin.flush();server.wait(timeout=60)
        except Exception: server.terminate()
    for h in handles:h.close()
    screenshots=EVIDENCE/"work"/"client-skill02-main"/"screenshots"
    if screenshots.exists():
        for screenshot in screenshots.glob("skill02-*.png"):
            shutil.copy2(screenshot,OUT/screenshot.name)
    manifest["finishedAt"]=datetime.datetime.now(datetime.timezone.utc).isoformat()
    save(OUT/"result.json",manifest);save(EVIDENCE/"result.json",manifest)

print("SKILL_STOP_PHASE02_RUNTIME="+("PASS" if manifest["passed"] else "FAIL"),flush=True)
raise SystemExit(0 if manifest["passed"] else 1)
