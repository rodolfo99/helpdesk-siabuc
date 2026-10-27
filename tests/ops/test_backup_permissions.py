"""Startup permission regressions; Docker calls are simulated except in the CI smoke."""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("backup_permissions_manage", ROOT / "scripts/manage.py")
manage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(manage)


class BackupPermissionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.values = {**manage.DEFAULTS, "BACKUP_UID": "1000", "BACKUP_GID": "1000"}
        self.enterContext(mock.patch.object(manage, "ROOT", self.root))
        self.enterContext(contextlib.redirect_stdout(io.StringIO()))

    def failure(self, code):
        return subprocess.CalledProcessError(code, "docker")

    def test_new_directory_is_private(self):
        path = manage.prepare_automatic_backup_directory(self.values)
        self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o700)

    def test_writable_directory_is_not_repaired(self):
        path = manage.prepare_automatic_backup_directory(self.values)
        path.chmod(0o750)
        before = path.stat()
        with mock.patch.object(manage, "compose") as compose:
            manage.verify_automatic_backup_permissions(self.values)
        self.assertEqual(compose.call_count, 2)
        self.assertNotIn("--user", compose.call_args.args)
        self.assertEqual(path.stat().st_mode, before.st_mode)
        self.assertEqual(path.stat().st_uid, before.st_uid)

    def test_permission_denial_repairs_directory_and_rechecks_as_worker(self):
        with mock.patch.object(manage, "IS_WINDOWS", False), mock.patch.object(
                manage, "compose", side_effect=[None, self.failure(73), None, None]) as compose:
            manage.verify_automatic_backup_permissions(self.values)
        calls = [call.args[1:] for call in compose.call_args_list]
        self.assertEqual(calls[0], ("build", "backup"))
        self.assertEqual(calls[1], calls[3])
        self.assertIn("--no-deps", calls[2])
        self.assertEqual(calls[2][-3:], ("/backups", "1000", "1000"))
        self.assertIn("CHOWN", calls[2])
        self.assertIn("FOWNER", calls[2])
        self.assertNotIn("--user", calls[3])

    def test_other_failures_never_change_ownership(self):
        for code in (1, 74, 125):
            with self.subTest(code=code), mock.patch.object(
                    manage, "compose", side_effect=[None, self.failure(code)]) as compose:
                with self.assertRaisesRegex(RuntimeError, "No se pudo verificar"):
                    manage.verify_automatic_backup_permissions(self.values)
                self.assertEqual(compose.call_count, 2)

    def test_failed_repair_stops_startup(self):
        with mock.patch.object(manage, "IS_WINDOWS", False), mock.patch.object(
                manage, "compose", side_effect=[None, self.failure(73), self.failure(1)]) as compose:
            with self.assertRaisesRegex(RuntimeError, "No se pudieron ajustar"):
                manage.verify_automatic_backup_permissions(self.values)
            self.assertEqual(compose.call_count, 3)

    def test_failed_second_probe_stops_startup(self):
        with mock.patch.object(manage, "IS_WINDOWS", False), mock.patch.object(
                manage, "compose", side_effect=[None, self.failure(73), None, self.failure(73)]):
            with self.assertRaisesRegex(RuntimeError, "No se pudo verificar"):
                manage.verify_automatic_backup_permissions(self.values)

    def test_windows_reports_acl_without_posix_repair(self):
        with mock.patch.object(manage, "IS_WINDOWS", True), mock.patch.object(
                manage, "compose", side_effect=[None, self.failure(73)]) as compose:
            with self.assertRaisesRegex(RuntimeError, "Docker Desktop"):
                manage.verify_automatic_backup_permissions(self.values)
            self.assertEqual(compose.call_count, 2)

    def test_rejects_project_ancestors_home_and_system_directory_before_docker(self):
        for destination in (self.root, self.root.parent, Path.home(), Path("/"), Path("/etc")):
            with self.subTest(destination=destination), mock.patch.object(manage, "compose") as compose:
                with self.assertRaises(RuntimeError):
                    manage.verify_automatic_backup_permissions({**self.values, "BACKUP_DIRECTORY": str(destination)})
                compose.assert_not_called()

    def test_rejects_file_and_symlink_without_modifying_target(self):
        file = self.root / "historic.dump"
        file.write_bytes(b"keep")
        target = self.root / "real"
        target.mkdir(mode=0o750)
        link = self.root / "linked"
        link.symlink_to(target, target_is_directory=True)
        for destination in (file, link, link / "nested"):
            with self.subTest(destination=destination), mock.patch.object(manage, "compose") as compose:
                with self.assertRaises((RuntimeError, OSError)):
                    manage.verify_automatic_backup_permissions({**self.values, "BACKUP_DIRECTORY": str(destination)})
                compose.assert_not_called()
        self.assertEqual(file.read_bytes(), b"keep")
        self.assertEqual(stat.S_IMODE(target.stat().st_mode), 0o750)
        self.assertFalse((target / "nested").exists())

    def test_cli_checks_before_up_and_keeps_solo_bd_independent(self):
        events = []
        def compose(values, *args, **kwargs):
            events.append(args[0])
        for solo in (False, True):
            events.clear()
            with mock.patch.object(sys, "argv", ["manage.py", "start"] + (["--solo-bd"] if solo else [])), \
                    mock.patch.object(manage, "config", return_value=self.values), \
                    mock.patch.object(manage, "docker_ready"), mock.patch.object(manage, "credentials"), \
                    mock.patch.object(manage, "smtp_information"), mock.patch.object(manage, "compose", side_effect=compose), \
                    mock.patch.object(manage, "verify_automatic_backup_permissions", side_effect=lambda _: events.append("verify")):
                manage.main()
            self.assertEqual(events, ["up"] if solo else ["verify", "up"])

    def test_cli_failure_does_not_start_application(self):
        with mock.patch.object(sys, "argv", ["manage.py", "start"]), \
                mock.patch.object(manage, "config", return_value=self.values), \
                mock.patch.object(manage, "docker_ready"), mock.patch.object(manage, "compose") as compose, \
                mock.patch.object(manage, "verify_automatic_backup_permissions", side_effect=RuntimeError("denied")):
            with self.assertRaisesRegex(RuntimeError, "denied"):
                manage.main()
            compose.assert_not_called()

    @unittest.skipUnless(hasattr(os, "geteuid") and os.geteuid() == 0, "Real UID switch needs root; also covered by Docker CI smoke")
    def test_real_root_owned_0700_directory_recovers_without_changing_existing_files(self):
        # Run the exact probe/repair programs on an isolated filesystem. This
        # exercises real Unix permissions but does not claim a Docker execution.
        try:
            subprocess.run([sys.executable, "-c", "pass"], user=1000, group=1000, extra_groups=[], check=True)
        except PermissionError:
            self.skipTest("Runtime disallows UID switching; real Docker scenario runs in CI")
        self.root.chmod(0o755)
        parent = self.root / "backups"
        parent.mkdir(mode=0o755)
        directory = manage.prepare_automatic_backup_directory(self.values)
        original = directory / "historic.dump"
        original.write_bytes(b"keep historical backup")
        original.chmod(0o600)
        before = original.stat()
        repairs = []
        def local_compose(values, *args, **kwargs):
            if args[0] == "build":
                return
            code = args[args.index("-c") + 1]
            repair = "--user" in args
            if repair:
                repairs.append(True)
            return subprocess.run([sys.executable, "-c", code, str(directory)] + (["1000", "1000"] if repair else []),
                                  user=0 if repair else 1000, group=0 if repair else 1000, extra_groups=[],
                                  check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        with mock.patch.object(manage, "IS_WINDOWS", False), mock.patch.object(manage, "compose", side_effect=local_compose):
            manage.verify_automatic_backup_permissions(self.values)
            manage.verify_automatic_backup_permissions(self.values)
        self.assertEqual(len(repairs), 1)
        self.assertEqual((directory.stat().st_uid, directory.stat().st_gid), (1000, 1000))
        self.assertEqual(stat.S_IMODE(directory.stat().st_mode), 0o700)
        self.assertEqual(original.read_bytes(), b"keep historical backup")
        after = original.stat()
        self.assertEqual((after.st_uid, after.st_gid, after.st_mode, after.st_mtime_ns),
                         (before.st_uid, before.st_gid, before.st_mode, before.st_mtime_ns))
        self.assertEqual(list(directory.iterdir()), [original])


if __name__ == "__main__":
    unittest.main()
