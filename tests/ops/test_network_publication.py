"""Verificación estática de redes; no arranca contenedores ni modifica datos."""
from pathlib import Path
import importlib.util
import unittest
from unittest import mock
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("siabuc_network_manage_test", ROOT / "scripts/manage.py")
manage = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(manage)


class NetworkPublicationTests(unittest.TestCase):
    def test_only_web_has_ports_in_regular_compose(self):
        text = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
        # No published database/backend/SMTP ports, even for legacy 0.0.0.0.
        for legacy in ("BIND_HOST:-127.0.0.1",):
            self.assertIn(legacy, text)
        self.assertIn('WEB_BIND_HOST:-', text)
        for service in ("db", "backend", "smtp"):
            segment = text.split("\n  " + service + ":\n", 1)[1].split("\n  ", 1)[0]
            self.assertNotIn("\n    ports:", segment)
        frontend = text.split("\n  frontend:\n", 1)[1].split("\n  ", 1)[0]
        self.assertIn("\n    ports:", frontend)
        self.assertIn(":80", frontend)

    def test_dev_ports_are_loopback_only(self):
        overlay = (ROOT / "docker-compose.dev.yml").read_text(encoding="utf-8")
        self.assertIn('"127.0.0.1:${DB_PORT:-5432}:5432"', overlay)
        self.assertIn('"127.0.0.1:${SMTP_INTERNAL_PORT:-2525}:25"', overlay)
        self.assertNotIn("0.0.0.0", overlay)

    def test_legacy_env_unchanged_and_web_override(self):
        with tempfile.TemporaryDirectory() as tmp:
            local = Path(tmp) / ".env"
            original = "BIND_HOST=0.0.0.0\nDB_PASSWORD=TestDatabasePassword123!\nADMIN_PASSWORD=TestAdminPassword123!\n"
            local.write_text(original, encoding="utf-8")
            with mock.patch.object(manage, "ENV_FILE", local):
                values = manage.config()
                self.assertEqual(values["WEB_BIND_HOST"], "0.0.0.0")
                self.assertEqual(local.read_text(encoding="utf-8"), original)
                local.write_text(original + "WEB_BIND_HOST=192.168.1.50\n", encoding="utf-8")
                self.assertEqual(manage.config()["WEB_BIND_HOST"], "192.168.1.50")

    def test_compose_override_selected_for_native_development_only(self):
        values = {"BACKUP_DIRECTORY": "./backups/automatic", "SERVER_PORT": "8080"}
        with mock.patch.object(manage, "require"), mock.patch.object(manage, "run") as run:
            manage.compose(values, "up", "-d", "--wait", "db", "smtp")
            cmd = run.call_args.args[0]
            self.assertIn("docker-compose.dev.yml", " ".join(cmd))
            manage.compose(values, "up", "-d", "--build", "--wait")
            cmd = run.call_args.args[0]
            self.assertNotIn("docker-compose.dev.yml", " ".join(cmd))


if __name__ == "__main__":
    unittest.main()
