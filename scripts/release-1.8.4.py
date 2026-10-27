#!/usr/bin/env python3
"""Empaqueta los fuentes versionados 1.8.4 con manifiesto y CRC, sin datos locales."""
from __future__ import annotations
import argparse
import hashlib
import json
import stat
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = '1.8.4'


def package(output: Path) -> Path:
    if (ROOT / 'VERSION').read_text().strip() != VERSION:
        raise RuntimeError('Los fuentes no corresponden a 1.8.4.')
    for name in ('frontend/package.json', 'frontend/package-lock.json'):
        if json.loads((ROOT / name).read_text())['version'] != VERSION:
            raise RuntimeError('Versión npm inconsistente: ' + name)
    names = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
    excluded = {'.git', 'node_modules', 'target', 'dist', 'backups', '.runtime', '__pycache__'}
    files = []
    for name in sorted(filter(None, names)):
        rel, path = Path(name), ROOT / name
        if not path.is_file() or path.is_symlink() or any(p in excluded for p in rel.parts):
            continue
        if rel.name in {'.env', '.npmrc', '.pypirc', 'MANIFEST-SHA256.txt'}:
            continue
        if rel.name.startswith('.env.') and rel.name not in {'.env.example', '.env.gmail.example'}:
            continue
        if rel.suffix.lower() in {'.zip', '.dump', '.pyc', '.pem', '.key', '.p12', '.pfx', '.log'}:
            continue
        files.append((name, path.read_bytes()))
    required = {'VERSION', 'docs/VERSION-1.8.4.md', 'frontend/src/app/case-status.ts',
                'backend/src/main/resources/db/migration/V27__license_support_balances.sql',
                'scripts/manage.py', 'tests/ops/backup_permissions_smoke.py'}
    if not required.issubset(name for name, _ in files):
        raise RuntimeError('Faltan fuentes 1.8.4 versionados en Git.')
    manifest = '\n'.join(hashlib.sha256(data).hexdigest() + '  ' + name for name, data in files) + '\n'
    output.mkdir(parents=True, exist_ok=True)
    target = output / ('SIABUC-Helpdesk-' + VERSION + '.zip')
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in files + [('MANIFEST-SHA256.txt', manifest.encode())]:
            entry = zipfile.ZipInfo('siabuc-helpdesk/' + name, (2026, 10, 26, 0, 0, 0))
            entry.create_system = 3
            entry.external_attr = (stat.S_IFREG | (0o755 if name.endswith('.sh') else 0o644)) << 16
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, data)
    with zipfile.ZipFile(target) as archive:
        if archive.testzip() is not None:
            raise RuntimeError('Error CRC en ZIP.')
        for name, data in files:
            if archive.read('siabuc-helpdesk/' + name) != data:
                raise RuntimeError('Contenido inconsistente: ' + name)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    target.with_suffix('.zip.sha256').write_text(digest + '  ' + target.name + '\n')
    print(json.dumps({'path': str(target), 'files': len(files), 'sha256': digest}))
    return target


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', action='store_true', required=True)
    parser.add_argument('--output', type=Path, default=ROOT / 'release-output')
    args = parser.parse_args()
    package(args.output)
