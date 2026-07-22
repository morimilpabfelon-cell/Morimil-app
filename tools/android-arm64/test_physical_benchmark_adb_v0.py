"""Focused tests for physical benchmark ADB parsing and timeouts."""
from __future__ import annotations

import subprocess
import unittest
from pathlib import Path
from unittest import mock

import physical_benchmark_adb_v0 as adb_support


class FakeAdb:
    def __init__(self) -> None:
        self.calls: list[tuple[str, list[str], float | None]] = []

    def run(
        self,
        label: str,
        args: list[str],
        *,
        allow_failure: bool = False,
        timeout_seconds: float = adb_support.DEFAULT_ADB_TIMEOUT_SECONDS,
    ) -> subprocess.CompletedProcess[str]:
        del allow_failure
        self.calls.append((label, list(args), timeout_seconds))
        return subprocess.CompletedProcess(args, 0, "")

    def text(
        self,
        label: str,
        args: list[str],
        *,
        timeout_seconds: float = adb_support.DEFAULT_ADB_TIMEOUT_SECONDS,
    ) -> str:
        self.calls.append((label, list(args), timeout_seconds))
        if label == "Verify temporary artifact size":
            return f"{adb_support.EXPECTED_SIZE_BYTES} /data/local/tmp/candidate"
        if label == "Verify temporary artifact hash":
            return f"{adb_support.EXPECTED_SHA256}  /data/local/tmp/candidate"
        if label == "Verify private artifact size":
            return f"{adb_support.EXPECTED_SIZE_BYTES} {adb_support.DEVICE_MODEL}"
        if label == "Verify private artifact hash":
            return f"{adb_support.EXPECTED_SHA256}  {adb_support.DEVICE_MODEL}"
        raise AssertionError(label)


class PhysicalBenchmarkAdbV0Test(unittest.TestCase):
    def test_byte_count_accepts_wc_file_output(self) -> None:
        self.assertEqual(
            adb_support.EXPECTED_SIZE_BYTES,
            adb_support.parse_byte_count(
                f"{adb_support.EXPECTED_SIZE_BYTES} /data/local/tmp/candidate",
                "size",
            ),
        )

    def test_byte_count_accepts_single_number(self) -> None:
        self.assertEqual(42, adb_support.parse_byte_count("42", "size"))

    def test_byte_count_rejects_three_metric_wc_output(self) -> None:
        with self.assertRaises(adb_support.RunnerError):
            adb_support.parse_byte_count(
                "12326516 134811506 3655827456",
                "size",
            )

    def test_command_timeout_is_fail_closed(self) -> None:
        timeout = subprocess.TimeoutExpired(
            cmd=["adb", "get-state"],
            timeout=1,
            output="partial output\n",
        )
        with mock.patch.object(adb_support.subprocess, "run", side_effect=timeout):
            with self.assertRaisesRegex(adb_support.RunnerError, "timed out after 1 seconds"):
                adb_support.command(
                    "Confirm device state",
                    ["adb", "get-state"],
                    timeout_seconds=1,
                )

    def test_stage_uses_unambiguous_wc_commands_and_bounded_timeouts(self) -> None:
        adb = FakeAdb()
        temporary = "/data/local/tmp/candidate.partial"
        adb_support.stage_artifact(adb, Path("candidate.litertlm"), temporary)
        calls = {label: (args, timeout) for label, args, timeout in adb.calls}
        self.assertEqual(
            ["shell", "wc", "-c", temporary],
            calls["Verify temporary artifact size"][0],
        )
        self.assertEqual(
            ["shell", "run-as", adb_support.TARGET_PACKAGE,
             "wc", "-c", adb_support.DEVICE_MODEL],
            calls["Verify private artifact size"][0],
        )
        self.assertEqual(
            adb_support.ARTIFACT_TRANSFER_TIMEOUT_SECONDS,
            calls["Push exact artifact"][1],
        )
        self.assertEqual(
            adb_support.PRIVATE_COPY_TIMEOUT_SECONDS,
            calls["Copy artifact to private read-only staging"][1],
        )


if __name__ == "__main__":
    unittest.main()
