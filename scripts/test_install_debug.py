import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from install_debug import read_build_info, validate_freshness, validate_modified_install, validate_owner


class InstallGuardTests(unittest.TestCase):
    def test_only_owner_installs_without_explicit_transfer(self):
        validate_owner(None, "/current", False)
        validate_owner({"workspace": "/current"}, "/current", False)
        with self.assertRaisesRegex(RuntimeError, "Install owner"):
            validate_owner({"workspace": "/other"}, "/current", False)
        validate_owner({"workspace": "/other"}, "/current", True)

    def test_transfer_cannot_overwrite_uncommitted_build(self):
        installed = {"commit": "head", "dirty": True}
        validate_modified_install(installed, {"workspace": "/current"}, "/current")
        for owner in (None, {"workspace": "/other"}):
            with self.assertRaisesRegex(RuntimeError, "uncommitted changes"):
                validate_modified_install(installed, owner, "/current")
        validate_modified_install({"commit": "head", "dirty": False}, None, "/current")

    def test_current_branch_is_allowed(self):
        validate_freshness("head", "main", {"commit": "installed", "dirty": False}, lambda a, b: True)

    def test_missing_main_is_blocked(self):
        with self.assertRaisesRegex(RuntimeError, "Stale checkout"):
            validate_freshness("head", "main", None, lambda a, b: False)

    def test_missing_installed_commit_is_blocked(self):
        with self.assertRaisesRegex(RuntimeError, "installed commit"):
            validate_freshness(
                "head", "main", {"commit": "installed", "dirty": False},
                lambda a, b: a == "main",
            )

    def test_legacy_bootstrap_still_checks_main(self):
        validate_freshness("head", "main", None, lambda a, b: True)

    def test_modified_build_warning_is_explicit(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            validate_freshness("head", "main", {"commit": "head", "dirty": True}, lambda a, b: True)
        self.assertIn("uncommitted changes", output.getvalue())

    def test_apk_metadata_and_legacy_detection(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app.apk"
            with zipfile.ZipFile(apk, "w"):
                pass
            self.assertIsNone(read_build_info(apk))
            info = {"commit": "abc", "dirty": False, "builtAt": "2026-10-10T12:00:00Z"}
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/build-info.json", json.dumps(info))
            self.assertEqual(info, read_build_info(apk))

    def test_invalid_metadata_is_not_a_legacy_fallback(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/build-info.json", '{"commit": 123, "dirty": false}')
            with self.assertRaisesRegex(RuntimeError, "Invalid APK"):
                read_build_info(apk)


if __name__ == "__main__":
    unittest.main()
