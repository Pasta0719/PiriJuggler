"""Real Paper + Fabric + mobile HTTP end-to-end acceptance."""
from pathlib import Path
import datetime, json, os, re, shutil, sqlite3, subprocess, time, urllib.request, urllib.error

ROOT=Path(__file__).resolve().parents[1]
E=ROOT/"runtime-evidence/MOBILE_E2E"
RUN=datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
OUT=E/"attempts"/RUN/"mobile-e2e"
SERVER=E/"work"/("server-"+RUN)
OUT.mkdir(parents=True,exist_ok=True); SERVER.mkdir(parents=True,exist_ok=True)
JAVA=shutil.which("java")
PAPER=ROOT/"runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar"
VAULT=E/"work/downloads/Vault.jar"
PROD=ROOT/"paper/build/libs/piri-juggler-paper-1.0.0.jar"
HELPER=ROOT/"runtime-test-support/paper/build/libs/piri-runtime-test-paper-1.0.0.jar"
VAULT_HELPER=ROOT/"runtime-test-support/vault/build/libs/piri-runtime-test-vault-1.0.0.jar"
FLAGS=subprocess.CREATE_NO_WINDOW if os.name=="nt" else 0
GRADLE=["cmd.exe","/d","/c",str(ROOT/"gradlew.bat")] if os.name=="nt" else [str(ROOT/"gradlew")]
server=None; client_proc=None; seq=0; handles=[]
server_result=OUT/"server-result.json"; client_result=OUT/"client-result.json"
manifest={"run":RUN,"passed":False,"assertions":[]}

def save(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding="utf-8")
def load(path):
    try:return json.loads(path.read_text(encoding="utf-8"))
    except Exception:return {}
def log(path): return path.read_text(encoding="utf-8",errors="replace") if path.exists() else ""
def state(): return load(server_result)
def client(): return load(client_result)
def wait(pred,label,timeout=180):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        if pred(): return
        if server and server.poll() is not None: raise RuntimeError("Paper exited: "+label)
        if client_proc and client_proc.poll() is not None: raise RuntimeError("Fabric exited: "+label)
        failure=client().get("failure") if client_result.exists() else None
        if failure: raise RuntimeError(failure)
        time.sleep(.2)
    raise TimeoutError(label)
def check(name,ok,evidence=None):
    manifest["assertions"].append({"name":name,"passed":bool(ok),"evidence":evidence})
    print(("PASS " if ok else "FAIL ")+name,flush=True); save(E/"result.json",manifest)
    if not ok: raise AssertionError(name)
def messages(): return client().get("messages",[])
def message_count(text): return sum(text in m for m in messages())
def action(kind,**kw):
    global seq
    seq+=1
    save(client_result.with_name(f"command-{seq}.json"),{"id":seq,"kind":kind,**kw})
    wait(lambda:client().get("completed",0)>=seq,"client action "+kind)
def command(text,expected=None):
    before=message_count(expected) if expected else 0
    action("command",text=text)
    if expected: wait(lambda:message_count(expected)>before,text+" -> "+expected)
def db_rows(sql,args=()):
    path=SERVER/"plugins/PiriJuggler/piri.db"
    if not path.exists(): return []
    with sqlite3.connect(path) as db:
        db.row_factory=sqlite3.Row
        return [dict(r) for r in db.execute(sql,args).fetchall()]
def wallet():
    rows=db_rows("SELECT pending_medals FROM player_wallet")
    return int(rows[0]["pending_medals"]) if rows else 0
def prizes():
    try: rows=db_rows("SELECT prize_type,amount FROM mobile_prizes")
    except sqlite3.OperationalError: return {}
    return {r["prize_type"]:int(r["amount"]) for r in rows}
def http(method,path,token=None,expect=200):
    req=urllib.request.Request("http://127.0.0.1:10271"+path,method=method)
    if token:req.add_header("X-Piri-Token",token)
    try:
        with urllib.request.urlopen(req,timeout=15) as res:
            status=res.status; body=json.loads(res.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        status=e.code; body=json.loads(e.read().decode("utf-8"))
    if status!=expect: raise AssertionError(f"{method} {path}: expected {expect}, got {status} {body}")
    return body
def start_server():
    global server
    plugins=SERVER/"plugins";plugins.mkdir(parents=True,exist_ok=True)
    for src in (PROD,HELPER,VAULT,VAULT_HELPER): shutil.copy2(src,plugins)
    (SERVER/"eula.txt").write_text("eula=true\n",encoding="utf-8")
    (SERVER/"server.properties").write_text("\n".join([
        "server-ip=127.0.0.1","server-port=25605","online-mode=false","enforce-secure-profile=false",
        "max-players=2","view-distance=2","simulation-distance=2","spawn-protection=0",
        "generate-structures=false","motd=Piri Mobile E2E"])+"\n",encoding="utf-8")
    h=(OUT/"server.log").open("w",encoding="utf-8");handles.append(h)
    cmd=[JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8","-Dpiri.runtime.phase=mobile",
         f"-Dpiri.runtime.serverResult={server_result}","-jar",str(PAPER),"nogui"]
    server=subprocess.Popen(cmd,cwd=SERVER,stdin=subprocess.PIPE,stdout=h,stderr=subprocess.STDOUT,text=True,creationflags=FLAGS)
    wait(lambda:"Done (" in log(OUT/"server.log") and state().get("ready") and "PIRI_MOBILE_HTTP_READY port=10271" in log(OUT/"server.log"),"Paper/mobile ready",300)
    check("runtime Vault provider loaded","PIRI_TEST_VAULT_READY" in log(OUT/"server.log"),log(OUT/"server.log")[-3000:])
def start_client():
    global client_proc,seq
    seq=0
    for p in OUT.glob("command-*.json"): p.unlink()
    if client_result.exists(): client_result.unlink()
    run_dir=E/"work/client-mobile-e2e"
    if run_dir.exists(): shutil.rmtree(run_dir)
    run_dir.mkdir(parents=True,exist_ok=True)
    (run_dir/"options.txt").write_text("version:3953\nlang:en_us\nrenderDistance:2\nmaxFps:20\npauseOnLostFocus:false\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\nonboardAccessibility:false\n",encoding="utf-8")
    h=(OUT/"client.log").open("a",encoding="utf-8");handles.append(h)
    cmd=GRADLE+["-PruntimeAcceptance=true","-PruntimeScenario=mobile-e2e",f"-PruntimeRun={RUN}",
                "-PruntimeEvidencePhase=MOBILE_E2E",":runtime-test-client:runClient","--console=plain"]
    client_proc=subprocess.Popen(cmd,cwd=ROOT,stdout=h,stderr=subprocess.STDOUT,creationflags=FLAGS)
    wait(lambda:client().get("connected") and client().get("handshake"),"Fabric join",600)
def stop_client():
    global client_proc
    if client_proc and client_proc.poll() is None:
        action("exit");client_proc.wait(timeout=60)
        check("Fabric exits cleanly",client_proc.returncode==0,client_proc.returncode)
    client_proc=None
def stop_server():
    global server
    if server and server.poll() is None:
        server.stdin.write("stop\n");server.stdin.flush();server.wait(timeout=60)
        check("Paper exits cleanly",server.returncode==0,server.returncode)
    server=None

try:
    start_server(); start_client()
    action("aim",x=0)
    command("piri machine create JUGGLER","MACHINE_CREATED 1")
    wait(lambda:len(state().get("machines",[]))==1,"machine registration")
    command("testvault set PiriRuntimeTest 5000","TEST_VAULT_SET")
    command("piritest mobilefund","TEST_MOBILE_FUNDED")
    command("piritest mobilecheck","TEST_MOBILE_INVENTORY medals=600 small=2 medium=1 large=1")
    check("real Minecraft inventory seeded with medals and all prize sizes",
          any("TEST_MOBILE_INVENTORY medals=600 small=2 medium=1 large=1" in m for m in messages()),messages()[-10:])

    command("piri mobile pair","スマホ接続コード:")
    pair_message=next(m for m in reversed(messages()) if "スマホ接続コード:" in m)
    code=re.search(r"(\d{6})",pair_message).group(1)
    paired=http("POST","/api/pair?code="+code)
    token=paired["token"]
    check("real in-game pairing code authenticates mobile HTTP",bool(token),paired)

    stop_client()
    wait(lambda:wallet()==600 and prizes().get("small")==2 and prizes().get("medium")==1 and prizes().get("large")==1,
         "logout inventory stash")
    check("logout moves real medals and prizes into mobile balances",True,{"wallet":wallet(),"prizes":prizes()})

    mobile=http("GET","/api/state",token)
    check("offline mobile sees Vault money and loan availability",
          mobile.get("seated") is False and float(mobile.get("vaultBalance",-1))==5000.0 and mobile.get("loanAvailable") is True,mobile)
    ps=http("GET","/api/prizes",token)
    check("offline mobile sees stashed prize inventory",
          ps.get("walletMedals")==600 and ps.get("smallPrizes")==2 and ps.get("mediumPrizes")==1 and ps.get("largePrizes")==1,ps)

    seated=http("POST","/api/seat?id=1",token)
    check("offline mobile can seat at real production machine",seated.get("seated") is True and seated.get("machineId")==1,seated)
    blocked=http("GET","/api/prizes",token,expect=409)
    check("prize exchange/cash is rejected while seated",blocked.get("error")=="MUST_LEAVE_MACHINE",blocked)

    loan=http("POST","/api/loan",token)
    check("mobile loan withdraws real Vault balance before gameplay",
          loan.get("credit")==46 and float(loan.get("vaultBalance",-1))==4000.0,loan)
    inserted=http("POST","/api/insert",token)
    check("offline wallet inserts into machine CREDIT",
          inserted.get("credit")==50 and inserted.get("walletMedals")==596,inserted)

    bet=http("POST","/api/action?type=SPACE_ACTION",token)
    check("mobile BET uses production game state",bet.get("gameState")=="NORMAL_BETTED",bet)
    spin=http("POST","/api/action?type=SPACE_ACTION",token)
    check("mobile lever starts real production spin","SPINNING" in str(spin.get("gameState","")),spin)
    spin_id=str(spin.get("spinId",""))
    resumed=http("POST","/api/resume",token)
    resume_events=resumed.get("events",[])
    check("lost SPIN_START can self-heal through authoritative resume",
          resumed.get("gameState","").endswith("SPINNING")
          and str(resumed.get("spinId",""))==spin_id
          and any(e.get("type")=="SPIN_START"
                  and e.get("payload",{}).get("animation")=="RESUME_NORMAL"
                  and str(e.get("payload",{}).get("spinId",""))==spin_id
                  for e in resume_events),
          resumed)

    time.sleep(.7)
    for _ in range(30):
        try:
            first_stop=http("POST","/api/action?type=STOP_LEFT&pressed=0",token,expect=200)
            break
        except AssertionError as failure:
            if "STOP_TOO_EARLY" not in str(failure): raise
            time.sleep(.15)
    else:
        raise TimeoutError("mobile left stop never became available")
    time.sleep(.15)

    partial=http("POST","/api/resume",token)
    partial_events=partial.get("events",[])
    check("resume preserves an already-stopped reel and the same spin",
          int(partial.get("stoppedMask",0))&1==1
          and str(partial.get("spinId",""))==spin_id
          and any(e.get("type")=="SPIN_START"
                  and e.get("payload",{}).get("animation")=="RESUME_NORMAL"
                  and str(e.get("payload",{}).get("spinId",""))==spin_id
                  for e in partial_events),
          partial)

    for stop in ("STOP_CENTER","STOP_RIGHT"):
        for _ in range(30):
            try:
                result=http("POST","/api/action?type="+stop+"&pressed=0",token,expect=200)
                break
            except AssertionError as failure:
                if "STOP_TOO_EARLY" not in str(failure): raise
                time.sleep(.15)
        else:
            raise TimeoutError("mobile stop never became available: "+stop)
        time.sleep(.15)
    after=http("GET","/api/state",token)
    check("mobile third stop commits a non-spinning production state",
          "SPINNING" not in str(after.get("gameState",""))
          and int(after.get("stoppedMask",0))==7,
          after)

    cash=http("POST","/api/cashout",token)
    pending=int(cash.get("cashoutPending",0))
    check("offline machine cashout returns machine medals to mobile wallet",
          int(cash.get("cashoutDelivered",0))==0 and pending==int(cash.get("cashoutAmount",0)) and int(cash.get("walletMedals",0))==wallet(),
          {"cashout":cash,"dbWallet":wallet()})
    wallet_after_cash=wallet()

    still_blocked=http("GET","/api/prizes",token,expect=409)
    check("exchange remains blocked until explicit leave",still_blocked.get("error")=="MUST_LEAVE_MACHINE",still_blocked)
    left=http("POST","/api/leave",token)
    check("mobile leave releases the play seat",left.get("seated") is False,left)

    before_shop=http("GET","/api/prizes",token)
    cost=int(before_shop["smallCost"])
    bought=http("POST","/api/prize/buy?type=small&count=1",token)
    check("lobby converts medals into an actual mobile small-prize holding",
          bought.get("walletMedals")==wallet_after_cash-cost and bought.get("smallPrizes")==3,bought)
    cashed=http("POST","/api/prize/cash?type=small&count=1",token)
    check("prize cash is a separate second step and credits Vault",
          cashed.get("smallPrizes")==2 and float(cashed.get("vaultBalance",-1))==5000.0 and float(cashed.get("vaultAdded",-1))==1000.0,cashed)
    expected_medals=int(cashed["walletMedals"])
    check("medium/large prizes survive mobile small-prize cash",
          cashed.get("mediumPrizes")==1 and cashed.get("largePrizes")==1,cashed)

    start_client()
    expected=f"TEST_MOBILE_INVENTORY medals={expected_medals} small=2 medium=1 large=1"
    end=time.monotonic()+90
    while time.monotonic()<end and not any(expected in m for m in messages()):
        command("piritest mobilecheck")
        time.sleep(1)
    check("login restores remaining mobile medals and prizes as real Minecraft items",
          any(expected in m for m in messages()),{"expected":expected,"messages":messages()[-20:]})
    command("testvault get PiriRuntimeTest","TEST_VAULT_BALANCE")
    check("loan then prize cash leaves Vault balance conserved at 5000",
          any("TEST_VAULT_BALANCE PiriRuntimeTest 5000.0" in m for m in messages()),messages()[-10:])

    manifest["passed"]=True
except Exception as e:
    manifest["failure"]=str(e); print("MOBILE_E2E_FAILURE "+str(e),flush=True)
finally:
    try:
        if client_proc and client_proc.poll() is None: stop_client()
    except Exception as e: manifest.setdefault("cleanup",[]).append("client "+str(e))
    try:
        if server and server.poll() is None: stop_server()
    except Exception as e: manifest.setdefault("cleanup",[]).append("server "+str(e))
    for h in handles:
        try:h.close()
        except Exception:pass
    save(OUT/"result.json",manifest);save(E/"result.json",manifest)
print("MOBILE_E2E="+("PASS" if manifest["passed"] else "FAIL"),flush=True)
raise SystemExit(0 if manifest["passed"] else 1)
