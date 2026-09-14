import hashlib
import json
import re
import struct
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
total = 0
for module in ("common", "paper", "fabric"):
    for report in (root / module / "build/test-results/test").glob("TEST-*.xml"):
        suite = ET.parse(report).getroot()
        assert all(int(suite.get(key, 0)) == 0 for key in ("failures", "errors", "skipped")), report
        total += int(suite.get("tests"))
    jar = root / module / f"build/libs/piri-juggler-{module}-1.0.0.jar"
    with ZipFile(jar) as z:
        names = set(z.namelist())
        assert "jp/pirijuggler/common/protocol/EnvelopeCodec.class" in names, jar
        assert not any("Test.class" in name for name in names)
        assert not any(name.startswith("jp/pirijuggler/runtime/") for name in names), "Runtime helpers leaked into production"
        assert "piri-runtime.mixins.json" not in names
        for name in names:
            if name.endswith(".class") and name.startswith("jp/pirijuggler/"):
                assert struct.unpack(">H", z.read(name)[6:8])[0] == 65, name
        if module == "paper":
            metadata = z.read("plugin.yml").decode()
            assert "version: '1.0.0'" in metadata and "api-version: '1.21'" in metadata
            assert "main: jp.pirijuggler.paper.PiriJugglerPlugin" in metadata
            assert z.read("config.yml") == (root / "paper/src/main/resources/config.yml").read_bytes()
            assert not any(name.startswith("jp/pirijuggler/fabric/") for name in names)
        if module == "fabric":
            metadata = json.loads(z.read("fabric.mod.json"))
            assert metadata["version"] == "1.0.0"
            assert metadata["environment"] == "client"
            assert metadata["depends"]["minecraft"] == "1.21"
            assert metadata["depends"]["fabricloader"] == "0.16.14"
            assert metadata["depends"]["fabric-api"] == "0.102.0+1.21"
            assert not any(name.startswith("jp/pirijuggler/paper/") for name in names)
            assert "config.yml" not in names and "docs/spec-lock.json" not in names
            payload = z.read("jp/pirijuggler/fabric/network/PiriPayload.class")
            assert b"net/minecraft/class_8710" in payload, "Fabric custom payload must be remapped to intermediary"
    print(f"{module}: {jar.stat().st_size} bytes; SHA256={hashlib.sha256(jar.read_bytes()).hexdigest()}")

for module in ("common", "paper", "fabric"):
    for source in (root / module / "src/main").rglob("*.java"):
        assert not re.search(r"TODO|FIXME|\bstub\b|UnsupportedOperationException", source.read_text(encoding="utf-8")), source
print(f"PASS: {total} tests, 3 Java 21 jars, metadata, remapping, common inclusion, server-only data isolation, no unfinished markers.")

