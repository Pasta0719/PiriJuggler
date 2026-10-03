"""SKILL STOP Phase 06 final real Paper+Fabric acceptance."""
from pathlib import Path
import datetime, hashlib, json, os, shutil, sqlite3, subprocess, time

ROOT=Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/"runtime-evidence/SKILL_STOP_PHASE_06"
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
    print("SKILL06_PROGRESS "+json.dumps(payload,ensure_ascii=False),flush=True)
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
server=None; spectator_proc=None; spectator_result=None; spectator_seq=0; client_proc=None; client_result=None; server_result=None; handles=[]; seq=0

def wait(pred,label,timeout=120):
    started=time.monotonic(); end=started+timeout
    last_sig=progress_signature(); last_progress=started
    progress("WAIT_START",label=label,timeout=timeout,**snapshot())
    while time.monotonic()<end:
        if pred():
            progress("WAIT_OK",label=label,elapsed=round(time.monotonic()-started,1),**snapshot())
            return
        if server and server.poll() is not None: raise RuntimeError("Paper exited while "+label)
        if spectator_proc and spectator_proc.poll() is not None: raise RuntimeError("Spectator Fabric exited while "+label)
        if spectator_result and load(spectator_result).get("failure"): raise RuntimeError(load(spectator_result)["failure"])
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
def fixture_audio(cdir):
    # Test-only alias of an existing user-provided OGG, never included in production jars.
    pack=cdir/"resourcepacks"/"skill06-sound-fixture"; sound=pack/"assets/piri/sounds/notice.ogg"
    sound.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(ROOT/"user-audio/juggler_god_god_stop_1.ogg",sound)
    (pack/"pack.mcmeta").write_text(json.dumps({"pack":{"pack_format":34,"description":"Runtime-only notice playback fixture"}}))
def spectator(): return load(spectator_result)
def spec_action(kind,**kw):
    global spectator_seq
    spectator_seq+=1;save(spectator_result.with_name(f"command-{spectator_seq}.json"),{"id":spectator_seq,"kind":kind,**kw})
    wait(lambda:spectator().get("completed",0)>=spectator_seq,"spectator "+kind)
def notices(client): return sum(e["sound"]=="piri:notice" for e in client.get("audioEvents",[]))
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
        "server-ip=127.0.0.1","server-port=25603","online-mode=false","enforce-secure-profile=false",
        "max-players=2","view-distance=2","simulation-distance=2","spawn-protection=0",
        "generate-structures=false","level-type=minecraft:normal","motd=Piri SKILL STOP Phase06 runtime"] )+"\n",encoding="utf-8")
    server_result=OUT/"server-result.json"
    sh=(OUT/"server.log").open("w",encoding="utf-8");handles.append(sh)
    server=subprocess.Popen([JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8","-Dpiri.runtime.phase=skill06",
        f"-Dpiri.runtime.serverResult={server_result}","-jar",str(PAPER),"nogui"],cwd=SERVER,
        stdin=subprocess.PIPE,stdout=sh,stderr=subprocess.STDOUT,text=True,creationflags=FLAGS)
    wait(lambda:"Done (" in log(OUT/"server.log") and state().get("ready"),"Paper ready",180)
    progress("PAPER_READY",**snapshot())

    cdir=EVIDENCE/"work"/"client-skill06-main";cdir.mkdir(parents=True,exist_ok=True)
    (cdir/"options.txt").write_text(
        "version:3953\nlang:en_us\nrenderDistance:2\nsimulationDistance:5\nmaxFps:30\n"
        "pauseOnLostFocus:false\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\n"
        "onboardAccessibility:false\nresourcePacks:[\"vanilla\",\"file/skill06-sound-fixture\"]\n",
        encoding="utf-8")
    fixture_audio(cdir)
    client_result=OUT/"client-result.json"
    ch=(OUT/"client.log").open("w",encoding="utf-8");handles.append(ch)
    client_proc=subprocess.Popen(GRADLE_CMD+["-PruntimeAcceptance=true",
        "-PruntimeScenario=skill06-main",f"-PruntimeRun={RUN}","-PruntimeEvidencePhase=SKILL_STOP_PHASE_06",
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

    def forced_spin(role,premium="NONE"):
        action("close");wait(lambda:not session() or session().get("lifecycle")!="ACTIVE","force role close")
        command("piri skillrole 1 "+role+" "+premium,"SKILL_ROLE_READY id=1")
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
        check("third stop clears challenge image",cli().get("skillChallengeTexture") is None)
        action("capture",label=target_state+"-"+str(pay))

    # A real MISS spin warms the client's first key/class initialization paths.
    # Subsequent precise presses use real Keyboard.onKey and the production protocol.
    forced_spin("MISS")
    for key,mask in [(264,2),(263,3)]:
        tap(key);wait(lambda:settled() and session()["stopped_mask"]==mask,"initial MISS stop")
    tap(262);wait_state("SEATED_READY")
    check("initial MISS has no payout",session()["pay_display"]==0,session())

    vectors=json.loads((ROOT/"build/skill-stop-phase06-vectors.json").read_text(encoding="utf-8"))
    manifest["runtimeVectors"]=vectors

    def stop_vector(vector,target_state,pay=None):
        masks=[1,3,7]
        for reel in range(3):
            stop_top(reel,vector["inputTop"][reel],masks[reel])
            check("runtime vector stop matches production solver reel "+str(reel),
                  session()["display_"+("left","center","right")[reel]+"_stop"]==vector["stopIndex"][reel],
                  {"vector":vector,"session":session()})
            if reel==0:
                hints=packets("REEL_STOP")[-1].get("nextStopHints",{})
                check("second-stop lookahead is published after actual first stop",set(hints.keys())=={"center","right"},hints)
            if reel==1:
                hints=packets("REEL_STOP")[-1].get("nextStopHints",{})
                check("last-stop lookahead is narrowed after actual second stop",set(hints.keys())=={"right"},hints)
        wait_state(target_state)
        if pay is not None: check("runtime vector payout "+str(pay),session()["pay_display"]==pay,session())

    def fresh_ready():
        action("close");wait(lambda:not session() or session().get("lifecycle")!="ACTIVE","fresh close")
        before=msgcount("RECOVER_CASHOUT")
        action("command",text="piri recover cashout")
        wait(lambda:msgcount("RECOVER_CASHOUT")>before,"recover cashout")
        wait(lambda:not sessions(),"recovery removed prior session")
        click(0);wait_state("SEATED_READY")
        command("piritest fund","TEST_FUNDED")
        action("close");wait(lambda:not session() or session().get("lifecycle")!="ACTIVE","fund refresh close")
        click(0);wait(lambda:settled() and session()["credit"]==50,"fund refresh reopen")

    # Phase06-only real input matrix. Each case is isolated by normal recovery cashout,
    # but remains in this single Paper/Fabric acceptance run and uses the same production JARs.
    for label in ("upperSeven","middleSeven","lowerSeven"):
        vector=vectors["sevenLines"][label]
        forced_spin("BIG")
        stop_vector(vector,"BIG_READY",vector["payout"])
        check(label+" actual line enters BIG",session().get("bonus_type")=="BIG",session())
        fresh_ready()

    # Normal BAR is forbidden even when the real inputs target the BAR/BAR/BAR top line.
    forced_spin("MISS")
    bar_inputs=[3,7,19]
    for reel,mask in [(0,1),(1,3),(2,7)]: stop_top(reel,bar_inputs[reel],mask)
    wait_state("SEATED_READY")
    attempted=[(22-n)%21 for n in bar_inputs]
    actual=[session()["display_left_stop"],session()["display_center_stop"],session()["display_right_stop"]]
    check("normal BAR/BAR/BAR target is rejected",actual!=attempted and session()["pay_display"]==0,{"attempted":attempted,"actual":actual})
    fresh_ready()

    # A-E dedicated first-stop laws through real keyboard edges.
    for premium in ("A","C","D","E"):
        forced_spin("CHERRY_BIG",premium)
        stop_top(1,20,2)
        check("premium "+premium+" cherry first-stop law",session()["display_center_stop"]==(22-21)%21 and packets("REEL_STOP")[-1]["slip"]==1,packets("REEL_STOP")[-1])
        fresh_ready()
        forced_spin("PIERO_BIG",premium)
        stop_top(1,20,2)
        check("premium "+premium+" piero first-stop law and four-symbol slip",session()["display_center_stop"]==(22-3)%21 and packets("REEL_STOP")[-1]["slip"]==4,packets("REEL_STOP")[-1])
        fresh_ready()
    forced_spin("CHERRY_BIG","B")
    stop_top(0,5,1)
    check("premium B middle-cherry first-stop law",session()["display_left_stop"]==(22-5)%21 and packets("REEL_STOP")[-1]["slip"]==0,packets("REEL_STOP")[-1])
    fresh_ready()

    # Premium F must fake the second-stop tenpai sound but refuse direct BIG entry.
    vector=vectors["sevenLines"]["upperSeven"]
    forced_spin("BIG","F")
    for reel,mask in [(0,1),(1,3),(2,7)]: stop_top(reel,vector["inputTop"][reel],mask)
    wait_state("BONUS_PENDING_BIG")
    check("premium F refuses direct BIG entry",session().get("bonus_type")=="BIG" and session()["game_state"]=="BONUS_PENDING_BIG",session())
    fresh_ready()

    # A REG draw must never turn the BIG-line input vector into BIG.
    forced_spin("REG")
    for reel,mask in [(0,1),(1,3),(2,7)]: stop_top(reel,vector["inputTop"][reel],mask)
    wait(lambda:settled() and session()["game_state"] in ("BONUS_PENDING_REG","REG_READY"),"wrong-bonus rejection")
    check("different bonus is rejected",session().get("bonus_type")=="REG" and session()["game_state"]!="BIG_READY",session())
    fresh_ready()

    # Six one-medal roles: actual recover, miss and BIG-priority inputs.
    for role,group in vectors["oneMedal"].items():
        for mode,target in (("recover","BONUS_PENDING_BIG"),("miss","BONUS_PENDING_BIG"),("big","BIG_READY")):
            forced_spin(role)
            v=group[mode]
            stop_vector(v,target,v["payout"])
            if mode=="recover": check(role+" one-medal recovered",session()["pay_display"]==1,session())
            elif mode=="miss": check(role+" one-medal miss pays zero",session()["pay_display"]==0,session())
            else: check(role+" BIG priority enters BIG",session().get("bonus_type")=="BIG" and session()["game_state"]=="BIG_READY",session())
            fresh_ready()

    # Carried BIG + replay: actual lower bonus-bit entry vector (CENTER, LEFT, RIGHT).
    forced_spin("BIG","F")
    for reel,mask in [(0,1),(1,3),(2,7)]: stop_top(reel,vector["inputTop"][reel],mask)
    wait_state("BONUS_PENDING_BIG")
    action("close");wait(lambda:not session() or session().get("lifecycle")!="ACTIVE","pending replay force close")
    command("piri skillrole 1 REPLAY NONE","SKILL_ROLE_READY id=1")
    click(0);wait_state("BONUS_PENDING_BIG")
    tap(32);wait_state("BONUS_ENTRY_BETTED_BIG");tap(32)
    wait(lambda:settled() and session()["game_state"]=="BONUS_ENTRY_SPINNING_BIG" and cli().get("stopEnabled"),"pending replay lower entry")
    stop_top(1,20,2);stop_top(0,1,3);stop_top(2,1,7);wait_state("BIG_READY")
    check("announced carried BIG replay permits lower bonus-bit entry",session().get("bonus_type")=="BIG" and session()["pay_display"]==0,session())
    fresh_ready()

    def bonus_state(): return json.loads(session()["machine_state_json"])
    def assets(): return session()["credit"]+session()["held_medals"]
    def bonus_lever(type):
        tap(32);wait_state(type+"_BETTED")
        tap(32);wait(lambda:settled() and session()["game_state"]==type+"_SPINNING" and cli().get("stopEnabled"),"bonus lever "+type)
        fixed=bonus_state()
        check("rendered remaining and fixed target agree",cli().get("skillRemaining")==fixed["skillRemaining"] and cli().get("skillChallenge")==fixed["skillChallenge"])
        check("public remaining and fixed challenge agree",cli()["publicState"]["skillRemaining"]==fixed["skillRemaining"] and cli()["publicState"]["skillChallenge"]==fixed["skillChallenge"])
        return fixed
    def failed_bonus(type,remaining):
        before=assets();fixed=bonus_lever(type)
        for reel,mask in [(0,1),(1,3),(2,7)]:stop_top(reel,1,mask)
        wait_state(type+"_READY" if remaining>1 else "SEATED_READY")
        check("bonus fail pays fourteen and decrements once",assets()==before+12 and session()["pay_display"]==14 and bonus_state()["skillRemaining"]==remaining-1)
        return fixed
    def enter(type):
        forced_spin(type)
        stop_top(1,20,2);stop_top(0,10,3);finish_top(2,20 if type=="BIG" else 19,type+"_READY",0)
        check("entry starts full games without consuming one",bonus_state()["skillRemaining"]==(20 if type=="BIG" else 8))

    enter("BIG")
    for remaining in range(20,1,-1):failed_bonus("BIG",remaining)
    action("close");wait(lambda:not session(),"final bonus force close")
    command("piri skillbonus 1 BAR","SKILL_BONUS_READY id=1");click(0);wait_state("BIG_READY")
    before=assets();fixed=bonus_lever("BIG")
    check("last-game target fixed at lever",fixed["skillRemaining"]==1 and fixed["skillChallenge"]=="BAR")
    stop_top(0,3,1);stop_top(1,7,3)
    saved=session().copy()
    action("close");wait(lambda:not session(),"partial challenge close")
    # Restart both real processes with the same world/SQLite, then reconnect.
    action("exit");client_proc.wait(timeout=60);client_proc=None
    server.stdin.write("stop\n");server.stdin.flush();server.wait(timeout=60)
    for instruction in OUT.glob("command-*.json"):instruction.unlink()
    server_result.unlink(missing_ok=True);client_result.unlink(missing_ok=True);cache.clear();seq=0
    server=subprocess.Popen([JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8","-Dpiri.runtime.phase=skill06",
        f"-Dpiri.runtime.serverResult={server_result}","-jar",str(PAPER),"nogui"],cwd=SERVER,
        stdin=subprocess.PIPE,stdout=sh,stderr=subprocess.STDOUT,text=True,creationflags=FLAGS)
    wait(lambda:state().get("ready"),"restarted Paper ready",180)
    recovered=next(s for s in sessions() if s["machine_id"]==1)
    check("real server restart preserves final-game target and partial input",recovered["spin_id"]==saved["spin_id"] and recovered["machine_state_json"]==saved["machine_state_json"] and recovered["game_state"]=="BIG_SPINNING" and recovered["lifecycle"]=="SUSPENDED_GRACE")
    client_proc=subprocess.Popen(GRADLE_CMD+["-PruntimeAcceptance=true",
        "-PruntimeScenario=skill06-main",f"-PruntimeRun={RUN}","-PruntimeEvidencePhase=SKILL_STOP_PHASE_06",
        ":runtime-test-client:runClient","--console=plain"],cwd=ROOT,stdout=ch,stderr=subprocess.STDOUT,creationflags=FLAGS)
    wait(lambda:cli().get("connected") and cli().get("handshake"),"Fabric rejoin",90)
    click(0);wait_state("BIG_SPINNING")
    check("partial final challenge survives reseat",session()["spin_id"]==saved["spin_id"] and session()["machine_state_json"]==saved["machine_state_json"] and session()["stopped_mask"]==3)
    finish_top(2,19,"BIG_READY",14)
    check("final-game success adds three before end",bonus_state()["skillRemaining"]==3 and assets()==before+12)
    # Force another target on an added game and deliberately align the wrong BAR.
    action("close");wait(lambda:not session(),"added force close")
    command("piri skillbonus 1 BELL","SKILL_BONUS_READY id=1");click(0);wait_state("BIG_READY")
    before=assets();bonus_lever("BIG")
    stop_top(0,3,1);stop_top(1,7,3);finish_top(2,19,"BIG_READY",14)
    check("added game draws; wrong target stays but fails",bonus_state()["skillRemaining"]==2 and assets()==before+12 and session()["display_left_stop"]==19 and session()["display_center_stop"]==15 and session()["display_right_stop"]==3)
    failed_bonus("BIG",2);failed_bonus("BIG",1)
    check("BIG ends by remaining games after twenty-three payouts",bonus_state()["skillRemaining"]==0 and session().get("bonus_type") is None)

    enter("REG")
    for remaining in range(8,0,-1):failed_bonus("REG",remaining)
    check("all-failure REG ends after eight payouts",bonus_state()["skillRemaining"]==0)

    forced_spin("BIG")
    stop_top(1,20,2);stop_top(0,1,3);finish_top(2,1,"BONUS_PENDING_BIG",0)
    for i in range(2):
        action("close");wait(lambda:not session(),"pending replay force close")
        command("piri skillrole 1 REPLAY NONE","SKILL_ROLE_READY id=1");click(0);wait_state("BONUS_PENDING_BIG")
        before=assets();tap(32);wait_state("BONUS_ENTRY_BETTED_BIG")
        check("only actual prior replay makes pending bet free",assets()==before-(1 if i==0 else 0))
        tap(32);wait(lambda:settled() and session()["game_state"]=="BONUS_ENTRY_SPINNING_BIG" and cli().get("stopEnabled"),"pending replay spin")
        stop_top(1,20,2);stop_top(0,2,3);finish_top(2,3,"BONUS_PENDING_BIG",0)
        check("replay retains BIG and next free flag",bonus_state()["skillPendingReplay"] and session().get("bonus_type")=="BIG")
    # Enter the retained BIG, then compare a second real Fabric client's world view.
    action("close");wait(lambda:not session(),"pending entry fixture close")
    command("piri skillrole 1 MISS NONE","SKILL_ROLE_READY id=1");click(0);wait_state("BONUS_PENDING_BIG")
    tap(32);wait_state("BONUS_ENTRY_BETTED_BIG");tap(32);wait_state("BONUS_ENTRY_SPINNING_BIG")
    wait(lambda:cli().get("stopEnabled"),"pending entry stops enabled")
    stop_top(1,20,2);stop_top(0,10,3);finish_top(2,20,"BIG_READY",0)
    spec_dir=EVIDENCE/"work/client-skill06-spectator";spec_dir.mkdir(parents=True,exist_ok=True)
    shutil.copy2(cdir/"options.txt",spec_dir/"options.txt");fixture_audio(spec_dir)
    spectator_result=EVIDENCE/"attempts"/(RUN+"-spectator")/"client-result.json";spectator_result.parent.mkdir(parents=True,exist_ok=True)
    sph=(OUT/"spectator.log").open("w",encoding="utf-8");handles.append(sph)
    spectator_proc=subprocess.Popen(GRADLE_CMD+["-PruntimeAcceptance=true","-PruntimeScenario=skill06-spectator",f"-PruntimeRun={RUN}-spectator","-PruntimeEvidencePhase=SKILL_STOP_PHASE_06",":runtime-test-client:runClient","--console=plain"],cwd=ROOT,stdout=sph,stderr=subprocess.STDOUT,creationflags=FLAGS)
    wait(lambda:spectator().get("connected") and spectator().get("handshake"),"second actual Fabric spectator",180)
    spec_action("aim",x=0)
    wait(lambda:spectator().get("remotePresentation",{}).get("1",{}).get("machineType")=="SKILL_STOP","spectator accepts SKILL_STOP")
    for target,tops,wrong in [("BAR",[3,7,19],False),("BELL",[9,19,1],False),("PIERO",[11,3,3],False),("BELL",[3,7,19],True)]:
        action("close");wait(lambda:not session(),"target fixture close")
        command("piri skillbonus 1 "+target,"SKILL_BONUS_READY id=1");click(0);wait_state("BIG_READY")
        own_sound=notices(cli());spec_sound=notices(spectator());fixed=bonus_lever("BIG")
        expected="symbols/"+target.lower()+".png"
        wait(lambda:cli().get("skillChallengeTexture")==expected and spectator().get("remotePresentation",{}).get("1",{}).get("skillChallengeTexture")==expected,"same target lights at lever on both clients")
        check("both clients show identical fixed remaining games",cli()["skillRemaining"]==spectator()["remotePresentation"]["1"]["skillRemaining"]==fixed["skillRemaining"])
        action("capture",label=target+("-wrong" if wrong else "")+"-target");spec_action("capture",label=target+("-wrong" if wrong else "")+"-world-target")
        stop_top(0,tops[0],1);stop_top(1,tops[1],3)
        if target=="BAR":
            action("close");wait(lambda:not session(),"challenge owner leave")
            wait(lambda:cli().get("remotePresentation",{}).get("1",{}).get("skillChallengeTexture")==expected,"owner sees fixed challenge from world after ESC")
            action("capture",label="BAR-owner-world-after-ESC")
            click(0);wait_state("BIG_SPINNING")
            check("resume preserves challenge image and two stops",cli()["skillChallengeTexture"]==expected and session()["stopped_mask"]==3)
        finish_top(2,tops[2],"BIG_READY",14)
        next_remaining=fixed["skillRemaining"]-1+(0 if wrong else 3)
        wait(lambda:spectator().get("remotePresentation",{}).get("1",{}).get("skillRemaining")==next_remaining,"world remaining updates after third stop")
        check("third stop clears both images",cli().get("skillChallengeTexture") is None and spectator()["remotePresentation"]["1"].get("skillChallengeTexture") is None)
        wait(lambda:notices(cli())==own_sound+(0 if wrong else 1) and notices(spectator())==spec_sound+(0 if wrong else 1),"one success sound each or silence for wrong target")
        check("actual SoundManager notice once on both clients",notices(cli())==own_sound+(0 if wrong else 1) and notices(spectator())==spec_sound+(0 if wrong else 1))
        if not wrong:
            check("spectator notice is positional",[e for e in spectator()["audioEvents"] if e["sound"]=="piri:notice"][-1]["relative"] is False)
            action("replay_success_notice");spec_action("replay_success_notice")
            check("replayed success packets remain silent",notices(cli())==own_sound+1 and notices(spectator())==spec_sound+1)
        action("capture",label=target+("-wrong" if wrong else "")+"-completed");spec_action("capture",label=target+("-wrong" if wrong else "")+"-world-completed")
    spec_action("exit");spectator_proc.wait(timeout=60);spectator_proc=None
    manifest["noticeAudioFixture"]="Runtime resource pack aliases existing user-audio/juggler_god_god_stop_1.ogg as notice.ogg only in the two test clients; production notice asset remains user-supplied"
    check("internal role is private on every public state",all("internalRole" not in p and "premiumType" not in p for p in packets("PUBLIC_STATE")))
    progress("ACCEPTANCE_COMPLETE",**snapshot())
    manifest["passed"]=True
except Exception as error:
    manifest["failure"]=str(error);print("SKILL_STOP_PHASE06_RUNTIME_FAILURE "+str(error),flush=True)
finally:
    if spectator_proc and spectator_proc.poll() is None:
        try: spec_action("exit");spectator_proc.wait(timeout=30)
        except Exception: spectator_proc.terminate()
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
    spec_screenshots=EVIDENCE/"work/client-skill06-spectator/screenshots"
    if spec_screenshots.exists():
        for image in spec_screenshots.glob("skill06-*.png"):
            destination=OUT/"screenshots"/("spectator-"+image.name);destination.parent.mkdir(exist_ok=True);shutil.copy2(image,destination)
    screenshots=EVIDENCE/"work"/"client-skill06-main"/"screenshots"
    if screenshots.exists():
        for screenshot in screenshots.glob("skill06-*.png"):
            shutil.copy2(screenshot,OUT/screenshot.name)
    manifest["finishedAt"]=datetime.datetime.now(datetime.timezone.utc).isoformat()
    save(OUT/"result.json",manifest);save(EVIDENCE/"result.json",manifest)

print("SKILL_STOP_PHASE06_RUNTIME="+("PASS" if manifest["passed"] else "FAIL"),flush=True)
raise SystemExit(0 if manifest["passed"] else 1)
