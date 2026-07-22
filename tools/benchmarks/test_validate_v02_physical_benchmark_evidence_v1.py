from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

from validate_v02_physical_benchmark_evidence_v1 import (  # noqa: E402
    default_manifest_path,
    load_manifest,
    validate_manifest,
)


class ValidateV02PhysicalBenchmarkEvidenceV1Test(unittest.TestCase):
    def setUp(self) -> None:
        self.manifest = load_manifest(default_manifest_path())

    def test_repository_evidence_is_valid(self) -> None:
        validate_manifest(self.manifest)

    def test_schema_pins_identity_and_top_level_shape(self) -> None:
        schema_path = default_manifest_path().with_suffix(".schema.json")
        with schema_path.open("r", encoding="utf-8") as handle:
            schema = json.load(handle)
        self.assertFalse(schema["additionalProperties"])
        self.assertEqual(set(self.manifest), set(schema["required"]))
        self.assertEqual(
            self.manifest["schemaVersion"],
            schema["properties"]["schemaVersion"]["const"],
        )
        self.assertEqual(
            self.manifest["sourceMainCommit"],
            schema["properties"]["sourceMainCommit"]["const"],
        )

    def test_quality_gate_cannot_be_rewritten_as_passed(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["benchmark"]["qualityGatePassed"] = True
        with self.assertRaisesRegex(ValueError, "quality gate must remain failed"):
            validate_manifest(forged)

    def test_failed_run_status_cannot_be_softened(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["benchmark"]["runStatus"] = "COMPLETED"
        with self.assertRaisesRegex(ValueError, "benchmark run status mismatch"):
            validate_manifest(forged)

    def test_false_acceptance_count_cannot_be_changed(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["benchmark"]["counts"]["falseAcceptedCount"] = 0
        with self.assertRaisesRegex(ValueError, "manifest benchmark counts differ"):
            validate_manifest(forged)

    def test_base64_part_digest_cannot_be_changed(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["storage"]["archiveBase64Parts"]["parts"][0]["sha256"] = (
            "sha256:" + "0" * 64
        )
        with self.assertRaisesRegex(ValueError, "manifest Base64 part digest mismatch"):
            validate_manifest(forged)

    def test_concatenated_base64_digest_cannot_be_changed(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["storage"]["archiveBase64Parts"]["concatenatedBase64Sha256"] = (
            "sha256:" + "0" * 64
        )
        with self.assertRaisesRegex(
            ValueError, "manifest concatenated Base64 digest mismatch"
        ):
            validate_manifest(forged)

    def test_source_model_revision_remains_unknown(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["artifact"]["sourceModelRevision"] = "0" * 40
        with self.assertRaisesRegex(ValueError, "source model revision must remain unknown"):
            validate_manifest(forged)

    def test_production_authorization_is_rejected(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["promotion"]["productionAuthorization"] = True
        with self.assertRaisesRegex(ValueError, "cannot authorize production"):
            validate_manifest(forged)

    def test_quality_blocker_cannot_be_removed(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["promotion"]["blockers"] = forged["promotion"]["blockers"][1:]
        with self.assertRaisesRegex(ValueError, "promotion blockers mismatch"):
            validate_manifest(forged)

    def test_identity_authority_is_rejected(self) -> None:
        forged = copy.deepcopy(self.manifest)
        forged["authorityBoundary"]["identityAuthority"] = True
        with self.assertRaisesRegex(ValueError, "authority boundary violated"):
            validate_manifest(forged)

    def test_manifest_contains_no_private_runtime_content(self) -> None:
        serialized = json.dumps(self.manifest, ensure_ascii=False)
        forbidden = ("credential", "conversationHistory", "genesisContent", "adbSerial")
        self.assertFalse(any(token in serialized for token in forbidden))


if __name__ == "__main__":
    unittest.main()
