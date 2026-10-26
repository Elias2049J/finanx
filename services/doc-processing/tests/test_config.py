import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.config import StorageSettings  # noqa: E402


class StorageSettingsTest(unittest.TestCase):
    def test_secret_files_override_direct_environment(self):
        with tempfile.TemporaryDirectory() as tmp:
            access = Path(tmp) / "access-key"
            secret = Path(tmp) / "secret-key"
            access.write_text("file-access\n", encoding="utf-8")
            secret.write_text("file-secret\r\n", encoding="utf-8")

            with patch.dict(
                os.environ,
                {
                    "S3_ACCESS_KEY": "env-access",
                    "S3_SECRET_KEY": "env-secret",
                    "S3_ACCESS_KEY_FILE": str(access),
                    "S3_SECRET_KEY_FILE": str(secret),
                },
                clear=False,
            ):
                settings = StorageSettings()

            self.assertEqual(settings.access_key, "file-access")
            self.assertEqual(settings.secret_key, "file-secret")

    def test_empty_secret_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            empty = Path(tmp) / "empty"
            empty.write_text("\n", encoding="utf-8")

            with patch.dict(os.environ, {"S3_ACCESS_KEY_FILE": str(empty)}, clear=False):
                with self.assertRaisesRegex(RuntimeError, "S3_ACCESS_KEY_FILE is empty"):
                    StorageSettings()

    def test_direct_environment_is_used_without_secret_file(self):
        with patch.dict(
            os.environ,
            {"S3_ACCESS_KEY": "env-access", "S3_SECRET_KEY": "env-secret"},
            clear=False,
        ):
            with patch.dict(os.environ, {"S3_ACCESS_KEY_FILE": "", "S3_SECRET_KEY_FILE": ""}):
                settings = StorageSettings()

        self.assertEqual(settings.access_key, "env-access")
        self.assertEqual(settings.secret_key, "env-secret")


if __name__ == "__main__":
    unittest.main()
