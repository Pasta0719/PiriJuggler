"""Real Paper + Fabric acceptance. First build production and the opt-in helpers.

Run on a desktop with Java 21 and a working OpenGL display:
python runtime-test-support/run_phase01.py
"""
from pathlib import Path
import datetime
import hashlib
import json
import os
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "runtime-evidence" / os.environ.get("PIRI_PHASE01_EVIDENCE", "PHASE_01")
assert EVIDENCE.name in ("PHASE_01", "PHASE_02_PHASE01_REGRESSION", "PHASE_03_PHASE01_REGRESSION")
JAVA = shutil.which("java")
assert JAVA, "Java 21 is required"
PAPER = ROOT / "runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar"
assert PAPER.is_file(), "Download and verify official Paper 1.21 build 130 first"
assert hashlib.sha256(PAPER.read_bytes()).hexdigest() == "ab9bb1afc3cea6978a0c03ce8448aa654fe8a9c4dddf341e7cbda1b0edaa73f5"

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

def read_log(path):
    return path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""

artifacts = {name: ROOT / name / f"build/libs/piri-juggler-{name}-1.0.0.jar" for name in ("common", "paper", "fabric")}
helpers = {
    "paper": ROOT / "runtime-test-support/paper/build/libs/piri-runtime-test-paper-1.0.0.jar",
    "fabric": ROOT / "runtime-test-support/client/build/libs/piri-runtime-test-client-1.0.0.jar"
}
manifest = {
    "startedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    "versions": {"minecraft": "1.21", "paper": "1.21-130", "fabricLoader": "0.16.14", "fabricApi": "0.102.0+1.21", "yarn": "1.21+build.9"},
    "buildHashes": {key: sha(path) for key, path in artifacts.items()},
    "helperHashes": {key: sha(path) for key, path in helpers.items()},
    "scenarios": [], "passed": False
}
save(EVIDENCE / "result.json", manifest)

def run_scenario(scenario):
    output = EVIDENCE / scenario
    server_dir = EVIDENCE / "work" / f"server-{scenario}"
    plugins = server_dir / "plugins"
    plugins.mkdir(parents=True, exist_ok=True)
    output.mkdir(parents=True, exist_ok=True)
    shutil.copy2(artifacts["paper"], plugins)
    shutil.copy2(helpers["paper"], plugins)
    (server_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (server_dir / "server.properties").write_text("\n".join([
        "server-ip=127.0.0.1", "server-port=25585", "online-mode=false", "enforce-secure-profile=false",
        "enable-query=false", "enable-rcon=false", "max-players=2", "view-distance=2", "simulation-distance=2",
        "spawn-protection=0", "generate-structures=false", "level-type=minecraft:normal", "motd=Piri Phase 01 Runtime Acceptance"
    ]) + "\n", encoding="utf-8")
    # Configure the test's own real client. No user Minecraft profile is changed.
    client_dir = EVIDENCE / "work" / f"client-{scenario}"
    client_dir.mkdir(parents=True, exist_ok=True)
    (client_dir / "options.txt").write_text("version:3953\nlang:en_us\nrenderDistance:2\nsimulationDistance:5\nmaxFps:30\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\nonboardAccessibility:false\n", encoding="utf-8")
    for result_name in ("server-result.json", "client-result.json"):
        stale = output / result_name
        if stale.exists():
            shutil.copy2(stale, output / (result_name + ".previous"))
            stale.unlink()
    server_log = output / "server.log"
    client_log = output / "client.log"
    server_cmd = [JAVA, "-Xms512M", "-Xmx1536M", "-Dfile.encoding=UTF-8", f"-Dpiri.runtime.serverResult={output / 'server-result.json'}", "-jar", str(PAPER), "nogui"]
    client_cmd = ["cmd.exe", "/d", "/c", str(ROOT / "gradlew.bat"), "-PruntimeAcceptance=true", f"-PruntimeScenario={scenario}", f"-PruntimeEvidencePhase={EVIDENCE.name}", ":runtime-test-client:runClient", "--console=plain"]
    record = {"name": scenario, "serverCommand": server_cmd, "clientCommand": client_cmd, "serverWorkingDirectory": str(server_dir), "clientWorkingDirectory": str(ROOT), "passed": False}
    creation = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    server = None
    client = None
    started = time.monotonic()
    try:
        with server_log.open("w", encoding="utf-8") as sout, client_log.open("w", encoding="utf-8") as cout:
            print(f"[{scenario}] Starting actual Paper 1.21 with built plugin", flush=True)
            server = subprocess.Popen(server_cmd, cwd=server_dir, stdin=subprocess.PIPE, stdout=sout, stderr=subprocess.STDOUT, text=True, creationflags=creation)
            while True:
                log = read_log(server_log)
                if 'Done (' in log and 'PIRI_RUNTIME_OBSERVER_READY' in log:
                    break
                if server.poll() is not None:
                    raise RuntimeError(f"Paper exited {server.returncode}: " + log[-6000:])
                if time.monotonic() - started > 300:
                    raise TimeoutError("Paper did not become ready in 300 seconds")
                time.sleep(1)
            print(f"[{scenario}] Paper ready. Starting actual Fabric client on the desktop", flush=True)
            client = subprocess.Popen(client_cmd, cwd=ROOT, stdout=cout, stderr=subprocess.STDOUT, creationflags=creation)
            while client.poll() is None:
                if time.monotonic() - started > 900:
                    raise TimeoutError("Fabric runtime did not complete in 900 seconds")
                time.sleep(1)
            record["clientExitCode"] = client.returncode
            if client.returncode != 0:
                raise RuntimeError(f"Fabric exited {client.returncode}: " + read_log(client_log)[-9000:])
            for side in ("server", "client"):
                result_path = output / f"{side}-result.json"
                if not result_path.is_file():
                    raise RuntimeError(f"Missing {side} runtime result")
                record[side] = json.loads(result_path.read_text(encoding="utf-8"))
                if not record[side]["passed"]:
                    raise RuntimeError(f"{side} assertion failed: {record[side]}")
            record["passed"] = True
    except Exception as error:
        record["failure"] = str(error)
        print(f"[{scenario}] FAIL: {error}", flush=True)
    finally:
        if client and client.poll() is None:
            # Only this task's launched process tree is stopped on failure.
            subprocess.run(["taskkill", "/PID", str(client.pid), "/T", "/F"], capture_output=True)
        if server and server.poll() is None:
            try:
                server.stdin.write("stop\n")
                server.stdin.flush()
                server.wait(timeout=60)
            except (OSError, subprocess.TimeoutExpired):
                server.terminate()
                server.wait(timeout=20)
        if server:
            record["serverExitCode"] = server.returncode
            record["passed"] = record["passed"] and server.returncode == 0
        failures = []
        for side, log_path in (("server", server_log), ("client", client_log)):
            for line in read_log(log_path).splitlines():
                if any(marker in line for marker in ("Exception in thread", "Unreported exception thrown", "Encountered an unexpected exception", "Error occurred while enabling", "Could not pass event", "Mixin apply for mod")):
                    failures.append({"side": side, "line": line})
        record["uncaughtExceptions"] = failures
        record["passed"] = record["passed"] and not failures
        for screenshot in (client_dir / "screenshots").glob("*.png"):
            screenshot_dir = EVIDENCE / "screenshots"
            screenshot_dir.mkdir(exist_ok=True)
            shutil.copy2(screenshot, screenshot_dir / screenshot.name)
        record["durationSeconds"] = round(time.monotonic() - started, 2)
        save(output / "result.json", record)
    print(f"[{scenario}] {'PASS' if record['passed'] else 'FAIL'}", flush=True)
    return record

for scenario in ("normal", "mismatch"):
    record = run_scenario(scenario)
    manifest["scenarios"].append(record)
    save(EVIDENCE / "result.json", manifest)
    if not record["passed"]:
        break
manifest["finishedAt"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
manifest["passed"] = len(manifest["scenarios"]) == 2 and all(r["passed"] for r in manifest["scenarios"])
save(EVIDENCE / "result.json", manifest)
for side in ("server", "client"):
    combined = "\n".join(f"===== {record['name']} =====\n" + read_log(EVIDENCE / record["name"] / f"{side}.log") for record in manifest["scenarios"])
    (EVIDENCE / f"{side}.log").write_text(combined, encoding="utf-8")
print("RUNTIME_ACCEPTANCE=" + ("PASS" if manifest["passed"] else "FAIL"), flush=True)
raise SystemExit(0 if manifest["passed"] else 1)
