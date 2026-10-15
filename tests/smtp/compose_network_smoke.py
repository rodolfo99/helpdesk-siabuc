#!/usr/bin/env python3
"""Check Postfix submission from a second Compose container without sending mail.

Run this script in a disposable container on the same Compose default network as
the healthy smtp service. It stops before DATA, resets the SMTP transaction, and
therefore never queues a message or attempts delivery to the example addresses.
"""
from __future__ import annotations

import smtplib


def expect(code: int, expected: int, operation: str) -> None:
    if code != expected:
        raise RuntimeError(f"{operation}: expected SMTP {expected}, received {code}")


def main() -> None:
    with smtplib.SMTP("smtp", 25, timeout=10) as client:
        code, _ = client.ehlo("network-smoke.siabuc.test")
        expect(code, 250, "EHLO")
        code, _ = client.mail("network-smoke@example.test")
        expect(code, 250, "MAIL FROM")
        code, _ = client.rcpt("network-smoke-recipient@example.test")
        expect(code, 250, "RCPT TO")
        code, _ = client.rset()
        expect(code, 250, "RSET")
    print("OK: Compose network client accepted; transaction reset without DATA or delivery")


if __name__ == "__main__":
    main()
