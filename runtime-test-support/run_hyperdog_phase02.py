#!/usr/bin/env python3
"""Real Paper 1.21 + Fabric 1.21 HYPER_DOG Phase02 network & approved-symbol acceptance."""
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import traceback

ROOT=Path(__file__).resolve().parents[1]
PHASE="HYPER_DOG_PHASE_02"
EVIDENCE=ROOT/"runtime-evidence"/PHASE
RUN=dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
OUT=EVIDENCE/"attempts"/RUN
SERVER=EVIDENCE/"work"/("server-"+RUN)
CLIENT_WORK=EVIDENCE/"work"/"client-hyperdog02-main"
PAPER=ROOT/"runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar"
JAVA=shutil.which("java")
GRADLE=["cmd.exe","/d","/c",str(ROOT/"gradlew.bat")] if os.name=="nt" else [str(ROOT/"gradlew")]
FLAGS=subprocess.CREATE_NO_WINDOW if os.name=="nt" else 0
OUT.mkdir(parents=True,exist_ok=True)

def artifact(name):
    result=ROOT/name
    if not result.is_file():raise RuntimeError("required production jar missing "+str(result))
    return result
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def load(path):
    try:return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError,json.JSONDecodeError,PermissionError):return {}
def save(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    temp=path.with_name(path.name+".tmp")
    temp.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding="utf-8")
    temp.replace(path)
def wait(label,predicate,seconds,server=None,client=None):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        if predicate():print("HYPER_DOG_PHASE02_WAIT_OK",label,flush=True);return
        if server and server.poll() is not None:raise RuntimeError(f"Paper died during {label}: {server.returncode}")
        if client and client.poll() is not None:raise RuntimeError(f"Fabric died during {label}: {client.returncode}")
        time.sleep(.25)
    raise TimeoutError(label)

server_process=None
client_process=None
handles=[]
server_file=OUT/"server-result.json"
client_file=OUT/"client-result.json"
manifest={"phase":PHASE,"run":RUN,"passed":False,"checks":{},"startedAt":dt.datetime.now(dt.timezone.utc).isoformat()}
def check(name,condition):
    manifest["checks"][name]=bool(condition)
    print("HYPER_DOG_PHASE02_CHECK",name,"PASS" if condition else "FAIL",flush=True)
    if not condition:raise AssertionError(name)
try:
    production=artifact("paper/build/libs/piri-juggler-paper-1.0.0.jar")
    fabric=artifact("fabric/build/libs/piri-juggler-fabric-1.0.0.jar")
    helper=artifact("runtime-test-support/paper/build/libs/piri-runtime-test-paper-1.0.0.jar")
    client_helper=artifact("runtime-test-support/client/build/libs/piri-runtime-test-client-1.0.0.jar")
    manifest["sha256"]={k:sha(v) for k,v in {"paper":production,"fabric":fabric,
                                                  "paper-helper":helper,"client-helper":client_helper}.items()}
    check("verified Paper 1.21 jar present",PAPER.exists())
    plugins=SERVER/"plugins";plugins.mkdir(parents=True,exist_ok=True)
    for jar in [production,helper]:shutil.copy2(jar,plugins)
    (SERVER/"eula.txt").write_text("eula=true\n",encoding="utf-8")
    (SERVER/"server.properties").write_text("\n".join([
       "server-ip=127.0.0.1","server-port=25606","online-mode=false",
       "enforce-secure-profile=false","max-players=2","view-distance=2",
       "simulation-distance=2","spawn-protection=0","generate-structures=false",
       "level-type=minecraft:normal","motd=Piri HYPER_DOG Phase02 acceptance"])+"\n",encoding="utf-8")
    server_log=(OUT/"server.log").open("w",encoding="utf-8");handles.append(server_log)
    server_process=subprocess.Popen([JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8",
              "-Dpiri.runtime.phase=hyperdog02",f"-Dpiri.runtime.serverResult={server_file}",
              "-jar",str(PAPER),"nogui"],cwd=SERVER,
              stdin=subprocess.PIPE,stdout=server_log,stderr=subprocess.STDOUT,
              text=True,creationflags=FLAGS)
    wait("Paper 1.21 live server",lambda:(OUT/"server.log").exists() and
            "Done (" in (OUT/"server.log").read_text(errors="replace"),210,server_process)
    CLIENT_WORK.mkdir(parents=True,exist_ok=True)
    (CLIENT_WORK/"options.txt").write_text(
        "version:3953\nlang:en_us\nrenderDistance:2\nsimulationDistance:5\nmaxFps:30\n"
        "pauseOnLostFocus:false\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\n"
        "onboardAccessibility:false\n",encoding="utf-8")
    client_log=(OUT/"client.log").open("w",encoding="utf-8");handles.append(client_log)
    client_process=subprocess.Popen(GRADLE+["-PruntimeAcceptance=true",
        "-PruntimeScenario=hyperdog02-main",f"-PruntimeRun={RUN}",
        "-PruntimeEvidencePhase="+PHASE,":runtime-test-client:runClient","--console=plain"],
        cwd=ROOT,stdout=client_log,stderr=subprocess.STDOUT,creationflags=FLAGS)
    wait("Fabric actual login/handshake",lambda:load(client_file).get("handshake") is True
         and load(server_file).get("ready") is True,150,server_process,client_process)
    print("HYPER_DOG_PHASE02_REAL_NETWORK_CONNECTED",flush=True)
    wait("57 complete Paper rounds",lambda:load(server_file).get("completedRounds",0)>=57,
         240,server_process,client_process)
    wait("57 complete Fabric displayed rounds",lambda:load(client_file).get("completedRounds",0)>=57,
         45,server_process,client_process)
    s=load(server_file);c=load(client_file)
    check("real Paper completed 57",s.get("completedRounds")==57)
    check("real Fabric completed 57",c.get("completedRounds")==57)
    check("three stops per Paper round",s.get("manualStops",0)+s.get("automaticStops",0)==171)
    check("real Paper accepted all valid stops",s.get("rejected")==0)
    check("real Paper snapshot and network replay",s.get("snapshotRestored") is True)
    check("all seven approved PNGs loaded by Fabric",set(c.get("availableTextures",[]))==
          {"red7","rainbow7","bar","grape","replay","cherry","watermelon"})
    check("preview screen rendered actual symbols",c.get("renderedFrames",0)>10)
    check("screenshot requested",c.get("screenshotRequested") is True)
    check("no client-side probe failures",not c.get("error"))
    check("57 unique Paper spin IDs",len({r["spinId"] for r in s.get("rounds",[])})==57)
    client_map={r["spinId"]:r["stops"] for r in c.get("rounds",[])}
    check("all Paper authoritative stops equal Fabric visible stops",
          len(client_map)==57 and all(client_map.get(r["spinId"])==r["stops"] for r in s["rounds"]))
    check("nine actual symbol paths per completed spin",all(len(r.get("visibleTextures",[]))==9
          and all(p.startswith("gui/original_at/symbols/") for p in r["visibleTextures"])
          for r in c["rounds"]))
    manifest["passed"]=True
except Exception as exc:
    manifest["failure"]=str(exc)
    manifest["traceback"]=traceback.format_exc(limit=10)
    print("HYPER_DOG_PHASE02_FAIL",str(exc),flush=True)
finally:
    if client_process and client_process.poll() is None:
        client_process.terminate()
        try:client_process.wait(timeout=30)
        except subprocess.TimeoutExpired:client_process.kill()
    if server_process and server_process.poll() is None:
        try:
            server_process.stdin.write("stop\n");server_process.stdin.flush()
            server_process.wait(timeout=60)
        except Exception:
            server_process.terminate()
    for handle in handles:handle.close()
    screenshot_dir=CLIENT_WORK/"screenshots"
    if screenshot_dir.exists():
        for shot in screenshot_dir.glob("hyperdog-phase02-*.png"):
            shutil.copy2(shot,OUT/shot.name)
    if manifest["passed"]:
        check("visual screenshot written to evidence",bool(list(OUT.glob("hyperdog-phase02-*.png"))))
    manifest["finishedAt"]=dt.datetime.now(dt.timezone.utc).isoformat()
    save(OUT/"result.json",manifest)
    save(EVIDENCE/"result.json",manifest)
    report="\n".join([
        "# HYPER_DOG Phase02 — real Paper 1.21/Fabric 1.21 acceptance",
        "",
        f"- Result: **{'PASS' if manifest['passed'] else 'FAIL'}**",
        f"- Run: \`{RUN}\`",
        "- Scope: real Minecraft plugin messaging and Fabric PNG preview; server role"
        " selection uses an **isolated test-only Paper fixture**, NOT a registered"
        " production HYPER_DOG machine nor a normal game economy.",
        "- Runtime evidence is produced by this script, not inferred from unit tests.",
        "",
        "## Checks",
        *[f"- {'PASS' if v else 'FAIL'} {k}" for k,v in manifest["checks"].items()],
        "",
        "## Evidence files",
        "- result.json / server-result.json / client-result.json",
        "- server.log / client.log",
        "- screenshot: hyperdog-phase02-57-rounds.png (if captured)",
        "",
        "Full playable Paper machine registration, AT economy and Phase05 cabinet are"
        " separate gates and cannot be asserted complete by this isolated reel test.",
        ""
    ])
    (OUT/"REPORT.md").write_text(report,encoding="utf-8")
    (EVIDENCE/"REPORT.md").write_text(report,encoding="utf-8")

print("HYPER_DOG_PHASE02_RUNTIME="+("PASS" if manifest["passed"] else "FAIL"),flush=True)
raise SystemExit(0 if manifest["passed"] else 1)
