"""Fail-closed Hugging Face provenance capture for Morimil Deliberative v0.2."""
from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import os
import re
import sys
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

TOOL_VERSION = "morimil.hf-provenance-capture.v0.1"
SCHEMA_VERSION = "morimil.deliberative.v0.2.hf-acquisition-evidence.v0.1"
SCHEMA_RELATIVE_PATH = (
    "docs/model-artifacts/"
    "morimil-deliberative-v0.2-hf-acquisition-evidence-v0.1.schema.json"
)
EXPECTED_HUB_REPOSITORY = "google/gemma-3n-E2B-it-litert-lm"
EXPECTED_HUB_ARTIFACT_REVISION = "ba9ca88da013b537b6ed38108be609b8db1c3a16"
OBSERVED_HUB_REPOSITORY_SNAPSHOT = "c03b6f60b8da6c5400b6838a2cf26420f80c0a01"
EXPECTED_HUB_ARTIFACT_FILENAME = "gemma-3n-E2B-it-int4.litertlm"
EXPECTED_LOCAL_FILENAME = "morimil-deliberative-v0.2.candidate.litertlm"
EXPECTED_SIZE_BYTES = 3_655_827_456
EXPECTED_SHA256 = "2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6"
EXPECTED_LICENSE_ID = "gemma"
SOURCE_MODEL_ID = "google/gemma-3n-E2B-it"
OFFICIAL_ALLOWLIST_REPOSITORY = "google-ai-edge/gallery"
OFFICIAL_ALLOWLIST_REVISION = "126501c8849affcfb094d2c5b193aa5deb1434a6"
OFFICIAL_ALLOWLIST_PATH = "model_allowlists/1_0_15.json"
ACQUISITION_MODE = "DIRECT_UPSTREAM_BINARY_RENAME_ONLY"
SOURCE_MODEL_REVISION_STATUS = "UPSTREAM_NOT_DISCLOSED"
EVIDENCE_KIND = "verified-direct-upstream-binary-acquisition"
STATUS = "research-only"
PROMOTION_BLOCKERS = [
    "UPSTREAM_BASE_CHECKPOINT_REVISION_UNDISCLOSED",
    "CERTIFICATION_MISSING",
    "SIGNATURE_MISSING",
    "INSTALLATION_AUTHORIZATION_MISSING",
    "PERSONAL_RUNTIME_ACTIVATION_AUTHORIZATION_MISSING",
]
SHA256_PATTERN = re.compile(r"^(?:sha256:)?([0-9a-fA-F]{64})$")
HF_USERNAME_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$")


class ProvenanceError(RuntimeError):
    pass


@dataclass(frozen=True)
class LocalFileMetadata:
    filename: str
    size_bytes: int
    sha256: str


@dataclass(frozen=True)
class HubFileMetadata:
    repository: str
    requested_revision: str
    resolved_revision: str
    filename: str
    size_bytes: int
    sha256: str
    gated: bool
    license_id: str


def fail(message: str) -> None:
    raise ProvenanceError(message)


def normalize_sha256(value: str) -> str:
    match = SHA256_PATTERN.fullmatch(str(value).strip())
    if not match:
        fail(f"invalid sha256 value: {value!r}")
    return match.group(1).lower()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def inspect_local_file(path: Path) -> LocalFileMetadata:
    if not path.is_file():
        fail(f"artifact file not found: {path}")
    return LocalFileMetadata(path.name, path.stat().st_size, sha256_file(path))


def _field(value: Any, key: str) -> Any:
    if isinstance(value, dict):
        return value.get(key)
    return getattr(value, key, None)


def _license(info: Any) -> str | None:
    card_data = getattr(info, "card_data", None)
    value = _field(card_data, "license") if card_data is not None else None
    if isinstance(value, str) and value:
        return value
    for tag in getattr(info, "tags", None) or []:
        if isinstance(tag, str) and tag.startswith("license:"):
            return tag.split(":", 1)[1]
    return None


def extract_hub_file_metadata(info: Any) -> HubFileMetadata:
    resolved = str(getattr(info, "sha", "") or "")
    if resolved != EXPECTED_HUB_ARTIFACT_REVISION:
        fail(
            "hub revision mismatch: "
            f"expected {EXPECTED_HUB_ARTIFACT_REVISION}, found {resolved!r}"
        )
    target = next(
        (
            item
            for item in (getattr(info, "siblings", None) or [])
            if getattr(item, "rfilename", None) == EXPECTED_HUB_ARTIFACT_FILENAME
        ),
        None,
    )
    if target is None:
        fail(f"hub artifact not found: {EXPECTED_HUB_ARTIFACT_FILENAME}")
    lfs = getattr(target, "lfs", None)
    sha = _field(lfs, "sha256") if lfs is not None else None
    size = _field(lfs, "size") if lfs is not None else None
    if size is None:
        size = getattr(target, "size", None)
    if not isinstance(size, int):
        fail("hub artifact size metadata is missing")
    if not isinstance(sha, str):
        fail("hub artifact LFS sha256 metadata is missing")
    license_id = _license(info)
    if license_id != EXPECTED_LICENSE_ID:
        fail(
            f"hub license mismatch: expected {EXPECTED_LICENSE_ID!r}, "
            f"found {license_id!r}"
        )
    gated = bool(getattr(info, "gated", False))
    if not gated:
        fail("expected gated Gemma repository")
    return HubFileMetadata(
        EXPECTED_HUB_REPOSITORY,
        EXPECTED_HUB_ARTIFACT_REVISION,
        resolved,
        EXPECTED_HUB_ARTIFACT_FILENAME,
        size,
        normalize_sha256(sha),
        gated,
        license_id,
    )


def validate_byte_identity(
    local: LocalFileMetadata,
    hub: HubFileMetadata,
) -> None:
    if local.filename != EXPECTED_LOCAL_FILENAME:
        fail("local filename mismatch")
    if local.size_bytes != EXPECTED_SIZE_BYTES:
        fail(f"local size mismatch: {local.size_bytes}")
    local_sha = normalize_sha256(local.sha256)
    if local_sha != EXPECTED_SHA256:
        fail(f"local sha256 mismatch: {local_sha}")
    if hub.repository != EXPECTED_HUB_REPOSITORY:
        fail("hub repository mismatch")
    if hub.requested_revision != EXPECTED_HUB_ARTIFACT_REVISION:
        fail("hub requested revision mismatch")
    if hub.resolved_revision != EXPECTED_HUB_ARTIFACT_REVISION:
        fail("hub resolved revision mismatch")
    if hub.filename != EXPECTED_HUB_ARTIFACT_FILENAME:
        fail("hub filename mismatch")
    if hub.size_bytes != EXPECTED_SIZE_BYTES:
        fail(f"hub size mismatch: {hub.size_bytes}")
    if normalize_sha256(hub.sha256) != local_sha:
        fail("local candidate is not byte-identical to the pinned Hub artifact")
    if not hub.gated or hub.license_id != EXPECTED_LICENSE_ID:
        fail("hub policy metadata mismatch")


def atomic_write_json(path: Path, value: Any) -> None:
    payload = (
        json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
    ).encode()
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        "wb",
        dir=path.parent,
        delete=False,
    ) as handle:
        temporary = Path(handle.name)
        handle.write(payload)
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temporary, path)


def _canonical_utc_timestamp(value: str) -> bool:
    if not isinstance(value, str) or not value.endswith("Z"):
        return False
    try:
        parsed = datetime.fromisoformat(value[:-1] + "+00:00")
    except ValueError:
        return False
    if parsed.utcoffset() != timezone.utc.utcoffset(parsed):
        return False
    return parsed.isoformat().replace("+00:00", "Z") == value


def _require_exact_keys(
    value: Any,
    expected: set[str],
    label: str,
) -> dict[str, Any]:
    if not isinstance(value, dict):
        fail(f"{label} must be an object")
    actual = set(value)
    if actual != expected:
        missing = sorted(expected - actual)
        unexpected = sorted(actual - expected)
        fail(
            f"{label} keys mismatch: "
            f"missing={missing}, unexpected={unexpected}"
        )
    return value


def build_schema() -> dict[str, Any]:
    return {
        "$id": (
            "morimil.deliberative.v0.2."
            "hf-acquisition-evidence-v0.1.schema.json"
        ),
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "additionalProperties": False,
        "properties": {
            "authorityBoundary": {
                "additionalProperties": False,
                "properties": {
                    "identityAuthority": {"const": False},
                    "lifecycleAuthority": {"const": False},
                    "memoryWriteCapability": {"const": False},
                    "normalRuntimeActivated": {"const": False},
                },
                "required": [
                    "memoryWriteCapability",
                    "identityAuthority",
                    "lifecycleAuthority",
                    "normalRuntimeActivated",
                ],
                "type": "object",
            },
            "captureToolVersion": {"const": TOOL_VERSION},
            "capturedAtUtc": {
                "format": "date-time",
                "pattern": "Z$",
                "type": "string",
            },
            "evidenceKind": {"const": EVIDENCE_KIND},
            "huggingFace": {
                "additionalProperties": False,
                "properties": {
                    "artifactFilename": {
                        "const": EXPECTED_HUB_ARTIFACT_FILENAME
                    },
                    "artifactSha256": {
                        "const": f"sha256:{EXPECTED_SHA256}"
                    },
                    "artifactSizeBytes": {"const": EXPECTED_SIZE_BYTES},
                    "authenticatedUser": {
                        "pattern": HF_USERNAME_PATTERN.pattern,
                        "type": "string",
                    },
                    "clientVersion": {
                        "minLength": 1,
                        "type": "string",
                    },
                    "gated": {"const": True},
                    "licenseId": {"const": EXPECTED_LICENSE_ID},
                    "observedRepositorySnapshot": {
                        "const": OBSERVED_HUB_REPOSITORY_SNAPSHOT
                    },
                    "repository": {"const": EXPECTED_HUB_REPOSITORY},
                    "requestedArtifactRevision": {
                        "const": EXPECTED_HUB_ARTIFACT_REVISION
                    },
                    "resolvedArtifactRevision": {
                        "const": EXPECTED_HUB_ARTIFACT_REVISION
                    },
                },
                "required": [
                    "authenticatedUser",
                    "clientVersion",
                    "repository",
                    "gated",
                    "licenseId",
                    "requestedArtifactRevision",
                    "resolvedArtifactRevision",
                    "observedRepositorySnapshot",
                    "artifactFilename",
                    "artifactSizeBytes",
                    "artifactSha256",
                ],
                "type": "object",
            },
            "localCandidate": {
                "additionalProperties": False,
                "properties": {
                    "artifactSha256": {
                        "const": f"sha256:{EXPECTED_SHA256}"
                    },
                    "artifactSizeBytes": {"const": EXPECTED_SIZE_BYTES},
                    "filename": {"const": EXPECTED_LOCAL_FILENAME},
                },
                "required": [
                    "filename",
                    "artifactSizeBytes",
                    "artifactSha256",
                ],
                "type": "object",
            },
            "officialGoogleAllowlist": {
                "additionalProperties": False,
                "properties": {
                    "modelFile": {
                        "const": EXPECTED_HUB_ARTIFACT_FILENAME
                    },
                    "modelFileRevision": {
                        "const": EXPECTED_HUB_ARTIFACT_REVISION
                    },
                    "modelId": {"const": EXPECTED_HUB_REPOSITORY},
                    "path": {"const": OFFICIAL_ALLOWLIST_PATH},
                    "repository": {
                        "const": OFFICIAL_ALLOWLIST_REPOSITORY
                    },
                    "revision": {"const": OFFICIAL_ALLOWLIST_REVISION},
                    "sizeInBytes": {"const": EXPECTED_SIZE_BYTES},
                },
                "required": [
                    "repository",
                    "revision",
                    "path",
                    "modelId",
                    "modelFile",
                    "modelFileRevision",
                    "sizeInBytes",
                ],
                "type": "object",
            },
            "promotion": {
                "additionalProperties": False,
                "properties": {
                    "blockers": {"const": PROMOTION_BLOCKERS},
                    "certified": {"const": False},
                    "installed": {"const": False},
                    "personalRuntimeActivationAuthorized": {
                        "const": False
                    },
                    "promotionAllowed": {"const": False},
                    "signed": {"const": False},
                },
                "required": [
                    "certified",
                    "signed",
                    "installed",
                    "personalRuntimeActivationAuthorized",
                    "promotionAllowed",
                    "blockers",
                ],
                "type": "object",
            },
            "provenance": {
                "additionalProperties": False,
                "properties": {
                    "acquisitionMode": {"const": ACQUISITION_MODE},
                    "conversionPerformedByMorimil": {"const": False},
                    "renameOnly": {"const": True},
                    "reproducibleAcquisitionEvidence": {"const": True},
                    "reproducibleConversionEvidence": {"const": False},
                    "sourceModelId": {"const": SOURCE_MODEL_ID},
                    "sourceModelRevision": {"const": None},
                    "sourceModelRevisionStatus": {
                        "const": SOURCE_MODEL_REVISION_STATUS
                    },
                    "upstreamByteIdentityVerified": {"const": True},
                },
                "required": [
                    "acquisitionMode",
                    "conversionPerformedByMorimil",
                    "renameOnly",
                    "upstreamByteIdentityVerified",
                    "reproducibleAcquisitionEvidence",
                    "reproducibleConversionEvidence",
                    "sourceModelId",
                    "sourceModelRevision",
                    "sourceModelRevisionStatus",
                ],
                "type": "object",
            },
            "schemaVersion": {"const": SCHEMA_VERSION},
            "status": {"const": STATUS},
        },
        "required": [
            "schemaVersion",
            "status",
            "evidenceKind",
            "captureToolVersion",
            "capturedAtUtc",
            "huggingFace",
            "localCandidate",
            "officialGoogleAllowlist",
            "provenance",
            "authorityBoundary",
            "promotion",
        ],
        "title": (
            "Morimil deliberative v0.2 "
            "Hugging Face acquisition evidence v0.1"
        ),
        "type": "object",
    }


def validate_schema_document(
    schema_path: Path | None = None,
) -> dict[str, Any]:
    path = schema_path or (
        Path(__file__).resolve().parents[2] / SCHEMA_RELATIVE_PATH
    )
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"could not read acquisition schema: {error}")
    if value != build_schema():
        fail("acquisition schema differs from the capture contract")
    return value


def build_evidence(
    local: LocalFileMetadata,
    hub: HubFileMetadata,
    *,
    authenticated_user: str,
    huggingface_hub_version: str,
    captured_at_utc: str | None = None,
) -> dict[str, Any]:
    validate_byte_identity(local, hub)
    if not HF_USERNAME_PATTERN.fullmatch(authenticated_user):
        fail("authenticated Hugging Face username is invalid")
    if not isinstance(huggingface_hub_version, str) or not huggingface_hub_version:
        fail("huggingface_hub client version is required")
    captured_at = captured_at_utc or (
        datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    )
    if not _canonical_utc_timestamp(captured_at):
        fail("capturedAtUtc must be a canonical UTC timestamp")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "status": STATUS,
        "evidenceKind": EVIDENCE_KIND,
        "captureToolVersion": TOOL_VERSION,
        "capturedAtUtc": captured_at,
        "huggingFace": {
            "authenticatedUser": authenticated_user,
            "clientVersion": huggingface_hub_version,
            "repository": hub.repository,
            "gated": hub.gated,
            "licenseId": hub.license_id,
            "requestedArtifactRevision": hub.requested_revision,
            "resolvedArtifactRevision": hub.resolved_revision,
            "observedRepositorySnapshot": (
                OBSERVED_HUB_REPOSITORY_SNAPSHOT
            ),
            "artifactFilename": hub.filename,
            "artifactSizeBytes": hub.size_bytes,
            "artifactSha256": (
                f"sha256:{normalize_sha256(hub.sha256)}"
            ),
        },
        "localCandidate": {
            "filename": local.filename,
            "artifactSizeBytes": local.size_bytes,
            "artifactSha256": (
                f"sha256:{normalize_sha256(local.sha256)}"
            ),
        },
        "officialGoogleAllowlist": {
            "repository": OFFICIAL_ALLOWLIST_REPOSITORY,
            "revision": OFFICIAL_ALLOWLIST_REVISION,
            "path": OFFICIAL_ALLOWLIST_PATH,
            "modelId": EXPECTED_HUB_REPOSITORY,
            "modelFile": EXPECTED_HUB_ARTIFACT_FILENAME,
            "modelFileRevision": EXPECTED_HUB_ARTIFACT_REVISION,
            "sizeInBytes": EXPECTED_SIZE_BYTES,
        },
        "provenance": {
            "acquisitionMode": ACQUISITION_MODE,
            "conversionPerformedByMorimil": False,
            "renameOnly": True,
            "upstreamByteIdentityVerified": True,
            "reproducibleAcquisitionEvidence": True,
            "reproducibleConversionEvidence": False,
            "sourceModelId": SOURCE_MODEL_ID,
            "sourceModelRevision": None,
            "sourceModelRevisionStatus": SOURCE_MODEL_REVISION_STATUS,
        },
        "authorityBoundary": {
            "memoryWriteCapability": False,
            "identityAuthority": False,
            "lifecycleAuthority": False,
            "normalRuntimeActivated": False,
        },
        "promotion": {
            "certified": False,
            "signed": False,
            "installed": False,
            "personalRuntimeActivationAuthorized": False,
            "promotionAllowed": False,
            "blockers": PROMOTION_BLOCKERS.copy(),
        },
    }


def validate_evidence(value: dict[str, Any]) -> None:
    root = _require_exact_keys(
        value,
        {
            "schemaVersion",
            "status",
            "evidenceKind",
            "captureToolVersion",
            "capturedAtUtc",
            "huggingFace",
            "localCandidate",
            "officialGoogleAllowlist",
            "provenance",
            "authorityBoundary",
            "promotion",
        },
        "evidence",
    )
    if root["schemaVersion"] != SCHEMA_VERSION:
        fail("evidence schema version mismatch")
    if root["status"] != STATUS:
        fail("evidence status mismatch")
    if root["evidenceKind"] != EVIDENCE_KIND:
        fail("evidence kind mismatch")
    if root["captureToolVersion"] != TOOL_VERSION:
        fail("capture tool version mismatch")
    if not _canonical_utc_timestamp(root["capturedAtUtc"]):
        fail("capturedAtUtc is not a canonical UTC timestamp")

    hf = _require_exact_keys(
        root["huggingFace"],
        {
            "authenticatedUser",
            "clientVersion",
            "repository",
            "gated",
            "licenseId",
            "requestedArtifactRevision",
            "resolvedArtifactRevision",
            "observedRepositorySnapshot",
            "artifactFilename",
            "artifactSizeBytes",
            "artifactSha256",
        },
        "huggingFace",
    )
    if not HF_USERNAME_PATTERN.fullmatch(
        str(hf["authenticatedUser"])
    ):
        fail("authenticated Hugging Face username is invalid")
    if not isinstance(hf["clientVersion"], str) or not hf["clientVersion"]:
        fail("huggingface_hub client version is missing")
    if (
        hf["observedRepositorySnapshot"]
        != OBSERVED_HUB_REPOSITORY_SNAPSHOT
    ):
        fail("observed repository snapshot mismatch")

    local = _require_exact_keys(
        root["localCandidate"],
        {"filename", "artifactSizeBytes", "artifactSha256"},
        "localCandidate",
    )
    validate_byte_identity(
        LocalFileMetadata(
            local["filename"],
            local["artifactSizeBytes"],
            local["artifactSha256"],
        ),
        HubFileMetadata(
            hf["repository"],
            hf["requestedArtifactRevision"],
            hf["resolvedArtifactRevision"],
            hf["artifactFilename"],
            hf["artifactSizeBytes"],
            hf["artifactSha256"],
            hf["gated"] is True,
            hf["licenseId"],
        ),
    )

    allowlist = _require_exact_keys(
        root["officialGoogleAllowlist"],
        {
            "repository",
            "revision",
            "path",
            "modelId",
            "modelFile",
            "modelFileRevision",
            "sizeInBytes",
        },
        "officialGoogleAllowlist",
    )
    expected_allowlist = {
        "repository": OFFICIAL_ALLOWLIST_REPOSITORY,
        "revision": OFFICIAL_ALLOWLIST_REVISION,
        "path": OFFICIAL_ALLOWLIST_PATH,
        "modelId": EXPECTED_HUB_REPOSITORY,
        "modelFile": EXPECTED_HUB_ARTIFACT_FILENAME,
        "modelFileRevision": EXPECTED_HUB_ARTIFACT_REVISION,
        "sizeInBytes": EXPECTED_SIZE_BYTES,
    }
    if allowlist != expected_allowlist:
        fail("official Google allowlist claims mismatch")

    provenance = _require_exact_keys(
        root["provenance"],
        {
            "acquisitionMode",
            "conversionPerformedByMorimil",
            "renameOnly",
            "upstreamByteIdentityVerified",
            "reproducibleAcquisitionEvidence",
            "reproducibleConversionEvidence",
            "sourceModelId",
            "sourceModelRevision",
            "sourceModelRevisionStatus",
        },
        "provenance",
    )
    expected_provenance = {
        "acquisitionMode": ACQUISITION_MODE,
        "conversionPerformedByMorimil": False,
        "renameOnly": True,
        "upstreamByteIdentityVerified": True,
        "reproducibleAcquisitionEvidence": True,
        "reproducibleConversionEvidence": False,
        "sourceModelId": SOURCE_MODEL_ID,
        "sourceModelRevision": None,
        "sourceModelRevisionStatus": SOURCE_MODEL_REVISION_STATUS,
    }
    if provenance != expected_provenance:
        fail("provenance claims mismatch")

    boundary = _require_exact_keys(
        root["authorityBoundary"],
        {
            "memoryWriteCapability",
            "identityAuthority",
            "lifecycleAuthority",
            "normalRuntimeActivated",
        },
        "authorityBoundary",
    )
    expected_boundary = {
        "memoryWriteCapability": False,
        "identityAuthority": False,
        "lifecycleAuthority": False,
        "normalRuntimeActivated": False,
    }
    if boundary != expected_boundary:
        fail("authority boundary claims mismatch")

    promotion = _require_exact_keys(
        root["promotion"],
        {
            "certified",
            "signed",
            "installed",
            "personalRuntimeActivationAuthorized",
            "promotionAllowed",
            "blockers",
        },
        "promotion",
    )
    expected_promotion = {
        "certified": False,
        "signed": False,
        "installed": False,
        "personalRuntimeActivationAuthorized": False,
        "promotionAllowed": False,
        "blockers": PROMOTION_BLOCKERS,
    }
    if promotion != expected_promotion:
        fail("promotion claims mismatch")


def _authenticated_user(api: Any) -> str:
    who = api.whoami()
    if isinstance(who, dict):
        value = who.get("name")
        if isinstance(value, str) and HF_USERNAME_PATTERN.fullmatch(value):
            return value
    fail("could not identify a public authenticated Hugging Face username")


def capture_live(
    artifact_path: Path,
    output_path: Path,
) -> dict[str, Any]:
    try:
        from huggingface_hub import HfApi
    except ImportError:
        fail("huggingface_hub is required for live capture")
    token = os.environ.get("HF_TOKEN")
    if not token:
        fail("HF_TOKEN is required for the gated repository")
    local = inspect_local_file(artifact_path)
    api = HfApi(token=token)
    info = api.model_info(
        EXPECTED_HUB_REPOSITORY,
        revision=EXPECTED_HUB_ARTIFACT_REVISION,
        files_metadata=True,
    )
    hub = extract_hub_file_metadata(info)
    try:
        client_version = importlib.metadata.version("huggingface_hub")
    except importlib.metadata.PackageNotFoundError:
        fail("could not resolve huggingface_hub client version")
    evidence = build_evidence(
        local,
        hub,
        authenticated_user=_authenticated_user(api),
        huggingface_hub_version=client_version,
    )
    validate_schema_document()
    validate_evidence(evidence)
    atomic_write_json(output_path, evidence)
    return evidence


def check_evidence(
    evidence_path: Path,
    artifact_path: Path | None = None,
) -> dict[str, Any]:
    try:
        value = json.loads(evidence_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"could not read evidence: {error}")
    if not isinstance(value, dict):
        fail("evidence root must be an object")
    validate_schema_document()
    validate_evidence(value)
    if artifact_path is not None:
        actual = inspect_local_file(artifact_path)
        recorded = value["localCandidate"]
        if (
            actual.filename != recorded["filename"]
            or actual.size_bytes != recorded["artifactSizeBytes"]
        ):
            fail("checked artifact identity differs from evidence")
        if normalize_sha256(actual.sha256) != normalize_sha256(
            recorded["artifactSha256"]
        ):
            fail("checked artifact sha256 differs from evidence")
    return value


def self_test() -> int:
    validate_schema_document()
    local = LocalFileMetadata(
        EXPECTED_LOCAL_FILENAME,
        EXPECTED_SIZE_BYTES,
        EXPECTED_SHA256,
    )
    hub = HubFileMetadata(
        EXPECTED_HUB_REPOSITORY,
        EXPECTED_HUB_ARTIFACT_REVISION,
        EXPECTED_HUB_ARTIFACT_REVISION,
        EXPECTED_HUB_ARTIFACT_FILENAME,
        EXPECTED_SIZE_BYTES,
        EXPECTED_SHA256,
        True,
        EXPECTED_LICENSE_ID,
    )
    evidence = build_evidence(
        local,
        hub,
        authenticated_user="self-test",
        huggingface_hub_version="0.0-self-test",
        captured_at_utc="2026-07-22T00:00:00Z",
    )
    validate_evidence(evidence)
    forged = json.loads(json.dumps(evidence))
    forged["promotion"]["promotionAllowed"] = True
    try:
        validate_evidence(forged)
    except ProvenanceError:
        pass
    else:
        fail("self-test accepted forged promotion")
    print("MORIMIL HUGGING FACE PROVENANCE SELF-TEST: PASS")
    return 0


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    cap = sub.add_parser("capture")
    cap.add_argument("artifact", type=Path)
    cap.add_argument("--output", type=Path, required=True)
    chk = sub.add_parser("check")
    chk.add_argument("evidence", type=Path)
    chk.add_argument("--artifact", type=Path)
    sub.add_parser("self-test")
    return parser


def main(argv: Iterable[str] | None = None) -> int:
    args = _parser().parse_args(
        list(argv) if argv is not None else None
    )
    try:
        if args.command == "capture":
            evidence = capture_live(args.artifact, args.output)
            print("HUGGING FACE PROVENANCE CAPTURE: PASS")
            print(
                json.dumps(
                    evidence,
                    ensure_ascii=False,
                    indent=2,
                    sort_keys=True,
                )
            )
            return 0
        if args.command == "check":
            check_evidence(args.evidence, args.artifact)
            print("HUGGING FACE PROVENANCE CHECK: PASS")
            return 0
        if args.command == "self-test":
            return self_test()
        fail(f"unsupported command: {args.command}")
    except ProvenanceError as error:
        print(f"STOPPED: {error}", file=sys.stderr)
        return 2
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
