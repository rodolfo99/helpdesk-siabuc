#!/usr/bin/env python3
"""Operación local sin ejecutar el contenido de .env como código shell."""
from __future__ import annotations

import argparse
import datetime as dt
import getpass
import hashlib
import ipaddress
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

IS_WINDOWS = os.name == "nt"

ROOT = Path(__file__).resolve().parents[1]
ENV_FILE = ROOT / ".env"
RUNTIME = ROOT / ".runtime"
DEFAULTS = {
    "COMPOSE_PROJECT_NAME": "siabuc-helpdesk",
    "BIND_HOST": "127.0.0.1",
    "WEB_BIND_HOST": "",
    "DB_PORT": "5432",
    "SERVER_PORT": "8080",
    "FRONTEND_PORT": "4200",
    "DB_NAME": "siabuc_helpdesk",
    "DB_USER": "siabuc",
    "ADMIN_USERNAME": "admin",
    "COOKIE_SECURE": "false",
    "REPORTING_TIME_ZONE": "America/Mexico_City",
    "BACKUP_DIRECTORY": "./backups/automatic",
    "BACKUP_UID": str(os.getuid()) if hasattr(os, "getuid") else "0",
    "BACKUP_GID": str(os.getgid()) if hasattr(os, "getgid") else "0",
    "ADMIN_EMAIL": "",
    "MAIL_ENABLED": "false",
    "SMTP_HOST": "",
    "SMTP_PORT": "587",
    "SMTP_USERNAME": "",
    "SMTP_PASSWORD": "",
    "SMTP_AUTH": "true",
    "SMTP_SECURITY": "STARTTLS",
    "MAIL_FROM": "",
    "MAIL_FROM_NAME": "SIABUC",
    "MAIL_REPLY_TO": "",
    "MAIL_POLL_MS": "15000",
    "MAIL_MAX_ATTEMPTS": "5",
    "MAILPIT_SMTP_PORT": "1025",
    "MAILPIT_UI_PORT": "8025",
    "SMTP_INTERNAL_PORT": "2525",
    "SMTP_HOSTNAME": "mail.siabuc.internal",
    "SMTP_LOCAL_IP": "127.0.0.1",
    "SMTP_RELAY_HOST": "",
    "SMTP_RELAY_PORT": "587",
    "SMTP_RELAY_SECURITY": "STARTTLS",
    "SMTP_RELAY_USERNAME": "",
    "SMTP_RELAY_PASSWORD": "",
}
FRESH_DEFAULTS = {
    **DEFAULTS,
    "MAIL_ENABLED": "false",
    "SMTP_HOST": "smtp.gmail.com",
    "SMTP_PORT": "587",
    "SMTP_AUTH": "true",
    "SMTP_SECURITY": "STARTTLS",
    "MAIL_FROM": "",
    "MAIL_FROM_NAME": "Soporte SIABUC",
}
# Accept obsolete keys in existing .env files without applying or rewriting them.
LEGACY_IGNORED_KEYS = {"SMTP_NETWORK_SUBNET"}
ALLOWED_KEYS = set(DEFAULTS) | {"DB_PASSWORD", "ADMIN_PASSWORD"} | LEGACY_IGNORED_KEYS
ENV_VALUE_PATTERN = r"[A-Za-z0-9_./:@%+=,!\-]+"


def env_value(value: str) -> str:
    """Literal dotenv syntax; never interpolate or evaluate shell expressions."""
    if any(ord(char) < 32 or ord(char) == 127 for char in value) or "'" in value or value.endswith("\\"):
        fail("Un valor de .env no admite controles, comilla simple ni barra inversa al final.")
    return value if not value or re.fullmatch(ENV_VALUE_PATTERN, value) else "'" + value + "'"


def parse_env_value(raw: str, number: int) -> str:
    if not raw or re.fullmatch(ENV_VALUE_PATTERN, raw):
        return raw
    if len(raw) >= 2 and raw.startswith("'") and raw.endswith("'"):
        value = raw[1:-1]
        env_value(value)
        return value
    fail(f".env línea {number}: use KEY=valor o KEY='valor literal', sin ejecutar expresiones shell.")


def valid_email_address(value: str) -> bool:
    if len(value) > 254 or value.count("@") != 1:
        return False
    local, domain = value.rsplit("@", 1)
    if not re.fullmatch(r"[^\s<>@,;]+", local):
        return False
    if domain.startswith("[") or domain.endswith("]"):
        if not (domain.startswith("[") and domain.endswith("]")):
            return False
        try:
            ipaddress.IPv4Address(domain[1:-1])
            return True
        except ValueError:
            return False
    return bool(re.fullmatch(r"[^\s<>@,;\[\]]+\.[^\s<>@,;\[\]]+", domain))


def validate_mail(values: dict[str, str]) -> None:
    for key in ("MAIL_ENABLED", "SMTP_AUTH"):
        if values[key] not in {"true", "false"}:
            fail(f"{key} debe ser true o false.")
    if values["SMTP_SECURITY"] not in {"STARTTLS", "SSL", "NONE"}:
        fail("SMTP_SECURITY debe ser STARTTLS, SSL o NONE.")
    for key, minimum, maximum in (("SMTP_PORT", 1, 65535), ("MAIL_POLL_MS", 1000, 3600000),
                                  ("MAIL_MAX_ATTEMPTS", 1, 5)):
        value = values[key]
        if not value.isdecimal() or not minimum <= int(value) <= maximum:
            fail(f"{key} debe estar entre {minimum} y {maximum}.")
    if values["SMTP_HOST"] and (len(values["SMTP_HOST"]) > 253 or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.\-]*", values["SMTP_HOST"])):
        fail("SMTP_HOST debe ser un nombre de servidor o IPv4, sin protocolo ni puerto.")
    for key in ("ADMIN_EMAIL", "MAIL_FROM", "MAIL_REPLY_TO"):
        value = values[key]
        if value and not valid_email_address(value):
            fail(f"{key} debe ser una dirección de correo individual válida o quedar vacío.")
    for key, limit in (("MAIL_FROM_NAME", 200), ("SMTP_USERNAME", 320), ("SMTP_PASSWORD", 4096)):
        if len(values[key]) > limit:
            fail(f"{key} excede el máximo de {limit} caracteres.")
        env_value(values[key])
    if values["MAIL_ENABLED"] == "true":
        if not values["SMTP_HOST"] or not values["MAIL_FROM"]:
            fail("Para activar correo configure SMTP_HOST y MAIL_FROM mediante ./configurar.sh --correo.")
        if values["SMTP_AUTH"] == "true" and (not values["SMTP_USERNAME"] or not values["SMTP_PASSWORD"]):
            fail("SMTP_AUTH=true requiere SMTP_USERNAME y SMTP_PASSWORD cuando se activa correo.")


def validate_smtp_server(values: dict[str, str]) -> None:
    try:
        local_ip = ipaddress.IPv4Address(values["SMTP_LOCAL_IP"])
    except ValueError:
        fail("SMTP_LOCAL_IP debe ser una IPv4 del equipo anfitrión, sin corchetes.")
    if local_ip.is_unspecified or local_ip.is_multicast or str(local_ip) == "255.255.255.255":
        fail("SMTP_LOCAL_IP debe ser IPv4 unicast; no se admiten 0.0.0.0, broadcast ni multicast.")
    hostname = values["SMTP_HOSTNAME"]
    def valid_hostname(value: str, fully_qualified: bool = False) -> bool:
        labels = value.split(".")
        return bool(value) and len(value) <= 253 and (not fully_qualified or (len(labels) >= 2 and re.search(r"[A-Za-z]", labels[-1]))) and all(
            re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?", label) for label in labels)
    if not valid_hostname(hostname, fully_qualified=True):
        fail("SMTP_HOSTNAME debe ser un nombre técnico completo, por ejemplo mail.siabuc.internal, independiente de la IP remitente.")
    host = values["SMTP_RELAY_HOST"]
    if host and not valid_hostname(host):
        fail("SMTP_RELAY_HOST debe ser un servidor o IPv4 sin protocolo ni puerto.")
    port = values["SMTP_RELAY_PORT"]
    if not port.isdecimal() or not 1 <= int(port) <= 65535:
        fail("SMTP_RELAY_PORT debe estar entre 1 y 65535.")
    security = values["SMTP_RELAY_SECURITY"]
    if security not in {"STARTTLS", "SSL", "NONE"}:
        fail("SMTP_RELAY_SECURITY debe ser STARTTLS, SSL o NONE.")
    username, password = values["SMTP_RELAY_USERNAME"], values["SMTP_RELAY_PASSWORD"]
    if password and password != password.rstrip():
        fail("SMTP_RELAY_PASSWORD no puede terminar en espacios u otros blancos porque Postfix los recorta; no se modificó el secreto.")
    if re.search(r"[:\s]", username):
        fail("SMTP_RELAY_USERNAME no admite dos puntos, espacios ni caracteres de control.")
    if bool(username) != bool(password):
        fail("SMTP_RELAY_USERNAME y SMTP_RELAY_PASSWORD deben definirse juntos o quedar ambos vacíos.")
    if username and (not host or security == "NONE"):
        fail("La autenticación del relay requiere servidor y TLS: STARTTLS o SSL; no se admite NONE.")
    for key, limit in (("SMTP_RELAY_USERNAME", 320), ("SMTP_RELAY_PASSWORD", 4096)):
        if len(values[key]) > limit:
            fail(f"{key} excede el máximo de {limit} caracteres.")
        env_value(values[key])


def fail(message: str) -> None:
    raise RuntimeError(message)


def require(*names: str) -> None:
    missing = [name for name in names if not shutil.which(name)]
    if missing:
        fail("Faltan herramientas: " + ", ".join(missing) + ". Consulte README.md.")


def run(command: list[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(command, check=True, **kwargs)


def digest_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def config(create: bool = False, prompt_db_password: bool = False, prompt_mail: bool = False,
           prompt_smtp: bool = False) -> dict[str, str]:
    if not ENV_FILE.exists():
        if not create:
            fail("No existe .env. Ejecute ./configurar.sh o ./iniciar.sh primero.")
        values = dict(FRESH_DEFAULTS)
        values["DB_PASSWORD"] = secrets.token_hex(24)
        values["ADMIN_PASSWORD"] = secrets.token_urlsafe(24)
        descriptor = os.open(ENV_FILE, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            handle.write("# Configuración local. No compartir ni subir al repositorio.\n")
            handle.writelines(f"{key}={env_value(value)}\n" for key, value in values.items())
        print("Configuración creada en .env con contraseñas aleatorias. Proteja el archivo con los permisos de su usuario (600 en Linux; ACL en Windows).", flush=True)
    os.chmod(ENV_FILE, 0o600)
    # Never activate mail or replace providers on an existing 1.0/1.1 installation.
    values = dict(DEFAULTS)
    seen = set()
    for number, raw in enumerate(ENV_FILE.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"([A-Z][A-Z0-9_]*)=(.*)", line)
        if not match:
            fail(f".env línea {number}: use KEY=valor o KEY='valor literal'.")
        key, raw_value = match.groups()
        value = parse_env_value(raw_value, number)
        if key not in ALLOWED_KEYS:
            fail(f".env línea {number}: variable no admitida: {key}.")
        if key in seen:
            fail(f".env: variable duplicada: {key}.")
        seen.add(key)
        if key in LEGACY_IGNORED_KEYS:
            continue
        values[key] = value
    for key in ("DB_PASSWORD", "ADMIN_PASSWORD"):
        if key == "DB_PASSWORD" and prompt_db_password:
            continue
        value = values.get(key, "")
        if len(value) < 12 or value.startswith("CAMBIAR_"):
            fail(f"Configure {key} con una contraseña propia de al menos 12 caracteres.")
    if len(values["ADMIN_PASSWORD"].encode("utf-8")) > 72:
        fail("ADMIN_PASSWORD debe ocupar como máximo 72 bytes.")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._@-]{2,79}", values["ADMIN_USERNAME"]):
        fail("ADMIN_USERNAME debe tener entre 3 y 80 caracteres: letras, números, punto, guion, arroba o guion bajo.")
    for key in ("DB_NAME", "DB_USER"):
        if not re.fullmatch(r"[a-z_][a-z0-9_]{0,62}", values[key]):
            fail(f"{key}: use letras minúsculas, números y guion bajo; no inicie con número.")
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", values["COMPOSE_PROJECT_NAME"]):
        fail("COMPOSE_PROJECT_NAME debe usar minúsculas, números, - o _.")
    if values["BIND_HOST"] not in {"127.0.0.1", "0.0.0.0"}:
        fail("BIND_HOST heredado debe ser 127.0.0.1 o 0.0.0.0.")
    web_bind = values["WEB_BIND_HOST"] or values["BIND_HOST"]
    try:
        web_ip = ipaddress.IPv4Address(web_bind)
    except ValueError:
        fail("WEB_BIND_HOST debe ser IPv4, por ejemplo 127.0.0.1, 192.168.1.50 o 0.0.0.0.")
    if web_ip.is_multicast or web_ip.is_reserved or (web_ip.is_unspecified and web_bind != "0.0.0.0"):
        fail("WEB_BIND_HOST debe ser una IPv4 unicast o 0.0.0.0.")
    values["WEB_BIND_HOST"] = web_bind
    ports = []
    for key in ("DB_PORT", "SERVER_PORT", "FRONTEND_PORT", "MAILPIT_SMTP_PORT", "MAILPIT_UI_PORT", "SMTP_INTERNAL_PORT"):
        value = values[key]
        if not value.isdecimal() or not 1 <= int(value) <= 65535:
            fail(f"{key} debe ser un número entre 1 y 65535.")
        ports.append(int(value))
    if len(set(ports)) != len(ports):
        fail("Los seis puertos publicados de PostgreSQL, Spring, Angular, SMTP interno y Mailpit deben ser diferentes.")
    if values["COOKIE_SECURE"] not in {"true", "false"}:
        fail("COOKIE_SECURE debe ser true o false.")
    if not values["BACKUP_DIRECTORY"].strip() or len(values["BACKUP_DIRECTORY"]) > 1000:
        fail("BACKUP_DIRECTORY debe indicar una carpeta privada de hasta 1000 caracteres.")
    for key in ("BACKUP_UID", "BACKUP_GID"):
        if not values[key].isdecimal() or not 0 <= int(values[key]) <= 2147483647:
            fail(f"{key} debe ser un identificador numérico de usuario/grupo válido.")
    if not prompt_mail and not prompt_smtp:
        validate_mail(values)
    if not prompt_smtp:
        validate_smtp_server(values)
    return values


def save_config_values(updates: dict[str, str], original: str | None = None) -> None:
    """Atomically change selected values, preserving unrelated keys and comments."""
    original = ENV_FILE.read_text(encoding="utf-8") if original is None else original
    lines = original.splitlines(keepends=True)
    remaining = dict(updates)
    for index, line in enumerate(lines):
        key = line.strip().split("=", 1)[0]
        if key in remaining:
            lines[index] = f"{key}={env_value(remaining.pop(key))}\n"
    if lines and not lines[-1].endswith("\n"):
        lines[-1] += "\n"
    lines.extend(f"{key}={env_value(value)}\n" for key, value in remaining.items())
    descriptor, filename = tempfile.mkstemp(prefix=".env-", dir=ENV_FILE.parent)
    temporary = Path(filename)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            handle.writelines(lines)
            handle.flush()
            os.fsync(handle.fileno())
        if ENV_FILE.read_text(encoding="utf-8") != original:
            fail(".env cambió durante la configuración; repita para conservar esos cambios.")
        os.replace(temporary, ENV_FILE)
    finally:
        temporary.unlink(missing_ok=True)


def set_mail_configuration(values: dict[str, str]) -> dict[str, str]:
    if not sys.stdin.isatty():
        fail("Use ./configurar.sh --correo desde una terminal interactiva.")
    original = ENV_FILE.read_text(encoding="utf-8")
    updated = dict(values)
    print("Configuración SMTP. No se envía ningún mensaje al guardar. Enter conserva el valor; '-' vacía un campo.")

    def ask(key: str, label: str, normalize=None) -> None:
        current = updated[key]
        value = input(f"{label} [{current}]: ").strip()
        if value:
            updated[key] = "" if value == "-" else (normalize(value) if normalize else value)
        env_value(updated[key])

    ask("MAIL_ENABLED", "Activar envío al reiniciar (true/false)", str.lower)
    ask("SMTP_HOST", "Servidor SMTP, sin protocolo")
    ask("SMTP_PORT", "Puerto SMTP")
    ask("SMTP_SECURITY", "Seguridad (STARTTLS/SSL/NONE)", str.upper)
    if updated["SMTP_SECURITY"] == "NONE":
        print("NONE se reserva para Mailpit local o un relay autorizado por su administrador.")
    ask("SMTP_AUTH", "Autenticación SMTP (true/false)", str.lower)
    if updated["SMTP_AUTH"] == "true":
        ask("SMTP_USERNAME", "Usuario SMTP")
        password = getpass.getpass("Contraseña SMTP (oculta; Enter conserva la actual): ")
        if password:
            env_value(password)
            if password != getpass.getpass("Repita la contraseña SMTP: "):
                fail("Las contraseñas SMTP no coinciden. No se modificó la configuración de correo.")
            updated["SMTP_PASSWORD"] = password
    else:
        updated["SMTP_USERNAME"] = ""
        updated["SMTP_PASSWORD"] = ""
    ask("MAIL_FROM", "Correo remitente autorizado")
    ask("MAIL_FROM_NAME", "Nombre visible del remitente")
    ask("MAIL_REPLY_TO", "Correo para respuestas (opcional)")
    ask("ADMIN_EMAIL", "Correo del administrador inicial (no cambia cuentas ya creadas)")
    validate_mail(updated)
    mail_keys = [key for key in DEFAULTS if key.startswith("SMTP_") or key.startswith("MAIL_") or key == "ADMIN_EMAIL"]
    save_config_values({key: updated[key] for key in mail_keys}, original)
    print("Configuración de correo guardada en .env (600). Reinicie con ./iniciar.sh para aplicarla.")
    print("No se ha enviado correo. Si activa MAIL_ENABLED, la cola pendiente se procesará al reiniciar.")
    return updated



def set_direct_gmail_configuration(values: dict[str, str]) -> dict[str, str]:
    """Gmail directo: cambiar claves de Spring conservando el relay heredado."""
    if not sys.stdin.isatty():
        fail("Use ./configurar.sh --gmail-directo desde una terminal interactiva.")
    original = ENV_FILE.read_text(encoding="utf-8")
    default_address = values["SMTP_USERNAME"] if values["SMTP_HOST"] == "smtp.gmail.com" else ""
    username = input(f"Cuenta Google Workspace/Gmail [{default_address}]: ").strip() or default_address
    if not valid_email_address(username):
        fail("Indique un correo completo y válido.")
    password = getpass.getpass("Nueva contraseña de aplicación Gmail (oculta; Enter conserva la directa existente): ")
    if password:
        env_value(password)
        if password != getpass.getpass("Repita la contraseña de aplicación: "):
            fail("Las contraseñas no coinciden; .env permanece intacto.")
    elif values["SMTP_HOST"] == "smtp.gmail.com" and values["SMTP_USERNAME"] == username:
        password = values["SMTP_PASSWORD"]
    else:
        fail("Debe proporcionar una contraseña de aplicación para Gmail directo.")
    updated = dict(values)
    updated.update(MAIL_ENABLED="true", SMTP_HOST="smtp.gmail.com", SMTP_PORT="587",
                   SMTP_AUTH="true", SMTP_SECURITY="STARTTLS", SMTP_USERNAME=username,
                   SMTP_PASSWORD=password, MAIL_FROM=username)
    validate_mail(updated)
    keys = ("MAIL_ENABLED", "SMTP_HOST", "SMTP_PORT", "SMTP_AUTH", "SMTP_SECURITY",
            "SMTP_USERNAME", "SMTP_PASSWORD", "MAIL_FROM")
    save_config_values({key: updated[key] for key in keys}, original)
    print("Gmail directo configurado en .env. No se ha probado autenticación ni enviado correo.")
    print("Revise la cola Postfix anterior antes de retirarlo y reinicie en mantenimiento.")
    return updated


def set_database_password(values: dict[str, str]) -> dict[str, str]:
    """Configure the local secret; never imply that an existing DB role was altered."""
    if not sys.stdin.isatty():
        fail("Use ./configurar.sh --password-bd desde una terminal interactiva, o edite DB_PASSWORD en .env.")
    print("Configurar DB_PASSWORD en .env. La contraseña no se mostrará al escribirla.")
    print("Si PostgreSQL ya tiene datos, cambie también la contraseña del usuario con \\password; consulte docs/OPERACION.md.")
    password = getpass.getpass("Contraseña de PostgreSQL: ")
    if len(password) < 12 or password.startswith("CAMBIAR_"):
        fail("La contraseña de PostgreSQL debe tener al menos 12 caracteres y no ser una plantilla CAMBIAR_.")
    if not re.fullmatch(ENV_VALUE_PATTERN, password):
        fail("Use letras, números y . _ / : @ % + = , - !; sin espacios, comillas, $, # ni otros símbolos.")
    confirmation = getpass.getpass("Repita la contraseña de PostgreSQL: ")
    if password != confirmation:
        fail("Las contraseñas no coinciden. No se modificó DB_PASSWORD.")
    original = ENV_FILE.read_text(encoding="utf-8")
    lines = original.splitlines(keepends=True)
    found = False
    for index, line in enumerate(lines):
        if line.strip().startswith("DB_PASSWORD="):
            lines[index] = f"DB_PASSWORD={password}\n"
            found = True
    if not found:
        if lines and not lines[-1].endswith("\n"):
            lines[-1] += "\n"
        lines.append(f"DB_PASSWORD={password}\n")
    descriptor, filename = tempfile.mkstemp(prefix=".env-", dir=ENV_FILE.parent)
    temporary = Path(filename)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            handle.writelines(lines)
            handle.flush()
            os.fsync(handle.fileno())
        if ENV_FILE.read_text(encoding="utf-8") != original:
            fail(".env cambió mientras se configuraba. Repita el comando para conservar esos cambios.")
        os.replace(temporary, ENV_FILE)
    finally:
        temporary.unlink(missing_ok=True)
    print("DB_PASSWORD guardada en .env con permisos 600. No se modificó ninguna base de datos existente.")
    updated = dict(values)
    updated["DB_PASSWORD"] = password
    return updated


def set_internal_smtp_configuration(values: dict[str, str]) -> dict[str, str]:
    if not sys.stdin.isatty():
        fail("Use ./configurar.sh --smtp-interno desde una terminal interactiva.")
    original = ENV_FILE.read_text(encoding="utf-8")
    updated = dict(values)
    print("Configurar Postfix SMTP interno (sin bandeja web). No se envían mensajes al guardar.")
    print("Enter conserva cada valor; '-' vacía campos opcionales. Puede configurar un relay autorizado o salida directa por MX/25.")

    def ask(key: str, label: str, normalize=None) -> None:
        value = input(f"{label} [{updated[key]}]: ").strip()
        if value:
            updated[key] = "" if value == "-" else (normalize(value) if normalize else value)
        env_value(updated[key])

    previous_ip = values["SMTP_LOCAL_IP"]
    suggested_ip = previous_ip if previous_ip != "127.0.0.1" else detected_local_ip()
    local_ip = input(f"IP local del equipo anfitrión [{suggested_ip}]: ").strip()
    updated["SMTP_LOCAL_IP"] = local_ip or suggested_ip
    ask("SMTP_HOSTNAME", "Nombre técnico del servidor SMTP (independiente de la IP remitente)")
    if updated["MAIL_FROM"] in {"", "soporte@siabuc.test", f"soporte@[{previous_ip}]"}:
        updated["MAIL_FROM"] = f"soporte@[{updated['SMTP_LOCAL_IP']}]"
    ask("MAIL_FROM", "Dirección remitente; puede usar soporte@[IP local]")
    ask("SMTP_RELAY_HOST", "Servidor relay opcional; vacío usa entrega directa")
    if updated["SMTP_RELAY_HOST"]:
        ask("SMTP_RELAY_PORT", "Puerto del relay")
        ask("SMTP_RELAY_SECURITY", "Seguridad del relay (STARTTLS/SSL/NONE)", str.upper)
        ask("SMTP_RELAY_USERNAME", "Usuario del relay; vacío significa sin autenticación")
        if updated["SMTP_RELAY_USERNAME"]:
            password = getpass.getpass("Contraseña del relay (oculta; Enter conserva la actual): ")
            if password:
                if password != password.rstrip():
                    fail("La contraseña del relay no puede terminar en espacios u otros blancos porque Postfix los recorta. No se modificó el secreto.")
                env_value(password)
                if password != getpass.getpass("Repita la contraseña del relay: "):
                    fail("Las contraseñas del relay no coinciden. No se modificó la configuración.")
                updated["SMTP_RELAY_PASSWORD"] = password
            elif updated["SMTP_RELAY_USERNAME"] != values["SMTP_RELAY_USERNAME"]:
                fail("Un nuevo usuario del relay requiere introducir su contraseña. No se modificó la configuración.")
        else:
            updated["SMTP_RELAY_PASSWORD"] = ""
    else:
        updated["SMTP_RELAY_USERNAME"] = ""
        updated["SMTP_RELAY_PASSWORD"] = ""
    updated.update(MAIL_ENABLED="true", SMTP_HOST="smtp", SMTP_PORT="25", SMTP_AUTH="false",
                   SMTP_SECURITY="NONE", SMTP_USERNAME="", SMTP_PASSWORD="")
    validate_mail(updated)
    validate_smtp_server(updated)
    keys = [key for key in DEFAULTS if key.startswith("SMTP_RELAY_")]
    keys.extend(["SMTP_HOSTNAME", "SMTP_LOCAL_IP", "SMTP_INTERNAL_PORT", "MAIL_ENABLED", "SMTP_HOST",
                 "SMTP_PORT", "SMTP_AUTH", "SMTP_SECURITY", "SMTP_USERNAME", "SMTP_PASSWORD", "MAIL_FROM"])
    save_config_values({key: updated[key] for key in keys}, original)
    print("SMTP interno configurado en .env (600). Reinicie con ./iniciar.sh para aplicarlo.")
    print("No se enviaron mensajes ni se modificaron cuentas existentes. Registre sus correos reales desde Usuarios.")
    smtp_information(updated)
    return updated


def detected_local_ip() -> str:
    """Find the current route's source address; UDP connect sends no application data."""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
            probe.connect(("192.0.2.1", 9))
            candidate = str(probe.getsockname()[0])
            address = ipaddress.IPv4Address(candidate)
            if not address.is_unspecified and not address.is_multicast:
                return candidate
    except (OSError, ValueError):
        pass
    return "127.0.0.1"


def smtp_information(values: dict[str, str]) -> None:
    if values["SMTP_HOST"] == "smtp":
        print(f"Postfix opcional: smtp:25 (127.0.0.1:{values['SMTP_INTERNAL_PORT']} en desarrollo).")
    else:
        print(f"SMTP directo de Spring: {values['SMTP_HOST']}:{values['SMTP_PORT']}.")
    print(f"IP local configurada: {values['SMTP_LOCAL_IP']}; remitente: {values['MAIL_FROM'] or '(sin configurar)' }.")
    if values["MAIL_ENABLED"] == "false":
        print("La aplicación conserva el envío desactivado; la cola permanece pendiente.")
    elif values["SMTP_HOST"] != "smtp":
        print("Spring entrega directamente al SMTP configurado, sin Postfix intermediario.")
    if values["SMTP_HOST"] == "smtp":
        if values["SMTP_RELAY_HOST"]:
            print(f"Postfix relay: {values['SMTP_RELAY_HOST']}:{values['SMTP_RELAY_PORT']} ({values['SMTP_RELAY_SECURITY']}).")
        else:
            print("Postfix usa salida directa MX/25, sujeta a las políticas de destino.")
    print("La configuración SMTP no prueba entrega externa.")


def native_environment(values: dict[str, str]) -> dict[str, str]:
    env = os.environ.copy()
    env.update(values)
    env.update(DB_HOST="127.0.0.1", SERVER_ADDRESS="127.0.0.1")
    if values["SMTP_HOST"] == "smtp":
        env.update(SMTP_HOST="127.0.0.1", SMTP_PORT=values["SMTP_INTERNAL_PORT"])
    elif values["SMTP_HOST"] == "mailpit":
        env.update(SMTP_HOST="127.0.0.1", SMTP_PORT=values["MAILPIT_SMTP_PORT"])
    return env


def compose(values: dict[str, str], *arguments: str, **kwargs):
    require("docker")
    # Añade puertos loopback solo a comandos de desarrollo o administración.
    extra_files = []
    if arguments and arguments[0] in {"up", "exec"} and (
        (arguments[0] == "up" and ("db" in arguments or "smtp" in arguments))
        or (arguments[0] == "exec" and "db" in arguments)
    ):
        extra_files = ["-f", str(ROOT / "docker-compose.dev.yml")]
    env = os.environ.copy()
    env.update(values)
    backup_directory = Path(values.get("BACKUP_DIRECTORY", "./backups/automatic")).expanduser()
    if not backup_directory.is_absolute():
        backup_directory = ROOT / backup_directory
    env["BACKUP_DIRECTORY"] = str(backup_directory.resolve())
    profiles = [p for p in env.get("COMPOSE_PROFILES", "").split(",") if p and p != "local-smtp"]
    if values.get("SMTP_HOST") == "smtp":
        profiles.append("local-smtp")
    if profiles:
        env["COMPOSE_PROFILES"] = ",".join(profiles)
    else:
        env.pop("COMPOSE_PROFILES", None)
    return run(["docker", "compose", "--project-directory", str(ROOT), "--env-file",
                str(ENV_FILE), "-f", str(ROOT / "docker-compose.yml"), *extra_files, *arguments], env=env, **kwargs)


def docker_ready(values: dict[str, str]) -> None:
    require("docker")
    try:
        compose(values, "version", stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        run(["docker", "info"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if IS_WINDOWS:
            engine = run(["docker", "info", "--format", "{{.OSType}}"], capture_output=True, text=True).stdout.strip()
            if engine != "linux":
                fail("Docker Desktop debe utilizar contenedores Linux. Seleccione Switch to Linux containers.")
    except subprocess.CalledProcessError:
        fail("Docker Engine o el complemento Docker Compose no está disponible para este usuario.")


def credentials(values: dict[str, str]) -> None:
    print(f"Administrador inicial: {values['ADMIN_USERNAME']}. La contraseña está en ADMIN_PASSWORD dentro de {ENV_FILE}.")
    print("Se utiliza sólo al crear la primera cuenta. Cámbiela al iniciar sesión; .env no modifica cuentas ya existentes.")


def native_requirements() -> None:
    if IS_WINDOWS:
        fail("En Windows use compilar.bat e iniciar.bat con Docker Desktop; para desarrollo nativo utilice los scripts .sh dentro de WSL2.")
    require("java", "mvn", "node", "npm")
    version = run(["node", "-p", "process.versions.node"], capture_output=True, text=True).stdout.strip()
    if int(version.split(".")[0]) < 24:
        fail("Instale Node.js 24 LTS para compilar este cliente Angular.")
    java_version = subprocess.run(["java", "-version"], capture_output=True, text=True)
    match = re.search(r'version "(\d+)', java_version.stderr + java_version.stdout)
    if java_version.returncode != 0 or not match or int(match.group(1)) < 17:
        fail("Instale un JDK Java 17 o posterior y configure JAVA_HOME correctamente.")


def build_native() -> None:
    native_requirements()
    run(["mvn", "-B", "-ntp", "package"], cwd=ROOT / "backend")
    run(["npm", "ci"], cwd=ROOT / "frontend")
    run(["npm", "run", "build"], cwd=ROOT / "frontend")
    print("Compilación terminada: backend/target/siabuc-helpdesk-1.8.3.jar y frontend/dist/helpdesk/browser/.")


def wait_http(url: str, process: subprocess.Popen, log_path: Path, timeout: int = 180) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            fail(f"El proceso terminó antes de estar listo. Revise {log_path}.")
        try:
            with urllib.request.urlopen(url, timeout=2) as response:
                if response.status == 200:
                    return
        except (urllib.error.URLError, TimeoutError, OSError):
            pass
        time.sleep(1)
    fail(f"El servicio no respondió en {timeout} s. Revise {log_path}.")


def development(values: dict[str, str]) -> None:
    native_requirements()
    docker_ready(values)
    RUNTIME.mkdir(mode=0o700, exist_ok=True)
    os.chmod(RUNTIME, 0o700)
    marker = RUNTIME / "dev.pid"
    if marker.exists():
        try:
            old_pid = int(marker.read_text().strip())
            os.kill(old_pid, 0)
        except (ValueError, ProcessLookupError):
            marker.unlink(missing_ok=True)
        else:
            fail("Ya existe una sesión de desarrollo activa. Deténgala con Ctrl+C en su terminal.")
    if values["COOKIE_SECURE"] == "true":
        fail("El servidor de desarrollo usa HTTP local. Configure COOKIE_SECURE=false para iniciar-dev.sh.")
    for key in ("SERVER_PORT", "FRONTEND_PORT"):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
            try:
                probe.bind(("127.0.0.1", int(values[key])))
            except OSError:
                fail(f"El puerto {values[key]} ({key}) está ocupado. Detenga el servicio o cambie .env.")
    services = ["db"]
    if values["SMTP_HOST"] == "smtp":
        services.append("smtp")
    if values["SMTP_HOST"] == "mailpit":
        services.append("mailpit")
    compose(values, "up", "-d", "--build", "--wait", "--wait-timeout", "180", *services)
    run(["mvn", "-B", "-ntp", "package"], cwd=ROOT / "backend")
    run(["npm", "ci"], cwd=ROOT / "frontend")
    env = native_environment(values)
    processes = []
    handles = []
    marker.write_text(str(os.getpid()), encoding="utf-8")

    def interrupted(_signum, _frame):
        raise KeyboardInterrupt

    previous_sigterm = signal.signal(signal.SIGTERM, interrupted)
    try:
        commands = [
            (["java", "-jar", str(ROOT / "backend/target/siabuc-helpdesk-1.8.3.jar")],
             ROOT / "backend", RUNTIME / "backend.log", f"http://127.0.0.1:{values['SERVER_PORT']}/api/health"),
            (["npm", "start", "--", "--host", "127.0.0.1", "--port", values["FRONTEND_PORT"]],
             ROOT / "frontend", RUNTIME / "frontend.log", f"http://127.0.0.1:{values['FRONTEND_PORT']}/"),
        ]
        for command, directory, log_path, url in commands:
            handle = log_path.open("w", encoding="utf-8")
            handles.append(handle)
            process = subprocess.Popen(command, cwd=directory, env=env, stdout=handle,
                                       stderr=subprocess.STDOUT, start_new_session=True)
            processes.append(process)
            print(f"Iniciando servicio; registro en {log_path}.", flush=True)
            wait_http(url, process, log_path)
        print(f"Desarrollo listo: http://127.0.0.1:{values['FRONTEND_PORT']}", flush=True)
        credentials(values)
        smtp_information(values)
        print("Ctrl+C detiene Spring y Angular; PostgreSQL permanece disponible hasta ./detener.sh.", flush=True)
        while True:
            if any(process.poll() is not None for process in processes):
                fail("Un servicio terminó. Revise .runtime/backend.log y .runtime/frontend.log.")
            time.sleep(1)
    except KeyboardInterrupt:
        print("Deteniendo procesos de desarrollo...", flush=True)
    finally:
        signal.signal(signal.SIGTERM, previous_sigterm)
        for process in reversed(processes):
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGTERM)
        for process in processes:
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
        for handle in handles:
            handle.close()
        marker.unlink(missing_ok=True)


def prepare_automatic_backup_directory(values: dict[str, str]) -> Path:
    """Create only the configured destination; never chmod/chown an existing host directory."""
    destination = Path(values.get("BACKUP_DIRECTORY", "./backups/automatic")).expanduser()
    if not destination.is_absolute():
        destination = ROOT / destination
    destination = destination.resolve()
    if destination == ROOT or destination == Path(destination.anchor):
        fail("BACKUP_DIRECTORY debe ser una carpeta exclusiva de copias, no la raíz del disco ni del proyecto.")
    destination.mkdir(parents=True, exist_ok=True, mode=0o700)
    if not destination.is_dir():
        fail("BACKUP_DIRECTORY no es un directorio.")
    return destination


def backup(values: dict[str, str], output: str | None) -> None:
    docker_ready(values)
    destination = Path(output).expanduser().resolve() if output else ROOT / "backups" / (
        "siabuc-" + dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + ".dump")
    destination.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if destination.exists():
        fail("El archivo destino ya existe. Elija otro nombre para conservar la copia anterior.")
    created = False
    try:
        descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_BINARY", 0), 0o600)
        created = True
        with os.fdopen(descriptor, "wb") as handle:
            compose(values, "exec", "-T", "db", "pg_dump", "-U", values["DB_USER"],
                    "-d", values["DB_NAME"], "--format=custom", "--no-owner", stdout=handle)
    except Exception:
        if created:
            destination.unlink(missing_ok=True)
        raise
    digest = digest_file(destination)
    checksum = destination.with_suffix(destination.suffix + ".sha256")
    checksum.write_text(f"{digest}  {destination.name}\n", encoding="utf-8")
    os.chmod(checksum, 0o600)
    print(f"Copia creada: {destination}\nSHA-256: {checksum}")


def restore(values: dict[str, str], filename: str, confirmed: bool) -> None:
    if not confirmed:
        fail("Restaurar reemplaza los datos actuales. Cree una copia y repita con --confirmar-reemplazo.")
    source = Path(filename).expanduser().resolve()
    if not source.is_file():
        fail("No existe el archivo de respaldo indicado.")
    if (RUNTIME / "dev.pid").exists():
        fail("Detenga antes la sesión de desarrollo con Ctrl+C. No restaure mientras haya procesos nativos activos.")
    checksum = source.with_suffix(source.suffix + ".sha256")
    if checksum.exists():
        try:
            checksum_fields = checksum.read_text(encoding="utf-8").split()
        except UnicodeDecodeError:
            fail("El archivo de verificación SHA-256 no contiene texto válido; no se inició la restauración.")
        if not checksum_fields or not re.fullmatch(r"[A-Fa-f0-9]{64}", checksum_fields[0]):
            fail("El archivo de verificación SHA-256 está vacío o es inválido; no se inició la restauración.")
        if digest_file(source) != checksum_fields[0].lower():
            fail("SHA-256 incorrecto: la copia no coincide con su archivo de verificación.")
    docker_ready(values)
    compose(values, "up", "-d", "--wait", "--wait-timeout", "180", "db")
    compose(values, "--profile", "local-smtp", "stop", "backup", "frontend", "backend", "smtp")
    try:
        with source.open("rb") as handle:
            compose(values, "exec", "-T", "db", "pg_restore", "-U", values["DB_USER"],
                    "-d", values["DB_NAME"], "--clean", "--if-exists", "--no-owner", "--no-privileges",
                    "--single-transaction", "--exit-on-error", stdin=handle)
    except subprocess.CalledProcessError:
        fail("Falló la restauración transaccional. Respaldos automáticos, Spring, Angular y Postfix siguen detenidos; revise el error antes de reiniciar.")
    print("Restauración terminada. Se conservarán las cuentas y contraseñas del respaldo.")
    print("Ejecute iniciar.bat para iniciar todos los servicios." if IS_WINDOWS else "Ejecute ./iniciar.sh para iniciar todos los servicios, o ./iniciar-dev.sh para desarrollo.")


def backup_filename(value: str) -> str:
    if not value.strip():
        raise argparse.ArgumentTypeError("El nombre o ruta del respaldo no puede estar vacío ni contener sólo espacios.")
    return value


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    start = sub.add_parser("start")
    start.add_argument("--solo-bd", action="store_true")
    configure = sub.add_parser("config")
    configure.add_argument("--password-bd", action="store_true",
                           help="Solicitar y confirmar DB_PASSWORD sin mostrarla; configura .env, no cambia una base existente.")
    mail_options = configure.add_mutually_exclusive_group()
    mail_options.add_argument("--correo", action="store_true", help="Configurar conexión SMTP y remitente sin enviar correo.")
    mail_options.add_argument("--smtp-interno", action="store_true", help="Usar Postfix opcional como SMTP interno o relay.")
    mail_options.add_argument("--gmail-directo", action="store_true", help="Configurar Gmail directo con TLS sin Postfix.")
    sub.add_parser("stop")
    build = sub.add_parser("build")
    build.add_argument("--docker", action="store_true")
    sub.add_parser("dev")
    save = sub.add_parser("backup", description="Crear un respaldo PostgreSQL. Sin archivo, genera un nombre con fecha en backups/.",
                          formatter_class=argparse.RawDescriptionHelpFormatter,
                          epilog='Ejemplos:\n  ./respaldar.sh\n  ./respaldar.sh --nombre-respaldo "copias/siabuc lunes.dump"')
    save.add_argument("archivo", nargs="?", type=backup_filename, metavar="ARCHIVO",
                      help="Nombre o ruta literal del respaldo; alternativa a --nombre-respaldo.")
    save.add_argument("--nombre-respaldo", type=backup_filename, metavar="ARCHIVO",
                      help="Nombre o ruta del archivo, relativo al directorio actual si no es absoluto; no se añade extensión.")
    recover = sub.add_parser("restore", description="Restaurar un respaldo PostgreSQL. Indique ARCHIVO o --nombre-respaldo, nunca ambos.",
                             formatter_class=argparse.RawDescriptionHelpFormatter,
                             epilog='Ejemplo:\n  ./restaurar.sh --nombre-respaldo "copias/siabuc lunes.dump" --confirmar-reemplazo')
    recover.add_argument("archivo", nargs="?", type=backup_filename, metavar="ARCHIVO",
                         help="Nombre o ruta del respaldo existente; alternativa a --nombre-respaldo.")
    recover.add_argument("--nombre-respaldo", type=backup_filename, metavar="ARCHIVO",
                         help="Nombre o ruta del respaldo existente, relativo al directorio actual si no es absoluto.")
    recover.add_argument("--confirmar-reemplazo", action="store_true",
                         help="Autorizar reemplazar los datos actuales con el respaldo indicado.")
    args = parser.parse_args()
    if args.command in {"backup", "restore"}:
        command_parser = save if args.command == "backup" else recover
        if args.archivo is not None and args.nombre_respaldo is not None:
            command_parser.error("Indique el archivo como argumento o con --nombre-respaldo; no combine ambas formas.")
        if args.nombre_respaldo is not None:
            args.archivo = args.nombre_respaldo
        if args.command == "restore" and args.archivo is None:
            command_parser.error("Debe indicar un respaldo mediante ARCHIVO o --nombre-respaldo ARCHIVO.")
    if args.command == "build" and not args.docker:
        build_native()
        return
    prompt_db_password = args.command == "config" and args.password_bd
    prompt_mail = args.command == "config" and args.correo
    prompt_smtp = args.command == "config" and args.smtp_interno
    prompt_gmail = args.command == "config" and args.gmail_directo
    values = config(create=args.command in {"config", "start", "dev", "build"},
                    prompt_db_password=prompt_db_password,
                    prompt_mail=prompt_mail or prompt_gmail, prompt_smtp=prompt_smtp)
    if args.command == "config":
        if prompt_db_password:
            values = set_database_password(values)
        if prompt_mail:
            values = set_mail_configuration(values)
        if prompt_gmail:
            values = set_direct_gmail_configuration(values)
        if prompt_smtp:
            values = set_internal_smtp_configuration(values)
        print(".env validado. Puede editar los puertos y volver a ejecutar este comando.")
        credentials(values)
    elif args.command == "start":
        docker_ready(values)
        if args.solo_bd:
            compose(values, "up", "-d", "--wait", "--wait-timeout", "180", "db")
            print(f"PostgreSQL listo en 127.0.0.1:{values['DB_PORT']}.")
        else:
            prepare_automatic_backup_directory(values)
            compose(values, "up", "-d", "--build", "--wait", "--wait-timeout", "300")
            print(f"SIABUC Helpdesk web: http://{values['WEB_BIND_HOST']}:{values['FRONTEND_PORT']} (si usa 0.0.0.0, sustituya por su IP LAN).")
            credentials(values)
            smtp_information(values)
    elif args.command == "stop":
        docker_ready(values)
        compose(values, "--profile", "local-smtp", "--profile", "mail-test", "down", "--remove-orphans")
        print("Contenedores detenidos. Se conservaron los volúmenes de PostgreSQL, la cola SMTP y Mailpit si se utilizó.")
    elif args.command == "build":
        docker_ready(values)
        services = ["backend", "frontend", "backup"]
        if values["SMTP_HOST"] == "smtp":
            services.append("smtp")
        compose(values, "build", *services)
    elif args.command == "dev":
        development(values)
    elif args.command == "backup":
        backup(values, args.archivo)
    elif args.command == "restore":
        restore(values, args.archivo, args.confirmar_reemplazo)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError) as error:
        print(f"Error: {error}", file=sys.stderr)
        sys.exit(1)
    except subprocess.CalledProcessError as error:
        print(f"Error: una herramienta terminó con código {error.returncode}. Revise la salida anterior.", file=sys.stderr)
        sys.exit(error.returncode or 1)
    except KeyboardInterrupt:
        print("Operación cancelada.", file=sys.stderr)
        sys.exit(130)
    except EOFError:
        print("Entrada interrumpida. Repita la configuración desde una terminal.", file=sys.stderr)
        sys.exit(1)
