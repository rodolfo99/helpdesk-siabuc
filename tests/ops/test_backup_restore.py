"""Backup/restore CLI and filesystem tests with a fake Docker Compose boundary.

No Docker daemon, PostgreSQL process or actual pg_dump/pg_restore is used here.
The fake boundary writes/reads binary streams to verify script behavior only.
"""
from __future__ import annotations

import contextlib
import hashlib
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
SPEC = importlib.util.spec_from_file_location("siabuc_backup_manage_under_test", ROOT / "scripts/manage.py")
manage = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = manage
SPEC.loader.exec_module(manage)

PAYLOAD = b"PGDMP\x00\x01\xff\nFixture binaria de respaldo, no una base real.\x00"


class BackupRestoreTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.base = Path(self.temporary.name)
        self.project = self.base / "proyecto"
        self.cwd = self.base / "directorio de invocacion"
        self.project.mkdir()
        self.cwd.mkdir()
        self.old_cwd = Path.cwd()
        os.chdir(self.cwd)
        self.paths = mock.patch.multiple(manage, ROOT=self.project, ENV_FILE=self.project / ".env", RUNTIME=self.project / ".runtime")
        self.paths.start()
        self.values = {"DB_USER": "siabuc", "DB_NAME": "siabuc_helpdesk"}
        self.output = io.StringIO()
        self.stdout = contextlib.redirect_stdout(self.output)
        self.stdout.__enter__()

    def tearDown(self) -> None:
        self.stdout.__exit__(None, None, None)
        self.paths.stop()
        os.chdir(self.old_cwd)
        self.temporary.cleanup()

    def cli(self, *arguments: str) -> None:
        with mock.patch.object(sys, "argv", ["manage.py", *arguments]):
            manage.main()

    def assert_cli_rejected_before_services(self, *arguments: str) -> None:
        with mock.patch.object(manage, "config") as configure, \
             mock.patch.object(manage, "docker_ready") as docker, \
             mock.patch.object(manage, "compose") as compose, \
             contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as raised:
            self.cli(*arguments)
        self.assertEqual(raised.exception.code, 2)
        configure.assert_not_called()
        docker.assert_not_called()
        compose.assert_not_called()

    def test_backup_cli_accepts_literal_named_or_legacy_path_and_automatic_name(self) -> None:
        literal = "carpeta con espacios/ copia sin extension "
        for arguments, expected in ((["backup", "--nombre-respaldo", literal], literal),
                                    (["backup", literal], literal), (["backup"], None)):
            with self.subTest(arguments=arguments), mock.patch.object(manage, "config", return_value=self.values), \
                 mock.patch.object(manage, "backup") as backup:
                self.cli(*arguments)
                backup.assert_called_once_with(self.values, expected)

    def test_restore_cli_accepts_both_filename_forms_and_forwards_confirmation(self) -> None:
        literal = "copias de datos/ mi respaldo "
        for arguments, confirmed in ((["restore", "--nombre-respaldo", literal, "--confirmar-reemplazo"], True),
                                     (["restore", literal, "--confirmar-reemplazo"], True),
                                     (["restore", "--nombre-respaldo", literal], False)):
            with self.subTest(arguments=arguments), mock.patch.object(manage, "config", return_value=self.values), \
                 mock.patch.object(manage, "restore") as restore:
                self.cli(*arguments)
                restore.assert_called_once_with(self.values, literal, confirmed)

    def test_positional_and_named_paths_are_exclusive_even_when_equal(self) -> None:
        for command in ("backup", "restore"):
            for named in ("primero", "otro"):
                with self.subTest(command=command, named=named):
                    self.assert_cli_rejected_before_services(command, "primero", "--nombre-respaldo", named)

    def test_empty_and_all_whitespace_names_are_rejected_before_configuration(self) -> None:
        for command in ("backup", "restore"):
            for value in ("", "   ", "\t"):
                for option in ([], ["--nombre-respaldo"]):
                    with self.subTest(command=command, value=repr(value), option=option):
                        self.assert_cli_rejected_before_services(command, *option, value)

    def test_restore_requires_a_filename_before_configuration(self) -> None:
        self.assert_cli_rejected_before_services("restore")
        self.assert_cli_rejected_before_services("restore", "--confirmar-reemplazo")

    def test_backup_writes_binary_file_and_checksum_privately_at_literal_cwd_path(self) -> None:
        filename = "carpeta con espacios/ copia sin extension "
        destination = self.cwd / filename
        commands = []

        def dump(values, *arguments, **kwargs):
            self.assertEqual(values, self.values)
            commands.append(arguments)
            kwargs["stdout"].write(PAYLOAD)

        with mock.patch.object(manage, "config", return_value=self.values), \
             mock.patch.object(manage, "docker_ready"), mock.patch.object(manage, "compose", side_effect=dump):
            self.cli("backup", "--nombre-respaldo", filename)
        self.assertEqual(destination.read_bytes(), PAYLOAD)
        self.assertFalse((self.project / filename).exists())
        self.assertFalse(Path(str(destination) + ".dump").exists())
        self.assertEqual(stat.S_IMODE(destination.stat().st_mode), 0o600)
        checksum = destination.with_suffix(destination.suffix + ".sha256")
        self.assertEqual(checksum.read_text(), f"{hashlib.sha256(PAYLOAD).hexdigest()}  {destination.name}\n")
        self.assertEqual(stat.S_IMODE(checksum.stat().st_mode), 0o600)
        self.assertEqual(commands, [("exec", "-T", "db", "pg_dump", "-U", "siabuc", "-d", "siabuc_helpdesk", "--format=custom", "--no-owner")])

    def test_backup_without_filename_creates_timestamped_file_in_project_backups(self) -> None:
        def dump(_values, *_arguments, **kwargs):
            kwargs["stdout"].write(PAYLOAD)

        with mock.patch.object(manage, "docker_ready"), mock.patch.object(manage, "compose", side_effect=dump):
            manage.backup(self.values, None)
        files = list((self.project / "backups").glob("*.dump"))
        self.assertEqual(len(files), 1)
        self.assertRegex(files[0].name, r"^siabuc-\d{8}T\d{12}Z\.dump$")
        self.assertEqual(files[0].read_bytes(), PAYLOAD)
        self.assertTrue(files[0].with_suffix(".dump.sha256").exists())

    def test_failed_dump_removes_only_the_file_it_created(self) -> None:
        destination = self.cwd / "fallido.bin"
        sibling = self.cwd / "archivo que debe conservarse"
        checksum = destination.with_suffix(".bin.sha256")
        sibling.write_bytes(b"original-ajeno")
        checksum.write_bytes(b"checksum-preexistente")

        def failing_dump(_values, *_arguments, **kwargs):
            kwargs["stdout"].write(b"salida parcial")
            raise subprocess.CalledProcessError(1, ["pg_dump"])

        with mock.patch.object(manage, "docker_ready"), mock.patch.object(manage, "compose", side_effect=failing_dump), \
             self.assertRaises(subprocess.CalledProcessError):
            manage.backup(self.values, str(destination))
        self.assertFalse(destination.exists())
        self.assertEqual(sibling.read_bytes(), b"original-ajeno")
        self.assertEqual(checksum.read_bytes(), b"checksum-preexistente")

    def test_existing_backup_is_never_overwritten_or_deleted(self) -> None:
        destination = self.cwd / "existente"
        checksum = destination.with_suffix(".sha256")
        destination.write_bytes(b"respaldo-original")
        checksum.write_bytes(b"checksum-original")
        with mock.patch.object(manage, "docker_ready"), mock.patch.object(manage, "compose") as compose, \
             self.assertRaises(RuntimeError):
            manage.backup(self.values, str(destination))
        compose.assert_not_called()
        self.assertEqual(destination.read_bytes(), b"respaldo-original")
        self.assertEqual(checksum.read_bytes(), b"checksum-original")

    def test_restore_validation_blocks_missing_confirmation_missing_file_and_bad_checksum_before_stop(self) -> None:
        source = self.cwd / "respaldo valido"
        source.write_bytes(PAYLOAD)
        checks = [(str(source), False, None), (str(self.cwd / "inexistente"), True, None)]
        checks.extend((str(source), True, checksum) for checksum in ("0" * 64 + "  respaldo\n", "", "   \n", "no-es-sha256\n"))
        for filename, confirmed, checksum in checks:
            with self.subTest(filename=filename, confirmed=confirmed, checksum=repr(checksum)):
                sidecar = source.with_suffix(".sha256")
                sidecar.unlink(missing_ok=True)
                if checksum is not None:
                    sidecar.write_text(checksum)
                with mock.patch.object(manage, "docker_ready") as docker, mock.patch.object(manage, "compose") as compose, \
                     self.assertRaises(RuntimeError):
                    manage.restore(self.values, filename, confirmed)
                docker.assert_not_called()
                compose.assert_not_called()
                self.assertEqual(source.read_bytes(), PAYLOAD)

    def test_valid_restore_preserves_binary_stdin_and_uses_transactional_flags_after_stopping_services(self) -> None:
        filename = "copias con espacios/ datos sin extension "
        source = self.cwd / filename
        source.parent.mkdir()
        source.write_bytes(PAYLOAD)
        for include_checksum in (False, True):
            with self.subTest(include_checksum=include_checksum):
                sidecar = source.with_suffix(".sha256")
                sidecar.unlink(missing_ok=True)
                if include_checksum:
                    sidecar.write_text(f"{hashlib.sha256(PAYLOAD).hexdigest()}  {source.name}\n")
                commands = []
                restored = []

                def compose(_values, *arguments, **kwargs):
                    commands.append(arguments)
                    if "pg_restore" in arguments:
                        restored.append(kwargs["stdin"].read())

                with mock.patch.object(manage, "config", return_value=self.values), \
                     mock.patch.object(manage, "docker_ready"), mock.patch.object(manage, "compose", side_effect=compose):
                    self.cli("restore", "--nombre-respaldo", filename, "--confirmar-reemplazo")
                self.assertEqual(restored, [PAYLOAD])
                self.assertEqual(source.read_bytes(), PAYLOAD)
                self.assertEqual(commands, [
                    ("up", "-d", "--wait", "--wait-timeout", "180", "db"),
                    ("stop", "frontend", "backend", "smtp"),
                    ("exec", "-T", "db", "pg_restore", "-U", "siabuc", "-d", "siabuc_helpdesk", "--clean", "--if-exists",
                     "--no-owner", "--no-privileges", "--single-transaction", "--exit-on-error"),
                ])

    def test_failed_restore_does_not_restart_services_or_modify_source(self) -> None:
        source = self.cwd / "archivo restaurar"
        source.write_bytes(PAYLOAD)
        commands = []

        def compose(_values, *arguments, **kwargs):
            commands.append(arguments)
            if "pg_restore" in arguments:
                self.assertEqual(kwargs["stdin"].read(), PAYLOAD)
                raise subprocess.CalledProcessError(2, ["pg_restore"])

        with mock.patch.object(manage, "docker_ready"), mock.patch.object(manage, "compose", side_effect=compose), \
             self.assertRaisesRegex(RuntimeError, "restauración transaccional"):
            manage.restore(self.values, str(source), True)
        self.assertEqual(source.read_bytes(), PAYLOAD)
        self.assertEqual(len(commands), 3)
        self.assertEqual(commands[1], ("stop", "frontend", "backend", "smtp"))
        self.assertIn("pg_restore", commands[-1])


if __name__ == "__main__":
    unittest.main()
