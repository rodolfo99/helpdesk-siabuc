import importlib.util
from pathlib import Path
import smtplib
import tempfile
import unittest
from unittest.mock import patch, MagicMock

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('gmail', ROOT / 'configurar-gmail.py')
gmail = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gmail)

class GmailTests(unittest.TestCase):
    def test_settings_and_grouped_password(self):
        values = gmail.settings('usuario@ucol.mx', 'abcd efgh ijkl mnop')
        self.assertEqual(values['SMTP_RELAY_PASSWORD'], 'abcdefghijklmnop')
        self.assertEqual(values['SMTP_RELAY_PORT'], '587')
        self.assertEqual(values['MAIL_FROM'], 'usuario@ucol.mx')
        self.assertEqual(values['SMTP_HOST'], 'smtp')
        self.assertNotIn('DB_PASSWORD', values)

    def test_reject_invalid_input(self):
        for account, password in [('x@hotmail.com', 'abcdefghijklmnop'), ('x@ucol.mx\nBcc:x', 'abcdefghijklmnop'), ('x@ucol.mx', 'normal')]:
            with self.assertRaises(RuntimeError):
                gmail.settings(account, password)

    def test_tls_before_auth_no_message(self):
        with patch.object(gmail.smtplib, 'SMTP') as smtp:
            gmail.authenticate('x@ucol.mx', 'abcdefghijklmnop')
            client = smtp.return_value.__enter__.return_value
            self.assertEqual([c[0] for c in client.method_calls], ['ehlo', 'starttls', 'ehlo', 'login'])
            context = client.starttls.call_args.kwargs['context']
            self.assertTrue(context.check_hostname)

    def test_auth_error_redacted(self):
        with patch.object(gmail.smtplib, 'SMTP', side_effect=smtplib.SMTPAuthenticationError(535, b'private-secret')):
            with self.assertRaises(RuntimeError) as result:
                gmail.authenticate('x@ucol.mx', 'abcdefghijklmnop')
            self.assertNotIn('private-secret', str(result.exception))

    def test_save_preserves_config_and_failed_auth_preserves_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / '.env'
            original = '# comentario\nDB_PASSWORD=conservar123456\nSERVER_PORT=8081\nMAIL_FROM=viejo@ucol.mx\n'
            path.write_text(original)
            values = gmail.settings('nuevo@ucol.mx', 'abcdefghijklmnop')
            with patch.object(gmail.manage, 'ENV_FILE', path):
                with patch.object(gmail, 'authenticate', side_effect=RuntimeError('rechazado')):
                    with self.assertRaises(RuntimeError):
                        gmail.apply(values, original)
                self.assertEqual(path.read_text(), original)
                with patch.object(gmail, 'authenticate'):
                    gmail.apply(values, original)
                self.assertIn('SERVER_PORT=8081\n', path.read_text())
                self.assertIn('DB_PASSWORD=conservar123456\n', path.read_text())
                self.assertIn('MAIL_FROM=nuevo@ucol.mx\n', path.read_text())
                self.assertEqual(path.stat().st_mode & 0o777, 0o600)

    def test_sudo_fallback(self):
        with patch.object(gmail.shutil, 'which', return_value='/bin/tool'), patch.object(gmail.subprocess, 'run') as run:
            run.return_value = MagicMock(returncode=1)
            self.assertEqual(gmail.docker_prefix(), ['sudo', 'docker'])
            self.assertEqual(run.call_args_list[1].args[0], ['sudo', '-v'])

if __name__ == '__main__':
    unittest.main()
