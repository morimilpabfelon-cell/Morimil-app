#!/usr/bin/env python3
"""Run Morimil's isolated 120-case tri-motor benchmark on a physical ARM64 device."""
from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
import time
import tempfile
import uuid
from pathlib import Path
from typing import Sequence

BENCHMARK_TOOLS_DIR = Path(__file__).resolve().parents[1] / "benchmarks"
if str(BENCHMARK_TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(BENCHMARK_TOOLS_DIR))

from validate_v02_physical_benchmark_evidence_v1 import (  # noqa: E402
    decode_archive as decode_v02_archive,
    load_manifest as load_v02_manifest,
)

from physical_benchmark_adb_v0 import (
    Adb,
    DEVICE_ROOT,
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
    EXPECTED_SIZE_BYTES,
)
from trimotor_physical_benchmark_contract_v0 import (
    RunnerError,
    atomic_write_json,
    atomic_write_text,
    fail,
    parse_jsonl,
    self_test,
    sha256_file,
    validate_physical_report,
)

TARGET_PACKAGE = "com.morimil.app"
TEST_PACKAGE = "com.morimil.app.test"
INSTRUMENTATION_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
HARNESS_CLASS = (
    "com.morimil.app.reasoning.intrinsic."
    "Gemma3nE2bArm64TriMotorBenchmarkV0Test"
)
ENABLE_ARGUMENT = "morimilArm64TriMotorBenchmarkEnabled"
BENCHMARK_TIMEOUT_SECONDS = 65 * 60
DEVICE_RESPONSES = f"{DEVICE_ROOT}/output/responses-trimotor-v0.2.jsonl"
DEVICE_PHYSICAL_REPORT = (
    f"{DEVICE_ROOT}/output/physical-execution-trimotor-v0.2.json"
)
MOTOR_VERSION = "morimil-trimotor-v0.2-bounded-local-v0"
BASELINE_MANIFEST = Path(
    "docs/model-artifacts/"
    "morimil-deliberative-v0.2-physical-benchmark-evidence-v1.json"
)
BASELINE_ARCHIVE_ENTRY = "report-v0.2.json"
BASELINE_REPORT_SHA256 = (
    "sha256:2a371f906cebd74a05a50883563b665a5b99fac9864c44c794bb245b73304a12"
)
LEGACY_SUPERIOR_OUTCOME = "V0_3_SUPERIOR"


def instrumentation_failed(returncode: int, output: str) -> bool:
    return bool(
        returncode
        or re.search(r"FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=", output)
        or "OK (2 tests)" not in output
    )


def capture_failure_diagnostics(adb: Adb, run_dir: Path) -> None:
    """Best-effort capture before private staging is removed."""
    diagnostics = (
        (
            "exit-info.txt",
            "Capture Morimil exit info",
            ["shell", "dumpsys", "activity", "exit-info", TARGET_PACKAGE],
        ),
        (
            "logcat-tail.txt",
            "Capture recent device logcat",
            ["logcat", "-d", "-v", "threadtime", "-t", "2500"],
        ),
    )
    for filename, label, args in diagnostics:
        try:
            result = adb.run(
                label,
                args,
                allow_failure=True,
                timeout_seconds=60,
            )
            atomic_write_text(run_dir / filename, result.stdout)
        except (RunnerError, OSError, ValueError):
            # The instrumentation transcript remains the primary evidence.
            pass


def materialize_frozen_baseline(root: Path, run_dir: Path) -> Path:
    manifest_path = (root / BASELINE_MANIFEST).resolve()
    if not manifest_path.is_file():
        fail(f"frozen v0.2 evidence manifest missing: {manifest_path}")
    manifest = load_v02_manifest(manifest_path)
    entries = decode_v02_archive(manifest, root)
    raw = entries.get(BASELINE_ARCHIVE_ENTRY)
    if raw is None:
        fail("frozen v0.2 baseline report is missing from immutable evidence")
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as error:
        fail(f"frozen v0.2 baseline report is not UTF-8: {error}")
    baseline = run_dir / BASELINE_ARCHIVE_ENTRY
    atomic_write_text(baseline, text)
    if sha256_file(baseline) != BASELINE_REPORT_SHA256:
        fail("materialized frozen v0.2 baseline report digest mismatch")
    return baseline


def evaluate(
    root: Path,
    run_dir: Path,
    responses: Path,
    run_id: str,
) -> tuple[Path, Path, Path, Path]:
    cli = root / "tools/benchmarks/morimil_deliberative_benchmark_v0.py"
    dataset = run_dir / "dataset-v0.json"
    report = run_dir / "report-trimotor-v0.2.json"
    comparison = run_dir / "comparison-v0.2.json"
    command(
        "Generate frozen dataset",
        [sys.executable, str(cli), "generate", "--output", str(dataset)],
    )
    if sha256_file(dataset) != EXPECTED_DATASET_SHA256:
        fail("generated tri-motor dataset digest mismatch")
    command(
        "Check frozen dataset",
        [sys.executable, str(cli), "check", "--dataset", str(dataset)],
    )
    command(
        "Evaluate tri-motor responses",
        [
            sys.executable,
            str(cli),
            "evaluate",
            "--dataset",
            str(dataset),
            "--responses",
            str(responses),
            "--run-id",
            run_id,
            "--motor-version",
            MOTOR_VERSION,
            "--output",
            str(report),
        ],
    )
    value = json.loads(report.read_text(encoding="utf-8"))
    if (
        value.get("benchmarkVersion") != EXPECTED_BENCHMARK_VERSION
        or value.get("datasetSha256") != EXPECTED_DATASET_SHA256
        or value.get("caseCount") != EXPECTED_CASE_COUNT
        or value.get("productionPromotionAllowed") is not False
    ):
        fail("tri-motor evaluator identity or activation boundary mismatch")

    baseline = materialize_frozen_baseline(root, run_dir)
    command(
        "Compare tri-motor result with frozen v0.2 baseline",
        [
            sys.executable,
            str(cli),
            "compare",
            "--baseline",
            str(baseline),
            "--candidate",
            str(report),
            "--output",
            str(comparison),
        ],
    )
    comparison_value = json.loads(comparison.read_text(encoding="utf-8"))
    if comparison_value.get("productionPromotionAllowed") is not False:
        fail("tri-motor comparison attempted to authorize activation")
    return dataset, report, comparison, baseline


def runner_self_test() -> int:
    self_test()
    if instrumentation_failed(0, "OK (2 tests)\n"):
        fail("successful instrumentation was classified as failed")
    if not instrumentation_failed(0, "shortMsg=Process crashed\n"):
        fail("instrumentation crash was not detected")
    with tempfile.TemporaryDirectory() as temporary:
        baseline = materialize_frozen_baseline(root=Path(__file__).resolve().parents[2], run_dir=Path(temporary))
        if baseline.name != BASELINE_ARCHIVE_ENTRY or sha256_file(baseline) != BASELINE_REPORT_SHA256:
            fail("frozen baseline self-test mismatch")
    print("MORIMIL TRIMOTOR PHYSICAL RUNNER SELF-TEST: PASS")
    return 0


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
    run_id = f"morimil-trimotor-v0.2-physical-{stamp}-{uuid.uuid4().hex[:8]}"
    report_root = (
        Path(args.report_directory).expanduser()
        if args.report_directory
        else root / "build/morimil-arm64-trimotor-benchmark-reports"
    )
    run_dir = report_root.resolve() / run_id
    run_dir.mkdir(parents=True, exist_ok=False)
    transcript = run_dir / "instrumentation-output.txt"
    responses = run_dir / "responses-trimotor-v0.2.jsonl"
    physical = run_dir / "physical-execution-trimotor-v0.2.json"
    temporary = f"/data/local/tmp/{EXPECTED_FILENAME}.{uuid.uuid4().hex}.partial"

    try:
        stage_artifact(adb, artifact, temporary)
        result = adb.run(
            "Execute opt-in physical tri-motor benchmark",
            [
                "shell",
                "am",
                "instrument",
                "-w",
                "-r",
                "-e",
                ENABLE_ARGUMENT,
                "true",
                "-e",
                "class",
                HARNESS_CLASS,
                f"{TEST_PACKAGE}/{INSTRUMENTATION_RUNNER}",
            ],
            allow_failure=True,
            timeout_seconds=BENCHMARK_TIMEOUT_SECONDS,
        )
        atomic_write_text(transcript, result.stdout)
        if instrumentation_failed(result.returncode, result.stdout):
            capture_failure_diagnostics(adb, run_dir)
            fail(
                "Android tri-motor instrumentation failed; inspect "
                f"{transcript} and captured diagnostics"
            )

        response_text = private_text(
            adb, DEVICE_RESPONSES, "tri-motor response JSONL"
        )
        physical_text = private_text(
            adb, DEVICE_PHYSICAL_REPORT, "tri-motor physical report"
        )
        atomic_write_text(responses, response_text)
        atomic_write_text(physical, physical_text)

        records = parse_jsonl(response_text)
        physical_value = json.loads(physical_text)
        if not isinstance(physical_value, dict):
            fail("tri-motor physical report is not an object")
        validate_physical_report(physical_value)
        dataset, report, comparison, baseline = evaluate(root, run_dir, responses, run_id)
        report_value = json.loads(report.read_text(encoding="utf-8"))
        comparison_value = json.loads(comparison.read_text(encoding="utf-8"))
        files = {
            "dataset": dataset,
            "responses": responses,
            "report": report,
            "comparison": comparison,
            "physicalExecution": physical,
            "transcript": transcript,
            "baselineReport": baseline,
        }
        bundle = {
            "schemaVersion": "morimil.trimotor.benchmark.physical-bundle.v0",
            "status": "COMPLETED",
            "runId": run_id,
            "artifact": {
                "version": "morimil-deliberative-v0.2",
                "filename": EXPECTED_FILENAME,
                "sizeBytes": EXPECTED_SIZE_BYTES,
                "sha256": artifact_hash,
                "sourceModelRevision": physical_value.get("sourceModelRevision"),
            },
            "benchmark": {
                "version": EXPECTED_BENCHMARK_VERSION,
                "datasetSha256": EXPECTED_DATASET_SHA256,
                "caseCount": EXPECTED_CASE_COUNT,
                "responseCount": len(records),
                "researchGatePassed": bool(report_value["researchGatePassed"]),
                "falseAcceptedCount": report_value["counts"][
                    "falseAcceptedCount"
                ],
                "acceptedCorrectCount": report_value["counts"][
                    "acceptedCorrectCount"
                ],
                "abstainedCount": report_value["counts"]["abstainedCount"],
                "strictFormatPassCount": report_value["counts"][
                    "strictFormatPassCount"
                ],
                "strictFormatCaseCount": report_value["counts"][
                    "strictFormatCaseCount"
                ],
            },
            "trimotor": {
                "motorVersion": MOTOR_VERSION,
                "triMotorRuntimeVersion": physical_value[
                    "triMotorRuntimeVersion"
                ],
                "benchmarkAdapterVersion": physical_value[
                    "benchmarkAdapterVersion"
                ],
                "intuitiveCoreVersion": physical_value["intuitiveCoreVersion"],
                "deliberativeArtifactVersion": physical_value[
                    "deliberativeArtifactVersion"
                ],
                "metacognitiveCoreVersion": physical_value[
                    "metacognitiveCoreVersion"
                ],
                "hybridAuthorityEnabled": True,
                "roleActivationCounts": physical_value["roleActivationCounts"],
                "authorityStatusCounts": physical_value[
                    "authorityStatusCounts"
                ],
                "openedConversationCount": physical_value[
                    "openedConversationCount"
                ],
                "closedConversationCount": physical_value[
                    "closedConversationCount"
                ],
            },
            "comparison": {
                "baselineRunId": comparison_value["baselineRunId"],
                "candidateRunId": comparison_value["candidateRunId"],
                "outcome": comparison_value["outcome"],
                "outcomeSemantics": (
                    "Legacy comparator label: superior candidate under the "
                    "frozen v0.3 research contract; it does not claim that a "
                    "trained neural v0.3 model exists."
                ),
                "reasons": comparison_value["reasons"],
                "status": comparison_value["status"],
            },
            "files": {
                name: {
                    "name": path.name,
                    "sha256": sha256_file(path),
                    "sizeBytes": path.stat().st_size,
                }
                for name, path in files.items()
            },
            "authorityBoundary": {
                "requestStateReleased": True,
                "memoryWriteCapability": False,
                "identityAuthority": False,
                "lifecycleAuthority": False,
            },
            "certified": False,
            "signed": False,
            "installed": False,
            "normalRuntimeActivated": False,
            "personalRuntimeActivationAuthorized": False,
            "productionAuthorization": False,
            "productionPromotionAllowed": False,
        }
        atomic_write_json(run_dir / "bundle-trimotor-v0.2.json", bundle)
        print("\nMORIMIL TRIMOTOR V0.2 PHYSICAL BENCHMARK COMPLETED")
        print(f"Responses:       {len(records)}/120")
        print(f"Research gate:   {report_value['researchGatePassed']}")
        print(
            "False accepted:  "
            f"{report_value['counts']['falseAcceptedCount']}"
        )
        print(f"Comparison:      {comparison_value['outcome']}")
        if comparison_value["outcome"] == LEGACY_SUPERIOR_OUTCOME:
            print(
                "Comparison note: legacy label only; no trained neural v0.3 "
                "model is claimed"
            )
        print(f"Directory:       {run_dir}")
        print("Personal runtime activation: BLOCKED")
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
        return runner_self_test() if args.self_test else run(args)
    except (RunnerError, OSError, ValueError, json.JSONDecodeError) as error:
        print(f"STOPPED: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
