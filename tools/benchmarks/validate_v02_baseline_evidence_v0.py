"""Fail-closed validator for the immutable Morimil v0.2 baseline snapshot."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

SCHEMA_VERSION = "morimil.deliberative.v0.2.baseline-evidence.v0"
SNAPSHOT_COMMIT = "5071f399703273a91ad280ebc43fbce8eb2c1ff7"
EXPECTED_DIGEST = "sha256:a80e074e0a4fbf5e63d2c9e5d96d575c432a12788e559717154a53e724ecb404"
MANIFEST_PATH = (
    "docs/model-artifacts/"
    "morimil-deliberative-v0.2-baseline-evidence-v0.json"
)
EXPECTED_BLOCKERS = [
    "SOURCE_MODEL_REVISION_MISSING",
    "REPRODUCIBLE_CONVERSION_EVIDENCE_MISSING",
    "CERTIFICATION_MISSING",
    "SIGNATURE_MISSING",
    "INSTALLATION_AUTHORIZATION_MISSING",
    "PRODUCTION_AUTHORIZATION_MISSING",
]


def canonical_bytes(value: Any) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        + "\n"
    ).encode("utf-8")


def digest(value: Any) -> str:
    return "sha256:" + hashlib.sha256(canonical_bytes(value)).hexdigest()


def repository_root() -> Path:
    return Path(__file__).resolve().parents[2]


def default_manifest_path() -> Path:
    return repository_root() / MANIFEST_PATH


def load_manifest(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError("baseline manifest must be a JSON object")
    return value


def validate_manifest(value: dict[str, Any]) -> None:
    if value.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError("schema version mismatch")
    if value.get("status") != "research-only":
        raise ValueError("baseline must remain research-only")
    if value.get("snapshotKind") != "repository-summary":
        raise ValueError("snapshot kind mismatch")
    if value.get("snapshotCommit") != SNAPSHOT_COMMIT:
        raise ValueError("snapshot commit mismatch")

    artifact = value.get("artifact", {})
    if artifact.get("sourceModelRevision") is not None:
        raise ValueError("source model revision must remain unknown")
    if artifact.get("upstreamRepositoryRevision") == artifact.get("sourceModelRevision"):
        raise ValueError("artifact repository revision is not source-model revision")

    physical = value.get("physicalEvidence", {})
    if physical.get("summaryStatus") != "ACCEPTED_SUMMARY_RAW_REPORT_NOT_VERSIONED":
        raise ValueError("physical evidence summary status mismatch")
    if physical.get("rawReportVersioned") is not False:
        raise ValueError("raw reports are not versioned")
    if physical.get("rawReportSha256") is not None:
        raise ValueError("missing raw reports cannot have a digest")

    benchmark = value.get("benchmark", {})
    if benchmark.get("runStatus") != "NOT_EXECUTED":
        raise ValueError("v0.2 smoke benchmark has not been executed")
    if any(
        benchmark.get(key) is not None
        for key in ("responseFileSha256", "reportFileSha256", "report")
    ):
        raise ValueError("unexecuted benchmark cannot contain results")

    promotion = value.get("promotion", {})
    if promotion.get("blockers") != EXPECTED_BLOCKERS:
        raise ValueError("promotion blockers mismatch")
    forbidden_promotion = (
        "reproducibleConversionEvidence",
        "certified",
        "signed",
        "installed",
        "normalRuntimeActivated",
        "productionAuthorization",
        "productionPromotionAllowed",
    )
    if any(promotion.get(key) is not False for key in forbidden_promotion):
        raise ValueError("baseline cannot authorize production")

    boundary = value.get("authorityBoundary", {})
    if boundary.get("intrinsicMotorOwnedByMorimil") is not True:
        raise ValueError("intrinsic motor ownership missing")
    if boundary.get("requestScopedStateRequired") is not True:
        raise ValueError("request-scoped state boundary missing")
    for key in (
        "externalMotorMayBecomeIdentity",
        "memoryWriteCapability",
        "identityAuthority",
        "lifecycleAuthority",
    ):
        if boundary.get(key) is not False:
            raise ValueError("Morimil identity or memory boundary violated")

    references = value.get("sourceReferences")
    if not isinstance(references, list) or len(references) < 6:
        raise ValueError("source references incomplete")
    if any(reference.get("commit") != SNAPSHOT_COMMIT for reference in references):
        raise ValueError("source reference commit mismatch")
    paths = [reference.get("path") for reference in references]
    if len(paths) != len(set(paths)) or any(not path for path in paths):
        raise ValueError("source reference paths invalid")

    limitations = value.get("limitations")
    if not isinstance(limitations, list) or len(limitations) < 5:
        raise ValueError("limitations incomplete")

    actual_digest = digest(value)
    if actual_digest != EXPECTED_DIGEST:
        raise ValueError(f"immutable baseline digest mismatch: {actual_digest}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "command",
        choices=("check", "print-digest"),
        nargs="?",
        default="check",
    )
    parser.add_argument("--manifest", type=Path, default=default_manifest_path())
    args = parser.parse_args()
    value = load_manifest(args.manifest)
    validate_manifest(value)
    if args.command == "print-digest":
        print(digest(value))
    else:
        print("Morimil v0.2 baseline evidence snapshot: VALID")
        print("Physical evidence: accepted summary; raw reports not versioned")
        print("120-case smoke benchmark: NOT_EXECUTED")
        print("Production promotion: BLOCKED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
