#!/usr/bin/env python3
"""Exercise image upgrades on disposable volumes and an internal Docker network.

Uses synthetic records/messages only. No published ports or external delivery.
The workflow supplies the exact pre-upgrade SMTP sources as a build context.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import time
import uuid


def run(*args: str, input: str | None = None) -> str:
    return subprocess.run(args, input=input, text=True, capture_output=True,
                          check=True, timeout=600).stdout.strip()


def wait_for(check, description: str, seconds: int = 180):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            result = check()
            if result:
                return result
        except (subprocess.CalledProcessError, json.JSONDecodeError):
            pass
        time.sleep(2)
    raise AssertionError("Timed out: " + description)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--legacy-smtp", type=Path, required=True)
    args = parser.parse_args()
    prefix = "siabuc-185-" + uuid.uuid4().hex[:10]
    network = prefix + "-net"
    volumes = [prefix + "-data", prefix + "-queue"]
    containers: list[str] = []
    images = {name: prefix + ":" + name for name in ("db", "backup", "backend", "smtp", "legacy")}
    password = "DisposableImage185Password"

    def start(name: str, image: str, *options: str) -> str:
        container = prefix + "-" + name
        containers.append(container)
        run("docker", "run", "-d", "--name", container, "--network", network,
            *options, image)
        return container

    def sql(container: str, statement: str, database: str = "upgrade") -> str:
        return run("docker", "exec", "-e", "PGPASSWORD=" + password, container, "psql", "-h", "127.0.0.1", "-U", "siabuc", "-d", database,
                   "-v", "ON_ERROR_STOP=1", "-Atc", statement)

    def healthy_smtp(container: str) -> bool:
        run("docker", "exec", container, "python3", "/opt/siabuc-smtp/healthcheck.py")
        return True

    def queued(container: str) -> list[dict]:
        return [json.loads(line) for line in run("docker", "exec", container, "postqueue", "-j").splitlines() if line]

    try:
        for name in ("db", "backup", "backend", "smtp"):
            print("Building " + name, flush=True)
            run("docker", "build", "--pull", "-t", images[name], name)
        run("docker", "build", "--pull", "-t", images["legacy"], str(args.legacy_smtp))
        run("docker", "pull", "postgres:17-alpine")
        run("docker", "network", "create", "--internal", network)
        for volume in volumes:
            run("docker", "volume", "create", volume)

        db_options = ("-v", volumes[0] + ":/var/lib/postgresql/data", "-e", "POSTGRES_DB=upgrade",
                      "-e", "POSTGRES_USER=siabuc", "-e", "POSTGRES_PASSWORD=" + password)
        old_db = start("old-db", "postgres:17-alpine", *db_options)
        wait_for(lambda: sql(old_db, "SELECT 1") == "1", "original PostgreSQL startup")
        sql(old_db, "CREATE TABLE upgrade_fixture (id integer PRIMARY KEY, label text NOT NULL); "
                    "INSERT INTO upgrade_fixture VALUES (185, 'conservado-áéí');")
        run("docker", "stop", old_db)
        run("docker", "rm", old_db)
        containers.remove(old_db)
        db = start("db", images["db"], "--network-alias", "db", *db_options)
        wait_for(lambda: sql(db, "SELECT label FROM upgrade_fixture WHERE id=185") == "conservado-áéí",
                 "data preserved after replacing PostgreSQL image")
        assert sql(db, "SHOW server_version").startswith("17.")
        assert run("docker", "exec", db, "gosu", "postgres", "id", "-u") == "70"

        # Exercise the actual backup runtime's pg_dump/pg_restore over the network.
        sql(db, "CREATE DATABASE restored")
        run("docker", "run", "--rm", "--network", network, "--entrypoint", "sh",
            "-e", "PGPASSWORD=" + password, images["backup"], "-ec",
            "pg_dump -h db -U siabuc -d upgrade -Fc -f /tmp/fixture.dump; "
            "pg_restore -h db -U siabuc -d restored --exit-on-error /tmp/fixture.dump")
        assert sql(db, "SELECT label FROM upgrade_fixture WHERE id=185", "restored") == "conservado-áéí"
        print("PASS: PostgreSQL 17 volume upgrade and backup/restore", flush=True)

        # Start the final non-root, read-only backend image and run Flyway V1–V27.
        sql(db, "CREATE DATABASE application")
        backend = start("backend", images["backend"], "--network-alias", "backend",
                        "--read-only", "--tmpfs", "/tmp", "--security-opt", "no-new-privileges:true",
                        "-e", "DB_HOST=db", "-e", "DB_NAME=application", "-e", "DB_USER=siabuc",
                        "-e", "DB_PASSWORD=" + password, "-e", "ADMIN_PASSWORD=" + password,
                        "-e", "MAIL_ENABLED=false")
        wait_for(lambda: run("docker", "exec", backend, "wget", "-qO-",
                             "http://127.0.0.1:8080/api/health"), "final backend health")
        assert sql(db, "SELECT count(*) FROM flyway_schema_history WHERE success", "application") == "27"
        print("PASS: final backend image startup and Flyway migrations", flush=True)

        smtp_options = ("-v", volumes[1] + ":/var/spool/postfix", "-e", "SMTP_RELAY_HOST=delivery.invalid",
                        "-e", "SMTP_RELAY_PORT=2525", "-e", "SMTP_RELAY_SECURITY=NONE")
        old_smtp = start("old-smtp", images["legacy"], *smtp_options)
        wait_for(lambda: healthy_smtp(old_smtp), "original SMTP health")
        run("docker", "exec", "-i", old_smtp, "python3", "-", input=r"""
import smtplib
with smtplib.SMTP('127.0.0.1', 25, timeout=10) as s:
    s.sendmail('sender@example.test', ['recipient@example.test'],
               'From: sender@example.test\r\nTo: recipient@example.test\r\n'
               'Subject: SIABUC upgrade fixture\r\n\r\nqueue-preserved-185\r\n')
"""
        )
        before = wait_for(lambda: queued(old_smtp), "synthetic SMTP queue record")
        queue_id = before[0]["queue_id"]
        run("docker", "stop", old_smtp)
        run("docker", "rm", old_smtp)
        containers.remove(old_smtp)
        smtp = start("smtp", images["smtp"], *smtp_options)
        wait_for(lambda: healthy_smtp(smtp), "updated SMTP health with existing queue")
        assert queue_id in {item["queue_id"] for item in queued(smtp)}
        assert "queue-preserved-185" in run("docker", "exec", smtp, "postcat", "-q", queue_id)
        run("docker", "exec", smtp, "postfix", "check")
        # Use Postfix's real LMDB implementation, private map permissions and SASL.
        run("docker", "exec", "-i", smtp, "python3", "-", input=r"""
import os, stat, subprocess, sys
from pathlib import Path
sys.path.insert(0, '/opt/siabuc-smtp')
import entrypoint as e
config=e.parse_configuration({'SMTP_RELAY_HOST':'relay.example.test',
    'SMTP_RELAY_USERNAME':'test-user','SMTP_RELAY_PASSWORD':'disposable-test-password'})
available=subprocess.check_output(['postconf','-m'],text=True)
kind=e.choose_map_type(available)
assert kind == 'lmdb', available.split()
e.write_sasl_map(config, Path('/etc/postfix'), kind)
assert subprocess.check_output(['postmap','-q',config.relay_destination,
    'lmdb:/etc/postfix/sasl_passwd'],text=True).strip() == 'test-user:disposable-test-password'
for path in ('/etc/postfix/sasl_passwd','/etc/postfix/sasl_passwd.lmdb'):
    assert stat.S_IMODE(os.stat(path).st_mode) == 0o600
assert 'cyrus' in subprocess.check_output(['postconf','-A'],text=True).split()
"""
        )
        print("PASS: Debian-to-Alpine SMTP queue, health, LMDB credentials and SASL", flush=True)
    except subprocess.CalledProcessError as error:
        print(error.stdout or "")
        print(error.stderr or "")
        raise
    finally:
        for container in containers:
            subprocess.run(["docker", "logs", "--tail", "100", container], check=False)
            subprocess.run(["docker", "rm", "-f", container], check=False)
        for volume in volumes:
            subprocess.run(["docker", "volume", "rm", volume], check=False)
        subprocess.run(["docker", "network", "rm", network], check=False)


if __name__ == "__main__":
    main()
