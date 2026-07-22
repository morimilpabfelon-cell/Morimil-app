"""Focused tests for physical benchmark ADB parsing, staging and timeouts."""
from __future__ import annotations

import subprocess
import unittest
from pathlib import Path
from unittest import mock

import physical_benchmark_adb_v0 as adb_support


class FakeAdb:
    def __init__(self) -> None:
        self.calls: list[tuple[str, list[str], float | None, str | None]] = []

    def run(
        self,
        label: str,
        args: list[str],
        *,
        allow_failure: bool = False,
        timeout_seconds: float = adb_support.DEFAULT_ADB_TIMEOUT_SECONDS,
        input_text: str | None = None,
    ) -> subprocess.CompletedProcess[str]:
        del allow_failure
        self.calls.append((label, list(args), timeout_seconds, input_text))
        return subprocess.CompletedProcess(args, 0, "")

    def text(
        self,
        label: str,
        args: list[str],
        *,
        timeout_seconds: float = adb_support.DEFAULT_ADB_TIMEOUT_SECONDS,
    ) -> str:
        self.calls.append((label, list(args), timeout_seconds, None))
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

    def test_command_passes_stdin_script_without_nested_shell(self) -> None:
        completed = subprocess.CompletedProcess(["adb", "shell"], 0, "")
        with mock.patch.object(
            adb_support.subprocess, "run", return_value=completed
        ) as run:
            adb_support.command(
                "Copy",
                ["adb", "shell"],
                input_text="echo fixed\n",
                timeout_seconds=2,
            )
        self.assertEqual("echo fixed\n", run.call_args.kwargs["input"])
        self.assertEqual(["adb", "shell"], run.call_args.args[0])

    def test_stage_uses_direct_private_commands_and_stdin_pipeline(self) -> None:
        adb = FakeAdb()
        temporary = "/data/local/tmp/candidate.partial"
        adb_support.stage_artifact(adb, Path("candidate.litertlm"), temporary)
        calls = {
            label: (args, timeout, input_text)
            for label, args, timeout, input_text in adb.calls
        }
        self.assertEqual(
            ["shell", "wc", "-c", temporary],
            calls["Verify temporary artifact size"][0],
        )
        self.assertEqual(
            ["shell", "run-as", adb_support.TARGET_PACKAGE, "mkdir", "-p",
             f"{adb_support.DEVICE_ROOT}/input",
             f"{adb_support.DEVICE_ROOT}/output"],
            calls["Create private staging"][0],
        )
        self.assertEqual(["shell"], calls["Copy artifact to private staging"][0])
        self.assertEqual(
            f"/system/bin/cat {temporary} | "
            f"run-as {adb_support.TARGET_PACKAGE} /system/bin/dd "
            f"of={adb_support.DEVICE_MODEL}.partial bs=1048576\n",
            calls["Copy artifact to private staging"][2],
        )
        self.assertEqual(
            ["shell", "run-as", adb_support.TARGET_PACKAGE, "mv",
             f"{adb_support.DEVICE_MODEL}.partial", adb_support.DEVICE_MODEL],
            calls["Commit private artifact staging"][0],
        )
        self.assertEqual(
            ["shell", "run-as", adb_support.TARGET_PACKAGE,
             "chmod", "400", adb_support.DEVICE_MODEL],
            calls["Protect private artifact"][0],
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
            calls["Copy artifact to private staging"][1],
        )
        for _, args, _, _ in adb.calls:
            pairs = [args[index:index + 2] for index in range(len(args) - 1)]
            self.assertNotIn(["sh", "-c"], pairs)

    def test_stage_rejects_unsafe_temporary_path_before_adb_calls(self) -> None:
        adb = FakeAdb()
        with self.assertRaisesRegex(adb_support.RunnerError, "safe absolute device path"):
            adb_support.stage_artifact(
                adb,
                Path("candidate.litertlm"),
                "/data/local/tmp/candidate;rm",
            )
        self.assertEqual([], adb.calls)


if __name__ == "__main__":
    unittest.main()
