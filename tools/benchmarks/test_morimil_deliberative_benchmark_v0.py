import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).parent
sys.path.insert(0, str(ROOT))
import benchmark_common_v0 as common
import benchmark_evaluator_v0 as evaluator
import morimil_deliberative_benchmark_v0 as cli


class MorimilDeliberativeBenchmarkV0Test(unittest.TestCase):
    def setUp(self):
        self.dataset = common.build_dataset()

    def perfect_responses(self):
        responses = []
        for case in self.dataset["cases"]:
            answer_required = case["expectedDisposition"] == "ANSWER_REQUIRED"
            responses.append(
                {
                    "caseId": case["caseId"],
                    "finalDisposition": "ACCEPTED" if answer_required else "ABSTAINED",
                    "finalAnswer": case["acceptedAnswers"][0] if answer_required else None,
                    "latencyMs": 100 + len(responses),
                    "stateKind": "TEXTUAL_CONVERSATION",
                    "completedIterations": 2,
                    "stopReason": "CONVERGED",
                    "confidencePermille": None,
                    "strictFormatPassed": True,
                    "instructionCompliant": True,
                    "claimVerificationPassed": True if case["claimVerificationRequired"] else None,
                    "requestStateReleased": True,
                    "memoryWriteCapability": False,
                    "identityAuthority": False,
                }
            )
        return responses

    def test_dataset_is_frozen_balanced_and_private_data_free(self):
        common.validate_dataset(self.dataset)
        self.assertEqual(120, self.dataset["caseCount"])
        self.assertEqual(set(common.DOMAINS), set(self.dataset["domainCounts"]))
        self.assertTrue(all(value == 12 for value in self.dataset["domainCounts"].values()))
        self.assertFalse(self.dataset["privateDataAllowed"])
        self.assertFalse(self.dataset["externalTeacherAllowed"])
        self.assertFalse(self.dataset["promotionEvidenceAllowed"])
        digest = common.digest(self.dataset)
        self.assertEqual(common.EXPECTED_DATASET_SHA256, digest)

    def test_perfect_run_passes_without_claiming_production(self):
        report = evaluator.evaluate(self.dataset, self.perfect_responses(), "baseline-perfect", "v0.2-test")
        self.assertTrue(report["researchGatePassed"])
        self.assertFalse(report["productionPromotionAllowed"])
        self.assertEqual(0, report["counts"]["falseAcceptedCount"])
        self.assertEqual(1.0, report["rates"]["acceptancePrecision"])
        self.assertEqual(108 / 120, report["rates"]["acceptedCorrectRate"])
        self.assertEqual(1.0, report["rates"]["abstentionPrecision"])
        self.assertIsNotNone(report["uncertainty"]["ruleOfThree95UpperBoundAmongAccepted"])

    def test_hybrid_routed_authority_abstention_is_valid(self):
        responses = self.perfect_responses()
        target = next(item for item in responses if item["caseId"] == "logic-0001")
        target["finalDisposition"] = "ABSTAINED"
        target["finalAnswer"] = None
        target["stateKind"] = "HYBRID_ROUTED"
        target["completedIterations"] = 2
        target["stopReason"] = "AUTHORITY_ABSTAINED"
        target["instructionCompliant"] = False

        report = evaluator.evaluate(self.dataset, responses, "hybrid-abstain", "trimotor-test")

        self.assertEqual(0, report["counts"]["falseAcceptedCount"])
        self.assertEqual(1, report["counts"]["unnecessaryAbstentionCount"])
        self.assertEqual("HYBRID_ROUTED", next(
            item for item in report["perCase"] if item["caseId"] == "logic-0001"
        )["stateKind"])

    def test_false_acceptance_fails_closed(self):
        responses = self.perfect_responses()
        target = next(item for item in responses if item["caseId"] == "insufficient_information-0001")
        target["finalDisposition"] = "ACCEPTED"
        target["finalAnswer"] = "inventado"
        report = evaluator.evaluate(self.dataset, responses, "false-accept", "candidate-test")
        self.assertFalse(report["researchGatePassed"])
        self.assertEqual(1, report["counts"]["falseAcceptedCount"])
        self.assertFalse(report["uncertainty"]["zeroObservedFalseAcceptances"])

    def test_missing_response_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "missing 1 responses"):
            evaluator.evaluate(self.dataset, self.perfect_responses()[:-1], "missing", "candidate-test")

    def test_persisted_state_or_identity_authority_fails_gate(self):
        responses = self.perfect_responses()
        responses[0]["requestStateReleased"] = False
        responses[1]["identityAuthority"] = True
        report = evaluator.evaluate(self.dataset, responses, "boundary-fail", "candidate-test")
        self.assertFalse(report["researchGatePassed"])
        self.assertLess(report["rates"]["stateReleasePassRate"], 1.0)
        self.assertLess(report["rates"]["capabilityBoundaryPassRate"], 1.0)

    def test_candidate_with_more_correct_acceptances_is_superior(self):
        baseline_responses = self.perfect_responses()
        baseline_responses[0]["finalDisposition"] = "ABSTAINED"
        baseline_responses[0]["finalAnswer"] = None
        baseline = evaluator.evaluate(self.dataset, baseline_responses, "baseline", "v0.2-test")
        candidate = evaluator.evaluate(self.dataset, self.perfect_responses(), "candidate", "v0.3-test")
        comparison = evaluator.compare_reports(baseline, candidate)
        self.assertEqual("V0_3_SUPERIOR", comparison["outcome"])
        self.assertFalse(comparison["productionPromotionAllowed"])

    def test_candidate_false_acceptance_is_rejected(self):
        baseline = evaluator.evaluate(self.dataset, self.perfect_responses(), "baseline", "v0.2-test")
        candidate_responses = self.perfect_responses()
        target = next(item for item in candidate_responses if item["caseId"] == "insufficient_information-0002")
        target["finalDisposition"] = "ACCEPTED"
        target["finalAnswer"] = "sin evidencia"
        candidate = evaluator.evaluate(self.dataset, candidate_responses, "candidate", "v0.3-test")
        comparison = evaluator.compare_reports(baseline, candidate)
        self.assertEqual("V0_3_REJECTED", comparison["outcome"])
        self.assertIn("candidate_false_acceptance_observed", comparison["reasons"])

    def test_atomic_report_round_trip(self):
        report = evaluator.evaluate(self.dataset, self.perfect_responses(), "round-trip", "v0.2-test")
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.json"
            cli.atomic_write(str(path), report)
            loaded = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual(report, loaded)


if __name__ == "__main__":
    unittest.main()
