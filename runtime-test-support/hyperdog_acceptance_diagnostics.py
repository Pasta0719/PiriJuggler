#!/usr/bin/env python3
"""Test-only diagnostics for HYPER_DOG Paper/Fabric CI, never gameplay logic.

A timeout or assertion is always a failure. This records *which side* stopped
making progress; it must not classify ambiguous timeouts as harmless or retry
failed gameplay assertions.
"""
from pathlib import Path
import json
import os
import sys

SAFE_SERVER=("ready","complete","completedRounds","startedRounds","acceptedBets",
             "acceptedLevers","acceptedStops","rejected","manualStops","automaticStops",
             "snapshotRestorations","snapshotRestored","setting","atStarts","replays")
SAFE_CLIENT=("connected","handshake","completedRounds","inputRequests","betRequests",
             "leverRequests","stopRequests","renderedFrames","error","screenshot",
             "screenshotRequested")

def read_json(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except (FileNotFoundError, PermissionError, json.JSONDecodeError):
        return {}

def compact_states(server_file,client_file):
    s=read_json(server_file)
    c=read_json(client_file)
    return {
        "Paper":{k:s[k] for k in SAFE_SERVER if k in s},
        "Fabric":{k:c[k] for k in SAFE_CLIENT if k in c},
        # Report only IDs/indices, not server-only lottery information.
        "serverLastSpinId": (s.get("rounds") or [{}])[-1].get("spinId"),
        "clientLastSpinId": (c.get("rounds") or [{}])[-1].get("spinId"),
        "serverResultPresent":Path(server_file).is_file(),
        "clientResultPresent":Path(client_file).is_file(),
    }

def failure_category(error):
    if isinstance(error, TimeoutError):
        return "PROGRESS_TIMEOUT_UNCLASSIFIED"
    if isinstance(error, AssertionError):
        return "ASSERTION_FAILED_INVESTIGATE"
    if isinstance(error, (FileNotFoundError, PermissionError, OSError)):
        return "FILESYSTEM_OR_ENVIRONMENT_ERROR"
    if isinstance(error, RuntimeError) and any(
            word in str(error).lower() for word in ("terminated","died","process exit")):
        return "RUNTIME_PROCESS_TERMINATED"
    return "RUNTIME_EXCEPTION_INVESTIGATE"

def tail(path, limit=12000):
    try:
        with Path(path).open("rb") as file:
            file.seek(0,os.SEEK_END)
            size=file.tell()
            file.seek(max(0,size-limit))
            return file.read().decode("utf-8","replace")[-limit:]
    except (OSError,PermissionError):
        return ""

def record_failure(out,manifest,exc,server_file,client_file,server_log,client_log,
                   server_process=None,client_process=None):
    manifest["failureCategory"]=failure_category(exc)
    details=compact_states(server_file,client_file)
    details["category"]=manifest["failureCategory"]
    details["reason"]=str(exc)
    details["paperExitCode"]=server_process.poll() if server_process else None
    details["fabricExitCode"]=client_process.poll() if client_process else None
    details["serverLogTail"]=tail(server_log)
    details["clientLogTail"]=tail(client_log)
    manifest["diagnostics"]={k:v for k,v in details.items()
                             if not k.endswith("LogTail")}
    Path(out).mkdir(parents=True,exist_ok=True)
    (Path(out)/"diagnostics.json").write_text(
        json.dumps(details,ensure_ascii=False,indent=2),encoding="utf-8")
    print("HYPER_DOG_CI_DIAGNOSIS",json.dumps(manifest["diagnostics"]),flush=True)

def summarize(phase,summary_file):
    root=Path(__file__).resolve().parents[1]
    base=root/"runtime-evidence"/phase
    result=read_json(base/"result.json")
    status="PASS" if result.get("passed") else "FAIL" if result else "NOT_RUN"
    lines=[f"### {phase} — {status}", ""]
    if result:
        if result.get("failure"):
            lines.append(f"- Failure: \`{result.get('failureCategory','UNCLASSIFIED')}\` — {result['failure']}")
        diag=result.get("diagnostics",{})
        for name in ("Paper","Fabric"):
            if name in diag:lines.append(f"- {name}: \`{json.dumps(diag[name],ensure_ascii=False)}\`")
        lines.append(f"- Checks passed: {sum(result.get('checks',{}).values())}/"
                     f"{len(result.get('checks',{}))}")
    else:
        lines.append("- No runtime result. Examine the failing build/preflight step; "
                     "do not treat as game acceptance.")
    lines.extend(["","Actual log and JSON evidence remain in the workflow artifact. "
                       "No automatic retry hides functional failures.",""])
    summary=Path(summary_file)
    summary.parent.mkdir(parents=True,exist_ok=True)
    with summary.open("a",encoding="utf-8") as file:
        file.write("\n".join(lines)+"\n")
    print("\n".join(lines))

if __name__=="__main__":
    if len(sys.argv)==4 and sys.argv[1]=="summarize":
        summarize(sys.argv[2],sys.argv[3])
    else:
        raise SystemExit("usage: hyperdog_acceptance_diagnostics.py summarize PHASE GITHUB_STEP_SUMMARY")
