"""Exercise dotenv compatibility and local configuration without Docker or SMTP."""
from __future__ import annotations

import contextlib
import importlib.util
import io
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("siabuc_manage_under_test", ROOT / "scripts/manage.py")
manage = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = manage
SPEC.loader.exec_module(manage)


class OperationalConfigurationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        self.env = self.root / ".env"
        self.paths = mock.patch.multiple(manage, ROOT=self.root, ENV_FILE=self.env, RUNTIME=self.root / ".runtime")
        self.paths.start()
        self.output = io.StringIO()
        self.stdout = contextlib.redirect_stdout(self.output)
        self.stdout.__enter__()

    def tearDown(self) -> None:
        self.stdout.__exit__(None, None, None)
        self.paths.stop()
        self.directory.cleanup()

    def existing(self, **extra: str) -> str:
        values = {
            "DB_PASSWORD": "ClaveBDAnterior123!",
            "ADMIN_PASSWORD": "ClaveAdminAnterior123!",
            "DB_PORT": "5544",
            "SERVER_PORT": "8181",
            "FRONTEND_PORT": "4300",
        }
        values.update(extra)
        content = "# Conservar este comentario y los datos anteriores\n" + "".join(
            f"{key}={manage.env_value(value)}\n" for key, value in values.items())
        self.env.write_text(content, encoding="utf-8")
        return content

    def wizard(self, answers: list[str], passwords: list[str] | None = None, via_cli: bool = False):
        with mock.patch.object(manage.sys.stdin, "isatty", return_value=True), \
             mock.patch.object(manage, "detected_local_ip", return_value="192.168.1.50"), \
             mock.patch("builtins.input", side_effect=answers), \
             mock.patch.object(manage.getpass, "getpass", side_effect=passwords or []), \
             mock.patch.object(manage, "run", side_effect=AssertionError("Configurar no debe ejecutar servicios")):
            if via_cli:
                with mock.patch.object(sys, "argv", ["manage.py", "config", "--smtp-interno"]):
                    manage.main()
                return manage.config()
            return manage.set_internal_smtp_configuration(manage.config())

    def test_new_installation_defaults_to_disabled_direct_gmail_with_private_env(self) -> None:
        values = manage.config(create=True)
        self.assertEqual(values["MAIL_ENABLED"], "false")
        self.assertEqual(values["SMTP_HOST"], "smtp.gmail.com")
        self.assertEqual(values["SMTP_PORT"], "587")
        self.assertEqual(values["SMTP_AUTH"], "true")
        self.assertEqual(values["SMTP_SECURITY"], "STARTTLS")
        self.assertEqual(values["SMTP_INTERNAL_PORT"], "2525")
        self.assertEqual(values["SMTP_LOCAL_IP"], "127.0.0.1")
        self.assertEqual(values["MAIL_FROM"], "")
        self.assertGreaterEqual(len(values["DB_PASSWORD"]), 12)
        self.assertGreaterEqual(len(values["ADMIN_PASSWORD"]), 12)
        self.assertEqual(stat.S_IMODE(self.env.stat().st_mode), 0o600)
        self.assertNotIn(values["DB_PASSWORD"], self.output.getvalue())
        self.assertNotIn(values["ADMIN_PASSWORD"], self.output.getvalue())

    def test_legacy_without_mail_keys_stays_disabled_and_file_bytes_are_preserved(self) -> None:
        original = self.existing()
        values = manage.config(create=True)
        self.assertEqual(values["MAIL_ENABLED"], "false")
        self.assertEqual(values["SMTP_HOST"], "")
        self.assertEqual(values["SMTP_PORT"], "587")
        self.assertEqual(values["DB_PORT"], "5544")
        self.assertEqual(self.env.read_text(), original)
        self.assertEqual(stat.S_IMODE(self.env.stat().st_mode), 0o600)

    def test_existing_external_mail_credentials_and_enabled_state_are_preserved(self) -> None:
        secret = "Anterior $ clave # privada"
        original = self.existing(MAIL_ENABLED="true", SMTP_HOST="relay.example.test", SMTP_PORT="465",
                                 SMTP_SECURITY="SSL", SMTP_AUTH="true", SMTP_USERNAME="soporte@example.test",
                                 SMTP_PASSWORD=secret, MAIL_FROM="soporte@example.test")
        values = manage.config(create=True)
        self.assertEqual(values["MAIL_ENABLED"], "true")
        self.assertEqual(values["SMTP_HOST"], "relay.example.test")
        self.assertEqual(values["SMTP_PASSWORD"], secret)
        self.assertEqual(self.env.read_text(), original)
        self.assertNotIn(secret, self.output.getvalue())

    def test_cli_internal_smtp_wizard_writes_secret_literally_and_preserves_other_settings(self) -> None:
        self.existing(MAIL_ENABLED="false", SMTP_PASSWORD="SecretoAnteriorExterno")
        secret = "Relay $ # ! clave con espacios"
        values = self.wizard(["192.168.1.50", "smtp.example.test", "soporte@example.test", "relay.example.test", "587", "STARTTLS", "soporte@example.test"],
                             [secret, secret], via_cli=True)
        self.assertEqual(values["SMTP_RELAY_PASSWORD"], secret)
        self.assertEqual(values["SMTP_RELAY_HOST"], "relay.example.test")
        self.assertEqual(values["MAIL_ENABLED"], "true")
        self.assertEqual(values["SMTP_HOST"], "smtp")
        self.assertEqual(values["SMTP_AUTH"], "false")
        self.assertEqual(values["SMTP_PASSWORD"], "")
        self.assertEqual(values["DB_PASSWORD"], "ClaveBDAnterior123!")
        self.assertEqual(values["DB_PORT"], "5544")
        self.assertIn("# Conservar este comentario", self.env.read_text())
        self.assertIn(f"SMTP_RELAY_PASSWORD='{secret}'", self.env.read_text())
        self.assertEqual(stat.S_IMODE(self.env.stat().st_mode), 0o600)
        self.assertNotIn(secret, self.output.getvalue())
        self.assertEqual(list(self.root.glob(".env-*")), [])

    def test_direct_mode_clears_previous_upstream_credentials(self) -> None:
        self.existing(SMTP_RELAY_HOST="relay.example.test", SMTP_RELAY_USERNAME="user@example.test",
                      SMTP_RELAY_PASSWORD="RelayAnteriorPrivado123!")
        values = self.wizard(["192.168.1.50", "", "", "-"])
        self.assertEqual(values["SMTP_RELAY_HOST"], "")
        self.assertEqual(values["SMTP_RELAY_USERNAME"], "")
        self.assertEqual(values["SMTP_RELAY_PASSWORD"], "")
        self.assertNotIn("RelayAnteriorPrivado123!", self.env.read_text())

    def test_password_mismatch_and_insecure_authenticated_relay_leave_file_unchanged(self) -> None:
        for security, passwords in (("STARTTLS", ["PrimeraClave123!", "OtraClave456!"]),
                                    ("NONE", ["ClaveConservada123!", "ClaveConservada123!"])):
            with self.subTest(security=security):
                original = self.existing()
                with self.assertRaises(RuntimeError):
                    self.wizard(["192.168.1.50", "smtp.example.test", "soporte@example.test", "relay.example.test", "587", security, "soporte@example.test"], passwords)
                self.assertEqual(self.env.read_text(), original)

    def test_noninteractive_wizard_refuses_without_modifying_file(self) -> None:
        original = self.existing()
        values = manage.config()
        with mock.patch.object(manage.sys.stdin, "isatty", return_value=False), self.assertRaises(RuntimeError):
            manage.set_internal_smtp_configuration(values)
        self.assertEqual(self.env.read_text(), original)

    def test_local_ip_wizard_derives_literal_sender_and_preserves_custom_sender(self) -> None:
        for existing_sender, expected in (("soporte@[127.0.0.1]", "soporte@[192.168.1.50]"),
                                          ("soporte@institucion.example", "soporte@institucion.example")):
            with self.subTest(existing_sender=existing_sender):
                self.existing(MAIL_FROM=existing_sender)
                values = self.wizard(["192.168.1.50", "", "", ""])
                self.assertEqual(values["SMTP_LOCAL_IP"], "192.168.1.50")
                self.assertEqual(values["MAIL_FROM"], expected)
                self.assertEqual(manage.config()["MAIL_FROM"], expected)

    def test_sender_literal_and_relay_trailing_whitespace_are_validated(self) -> None:
        self.existing(MAIL_FROM="soporte@[192.168.1.50]")
        self.assertEqual(manage.config()["MAIL_FROM"], "soporte@[192.168.1.50]")
        for invalid_sender in ("soporte@[999.168.1.50]", "soporte@[::1]", "soporte@[192.168.1.50/24]"):
            with self.subTest(sender=invalid_sender):
                self.existing(MAIL_FROM=invalid_sender)
                with self.assertRaises(RuntimeError):
                    manage.config()
        secret = "ClaveRelayNoReal123! "
        self.existing(SMTP_RELAY_HOST="relay.example.test", SMTP_RELAY_USERNAME="user@example.test", SMTP_RELAY_PASSWORD=secret)
        with self.assertRaises(RuntimeError) as raised:
            manage.config()
        self.assertNotIn(secret, str(raised.exception))
        self.assertNotIn(secret, self.output.getvalue())

    def test_native_environment_translates_only_container_hostnames(self) -> None:
        values = dict(manage.FRESH_DEFAULTS)
        values["SMTP_INTERNAL_PORT"] = "2626"
        values["SMTP_HOST"] = "smtp"
        native = manage.native_environment(values)
        self.assertEqual(native["SMTP_HOST"], "127.0.0.1")
        self.assertEqual(native["SMTP_PORT"], "2626")
        self.assertEqual(native["DB_HOST"], "127.0.0.1")
        self.assertEqual(values["SMTP_HOST"], "smtp")
        values.update(SMTP_HOST="mailpit", SMTP_PORT="1025", MAILPIT_SMTP_PORT="1125")
        native = manage.native_environment(values)
        self.assertEqual((native["SMTP_HOST"], native["SMTP_PORT"]), ("127.0.0.1", "1125"))
        values.update(SMTP_HOST="relay.example.test", SMTP_PORT="465")
        native = manage.native_environment(values)
        self.assertEqual((native["SMTP_HOST"], native["SMTP_PORT"]), ("relay.example.test", "465"))

    def test_legacy_smtp_subnet_is_ignored_without_rewriting_env(self) -> None:
        for subnet in ("172.30.55.0/24", "0.0.0.0/0", "not-a-network"):
            with self.subTest(subnet=subnet):
                original = self.existing(SMTP_NETWORK_SUBNET=subnet)
                self.assertNotIn("SMTP_NETWORK_SUBNET", manage.config())
                self.assertEqual(self.env.read_text(), original)
        self.assertNotIn("SMTP_NETWORK_SUBNET", manage.FRESH_DEFAULTS)

    def test_control_injection_and_conflicting_ports_are_rejected(self) -> None:
        for overrides in (
            {"SMTP_INTERNAL_PORT": "5544"},
            {"SMTP_INTERNAL_PORT": "0"}, {"SMTP_RELAY_PORT": "65536"},
            {"SMTP_LOCAL_IP": "0.0.0.0"}, {"SMTP_LOCAL_IP": "224.1.2.3"}, {"SMTP_LOCAL_IP": "[192.168.1.50]"},
            {"SMTP_HOSTNAME": "bad.example.test-"}, {"SMTP_RELAY_HOST": "relay..example.test"},
        ):
            with self.subTest(overrides=overrides):
                self.existing(**overrides)
                with self.assertRaises(RuntimeError):
                    manage.config()
        values = dict(manage.DEFAULTS)
        values["SMTP_HOSTNAME"] = "mail.example.test\r\ninjected=value"
        with self.assertRaises(RuntimeError):
            manage.validate_smtp_server(values)

    def test_cli_mail_options_are_mutually_exclusive(self) -> None:
        original = self.existing()
        with mock.patch.object(sys, "argv", ["manage.py", "config", "--correo", "--smtp-interno"]), \
             contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as raised:
            manage.main()
        self.assertEqual(raised.exception.code, 2)
        self.assertEqual(self.env.read_text(), original)


if __name__ == "__main__":
    unittest.main()
