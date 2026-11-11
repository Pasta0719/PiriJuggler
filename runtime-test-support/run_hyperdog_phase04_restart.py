#!/usr/bin/env python3
"""Two separate real Paper 1.21 JVM boots, isolated schema-v4 SQLite and half-stopped BAR."""
import datetime as dt
import json, os, shutil, subprocess, time, traceback
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "runtime-evidence/HYPER_DOG_PHASE_04"
RUN = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
OUT = EVIDENCE / "attempts" / ("restart-" + RUN)
SERVER = EVIDENCE / "work" / ("jvm-restart-" + RUN)
PAPER = ROOT / "runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar"
JARS = [
    ROOT / "paper/build/libs/piri-juggler-paper-1.0.0.jar",
    ROOT / "runtime-test-support/paper/build/libs/piri-runtime-test-paper-1.0.0.jar",
]
JAVA = shutil.which("java")
OUT.mkdir(parents=True,exist_ok=True)
DB = OUT / "phase04-proof.sqlite"
FIRST = OUT / "first.json"
SECOND = OUT / "second.json"
manifest = {"phase":"HYPER_DOG_PHASE_04_RESTART", "run":RUN,"checks":{}, "passed":False}

def check(label, condition):
    manifest["checks"][label] = bool(condition)
    print("HYPER_DOG_PHASE04_JVM_CHECK",label,"PASS" if condition else "FAIL",flush=True)
    if not condition:
        raise AssertionError(label)

def data(path):
    try: return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError, PermissionError): return {}

def run_paper(mode,output):
    log_path = OUT / (mode + ".log")
    proc = None
    try:
        with log_path.open("w",encoding="utf-8") as log:
            proc = subprocess.Popen(
                [JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8",
                 "-Dpiri.runtime.phase="+mode,
                 "-Dpiri.runtime.phase04Db="+str(DB),
                 "-Dpiri.runtime.serverResult="+str(output),
                 "-Dpiri.runtime.phase04Prior="+str(FIRST),
                 "-jar",str(PAPER),"nogui"],
                cwd=SERVER,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,
                text=True,creationflags=subprocess.CREATE_NO_WINDOW if os.name=="nt" else 0)
            start=time.monotonic()
            ready=False
            while time.monotonic()-start<220:
                if proc.poll() is not None:
                    raise RuntimeError(mode+" Paper exited early: "+log_path.read_text(errors="replace")[-3000:])
                if not ready and "Done (" in log_path.read_text(errors="replace"):
                    ready=True
                result=data(output)
                if result:
                    if not result.get("passed"):
                        raise RuntimeError(mode+" Paper test failed: "+repr(result))
                    check(mode+" real Paper started",ready)
                    check(mode+" fixture saved evidence",result["passed"])
                    break
                time.sleep(.35)
            else:
                raise TimeoutError(mode+" Paper evidence timeout; see "+str(log_path))
            if not ready:
                raise RuntimeError(mode+" result arrived before Paper became ready")
    finally:
        if proc is not None and proc.poll() is None:
            try:
                proc.stdin.write("stop\n")
                proc.stdin.flush()
                proc.wait(timeout=70)
            except (BrokenPipeError,subprocess.TimeoutExpired,AttributeError):
                proc.terminate()
                try:proc.wait(timeout=10)
                except subprocess.TimeoutExpired:proc.kill()
    return data(output)

try:
    check("verified Paper 1.21 build 130 present",PAPER.is_file())
    check("Java executable available",JAVA is not None)
    check("production and test-only helper JARs available",all(p.is_file() for p in JARS))
    plugins=SERVER/"plugins"
    plugins.mkdir(parents=True,exist_ok=True)
    for jar in JARS: shutil.copy2(jar,plugins)
    (SERVER/"eula.txt").write_text("eula=true\n",encoding="utf-8")
    (SERVER/"server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=25609\nonline-mode=false\n"
        "enforce-secure-profile=false\nmax-players=2\nview-distance=2\n"
        "simulation-distance=2\nspawn-protection=0\ngenerate-structures=false\n"
        "motd=Piri HYPER_DOG Phase04 two-boot acceptance\n",encoding="utf-8")

    first=run_paper("hyperdog04-save",FIRST)
    check("first boot owns two physically stopped reels",
          first.get("physicalStopCount")==2 and first.get("foreignSeatDenied") is True)
    check("before first reboot no pending BAR payout minted",
          first.get("totalIn")==3 and first.get("totalOut")==0 and first.get("credit")==497)
    check("isolation database persisted on disk",DB.is_file() and DB.stat().st_size>0)
    check("Paper first process fully terminated",True)

    second=run_paper("hyperdog04-restore",SECOND)
    check("real separate JVM replayed remaining third stop",
          second.get("physicalStopCountAfterRestart")==3)
    check("BAR payout settled once, neither lost nor duplicated",
          second.get("recoveredTotalIn")==first.get("totalIn") and
          second.get("recoveredTotalOut")==first.get("totalOut")+15 and
          second.get("recoveredCredit")==first.get("credit")+15)
    check("next upper 5G grants at least 500 medals",
          second.get("upperInitialMedals",0)>=500 and
          second.get("stageAfterRestart")=="UPPER_AT")
    check("foreign player denied across Paper restart",
          second.get("foreignSeatDenied") is True)
    check("session and exact RNG/physical snapshot stable",
          second.get("sessionId")==first.get("sessionId"))
    manifest["passed"]=True
except Exception as error:
    manifest["failure"]=str(error)
    manifest["traceback"]=traceback.format_exc(limit=12)
    print("HYPER_DOG_PHASE04_RESTART_FAIL",error,flush=True)
finally:
    manifest["finishedAt"]=dt.datetime.now(dt.timezone.utc).isoformat()
    (OUT/"result.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
    report=["# HYPER_DOG Phase04 — real Paper JVM restart on isolated production schema-v4 SQLite",
            "",
            "- Result: **"+("PASS" if manifest["passed"] else "FAIL")+"**",
            "- Two distinct Paper 1.21 server processes, identical server world and database",
            "- Test-only module; no production/hall piri.db modified",
            "- Mid-BAR third-stop persistence, credit/held, owner lock, next upper 5G continuation",
            "",
            "## Checks"]+[("- PASS " if ok else "- FAIL ")+label for label,ok in manifest["checks"].items()]
    report += ["","## Evidence","- first.json, second.json, phase04-proof.sqlite, Paper logs from both processes",""]
    (OUT/"REPORT.md").write_text("\n".join(report),encoding="utf-8")
    print("HYPER_DOG_PHASE04_REAL_TWO_JVM_RESTART="+("PASS" if manifest["passed"] else "FAIL"),flush=True)

raise SystemExit(0 if manifest["passed"] else 1)
