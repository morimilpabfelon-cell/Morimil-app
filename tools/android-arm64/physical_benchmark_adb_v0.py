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


def command(
    label: str,
    args: Sequence[str],
    *,
    allow_failure: bool = False,
    cwd: Path | None = None,
) -> subprocess.CompletedProcess[str]:
    print(f"\n==> {label}")
    result = subprocess.run(
        list(args),
        check=False,
        cwd=str(cwd) if cwd else None,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        encoding="utf-8",
        errors="replace",
    )
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
    ) -> subprocess.CompletedProcess[str]:
        return command(label, [*self.prefix, *args], allow_failure=allow_failure)

    def text(self, label: str, args: Sequence[str]) -> str:
        return self.run(label, args).stdout.strip()


def first_token(value: str, label: str) -> str:
    tokens = value.split()
    if not tokens:
        fail(f"{label} returned no value")
    return tokens[0]


def resolve_serial(adb: str, requested: str | None) -> str:
    if requested:
        return requested.strip()
    result = command("Enumerate adb devices", [adb, "devices"])
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
        "Read device memory", ["shell", "sh", "-c", "head -n 1 /proc/meminfo"]
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
        )
    if not debug.is_file() or not tests.is_file():
        fail("required APK outputs are missing")
    adb.run("Install debug APK", ["install", "-r", "-t", str(debug)])
    adb.run("Install instrumentation APK", ["install", "-r", "-t", str(tests)])
    adb.run(
        "Stop Morimil before benchmark",
        ["shell", "am", "force-stop", TARGET_PACKAGE],
        allow_failure=True,
    )


def stage_artifact(adb: Adb, artifact: Path, temporary: str) -> None:
    adb.run(
        "Remove prior private staging",
        ["shell", "run-as", TARGET_PACKAGE, "rm", "-rf", DEVICE_ROOT],
        allow_failure=True,
    )
    adb.run("Push exact artifact", ["push", str(artifact), temporary])
    size = int(first_token(adb.text(
        "Verify temporary artifact size",
        ["shell", "sh", "-c", f"wc -c < '{temporary}'"],
    ), "temporary artifact size"))
    digest = first_token(adb.text(
        "Verify temporary artifact hash", ["shell", "sha256sum", temporary]
    ), "temporary artifact hash").lower()
    if size != EXPECTED_SIZE_BYTES or digest != EXPECTED_SHA256:
        fail("temporary artifact identity mismatch")
    adb.run(
        "Create private staging",
        ["shell", "run-as", TARGET_PACKAGE, "sh", "-c",
         f"mkdir -p {DEVICE_ROOT}/input {DEVICE_ROOT}/output"],
    )
    copy = (
        f"cat '{temporary}' | run-as {TARGET_PACKAGE} sh -c "
        f"'cat > {DEVICE_MODEL}.partial && mv {DEVICE_MODEL}.partial {DEVICE_MODEL} "
        f"&& chmod 400 {DEVICE_MODEL}'"
    )
    adb.run("Copy artifact to private read-only staging", ["shell", "sh", "-c", copy])
    private_size = int(first_token(adb.text(
        "Verify private artifact size",
        ["shell", "run-as", TARGET_PACKAGE, "sh", "-c",
         f"test -r {DEVICE_MODEL} && test ! -w {DEVICE_MODEL} "
         f"&& wc -c < {DEVICE_MODEL}"],
    ), "private artifact size"))
    private_hash = first_token(adb.text(
        "Verify private artifact hash",
        ["shell", "run-as", TARGET_PACKAGE, "sha256sum", DEVICE_MODEL],
    ), "private artifact hash").lower()
    if private_size != EXPECTED_SIZE_BYTES or private_hash != EXPECTED_SHA256:
        fail("private artifact identity mismatch")


def private_text(adb: Adb, path: str, label: str) -> str:
    result = adb.run(
        f"Extract {label}",
        ["shell", "run-as", TARGET_PACKAGE, "cat", path],
        allow_failure=True,
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
