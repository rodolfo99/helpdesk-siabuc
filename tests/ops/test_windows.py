"""Windows branches simulated on Linux; no claim of CMD/Desktop execution."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('windows_manage_test', ROOT / 'scripts/manage.py')
manage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(manage)

class WindowsOperationTests(unittest.TestCase):
    def test_native_development_stops_before_process_operations(self):
        with mock.patch.object(manage, 'IS_WINDOWS', True), mock.patch.object(manage, 'docker_ready') as docker:
            with self.assertRaisesRegex(RuntimeError, 'WSL2'):
                manage.development({})
            docker.assert_not_called()

    def test_accept_linux_engine(self):
        with mock.patch.object(manage, 'IS_WINDOWS', True), mock.patch.object(manage, 'require'), mock.patch.object(manage, 'compose'), mock.patch.object(manage, 'run', return_value=subprocess.CompletedProcess([], 0, 'linux\n')) as run:
            manage.docker_ready({})
            self.assertEqual(run.call_args.args[0], ['docker', 'info', '--format', '{{.OSType}}'])

    def test_reject_windows_engine(self):
        with mock.patch.object(manage, 'IS_WINDOWS', True), mock.patch.object(manage, 'require'), mock.patch.object(manage, 'compose'), mock.patch.object(manage, 'run', return_value=subprocess.CompletedProcess([], 0, 'windows\n')):
            with self.assertRaisesRegex(RuntimeError, 'contenedores Linux'):
                manage.docker_ready({})

    def test_binary_backup_round_trip_with_space_in_path(self):
        payload = b'PGDMP\x00\x1a\xff\r\n\x00\n'
        with tempfile.TemporaryDirectory(prefix='siabuc windows ') as directory:
            target = Path(directory) / 'copia con espacios.dump'
            def dump(values, *args, **kwargs):
                kwargs['stdout'].write(payload)
            with mock.patch.object(manage, 'docker_ready'), mock.patch.object(manage, 'compose', side_effect=dump):
                manage.backup({'DB_USER': 'siabuc', 'DB_NAME': 'helpdesk'}, str(target))
            self.assertEqual(target.read_bytes(), payload)
            self.assertTrue(target.with_suffix('.dump.sha256').exists())
            def restore(values, *args, **kwargs):
                if 'stdin' in kwargs:
                    self.assertEqual(kwargs['stdin'].read(), payload)
            with mock.patch.object(manage, 'RUNTIME', Path(directory)), mock.patch.object(manage, 'IS_WINDOWS', True), mock.patch.object(manage, 'docker_ready'), mock.patch.object(manage, 'compose', side_effect=restore):
                manage.restore({'DB_USER': 'siabuc', 'DB_NAME': 'helpdesk'}, str(target), True)

    def test_compose_preserves_paths_as_arguments(self):
        with mock.patch.object(manage, 'ROOT', Path('/project with spaces')), mock.patch.object(manage, 'ENV_FILE', Path('/project with spaces/.env')), mock.patch.object(manage, 'require'), mock.patch.object(manage, 'run') as run:
            manage.compose({'SERVER_PORT': '8081'}, 'up', '-d')
            args = run.call_args.args[0]
            self.assertEqual(args[args.index('--project-directory')+1], '/project with spaces')
            self.assertEqual(run.call_args.kwargs['env']['SERVER_PORT'], '8081')
            self.assertNotIn('shell', run.call_args.kwargs)

if __name__ == '__main__':
    unittest.main()
