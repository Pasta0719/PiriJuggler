#!/usr/bin/env python3
"""HYPER_DOG Phase03 — real Paper 1.21/Fabric 1.21 100-game normal lottery acceptance."""
from pathlib import Path
import datetime as dt,hashlib,json,os,shutil,subprocess,time,traceback

ROOT=Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/"runtime-evidence/HYPER_DOG_PHASE_03"
RUN=dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
OUT=EVIDENCE/"attempts"/RUN
SERVER=EVIDENCE/"work"/("server-"+RUN)
CLIENT=EVIDENCE/"work"/"client-hyperdog03-main"
PAPER=ROOT/"runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar"
JAVA=shutil.which("java")
GRADLE=["cmd.exe","/d","/c",str(ROOT/"gradlew.bat")] if os.name=="nt" else [str(ROOT/"gradlew")]
FLAGS=subprocess.CREATE_NO_WINDOW if os.name=="nt" else 0
OUT.mkdir(parents=True,exist_ok=True)
server_result=OUT/"server-result.json";client_result=OUT/"client-result.json"
server=None;client=None;handles=[]
manifest={"phase":"HYPER_DOG_PHASE_03","run":RUN,"startedAt":dt.datetime.now(dt.timezone.utc).isoformat(),
          "passed":False,"checks":{}}

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def check(label,cond):
    manifest["checks"][label]=bool(cond)
    print("HYPER_DOG_PHASE03_CHECK",label,"PASS" if cond else "FAIL",flush=True)
    if not cond:raise AssertionError(label)
def load(path):
    try:return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError,PermissionError,json.JSONDecodeError):return {}
def save(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    tmp=path.with_name(path.name+".tmp")
    tmp.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding="utf-8")
    tmp.replace(path)
def wait(label,predicate,seconds,check_client=True):
    until=time.monotonic()+seconds
    while time.monotonic()<until:
        if predicate():
            print("HYPER_DOG_PHASE03_WAIT_OK",label,flush=True);return
        if server and server.poll() is not None:raise RuntimeError("Paper terminated during "+label)
        if check_client and client and client.poll() is not None:raise RuntimeError("Fabric terminated during "+label)
        time.sleep(.25)
    raise TimeoutError(label)

try:
    jars={
        "paper":ROOT/"paper/build/libs/piri-juggler-paper-1.0.0.jar",
        "fabric":ROOT/"fabric/build/libs/piri-juggler-fabric-1.0.0.jar",
        "helperPaper":ROOT/"runtime-test-support/paper/build/libs/piri-runtime-test-paper-1.0.0.jar",
        "helperClient":ROOT/"runtime-test-support/client/build/libs/piri-runtime-test-client-1.0.0.jar"
    }
    for key,jar in jars.items():check("JAR present: "+key,jar.is_file())
    check("verified Paper 1.21 build 130 present",PAPER.is_file())
    manifest["sha256"]={k:sha(p) for k,p in jars.items()}
    plugins=SERVER/"plugins";plugins.mkdir(parents=True,exist_ok=True)
    for key in ["paper","helperPaper"]:shutil.copy2(jars[key],plugins)
    (SERVER/"eula.txt").write_text("eula=true\n")
    (SERVER/"server.properties").write_text("\n".join([
        "server-ip=127.0.0.1","server-port=25607","online-mode=false",
        "enforce-secure-profile=false","max-players=2","view-distance=2",
        "simulation-distance=2","spawn-protection=0","generate-structures=false",
        "level-type=minecraft:normal","motd=Piri HYPER_DOG Phase03 acceptance"])+"\n")
    sl=(OUT/"server.log").open("w",encoding="utf-8");handles.append(sl)
    server=subprocess.Popen([JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8",
            "-Dpiri.runtime.phase=hyperdog03",f"-Dpiri.runtime.serverResult={server_result}",
            "-jar",str(PAPER),"nogui"],cwd=SERVER,
            stdin=subprocess.PIPE,stdout=sl,stderr=subprocess.STDOUT,text=True,creationflags=FLAGS)
    wait("real Paper server boot",lambda: "Done (" in (OUT/"server.log").read_text(errors="replace"),210)
    CLIENT.mkdir(parents=True,exist_ok=True)
    (CLIENT/"options.txt").write_text(
        "version:3953\nlang:en_us\nrenderDistance:2\nsimulationDistance:5\nmaxFps:30\n"
        "pauseOnLostFocus:false\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\n"
        "onboardAccessibility:false\n",encoding="utf-8")
    cl=(OUT/"client.log").open("w",encoding="utf-8");handles.append(cl)
    client=subprocess.Popen(GRADLE+["-PruntimeAcceptance=true",
        "-PruntimeScenario=hyperdog03-main",f"-PruntimeRun={RUN}",
        "-PruntimeEvidencePhase=HYPER_DOG_PHASE_03",":runtime-test-client:runClient","--console=plain"],
        cwd=ROOT,stdout=cl,stderr=subprocess.STDOUT,creationflags=FLAGS)
    wait("real Fabric connection",lambda:load(client_result).get("handshake") is True and
         load(server_result).get("ready") is True,150)
    wait("100 real normal spin results delivered",lambda:load(server_result).get("completedRounds",0)==100
          and load(client_result).get("completedRounds",0)==100,340)
    s=load(server_result);c=load(client_result)
    check("Paper 100 normal games",s.get("completedRounds")==100)
    check("Fabric 100 visible games",c.get("completedRounds")==100)
    check("Paper genuine bet/lever sequence accepted",s.get("acceptedLevers")==100 and s.get("acceptedBets",0)>0)
    check("Paper physical stop count 300",s.get("acceptedStops")==300)
    check("valid C2S inputs not rejected",s.get("rejected")==0)
    check("reliable real bet and lever requests",c.get("leverRequests",0)>0 and c.get("betRequests",0)>0)
    check("client stop requests 300",c.get("stopRequests")==300)
    check("all seven approved textures present",set(c.get("textures",[]))==
          {"red7","rainbow7","bar","grape","replay","cherry","watermelon"})
    check("actual 3x3 drawn",c.get("renderedFrames",0)>25)
    check("client observed no errors",not c.get("error"))
    check("server model JSON snapshot/reel restored during live session",s.get("snapshotRestorations")==1)
    check("all serials strictly sequential",all(t["number"]==i+1 for i,t in enumerate(s["rounds"])))
    check("all expected 3BET vs replay-free",all(
        t["bet"]==(0 if i>0 and s["rounds"][i-1]["role"]=="REPLAY"
                    and s["rounds"][i-1]["hit"]=="NONE" else 3)
        for i,t in enumerate(s["rounds"])))
    check("server normal credit and total in/out ledger exact",
          s["totalBet"]==sum(x["bet"] for x in s["rounds"]) and
          s["totalPayout"]==sum(x["payout"] for x in s["rounds"]) and
          s["credit"]==10000-s["totalBet"]+s["totalPayout"])
    check("server mode chain preserved across every normal game and AT return",all(
        t["modeBefore"]==(s["rounds"][i-1].get("testResumeMode",s["rounds"][i-1]["modeAfter"]))
        for i,t in enumerate(s["rounds"]) if i>0))
    check("AT entry saved AFTER same-game mode transition",all(
        t["savedHitMode"]==t["modeAfter"] for t in s["rounds"] if t["hit"]!="NONE"))
    client_map={x["spinId"]:x for x in c["rounds"]}
    check("100 unique spin IDs and exact Paper/Fabric stop agreement",
          len(client_map)==100 and all(client_map.get(x["spinId"],{}).get("stops")==x["stops"]
                                   for x in s["rounds"]))
    check("all 9 visible textures per game resolved",all(
        len(x["visibleTextures"])==9 and all(path.startswith("gui/original_at/symbols/")
             for path in x["visibleTextures"]) for x in c["rounds"]))
    check("client JSON never reveals private mode, role or RNG",
          not any(k in c for k in ("mode","modeBefore","internalRole","role","rngState","savedHitMode")))
    manifest["passed"]=True
except Exception as e:
    manifest["failure"]=str(e)
    manifest["traceback"]=traceback.format_exc(limit=10)
    print("HYPER_DOG_PHASE03_FAIL",e,flush=True)
finally:
    if client and client.poll() is None:
        client.terminate()
        try:client.wait(timeout=30)
        except subprocess.TimeoutExpired:client.kill()
    if server and server.poll() is None:
        try:
            server.stdin.write("stop\n");server.stdin.flush()
            server.wait(timeout=60)
        except Exception:server.terminate()
    for handle in handles:handle.close()
    screenshot_dir=CLIENT/"screenshots"
    if screenshot_dir.exists():
        for screenshot in screenshot_dir.glob("hyperdog-phase03-*.png"):
            shutil.copy2(screenshot,OUT/screenshot.name)
    if manifest["passed"]:
        check("real 100-game screenshot generated",bool(list(OUT.glob("hyperdog-phase03-*.png"))))
    manifest["finishedAt"]=dt.datetime.now(dt.timezone.utc).isoformat()
    save(OUT/"result.json",manifest);save(EVIDENCE/"result.json",manifest)
    report=[
        "# HYPER_DOG Phase03 — Paper/Fabric NORMAL and six-mode v5 runtime",
        "",
        f"- Status: **{'PASS' if manifest['passed'] else 'FAIL'}**",
        f"- Run: \`{RUN}\`",
        "- Real Paper 1.21+Fabric 1.21 network with production normal engine",
        "  and isolated TEST-ONLY economy/session fixture.",
        "- Never publish private mode, role or RNG to Fabric; no existing machine modified.",
        "- AT gameplay and production SQLite session/economy integration remain Phase04.",
        "",
        "## Checks",
        *[f"- {'PASS' if v else 'FAIL'} {k}" for k,v in manifest["checks"].items()],
        "",
        "## Evidence",
        "- server-result.json / client-result.json / server.log / client.log / result.json",
        "- hyperdog-phase03-normal-100-games.png (if present)",
        ""
    ]
    (OUT/"REPORT.md").write_text("\n".join(report),encoding="utf-8")
    (EVIDENCE/"REPORT.md").write_text("\n".join(report),encoding="utf-8")

print("HYPER_DOG_PHASE03_RUNTIME="+("PASS" if manifest["passed"] else "FAIL"),flush=True)
raise SystemExit(0 if manifest["passed"] else 1)
