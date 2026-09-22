"""NEXT Phase 02 real Paper+Fabric acceptance for JUGGLER_GOD."""
from pathlib import Path
import datetime, hashlib, json, os, shutil, sqlite3, subprocess, time

ROOT=Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/"runtime-evidence/NEXT_PHASE_02"
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
    print("NEXT02_PROGRESS "+json.dumps(payload,ensure_ascii=False),flush=True)
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
            progress("STALL_FAIL",label=label,stalled_for=round(now-last_progress,1),**snapshot())
            raise RuntimeError("No observable runtime progress for 20s while "+label)
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
        "server-ip=127.0.0.1","server-port=25597","online-mode=false","enforce-secure-profile=false",
        "max-players=2","view-distance=2","simulation-distance=2","spawn-protection=0",
        "generate-structures=false","level-type=minecraft:normal","motd=Piri NEXT Phase02 runtime"] )+"\n",encoding="utf-8")
    server_result=OUT/"server-result.json"
    sh=(OUT/"server.log").open("w",encoding="utf-8");handles.append(sh)
    server=subprocess.Popen([JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8","-Dpiri.runtime.phase=next02",
        f"-Dpiri.runtime.serverResult={server_result}","-jar",str(PAPER),"nogui"],cwd=SERVER,
        stdin=subprocess.PIPE,stdout=sh,stderr=subprocess.STDOUT,text=True,creationflags=FLAGS)
    wait(lambda:"Done (" in log(OUT/"server.log") and state().get("ready"),"Paper ready",180)
    progress("PAPER_READY",**snapshot())

    cdir=EVIDENCE/"work"/"client-next02-main";cdir.mkdir(parents=True,exist_ok=True)
    (cdir/"options.txt").write_text(
        "version:3953\nlang:en_us\nrenderDistance:2\nsimulationDistance:5\nmaxFps:30\n"
        "pauseOnLostFocus:false\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\n"
        "onboardAccessibility:false\n",
        encoding="utf-8")
    client_result=OUT/"client-result.json"
    ch=(OUT/"client.log").open("w",encoding="utf-8");handles.append(ch)
    client_proc=subprocess.Popen(GRADLE_CMD+["-PruntimeAcceptance=true",
        "-PruntimeScenario=next02-main",f"-PruntimeRun={RUN}","-PruntimeEvidencePhase=NEXT_PHASE_02",
        ":runtime-test-client:runClient","--console=plain"],cwd=ROOT,stdout=ch,stderr=subprocess.STDOUT,creationflags=FLAGS)
    wait(lambda:cli().get("connected") and cli().get("handshake"),"Fabric join",90)
    progress("FABRIC_JOINED",**snapshot())

    action("aim",x=0)
    command("piri machine create JUGGLER_GOD","MACHINE_CREATED 1")
    click(0)
    wait_state("SEATED_READY")
    check("successor machine opens through Juggler client",cli().get("machineType")=="JUGGLER_GOD",
          {"screen":cli().get("screen"),"machineType":cli().get("machineType")})

    command("piritest fund","TEST_FUNDED")
    action("close");wait(lambda:not session() or session().get("lifecycle")!="ACTIVE","fund refresh close")
    click(0);wait(lambda:settled() and session()["credit"]==50,"fund refresh reopen")

    progress("GOD_ACCEPTANCE_BEGIN",**snapshot())
    command("piritest force god","TEST_FORCE_ARMED GOD")
    tap(32);wait_state("NORMAL_BETTED")
    before=len(packets("SPIN_START"));tap(32)
    wait(lambda:len(packets("SPIN_START"))>before and settled() and settled() and session()["game_state"]=="NORMAL_SPINNING" and cli().get("stopEnabled"),"forced GOD lever")
    spin=packets("SPIN_START")[-1]
    check("GOD lever uses dedicated freeze contract",spin.get("godFreeze") is True,spin)
    check("GOD draw remains server authoritative",session()["internal_role"]=="GOD" and (cli().get("publicState") or {}).get("gameState")=="NORMAL_SPINNING")

    for key,mask in [(263,1),(264,3)]:
        tap(key);wait(lambda:settled() and session()["stopped_mask"]==mask,"GOD stop")
    tap(262);wait_state("BIG_READY")
    s=session()
    check("GOD BAR awards 15 and starts BIG",s["pay_display"]==15 and s["bonus_type"]=="BIG",s)
    runtime=json.loads(s["machine_state_json"])
    check("GOD initializes five-BIG chain",runtime["jgMode"]=="GOD_CHAIN" and runtime["guaranteedRemaining"]==4 and runtime["godBigCount"]==1,runtime)
    hist=dbrows("SELECT event_type,games FROM juggler_god_history WHERE machine_id=1 ORDER BY id DESC")
    check("GOD parent history is separate",len(hist)==1 and hist[0]["event_type"]=="GOD",hist)

    # The GOD-trigger BIG is the first of five guaranteed BIGs. The runner below
    # completes it and then drives all four successor BIGs through the real production loop.
    stats=dbrows("SELECT total_games,current_games,big_count FROM machine_period_stats WHERE machine_id=1")[0]
    check("GOD trigger counts one normal game before guaranteed zero-G stock",stats["total_games"]==1 and stats["big_count"]==1,stats)

    def finish_bonus():
        # BIG is 20 payout rounds of 14 medals under production rules.
        for _ in range(20):
            wait(lambda:session()["game_state"]=="BIG_READY","BIG ready")
            tap(32);wait_state("BIG_BETTED")
            tap(32);wait(lambda:settled() and session()["game_state"]=="BIG_SPINNING" and cli().get("stopEnabled"),"BIG lever")
            for key,mask in [(263,1),(264,3)]:
                tap(key);wait(lambda:settled() and session()["stopped_mask"]==mask,"BIG stop")
            tap(262)
            wait(lambda:session()["game_state"] in ("BIG_READY","SEATED_READY"),"BIG settle")
        wait_state("SEATED_READY")

    # Finish the initial GOD BIG, then verify four guaranteed successor BIGs.
    progress("GOD_BIG_COMPLETE",index=1,**snapshot())
    finish_bonus()
    for guaranteed_index in range(2,6):
        progress("GOD_GUARANTEED_BIG_BEGIN",index=guaranteed_index,**snapshot())
        before_stats=dbrows("SELECT total_games,current_games,big_count FROM machine_period_stats WHERE machine_id=1")[0]
        tap(32);wait_state("NORMAL_BETTED")
        tap(32);wait(lambda:settled() and session()["game_state"]=="NORMAL_SPINNING" and cli().get("stopEnabled"),f"guaranteed BIG {guaranteed_index} draw")
        for key,mask in [(263,1),(264,3)]:
            tap(key);wait(lambda:settled() and session()["stopped_mask"]==mask,"guaranteed BIG trigger stop")
        tap(262);wait(lambda:session()["game_state"]!="NORMAL_SPINNING","guaranteed BIG trigger settle")
        # Either direct entry or normal pending+entry must end at BIG_READY without game count advancing.
        if session()["game_state"]=="BONUS_PENDING_BIG":
            tap(32);wait_state("BONUS_ENTRY_BETTED_BIG")
            tap(32);wait(lambda:settled() and session()["game_state"]=="BONUS_ENTRY_SPINNING_BIG" and cli().get("stopEnabled"),"bonus entry lever")
            for key,mask in [(263,1),(264,3)]:
                tap(key);wait(lambda:settled() and session()["stopped_mask"]==mask,"bonus entry stop")
            tap(262)
        wait_state("BIG_READY")
        mid_stats=dbrows("SELECT total_games,current_games,big_count FROM machine_period_stats WHERE machine_id=1")[0]
        check(f"guaranteed BIG {guaranteed_index} is zero-game",
              mid_stats["total_games"]==before_stats["total_games"] and mid_stats["current_games"]==0,
              {"before":before_stats,"after":mid_stats})
        hist=dbrows("SELECT bonus_type,games FROM bonus_history WHERE machine_id=1 ORDER BY id DESC LIMIT 1")
        check(f"guaranteed BIG {guaranteed_index} history is 0G",
              bool(hist) and hist[0]["bonus_type"]=="BIG" and hist[0]["games"]==0,hist)
        finish_bonus()
        progress("GOD_GUARANTEED_BIG_COMPLETE",index=guaranteed_index,**snapshot())

    runtime=json.loads(session()["machine_state_json"])
    check("five guaranteed GOD BIGs completed",runtime["godBigCount"]==5,
          {"runtime":runtime,"stats":dbrows("SELECT total_games,current_games,big_count FROM machine_period_stats WHERE machine_id=1")[0]})
    # After the fifth BIG, state is either one-game continuation or guaranteed heaven.
    check("post-guarantee state is continuation or heaven",
          runtime["jgMode"] in ("GOD_CHAIN","HEAVEN") and (
              runtime["jgMode"]=="HEAVEN" or runtime["countNextChainGame"] is True),
          runtime)

    progress("HEAVEN_ACCEPTANCE_BEGIN",target=2,**snapshot())
    # Deterministic heaven acceptance: target=2 must suppress a natural bonus on game 1,
    # then force a setting-weighted BIG/REG family on game 2.
    wait_state("SEATED_READY")
    command("piritest heaven 2","TEST_HEAVEN_SET target=2")
    action("close");wait(lambda:not session() or session().get("lifecycle")!="ACTIVE","heaven refresh close")
    click(0);wait_state("SEATED_READY")
    h0=json.loads(session()["machine_state_json"])
    check("test heaven target persisted",h0["jgMode"]=="HEAVEN" and h0["heavenTarget"]==2 and h0["heavenProgress"]==0,h0)

    tap(32);wait_state("NORMAL_BETTED")
    tap(32);wait(lambda:settled() and session()["game_state"]=="NORMAL_SPINNING" and cli().get("stopEnabled"),"heaven game 1 lever")
    first_role=session()["internal_role"]
    check("heaven game 1 cannot pre-empt target with bonus",
          first_role not in ("BIG","REG","CHERRY_BIG","CHERRY_REG","PIERO_BIG","PIERO_REG","GOD"),
          {"role":first_role,"runtime":json.loads(session()["machine_state_json"])})
    for key,mask in [(263,1),(264,3)]:
        tap(key);wait(lambda:settled() and session()["stopped_mask"]==mask,"heaven game 1 stop")
    tap(262);wait(lambda:session()["game_state"] in ("SEATED_READY","REPLAY_READY"),"heaven game 1 settle")
    h1=json.loads(session()["machine_state_json"])
    check("heaven game 1 advances progress only",h1["jgMode"]=="HEAVEN" and h1["heavenProgress"]==1,h1)

    if session()["game_state"]=="REPLAY_READY":
        pass
    else:
        tap(32);wait_state("NORMAL_BETTED")
    tap(32);wait(lambda:settled() and session()["game_state"]=="NORMAL_SPINNING" and cli().get("stopEnabled"),"heaven target lever")
    target_role=session()["internal_role"]
    check("heaven target forces BIG or REG family",
          target_role in ("BIG","REG","CHERRY_BIG","CHERRY_REG","PIERO_BIG","PIERO_REG"),
          {"role":target_role,"runtime":json.loads(session()["machine_state_json"])})
    h2=json.loads(session()["machine_state_json"])
    check("heaven target progress reaches two",h2["heavenProgress"]==2 and h2["bonusOrigin"]=="HEAVEN",h2)

    progress("ACCEPTANCE_COMPLETE",**snapshot())
    manifest["passed"]=True
except Exception as error:
    manifest["failure"]=str(error);print("NEXT_PHASE02_RUNTIME_FAILURE "+str(error),flush=True)
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
    manifest["finishedAt"]=datetime.datetime.now(datetime.timezone.utc).isoformat()
    save(OUT/"result.json",manifest);save(EVIDENCE/"result.json",manifest)

print("NEXT_PHASE02_RUNTIME="+("PASS" if manifest["passed"] else "FAIL"),flush=True)
raise SystemExit(0 if manifest["passed"] else 1)
