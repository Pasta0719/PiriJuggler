import importlib.util
from pathlib import Path
import tempfile
import unittest
import warnings
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "verify_jar_patch.py"
spec = importlib.util.spec_from_file_location("verify_jar_patch", SCRIPT)
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


def jar(path, records):
    with zipfile.ZipFile(path, "w") as archive:
        for name, data in records:
            archive.writestr(name, data)


class VerifyJarPatchTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        root = Path(self.directory.name)
        self.old = root / "old" / "same.jar"
        self.new = root / "new" / "same.jar"
        self.old.parent.mkdir()
        self.new.parent.mkdir()
        jar(self.old, [("a.class", b"a"), ("b.class", b"b")])

    def test_allowed_one_class_patch(self):
        jar(self.new, [("a.class", b"a2"), ("b.class", b"b")])
        info = guard.verify(self.old, self.new, {"a.class"}, set())
        self.assertEqual([item["path"] for item in info["changed"]], ["a.class"])
        self.assertEqual(info["unchanged_count"], 1)
        self.assertEqual(info["removed"], [])

    def test_reject_unapproved_modification(self):
        jar(self.new, [("a.class", b"a2"), ("b.class", b"b2")])
        with self.assertRaisesRegex(ValueError, "unexpected_changed"):
            guard.verify(self.old, self.new, {"a.class"}, set())

    def test_reject_added_class_unless_explicit(self):
        jar(self.new, [("a.class", b"a"), ("b.class", b"b"), ("c.class", b"c")])
        with self.assertRaisesRegex(ValueError, "unexpected_added"):
            guard.verify(self.old, self.new, {"a.class"}, set())
        self.assertEqual(len(guard.verify(self.old, self.new, set(), {"c.class"})["added"]), 1)

    def test_reject_deletion_even_if_other_change_allowed(self):
        jar(self.new, [("a.class", b"a2")])
        with self.assertRaisesRegex(ValueError, "removed"):
            guard.verify(self.old, self.new, {"a.class"}, set())

    def test_reject_renamed_jar(self):
        renamed = self.new.with_name("different.jar")
        jar(renamed, [("a.class", b"a2"), ("b.class", b"b")])
        with self.assertRaisesRegex(ValueError, "filename changed"):
            guard.verify(self.old, renamed, {"a.class"}, set())

    def test_reject_baseline_hash_mismatch(self):
        jar(self.new, [("a.class", b"a2"), ("b.class", b"b")])
        with self.assertRaisesRegex(ValueError, "Baseline hash mismatch"):
            guard.verify(self.old, self.new, {"a.class"}, set(), "0" * 64)

    def test_reject_duplicate_entries(self):
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            jar(self.new, [("a.class", b"a2"), ("b.class", b"b"), ("b.class", b"bad")])
        with self.assertRaisesRegex(ValueError, "Duplicate entry"):
            guard.verify(self.old, self.new, {"a.class"}, set())

    def test_reject_bad_jar(self):
        self.new.write_bytes(b"broken zip")
        with self.assertRaisesRegex(ValueError, "Invalid JAR"):
            guard.verify(self.old, self.new, {"a.class"}, set())

    def test_reject_noop(self):
        jar(self.new, [("a.class", b"a"), ("b.class", b"b")])
        with self.assertRaisesRegex(ValueError, "No payload"):
            guard.verify(self.old, self.new, {"a.class"}, set())

    def test_reject_unsafe_zip_path(self):
        jar(self.new, [("a.class", b"a2"), ("b.class", b"b"), ("../escape.class", b"bad")])
        with self.assertRaisesRegex(ValueError, "Unsafe entry"):
            guard.verify(self.old, self.new, {"a.class"}, {"../escape.class"})


if __name__ == "__main__":
    unittest.main()
