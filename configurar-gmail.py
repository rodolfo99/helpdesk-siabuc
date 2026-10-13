#!/usr/bin/env python3
"""Configura Gmail UCOL como relay del Postfix existente, sin enviar correo de prueba."""
import argparse
import getpass
import os
from pathlib import Path
import re
import shutil
import smtplib
import ssl
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / 'scripts'))
import manage


def settings(account, password):
    account = account.strip()
    if not re.fullmatch(r'[A-Za-z0-9._%+\-]+@ucol\.mx', account, re.I):
        raise RuntimeError('Introduzca su dirección completa @ucol.mx alojada en Gmail.')
    password = password.replace(' ', '')
    if not re.fullmatch(r'[A-Za-z0-9]{16}', password):
        raise RuntimeError('Se requiere la contraseña de aplicación de 16 caracteres, no su contraseña habitual.')
    return dict(MAIL_ENABLED='true', SMTP_HOST='smtp', SMTP_PORT='25',
                SMTP_AUTH='false', SMTP_SECURITY='NONE', SMTP_USERNAME='', SMTP_PASSWORD='',
                SMTP_RELAY_HOST='smtp.gmail.com', SMTP_RELAY_PORT='587',
                SMTP_RELAY_SECURITY='STARTTLS', SMTP_RELAY_USERNAME=account,
                SMTP_RELAY_PASSWORD=password, MAIL_FROM=account)


def authenticate(account, password):
    try:
        with smtplib.SMTP('smtp.gmail.com', 587, timeout=15) as client:
            client.ehlo()
            client.starttls(context=ssl.create_default_context())
            client.ehlo()
            client.login(account, password)
    except smtplib.SMTPAuthenticationError:
        raise RuntimeError('Google rechazó la autenticación. Revise la contraseña de aplicación y la política de UCOL.') from None
    except (OSError, smtplib.SMTPException):
        raise RuntimeError('No se pudo autenticar mediante TLS en smtp.gmail.com:587. Revise DNS, salida TCP 587 y acceso institucional.') from None


def docker_prefix():
    if not shutil.which('docker'):
        raise RuntimeError('Instale Docker con Compose antes de continuar.')
    if subprocess.run(['docker', 'info'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
        prefix = ['docker']
    else:
        if manage.IS_WINDOWS:
            raise RuntimeError('Inicie Docker Desktop con contenedores Linux y compruebe docker info. En Windows no se usa sudo.')
        if not shutil.which('sudo'):
            raise RuntimeError('No se puede acceder a Docker y sudo no está disponible.')
        print('Se necesita acceso a Docker; sudo puede pedir su contraseña del equipo.')
        subprocess.run(['sudo', '-v'], check=True)
        prefix = ['sudo', 'docker']
        subprocess.run(prefix + ['info'], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(prefix + ['compose', 'version'], check=True, stdout=subprocess.DEVNULL)
    return prefix


def apply(updates, original):
    # Authentication failure never replaces .env.
    authenticate(updates['SMTP_RELAY_USERNAME'], updates['SMTP_RELAY_PASSWORD'])
    manage.save_config_values(updates, original)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--solo-configurar', action='store_true', help='Validar y guardar sin reiniciar contenedores.')
    args = parser.parse_args()
    if not sys.stdin.isatty():
        raise RuntimeError('Ejecute desde una terminal interactiva; no pase contraseñas por argumentos.')
    values = manage.config(prompt_mail=True, prompt_smtp=True)
    original = manage.ENV_FILE.read_text(encoding='utf-8')
    prefix = None if args.solo_configurar else docker_prefix()
    print('Cree una contraseña de aplicación para SIABUC en https://myaccount.google.com/apppasswords')
    print('Requiere verificación en dos pasos y autorización de la política institucional.')
    suggested = values.get('SMTP_RELAY_USERNAME', '') or values.get('ADMIN_EMAIL', '')
    if not suggested.lower().endswith('@ucol.mx'):
        suggested = ''
    account = input(f'Correo Gmail UCOL [{suggested}]: ').strip() or suggested
    updates = settings(account, getpass.getpass('Contraseña de aplicación (entrada oculta): '))
    candidate = {**values, **updates}
    manage.validate_mail(candidate)
    manage.validate_smtp_server(candidate)
    print('Comprobando TLS y autenticación; no se envía un correo de prueba…')
    apply(updates, original)
    print('Configuración guardada en .env. PostgreSQL y puertos conservados. Use permisos privados (600 en Linux; ACL en Windows).')
    if args.solo_configurar:
        print('Pendiente: recrear smtp y backend para aplicar los cambios. Consulte docs/GMAIL-UCOL.md.')
        return
    # Remove inherited overrides: Compose must consume this .env, including through sudo.
    clean_env = {key: value for key, value in os.environ.items()
                 if key not in manage.ALLOWED_KEYS and not key.startswith('COMPOSE_')}
    command = prefix + ['compose', '--project-directory', str(ROOT), '--env-file', str(manage.ENV_FILE),
                        '-f', str(ROOT / 'docker-compose.yml')]
    try:
        subprocess.run(command + ['up', '-d', '--no-deps', '--force-recreate', '--wait',
                                 '--wait-timeout', '180', 'smtp', 'backend'], check=True, env=clean_env)
        subprocess.run(command + ['exec', '-T', 'smtp', 'postqueue', '-f'], check=True, env=clean_env)
    except subprocess.CalledProcessError:
        raise RuntimeError('Gmail quedó guardado, pero falló la aplicación en Docker. Revise docker compose ps/logs y docs/GMAIL-UCOL.md; no se borraron volúmenes.') from None
    print('SMTP y backend recreados. Se solicitó reintentar la cola existente.')
    print('Los mensajes antiguos conservan el remitente anterior; revise los logs para confirmar su entrega.')
    print('Autenticación correcta no garantiza recepción. Revise también correo no deseado.')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print('Error: ' + (str(error) if isinstance(error, RuntimeError) else 'No se pudo acceder a Docker/Compose.'), file=sys.stderr)
        sys.exit(1)
    except (EOFError, KeyboardInterrupt):
        print('\nOperación interrumpida.', file=sys.stderr)
        sys.exit(130)
