from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

from validate_trimotor_v02_physical_benchmark_evidence_v1 import (  # noqa: E402
    default_manifest_path,
    load_manifest,
    validate_manifest,
)


class ValidateTrimotorEvidenceV1Test(unittest.TestCase):
    def setUp(self) -> None:
        self.manifest = load_manifest(default_manifest_path())

    def forged(self) -> dict:
        return copy.deepcopy(self.manifest)

    def test_repository_evidence_is_valid(self) -> None:
        validate_manifest(self.manifest)

    def test_schema_pins_identity_and_shape(self) -> None:
        schema = json.loads(default_manifest_path().with_suffix(".schema.json").read_text())
        self.assertFalse(schema["additionalProperties"])
        self.assertEqual(set(self.manifest), set(schema["required"]))
        self.assertEqual(self.manifest["schemaVersion"], schema["properties"]["schemaVersion"]["const"])
        self.assertEqual(self.manifest["sourceMainCommit"], schema["properties"]["sourceMainCommit"]["const"])

    def test_false_acceptance_cannot_be_added(self) -> None:
        forged = self.forged()
        forged["benchmark"]["counts"]["falseAcceptedCount"] = 1
        with self.assertRaisesRegex(ValueError, "manifest benchmark mismatch"):
            validate_manifest(forged)

    def test_gate_cannot_be_softened(self) -> None:
        forged = self.forged()
        forged["benchmark"]["qualityGatePassed"] = False
        with self.assertRaisesRegex(ValueError, "manifest benchmark mismatch"):
            validate_manifest(forged)

    def test_legacy_label_must_be_clarified(self) -> None:
        forged = self.forged()
        forged["comparison"]["outcomeSemantics"] = "A trained v0.3 exists."
        with self.assertRaisesRegex(ValueError, "legacy comparator label"):
            validate_manifest(forged)

    def test_source_archive_identity_is_pinned(self) -> None:
        forged = self.forged()
        forged["sourceArchive"]["sha256"] = "sha256:" + "0" * 64
        with self.assertRaisesRegex(ValueError, "source archive identity"):
            validate_manifest(forged)

    def test_base64_part_metadata_is_pinned(self) -> None:
        forged = self.forged()
        forged["storage"]["archiveBase64Parts"]["parts"][0]["sha256"] = "sha256:" + "0" * 64
        with self.assertRaisesRegex(ValueError, "Base64 metadata"):
            validate_manifest(forged)

    def test_activation_remains_blocked(self) -> None:
        forged = self.forged()
        forged["promotion"]["personalRuntimeActivationAuthorized"] = True
        with self.assertRaisesRegex(ValueError, "cannot authorize activation"):
            validate_manifest(forged)

    def test_blockers_cannot_be_removed(self) -> None:
        forged = self.forged()
        forged["promotion"]["blockers"] = forged["promotion"]["blockers"][:-1]
        with self.assertRaisesRegex(ValueError, "activation blockers"):
            validate_manifest(forged)

    def test_identity_authority_is_rejected(self) -> None:
        forged = self.forged()
        forged["authorityBoundary"]["identityAuthority"] = True
        with self.assertRaisesRegex(ValueError, "authority boundary"):
            validate_manifest(forged)

    def test_source_revision_remains_unknown(self) -> None:
        forged = self.forged()
        forged["artifact"]["sourceModelRevision"] = "0" * 40
        with self.assertRaisesRegex(ValueError, "source model revision"):
            validate_manifest(forged)

    def test_no_private_runtime_content(self) -> None:
        serialized = json.dumps(self.manifest, ensure_ascii=False)
        self.assertFalse(any(token in serialized for token in ("credential", "conversationHistory", "genesisContent", "adbSerial")))


if __name__ == "__main__":
    unittest.main()
