"""Regression tests for Gmail direct and legacy Postfix profiles. No external email."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("siabuc_mail_modes", ROOT / "scripts/manage.py")
manage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(manage)

class DirectGmailTests(unittest.TestCase):
    def test_new_install_does_not_send_without_credentials(self):
        with tempfile.TemporaryDirectory() as d, mock.patch.object(manage,"ENV_FILE",Path(d)/".env"):
            config=manage.config(create=True)
            self.assertEqual(config["SMTP_HOST"],"smtp.gmail.com")
            self.assertEqual(config["SMTP_PORT"],"587")
            self.assertEqual(config["MAIL_ENABLED"],"false")
            self.assertFalse(config["SMTP_PASSWORD"])

    def test_compose_uses_postfix_profile_only_when_requested(self):
        for host, enabled in (("smtp.gmail.com",False),("smtp",True),("other.example.test",False)):
            with self.subTest(host=host), mock.patch.object(manage,"require"), mock.patch.object(manage,"run") as run:
                with mock.patch.dict(manage.os.environ,{"COMPOSE_PROFILES":""}):
                    manage.compose({"SMTP_HOST":host,"BACKUP_DIRECTORY":"./backups/automatic"},"up","-d")
                env=run.call_args.kwargs["env"]
                self.assertEqual("local-smtp" in env.get("COMPOSE_PROFILES",""),enabled)

    def test_direct_gmail_wizard_preserves_legacy_settings_and_hides_secrets(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/".env"
            original="# original\nDB_PASSWORD=DummyDatabasePassword123!\nADMIN_PASSWORD=DummyAdminPassword123!\nSMTP_HOST=smtp\nSMTP_RELAY_HOST=relay.test\nSMTP_RELAY_PASSWORD=OldSecret123!\n"
            path.write_text(original)
            base=dict(manage.DEFAULTS)
            base.update(DB_PASSWORD="DummyDatabasePassword123!",ADMIN_PASSWORD="DummyAdminPassword123!",
                        SMTP_HOST="smtp",SMTP_RELAY_HOST="relay.test",SMTP_RELAY_PASSWORD="OldSecret123!")
            with mock.patch.object(manage,"ENV_FILE",path),mock.patch.object(manage.sys.stdin,"isatty",return_value=True),mock.patch("builtins.input",return_value="user@example.test"),mock.patch.object(manage.getpass,"getpass",side_effect=["NewAppPassword1234","NewAppPassword1234"]):
                updated=manage.set_direct_gmail_configuration(base)
            self.assertEqual(updated["SMTP_HOST"],"smtp.gmail.com")
            self.assertEqual(updated["SMTP_AUTH"],"true")
            self.assertIn("SMTP_RELAY_PASSWORD=OldSecret123!",path.read_text())
            self.assertIn("DB_PASSWORD=DummyDatabasePassword123!",path.read_text())
            self.assertIn("SMTP_PASSWORD=NewAppPassword1234",path.read_text())

    def test_failed_wizard_leaves_file_untouched(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/".env"
            original="DB_PASSWORD=DummyDatabasePassword123!\n"
            path.write_text(original)
            with mock.patch.object(manage,"ENV_FILE",path),mock.patch.object(manage.sys.stdin,"isatty",return_value=True),mock.patch("builtins.input",return_value="user@example.test"),mock.patch.object(manage.getpass,"getpass",side_effect=["secret111111111","wrong111111111"]):
                with self.assertRaises(RuntimeError):
                    manage.set_direct_gmail_configuration(dict(manage.DEFAULTS))
            self.assertEqual(path.read_text(),original)

if __name__ == "__main__":
    unittest.main()
