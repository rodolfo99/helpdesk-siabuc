#!/usr/bin/env python3
"""Configure a send-only Postfix service without evaluating environment as shell code.

Only the container's Compose subnet and loopback may submit messages. There is no
mailbox, web inbox, public relay, server-side SMTP authentication, or IMAP/POP.
Pure parsing/rendering functions are importable by tests and do not read os.environ.
"""
from __future__ import annotations

from dataclasses import dataclass, field
import ipaddress
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from typing import Callable, Mapping


class ConfigurationError(ValueError):
    """A safe operator message; never include credential values in this exception."""


@dataclass(frozen=True)
class SMTPConfiguration:
    hostname: str
    local_ip: str
    relay_host: str
    relay_port: int
    relay_security: str
    relay_username: str
    relay_password: str = field(repr=False)

    @property
    def auth(self) -> bool:
        return bool(self.relay_username and self.relay_password)

    @property
    def relay_destination(self) -> str:
        return f"[{self.relay_host}]:{self.relay_port}" if self.relay_host else ""


_DNS_LABEL = re.compile(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")


def _host(value: str, key: str, *, fqdn: bool) -> str:
    if not value or len(value) > 253 or value != value.strip():
        raise ConfigurationError(f"{key} debe ser un nombre de servidor válido, sin espacios.")
    try:
        address = ipaddress.ip_address(value)
    except ValueError:
        address = None
    if address is not None:
        if fqdn or address.version != 4 or address.is_unspecified or address.is_multicast:
            raise ConfigurationError(f"{key} requiere un nombre DNS válido.")
        return str(address)
    labels = value.split(".")
    if (fqdn and len(labels) < 2) or any(_DNS_LABEL.fullmatch(label) is None for label in labels):
        raise ConfigurationError(f"{key} debe usar etiquetas DNS ASCII válidas, sin puerto ni corchetes.")
    return value.lower()


def parse_configuration(env: Mapping[str, str]) -> SMTPConfiguration:
    hostname = _host(env.get("SMTP_HOSTNAME", "mail.siabuc.internal"), "SMTP_HOSTNAME", fqdn=True)
    try:
        local_ip = ipaddress.IPv4Address(env.get("SMTP_LOCAL_IP", "127.0.0.1"))
    except ipaddress.AddressValueError:
        raise ConfigurationError("SMTP_LOCAL_IP debe ser una dirección IPv4, sin corchetes, puerto ni prefijo de red.") from None
    if local_ip.is_unspecified or local_ip.is_multicast or str(local_ip) == "255.255.255.255":
        raise ConfigurationError("SMTP_LOCAL_IP debe identificar un equipo; no se admite la dirección genérica ni multicast o broadcast.")
    raw_relay = env.get("SMTP_RELAY_HOST", "")
    relay_host = _host(raw_relay, "SMTP_RELAY_HOST", fqdn=False) if raw_relay else ""
    raw_port = env.get("SMTP_RELAY_PORT", "587")
    if not re.fullmatch(r"[0-9]{1,5}", raw_port) or not 1 <= int(raw_port) <= 65535:
        raise ConfigurationError("SMTP_RELAY_PORT debe estar entre 1 y 65535.")
    security = env.get("SMTP_RELAY_SECURITY", "STARTTLS")
    if security not in {"STARTTLS", "SSL", "NONE"}:
        raise ConfigurationError("SMTP_RELAY_SECURITY debe ser STARTTLS, SSL o NONE.")
    username = env.get("SMTP_RELAY_USERNAME", "")
    password = env.get("SMTP_RELAY_PASSWORD", "")
    if len(username) > 320 or any(character.isspace() or ord(character) < 32 or ord(character) == 127
                                  or character == ":" for character in username):
        raise ConfigurationError("SMTP_RELAY_USERNAME no admite espacios, dos puntos ni caracteres de control; máximo 320 caracteres.")
    if len(password) > 4096 or any(character in password for character in ("\r", "\n", "\0")):
        raise ConfigurationError("SMTP_RELAY_PASSWORD no admite saltos de línea ni NUL; máximo 4096 caracteres.")
    if password and password[-1].isspace():
        raise ConfigurationError("SMTP_RELAY_PASSWORD no puede terminar en espacios o tabuladores: postmap los eliminaría. Use una credencial sin espacios finales.")
    if bool(username) != bool(password):
        raise ConfigurationError("Configure juntos SMTP_RELAY_USERNAME y SMTP_RELAY_PASSWORD, o deje ambos vacíos.")
    if not relay_host and (username or password):
        raise ConfigurationError("Las credenciales de relay requieren SMTP_RELAY_HOST.")
    if username and security == "NONE":
        raise ConfigurationError("La autenticación de relay requiere STARTTLS o SSL; no se permiten credenciales sin TLS.")
    return SMTPConfiguration(hostname, str(local_ip), relay_host, int(raw_port), security, username, password)


def render_main_cf(config: SMTPConfiguration, map_type: str = "hash") -> str:
    if map_type not in {"hash", "lmdb"}:
        raise ConfigurationError("Tipo de mapa SASL no admitido.")
    tls = "may" if not config.relay_host else ("none" if config.relay_security == "NONE" else "secure")
    values = {
        "compatibility_level": "3.6",
        "queue_directory": "/var/spool/postfix",
        "command_directory": "/usr/sbin",
        "daemon_directory": "/usr/lib/postfix/sbin",
        "data_directory": "/var/lib/postfix",
        "mail_owner": "postfix",
        "setgid_group": "postdrop",
        "myhostname": config.hostname,
        # RFC 5321 address literal: keep myhostname a valid technical DNS name.
        "smtp_helo_name": f"[{config.local_ip}]",
        "myorigin": "$myhostname",
        "inet_interfaces": "all",
        "inet_protocols": "ipv4",
        # Derive trust from the actual container interfaces, not a fixed CIDR.
        # Compose attaches smtp only to its default bridge and publishes no port.
        "mynetworks_style": "subnet",
        "mydestination": "",
        "relay_domains": "",
        "local_transport": "error:5.1.1 Local delivery is disabled",
        "virtual_transport": "error:5.1.1 Virtual delivery is disabled",
        "alias_maps": "",
        "alias_database": "",
        "local_recipient_maps": "",
        "virtual_alias_domains": "",
        "virtual_mailbox_domains": "",
        "smtpd_banner": "$myhostname ESMTP SIABUC",
        "smtpd_delay_reject": "no",
        "smtpd_client_restrictions": "permit_mynetworks, reject",
        "smtpd_relay_restrictions": "permit_mynetworks, reject",
        "smtpd_recipient_restrictions": "permit_mynetworks, reject",
        "smtpd_sasl_auth_enable": "no",
        "smtpd_tls_security_level": "none",
        "disable_vrfy_command": "yes",
        "smtpd_helo_required": "yes",
        "smtpd_forbid_unauth_pipelining": "yes",
        "smtpd_timeout": "60s",
        "message_size_limit": "10485760",
        "smtpd_recipient_limit": "50",
        "relayhost": config.relay_destination,
        "smtp_tls_security_level": tls,
        "smtp_tls_CAfile": "/etc/ssl/certs/ca-certificates.crt",
        "smtp_tls_secure_cert_match": "nexthop",
        "smtp_tls_mandatory_protocols": ">=TLSv1.2",
        "smtp_tls_mandatory_ciphers": "high",
        "smtp_tls_wrappermode": "yes" if config.relay_host and config.relay_security == "SSL" else "no",
        "smtp_tls_loglevel": "0",
        "smtp_sasl_auth_enable": "yes" if config.auth else "no",
        "smtp_sasl_password_maps": f"{map_type}:/etc/postfix/sasl_passwd" if config.auth else "",
        "smtp_sasl_security_options": "noanonymous, noplaintext",
        "smtp_sasl_tls_security_options": "noanonymous",
        "smtp_sasl_mechanism_filter": "plain, login" if config.auth else "",
        "smtp_connect_timeout": "30s",
        "smtp_helo_timeout": "30s",
        "smtp_starttls_timeout": "30s",
        "smtp_mail_timeout": "60s",
        "smtp_rcpt_timeout": "60s",
        "smtp_data_init_timeout": "60s",
        "smtp_data_xfer_timeout": "120s",
        "smtp_data_done_timeout": "120s",
        "maximal_queue_lifetime": "5d",
        "bounce_queue_lifetime": "1d",
        "minimal_backoff_time": "60s",
        "maximal_backoff_time": "1h",
        "queue_run_delay": "60s",
        "maillog_file": "/dev/stdout",
    }
    return "# Generated by SIABUC. No credentials are stored in this configuration.\n" + \
        "".join(f"{name} = {value}\n" for name, value in values.items())


def render_master_cf() -> str:
    # Explicitly disable chroot for every service so container DNS and CA files
    # remain available. No local/virtual mailbox service and no submission ports.
    return """# service type private unpriv chroot wakeup maxproc command
smtp      inet       n  -  n  -      -  smtpd
pickup    unix       n  -  n  60     1  pickup
cleanup   unix       n  -  n  -      0  cleanup
qmgr      unix       n  -  n  300    1  qmgr
tlsmgr    unix       -  -  n  1000?  1  tlsmgr
rewrite   unix       -  -  n  -      -  trivial-rewrite
bounce    unix       -  -  n  -      0  bounce
defer     unix       -  -  n  -      0  bounce
trace     unix       -  -  n  -      0  bounce
verify    unix       -  -  n  -      1  verify
flush     unix       n  -  n  1000?  0  flush
proxymap  unix       -  -  n  -      -  proxymap
proxywrite unix      -  -  n  -      1  proxymap
smtp      unix       -  -  n  -      -  smtp
relay     unix       -  -  n  -      -  smtp
showq     unix       n  -  n  -      -  showq
error     unix       -  -  n  -      -  error
retry     unix       -  -  n  -      -  error
discard   unix       -  -  n  -      -  discard
anvil     unix       -  -  n  -      1  anvil
scache    unix       -  -  n  -      1  scache
postlog   unix-dgram n  -  n  -      1  postlogd
"""


def _write_atomic(path: Path, text: str, mode: int) -> None:
    descriptor, temporary = tempfile.mkstemp(prefix="." + path.name + "-", dir=path.parent)
    temporary_path = Path(temporary)
    try:
        os.fchmod(descriptor, mode)
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(text)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary_path, path)
        os.chmod(path, mode)
    finally:
        temporary_path.unlink(missing_ok=True)


def write_sasl_map(config: SMTPConfiguration, directory: Path, map_type: str = "hash",
                   run: Callable = subprocess.run) -> None:
    if map_type not in {"hash", "lmdb"}:
        raise ConfigurationError("Tipo de mapa SASL no admitido.")
    source = directory / "sasl_passwd"
    if not config.auth:
        for path in (source, directory / "sasl_passwd.db", directory / "sasl_passwd.lmdb"):
            path.unlink(missing_ok=True)
        return
    directory.mkdir(parents=True, exist_ok=True)
    _write_atomic(source, f"{config.relay_destination}\t{config.relay_username}:{config.relay_password}\n", 0o600)
    try:
        run(["postmap", f"{map_type}:{source}"], check=True,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except (OSError, subprocess.CalledProcessError):
        raise ConfigurationError("No se pudo preparar el mapa privado de autenticación del relay.") from None
    indexed = directory / ("sasl_passwd.db" if map_type == "hash" else "sasl_passwd.lmdb")
    if not indexed.is_file():
        raise ConfigurationError("Postfix no generó el mapa privado de autenticación esperado.")
    os.chmod(source, 0o600)
    os.chmod(indexed, 0o600)


def choose_map_type(available: str) -> str:
    choices = set(available.split())
    if "hash" in choices:
        return "hash"
    if "lmdb" in choices:
        return "lmdb"
    raise ConfigurationError("Postfix requiere soporte de mapas hash o lmdb para el relay.")


def main() -> None:
    try:
        configuration = parse_configuration(os.environ)
        directory = Path("/etc/postfix")
        directory.mkdir(parents=True, exist_ok=True)
        # Debian's "No configuration" install can leave main.cf absent; postconf
        # still requires that file even when only listing supported map types.
        _write_atomic(directory / "main.cf", render_main_cf(configuration), 0o644)
        _write_atomic(directory / "master.cf", render_master_cf(), 0o644)
        available = subprocess.run(["postconf", "-m"], check=True, capture_output=True, text=True).stdout
        map_type = choose_map_type(available)
        _write_atomic(directory / "main.cf", render_main_cf(configuration, map_type), 0o644)
        write_sasl_map(configuration, directory, map_type)
        Path("/var/spool/postfix").mkdir(parents=True, exist_ok=True)
        Path("/var/lib/postfix").mkdir(parents=True, exist_ok=True)
        subprocess.run(["postfix", "set-permissions"], check=True)
        subprocess.run(["postfix", "check"], check=True)
        # Do not propagate the relay credential environment into daemon processes.
        child_env = dict(os.environ)
        child_env.pop("SMTP_RELAY_PASSWORD", None)
        child_env.pop("SMTP_RELAY_USERNAME", None)
        print("SIABUC SMTP listo: " + ("entrega mediante relay configurado." if configuration.relay_host
                                      else "entrega directa mediante los registros MX de los destinatarios."), flush=True)
        os.execvpe("postfix", ["postfix", "start-fg"], child_env)
    except ConfigurationError as error:
        print("Configuración SMTP inválida: " + str(error), file=sys.stderr)
        raise SystemExit(1) from None
    except (OSError, subprocess.CalledProcessError):
        print("No se pudo preparar Postfix. Revise la imagen y los permisos del volumen de cola.", file=sys.stderr)
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
