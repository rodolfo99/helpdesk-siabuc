#!/usr/bin/env python3
"""Empaqueta fuentes completas de SIABUC Helpdesk 1.8 sin datos locales."""
from __future__ import annotations
import hashlib
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NAME = "SIABUC-Helpdesk-1.8.1.zip"
EXCLUDE_DIRS = {".git", ".github-cache", ".venv", "node_modules", "target", "dist",
                "__pycache__", ".angular", ".runtime", "backups", "smtp_queue"}
EXCLUDE_FILES = {".env", "MANIFEST-SHA256.txt", NAME}
EXCLUDE_SUFFIXES = {".pyc", ".p12", ".pfx", ".pem", ".key", ".dump", ".sql.gz"}

def include(path: Path) -> bool:
    rel = path.relative_to(ROOT)
    if any(part in EXCLUDE_DIRS for part in rel.parts):
        return False
    if path.is_symlink() or not path.is_file() or rel.name in EXCLUDE_FILES:
        return False
    if rel.name in {".npmrc", ".pypirc", "id_rsa", "id_ed25519"}:
        return False
    if path.suffix.lower() in EXCLUDE_SUFFIXES:
        return False
    if rel.name.endswith((".local", ".bak", ".log", ".db")):
        return False
    return not (rel.name.startswith(".env.") and rel.name != ".env.example")

def package() -> Path:
    if (ROOT / "VERSION").read_text(encoding="utf-8").strip() != "1.8.1":
        raise ValueError("VERSION no corresponde a 1.8.1.")
    files = sorted((p for p in ROOT.rglob("*") if include(p)),
                   key=lambda p: p.relative_to(ROOT).as_posix())
    if not files:
        raise ValueError("No hay archivos para el paquete.")
    target = ROOT / NAME
    manifest = []
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED,
                         compresslevel=9, strict_timestamps=False) as archive:
        for path in files:
            rel = path.relative_to(ROOT).as_posix()
            data = path.read_bytes()
            if rel.startswith(("backend/src/main/resources/", "frontend/src/")):
                # No filtrar contenido de código por coincidencias textuales:
                # esas palabras son habituales en implementaciones de seguridad.
                pass
            manifest.append(f"{hashlib.sha256(data).hexdigest()}  {rel}")
            archive.writestr(f"siabuc-helpdesk/{rel}", data)
        archive.writestr("siabuc-helpdesk/MANIFEST-SHA256.txt",
                         "\n".join(manifest) + "\n")
    with zipfile.ZipFile(target) as archive:
        bad = archive.testzip()
        if bad:
            raise RuntimeError(f"CRC inválido: {bad}")
    return target

if __name__ == "__main__":
    result = package()
    print(f"Paquete creado: {result} ({result.stat().st_size} bytes)")
