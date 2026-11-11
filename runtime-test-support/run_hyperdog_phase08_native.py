#!/usr/bin/env python3
"""Phase08: native production Paper GameEngine and isolated schema-v4 SQLite, no release."""
from pathlib import Path
import datetime as dt
import json, os, shutil, subprocess, time, traceback

ROOT=Path(__file__).resolve().parents[1]
RUN=dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
BASE=ROOT/"runtime-evidence/HYPER_DOG_PHASE_08"
OUT=BASE/"native-attempts"/RUN
SERVER=BASE/"work"/("native-server-"+RUN)
PAPER=ROOT/"runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar"
PRODUCTION=ROOT/"paper/build/libs/piri-juggler-paper-1.0.0.jar"
HELPER=ROOT/"runtime-test-support/paper/build/libs/piri-runtime-test-paper-1.0.0.jar"
JAVA=shutil.which("java")
DB=OUT/"native-v4.sqlite"
RESULT=OUT/"native.json"
manifest={"run":RUN,"passed":False}
process=None
OUT.mkdir(parents=True,exist_ok=True)
log=OUT/"paper.log"
try:
    for name,path in [("Paper",PAPER),("Production",PRODUCTION),("Helper",HELPER)]:
        if not path.is_file():raise FileNotFoundError(name+" missing: "+str(path))
    if JAVA is None:raise RuntimeError("Java21 not installed")
    plugin=SERVER/"plugins"
    plugin.mkdir(parents=True,exist_ok=True)
    shutil.copy2(PRODUCTION,plugin)
    shutil.copy2(HELPER,plugin)
    (SERVER/"eula.txt").write_text("eula=true\n",encoding="utf-8")
    (SERVER/"server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=25618\nonline-mode=false\n"
        "enforce-secure-profile=false\nmax-players=2\nview-distance=2\n"
        "simulation-distance=2\nspawn-protection=0\ngenerate-structures=false\n",
        encoding="utf-8")
    with log.open("w",encoding="utf-8") as output:
        process=subprocess.Popen(
            [JAVA,"-Xms512M","-Xmx1536M","-Dfile.encoding=UTF-8",
             "-Dpiri.runtime.phase=hyperdog08-native",
             f"-Dpiri.runtime.serverResult={RESULT}",
             f"-Dpiri.runtime.phase08Db={DB}",
             "-jar",str(PAPER),"nogui"],cwd=SERVER,stdin=subprocess.PIPE,
            stdout=output,stderr=subprocess.STDOUT,text=True,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name=="nt" else 0)
        deadline=time.monotonic()+250
        outcome=None
        while time.monotonic()<deadline:
            if process.poll() is not None:
                raise RuntimeError("Paper server died before fixture result: "+
                                   log.read_text(errors="replace")[-4000:])
            if RESULT.exists():
                try:outcome=json.loads(RESULT.read_text(encoding="utf-8"))
                except json.JSONDecodeError:outcome=None
                if outcome is not None:break
            time.sleep(.25)
        if outcome is None:raise TimeoutError("native real Paper fixture result not produced")
        if not outcome.get("passed"):raise AssertionError("native fixture returned "+repr(outcome))
        assert outcome["games"]==30 and outcome["physicalStops"]==90
        assert outcome["acceptedActions"]==outcome["betActions"]+outcome["games"]+outcome["physicalStops"]
        assert outcome["credit"]==4050-outcome["totalBet"]+outcome["totalPayout"]
        assert DB.exists() and DB.stat().st_size>0
        assert "Done (" in log.read_text(errors="replace")
        manifest.update(outcome)
        manifest["databaseFile"]=str(DB)
        manifest["paperLog"]=str(log)
        manifest["passed"]=True
        print("HYPER_DOG_PHASE08_NATIVE_REAL_PAPER=PASS",json.dumps(manifest),flush=True)
except Exception as failure:
    manifest["error"]=str(failure)
    manifest["traceback"]=traceback.format_exc(limit=10)
    print("HYPER_DOG_PHASE08_NATIVE_REAL_PAPER=FAIL",repr(failure),flush=True)
finally:
    if process is not None and process.poll() is None:
        try:
            process.stdin.write("stop\n");process.stdin.flush()
            process.wait(timeout=80)
        except Exception:
            process.kill();process.wait(timeout=10)
    manifest["finishedAt"]=dt.datetime.now(dt.timezone.utc).isoformat()
    (OUT/"RESULT.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
    (OUT/"REPORT.md").write_text(
        "# HYPER_DOG Phase08 native Paper adapter acceptance\n\n"+
        ("PASS" if manifest["passed"] else "FAIL")+
        "\n\n- Actual Paper 1.21 JVM; production GameEngine / GameStore / schema-v4 SQLite\n"
        "- 30 genuine v7-normal rounds with 90 physical stop commands\n"
        "- Fixed seed is selected for transport/reconciliation coverage; not RTP validation\n"
        "- Isolated test helper and SQLite file; no hall DB or public create gate modified\n",
        encoding="utf-8")
raise SystemExit(0 if manifest["passed"] else 1)
