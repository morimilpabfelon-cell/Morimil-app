#!/usr/bin/env python3
"""Fail-closed wireless-ADB energy profile for the pinned Gemma 3n E2B candidate."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime, timezone
from typing import Any, Sequence

EXPECTED_FILENAME = "morimil-deliberative-v0.2.candidate.litertlm"
EXPECTED_SHA256 = "2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6"
EXPECTED_SIZE_BYTES = 3_655_827_456
EXPECTED_REPORT_SCHEMA = "morimil.android-arm64-sustained-profile.v0"
AGGREGATE_SCHEMA = "morimil.android-arm64-unplugged-energy-profile.v0"
REQUIRED_ABI = "arm64-v8a"
TARGET_PACKAGE = "com.morimil.app"
TEST_PACKAGE = "com.morimil.app.test"
RUNNER = "androidx.test.runner.AndroidJUnitRunner"
HARNESS_CLASS = (
    "com.morimil.app.reasoning.intrinsic."
    "Gemma3nE2bArm64SustainedProfileV0Test"
)
ENABLE_ARGUMENT = "morimilArm64SustainedProfileEnabled"
DEVICE_ROOT = "files/morimil-arm64-sustained-profile"
DEVICE_MODEL = f"{DEVICE_ROOT}/input/{EXPECTED_FILENAME}"
DEVICE_REPORT = f"{DEVICE_ROOT}/output/morimil-arm64-sustained-profile-v0.json"
EXPECTED_ROUNDS_PER_PASS = 6
MINIMUM_MEMORY_KIB = 6 * 1024 * 1024
MINIMUM_FREE_BYTES = (EXPECTED_SIZE_BYTES * 2) + (2 * 1024 * 1024 * 1024)
SEVERE_THERMAL_STATUS = 3


class ProfileError(RuntimeError):
    """A fail-closed profile precondition or validation failure."""


def stop(message: str) -> None:
    raise ProfileError(f"Detenido: {message}")


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def run_command(
    args: Sequence[str],
    *,
    step: str,
    allow_failure: bool = False,
    echo: bool = True,
    cwd: Path | None = None,
) -> subprocess.CompletedProcess[str]:
    if echo:
        print(f"\n==> {step}")
    completed = subprocess.run(
        list(args),
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
        cwd=str(cwd) if cwd is not None else None,
    )
    output = completed.stdout or ""
    if echo and output:
        print(output.rstrip())
    if not allow_failure and completed.returncode != 0:
        stop(f"fallo en '{step}' (codigo {completed.returncode})")
    return completed


def adb_args(adb_path: str, serial: str, *args: str) -> list[str]:
    return [adb_path, "-s", serial, *args]


def run_adb(
    adb_path: str,
    serial: str,
    *args: str,
    step: str,
    allow_failure: bool = False,
    echo: bool = True,
) -> subprocess.CompletedProcess[str]:
    return run_command(
        adb_args(adb_path, serial, *args),
        step=step,
        allow_failure=allow_failure,
        echo=echo,
    )


def run_adb_shell(
    adb_path: str,
    serial: str,
    command: str,
    *,
    step: str,
    allow_failure: bool = False,
    echo: bool = True,
) -> subprocess.CompletedProcess[str]:
    return run_adb(
        adb_path,
        serial,
        "shell",
        command,
        step=step,
        allow_failure=allow_failure,
        echo=echo,
    )


def parse_adb_devices(text: str) -> dict[str, str]:
    devices: dict[str, str] = {}
    for line in text.splitlines():
        match = re.match(r"^([^\s]+)\s+(device|offline|unauthorized)\b", line.strip())
        if match:
            devices[match.group(1)] = match.group(2)
    return devices


def ensure_wireless_serial(serial: str) -> None:
    match = re.fullmatch(r"((?:\d{1,3}\.){3}\d{1,3}):(\d{1,5})", serial)
    if not match:
        stop("--serial debe ser una direccion ADB inalambrica IPv4:puerto")
    octets = [int(value) for value in match.group(1).split(".")]
    port = int(match.group(2))
    if any(value < 0 or value > 255 for value in octets) or not (1 <= port <= 65535):
        stop("direccion ADB inalambrica invalida")


def parse_first_int(text: str, pattern: str, label: str) -> int:
    match = re.search(pattern, text, flags=re.MULTILINE)
    if not match:
        stop(f"no se pudo interpretar {label}")
    return int(match.group(1))


def parse_df_available_bytes(text: str) -> int:
    candidates = [
        line
        for line in text.splitlines()
        if re.search(r"\s/data(?:/\S*)?$", line.strip())
    ]
    if not candidates:
        stop("no se pudo interpretar df -k /data")
    columns = re.split(r"\s+", candidates[-1].strip())
    if len(columns) < 4:
        stop(f"salida df inesperada: {candidates[-1]}")
    return int(columns[3]) * 1024


def parse_battery_dump(text: str) -> dict[str, float | int]:
    def value(name: str) -> int:
        return parse_first_int(text, rf"^\s*{re.escape(name)}:\s*(-?\d+)\s*$", name)

    return {
        "levelPercent": value("level"),
        "temperatureCelsius": value("temperature") / 10.0,
        "plugged": value("plugged"),
        "status": value("status"),
    }


def atomic_write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".partial")
    with temporary.open("w", encoding="utf-8", newline="\n") as stream:
        json.dump(payload, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)


def percentile_nearest_rank(values: Sequence[int], percentile: float) -> int:
    if not values:
        stop("no hay valores para calcular percentil")
    ordered = sorted(values)
    index = max(0, math.ceil(percentile * len(ordered)) - 1)
    return ordered[index]


def environment_snapshots(report: dict[str, Any]) -> list[dict[str, Any]]:
    snapshots = report.get("environmentSnapshots")
    if not isinstance(snapshots, list) or not snapshots:
        stop("el informe no contiene muestras ambientales")
    return [dict(item) for item in snapshots]


def validate_pass_report(
    report: dict[str, Any],
    *,
    maximum_temperature_celsius: float,
) -> None:
    if report.get("schemaVersion") != EXPECTED_REPORT_SCHEMA:
        stop("esquema de informe Android inesperado")
    if report.get("status") != "passed" or report.get("researchGatePassed") is not True:
        stop("una pasada Android no termino en estado passed con gate aprobado")
    if report.get("expectedArtifactSha256") != f"sha256:{EXPECTED_SHA256}":
        stop("una pasada declaro otro hash esperado")
    if (
        report.get("hashBeforeFirst") != f"sha256:{EXPECTED_SHA256}"
        or report.get("hashAfter") != f"sha256:{EXPECTED_SHA256}"
        or report.get("hashStable") is not True
    ):
        stop("una pasada no confirmo integridad estable del artefacto")
    if report.get("process64Bit") is not True or report.get("requiredAbi") != REQUIRED_ABI:
        stop("una pasada no confirmo proceso arm64 de 64 bits")
    if report.get("engineInitialized") is not True or report.get("engineClosed") is not True:
        stop("una pasada no cerro correctamente LiteRT-LM")
    summary = report.get("summary")
    if not isinstance(summary, dict):
        stop("una pasada no contiene summary")
    if any(
        int(summary.get(field, -1)) != EXPECTED_ROUNDS_PER_PASS
        for field in ("requestedRounds", "completedRounds", "strictOutputPassCount")
    ):
        stop("una pasada no confirma seis rondas estrictas")
    if summary.get("allConversationsClosed") is not True:
        stop("una pasada dejo conversaciones abiertas")
    if summary.get("lowMemoryObserved") is not False:
        stop("Android declaro low-memory")
    if float(summary.get("maximumBatteryTemperatureCelsius", 999.0)) >= maximum_temperature_celsius:
        stop("temperatura fuera del limite del perfil energetico")
    if int(summary.get("maximumThermalStatus", 999)) >= SEVERE_THERMAL_STATUS:
        stop("estado termico severo detectado")
    if report.get("errors") not in ([], None):
        stop("una pasada contiene errores")
    if any(
        report.get(field) is not False
        for field in (
            "certified",
            "signed",
            "installed",
            "promotionAllowed",
            "productionAuthorization",
            "normalRuntimeActivated",
        )
    ):
        stop("una pasada no conserva el estado fail-closed")
    snapshots = environment_snapshots(report)
    if any(int(item.get("batteryPlugged", -1)) != 0 for item in snapshots):
        stop("se detecto alimentacion externa durante una pasada")


def aggregate_reports(
    reports: Sequence[dict[str, Any]],
    *,
    requested_passes: int,
    started_at: str,
    completed_at: str,
    elapsed_seconds: float,
    maximum_temperature_celsius: float,
    maximum_duration_minutes: float,
) -> dict[str, Any]:
    if len(reports) != requested_passes:
        stop("numero de informes distinto al numero de pasadas solicitado")
    for report in reports:
        validate_pass_report(
            report,
            maximum_temperature_celsius=maximum_temperature_celsius,
        )

    summaries = [dict(report["summary"]) for report in reports]
    all_rounds = [dict(round_item) for report in reports for round_item in report.get("rounds", [])]
    expected_rounds = requested_passes * EXPECTED_ROUNDS_PER_PASS
    if len(all_rounds) != expected_rounds:
        stop("numero agregado de rondas inesperado")

    latencies = [int(item["latencyMilliseconds"]) for item in all_rounds]
    all_env = [snapshot for report in reports for snapshot in environment_snapshots(report)]
    first_env = environment_snapshots(reports[0])[0]
    last_env = environment_snapshots(reports[-1])[-1]
    initial_charge = first_env.get("batteryChargeCounterMicroampHours")
    final_charge = last_env.get("batteryChargeCounterMicroampHours")
    charge_decrease_uah: int | None = None
    if isinstance(initial_charge, (int, float)) and isinstance(final_charge, (int, float)):
        charge_decrease_uah = int(initial_charge) - int(final_charge)

    initial_level = int(first_env["batteryLevelPercent"])
    final_level = int(last_env["batteryLevelPercent"])
    battery_percent_decrease = initial_level - final_level
    energy_observation_supported = (
        charge_decrease_uah is not None and charge_decrease_uah > 0
    ) or battery_percent_decrease > 0

    peak_pss_kib = max(int(summary["peakTotalPssKilobytes"]) for summary in summaries)
    minimum_available_bytes = min(int(summary["minimumSystemAvailableBytes"]) for summary in summaries)
    maximum_temperature = max(float(item["batteryTemperatureCelsius"]) for item in all_env)
    initial_temperature = float(first_env["batteryTemperatureCelsius"])
    maximum_thermal_item = max(all_env, key=lambda item: int(item["thermalStatus"]))
    maximum_thermal = int(maximum_thermal_item["thermalStatus"])
    all_unplugged = all(int(item["batteryPlugged"]) == 0 for item in all_env)
    all_strict = all(bool(item.get("strictOutputPassed")) for item in all_rounds)
    all_closed = all(bool(item.get("conversationClosed")) for item in all_rounds)
    duration_within_limit = elapsed_seconds <= maximum_duration_minutes * 60.0

    gate_passed = all(
        (
            all_unplugged,
            all_strict,
            all_closed,
            maximum_temperature < maximum_temperature_celsius,
            maximum_thermal < SEVERE_THERMAL_STATUS,
            duration_within_limit,
            energy_observation_supported,
        )
    )

    return {
        "schemaVersion": AGGREGATE_SCHEMA,
        "status": "passed" if gate_passed else "failed",
        "startedAt": started_at,
        "completedAt": completed_at,
        "elapsedSeconds": round(elapsed_seconds, 3),
        "requestedPasses": requested_passes,
        "completedPasses": len(reports),
        "requestedRounds": expected_rounds,
        "completedRounds": len(all_rounds),
        "strictOutputPassCount": sum(bool(item.get("strictOutputPassed")) for item in all_rounds),
        "allConversationsClosed": all_closed,
        "allSamplesUnplugged": all_unplugged,
        "minimumInferenceMilliseconds": min(latencies),
        "medianInferenceMilliseconds": percentile_nearest_rank(latencies, 0.5),
        "p95InferenceMilliseconds": percentile_nearest_rank(latencies, 0.95),
        "maximumInferenceMilliseconds": max(latencies),
        "averageInferenceMilliseconds": sum(latencies) / len(latencies),
        "peakTotalPssKilobytes": peak_pss_kib,
        "minimumSystemAvailableBytes": minimum_available_bytes,
        "initialBatteryLevelPercent": initial_level,
        "finalBatteryLevelPercent": final_level,
        "batteryPercentDecrease": battery_percent_decrease,
        "initialChargeCounterMicroampHours": initial_charge,
        "finalChargeCounterMicroampHours": final_charge,
        "observedChargeDecreaseMicroampHours": charge_decrease_uah,
        "observedChargeDecreaseMilliampHours": (
            charge_decrease_uah / 1000.0 if charge_decrease_uah is not None else None
        ),
        "energyObservationSupported": energy_observation_supported,
        "initialBatteryTemperatureCelsius": initial_temperature,
        "maximumBatteryTemperatureCelsius": maximum_temperature,
        "batteryTemperatureIncreaseCelsius": maximum_temperature - initial_temperature,
        "maximumThermalStatus": maximum_thermal,
        "maximumThermalStatusName": str(maximum_thermal_item.get("thermalStatusName", "unknown")),
        "maximumDurationMinutes": maximum_duration_minutes,
        "maximumBatteryTemperatureCelsiusExclusive": maximum_temperature_celsius,
        "researchGatePassed": gate_passed,
        "sourceModelRevision": None,
        "certified": False,
        "signed": False,
        "installed": False,
        "promotionAllowed": False,
        "productionAuthorization": False,
        "normalRuntimeActivated": False,
        "passReports": reports,
        "errors": [] if gate_passed else ["unplugged_energy_gate_failed"],
    }


def validate_local_artifact(path: Path) -> None:
    if not path.is_file():
        stop(f"el artefacto no existe: {path}")
    if path.is_symlink():
        stop("el artefacto local no puede ser enlace simbolico")
    if path.name != EXPECTED_FILENAME:
        stop(f"nombre de artefacto inesperado: {path.name}")
    if path.stat().st_size != EXPECTED_SIZE_BYTES:
        stop(f"tamano local inesperado: {path.stat().st_size}")
    print("==> Verificar dos veces el artefacto local exacto")
    first = sha256_file(path)
    second = sha256_file(path)
    if first != second:
        stop("el hash local no fue estable")
    if first != EXPECTED_SHA256:
        stop(f"SHA-256 local inesperado: {first}")


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Perfil energetico ARM64 sin cargador para el candidato Gemma 3n E2B.",
    )
    parser.add_argument("artifact_path", nargs="?", type=Path)
    parser.add_argument("--serial", help="ADB inalambrico IPv4:puerto")
    parser.add_argument("--passes", type=int, default=6)
    parser.add_argument("--report-directory", type=Path)
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--minimum-battery-percent", type=int, default=50)
    parser.add_argument("--maximum-battery-temperature-celsius", type=float, default=43.0)
    parser.add_argument("--maximum-duration-minutes", type=float, default=15.0)
    parser.add_argument("--cooldown-seconds", type=int, default=5)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        return args
    if args.artifact_path is None or not args.serial:
        parser.error("artifact_path y --serial son obligatorios")
    if not 3 <= args.passes <= 8:
        parser.error("--passes debe estar entre 3 y 8")
    if not 40 <= args.minimum_battery_percent <= 100:
        parser.error("--minimum-battery-percent debe estar entre 40 y 100")
    if not 40.0 <= args.maximum_battery_temperature_celsius <= 44.0:
        parser.error("--maximum-battery-temperature-celsius debe estar entre 40 y 44")
    if not 8.0 <= args.maximum_duration_minutes <= 25.0:
        parser.error("--maximum-duration-minutes debe estar entre 8 y 25")
    if not 0 <= args.cooldown_seconds <= 30:
        parser.error("--cooldown-seconds debe estar entre 0 y 30")
    return args


def synthetic_report(*, initial_charge: int, final_charge: int) -> dict[str, Any]:
    rounds = [
        {
            "latencyMilliseconds": 7000 + index,
            "strictOutputPassed": True,
            "conversationClosed": True,
        }
        for index in range(EXPECTED_ROUNDS_PER_PASS)
    ]
    env = [
        {
            "batteryLevelPercent": 70,
            "batteryTemperatureCelsius": 35.0,
            "batteryChargeCounterMicroampHours": initial_charge,
            "batteryPlugged": 0,
            "thermalStatus": 0,
            "thermalStatusName": "none",
        },
        {
            "batteryLevelPercent": 69,
            "batteryTemperatureCelsius": 36.0,
            "batteryChargeCounterMicroampHours": final_charge,
            "batteryPlugged": 0,
            "thermalStatus": 0,
            "thermalStatusName": "none",
        },
    ]
    return {
        "schemaVersion": EXPECTED_REPORT_SCHEMA,
        "status": "passed",
        "researchGatePassed": True,
        "expectedArtifactSha256": f"sha256:{EXPECTED_SHA256}",
        "hashBeforeFirst": f"sha256:{EXPECTED_SHA256}",
        "hashAfter": f"sha256:{EXPECTED_SHA256}",
        "hashStable": True,
        "process64Bit": True,
        "requiredAbi": REQUIRED_ABI,
        "engineInitialized": True,
        "engineClosed": True,
        "rounds": rounds,
        "environmentSnapshots": env,
        "summary": {
            "requestedRounds": EXPECTED_ROUNDS_PER_PASS,
            "completedRounds": EXPECTED_ROUNDS_PER_PASS,
            "strictOutputPassCount": EXPECTED_ROUNDS_PER_PASS,
            "allConversationsClosed": True,
            "lowMemoryObserved": False,
            "maximumBatteryTemperatureCelsius": 36.0,
            "maximumThermalStatus": 0,
            "peakTotalPssKilobytes": 2_650_000,
            "minimumSystemAvailableBytes": 5_900_000_000,
        },
        "errors": [],
        "certified": False,
        "signed": False,
        "installed": False,
        "promotionAllowed": False,
        "productionAuthorization": False,
        "normalRuntimeActivated": False,
    }


def run_self_test() -> int:
    assert parse_adb_devices("List of devices attached\n1.2.3.4:5555\tdevice\n") == {
        "1.2.3.4:5555": "device"
    }
    ensure_wireless_serial("192.168.1.2:5555")
    assert parse_df_available_bytes(
        "Filesystem 1K-blocks Used Available Use% Mounted on\n"
        "/dev/block/dm 100 20 80 20% /data/user/0\n"
    ) == 80 * 1024
    assert parse_battery_dump(
        "level: 55\ntemperature: 368\nplugged: 0\nstatus: 3\n"
    ) == {
        "levelPercent": 55,
        "temperatureCelsius": 36.8,
        "plugged": 0,
        "status": 3,
    }
    reports = [
        synthetic_report(initial_charge=2_000_000 - i * 10_000, final_charge=1_990_000 - i * 10_000)
        for i in range(3)
    ]
    aggregate = aggregate_reports(
        reports,
        requested_passes=3,
        started_at="2026-01-01T00:00:00Z",
        completed_at="2026-01-01T00:05:00Z",
        elapsed_seconds=300.0,
        maximum_temperature_celsius=43.0,
        maximum_duration_minutes=15.0,
    )
    assert aggregate["researchGatePassed"] is True
    assert aggregate["completedRounds"] == 18
    assert aggregate["allSamplesUnplugged"] is True
    assert aggregate["observedChargeDecreaseMicroampHours"] == 30_000
    print("SELF-TEST UNPLUGGED ENERGY V0: PASS")
    return 0


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv if argv is not None else sys.argv[1:])
    if args.self_test:
        return run_self_test()

    ensure_wireless_serial(args.serial)
    artifact_path = args.artifact_path.expanduser().resolve(strict=False)
    validate_local_artifact(artifact_path)

    adb_path = shutil.which("adb")
    if not adb_path:
        stop("adb no esta instalado o no esta en PATH")

    devices_result = run_command([adb_path, "devices"], step="Enumerar dispositivos adb")
    devices = parse_adb_devices(devices_result.stdout or "")
    if devices.get(args.serial) != "device":
        stop(f"el dispositivo inalambrico {args.serial} no esta en estado device")

    run_adb(adb_path, args.serial, "get-state", step="Confirmar estado del dispositivo")
    abi = run_adb(
        adb_path,
        args.serial,
        "shell",
        "getprop ro.product.cpu.abilist",
        step="Comprobar ABI del dispositivo",
    ).stdout.strip()
    if REQUIRED_ABI not in abi.split(","):
        stop(f"el dispositivo no declara {REQUIRED_ABI}; ABI: {abi}")
    sdk = int(
        run_adb(
            adb_path,
            args.serial,
            "shell",
            "getprop ro.build.version.sdk",
            step="Comprobar version Android",
        ).stdout.strip()
    )
    if sdk < 29:
        stop(f"el perfil requiere Android API 29 o superior; API={sdk}")
    qemu = run_adb(
        adb_path,
        args.serial,
        "shell",
        "getprop ro.kernel.qemu",
        step="Rechazar emulador",
    ).stdout.strip()
    if qemu == "1":
        stop("este perfil requiere un dispositivo fisico")

    meminfo = run_adb_shell(
        adb_path,
        args.serial,
        "cat /proc/meminfo | head -n 1",
        step="Leer memoria fisica",
    ).stdout
    memory_kib = parse_first_int(meminfo, r"MemTotal:\s+(\d+)\s+kB", "MemTotal")
    if memory_kib < MINIMUM_MEMORY_KIB:
        stop(f"el dispositivo declara menos de 6 GiB de RAM; MemTotal={memory_kib} kB")
    df_text = run_adb_shell(
        adb_path,
        args.serial,
        "df -k /data",
        step="Comprobar espacio libre en /data",
    ).stdout
    if parse_df_available_bytes(df_text) < MINIMUM_FREE_BYTES:
        stop("espacio insuficiente en /data para staging doble")

    battery = parse_battery_dump(
        run_adb_shell(
            adb_path,
            args.serial,
            "dumpsys battery",
            step="Confirmar bateria desconectada antes de iniciar",
        ).stdout
    )
    if int(battery["plugged"]) != 0:
        stop("el telefono sigue conectado a una fuente de energia")
    if int(battery["levelPercent"]) < args.minimum_battery_percent:
        stop(f"bateria inicial insuficiente: {battery['levelPercent']}%")
    if float(battery["temperatureCelsius"]) >= args.maximum_battery_temperature_celsius:
        stop(f"temperatura inicial demasiado alta: {battery['temperatureCelsius']} C")

    repo_root = Path(__file__).resolve().parents[2]
    gradle_wrapper = repo_root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    debug_apk = repo_root / "app/build/outputs/apk/debug/app-debug.apk"
    test_apk = repo_root / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    if not args.skip_build:
        run_command(
            [str(gradle_wrapper), ":app:assembleDebug", ":app:assembleDebugAndroidTest"],
            step="Compilar APK debug y androidTest",
            cwd=repo_root,
        )
    if not debug_apk.is_file() or not test_apk.is_file():
        stop("faltan APK debug o androidTest")

    run_adb(adb_path, args.serial, "install", "-r", "-t", str(debug_apk), step="Instalar APK debug")
    run_adb(adb_path, args.serial, "install", "-r", "-t", str(test_apk), step="Instalar APK de instrumentacion")
    run_adb_shell(
        adb_path,
        args.serial,
        f"am force-stop {TARGET_PACKAGE}",
        step="Detener procesos de Morimil",
        allow_failure=True,
    )

    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    report_root = (
        args.report_directory.expanduser().resolve()
        if args.report_directory
        else repo_root / "build/morimil-arm64-unplugged-energy-reports"
    )
    run_directory = report_root / stamp
    run_directory.mkdir(parents=True, exist_ok=True)
    aggregate_path = run_directory / "morimil-arm64-unplugged-energy-v0.json"
    remote_temporary = f"/data/local/tmp/{EXPECTED_FILENAME}.{os.urandom(8).hex()}.partial"
    reports: list[dict[str, Any]] = []

    try:
        run_adb_shell(
            adb_path,
            args.serial,
            f"run-as {TARGET_PACKAGE} rm -rf {DEVICE_ROOT}",
            step="Eliminar staging privado anterior",
            allow_failure=True,
        )
        run_adb(
            adb_path,
            args.serial,
            "push",
            str(artifact_path),
            remote_temporary,
            step="Subir candidato por ADB inalambrico",
        )
        remote_size = int(
            run_adb_shell(
                adb_path,
                args.serial,
                f"wc -c < '{remote_temporary}'",
                step="Verificar tamano del staging shell",
            ).stdout.strip().split()[0]
        )
        if remote_size != EXPECTED_SIZE_BYTES:
            stop(f"tamano remoto inesperado: {remote_size}")
        remote_hash = run_adb_shell(
            adb_path,
            args.serial,
            f"sha256sum '{remote_temporary}'",
            step="Verificar SHA-256 del staging shell",
        ).stdout.strip().split()[0].lower()
        if remote_hash != EXPECTED_SHA256:
            stop(f"SHA-256 remoto inesperado: {remote_hash}")

        run_adb_shell(
            adb_path,
            args.serial,
            f"run-as {TARGET_PACKAGE} sh -c 'mkdir -p {DEVICE_ROOT}/input {DEVICE_ROOT}/output'",
            step="Preparar staging privado",
        )
        run_adb_shell(
            adb_path,
            args.serial,
            (
                f"cat '{remote_temporary}' | run-as {TARGET_PACKAGE} sh -c "
                f"'cat > {DEVICE_MODEL}.partial && mv {DEVICE_MODEL}.partial {DEVICE_MODEL} "
                f"&& chmod 400 {DEVICE_MODEL}'"
            ),
            step="Copiar candidato al staging privado y volverlo solo lectura",
        )
        private_hash = run_adb_shell(
            adb_path,
            args.serial,
            f"run-as {TARGET_PACKAGE} sha256sum {DEVICE_MODEL}",
            step="Verificar SHA-256 del staging privado",
        ).stdout.strip().split()[0].lower()
        if private_hash != EXPECTED_SHA256:
            stop(f"SHA-256 privado inesperado: {private_hash}")

        profile_started_at = utc_now()
        profile_started_monotonic = time.monotonic()

        for pass_index in range(1, args.passes + 1):
            elapsed = time.monotonic() - profile_started_monotonic
            if elapsed >= args.maximum_duration_minutes * 60.0:
                stop("se alcanzo el limite temporal antes de completar las pasadas")
            battery = parse_battery_dump(
                run_adb_shell(
                    adb_path,
                    args.serial,
                    "dumpsys battery",
                    step=f"Comprobar bateria antes de pasada {pass_index}/{args.passes}",
                ).stdout
            )
            if int(battery["plugged"]) != 0:
                stop(f"se detecto alimentacion externa antes de la pasada {pass_index}")
            required_level = args.minimum_battery_percent if pass_index == 1 else 20
            if int(battery["levelPercent"]) < required_level:
                stop(f"bateria por debajo de {required_level}% antes de la pasada {pass_index}")
            if float(battery["temperatureCelsius"]) >= args.maximum_battery_temperature_celsius:
                stop(f"temperatura limite alcanzada antes de la pasada {pass_index}")

            transcript_path = run_directory / f"pass-{pass_index:02d}-instrumentation.txt"
            report_path = run_directory / f"pass-{pass_index:02d}.json"
            instrumentation = run_adb_shell(
                adb_path,
                args.serial,
                (
                    f"am instrument -w -r -e {ENABLE_ARGUMENT} true "
                    f"-e class {HARNESS_CLASS} {TEST_PACKAGE}/{RUNNER}"
                ),
                step=f"Ejecutar pasada sostenida {pass_index}/{args.passes}",
                allow_failure=True,
            )
            transcript_path.write_text((instrumentation.stdout or "") + "\n", encoding="utf-8")
            if instrumentation.returncode != 0:
                stop(f"instrumentacion fallo en pasada {pass_index}")
            output = instrumentation.stdout or ""
            if "FAILURES!!!" in output or "INSTRUMENTATION_FAILED" in output or "OK (2 tests)" not in output:
                stop(f"instrumentacion invalida en pasada {pass_index}; revise {transcript_path}")

            report_result = run_adb(
                adb_path,
                args.serial,
                "exec-out",
                "run-as",
                TARGET_PACKAGE,
                "cat",
                DEVICE_REPORT,
                step=f"Extraer informe Android de pasada {pass_index}",
            )
            report_text = (report_result.stdout or "").strip()
            try:
                report = json.loads(report_text)
            except json.JSONDecodeError as error:
                stop(f"JSON invalido en pasada {pass_index}: {error}")
            validate_pass_report(
                report,
                maximum_temperature_celsius=args.maximum_battery_temperature_celsius,
            )
            atomic_write_json(report_path, report)
            reports.append(report)
            if pass_index < args.passes and args.cooldown_seconds:
                print(f"\n==> Enfriamiento controlado: {args.cooldown_seconds} s")
                time.sleep(args.cooldown_seconds)

        profile_completed_at = utc_now()
        elapsed_seconds = time.monotonic() - profile_started_monotonic
        aggregate = aggregate_reports(
            reports,
            requested_passes=args.passes,
            started_at=profile_started_at,
            completed_at=profile_completed_at,
            elapsed_seconds=elapsed_seconds,
            maximum_temperature_celsius=args.maximum_battery_temperature_celsius,
            maximum_duration_minutes=args.maximum_duration_minutes,
        )
        atomic_write_json(aggregate_path, aggregate)
        if aggregate["researchGatePassed"] is not True:
            stop(f"el perfil energetico no supero el gate; revise {aggregate_path}")

        print("\nPERFIL ENERGETICO ANDROID ARM64 SIN CARGADOR V0 COMPLETADO.")
        print(f"Pasadas:                 {aggregate['completedPasses']}/{aggregate['requestedPasses']}")
        print(f"Rondas estrictas:        {aggregate['strictOutputPassCount']}/{aggregate['requestedRounds']}")
        print(f"Duracion:                {aggregate['elapsedSeconds']:.1f} s")
        print(f"Latencia mediana:        {aggregate['medianInferenceMilliseconds']} ms")
        print(f"Latencia p95:            {aggregate['p95InferenceMilliseconds']} ms")
        print(f"PSS maximo:              {aggregate['peakTotalPssKilobytes'] / 1024.0:.1f} MiB")
        print(f"Bateria inicial/final:   {aggregate['initialBatteryLevelPercent']}% / {aggregate['finalBatteryLevelPercent']}%")
        print(f"Carga observada:         {aggregate['observedChargeDecreaseMilliampHours']} mAh")
        print(f"Temperatura maxima:      {aggregate['maximumBatteryTemperatureCelsius']} C")
        print(f"Muestras sin cargador:   {aggregate['allSamplesUnplugged']}")
        print(f"Gate de investigacion:   {aggregate['researchGatePassed']}")
        print(f"Informe agregado:        {aggregate_path}")
        print("\nNO certificado, NO firmado, NO instalado y promocion bloqueada.")
        return 0
    finally:
        run_adb_shell(
            adb_path,
            args.serial,
            f"rm -f '{remote_temporary}'",
            step="Eliminar staging shell temporal",
            allow_failure=True,
        )
        run_adb_shell(
            adb_path,
            args.serial,
            f"run-as {TARGET_PACKAGE} rm -rf {DEVICE_ROOT}",
            step="Eliminar staging privado del APK",
            allow_failure=True,
        )
        run_adb_shell(
            adb_path,
            args.serial,
            f"am force-stop {TARGET_PACKAGE}",
            step="Detener proceso de Morimil",
            allow_failure=True,
        )


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ProfileError as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
