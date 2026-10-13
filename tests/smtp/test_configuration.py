"""Validate Postfix configuration only; these tests do not claim SMTP delivery."""
from __future__ import annotations

import contextlib
import importlib.util
import io
import ipaddress
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("siabuc_smtp_entrypoint_under_test", ROOT / "smtp/entrypoint.py")
smtp = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = smtp
SPEC.loader.exec_module(smtp)


def relay_env(**overrides: str) -> dict[str, str]:
    values = {
        "SMTP_HOSTNAME": "smtp.siabuc.test",
        "SMTP_NETWORK_SUBNET": "172.30.55.0/24",
        "SMTP_RELAY_HOST": "relay.example.test",
        "SMTP_RELAY_PORT": "587",
        "SMTP_RELAY_SECURITY": "STARTTLS",
        "SMTP_RELAY_USERNAME": "soporte@example.test",
        "SMTP_RELAY_PASSWORD": "clave-de-prueba-no-real-$# !",
    }
    values.update(overrides)
    return values


def settings(rendered: str) -> dict[str, str]:
    return {key.strip(): value.strip() for line in rendered.splitlines()
            if line.strip() and not line.lstrip().startswith("#") and "=" in line
            for key, value in [line.split("=", 1)]}


class SmtpConfigurationTests(unittest.TestCase):
    def test_default_direct_mode_has_no_upstream_credentials(self) -> None:
        config = smtp.parse_configuration({})
        rendered = settings(smtp.render_main_cf(config))
        self.assertFalse(config.auth)
        self.assertEqual(config.relay_host, "")
        self.assertEqual(rendered.get("relayhost", ""), "")
        self.assertEqual(rendered["smtp_sasl_auth_enable"], "no")
        self.assertEqual(rendered["smtp_tls_security_level"], "may")
        self.assertEqual(rendered["inet_protocols"], "ipv4")
        self.assertEqual(config.local_ip, "127.0.0.1")
        self.assertEqual(rendered["smtp_helo_name"], "[127.0.0.1]")

    def test_local_ipv4_becomes_a_bracketed_helo_literal_without_changing_dns_hostname(self) -> None:
        config = smtp.parse_configuration({"SMTP_LOCAL_IP": "192.168.1.50"})
        rendered = settings(smtp.render_main_cf(config))
        self.assertEqual(config.local_ip, "192.168.1.50")
        self.assertEqual(rendered["smtp_helo_name"], "[192.168.1.50]")
        self.assertEqual(rendered["myhostname"], config.hostname)
        self.assertNotEqual(rendered["myhostname"], config.local_ip)
        for address in ("0.0.0.0", "255.255.255.255", "224.1.2.3", "::1", "[192.168.1.50]", "192.168.1.50/24", "999.1.2.3", "192.168.1.50\r\nrelayhost=bad"):
            with self.subTest(address=address), self.assertRaises(smtp.ConfigurationError):
                smtp.parse_configuration({"SMTP_LOCAL_IP": address})

    def test_starttls_relay_requires_tls_and_uses_bracketed_destination(self) -> None:
        config = smtp.parse_configuration(relay_env())
        rendered = settings(smtp.render_main_cf(config))
        self.assertTrue(config.auth)
        self.assertEqual(config.relay_destination, "[relay.example.test]:587")
        self.assertEqual(rendered["relayhost"], config.relay_destination)
        self.assertIn(rendered["smtp_tls_security_level"], {"verify", "secure"})
        self.assertEqual(rendered["smtp_tls_wrappermode"], "no")
        self.assertEqual(rendered["smtp_sasl_auth_enable"], "yes")
        self.assertNotIn(config.relay_password, smtp.render_main_cf(config))

    def test_ssl_relay_enables_wrapper_and_certificate_validation(self) -> None:
        config = smtp.parse_configuration(relay_env(SMTP_RELAY_PORT="465", SMTP_RELAY_SECURITY="SSL"))
        rendered = settings(smtp.render_main_cf(config))
        self.assertEqual(rendered["relayhost"], "[relay.example.test]:465")
        self.assertEqual(rendered["smtp_tls_wrappermode"], "yes")
        self.assertIn(rendered["smtp_tls_security_level"], {"verify", "secure"})

    def test_none_only_allows_unauthenticated_relay(self) -> None:
        with self.assertRaises(smtp.ConfigurationError):
            smtp.parse_configuration(relay_env(SMTP_RELAY_SECURITY="NONE"))
        config = smtp.parse_configuration(relay_env(SMTP_RELAY_SECURITY="NONE", SMTP_RELAY_USERNAME="", SMTP_RELAY_PASSWORD=""))
        rendered = settings(smtp.render_main_cf(config))
        self.assertEqual(rendered["smtp_sasl_auth_enable"], "no")
        self.assertEqual(rendered["smtp_tls_security_level"], "none")
        self.assertEqual(rendered["smtp_tls_wrappermode"], "no")

    def test_trust_is_limited_to_loopback_and_one_private_bounded_subnet(self) -> None:
        for subnet in ("10.25.30.0/24", "172.16.25.0/25", "192.168.20.64/26", "10.25.30.0/27", "172.31.55.16/28"):
            with self.subTest(subnet=subnet):
                config = smtp.parse_configuration({"SMTP_NETWORK_SUBNET": subnet})
                rendered = settings(smtp.render_main_cf(config))
                trusted = [ipaddress.ip_network(value.strip()) for value in rendered["mynetworks"].replace(",", " ").split()]
                self.assertEqual(set(trusted), {ipaddress.ip_network("127.0.0.1/32"), ipaddress.ip_network(subnet)})
                restrictions = rendered["smtpd_relay_restrictions"]
                rules = [value.strip() for value in restrictions.split(",")]
                self.assertEqual(rules[0], "permit_mynetworks")
                self.assertIn(rules[-1], {"reject", "reject_unauth_destination"})
                self.assertEqual(len(rules), 2)
                self.assertNotIn("permit_sasl_authenticated", restrictions)

    def test_public_broad_hostbit_and_ipv6_networks_are_rejected(self) -> None:
        for subnet in ("0.0.0.0/0", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "192.168.10.0/23",
                       "192.168.10.0/29", "192.168.10.1/24", "8.8.8.0/24", "100.64.0.0/24", "127.0.0.0/24",
                       "169.254.0.0/24", "198.18.0.0/24", "::/0", "fd00::/64", "not-a-network"):
            with self.subTest(subnet=subnet), self.assertRaises(smtp.ConfigurationError):
                smtp.parse_configuration({"SMTP_NETWORK_SUBNET": subnet})

    def test_hostname_rejects_control_characters_and_invalid_dns_labels(self) -> None:
        for hostname in ("localhost", "-smtp.example.test", "smtp-.example.test", "smtp..test", "smtp example.test",
                         "smtpä.example.test", "smtp.example.test\r\nmynetworks=0.0.0.0/0", "a" * 64 + ".example.test"):
            with self.subTest(hostname=hostname), self.assertRaises(smtp.ConfigurationError):
                smtp.parse_configuration({"SMTP_HOSTNAME": hostname})

    def test_relay_host_port_and_security_are_validated(self) -> None:
        for hostname in ("https://relay.example.test", "relay.example.test:587", "[relay.example.test]", "relay.example.test\nother=value", "relay example.test"):
            with self.subTest(hostname=hostname), self.assertRaises(smtp.ConfigurationError):
                smtp.parse_configuration(relay_env(SMTP_RELAY_HOST=hostname))
        for port in ("0", "65536", "-1", "25.0", "abc", "25\nrelayhost=x"):
            with self.subTest(port=port), self.assertRaises(smtp.ConfigurationError):
                smtp.parse_configuration(relay_env(SMTP_RELAY_PORT=port))
        for security in ("TLS", "STARTTLS\nother=value", "SSL\r\nother=value"):
            with self.subTest(security=security), self.assertRaises(smtp.ConfigurationError):
                smtp.parse_configuration(relay_env(SMTP_RELAY_SECURITY=security))

    def test_credentials_must_be_complete_and_cannot_inject_map_records(self) -> None:
        invalid = (
            {"SMTP_RELAY_USERNAME": ""}, {"SMTP_RELAY_PASSWORD": ""},
            {"SMTP_RELAY_USERNAME": "user:name"}, {"SMTP_RELAY_USERNAME": "user name"},
            {"SMTP_RELAY_USERNAME": "user\nother"}, {"SMTP_RELAY_PASSWORD": "secret\nother:password"},
            {"SMTP_RELAY_PASSWORD": "secret\rrecord"}, {"SMTP_RELAY_PASSWORD": "secret\x00record"},
            {"SMTP_RELAY_PASSWORD": "x" * 4097}, {"SMTP_RELAY_PASSWORD": "secret-with-trailing-space "},
        )
        for override in invalid:
            with self.subTest(keys=list(override)), self.assertRaises(smtp.ConfigurationError):
                smtp.parse_configuration(relay_env(**override))

    def test_secret_map_is_private_and_password_never_appears_in_command_or_output(self) -> None:
        config = smtp.parse_configuration(relay_env())
        commands: list[list[str]] = []

        def fake_postmap(command, **kwargs):
            commands.append(command)
            self.assertNotIn(config.relay_password, repr(command))
            self.assertEqual(kwargs.get("stdout"), subprocess.DEVNULL)
            self.assertEqual(kwargs.get("stderr"), subprocess.DEVNULL)
            map_path = Path(command[-1].split(":", 1)[1])
            Path(str(map_path) + ".db").write_bytes(b"simulated-index")
            return subprocess.CompletedProcess(command, 0)

        output = io.StringIO()
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            smtp.write_sasl_map(config, Path(directory), run=fake_postmap)
            source = Path(directory) / "sasl_passwd"
            indexed = Path(str(source) + ".db")
            self.assertEqual(source.read_text().rstrip("\n").split(maxsplit=1),
                             ["[relay.example.test]:587", f"soporte@example.test:{config.relay_password}"])
            self.assertEqual(stat.S_IMODE(source.stat().st_mode), 0o600)
            self.assertEqual(stat.S_IMODE(indexed.stat().st_mode), 0o600)
        self.assertEqual(len(commands), 1)
        self.assertEqual(commands[0][0], "postmap")
        self.assertNotIn(config.relay_password, repr(config))
        self.assertNotIn(config.relay_password, output.getvalue())

    def test_invalid_secret_error_never_echoes_the_secret(self) -> None:
        secret = "secret-invalid\nprivate-second-line"
        output = io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            with self.assertRaises(smtp.ConfigurationError) as raised:
                smtp.parse_configuration(relay_env(SMTP_RELAY_PASSWORD=secret))
        self.assertNotIn(secret, str(raised.exception))
        self.assertNotIn(secret, output.getvalue())

    def test_master_configuration_has_only_the_expected_smtp_listener(self) -> None:
        rendered = smtp.render_master_cf()
        listeners = [line.split() for line in rendered.splitlines() if line and not line[0].isspace() and not line.startswith("#")
                     and len(line.split()) > 1 and line.split()[1] == "inet"]
        self.assertEqual(len(listeners), 1)
        self.assertEqual(listeners[0][0], "smtp")
        self.assertEqual(listeners[0][-1], "smtpd")


if __name__ == "__main__":
    unittest.main()
