"""Check the final Phase02 build and runtime evidence without launching Minecraft."""
from pathlib import Path
from zipfile import ZipFile
import hashlib, json, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "runtime-evidence/PHASE_02"
runtime = json.loads((EVIDENCE / "result.json").read_text(encoding="utf-8"))
assert runtime["passed"], runtime.get("failure")
assert not runtime["uncaughtExceptions"]
assert len(runtime["assertions"]) == 19 and all(a["passed"] for a in runtime["assertions"])
assert len([e for e in runtime["exits"] if "server" in e and e["server"] == 0]) == 2
assert len([e for e in runtime["exits"] if "client" in e and e["exit"] == 0]) == 3
for module in ("common", "paper", "fabric"):
    jar = ROOT / module / f"build/libs/piri-juggler-{module}-1.0.0.jar"
    assert hashlib.sha256(jar.read_bytes()).hexdigest() == runtime["buildHashes"][module], module + " runtime jar differs from final build"
    with ZipFile(jar) as archive:
        names = set(archive.namelist())
        assert not any(name.startswith("jp/pirijuggler/runtime/") for name in names)
        if module == "paper":
            assert "org/sqlite/JDBC.class" in names
            assert any(name.startswith("org/sqlite/native/Windows/x86_64/") for name in names)
            assert archive.read("schema-v4.sql") == (ROOT / "paper/src/main/resources/schema-v4.sql").read_bytes()
        if module == "fabric":
            assert not any(name.startswith(("org/sqlite/", "jp/pirijuggler/paper/")) for name in names)
            assert "schema-v4.sql" not in names
tests = 0
for module in ("common", "paper", "fabric"):
    for file in (ROOT / module / "build/test-results/test").glob("TEST-*.xml"):
        suite = ET.parse(file).getroot()
        assert all(int(suite.get(key, 0)) == 0 for key in ("failures", "errors", "skipped")), file
        tests += int(suite.get("tests"))
for task in ("test", "build"):
    assert "BUILD SUCCESSFUL" in (EVIDENCE / f"gradle-{task}.log").read_text(encoding="utf-8-sig")
print(f"PASS: {tests} tests; {len(runtime['assertions'])} actual-runtime assertions; exact build hashes; SQLite/schema packaged only in Paper; no runtime helpers in production.")
