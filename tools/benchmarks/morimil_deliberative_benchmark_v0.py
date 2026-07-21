#!/usr/bin/env python3
"""CLI for Morimil deliberative comparative benchmark v0."""
from __future__ import annotations

import argparse
import json
import os
import tempfile
from pathlib import Path
from typing import Any

from benchmark_common_v0 import EXPECTED_DATASET_SHA256, build_dataset, canonical_bytes, digest
from benchmark_evaluator_v0 import compare_reports, evaluate


def load_json(path: str) -> Any:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def load_jsonl(path: str) -> list[dict[str, Any]]:
    return [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]


def atomic_write(path: str, value: Any) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("wb", dir=target.parent, delete=False) as handle:
        temp = Path(handle.name)
        handle.write(canonical_bytes(value))
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temp, target)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    generate = sub.add_parser("generate")
    generate.add_argument("--output", required=True)
    check = sub.add_parser("check")
    check.add_argument("--dataset")
    evaluate_cmd = sub.add_parser("evaluate")
    for name in ("dataset", "responses", "run-id", "motor-version", "output"):
        evaluate_cmd.add_argument(f"--{name}", required=True)
    compare_cmd = sub.add_parser("compare")
    for name in ("baseline", "candidate", "output"):
        compare_cmd.add_argument(f"--{name}", required=True)
    args = parser.parse_args()

    if args.command == "generate":
        dataset = build_dataset()
        atomic_write(args.output, dataset)
        print(digest(dataset))
        return 0
    if args.command == "check":
        dataset = build_dataset()
        if digest(dataset) != EXPECTED_DATASET_SHA256:
            raise SystemExit("generated dataset digest mismatch")
        if args.dataset and canonical_bytes(load_json(args.dataset)) != canonical_bytes(dataset):
            raise SystemExit("dataset file differs from deterministic generator")
        print(f"BENCHMARK DATASET CHECK: PASS ({EXPECTED_DATASET_SHA256})")
        return 0
    if args.command == "evaluate":
        report = evaluate(load_json(args.dataset), load_jsonl(args.responses), args.run_id, args.motor_version)
        atomic_write(args.output, report)
        print("BENCHMARK EVALUATION:", "PASS" if report["researchGatePassed"] else "FAIL")
        return 0
    comparison = compare_reports(load_json(args.baseline), load_json(args.candidate))
    atomic_write(args.output, comparison)
    print("BENCHMARK COMPARISON:", comparison["outcome"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
