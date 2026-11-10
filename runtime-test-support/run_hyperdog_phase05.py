#!/usr/bin/env python3
"""HYPER_DOG Phase05 — real Paper 1.21/Fabric 1.21 full cabinet visual/physical input acceptance."""
from pathlib import Path
import datetime as dt,hashlib,json,os,shutil,subprocess,time,traceback
from hyperdog_acceptance_diagnostics import compact_states,record_failure

ROOT=Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/"runtime-evidence/HYPER_DOG_PHASE_05"
RUN=dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
OUT=EVIDENCE/"attempts"/RUN
SERVER=EVIDENCE/"work"/("server-"+RUN)
CLIENT=EVIDENCE/"work"/"client-hyperdog05-main"
PAPER=ROOT/"runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar"
JAVA=shutil.which("java")
GRADLE=["cmd.exe","/d","/c",str(ROOT/"gradlew.bat")] if os.name=="nt" else [str(ROOT/"gradlew")]
FLAGS=subprocess.CREATE_NO_WINDOW if os.name=="nt" else 0
OUT.mkdir(parents=True,exist_ok=True)
server_result=OUT/"server-result.json";client_result=OUT/"client-result.json"
server=None;client=None;handles=[]
manifest={"phase":"HYPER_DOG_PHASE_05","run":RUN,"startedAt":dt.datetime.now(dt.timezone.utc).isoformat(),
          "passed":False,"checks":{}}

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def check(label,cond):
    manifest["checks"][label]=bool(cond)
    print("HYPER_DOG_PHASE05_CHECK",label,"PASS" if cond else "FAIL",flush=True)
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
    last_rounds=-1
    last_advance=time.monotonic()
    while time.monotonic()<until:
        if predicate():
            print("HYPER_DOG_PHASE05_WAIT_OK",label,flush=True)
            return
        if server and server.poll() is not None:
            raise RuntimeError("Paper terminated during "+label)
        if check_client and client and client.poll() is not None:
            raise RuntimeError("Fabric terminated during "+label)
        state=compact_states(server_result,client_result)
        if label=="Phase05 full cabinet screenshot and reseat complete":
            current_rounds=state["Paper"].get("completedRounds",0)
            if current_rounds>last_rounds:
                last_rounds=current_rounds
                last_advance=time.monotonic()
            elif current_rounds>0 and time.monotonic()-last_advance>55:
                raise RuntimeError("No Paper AT/CZ game progress for >55s after round "+
                                   str(current_rounds)+"; likely lost BET/LEVER ACK or stuck stop")
        if state["Fabric"].get("error"):
            raise RuntimeError("Fabric probe error during "+label+": "+state["Fabric"]["error"])
        if time.monotonic()-last_report>=20:
            print("HYPER_DOG_PHASE05_PROGRESS",label,
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
        "server-ip=127.0.0.1","server-port=25610","online-mode=false",
        "enforce-secure-profile=false","max-players=2","view-distance=2",
        "simulation-distance=2","spawn-protection=0","generate-structures=false",
        "level-type=minecraft:normal","motd=Piri HYPER_DOG Phase05 acceptance"])+"\n")
    sl=(OUT/"server.log").open("w",encoding="utf-8");handles.append(sl)
    server=subprocess.Popen([JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8",
            "-Dpiri.runtime.phase=hyperdog05",f"-Dpiri.runtime.serverResult={server_result}",
            "-jar",str(PAPER),"nogui"],cwd=SERVER,
            stdin=subprocess.PIPE,stdout=sl,stderr=subprocess.STDOUT,text=True,creationflags=FLAGS)
    wait("real Paper server boot",lambda: "Done (" in (OUT/"server.log").read_text(errors="replace"),210)
    CLIENT.mkdir(parents=True,exist_ok=True)
    (CLIENT/"options.txt").write_text(
        "version:3953\nlang:en_us\nrenderDistance:2\nsimulationDistance:5\nmaxFps:30\n"
        "pauseOnLostFocus:false\nsoundCategory_master:1.0\nskipMultiplayerWarning:true\n"
        "onboardAccessibility:false\n",encoding="utf-8")
    cl=(OUT/"client.log").open("w",encoding="utf-8");handles.append(cl)
    client=subprocess.Popen(GRADLE+["-PruntimeAcceptance=true",
        "-PruntimeScenario=hyperdog05-main",f"-PruntimeRun={RUN}",
        "-PruntimeEvidencePhase=HYPER_DOG_PHASE_05",":runtime-test-client:runClient","--console=plain","--no-daemon"],
        cwd=ROOT,stdout=cl,stderr=subprocess.STDOUT,creationflags=FLAGS)
    wait("real Fabric connection",lambda:load(client_result).get("handshake") is True and
         load(server_result).get("ready") is True,150)
    wait("Phase05 full cabinet screenshot and reseat complete",lambda:
         load(server_result).get("complete") is True and
         load(server_result).get("reseats",0)==1 and
         load(client_result).get("reopened") is True and
         load(client_result).get("legacyComparison") is True and
         load(client_result).get("completedRounds",0)==load(server_result).get("completedRounds",0) and
         load(server_result).get("completedRounds",0)>=70,710)
    p=load(server_result);c=load(client_result)
    rows=p["rounds"];n=len(rows)
    check("real Paper/Fabric HYPER_DOG 70+ spins",n>=70 and c["completedRounds"]==n)
    # The former headless test set master volume to 0 and could never establish
    # that it even requested an audible signal. Observe real SoundManager.play
    # calls through the test-only mixin. This proves audio dispatch, NOT speakers.
    check("audio not disabled in Minecraft options",
          "soundCategory_master:1.0" in (CLIENT/"options.txt").read_text())
    audio=c.get("soundManagerPlayCalls",[])
    ids=[e["sound"] for e in audio if e.get("category")=="MASTER"]
    check("actual SoundManager dispatched BET/STOP fallback",
          ids.count("minecraft:block.note_block.hat")>=n)
    check("actual SoundManager dispatched LEVER fallback",
          ids.count("minecraft:block.lever.click")>=n)
    manifest["audioDispatch"]={
        "masterSoundSetting":1.0,
        "masterEventCount":len(ids),
        "lever":ids.count("minecraft:block.lever.click"),
        "betOrStop":ids.count("minecraft:block.note_block.hat"),
        "notice":ids.count("minecraft:block.note_block.chime"),
        "noticeStrong":ids.count("minecraft:entity.player.levelup"),
        "payout":ids.count("minecraft:entity.experience_orb.pickup"),
        "proofLevel":"SoundManager.play dispatched, not acoustic waveform",
    }
    check("physical LEVER exactly once per spin",p["acceptedLevers"]==n)
    check("all client BET/LEVER requests acknowledged",
          c["betRequests"]==p["acceptedBets"] and c["leverRequests"]==p["acceptedLevers"])
    check("physical stop count exactly 3 per spin",
          p["manualStops"]+p["automaticStops"]==3*n and c["stopRequests"]==p["manualStops"])
    check("genuine GUI keyboard and mouse stopping",
          c["mouseStops"]>20 and c["keyboardStops"]>20)
    check("zero rejected actions and client errors",p["rejected"]==0 and not c["error"])
    check("separate full cabinet routed through the production screen factory",
          set(c["screenTypes"])=={"HyperDogCabinetScreen"})
    check("LOWER UPPER CZ lamps and stages all rendered",
          {"LOWER","UPPER","CZ"}.issubset(c["lamps"]) and
          {"LOWER_AT","UPPER_AT","UPPER_CZ"}.issubset(c["stages"]))
    check("server-owned display FIFO TOTAL and ADD gated real next BET",
          {"TOTAL","ADD"}.issubset(c["displays"]) and c["displayLockFrames"]>50
          and p["displayEventCount"]>5)
    check("right lamps on for reward and off for zero",c["rightOnFrames"]>20 and c["rightOffFrames"]>20)
    check("7 supplied symbol textures loaded",set(c["textures"])==
          {"red7","rainbow7","bar","grape","replay","cherry","watermelon"})
    check("real framebuffer rendered 200+ frames",c["renderedFrames"]>200)
    check("Fabric/Paper spin IDs and stop triplets all identical",
          len({row["spinId"] for row in rows})==n and
          {row["spinId"] for row in c["rounds"]}=={row["spinId"] for row in rows} and
          all(next(x for x in c["rounds"] if x["spinId"]==row["spinId"])["stops"]==
              row["stops"] for row in rows))
    check("real CLOSE and re-seat preserved screen",
          c["closeSent"] and c["reopened"] and p["closeRequests"]==1
          and p["reseats"]==1 and c["reopenScreenCount"]==1)
    check("baseline old SlotScreen was rendered and photographed",c["legacyComparison"])
    check("real window resized to 4:3 and ultrawide",c["resizeStage"]>=3)
    check("all lower/upper/CZ win and loss scenes exercised",
          p["lowerRounds"]>0 and p["upperRounds"]>0 and
          p["czSuccesses"]>0 and p["czFailures"]>0)
    manifest["passed"]=True
except Exception as e:
    manifest["failure"]=str(e)
    manifest["traceback"]=traceback.format_exc(limit=10)
    record_failure(OUT,manifest,e,server_result,client_result,
                   OUT/"server.log",OUT/"client.log",server,client)
    print("HYPER_DOG_PHASE05_FAIL",e,flush=True)
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
        for screenshot in screenshot_dir.glob("hyperdog-phase05-*.png"):
            shutil.copy2(screenshot,OUT/screenshot.name)
    if manifest["passed"]:
        import struct
        names={"hyperdog-phase05-16x9.png":16/9,
               "hyperdog-phase05-4x3.png":4/3,
               "hyperdog-phase05-2x1.png":2.0,
               "hyperdog-phase05-existing-slot-screen-comparison.png":16/9,
               "hyperdog-phase05-after-reseat.png":16/9}
        check("five real Minecraft screenshots captured",
              all((OUT/name).is_file() for name in names))
        for name,aspect in names.items():
            buf=(OUT/name).read_bytes()
            check("PNG has real framebuffer signature "+name,buf[:8]==b"\x89PNG\r\n\x1a\n")
            width,height=struct.unpack(">II",buf[16:24])
            check("verified window aspect "+name,abs(width/height-aspect)<.04)
    manifest["finishedAt"]=dt.datetime.now(dt.timezone.utc).isoformat()
    save(OUT/"result.json",manifest);save(EVIDENCE/"result.json",manifest)
    report=[
        "# HYPER_DOG Phase05 — Paper/Fabric full cabinet, mouse/keyboard, resize, close/reseat",
        "",
        f"- Status: **{'PASS' if manifest['passed'] else 'FAIL'}**",
        f"- Run: \`{RUN}\`",
        "- Real Paper 1.21+Fabric 1.21 physical AT/CZ reels driven by the authoritative production Phase04 AT engine",
        "  with isolated test-only deterministic role injection (no public role forcing).",
        "- Never publish private mode, role or RNG to Fabric; no existing machine modified.",
        "- Existing SQLite CAS and restart reconciliation are covered by Phase04/05 dedicated JUnit tests.",
        "",
        "## Checks",
        *[f"- {'PASS' if v else 'FAIL'} {k}" for k,v in manifest["checks"].items()],
        "",
        "## Evidence",
        "- server-result.json / client-result.json / server.log / client.log / result.json",
        "- hyperdog-phase05-16x9.png / 4x3.png / 2x1.png / existing-slot-screen-comparison.png / after-reseat.png",
        ""
    ]
    (OUT/"REPORT.md").write_text("\n".join(report),encoding="utf-8")
    (EVIDENCE/"REPORT.md").write_text("\n".join(report),encoding="utf-8")

print("HYPER_DOG_PHASE05_RUNTIME="+("PASS" if manifest["passed"] else "FAIL"),flush=True)
raise SystemExit(0 if manifest["passed"] else 1)
