#!/usr/bin/env python3
"""Run Morimil's exact v0.2 120-case benchmark on a physical Android ARM64 device."""
from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
import time
import uuid
from pathlib import Path
from typing import Sequence

from physical_benchmark_adb_v0 import (
    Adb,
    DEVICE_PHYSICAL_REPORT,
    DEVICE_RESPONSES,
    build_and_install,
    cleanup,
    command,
    private_text,
    resolve_serial,
    stage_artifact,
    verify_artifact,
    verify_device,
)
from physical_benchmark_contract_v0 import (
    EXPECTED_BENCHMARK_VERSION,
    EXPECTED_CASE_COUNT,
    EXPECTED_DATASET_SHA256,
    EXPECTED_FILENAME,
    EXPECTED_PHYSICAL_SCHEMA,
    EXPECTED_RESPONSE_FIELDS,
    EXPECTED_SHA256,
    EXPECTED_SIZE_BYTES,
    RunnerError,
    atomic_write_json,
    atomic_write_text,
    fail,
    parse_jsonl,
    self_test,
    sha256_file,
    validate_physical_report,
    validate_responses,
)

TARGET_PACKAGE = "com.morimil.app"
TEST_PACKAGE = "com.morimil.app.test"
INSTRUMENTATION_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
HARNESS_CLASS = (
    "com.morimil.app.reasoning.intrinsic."
    "Gemma3nE2bArm64DeliberativeBenchmarkV0Test"
)
ENABLE_ARGUMENT = "morimilArm64DeliberativeBenchmarkEnabled"
BENCHMARK_TIMEOUT_SECONDS = 65 * 60


def evaluate(
    root: Path,
    run_dir: Path,
    responses: Path,
    run_id: str,
) -> tuple[Path, Path]:
    cli = root / "tools/benchmarks/morimil_deliberative_benchmark_v0.py"
    dataset, report = run_dir / "dataset-v0.json", run_dir / "report-v0.2.json"
    command("Generate frozen dataset", [
        sys.executable, str(cli), "generate", "--output", str(dataset)
    ])
    if sha256_file(dataset) != EXPECTED_DATASET_SHA256:
        fail("generated dataset digest mismatch")
    command("Check frozen dataset", [
        sys.executable, str(cli), "check", "--dataset", str(dataset)
    ])
    command("Evaluate v0.2 responses", [
        sys.executable, str(cli), "evaluate",
        "--dataset", str(dataset), "--responses", str(responses),
        "--run-id", run_id, "--motor-version", "morimil-deliberative-v0.2",
        "--output", str(report),
    ])
    value = json.loads(report.read_text(encoding="utf-8"))
    if (
        value.get("benchmarkVersion") != EXPECTED_BENCHMARK_VERSION
        or value.get("datasetSha256") != EXPECTED_DATASET_SHA256
        or value.get("caseCount") != EXPECTED_CASE_COUNT
        or value.get("productionPromotionAllowed") is not False
    ):
        fail("evaluator report identity or production boundary mismatch")
    return dataset, report


def run(args: argparse.Namespace) -> int:
    root = Path(__file__).resolve().parents[2]
    artifact, artifact_hash = verify_artifact(Path(args.artifact_path))
    adb_path = shutil.which("adb")
    if not adb_path:
        fail("adb is not installed or not on PATH")
    adb = Adb(adb_path, resolve_serial(adb_path, args.serial))
    verify_device(adb)
    build_and_install(adb, root, args.skip_build)

    stamp = time.strftime("%Y%m%d-%H%M%S")
    run_id = f"morimil-v0.2-physical-{stamp}-{uuid.uuid4().hex[:8]}"
    report_root = (
        Path(args.report_directory).expanduser()
        if args.report_directory
        else root / "build/morimil-arm64-deliberative-benchmark-reports"
    )
    run_dir = report_root.resolve() / run_id
    run_dir.mkdir(parents=True, exist_ok=False)
    transcript = run_dir / "instrumentation-output.txt"
    responses = run_dir / "responses-v0.2.jsonl"
    physical = run_dir / "physical-execution-v0.2.json"
    temporary = f"/data/local/tmp/{EXPECTED_FILENAME}.{uuid.uuid4().hex}.partial"

    try:
        stage_artifact(adb, artifact, temporary)
        result = adb.run(
            "Execute opt-in physical benchmark",
            ["shell", "am", "instrument", "-w", "-r",
             "-e", ENABLE_ARGUMENT, "true",
             "-e", "class", HARNESS_CLASS,
             f"{TEST_PACKAGE}/{INSTRUMENTATION_RUNNER}"],
            allow_failure=True,
            timeout_seconds=BENCHMARK_TIMEOUT_SECONDS,
        )
        atomic_write_text(transcript, result.stdout)
        response_text = private_text(adb, DEVICE_RESPONSES, "response JSONL")
        physical_text = private_text(adb, DEVICE_PHYSICAL_REPORT, "physical report")
        atomic_write_text(responses, response_text)
        atomic_write_text(physical, physical_text)
        if (
            result.returncode
            or re.search(r"FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=", result.stdout)
            or "OK (2 tests)" not in result.stdout
        ):
            fail("Android instrumentation failed; inspect extracted outputs")
        records = parse_jsonl(response_text)
        physical_value = json.loads(physical_text)
        if not isinstance(physical_value, dict):
            fail("physical report is not an object")
        validate_physical_report(physical_value)
        dataset, report = evaluate(root, run_dir, responses, run_id)
        report_value = json.loads(report.read_text(encoding="utf-8"))
        files = {
            "dataset": dataset, "responses": responses, "report": report,
            "physicalExecution": physical, "transcript": transcript,
        }
        bundle = {
            "schemaVersion": "morimil.deliberative.benchmark.physical-bundle.v0",
            "status": "COMPLETED", "runId": run_id,
            "artifact": {
                "version": "morimil-deliberative-v0.2",
                "filename": EXPECTED_FILENAME, "sizeBytes": EXPECTED_SIZE_BYTES,
                "sha256": artifact_hash,
            },
            "benchmark": {
                "version": EXPECTED_BENCHMARK_VERSION,
                "datasetSha256": EXPECTED_DATASET_SHA256,
                "caseCount": 120, "responseCount": len(records),
                "researchGatePassed": bool(report_value["researchGatePassed"]),
            },
            "files": {
                name: {"name": path.name, "sha256": sha256_file(path)}
                for name, path in files.items()
            },
            "authorityBoundary": {
                "requestStateReleased": True, "memoryWriteCapability": False,
                "identityAuthority": False, "lifecycleAuthority": False,
            },
            "certified": False, "signed": False, "installed": False,
            "normalRuntimeActivated": False, "productionAuthorization": False,
            "productionPromotionAllowed": False,
        }
        atomic_write_json(run_dir / "bundle-v0.2.json", bundle)
        print("\nMORIMIL V0.2 PHYSICAL BENCHMARK COMPLETED")
        print(f"Responses:       {len(records)}/120")
        print(f"Research gate:   {report_value['researchGatePassed']}")
        print(f"False accepted:  {report_value['counts']['falseAcceptedCount']}")
        print(f"Directory:       {run_dir}")
        print("Production:      BLOCKED")
        return 0
    finally:
        cleanup(adb, temporary)


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact_path", nargs="?")
    parser.add_argument("--serial")
    parser.add_argument("--report-directory")
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if not args.self_test and not args.artifact_path:
        parser.error("artifact_path is required unless --self-test is used")
    return args


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        return self_test() if args.self_test else run(args)
    except (RunnerError, OSError, ValueError, json.JSONDecodeError) as error:
        print(f"STOPPED: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
