#!/usr/bin/env python3
"""HYPER_DOG Phase04 — real Paper 1.21/Fabric 1.21 100-game normal lottery acceptance."""
from pathlib import Path
import datetime as dt,hashlib,json,os,shutil,subprocess,time,traceback
from hyperdog_acceptance_diagnostics import compact_states,record_failure

ROOT=Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/"runtime-evidence/HYPER_DOG_PHASE_04"
RUN=dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
OUT=EVIDENCE/"attempts"/RUN
SERVER=EVIDENCE/"work"/("server-"+RUN)
CLIENT=EVIDENCE/"work"/"client-hyperdog04-main"
PAPER=ROOT/"runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar"
JAVA=shutil.which("java")
GRADLE=["cmd.exe","/d","/c",str(ROOT/"gradlew.bat")] if os.name=="nt" else [str(ROOT/"gradlew")]
FLAGS=subprocess.CREATE_NO_WINDOW if os.name=="nt" else 0
OUT.mkdir(parents=True,exist_ok=True)
server_result=OUT/"server-result.json";client_result=OUT/"client-result.json"
server=None;client=None;handles=[]
manifest={"phase":"HYPER_DOG_PHASE_04","run":RUN,"startedAt":dt.datetime.now(dt.timezone.utc).isoformat(),
          "passed":False,"checks":{}}

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def check(label,cond):
    manifest["checks"][label]=bool(cond)
    print("HYPER_DOG_PHASE04_CHECK",label,"PASS" if cond else "FAIL",flush=True)
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
    last_report=0
    while time.monotonic()<until:
        if predicate():
            print("HYPER_DOG_PHASE04_WAIT_OK",label,flush=True)
            return
        if server and server.poll() is not None:
            raise RuntimeError("Paper terminated during "+label)
        if check_client and client and client.poll() is not None:
            raise RuntimeError("Fabric terminated during "+label)
        state=compact_states(server_result,client_result)
        if state["Fabric"].get("error"):
            raise RuntimeError("Fabric probe error during "+label+": "+state["Fabric"]["error"])
        if time.monotonic()-last_report>=20:
            print("HYPER_DOG_PHASE04_PROGRESS",label,
                  json.dumps(state,ensure_ascii=False),flush=True)
            last_report=time.monotonic()
        time.sleep(.25)

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
        "server-ip=127.0.0.1","server-port=25608","online-mode=false",
        "enforce-secure-profile=false","max-players=2","view-distance=2",
        "simulation-distance=2","spawn-protection=0","generate-structures=false",
        "level-type=minecraft:normal","motd=Piri HYPER_DOG Phase04 acceptance"])+"\n")
    sl=(OUT/"server.log").open("w",encoding="utf-8");handles.append(sl)
    server=subprocess.Popen([JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8",
            "-Dpiri.runtime.phase=hyperdog04",f"-Dpiri.runtime.serverResult={server_result}",
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
        "-PruntimeScenario=hyperdog04-main",f"-PruntimeRun={RUN}",
        "-PruntimeEvidencePhase=HYPER_DOG_PHASE_04",":runtime-test-client:runClient","--console=plain"],
        cwd=ROOT,stdout=cl,stderr=subprocess.STDOUT,creationflags=FLAGS)
    wait("real Fabric connection",lambda:load(client_result).get("handshake") is True and
         load(server_result).get("ready") is True,150)
    wait("AT/CZ real network acceptance completed",lambda:
         load(server_result).get("complete") is True and
         load(client_result).get("acceptanceComplete") is True and
         load(server_result).get("completedRounds",0)>=70 and
         load(client_result).get("completedRounds",0)==load(server_result).get("completedRounds",0),650)
    s=load(server_result);c=load(client_result)
    rounds=s["rounds"];n=len(rounds)
    check("Paper and Fabric completed the same 70+ AT/CZ spins",
          n>=70 and s.get("completedRounds")==n and c.get("completedRounds")==n)
    check("real Paper received exactly one lever per spin",s.get("acceptedLevers")==n)
    check("real Paper exactly three physical stops per spin",
          s.get("manualStops",0)+s.get("automaticStops",0)==3*n)
    check("Fabric client stop requests equal manual physical stops",
          c.get("stopRequests")==s.get("manualStops"))
    check("real packets rejected zero",s.get("rejected")==0)
    check("Fabric no errors",not c.get("error"))
    check("Paper AT/CZ full scenario ended in confirmed upper AT",
          s.get("complete") and s.get("episode")==1 and s.get("stage")=="UPPER_AT")
    check("both lower and upper paid AT stages exercised",
          s.get("lowerRounds",0)>4 and s.get("upperRounds",0)>30)
    check("at least three earned lower CZ tickets",
          s.get("lowerTicketsIssued",0)>=3)
    check("upper-origin 50 percent CZ failure and lower guaranteed CZ success observed",
          s.get("czFailures",0)>=1 and s.get("czSuccesses",0)>=1)
    check("all three free stages pay and cost zero",
          all(x["bet"]==0 and x["payout"]==0 for x in rounds if
              x["stage"] in ("LOWER_INITIAL","UPPER_INITIAL","UPPER_CZ")))
    check("lower initial 150 and upper initial 100 per spin guarantees",
          all(x["award"]>=150 for x in rounds if x["stage"]=="LOWER_INITIAL") and
          all(100<=x["award"]<=2000 for x in rounds if x["stage"]=="UPPER_INITIAL"))
    check("paid GRAPE gives 30 and costs 3",
          all(x["payout"]==30 and x["bet"]==3 for x in rounds if
              x["stage"] in ("LOWER_AT","UPPER_AT") and x["role"]=="GRAPE"))
    check("AT paid GRAPE difference consumes 27 remaining",
          all((rounds[i]["remaining"]==rounds[i-1]["remaining"]-27)
              for i in range(1,n) if
              rounds[i]["stage"] in ("LOWER_AT","UPPER_AT")
              and rounds[i]["role"]=="GRAPE"
              and rounds[i-1]["episode"]==rounds[i]["episode"]
              and rounds[i-1]["stage"]==rounds[i]["stage"]
              and rounds[i]["remaining"]>0))
    check("one pending physical stop snapshot/recovery succeeded",
          s.get("snapshotRestorations",0)==1)
    check("all seven approved textures loaded",
          set(c.get("textures",[]))==
          {"red7","rainbow7","bar","grape","replay","cherry","watermelon"})
    check("real Fabric 3x3 rendered",c.get("renderedFrames",0)>100)
    check("exact server/Fabric spin IDs and stop triplets",
          len({x["spinId"] for x in rounds})==n and
          {x["spinId"] for x in c["rounds"]}=={x["spinId"] for x in rounds} and
          all(next(crow for crow in c["rounds"] if crow["spinId"]==row["spinId"])["stops"]==
              row["stops"] for row in rounds))
    check("only public state displayed to Fabric, no hidden RNG or CZ flag",
          not any(k in c for k in ("mode","rng","rngState","ticket","czSuccess","savedHitMode","internalRole")))
    check("actual CZ win and loss both shown with server-selected outcomes",
          any(x["stage"]=="UPPER_CZ" and x["czSuccess"] for x in rounds) and
          any(x["stage"]=="UPPER_CZ" and not x["czSuccess"] for x in rounds))
    manifest["passed"]=True
except Exception as e:
    manifest["failure"]=str(e)
    manifest["traceback"]=traceback.format_exc(limit=10)
    record_failure(OUT,manifest,e,server_result,client_result,
                   OUT/"server.log",OUT/"client.log",server,client)
    print("HYPER_DOG_PHASE04_FAIL",e,flush=True)
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
        for screenshot in screenshot_dir.glob("hyperdog-phase04-*.png"):
            shutil.copy2(screenshot,OUT/screenshot.name)
    if manifest["passed"]:
        check("real 100-game screenshot generated",bool(list(OUT.glob("hyperdog-phase04-*.png"))))
    manifest["finishedAt"]=dt.datetime.now(dt.timezone.utc).isoformat()
    save(OUT/"result.json",manifest);save(EVIDENCE/"result.json",manifest)
    report=[
        "# HYPER_DOG Phase04 — Paper/Fabric NORMAL and six-mode v5 runtime",
        "",
        f"- Status: **{'PASS' if manifest['passed'] else 'FAIL'}**",
        f"- Run: \`{RUN}\`",
        "- Real Paper 1.21+Fabric 1.21 physical AT/CZ reels driven by the authoritative production Phase04 AT engine",
        "  with isolated test-only deterministic role injection (no public role forcing).",
        "- Never publish private mode, role or RNG to Fabric; no existing machine modified.",
        "- Existing SQLite CAS and restart reconciliation are covered by Phase04 dedicated JUnit tests.",
        "",
        "## Checks",
        *[f"- {'PASS' if v else 'FAIL'} {k}" for k,v in manifest["checks"].items()],
        "",
        "## Evidence",
        "- server-result.json / client-result.json / server.log / client.log / result.json",
        "- hyperdog-phase04-normal-100-games.png (if present)",
        ""
    ]
    (OUT/"REPORT.md").write_text("\n".join(report),encoding="utf-8")
    (EVIDENCE/"REPORT.md").write_text("\n".join(report),encoding="utf-8")

print("HYPER_DOG_PHASE04_RUNTIME="+("PASS" if manifest["passed"] else "FAIL"),flush=True)
raise SystemExit(0 if manifest["passed"] else 1)
