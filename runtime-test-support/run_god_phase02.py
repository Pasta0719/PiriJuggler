"""GOD Phase-02 real-client forced-role acceptance.
Runs production Paper/Fabric jars and verifies forced role payout/replay,
Paper stop indices, Fabric final rest positions, navigation, and representative forms.
"""
from pathlib import Path
import datetime, hashlib, json, os, shutil, subprocess, time

ROOT=Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/"runtime-evidence/GOD_PHASE_02"
RUN=datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
OUT=EVIDENCE/"attempts"/RUN
SERVER=EVIDENCE/"work"/("server-"+RUN)
JAVA=shutil.which("java")
PAPER=ROOT/"runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar"
FLAGS=subprocess.CREATE_NO_WINDOW if os.name=="nt" else 0
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
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        if pred(): return
        if server and server.poll() is not None: raise RuntimeError("Paper exited while "+label)
        if client_proc and client_proc.poll() is not None: raise RuntimeError("Fabric exited while "+label)
        if client_result:
            f=load(client_result).get("failure")
            if f: raise RuntimeError(f)
        time.sleep(.2)
    raise TimeoutError(label)
def check(name,ok,evidence=None):
    manifest["assertions"].append({"name":name,"passed":bool(ok),"evidence":evidence})
    print(("PASS " if ok else "FAIL ")+name,flush=True)
    if not ok: raise AssertionError(name)
def state(): return load(server_result)
def cli(): return load(client_result)
def sessions(): return state().get("sessions",[])
def session(): return sessions()[0] if sessions() else None
def packets(kind): return [p["payload"] for p in cli().get("packets",[]) if p["type"]==kind]
def msgcount(text): return sum(text in m for m in cli().get("messages",[]))
def action(kind,**kw):
    global seq
    seq+=1; payload={"id":seq,"kind":kind,**kw}; save(client_result.with_name(f"command-{seq}.json"),payload)
    wait(lambda:cli().get("completed",0)>=seq,"client "+kind);manifest["commands"].append(payload)
def command(text,expected):
    before=msgcount(expected);action("command",text=text);wait(lambda:msgcount(expected)>before,text+" -> "+expected)
def click(x,kind="OPEN_MACHINE"):
    before=len(packets(kind));action("aim",x=x);action("click",x=x);wait(lambda:len(packets(kind))>before,"click "+str(x))
def tap(key): action("tap",key=key)
def capture(label):
    action("capture",label=label)
    src=EVIDENCE/"work"/"client-god02-main"/"screenshots"/("god02-"+label+".png")
    wait(lambda:src.exists() and src.stat().st_size>1000,"screenshot "+label)
    (OUT/"screenshots").mkdir(exist_ok=True);shutil.copy2(src,OUT/"screenshots"/src.name)

LEFT=["GOD","BLUE7","RED7","MILLION","YELLOW7","GOD","BLUE7","RED7","MILLION","YELLOW7","GOD","BLUE7","DEKA_BOTTOM","DEKA_TOP","YELLOW7","GOD","BLUE7","RED7","MILLION","YELLOW7"]
CENTER=["GOD","YELLOW7","BLUE7","YELLOW7","RED7","GOD","YELLOW7","BLUE7","YELLOW7","RED7","GOD","YELLOW7","BLUE7","YELLOW7","RED7","GOD","YELLOW7","BLUE7","YELLOW7","RED7"]
RIGHT=["GOD","YELLOW7","DEKA_BOTTOM","DEKA_TOP","BLUE7","GOD","YELLOW7","RED7","MILLION","BLUE7","GOD","YELLOW7","RED7","MILLION","BLUE7","GOD","YELLOW7","RED7","MILLION","BLUE7"]
STRIPS=[LEFT,CENTER,RIGHT]
def sym(reel,middle,row):
    off={"TOP":1,"MIDDLE":0,"BOTTOM":-1}[row]
    return STRIPS[reel][(middle+off)%20]
def visible(stops): return [[sym(r,stops[r],row) for row in ("TOP","MIDDLE","BOTTOM")] for r in range(3)]
def form(role,stops):
    if role=="UPPER_BLUE7": return all(sym(r,stops[r],"TOP")=="BLUE7" for r in range(3))
    if role=="MIDDLE_BLUE7": return all(sym(r,stops[r],"MIDDLE")=="BLUE7" for r in range(3))
    if role=="ORDERED_YELLOW7_ONE": return sym(0,stops[0],"BOTTOM")=="BLUE7" and sym(1,stops[1],"MIDDLE")=="YELLOW7" and sym(2,stops[2],"TOP")=="BLUE7"
    if role in ("ORDERED_YELLOW7_AT","COMMON_YELLOW7"): return sym(0,stops[0],"BOTTOM")=="YELLOW7" and sym(1,stops[1],"MIDDLE")=="BLUE7" and sym(2,stops[2],"BOTTOM")=="YELLOW7"
    if role=="LOWER_YELLOW7": return sym(0,stops[0],"BOTTOM")=="YELLOW7" and sym(1,stops[1],"MIDDLE")=="RED7" and sym(2,stops[2],"BOTTOM")=="YELLOW7"
    if role=="RISING_YELLOW7": return sym(0,stops[0],"BOTTOM")=="YELLOW7" and sym(1,stops[1],"MIDDLE")=="YELLOW7" and sym(2,stops[2],"TOP")=="YELLOW7"
    if role=="MIDDLE_YELLOW7": return all(sym(r,stops[r],"MIDDLE")=="YELLOW7" for r in range(3))
    if role=="GAIA_BELL": return sym(0,stops[0],"TOP")=="YELLOW7" and sym(1,stops[1],"MIDDLE")=="YELLOW7" and sym(2,stops[2],"TOP")=="YELLOW7"
    if role=="RED7": return all(sym(r,stops[r],"MIDDLE")=="RED7" for r in range(3))
    if role=="GOD": return all(sym(r,stops[r],"MIDDLE")=="GOD" for r in range(3))
    if role=="SP": return [sym(r,stops[r],"MIDDLE") for r in range(3)]==["RED7","RED7","GOD"]
    if role in ("MISS","RED7_FAKE"): return True
    raise ValueError(role)

cases=[
 ("MISS",0,False,"MISS"),("UPPER_BLUE7",0,True,"UPPER_BLUE7"),("MIDDLE_BLUE7",0,True,"MIDDLE_BLUE7"),
 ("ORDERED_YELLOW7",1,False,"ORDERED_YELLOW7_ONE"),("LOWER_YELLOW7",3,False,"LOWER_YELLOW7"),
 ("RISING_YELLOW7",15,False,"RISING_YELLOW7"),("MIDDLE_YELLOW7",15,False,"MIDDLE_YELLOW7"),
 ("COMMON_YELLOW7",15,False,"COMMON_YELLOW7"),("GAIA_BELL",1,False,"GAIA_BELL"),
 ("RED7",15,False,"RED7"),("GOD",15,False,"GOD"),("SP",15,False,"SP"),
 ("ORDERED_YELLOW7_AT",15,False,"ORDERED_YELLOW7_AT"),("RED7_FAKE",0,True,"RED7_FAKE")
]
try:
    plugins=SERVER/"plugins";plugins.mkdir(parents=True,exist_ok=True)
    shutil.copy2(artifacts["paper"],plugins);shutil.copy2(helpers["paper"],plugins)
    (SERVER/"eula.txt").write_text("eula=true\n",encoding="utf-8")
    (SERVER/"server.properties").write_text("\n".join(["server-ip=127.0.0.1","server-port=25596","online-mode=false","enforce-secure-profile=false","max-players=2","view-distance=2","simulation-distance=2","spawn-protection=0","generate-structures=false","level-type=minecraft:normal","motd=Piri GOD Phase02 runtime"])+"\n",encoding="utf-8")
    server_result=OUT/"server-result.json"; sh= (OUT/"server.log").open("w",encoding="utf-8");handles.append(sh)
    server=subprocess.Popen([JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8","-Dpiri.runtime.phase=god02",f"-Dpiri.runtime.serverResult={server_result}","-jar",str(PAPER),"nogui"],cwd=SERVER,stdin=subprocess.PIPE,stdout=sh,stderr=subprocess.STDOUT,text=True,creationflags=FLAGS)
    wait(lambda:"Done (" in log(OUT/"server.log") and state().get("ready"),"Paper ready",300)
    cdir=EVIDENCE/"work"/"client-god02-main";cdir.mkdir(parents=True,exist_ok=True)
    client_result=OUT/"client-result.json"; ch=(OUT/"client.log").open("w",encoding="utf-8");handles.append(ch)
    client_proc=subprocess.Popen(["cmd.exe","/d","/c",str(ROOT/"gradlew.bat"),"-PruntimeAcceptance=true","-PruntimeScenario=god02-main",f"-PruntimeRun={RUN}","-PruntimeEvidencePhase=GOD_PHASE_02",":runtime-test-client:runClient","--console=plain"],cwd=ROOT,stdout=ch,stderr=subprocess.STDOUT,creationflags=FLAGS)
    wait(lambda:cli().get("connected") and cli().get("handshake"),"Fabric join",600)

    action("aim",x=0);command("piri machine create GOD","MACHINE_CREATED 1")

    for i,(role,payout,replay,display) in enumerate(cases):
        mid=1; x=0
        command("piri godtest 1 "+("gg" if role=="ORDERED_YELLOW7_AT" else "normal"),"GOD_TEST_READY")
        force="ORDERED_YELLOW7" if role=="ORDERED_YELLOW7_AT" else role
        command(f"piri godrole 1 {force}","GOD_ROLE_READY")
        click(x);wait(lambda:session() and session()["machine_id"]==mid,"seat "+role)
        wait(lambda:cli().get("screen")=="SlotScreen" and cli().get("machineType")=="GOD",role+" GOD client view",30)
        check(role+" opens as GOD",cli().get("machineType")=="GOD",{"screen":cli().get("screen"),"machineType":cli().get("machineType")})
        command("piribalance 50 0","TEST_BALANCE_SET")
        action("close");wait(lambda:not session() or session()["lifecycle"]!="ACTIVE","balance refresh close")
        click(x);wait(lambda:session() and session()["machine_id"]==mid and session()["credit"]==50,"balance refresh reopen")
        tap(32);wait(lambda:session()["game_state"] in ("NORMAL_BETTED","REPLAY_READY"),role+" bet")
        tap(32);wait(lambda:session()["game_state"]=="NORMAL_SPINNING" and cli().get("stopEnabled"),role+" lever")
        spin=packets("SPIN_START")[-1]
        if role=="GAIA_BELL":
            check("GAIA exposes RIGHT-first nav",spin.get("godNav")=="R",spin)
            order=[262,263,264]
        elif role=="ORDERED_YELLOW7_AT":
            nav=spin.get("godNav",""); check("AT ordered yellow exposes complete nav",len(nav.split("-"))==3,nav)
            order=[{"L":263,"C":264,"R":262}[v] for v in nav.split("-")]
        else:
            order=[263,264,262]
        for key in order:
            tap(key); time.sleep(.15)
        wait(lambda:session()["game_state"]!="NORMAL_SPINNING",role+" settle")
        s=session()
        check(role+" payout",s["pay_display"]==payout,{"pay":s["pay_display"],"expected":payout})
        check(role+" replay state",(s["game_state"]=="REPLAY_READY")==replay,s["game_state"])
        stops=[int(s["display_left_stop"]),int(s["display_center_stop"]),int(s["display_right_stop"])]
        wait(lambda:len(cli().get("displayPhases",[]))==3 and all(abs((((cli()["displayPhases"][r]-stops[r])+10)%20)-10)<.05 for r in range(3)),role+" Fabric rests on Paper indices",30)
        check(role+" representative/Piri form",form(display,stops),{"stops":stops,"visible":visible(stops)})
        check(role+" Paper/Fabric stop agreement",True,{"paper":stops,"fabric":cli().get("displayPhases")})
        capture(f"{i+1:02d}-{role.lower()}")
        if i==len(cases)-1:
            credit=s["credit"];tap(32);wait(lambda:session()["game_state"]=="NORMAL_SPINNING","replay launches free next game")
            check("replay next game consumes no new bet",session()["credit"]==credit,{"before":credit,"after":session()["credit"]})
            break
        command("pirigamereset","TEST_GAME_RESET")
        command("piribalance 0 0","TEST_BALANCE_SET")
        action("close");wait(lambda:not sessions(),"clean session end",60)

    manifest["passed"]=True
except Exception as e:
    manifest["failure"]=str(e);print("GOD_RUNTIME_FAILURE "+str(e),flush=True)
finally:
    if client_proc and client_proc.poll() is None:
        try: action("exit");client_proc.wait(timeout=60)
        except Exception: subprocess.run(["taskkill","/PID",str(client_proc.pid),"/T","/F"],capture_output=True)
    if server and server.poll() is None:
        try: server.stdin.write("stop\n");server.stdin.flush();server.wait(timeout=60)
        except Exception: server.terminate()
    for h in handles:h.close()
    manifest["finishedAt"]=datetime.datetime.now(datetime.timezone.utc).isoformat()
    save(OUT/"result.json",manifest);save(EVIDENCE/"result.json",manifest)
print("GOD_PHASE02_RUNTIME="+("PASS" if manifest["passed"] else "FAIL"),flush=True)
raise SystemExit(0 if manifest["passed"] else 1)
