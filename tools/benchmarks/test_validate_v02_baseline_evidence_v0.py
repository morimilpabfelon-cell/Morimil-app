from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

from validate_v02_baseline_evidence_v0 import (  # noqa: E402
    default_manifest_path,
    load_manifest,
    validate_manifest,
)


class ValidateV02BaselineEvidenceV0Test(unittest.TestCase):
    def setUp(self) -> None:
        self.manifest = load_manifest(default_manifest_path())

    def test_repository_snapshot_is_valid(self) -> None:
        validate_manifest(self.manifest)

    def test_benchmark_cannot_be_marked_executed_without_evidence(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["benchmark"]["runStatus"] = "EXECUTED"
        with self.assertRaisesRegex(ValueError, "has not been executed"):
            validate_manifest(forged)

    def test_unexecuted_benchmark_cannot_contain_report(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["benchmark"]["report"] = {"acceptedCorrectRate": 1.0}
        with self.assertRaisesRegex(ValueError, "cannot contain results"):
            validate_manifest(forged)

    def test_artifact_repository_revision_cannot_be_source_revision(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["artifact"]["sourceModelRevision"] = forged["artifact"][
            "upstreamRepositoryRevision"
        ]
        with self.assertRaisesRegex(ValueError, "source model revision must remain unknown"):
            validate_manifest(forged)

    def test_raw_report_cannot_be_claimed_as_versioned(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["physicalEvidence"]["rawReportVersioned"] = True
        with self.assertRaisesRegex(ValueError, "raw reports are not versioned"):
            validate_manifest(forged)

    def test_physical_numbers_cannot_be_silently_changed(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["physicalEvidence"]["p95InferenceMilliseconds"] = 1
        with self.assertRaisesRegex(ValueError, "immutable baseline digest mismatch"):
            validate_manifest(forged)

    def test_production_authorization_is_rejected(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["promotion"]["productionAuthorization"] = True
        with self.assertRaisesRegex(ValueError, "cannot authorize production"):
            validate_manifest(forged)

    def test_external_motor_cannot_become_morimil_identity(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["authorityBoundary"]["externalMotorMayBecomeIdentity"] = True
        with self.assertRaisesRegex(ValueError, "identity or memory boundary violated"):
            validate_manifest(forged)

    def test_source_references_remain_pinned_to_snapshot_commit(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["sourceReferences"][0]["commit"] = "0" * 40
        with self.assertRaisesRegex(ValueError, "source reference commit mismatch"):
            validate_manifest(forged)

    def test_manifest_is_json_serializable_without_private_runtime_data(self) -> None:
        serialized = json.dumps(self.manifest, ensure_ascii=False)
        forbidden = ("credential", "conversationHistory", "genesisContent")
        self.assertFalse(any(token in serialized for token in forbidden))


if __name__ == "__main__":
    unittest.main()
