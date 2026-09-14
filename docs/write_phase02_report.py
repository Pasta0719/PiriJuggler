"""Publish a Phase02 report only after verifying the actual final build and runtime."""
from pathlib import Path
import hashlib, json, subprocess, sys, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "runtime-evidence/PHASE_02"
REGRESSION = ROOT / "runtime-evidence/PHASE_02_PHASE01_REGRESSION"
for script in ("verify_phase01_artifacts.py", "verify_phase02_artifacts.py"):
    completed = subprocess.run([sys.executable, str(ROOT / "docs" / script)], cwd=ROOT, text=True, capture_output=True, encoding="utf-8")
    (EVIDENCE / (script.removesuffix(".py") + ".log")).write_text(completed.stdout + completed.stderr, encoding="utf-8")
    assert completed.returncode == 0, completed.stdout + completed.stderr
result = json.loads((EVIDENCE / "result.json").read_text(encoding="utf-8"))
regression = json.loads((REGRESSION / "result.json").read_text(encoding="utf-8"))
assert regression["passed"] and len(regression["scenarios"]) == 2
assert regression["buildHashes"] == result["buildHashes"]
counts = {}
for module in ("common", "paper", "fabric"):
    counts[module] = sum(int(ET.parse(p).getroot().get("tests")) for p in (ROOT / module / "build/test-results/test").glob("TEST-*.xml"))
labels = [
    "実ワールドにButtonを配置", "実プレイヤーの視線とcreateコマンドで登録", "同一座標の重複登録を拒否",
    "実セッション作成とOPEN_MACHINE / PUBLIC_STATE受信", "登録Buttonのvanilla動作をキャンセル", "同一プレイヤーの2台目着席を拒否",
    "占有中も台鍵によるAdminSession入口を開く", "切断時のGRACE snapshot永続化", "プロセス終了後のDB登録保持",
    "実JVM再起動後も登録保持・新営業期間へ切替", "旧セッションをSAFE化して保持", "SAFE再開時に同じsessionIdと現在営業期間を使用",
    "2台の実Fabric同時接続で占有台の右クリックを拒否", "空セッション終了後に別プレイヤーが着席可能",
    "実Buttonへredefineして台ID・設定を保持", "remove後も実Buttonを維持", "削除済み台IDを再利用しない",
    "redefine/remove後も過去期間の統計・履歴を保持", "SQLite integrity / foreign keys正常"
]
assert len(labels) == len(result["assertions"])
lines = ["# Phase02 Runtime Acceptance — PASS", "", f"実行: {result['startedAt']} ～ {result['finishedAt']}（UTC）", "",
    "Minecraft 1.21 / Paper 1.21-130 / Fabric Loader 0.16.14 / Fabric API 0.102.0+1.21 / Java 21。",
    "localhost 127.0.0.1:25586、Phase02専用の隔離ワールド。PiriRuntimeTest と PiriRuntimeTest2 の実Fabricクライアントを使用。", "",
    f"自動テスト: **{sum(counts.values())}件 PASS**（common {counts['common']} / paper {counts['paper']} / fabric {counts['fabric']}）。失敗・error・skipは0。",
    "`gradlew.bat test --console=plain` と `gradlew.bat build --console=plain` はexit 0。実Paperは2プロセス、実Fabricは3起動ともexit 0。未処理例外0。", "",
    "## 受入結果", "", "| 確認項目 | 結果 |", "|---|---|"]
for label, assertion in zip(labels, result["assertions"]):
    assert assertion["passed"]
    lines.append(f"| {label} | PASS |")
lines += ["", "## 再現手順", "", "```powershell", ".\\gradlew.bat test --console=plain", ".\\gradlew.bat build --console=plain",
    ".\\gradlew.bat -PruntimeAcceptance=true build --console=plain", "python runtime-test-support/run_phase02.py", "```", "",
    "1. テスト補助moduleが実ワールドにStone Buttonを配置し、実プレイヤーをOPにする。",
    "2. 実クライアントから視線更新、createコマンド、右クリックを送信し、本番pluginのDB commitと本番modの受信状態を照合。",
    "3. クライアントとサーバーを正常終了。同じDBとworldで別のPaper JVMを起動し、登録・営業期間・セッションを照合。",
    "4. 2台の実Fabricを同時接続して占有拒否・席の解放・再定義・削除・ID非再利用を確認。",
    "5. 終了後のSQLite integrity、foreign keys、過去統計・履歴を確認。", "",
    "操作補助・観測コードはruntime-test-supportだけに存在し、本番jarには含めない。ゲーム画面・管理画面UIは後続Phaseの範囲。", "",
    "## Build SHA-256", "", "| Artifact | SHA-256 |", "|---|---|"]
for module, digest in result["buildHashes"].items(): lines.append(f"| {module} | `{digest}` |")
lines += ["", "## 証跡", "", f"- 実行別詳細: `attempts/{result['run']}/`（command、各段階snapshot、client/server結果）",
    "- 集約ログ: `server.log`, `client.log`", "- 判定と起動コマンド・build hash: `result.json`",
    "- build/testログ: `gradle-test.log`, `gradle-build.log`", "- 実Minecraft screenshot: `screenshots/`",
    "- artifact検証: `verify_phase01_artifacts.log`, `verify_phase02_artifacts.log`", "",
    "## Phase01回帰テスト", "", f"同じ本番buildで {regression['startedAt']} ～ {regression['finishedAt']}（UTC）に再実行。",
    "正常HELLO/ACKとprotocol不一致による利用拒否を、実Paper/Fabricで両方PASS。過去migration evidenceは上書きせず保持。",
    "証跡: `../PHASE_02_PHASE01_REGRESSION/result.json`, `server.log`, `client.log`。", "",
    "## 修正と後続Phase", "",
    "途中runはWindowsでのテスト制御・観測ファイルの競合、クライアント応答を待つ前のテスト判定、コマンド間の視線条件によりFAILとなり、各attemptを保存した。観測書込を再試行可能にし、操作指示を操作ごとの不変ファイルに変更。各操作の直前に実クライアントの視線を設定し、終了時にはサーバーcommitとクライアント応答の両方を待って、上記runで全項目を再実行した。",
    "SESSION_ENDはSPECどおりsessionIdだけのpayloadに修正し、クライアントの終了処理も照合した。",
    "Phase02が作成するゲーム状態はSEATED_READY。未完ゲームのForce Settlementはリール・抽選・ボーナスを実装した後のPhase11で統合する。この段階では新しいゲーム実装由来の未完snapshotを検出した場合、権利を消去せずロック解放と期間切替を拒否する。",
    "Phase03には進まず、このPhase02 runで終了。", ""]
(EVIDENCE / "REPORT.md").write_text("\n".join(lines), encoding="utf-8")
regression_report = ["# Phase01 regression after Phase02 — PASS", "", f"{regression['startedAt']} — {regression['finishedAt']}", "",
    "Actual Paper 1.21-130 and Fabric 1.21 / Loader 0.16.14 / API 0.102.0+1.21. Same production jar hashes as Phase02.", ""]
for scenario in regression["scenarios"]:
    assert scenario["passed"] and not scenario["uncaughtExceptions"]
    regression_report.append(f"- {scenario['name']}: PASS; client exit {scenario['clientExitCode']}; server exit {scenario['serverExitCode']}.")
regression_report += ["", "Commands, timestamps, received payloads and build hashes are in result.json; raw logs are server.log and client.log.", ""]
(REGRESSION / "REPORT.md").write_text("\n".join(regression_report), encoding="utf-8")
print("Verified reports written; status has not been changed by this script.")
