"""Preflight Docker del destino de respaldos, sin modificar copias reales."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("siabuc_backup_permission_check", ROOT / "scripts/manage.py")
manage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(manage)

class BackupPermissionsTests(unittest.TestCase):
    def test_new_folder_created_without_changing_existing_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp)
            with mock.patch.object(manage, "ROOT", project):
                dest = manage.prepare_automatic_backup_directory({"BACKUP_DIRECTORY":"./backups/automatic"})
                self.assertTrue(dest.is_dir())
                self.assertEqual(dest.stat().st_mode & 0o777, 0o700)
                previous = dest / "backup.dump"
                previous.write_bytes(b"preserve")
                manage.prepare_automatic_backup_directory({"BACKUP_DIRECTORY":"./backups/automatic"})
                self.assertEqual(previous.read_bytes(), b"preserve")

    def test_probe_as_configured_uid_with_real_compose_command(self):
        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch.object(manage, "ROOT", Path(tmp)), mock.patch.object(manage, "compose") as compose:
                dest = manage.verify_automatic_backup_permissions({"BACKUP_DIRECTORY": "./backups/automatic",
                                                                    "BACKUP_UID": "1234", "BACKUP_GID": "1235"})
                self.assertTrue(dest.is_dir())
                self.assertEqual(compose.call_count, 2)
                self.assertEqual(compose.call_args_list[0].args[1:], ("build", "backup"))
                command = compose.call_args_list[1].args[1:]
                self.assertEqual(command[:6], ("run", "--rm", "--no-deps", "--entrypoint", "python3", "backup"))
                self.assertIn("mkstemp", command[-1])
                self.assertIn("unlink", command[-1])

    def test_unwritable_destination_keeps_existing_backups(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / "backups"
            dest.mkdir()
            file = dest / "old.dump"
            file.write_bytes(b"historic")
            with mock.patch.object(manage, "ROOT", Path(tmp)), mock.patch.object(manage, "compose", side_effect=[
                None, subprocess.CalledProcessError(1, "docker")]):
                with self.assertRaisesRegex(RuntimeError, "sudo chown 999:998") as raised:
                    manage.verify_automatic_backup_permissions({"BACKUP_DIRECTORY": str(dest),
                                                                 "BACKUP_UID":"999", "BACKUP_GID":"998"})
            self.assertIn("sudo chmod 700", str(raised.exception))
            self.assertEqual(file.read_bytes(), b"historic")

    def test_invalid_target_rejected_before_docker_probe(self):
        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch.object(manage, "ROOT", Path(tmp)), mock.patch.object(manage, "compose") as compose:
                with self.assertRaises(RuntimeError):
                    manage.verify_automatic_backup_permissions({"BACKUP_DIRECTORY": str(Path(tmp))})
                compose.assert_not_called()

if __name__ == "__main__":
    unittest.main()
