#!/usr/bin/env python3
"""Compatibility entrypoint for Android wireless ADB TLS mDNS and Xiaomi battery dumps."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import re
import sys
from types import ModuleType
from typing import Sequence

BASE_RUNNER_FILENAME = "run_gemma3n_e2b_arm64_unplugged_energy_v0.py"
IPV4_PORT_PATTERN = re.compile(r"^((?:\d{1,3}\.){3}\d{1,3}):(\d{1,5})$")
MDNS_TLS_CONNECT_PATTERN = re.compile(
    r"^adb-[A-Za-z0-9._-]+\._adb-tls-connect\._tcp$"
)
POWER_SOURCE_BITS = (
    ("AC", 1),
    ("USB", 2),
    ("Wireless", 4),
    ("Dock", 8),
)


def wireless_serial_kind(serial: str) -> str:
    """Return the accepted wireless serial kind or raise ValueError."""
    if not serial or serial != serial.strip() or any(character.isspace() for character in serial):
        raise ValueError("serial ADB inalambrico vacio o con espacios")

    ipv4_match = IPV4_PORT_PATTERN.fullmatch(serial)
    if ipv4_match:
        octets = [int(value) for value in ipv4_match.group(1).split(".")]
        port = int(ipv4_match.group(2))
        if any(value < 0 or value > 255 for value in octets) or not (1 <= port <= 65535):
            raise ValueError("direccion ADB IPv4:puerto invalida")
        return "ipv4_port"

    if len(serial) <= 255 and MDNS_TLS_CONNECT_PATTERN.fullmatch(serial):
        return "mdns_tls_connect"

    raise ValueError(
        "--serial debe ser IPv4:puerto o un servicio mDNS "
        "adb-..._adb-tls-connect._tcp"
    )


def powered_flags_to_plugged(text: str) -> int:
    """Convert Android powered booleans to BatteryManager plugged bit flags."""
    plugged = 0
    for source_name, source_bit in POWER_SOURCE_BITS:
        match = re.search(
            rf"^\s*{re.escape(source_name)} powered:\s*(true|false)\s*$",
            text,
            flags=re.MULTILINE | re.IGNORECASE,
        )
        if not match:
            raise ValueError(f"no se pudo interpretar {source_name} powered")
        if match.group(1).lower() == "true":
            plugged |= source_bit
    return plugged


def load_base_runner() -> ModuleType:
    runner_path = Path(__file__).resolve().with_name(BASE_RUNNER_FILENAME)
    if not runner_path.is_file():
        raise RuntimeError(f"Detenido: falta el runner base: {runner_path}")

    spec = importlib.util.spec_from_file_location(
        "morimil_unplugged_energy_v0_base",
        runner_path,
    )
    if spec is None or spec.loader is None:
        raise RuntimeError("Detenido: no se pudo cargar el runner base")

    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def patch_compatibility(base_runner: ModuleType) -> None:
    original_parse_battery_dump = base_runner.parse_battery_dump

    def ensure_wireless_serial_v0_1(serial: str) -> None:
        try:
            wireless_serial_kind(serial)
        except ValueError as error:
            base_runner.stop(str(error))

    def parse_battery_dump_v0_1(text: str) -> dict[str, float | int]:
        try:
            return original_parse_battery_dump(text)
        except base_runner.ProfileError as error:
            if "no se pudo interpretar plugged" not in str(error):
                raise

        try:
            plugged = powered_flags_to_plugged(text)
        except ValueError as error:
            base_runner.stop(str(error))

        return {
            "levelPercent": base_runner.parse_first_int(
                text,
                r"^\s*level:\s*(-?\d+)\s*$",
                "level",
            ),
            "temperatureCelsius": base_runner.parse_first_int(
                text,
                r"^\s*temperature:\s*(-?\d+)\s*$",
                "temperature",
            )
            / 10.0,
            "plugged": plugged,
            "status": base_runner.parse_first_int(
                text,
                r"^\s*status:\s*(-?\d+)\s*$",
                "status",
            ),
        }

    base_runner.ensure_wireless_serial = ensure_wireless_serial_v0_1
    base_runner.parse_battery_dump = parse_battery_dump_v0_1


def run_self_test() -> int:
    assert wireless_serial_kind("192.168.1.32:45109") == "ipv4_port"
    assert (
        wireless_serial_kind(
            "adb-EY998HXKNJSSFA7T-MglFBH._adb-tls-connect._tcp"
        )
        == "mdns_tls_connect"
    )

    rejected = (
        "EY998HXKNJSSFA7T",
        "adb-device._adb-tls-pairing._tcp",
        "192.168.1.32:0",
        "192.168.1.999:45109",
        " adb-device._adb-tls-connect._tcp",
    )
    for serial in rejected:
        try:
            wireless_serial_kind(serial)
        except ValueError:
            continue
        raise AssertionError(f"serial inseguro aceptado: {serial}")

    base_runner = load_base_runner()
    patch_compatibility(base_runner)

    xiaomi_unplugged = """Current Battery Service state:
  AC powered: false
  USB powered: false
  Wireless powered: false
  Dock powered: false
  status: 3
  level: 59
  temperature: 314
"""
    assert base_runner.parse_battery_dump(xiaomi_unplugged) == {
        "levelPercent": 59,
        "temperatureCelsius": 31.4,
        "plugged": 0,
        "status": 3,
    }

    xiaomi_usb = xiaomi_unplugged.replace("USB powered: false", "USB powered: true")
    assert base_runner.parse_battery_dump(xiaomi_usb)["plugged"] == 2

    assert base_runner.run_self_test() == 0
    print("SELF-TEST UNPLUGGED ENERGY MDNS/XIAOMI V0.1: PASS")
    return 0


def main(argv: Sequence[str] | None = None) -> int:
    arguments = list(argv if argv is not None else sys.argv[1:])
    if "--self-test" in arguments:
        return run_self_test()

    base_runner = load_base_runner()
    patch_compatibility(base_runner)
    return int(base_runner.main(arguments))


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
