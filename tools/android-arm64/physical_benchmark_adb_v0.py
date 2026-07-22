"""ADB staging and lifecycle support for the Morimil physical benchmark."""
from __future__ import annotations

import os
import re
import subprocess
from pathlib import Path
from typing import Sequence

from physical_benchmark_contract_v0 import (
    EXPECTED_FILENAME,
    EXPECTED_SHA256,
    EXPECTED_SIZE_BYTES,
    RunnerError,
    fail,
    sha256_file,
)

REQUIRED_ABI = "arm64-v8a"
TARGET_PACKAGE = "com.morimil.app"
DEVICE_ROOT = "files/morimil-arm64-deliberative-benchmark-v0"
DEVICE_MODEL = f"{DEVICE_ROOT}/input/{EXPECTED_FILENAME}"
DEVICE_RESPONSES = f"{DEVICE_ROOT}/output/responses-v0.2.jsonl"
DEVICE_PHYSICAL_REPORT = f"{DEVICE_ROOT}/output/physical-execution-v0.2.json"
MINIMUM_MEMORY_KIB = 6 * 1024 * 1024
MINIMUM_FREE_BYTES = EXPECTED_SIZE_BYTES * 2 + 2 * 1024 * 1024 * 1024

DEFAULT_HOST_TIMEOUT_SECONDS = 15 * 60
DEFAULT_ADB_TIMEOUT_SECONDS = 30
APK_INSTALL_TIMEOUT_SECONDS = 5 * 60
ARTIFACT_TRANSFER_TIMEOUT_SECONDS = 15 * 60
ARTIFACT_HASH_TIMEOUT_SECONDS = 10 * 60
PRIVATE_COPY_TIMEOUT_SECONDS = 15 * 60
OUTPUT_EXTRACTION_TIMEOUT_SECONDS = 2 * 60


def _timeout_output(error: subprocess.TimeoutExpired) -> str:
    output = error.stdout or error.output or ""
    if isinstance(output, bytes):
        return output.decode("utf-8", errors="replace")
    return str(output)


def command(
    label: str,
    args: Sequence[str],
    *,
    allow_failure: bool = False,
    cwd: Path | None = None,
    timeout_seconds: float = DEFAULT_HOST_TIMEOUT_SECONDS,
    input_text: str | None = None,
) -> subprocess.CompletedProcess[str]:
    if timeout_seconds <= 0:
        fail(f"{label} received an invalid timeout: {timeout_seconds}")
    print(f"\n==> {label}")
    try:
        result = subprocess.run(
            list(args),
            check=False,
            cwd=str(cwd) if cwd else None,
            text=True,
            input=input_text,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            encoding="utf-8",
            errors="replace",
            timeout=timeout_seconds,
        )
    except subprocess.TimeoutExpired as error:
        output = _timeout_output(error)
        if output:
            print(output, end="" if output.endswith("\n") else "\n")
        fail(f"{label} timed out after {timeout_seconds:g} seconds")
    if result.stdout:
        print(result.stdout, end="" if result.stdout.endswith("\n") else "\n")
    if result.returncode and not allow_failure:
        fail(f"{label} failed with exit code {result.returncode}")
    return result


class Adb:
    def __init__(self, executable: str, serial: str) -> None:
        self.prefix = [executable, "-s", serial]

    def run(
        self,
        label: str,
        args: Sequence[str],
        *,
        allow_failure: bool = False,
        timeout_seconds: float = DEFAULT_ADB_TIMEOUT_SECONDS,
        input_text: str | None = None,
    ) -> subprocess.CompletedProcess[str]:
        return command(
            label,
            [*self.prefix, *args],
            allow_failure=allow_failure,
            timeout_seconds=timeout_seconds,
            input_text=input_text,
        )

    def text(
        self,
        label: str,
        args: Sequence[str],
        *,
        timeout_seconds: float = DEFAULT_ADB_TIMEOUT_SECONDS,
    ) -> str:
        return self.run(label, args, timeout_seconds=timeout_seconds).stdout.strip()


def first_token(value: str, label: str) -> str:
    tokens = value.split()
    if not tokens:
        fail(f"{label} returned no value")
    return tokens[0]


def parse_byte_count(value: str, label: str) -> int:
    """Parse `wc -c FILE` output and reject ambiguous multi-metric output."""
    tokens = value.split()
    if len(tokens) not in (1, 2) or not tokens[0].isdigit():
        fail(f"{label} returned an ambiguous byte count: {value!r}")
    return int(tokens[0])


def absolute_device_path(value: str, label: str) -> str:
    """Accept only simple absolute Android paths safe for a fixed stdin script."""
    if not re.fullmatch(r"/[A-Za-z0-9._/-]+", value):
        fail(f"{label} is not a safe absolute device path: {value!r}")
    if any(part in {"", ".", ".."} for part in value.split("/")[1:]):
        fail(f"{label} contains an unsafe path component: {value!r}")
    return value


def resolve_serial(adb: str, requested: str | None) -> str:
    if requested:
        return requested.strip()
    result = command(
        "Enumerate adb devices",
        [adb, "devices"],
        timeout_seconds=DEFAULT_ADB_TIMEOUT_SECONDS,
    )
    devices = []
    for line in result.stdout.splitlines():
        match = re.fullmatch(r"([^\s]+)\s+device", line.strip())
        if match:
            devices.append(match.group(1))
    if len(devices) != 1:
        fail(f"exactly one adb device is required; found {len(devices)}")
    return devices[0]


def verify_artifact(path: Path) -> tuple[Path, str]:
    expanded = path.expanduser()
    if expanded.is_symlink():
        fail("artifact symlinks are rejected")
    resolved = expanded.resolve(strict=True)
    if not resolved.is_file() or resolved.name != EXPECTED_FILENAME:
        fail(f"unexpected artifact path or filename: {resolved}")
    if resolved.stat().st_size != EXPECTED_SIZE_BYTES:
        fail(f"unexpected artifact size: {resolved.stat().st_size}")
    first, second = sha256_file(resolved), sha256_file(resolved)
    if first != second or first != f"sha256:{EXPECTED_SHA256}":
        fail(f"artifact SHA-256 mismatch or instability: {first} / {second}")
    return resolved, first


def verify_device(adb: Adb) -> None:
    if adb.text("Confirm device state", ["get-state"]) != "device":
        fail("adb device is not ready")
    abis = adb.text(
        "Read device ABIs", ["shell", "getprop", "ro.product.cpu.abilist"]
    ).split(",")
    if REQUIRED_ABI not in abis:
        fail(f"device does not declare {REQUIRED_ABI}: {abis}")
    sdk = int(first_token(adb.text(
        "Read Android API", ["shell", "getprop", "ro.build.version.sdk"]
    ), "Android API"))
    if sdk < 29:
        fail(f"Android API 29 or newer is required; found {sdk}")
    if adb.text("Reject emulator", ["shell", "getprop", "ro.kernel.qemu"]) == "1":
        fail("a physical Android device is required")
    meminfo = adb.text(
        "Read device memory", ["shell", "head", "-n", "1", "/proc/meminfo"]
    )
    match = re.search(r"MemTotal:\s+(\d+)\s+kB", meminfo)
    if not match or int(match.group(1)) < MINIMUM_MEMORY_KIB:
        fail(f"device must expose at least 6 GiB RAM: {meminfo}")
    disk = adb.text("Read /data free space", ["shell", "df", "-k", "/data"])
    rows = [line for line in disk.splitlines() if re.search(r"\s/data(?:/\S*)?$", line)]
    if not rows or len(rows[-1].split()) < 4:
        fail("could not parse /data free space")
    if int(rows[-1].split()[3]) * 1024 < MINIMUM_FREE_BYTES:
        fail("insufficient /data free space for bounded staging")


def build_and_install(adb: Adb, root: Path, skip_build: bool) -> None:
    debug = root / "app/build/outputs/apk/debug/app-debug.apk"
    tests = root / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    if not skip_build:
        wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
        if not wrapper.is_file():
            fail(f"missing Gradle wrapper: {wrapper}")
        command(
            "Build debug and instrumentation APKs",
            [str(wrapper), ":app:assembleDebug", ":app:assembleDebugAndroidTest"],
            cwd=root,
            timeout_seconds=DEFAULT_HOST_TIMEOUT_SECONDS,
        )
    if not debug.is_file() or not tests.is_file():
        fail("required APK outputs are missing")
    adb.run(
        "Install debug APK",
        ["install", "-r", "-t", str(debug)],
        timeout_seconds=APK_INSTALL_TIMEOUT_SECONDS,
    )
    adb.run(
        "Install instrumentation APK",
        ["install", "-r", "-t", str(tests)],
        timeout_seconds=APK_INSTALL_TIMEOUT_SECONDS,
    )
    adb.run(
        "Stop Morimil before benchmark",
        ["shell", "am", "force-stop", TARGET_PACKAGE],
        allow_failure=True,
    )


def stage_artifact(adb: Adb, artifact: Path, temporary: str) -> None:
    temporary = absolute_device_path(temporary, "temporary artifact path")
    private_input = f"{DEVICE_ROOT}/input"
    private_output = f"{DEVICE_ROOT}/output"
    partial_model = f"{DEVICE_MODEL}.partial"
    adb.run(
        "Remove prior private staging",
        ["shell", "run-as", TARGET_PACKAGE, "rm", "-rf", DEVICE_ROOT],
        allow_failure=True,
    )
    adb.run(
        "Push exact artifact",
        ["push", str(artifact), temporary],
        timeout_seconds=ARTIFACT_TRANSFER_TIMEOUT_SECONDS,
    )
    size = parse_byte_count(adb.text(
        "Verify temporary artifact size",
        ["shell", "wc", "-c", temporary],
        timeout_seconds=DEFAULT_ADB_TIMEOUT_SECONDS,
    ), "temporary artifact size")
    digest = first_token(adb.text(
        "Verify temporary artifact hash",
        ["shell", "sha256sum", temporary],
        timeout_seconds=ARTIFACT_HASH_TIMEOUT_SECONDS,
    ), "temporary artifact hash").lower()
    if size != EXPECTED_SIZE_BYTES or digest != EXPECTED_SHA256:
        fail("temporary artifact identity mismatch")
    adb.run(
        "Create private staging",
        ["shell", "run-as", TARGET_PACKAGE, "mkdir", "-p",
         private_input, private_output],
    )
    copy_script = (
        f"/system/bin/cat {temporary} | "
        f"run-as {TARGET_PACKAGE} /system/bin/dd "
        f"of={partial_model} bs=1048576\n"
    )
    adb.run(
        "Copy artifact to private staging",
        ["shell"],
        input_text=copy_script,
        timeout_seconds=PRIVATE_COPY_TIMEOUT_SECONDS,
    )
    adb.run(
        "Commit private artifact staging",
        ["shell", "run-as", TARGET_PACKAGE, "mv", partial_model, DEVICE_MODEL],
    )
    adb.run(
        "Protect private artifact",
        ["shell", "run-as", TARGET_PACKAGE, "chmod", "400", DEVICE_MODEL],
    )
    private_size = parse_byte_count(adb.text(
        "Verify private artifact size",
        ["shell", "run-as", TARGET_PACKAGE, "wc", "-c", DEVICE_MODEL],
        timeout_seconds=DEFAULT_ADB_TIMEOUT_SECONDS,
    ), "private artifact size")
    private_hash = first_token(adb.text(
        "Verify private artifact hash",
        ["shell", "run-as", TARGET_PACKAGE, "sha256sum", DEVICE_MODEL],
        timeout_seconds=ARTIFACT_HASH_TIMEOUT_SECONDS,
    ), "private artifact hash").lower()
    if private_size != EXPECTED_SIZE_BYTES or private_hash != EXPECTED_SHA256:
        fail("private artifact identity mismatch")


def private_text(adb: Adb, path: str, label: str) -> str:
    result = adb.run(
        f"Extract {label}",
        ["shell", "run-as", TARGET_PACKAGE, "cat", path],
        allow_failure=True,
        timeout_seconds=OUTPUT_EXTRACTION_TIMEOUT_SECONDS,
    )
    if result.returncode or not result.stdout.strip():
        fail(f"could not extract {label}")
    return result.stdout.strip() + "\n"


def cleanup(adb: Adb, temporary: str) -> None:
    for label, args in (
        ("Remove shell staging", ["shell", "rm", "-f", temporary]),
        ("Remove private staging",
         ["shell", "run-as", TARGET_PACKAGE, "rm", "-rf", DEVICE_ROOT]),
        ("Force-stop Morimil",
         ["shell", "am", "force-stop", TARGET_PACKAGE]),
    ):
        adb.run(label, args, allow_failure=True)
