from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("lotus_feasibility_preflight_v0.py")
SPEC = importlib.util.spec_from_file_location("morimil_lotus_preflight_v0", MODULE_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class LotusFeasibilityPreflightV0Test(unittest.TestCase):
    def snapshots(self):
        return MODULE._self_test_snapshots()

    def evidence(self):
        source, hub = self.snapshots()
        return MODULE.build_evidence(
            source,
            hub,
            captured_at_utc="2026-07-23T00:00:00Z",
        )

    def test_valid_preflight_is_research_only_and_blocked(self):
        value = self.evidence()

        MODULE.validate_evidence(value)

        self.assertEqual("research-only", value["status"])
        self.assertEqual(
            "REFERENCE_VERIFIED_EXPERIMENT_BLOCKED",
            value["runtimeFeasibility"]["preflightDecision"],
        )
        self.assertFalse(value["promotion"]["promotionAllowed"])
        self.assertFalse(value["execution"]["gpuUsed"])
        self.assertFalse(value["huggingFaceReference"]["weightsDownloaded"])

    def test_current_adapter_cannot_claim_hidden_state_reinjection(self):
        value = self.evidence()
        value["runtimeFeasibility"][
            "morimilCurrentAdapterSupportsHiddenStateReinjection"
        ] = True

        with self.assertRaises(MODULE.PreflightError):
            MODULE.validate_evidence(value)

    def test_preflight_cannot_activate_normal_runtime(self):
        value = self.evidence()
        value["authorityBoundary"]["normalRuntimeActivated"] = True

        with self.assertRaises(MODULE.PreflightError):
            MODULE.validate_evidence(value)

    def test_preflight_cannot_claim_gpu_or_training_execution(self):
        value = self.evidence()
        value["execution"]["trainingRun"] = True

        with self.assertRaises(MODULE.PreflightError):
            MODULE.validate_evidence(value)

    def test_preflight_cannot_claim_promotion(self):
        value = self.evidence()
        value["promotion"]["promotionAllowed"] = True

        with self.assertRaises(MODULE.PreflightError):
            MODULE.validate_evidence(value)

    def test_missing_license_chain_blocker_is_rejected(self):
        value = self.evidence()
        value["promotion"]["blockers"].remove(
            "BASE_MODEL_LICENSE_CHAIN_REVIEW_REQUIRED"
        )

        with self.assertRaises(MODULE.PreflightError):
            MODULE.validate_evidence(value)

    def test_hub_snapshot_rejects_wrong_base_lineage(self):
        _, valid = self.snapshots()
        forged = MODULE.HubSnapshot(
            model_id=valid.model_id,
            revision=valid.revision,
            gated=valid.gated,
            license_tag=valid.license_tag,
            base_model_id="unrelated/model",
            files=valid.files,
        )

        with self.assertRaises(MODULE.PreflightError):
            MODULE.validate_hub_snapshot(forged)

    def test_source_snapshot_rejects_modified_upstream_file(self):
        valid, _ = self.snapshots()
        hashes = dict(valid.file_sha256)
        hashes["scripts/lotus.py"] = "0" * 64
        forged = MODULE.SourceSnapshot(
            repository=valid.repository,
            commit=valid.commit,
            clean=valid.clean,
            file_sha256=hashes,
        )

        with self.assertRaises(MODULE.PreflightError):
            MODULE.validate_source_snapshot(forged)

    def test_check_rejects_malformed_json(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.json"
            path.write_text("{", encoding="utf-8")

            with self.assertRaises(MODULE.PreflightError):
                MODULE.check(path)

    def test_frozen_evidence_matches_validator(self):
        repo_root = Path(__file__).resolve().parents[2]
        path = (
            repo_root
            / "docs/model-artifacts/morimil-lotus-feasibility-preflight-v0.json"
        )
        value = json.loads(path.read_text(encoding="utf-8"))

        MODULE.validate_evidence(value)
        self.assertEqual(MODULE.UPSTREAM_COMMIT, value["upstreamSource"]["commit"])
        self.assertEqual(
            MODULE.HUB_MODEL_REVISION,
            value["huggingFaceReference"]["revision"],
        )

    def test_self_test_passes(self):
        self.assertEqual(0, MODULE.self_test())


if __name__ == "__main__":
    unittest.main()
