from __future__ import annotations

import importlib.util
import inspect
import json
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name(
    "capture_gemma3n_e2b_hf_provenance_v0.py"
)
SPEC = importlib.util.spec_from_file_location(
    "morimil_hf_provenance_v0",
    MODULE_PATH,
)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class _LfsObject:
    def __init__(self, sha256: str, size: int) -> None:
        self.sha256 = sha256
        self.size = size


class _Sibling:
    def __init__(
        self,
        filename: str,
        lfs: object,
        size: int | None = None,
    ) -> None:
        self.rfilename = filename
        self.lfs = lfs
        self.size = size


class _Info:
    def __init__(
        self,
        *,
        lfs: object,
        sha: str | None = None,
    ) -> None:
        self.sha = sha or MODULE.EXPECTED_HUB_ARTIFACT_REVISION
        self.siblings = [
            _Sibling("README.md", None, 100),
            _Sibling(
                MODULE.EXPECTED_HUB_ARTIFACT_FILENAME,
                lfs,
            ),
        ]
        self.gated = "manual"
        self.card_data = {"license": MODULE.EXPECTED_LICENSE_ID}
        self.tags = [f"license:{MODULE.EXPECTED_LICENSE_ID}"]


class _WhoAmI:
    def __init__(self, value: dict[str, object]) -> None:
        self.value = value

    def whoami(self) -> dict[str, object]:
        return self.value


class HuggingFaceProvenanceV01Test(unittest.TestCase):
    def valid_local(self):
        return MODULE.LocalFileMetadata(
            MODULE.EXPECTED_LOCAL_FILENAME,
            MODULE.EXPECTED_SIZE_BYTES,
            MODULE.EXPECTED_SHA256,
        )

    def valid_hub(self):
        return MODULE.HubFileMetadata(
            MODULE.EXPECTED_HUB_REPOSITORY,
            MODULE.EXPECTED_HUB_ARTIFACT_REVISION,
            MODULE.EXPECTED_HUB_ARTIFACT_REVISION,
            MODULE.EXPECTED_HUB_ARTIFACT_FILENAME,
            MODULE.EXPECTED_SIZE_BYTES,
            MODULE.EXPECTED_SHA256,
            True,
            MODULE.EXPECTED_LICENSE_ID,
        )

    def valid_evidence(self):
        return MODULE.build_evidence(
            self.valid_local(),
            self.valid_hub(),
            authenticated_user="Morimil",
            huggingface_hub_version="1.0-test",
            captured_at_utc="2026-07-22T00:00:00Z",
        )

    def assert_rejected(self, value):
        with self.assertRaises(MODULE.ProvenanceError):
            MODULE.validate_evidence(value)

    def test_exact_identity_builds_research_only_evidence(self):
        value = self.valid_evidence()
        MODULE.validate_evidence(value)
        self.assertEqual(
            MODULE.ACQUISITION_MODE,
            value["provenance"]["acquisitionMode"],
        )
        self.assertFalse(
            value["provenance"]["conversionPerformedByMorimil"]
        )
        self.assertTrue(
            value["provenance"]["upstreamByteIdentityVerified"]
        )
        self.assertTrue(
            value["provenance"]["reproducibleAcquisitionEvidence"]
        )
        self.assertIsNone(
            value["provenance"]["sourceModelRevision"]
        )
        self.assertFalse(value["promotion"]["promotionAllowed"])

    def test_schema_document_matches_capture_contract(self):
        schema = MODULE.validate_schema_document()
        self.assertEqual(MODULE.build_schema(), schema)
        self.assertEqual(
            f"sha256:{MODULE.EXPECTED_SHA256}",
            schema["properties"]["huggingFace"]["properties"][
                "artifactSha256"
            ]["const"],
        )

    def test_mismatched_upstream_hash_fails_closed(self):
        valid = self.valid_hub()
        forged = MODULE.HubFileMetadata(
            valid.repository,
            valid.requested_revision,
            valid.resolved_revision,
            valid.filename,
            valid.size_bytes,
            "0" * 64,
            valid.gated,
            valid.license_id,
        )
        with self.assertRaises(MODULE.ProvenanceError):
            MODULE.validate_byte_identity(self.valid_local(), forged)

    def test_wrong_artifact_revision_fails_closed(self):
        info = _Info(
            lfs={
                "sha256": MODULE.EXPECTED_SHA256,
                "size": MODULE.EXPECTED_SIZE_BYTES,
            },
            sha="1" * 40,
        )
        with self.assertRaises(MODULE.ProvenanceError):
            MODULE.extract_hub_file_metadata(info)

    def test_dict_lfs_metadata_is_supported(self):
        info = _Info(
            lfs={
                "sha256": MODULE.EXPECTED_SHA256,
                "size": MODULE.EXPECTED_SIZE_BYTES,
            }
        )
        metadata = MODULE.extract_hub_file_metadata(info)
        self.assertEqual(MODULE.EXPECTED_SHA256, metadata.sha256)
        self.assertEqual(
            MODULE.EXPECTED_SIZE_BYTES,
            metadata.size_bytes,
        )

    def test_object_lfs_metadata_is_supported(self):
        info = _Info(
            lfs=_LfsObject(
                MODULE.EXPECTED_SHA256,
                MODULE.EXPECTED_SIZE_BYTES,
            )
        )
        metadata = MODULE.extract_hub_file_metadata(info)
        self.assertEqual(MODULE.EXPECTED_SHA256, metadata.sha256)

    def test_missing_lfs_sha_fails_closed(self):
        with self.assertRaises(MODULE.ProvenanceError):
            MODULE.extract_hub_file_metadata(
                _Info(lfs={"size": MODULE.EXPECTED_SIZE_BYTES})
            )

    def test_unknown_root_claim_fails_closed(self):
        value = self.valid_evidence()
        value["productionAuthorization"] = True
        self.assert_rejected(value)

    def test_unknown_nested_claim_fails_closed(self):
        value = self.valid_evidence()
        value["authorityBoundary"]["productionAuthorization"] = True
        self.assert_rejected(value)

    def test_unknown_local_candidate_claim_fails_closed(self):
        value = self.valid_evidence()
        value["localCandidate"]["downloadUrl"] = "https://example.invalid"
        self.assert_rejected(value)

    def test_forged_official_allowlist_fails_closed(self):
        value = self.valid_evidence()
        value["officialGoogleAllowlist"]["revision"] = "0" * 40
        self.assert_rejected(value)

    def test_source_model_revision_cannot_be_forged(self):
        value = self.valid_evidence()
        value["provenance"][
            "sourceModelRevision"
        ] = MODULE.EXPECTED_HUB_ARTIFACT_REVISION
        self.assert_rejected(value)

    def test_promotion_cannot_be_claimed(self):
        value = self.valid_evidence()
        value["promotion"]["promotionAllowed"] = True
        self.assert_rejected(value)

    def test_extra_promotion_blocker_fails_closed(self):
        value = self.valid_evidence()
        value["promotion"]["blockers"].append(
            "PRODUCTION_AUTHORIZATION_GRANTED"
        )
        self.assert_rejected(value)

    def test_authority_cannot_be_claimed(self):
        value = self.valid_evidence()
        value["authorityBoundary"]["identityAuthority"] = True
        self.assert_rejected(value)

    def test_wrong_capture_tool_version_fails_closed(self):
        value = self.valid_evidence()
        value["captureToolVersion"] = "forged"
        self.assert_rejected(value)

    def test_noncanonical_timestamp_fails_closed(self):
        value = self.valid_evidence()
        value["capturedAtUtc"] = "2026-07-22T00:00:00+00:00"
        self.assert_rejected(value)

    def test_authenticated_user_must_be_public_username(self):
        value = self.valid_evidence()
        value["huggingFace"][
            "authenticatedUser"
        ] = "private@example.com"
        self.assert_rejected(value)

    def test_whoami_uses_name_and_never_email(self):
        self.assertEqual(
            "Morimil",
            MODULE._authenticated_user(
                _WhoAmI(
                    {
                        "name": "Morimil",
                        "email": "private@example.com",
                    }
                )
            ),
        )
        with self.assertRaises(MODULE.ProvenanceError):
            MODULE._authenticated_user(
                _WhoAmI({"email": "private@example.com"})
            )

    def test_capture_accepts_no_cli_token_argument(self):
        parameters = inspect.signature(MODULE.capture_live).parameters
        self.assertEqual(
            ["artifact_path", "output_path"],
            list(parameters),
        )
        with self.assertRaises(SystemExit):
            MODULE._parser().parse_args(
                [
                    "capture",
                    "artifact.litertlm",
                    "--output",
                    "evidence.json",
                    "--token",
                    "hf_forbidden",
                ]
            )

    def test_check_rejects_malformed_json(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.json"
            path.write_text("{", encoding="utf-8")
            with self.assertRaises(MODULE.ProvenanceError):
                MODULE.check_evidence(path)

    def test_discovery_snapshot_matches_capture_policy(self):
        repo_root = Path(__file__).resolve().parents[2]
        path = (
            repo_root
            / "docs/model-artifacts/"
            "morimil-deliberative-v0.2-hf-provenance-discovery-v0.json"
        )
        value = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual(
            MODULE.EXPECTED_HUB_REPOSITORY,
            value["huggingFace"]["repository"],
        )
        self.assertEqual(
            MODULE.EXPECTED_HUB_ARTIFACT_REVISION,
            value["huggingFace"]["artifactRevision"],
        )
        self.assertEqual(
            MODULE.EXPECTED_SIZE_BYTES,
            value["huggingFace"]["artifactSizeBytes"],
        )
        self.assertFalse(
            value["provenanceClassification"][
                "upstreamByteIdentityVerified"
            ]
        )
        self.assertFalse(value["promotion"]["promotionAllowed"])
        self.assertEqual(
            MODULE.SCHEMA_VERSION,
            value["requiredAuthenticatedCapture"][
                "schemaVersion"
            ],
        )
        self.assertEqual(
            MODULE.SCHEMA_RELATIVE_PATH,
            value["requiredAuthenticatedCapture"]["schemaPath"],
        )

    def test_self_test_passes(self):
        self.assertEqual(0, MODULE.self_test())


if __name__ == "__main__":
    unittest.main()
