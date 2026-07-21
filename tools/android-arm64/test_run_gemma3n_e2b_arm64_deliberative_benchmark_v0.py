"""Unit tests for the Morimil physical benchmark host runner."""
from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name(
    "run_gemma3n_e2b_arm64_deliberative_benchmark_v0.py"
)
SPEC = importlib.util.spec_from_file_location("morimil_physical_benchmark_v0", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


def valid_record(case_id: str) -> dict[str, object]:
    return {
        "caseId": case_id,
        "finalDisposition": "ACCEPTED",
        "finalAnswer": "1",
        "latencyMs": 10,
        "stateKind": "TEXTUAL_CONVERSATION",
        "completedIterations": 1,
        "stopReason": "CONVERGED",
        "confidencePermille": None,
        "strictFormatPassed": True,
        "instructionCompliant": True,
        "claimVerificationPassed": None,
        "requestStateReleased": True,
        "memoryWriteCapability": False,
        "identityAuthority": False,
    }


def valid_records() -> list[dict[str, object]]:
    records = []
    for domain in (
        "arithmetic",
        "logic",
        "spanish",
        "restricted_code",
        "claim_verification",
        "planning",
        "insufficient_information",
        "strict_format",
        "adversarial_consensus",
        "multi_turn_context",
    ):
        for index in range(1, 13):
            records.append(valid_record(f"{domain}-{index:04d}"))
    return records


def valid_physical_report() -> dict[str, object]:
    return {
        "schemaVersion": runner.EXPECTED_PHYSICAL_SCHEMA,
        "status": "passed",
        "benchmarkVersion": runner.EXPECTED_BENCHMARK_VERSION,
        "datasetSha256": runner.EXPECTED_DATASET_SHA256,
        "requestedCaseCount": runner.EXPECTED_CASE_COUNT,
        "completedCaseCount": runner.EXPECTED_CASE_COUNT,
        "artifactVersion": "morimil-deliberative-v0.2",
        "artifactFilename": runner.EXPECTED_FILENAME,
        "expectedArtifactSha256": f"sha256:{runner.EXPECTED_SHA256}",
        "hashStable": True,
        "engineInitialized": True,
        "engineClosed": True,
        "allConversationsClosed": True,
        "requestStateReleased": True,
        "memoryWriteCapability": False,
        "identityAuthority": False,
        "normalRuntimeActivated": False,
        "productionAuthorization": False,
        "promotionAllowed": False,
        "errors": [],
    }


class PhysicalBenchmarkHostRunnerV0Test(unittest.TestCase):
    def test_valid_response_set_is_accepted(self) -> None:
        runner.validate_responses(valid_records())

    def test_missing_response_is_rejected(self) -> None:
        records = valid_records()
        records.pop()
        with self.assertRaises(runner.RunnerError):
            runner.validate_responses(records)

    def test_duplicate_response_is_rejected(self) -> None:
        records = valid_records()
        records[-1]["caseId"] = records[0]["caseId"]
        with self.assertRaises(runner.RunnerError):
            runner.validate_responses(records)

    def test_identity_authority_is_rejected(self) -> None:
        records = valid_records()
        records[0]["identityAuthority"] = True
        with self.assertRaises(runner.RunnerError):
            runner.validate_responses(records)

    def test_memory_write_capability_is_rejected(self) -> None:
        records = valid_records()
        records[0]["memoryWriteCapability"] = True
        with self.assertRaises(runner.RunnerError):
            runner.validate_responses(records)

    def test_unreleased_request_state_is_rejected(self) -> None:
        records = valid_records()
        records[0]["requestStateReleased"] = False
        with self.assertRaises(runner.RunnerError):
            runner.validate_responses(records)

    def test_latent_state_claim_is_rejected_for_v02(self) -> None:
        records = valid_records()
        records[0]["stateKind"] = "LATENT_RECURRENT"
        with self.assertRaises(runner.RunnerError):
            runner.validate_responses(records)

    def test_valid_physical_report_is_accepted(self) -> None:
        runner.validate_physical_report(valid_physical_report())

    def test_production_activation_is_rejected(self) -> None:
        report = valid_physical_report()
        report["normalRuntimeActivated"] = True
        with self.assertRaises(runner.RunnerError):
            runner.validate_physical_report(report)

    def test_incomplete_physical_report_is_rejected(self) -> None:
        report = valid_physical_report()
        report["completedCaseCount"] = 119
        with self.assertRaises(runner.RunnerError):
            runner.validate_physical_report(report)

    def test_jsonl_parser_accepts_exactly_120_records(self) -> None:
        text = "\n".join(json.dumps(record) for record in valid_records()) + "\n"
        self.assertEqual(120, len(runner.parse_jsonl(text)))

    def test_atomic_json_is_canonical(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "value.json"
            runner.atomic_write_json(path, {"b": 2, "a": 1})
            self.assertEqual(b'{\n  "a": 1,\n  "b": 2\n}\n', path.read_bytes())


if __name__ == "__main__":
    unittest.main()
