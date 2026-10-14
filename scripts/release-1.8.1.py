#!/usr/bin/env python3
"""Preparación y empaquetado de fuentes 1.8.1, sin acceder a datos operativos."""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import stat
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = '1.8.1'
BASE = 'b73bb839863641448f112c5285591f0503da4126'


def prepare() -> None:
    current = (ROOT / 'VERSION').read_text(encoding='utf-8').strip()
    if current not in {'1.8.0', VERSION}:
        raise RuntimeError('La preparación requiere fuentes 1.8.0 corregidas o 1.8.1.')
    replacements = {
        'backend/pom.xml': [('1.8.0', VERSION)],
        'backend/Dockerfile': [('siabuc-helpdesk-1.7.0.jar', 'siabuc-helpdesk-1.8.1.jar'), ('siabuc-helpdesk-1.8.0.jar', 'siabuc-helpdesk-1.8.1.jar')],
        'scripts/manage.py': [('siabuc-helpdesk-1.8.0.jar', 'siabuc-helpdesk-1.8.1.jar')],
        'scripts/package-1.8.py': [('1.8.0', VERSION)],
        'README.md': [('# SIABUC Helpdesk 1.8.0', '# SIABUC Helpdesk 1.8.1')],
    }
    for name, pairs in replacements.items():
        path = ROOT / name
        text = path.read_text(encoding='utf-8')
        for old, new in pairs:
            text = text.replace(old, new)
        path.write_text(text, encoding='utf-8')
    for name in ('frontend/package.json', 'frontend/package-lock.json'):
        path = ROOT / name
        data = json.loads(path.read_text(encoding='utf-8'))
        data['version'] = VERSION
        if '' in data.get('packages', {}):
            data['packages']['']['version'] = VERSION
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (ROOT / 'VERSION').write_text(VERSION + '\n', encoding='utf-8')
    models = (ROOT / 'frontend/src/app/knowledge-models.ts').read_text(encoding='utf-8')
    linked = (ROOT / 'frontend/src/app/case-knowledge.component.ts').read_text(encoding='utf-8')
    if 'KnowledgeCategory' not in models or 'KNOWLEDGE_CATEGORIES' not in linked:
        raise RuntimeError('Falta la corrección del error TS2345; no se empaquetan fuentes antiguas.')
    if "from './case-profile.component'" in linked:
        raise RuntimeError('Soluciones vinculadas todavía usa categorías de casos.')
    doc = '''# SIABUC Helpdesk 1.8.1 — corrección de compilación y distribución

Esta revisión conserva las funciones y límites de la rama 1.8. No aprueba
requisitos institucionales pendientes ni cambia autenticación, roles, saldos,
SMTP o permisos del portal financiero.

## Correcciones incluidas

- Las soluciones vinculadas usan KnowledgeCategory y KNOWLEDGE_CATEGORIES,
  separados de la clasificación de casos; se corrige el TS2345 comunicado.
- Docker copia el JAR 1.8.1 que genera Maven. Los scripts nativos y los
  metadatos npm/package-lock también utilizan 1.8.1.
- Se conservan V1–V25 sin modificar sus bytes. No hay migración nueva para
  actualizar desde la rama 1.8 corregida.
- ZIP completo de fuentes, con manifiesto SHA-256 y comprobación CRC.

## Uso

Descomprimir en una carpeta nueva. Conservar .env, COMPOSE_PROJECT_NAME,
volúmenes y destino de respaldos de la instalación existente. Ejecutar primero
`bash ./compilar.sh --docker`. Una vez validado el sistema y respaldados los
datos, detener la instalación anterior y ejecutar `bash ./iniciar.sh` desde
la carpeta nueva. No usar `docker compose down -v`.

El .env no contiene la base de datos. Cambiar de computadora requiere también
respaldar y restaurar los datos. No sustituir una instalación productiva por
esta rama sin pruebas de regresión y restauración.

## Alcance de validación

Consultar docs/VALIDACION-1.8.1.json, cuando se incluya en el paquete. Un
resultado de compilación no equivale a validación integral de Docker, PostgreSQL,
Flyway, concurrencia, restauración o entrega SMTP. La evidencia de 1.7 en
VALIDACION.md conserva su fecha y no se presenta como una ejecución de 1.8.1.
'''
    (ROOT / 'docs/VERSION-1.8.1.md').write_text(doc, encoding='utf-8')
    readme = ROOT / 'README.md'
    text = readme.read_text(encoding='utf-8')
    notice = '\n**Revisión 1.8.1:** incluye la corrección de categorías TS2345 y del nombre del JAR Docker. [Guía 1.8.1](docs/VERSION-1.8.1.md). La rama sigue pendiente de validación operativa integral.\n'
    if notice not in text:
        text = text.replace('# SIABUC Helpdesk 1.8.1\n', '# SIABUC Helpdesk 1.8.1\n' + notice, 1)
    readme.write_text(text, encoding='utf-8')
    print('Versiones, correcciones y documentación preparadas: 1.8.1')


def package(output: Path) -> Path:
    if (ROOT / 'VERSION').read_text(encoding='utf-8').strip() != VERSION:
        raise RuntimeError('Ejecute primero --prepare.')
    names = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode('utf-8').split('\0')
    extra = ['docs/VERSION-1.8.1.md', 'docs/VALIDACION-1.8.1.json']
    names = sorted(set(filter(None, names + extra)))
    excluded = {'.git', 'node_modules', 'target', 'dist', 'backups', '.runtime', '__pycache__'}
    files = []
    for name in names:
        rel = Path(name)
        path = ROOT / rel
        if not path.is_file() or path.is_symlink():
            continue
        if any(part in excluded for part in rel.parts):
            continue
        if name == 'MANIFEST-SHA256.txt' or rel.name in {'.env', '.npmrc', '.pypirc'}:
            continue
        if rel.suffix.lower() in {'.zip', '.dump', '.pyc', '.pem', '.key', '.p12', '.pfx'}:
            continue
        if rel.name.startswith('.env.') and rel.name != '.env.example':
            continue
        files.append((name, path.read_bytes()))
    output.mkdir(parents=True, exist_ok=True)
    target = output / ('SIABUC-Helpdesk-' + VERSION + '.zip')
    manifest = '\n'.join(hashlib.sha256(data).hexdigest() + '  ' + name for name, data in files) + '\n'
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in files + [('MANIFEST-SHA256.txt', manifest.encode('utf-8'))]:
            entry = zipfile.ZipInfo('siabuc-helpdesk/' + name, (2026, 10, 14, 0, 0, 0))
            entry.create_system = 3
            mode = 0o755 if name.endswith('.sh') else 0o644
            entry.external_attr = (stat.S_IFREG | mode) << 16
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, data)
    with zipfile.ZipFile(target) as archive:
        if archive.testzip() is not None:
            raise RuntimeError('ZIP con error CRC.')
        for line in manifest.splitlines():
            digest, name = line.split('  ', 1)
            if hashlib.sha256(archive.read('siabuc-helpdesk/' + name)).hexdigest() != digest:
                raise RuntimeError('Manifiesto inconsistente: ' + name)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    target.with_suffix('.zip.sha256').write_text(digest + '  ' + target.name + '\n', encoding='utf-8')
    print(json.dumps({'path': str(target), 'files': len(files), 'bytes': target.stat().st_size, 'sha256': digest}))
    return target


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare', action='store_true')
    parser.add_argument('--package', action='store_true')
    parser.add_argument('--output', type=Path, default=ROOT / 'release-output')
    args = parser.parse_args()
    if not args.prepare and not args.package:
        parser.error('Indique --prepare y/o --package.')
    if args.prepare:
        prepare()
    if args.package:
        package(args.output)
