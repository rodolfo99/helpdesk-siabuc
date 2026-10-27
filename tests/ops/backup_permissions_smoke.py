"""Real Docker regression in a disposable project; never starts the application or a database."""
import importlib.util
import contextlib
import io
import os
from pathlib import Path
import shutil
import stat
import subprocess
import tempfile
import uuid

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("smoke_manage", ROOT / "scripts/manage.py")
manage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(manage)


def main():
    if os.geteuid() != 0:
        raise RuntimeError("Run this isolated regression with sudo to reproduce a root-owned 0700 directory.")
    with tempfile.TemporaryDirectory(prefix="siabuc-backup-permissions-") as temporary:
        project = Path(temporary)
        shutil.copy2(ROOT / "docker-compose.yml", project / "docker-compose.yml")
        shutil.copytree(ROOT / "backup", project / "backup")
        manage.ROOT, manage.ENV_FILE = project, project / ".env"
        values = {**manage.DEFAULTS,
                  "COMPOSE_PROJECT_NAME": "backup-permissions-" + uuid.uuid4().hex[:12],
                  "DB_PASSWORD": "DisposableDatabase123", "ADMIN_PASSWORD": "DisposableAdmin123",
                  "BACKUP_UID": "1000", "BACKUP_GID": "1000", "SMTP_HOST": "", "MAIL_ENABLED": "false"}
        manage.ENV_FILE.write_text("".join(f"{key}={manage.env_value(value)}\n" for key, value in values.items()))
        manage.ENV_FILE.chmod(0o600)
        destination = manage.prepare_automatic_backup_directory(values)
        original = destination / "historical.dump"
        original.write_bytes(b"historical fixture, not a PostgreSQL dump")
        original.chmod(0o600)
        previous = original.stat()
        assert destination.stat().st_uid == 0
        assert stat.S_IMODE(destination.stat().st_mode) == 0o700
        try:
            manage.verify_automatic_backup_permissions(values)
            assert (destination.stat().st_uid, destination.stat().st_gid) == (1000, 1000)
            assert stat.S_IMODE(destination.stat().st_mode) == 0o700
            second_start = io.StringIO()
            with contextlib.redirect_stdout(second_start):
                manage.verify_automatic_backup_permissions(values)
            assert "Preparando permisos" not in second_start.getvalue(), "second start repaired permissions again"
            assert original.read_bytes() == b"historical fixture, not a PostgreSQL dump"
            current = original.stat()
            assert (current.st_uid, current.st_gid, current.st_mode, current.st_mtime_ns) == (
                previous.st_uid, previous.st_gid, previous.st_mode, previous.st_mtime_ns)
            assert list(destination.iterdir()) == [original], "temporary probe left files behind"
            running = manage.compose(values, "ps", "-a", "-q", capture_output=True, text=True)
            assert not running.stdout.strip(), "preflight started a persistent service"
            print("PASS real Docker: root:0700 -> worker 1000:1000, repeat startup, historical files preserved; no database or SMTP started")
        finally:
            manage.compose(values, "down", "--remove-orphans", stdout=subprocess.DEVNULL)


if __name__ == "__main__":
    main()
