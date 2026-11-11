"""Regression tests proving the release gate refuses unauthorized release changes."""
import copy
import hashlib
import json
import pathlib
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent / "scripts"))
import guard_jar_release as guard


class JarReleaseGuardTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = pathlib.Path(self.tmp.name)
        self.old = self.dir / "baseline.jar"
        self.paper = self.dir / "piri-juggler-paper-1.0.0.jar"
        self.fabric = self.dir / "piri-juggler-fabric-1.0.0.jar"
        self.original = {
            "assets/piri/sounds/important.ogg": b"sound must not change",
            "assets/piri/textures/chance.png": b"image must not change",
            "jp/pirijuggler/Unrelated.class": b"old unrelated class",
            "jp/pirijuggler/Allowed.class": b"old class",
        }
        self.updated = dict(self.original)
        self.updated["jp/pirijuggler/Allowed.class"] = b"approved new class"
        self.updated["jp/pirijuggler/Added.class"] = b"approved added class"
        self.write(self.old, self.original)
        self.write(self.paper, self.updated)
        self.write(self.fabric, self.updated)
        with zipfile.ZipFile(self.old) as z:
            protected = set(self.original) - {"jp/pirijuggler/Allowed.class"}
            template = {
                "baseline_sha256": guard.sha256_file(self.old),
                "baseline_entries": 4,
                "protected_entries": 3,
                "protected_fingerprint": guard.fingerprint(z, protected),
                "allowed_modified": ["jp/pirijuggler/Allowed.class"],
                "allowed_added": ["jp/pirijuggler/Added.class"],
                "modified_original_sha256": {
                    "jp/pirijuggler/Allowed.class":
                    hashlib.sha256(self.original["jp/pirijuggler/Allowed.class"]).hexdigest()},
            }
        self.policy = {"schema": 1, "jars": {
            side: dict(template, filename=f"piri-juggler-{side}-1.0.0.jar")
            for side in ("paper", "fabric")}}
        self.policy_path = self.dir / "policy.json"
        self.policy_path.write_text(json.dumps(self.policy))

    @staticmethod
    def write(path, entries, compression=zipfile.ZIP_DEFLATED):
        with zipfile.ZipFile(path, "w", compression=compression) as z:
            for name, data in entries.items():
                z.writestr(name, data)

    def run_guard(self, paper=None, fabric=None, mode="verify-release"):
        return guard.main([mode, "--paper", str(paper or self.paper),
                           "--fabric", str(fabric or self.fabric),
                           "--policy", str(self.policy_path)])

    def test_approved_changes_pass(self):
        self.assertEqual(0, self.run_guard())

    def test_exact_original_baseline_sha256_passes(self):
        self.assertEqual(0, self.run_guard(self.old, self.old, "verify-baseline"))

    def test_modified_sound_is_rejected(self):
        data = dict(self.updated)
        data["assets/piri/sounds/important.ogg"] = b"no sound!"
        self.write(self.paper, data)
        self.assertEqual(1, self.run_guard())

    def test_modified_image_is_rejected(self):
        data = dict(self.updated)
        data["assets/piri/textures/chance.png"] = b"replacement image"
        self.write(self.paper, data)
        self.assertEqual(1, self.run_guard())

    def test_modified_unrelated_java_class_is_rejected(self):
        data = dict(self.updated)
        data["jp/pirijuggler/Unrelated.class"] = b"silent refactor"
        self.write(self.fabric, data)
        self.assertEqual(1, self.run_guard())

    def test_removed_sound_is_rejected(self):
        data = dict(self.updated)
        del data["assets/piri/sounds/important.ogg"]
        self.write(self.fabric, data)
        self.assertEqual(1, self.run_guard())

    def test_added_unapproved_class_is_rejected(self):
        data = dict(self.updated)
        data["jp/pirijuggler/Extra.class"] = b"unapproved"
        self.write(self.paper, data)
        self.assertEqual(1, self.run_guard())

    def test_allowed_class_not_actually_changed_is_rejected(self):
        data = dict(self.updated)
        data["jp/pirijuggler/Allowed.class"] = self.original["jp/pirijuggler/Allowed.class"]
        self.write(self.paper, data)
        self.assertEqual(1, self.run_guard())

    def test_identical_file_recompressed_is_rejected(self):
        self.write(self.fabric, self.updated, compression=zipfile.ZIP_STORED)
        self.assertEqual(1, self.run_guard())

    def test_renamed_jar_is_rejected(self):
        renamed = self.dir / "piri-fabric-safe.jar"
        renamed.write_bytes(self.fabric.read_bytes())
        self.assertEqual(1, self.run_guard(fabric=renamed))

    def test_bad_baseline_is_rejected(self):
        self.assertEqual(1, self.run_guard(self.paper, self.fabric, "verify-baseline"))

    def test_fails_on_malformed_jar(self):
        self.paper.write_bytes(b"not a JAR")
        self.assertEqual(1, self.run_guard())

    def test_duplicate_zip_entries_are_rejected(self):
        with zipfile.ZipFile(self.fabric, "a") as z:
            z.writestr("assets/piri/sounds/important.ogg", b"duplicate")
        self.assertEqual(1, self.run_guard())


if __name__ == "__main__":
    unittest.main()