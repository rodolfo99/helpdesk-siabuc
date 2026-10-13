#!/usr/bin/env python3
"""Check the local SMTP listener without creating or delivering any message."""
from __future__ import annotations

import smtplib
import sys


def healthy() -> bool:
    try:
        with smtplib.SMTP("127.0.0.1", 25, timeout=3) as client:
            if client.ehlo("[127.0.0.1]")[0] != 250:
                return False
            return client.noop()[0] == 250
    except (OSError, smtplib.SMTPException):
        return False


if __name__ == "__main__":
    sys.exit(0 if healthy() else 1)
