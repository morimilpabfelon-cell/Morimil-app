#!/usr/bin/env python3
"""Run and strictly validate Morimil's current 84/36 physical tri-motor benchmark."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Sequence

from current_trimotor_physical_evidence_v0 import (
    self_test as evidence_self_test,
    validate_run_directory,
)
from physical_benchmark_contract_v0 import RunnerError, fail
from run_gemma3n_e2b_arm64_trimotor_benchmark_v0 import (
    run as run_legacy_harness,
    runner_self_test as legacy_runner_self_test,
)


def strict_self_test() -> int:
    legacy_runner_self_test()
    evidence_self_test()
    print("MORIMIL CURRENT TRIMOTOR STRICT RUNNER SELF-TEST: PASS")
    return 0


def run(args: argparse.Namespace) -> int:
    root = Path(__file__).resolve().parents[2]
    report_root = (
        Path(args.report_directory).expanduser().resolve()
        if args.report_directory
        else (root / "build/morimil-arm64-trimotor-benchmark-reports").resolve()
    )
    report_root.mkdir(parents=True, exist_ok=True)
    before = {path.resolve() for path in report_root.iterdir() if path.is_dir()}

    legacy_args = argparse.Namespace(
        artifact_path=args.artifact_path,
        serial=args.serial,
        report_directory=str(report_root),
        skip_build=args.skip_build,
        self_test=False,
    )
    result = run_legacy_harness(legacy_args)
    if result != 0:
        fail(f"legacy physical harness returned non-zero status: {result}")

    after = {path.resolve() for path in report_root.iterdir() if path.is_dir()}
    created = sorted(after - before)
    if len(created) != 1:
        fail(
            "strict current tri-motor runner expected exactly one new report directory; "
            f"found {len(created)}"
        )
    run_dir = created[0]
    validate_run_directory(run_dir)

    summary = json.loads((run_dir / "report-trimotor-v0.2.json").read_text(encoding="utf-8"))
    counts = summary["counts"]
    print("\nMORIMIL CURRENT 84/36 PHYSICAL EVIDENCE: VERIFIED")
    print(f"Accepted correct: {counts['acceptedCorrectCount']}/84")
    print(f"Abstained:       {counts['abstainedCount']}/36")
    print(f"False accepted:  {counts['falseAcceptedCount']}")
    print(f"Directory:       {run_dir}")
    print("Deliberative normal-runtime activation: BLOCKED")
    return 0


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
        return strict_self_test() if args.self_test else run(args)
    except (RunnerError, OSError, ValueError, json.JSONDecodeError) as error:
        print(f"STOPPED: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
