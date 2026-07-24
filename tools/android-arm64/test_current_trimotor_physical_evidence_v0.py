#!/usr/bin/env python3
"""Tests for the strict current 84/36 physical evidence gate."""
from __future__ import annotations

import unittest

from current_trimotor_physical_evidence_v0 import (
    exact_current_response_records,
    validate_exact_responses,
)
from trimotor_physical_benchmark_contract_v0 import (
    RunnerError,
    current_contract_report,
    current_contract_response_records,
    validate_current_evaluation_report,
    validate_physical_report,
    validate_responses,
)


class CurrentTriMotorPhysicalEvidenceTest(unittest.TestCase):
    def test_current_structural_contract_passes(self) -> None:
        validate_responses(current_contract_response_records())
        validate_physical_report(current_contract_report())

    def test_exact_frozen_answers_pass(self) -> None:
        validate_exact_responses(exact_current_response_records())

    def test_wrong_answer_fails(self) -> None:
        records = exact_current_response_records()
        record = next(item for item in records if item["domain"] == "logic")
        record["finalAnswer"] = "PERSONA_INCORRECTA"
        with self.assertRaises(RunnerError):
            validate_exact_responses(records)

    def test_wrong_route_fails(self) -> None:
        records = exact_current_response_records()
        record = next(item for item in records if item["domain"] == "strict_format")
        record["authorityRoute"] = "DETERMINISTIC_ARITHMETIC"
        with self.assertRaises(RunnerError):
            validate_exact_responses(records)

    def test_stale_72_48_report_fails(self) -> None:
        report = current_contract_report()
        report["roleActivationCounts"] = {
            "INTUITIVE": 72,
            "DELIBERATIVE": 48,
            "METACOGNITIVE": 72,
        }
        with self.assertRaises(RunnerError):
            validate_physical_report(report)

    def test_false_acceptance_fails_evaluation_gate(self) -> None:
        with self.assertRaises(RunnerError):
            validate_current_evaluation_report({
                "researchGatePassed": False,
                "productionPromotionAllowed": False,
                "counts": {
                    "acceptedCount": 84,
                    "acceptedCorrectCount": 83,
                    "falseAcceptedCount": 1,
                    "abstainedCount": 36,
                    "correctAbstentionCount": 12,
                    "unnecessaryAbstentionCount": 24,
                    "strictFormatCaseCount": 24,
                    "strictFormatPassCount": 24,
                    "claimVerificationCaseCount": 12,
                    "claimVerificationPassCount": 12,
                    "stateReleasePassCount": 120,
                    "capabilityBoundaryPassCount": 120,
                },
            })


if __name__ == "__main__":
    unittest.main()
