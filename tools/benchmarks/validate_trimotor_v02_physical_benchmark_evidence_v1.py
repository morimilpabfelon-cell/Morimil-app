"""Fail-closed validator for Morimil trimotor v0.2 physical evidence v1."""
from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
import zipfile
from pathlib import Path
from typing import Any

SCHEMA_VERSION = "morimil.trimotor.v0.2.physical-benchmark-evidence.v1"
SOURCE_MAIN_COMMIT = "79eb5e31fe11611901048e80803c5c284f58e5cc"
RUN_ID = "morimil-trimotor-v0.2-physical-20260722-043653-908d91aa"
BASELINE_RUN_ID = "morimil-v0.2-physical-20260721-192940-2e071af0"
MANIFEST_PATH = "docs/model-artifacts/morimil-trimotor-v0.2-physical-benchmark-evidence-v1.json"
MANIFEST_DIGEST = "sha256:ae370beca419ff5bcd09d0d8fad7c107c291cb924fa0a85937d1337a4f7ad9aa"
ARCHIVE_SHA256 = "sha256:799d29f130dda25981941c28a702f46c5be9a998fdb36cbc9c427532a9b3b8f1"
BASE64_SHA256 = "sha256:7fed0bb02cf5378931cb1f160fb579ce2a7690095719d939bae0322df68ba7be"
EVIDENCE_ROOT = "docs/research/evidence/morimil-trimotor-v0.2-physical-20260722-043653-908d91aa"
PARTS = [
    ("part-0001.txt", 4001, "126b45954343a2b7145c9a602f2c4839b8903e1f8afbd21c2a298e272c2c91cb"),
    ("part-0002.txt", 4001, "eaa71c996fcaf117f80020184878583afc2d19189af4d64052d630f712131172"),
    ("part-0003.txt", 4001, "b170807f820015dff9d96a1f6b80442229614b8f7c7e5aea951e33545d5d5944"),
    ("part-0004.txt", 4001, "305cec73fe204e0c6b054665f1898b313bf079c4cd206167443e7f967a7061ed"),
    ("part-0005.txt", 4001, "f15cd34d9f512205ef91e9db4cebf38858c89f9573455a1794398545a1851352"),
    ("part-0006.txt", 4001, "79ce7e5229f606d32c06d8db1bd521a522894f8ac98dc6e4c3f44031e067662e"),
    ("part-0007.txt", 1897, "470324706267e862ea9435271f93d3be626903637bbcefa9a7d2c82a767df744"),
]
CONTENTS = {
    "baseline-report-v0.2.json": (43935, "2a371f906cebd74a05a50883563b665a5b99fac9864c44c794bb245b73304a12"),
    "comparison-v0.2.json": (520, "5b850d760da8da950448e4ef932a39f8b41d5e6e6a11415cecd2f83446a8251f"),
    "dataset-v0.json": (85714, "f5531706637d2358ca7da9181ab3bd4ccebed634a774c66ebb538bce5cf651fc"),
    "instrumentation-output.txt": (1645, "dcadc8dcb0612f0d4bfe887b834b7b8104a21518077befdc2bf2d20ece7ca150"),
    "physical-execution-trimotor-v0.2.json": (53428, "5d6a1f11fbb04990aa280b801d4d02b29bf5ae2db0d52c51e6729064b4eac8fb"),
    "report-trimotor-v0.2.json": (43371, "a7800a7992c99f0ab1294ea7892348413e9de1f8d4fd61056d898acebbf5393a"),
    "responses-trimotor-v0.2.jsonl": (204065, "7461727a8e78aa54d98a180ce1916af81a55ee5d01bb368836f4c1a21b15187b"),
}
COUNTS = {
    "abstainedCount": 48, "acceptedCorrectCount": 72, "acceptedCount": 72,
    "capabilityBoundaryPassCount": 120, "claimVerificationCaseCount": 12,
    "claimVerificationPassCount": 12, "correctAbstentionCount": 12,
    "falseAcceptedCount": 0, "instructionCaseCount": 120,
    "instructionComplianceCount": 84, "stateReleasePassCount": 120,
    "strictFormatCaseCount": 24, "strictFormatPassCount": 24,
    "unnecessaryAbstentionCount": 36,
}
ROLE_COUNTS = {"INTUITIVE": 72, "DELIBERATIVE": 48, "METACOGNITIVE": 72}
AUTHORITY_COUNTS = {"ACCEPTED_DETERMINISTIC": 72, "ABSTAINED": 48}
BLOCKERS = [
    "SOURCE_MODEL_REVISION_MISSING", "REPRODUCIBLE_CONVERSION_EVIDENCE_MISSING",
    "CERTIFICATION_MISSING", "SIGNATURE_MISSING", "INSTALLATION_AUTHORIZATION_MISSING",
    "PERSONAL_RUNTIME_ACTIVATION_AUTHORIZATION_MISSING",
]


def root() -> Path:
    return Path(__file__).resolve().parents[2]


def manifest_path() -> Path:
    return root() / MANIFEST_PATH


def sha(data: bytes) -> str:
    return "sha256:" + hashlib.sha256(data).hexdigest()


def canonical(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode()


def load_json(data: bytes, label: str) -> dict[str, Any]:
    value = json.loads(data.decode())
    if not isinstance(value, dict):
        raise ValueError(f"{label} must be an object")
    return value


def load_manifest(path: Path | None = None) -> dict[str, Any]:
    return load_json((path or manifest_path()).read_bytes(), "manifest")


def decode_archive(manifest: dict[str, Any], repo: Path | None = None) -> dict[str, bytes]:
    repo = repo or root()
    stored = manifest.get("storage", {})
    recorded_parts = stored.get("archiveBase64Parts", {}).get("parts")
    expected_paths = [f"{EVIDENCE_ROOT}/archive-base64/{name}" for name, _, _ in PARTS]
    if [item.get("path") for item in recorded_parts or []] != expected_paths:
        raise ValueError("manifest Base64 part set mismatch")
    encoded_parts = []
    for (name, size, digest), record in zip(PARTS, recorded_parts, strict=True):
        path = repo / EVIDENCE_ROOT / "archive-base64" / name
        raw = path.read_bytes()
        if len(raw) != size or sha(raw) != f"sha256:{digest}":
            raise ValueError(f"Base64 part mismatch: {name}")
        if record.get("sizeBytes") != size or record.get("sha256") != f"sha256:{digest}":
            raise ValueError(f"manifest Base64 metadata mismatch: {name}")
        encoded_parts.append(b"".join(raw.split()))
    encoded = b"".join(encoded_parts)
    if len(encoded) != 25896 or sha(encoded) != BASE64_SHA256:
        raise ValueError("concatenated Base64 mismatch")
    archive = base64.b64decode(encoded, validate=True)
    if len(archive) != 19421 or sha(archive) != ARCHIVE_SHA256:
        raise ValueError("decoded ZIP mismatch")
    decoded = stored.get("decodedArchive", {})
    if decoded.get("sizeBytes") != 19421 or decoded.get("sha256") != ARCHIVE_SHA256:
        raise ValueError("manifest ZIP metadata mismatch")
    with zipfile.ZipFile(io.BytesIO(archive)) as handle:
        if set(handle.namelist()) != set(CONTENTS):
            raise ValueError("ZIP file set mismatch")
        entries = {name: handle.read(name) for name in handle.namelist()}
    for name, (size, digest) in CONTENTS.items():
        if len(entries[name]) != size or sha(entries[name]) != f"sha256:{digest}":
            raise ValueError(f"ZIP content mismatch: {name}")
        record = stored.get("archiveContents", {}).get(name, {})
        if record.get("sizeBytes") != size or record.get("sha256") != f"sha256:{digest}":
            raise ValueError(f"manifest content metadata mismatch: {name}")
    for key, filename in (("bundle", "bundle-trimotor-v0.2.json"), ("comparison", "comparison-v0.2.json"), ("transcript", "instrumentation-output.txt")):
        record = stored.get("convenienceFiles", {}).get(key, {})
        direct = (repo / record.get("path", "")).read_bytes()
        archive_name = "comparison-v0.2.json" if key == "comparison" else "instrumentation-output.txt" if key == "transcript" else None
        expected = entries[archive_name] if archive_name else direct
        if key != "bundle" and direct != expected:
            raise ValueError(f"convenience file mismatch: {key}")
    return entries


def ids(values: list[dict[str, Any]]) -> set[str]:
    result = [item.get("caseId") for item in values]
    if len(result) != len(set(result)) or any(not isinstance(item, str) for item in result):
        raise ValueError("invalid or duplicate caseId")
    return set(result)


def validate_manifest(value: dict[str, Any], repo: Path | None = None) -> None:
    repo = repo or root()
    if value.get("schemaVersion") != SCHEMA_VERSION or value.get("sourceMainCommit") != SOURCE_MAIN_COMMIT:
        raise ValueError("evidence identity mismatch")
    if value.get("runId") != RUN_ID or value.get("status") != "research-only":
        raise ValueError("run identity or status mismatch")
    if sha(canonical(value)) != MANIFEST_DIGEST:
        raise ValueError("immutable evidence manifest digest mismatch")
    entries = decode_archive(value, repo)
    dataset = load_json(entries["dataset-v0.json"], "dataset")
    report = load_json(entries["report-trimotor-v0.2.json"], "report")
    physical = load_json(entries["physical-execution-trimotor-v0.2.json"], "physical")
    comparison = load_json(entries["comparison-v0.2.json"], "comparison")
    bundle = load_json((repo / EVIDENCE_ROOT / "bundle-trimotor-v0.2.json").read_bytes(), "bundle")
    responses = [json.loads(line) for line in entries["responses-trimotor-v0.2.jsonl"].decode().splitlines() if line]
    cases, per_case, telemetry = dataset["cases"], report["perCase"], physical["caseTelemetry"]
    if not (len(cases) == len(responses) == len(per_case) == len(telemetry) == 120):
        raise ValueError("evidence must contain exactly 120 cases")
    if not (ids(cases) == ids(responses) == ids(per_case) == ids(telemetry)):
        raise ValueError("caseId sets differ")
    if report.get("counts") != COUNTS or report.get("researchGatePassed") is not True:
        raise ValueError("research gate or counts mismatch")
    if report.get("productionPromotionAllowed") is not False:
        raise ValueError("report cannot authorize production")
    if comparison != {
        "baselineRunId": BASELINE_RUN_ID,
        "benchmarkVersion": report["benchmarkVersion"],
        "candidateRunId": RUN_ID,
        "comparisonVersion": "morimil.deliberative.loop-effort.benchmark.comparison.v0",
        "datasetSha256": report["datasetSha256"], "outcome": "V0_3_SUPERIOR",
        "productionPromotionAllowed": False, "reasons": [], "status": "research-only",
    }:
        raise ValueError("comparison evidence mismatch")
    if physical.get("status") != "passed" or physical.get("completedCaseCount") != 120:
        raise ValueError("physical execution did not pass")
    if physical.get("roleActivationCounts") != ROLE_COUNTS or physical.get("authorityStatusCounts") != AUTHORITY_COUNTS:
        raise ValueError("role or authority counts mismatch")
    if physical.get("openedConversationCount") != 48 or physical.get("closedConversationCount") != 48:
        raise ValueError("conversation lifecycle mismatch")
    for key in ("hashStable", "engineInitialized", "engineClosed", "allConversationsClosed", "allRequestStatesReleased"):
        if physical.get(key) is not True:
            raise ValueError(f"physical invariant failed: {key}")
    for key in ("memoryWriteCapability", "identityAuthority", "lifecycleAuthority", "normalRuntimeActivated", "productionAuthorization", "promotionAllowed"):
        if physical.get(key) is not False:
            raise ValueError(f"physical boundary violated: {key}")
    for record in responses:
        if record.get("requestStateReleased") is not True:
            raise ValueError("response state was not released")
        if any(record.get(key) is not False for key in ("memoryWriteCapability", "identityAuthority", "lifecycleAuthority", "normalRuntimeActivated")):
            raise ValueError("response boundary violated")
    benchmark = value.get("benchmark", {})
    if benchmark.get("runStatus") != "COMPLETED_PASSED_RESEARCH_GATE" or benchmark.get("qualityGatePassed") is not True or benchmark.get("counts") != COUNTS:
        raise ValueError("manifest benchmark mismatch")
    if value.get("execution", {}).get("roleActivationCounts") != ROLE_COUNTS:
        raise ValueError("manifest execution mismatch")
    semantics = str(value.get("comparison", {}).get("outcomeSemantics", ""))
    if value.get("comparison", {}).get("outcome") != "V0_3_SUPERIOR" or "does not identify" not in semantics:
        raise ValueError("legacy comparator label is not clarified")
    if bundle.get("benchmark", {}).get("falseAcceptedCount") != 0 or bundle.get("benchmark", {}).get("researchGatePassed") is not True:
        raise ValueError("bundle benchmark mismatch")
    for key in ("normalRuntimeActivated", "personalRuntimeActivationAuthorized", "productionAuthorization", "productionPromotionAllowed"):
        if bundle.get(key) is not False:
            raise ValueError("bundle cannot authorize activation")
    promotion = value.get("promotion", {})
    if promotion.get("blockers") != BLOCKERS:
        raise ValueError("activation blockers mismatch")
    for key in ("reproducibleConversionEvidence", "certified", "signed", "installed", "normalRuntimeActivated", "personalRuntimeActivationAuthorized", "productionAuthorization", "productionPromotionAllowed"):
        if promotion.get(key) is not False:
            raise ValueError("evidence cannot authorize activation")
    boundary = value.get("authorityBoundary", {})
    if boundary.get("intrinsicMotorsOwnedByMorimil") is not True or boundary.get("requestStateReleased") is not True:
        raise ValueError("Morimil ownership or state boundary missing")
    if any(boundary.get(key) is not False for key in ("externalMotorMayBecomeIdentity", "memoryWriteCapability", "identityAuthority", "lifecycleAuthority")):
        raise ValueError("Morimil authority boundary violated")
    if value.get("artifact", {}).get("sourceModelRevision") is not None:
        raise ValueError("source model revision must remain unknown")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("check", "print-digest"), nargs="?", default="check")
    parser.add_argument("--manifest", type=Path, default=manifest_path())
    args = parser.parse_args()
    manifest = load_manifest(args.manifest)
    validate_manifest(manifest)
    if args.command == "print-digest":
        print(sha(canonical(manifest)))
    else:
        print("Morimil trimotor v0.2 physical benchmark evidence v1: VALID")
        print("Physical infrastructure: PASSED")
        print("Cases completed: 120/120")
        print("Research gate: PASSED (0 false acceptances)")
        print("Comparison label: V0_3_SUPERIOR (legacy label, no trained v0.3 claim)")
        print("Personal runtime activation: BLOCKED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
