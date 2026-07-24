"""Fail-closed validator for Morimil current 84/36 physical evidence v2."""
from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Any

SCHEMA_VERSION = "morimil.trimotor.v0.2.physical-benchmark-evidence.v2"
SOURCE_MAIN_COMMIT = "6dc018a610c8f2a7ca5bf76748b6d639044c6c4d"
RUN_ID = "morimil-trimotor-v0.2-physical-20260723-203336-1d3baa47"
MANIFEST_PATH = "docs/model-artifacts/morimil-trimotor-v0.2-physical-benchmark-evidence-v2.json"
MANIFEST_DIGEST = "sha256:caf68142bb7808ed0d73331405f32c0a1539b15781f753988c3dfc5bfa8abed4"
ARCHIVE_SHA256 = "sha256:7385d5156bb095067a99cbd338e1c009273e1926f476a6754a4dec6e90b2f2a4"
BASE64_SHA256 = "sha256:99b2519023b48a5a7f442aa409d9b9e3fb032346ff9192dc1f4538919cb6d0d3"
EVIDENCE_ROOT = f"docs/research/evidence/{RUN_ID}"
EXPECTED_COUNTS = {
    "abstainedCount": 36,
    "acceptedCorrectCount": 84,
    "acceptedCount": 84,
    "capabilityBoundaryPassCount": 120,
    "claimVerificationCaseCount": 12,
    "claimVerificationPassCount": 12,
    "correctAbstentionCount": 12,
    "falseAcceptedCount": 0,
    "instructionCaseCount": 120,
    "instructionComplianceCount": 96,
    "stateReleasePassCount": 120,
    "strictFormatCaseCount": 24,
    "strictFormatPassCount": 24,
    "unnecessaryAbstentionCount": 24,
}
EXPECTED_ROLE_COUNTS = {"INTUITIVE": 84, "DELIBERATIVE": 36, "METACOGNITIVE": 84}
EXPECTED_AUTHORITY_COUNTS = {"ACCEPTED_DETERMINISTIC": 84, "ABSTAINED": 36}
EXPECTED_BLOCKERS = [
    "SOURCE_MODEL_REVISION_MISSING",
    "REPRODUCIBLE_CONVERSION_EVIDENCE_MISSING",
    "CERTIFICATION_MISSING",
    "SIGNATURE_MISSING",
    "INSTALLATION_AUTHORIZATION_MISSING",
    "PERSONAL_RUNTIME_ACTIVATION_AUTHORIZATION_MISSING",
    "PRODUCTION_AUTHORIZATION_MISSING",
]
EXPECTED_CONTENTS = {
    "bundle-trimotor-v0.2.json": (3752, "5dab9913ed6f34a48c506eeae42f484bd0e5d2938f8d971d801f42635e1e14a7"),
    "comparison-v0.2.json": (520, "38efab5211295738d59d6feec83a21c2d61d1efdf4b2c4789fa42a52186a625e"),
    "dataset-v0.json": (85714, "f5531706637d2358ca7da9181ab3bd4ccebed634a774c66ebb538bce5cf651fc"),
    "instrumentation-output.txt": (1646, "43350ade3d197bb79f2ab8667e154cae24a04a45ddbe9e2297c34c7b5c763326"),
    "physical-execution-trimotor-v0.2.json": (53227, "edd115c4f1c6de3733686274610150a01b777439812aec95b83165e2460a20a6"),
    "report-trimotor-v0.2.json": (43194, "9cbe8102ac4acacc77acc22bbc5af60b5807935778ad52523d05b4a5798353f4"),
    "report-v0.2.json": (43935, "2a371f906cebd74a05a50883563b665a5b99fac9864c44c794bb245b73304a12"),
    "responses-trimotor-v0.2.jsonl": (212382, "9f418e70698567e5296ab1056e4004bc11f0d91309550a90335a0dd45ea585f7"),
}


def root() -> Path:
    return Path(__file__).resolve().parents[2]


def default_manifest_path() -> Path:
    return root() / MANIFEST_PATH


def sha(data: bytes) -> str:
    return "sha256:" + hashlib.sha256(data).hexdigest()


def canonical(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode()


def load_object(data: bytes, label: str) -> dict[str, Any]:
    value = json.loads(data.decode("utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{label} must be an object")
    return value


def load_manifest(path: Path | None = None) -> dict[str, Any]:
    raw = (path or default_manifest_path()).read_bytes()
    if sha(raw) != MANIFEST_DIGEST:
        raise ValueError("current 84/36 evidence manifest digest mismatch")
    value = load_object(raw, "manifest")
    if canonical(value) != raw:
        raise ValueError("current 84/36 evidence manifest is not canonical")
    return value


def decode_archive(manifest: dict[str, Any], repo: Path | None = None) -> dict[str, bytes]:
    repo = repo or root()
    storage = manifest.get("storage", {})
    parts = storage.get("archiveBase64Parts", {}).get("parts")
    if not isinstance(parts, list) or len(parts) != 8:
        raise ValueError("current 84/36 Base64 part set mismatch")
    encoded_parts: list[bytes] = []
    for index, record in enumerate(parts, 1):
        expected_path = f"{EVIDENCE_ROOT}/archive-base64/part-{index:04d}.txt"
        if record.get("path") != expected_path:
            raise ValueError("current 84/36 Base64 part path mismatch")
        raw = (repo / expected_path).read_bytes()
        if len(raw) != record.get("sizeBytes") or sha(raw) != record.get("sha256"):
            raise ValueError(f"current 84/36 Base64 part mismatch: part-{index:04d}.txt")
        encoded_parts.append(raw)
    encoded = b"".join(encoded_parts)
    metadata = storage.get("archiveBase64Parts", {})
    if len(encoded) != metadata.get("concatenatedBase64SizeBytes") or sha(encoded) != BASE64_SHA256:
        raise ValueError("current 84/36 concatenated Base64 mismatch")
    archive = base64.b64decode(encoded, validate=True)
    decoded = storage.get("decodedArchive", {})
    if len(archive) != decoded.get("sizeBytes") or sha(archive) != ARCHIVE_SHA256:
        raise ValueError("current 84/36 decoded ZIP mismatch")
    with zipfile.ZipFile(io.BytesIO(archive)) as handle:
        if set(handle.namelist()) != set(EXPECTED_CONTENTS):
            raise ValueError("current 84/36 ZIP file set mismatch")
        entries = {name: handle.read(name) for name in handle.namelist()}
    archive_contents = storage.get("archiveContents", {})
    for name, (size, digest) in EXPECTED_CONTENTS.items():
        expected = {"sizeBytes": size, "sha256": f"sha256:{digest}"}
        if archive_contents.get(name) != expected:
            raise ValueError(f"current 84/36 manifest content metadata mismatch: {name}")
        if len(entries[name]) != size or sha(entries[name]) != expected["sha256"]:
            raise ValueError(f"current 84/36 ZIP content mismatch: {name}")
    for key, filename in {
        "bundle": "bundle-trimotor-v0.2.json",
        "comparison": "comparison-v0.2.json",
        "transcript": "instrumentation-output.txt",
    }.items():
        record = storage.get("convenienceFiles", {}).get(key, {})
        path = repo / str(record.get("path", ""))
        direct = path.read_bytes()
        if len(direct) != record.get("sizeBytes") or sha(direct) != record.get("sha256"):
            raise ValueError(f"current 84/36 convenience file mismatch: {key}")
        if direct != entries[filename]:
            raise ValueError(f"current 84/36 convenience/archive mismatch: {key}")
    return entries


def validate_manifest(value: dict[str, Any], repo: Path | None = None) -> None:
    repo = repo or root()
    if value.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError("current 84/36 evidence schema mismatch")
    if value.get("sourceMainCommit") != SOURCE_MAIN_COMMIT:
        raise ValueError("current 84/36 source commit mismatch")
    if value.get("runId") != RUN_ID or value.get("status") != "research-only":
        raise ValueError("current 84/36 run identity mismatch")
    if value.get("sourceArchive") != {
        "filename": "morimil-trimotor-84-36-physical-evidence.zip",
        "sha256": ARCHIVE_SHA256,
        "sizeBytes": 21053,
    }:
        raise ValueError("current 84/36 source archive identity mismatch")

    entries = decode_archive(value, repo)
    tools = repo / "tools/android-arm64"
    if str(tools) not in sys.path:
        sys.path.insert(0, str(tools))
    from current_trimotor_physical_evidence_v0 import validate_run_directory

    with tempfile.TemporaryDirectory() as temporary:
        run_dir = Path(temporary) / RUN_ID
        run_dir.mkdir()
        for name, raw in entries.items():
            (run_dir / name).write_bytes(raw)
        validate_run_directory(run_dir)

    report = load_object(entries["report-trimotor-v0.2.json"], "report")
    physical = load_object(entries["physical-execution-trimotor-v0.2.json"], "physical")
    bundle = load_object(entries["bundle-trimotor-v0.2.json"], "bundle")
    comparison = load_object(entries["comparison-v0.2.json"], "comparison")
    if report.get("counts") != EXPECTED_COUNTS or report.get("researchGatePassed") is not True:
        raise ValueError("current 84/36 evaluator gate mismatch")
    if report.get("productionPromotionAllowed") is not False:
        raise ValueError("current 84/36 report cannot authorize production")
    if physical.get("status") != "passed" or physical.get("completedCaseCount") != 120:
        raise ValueError("current 84/36 physical execution did not pass")
    if physical.get("roleActivationCounts") != EXPECTED_ROLE_COUNTS:
        raise ValueError("current 84/36 role counts mismatch")
    if physical.get("authorityStatusCounts") != EXPECTED_AUTHORITY_COUNTS:
        raise ValueError("current 84/36 authority counts mismatch")
    if physical.get("openedConversationCount") != 36 or physical.get("closedConversationCount") != 36:
        raise ValueError("current 84/36 conversation lifecycle mismatch")
    for field in ("hashStable", "engineInitialized", "engineClosed", "allConversationsClosed", "allRequestStatesReleased"):
        if physical.get(field) is not True:
            raise ValueError(f"current 84/36 invariant failed: {field}")
    for field in ("memoryWriteCapability", "identityAuthority", "lifecycleAuthority", "normalRuntimeActivated", "productionAuthorization", "promotionAllowed"):
        if physical.get(field) is not False:
            raise ValueError(f"current 84/36 authority boundary violated: {field}")
    if physical.get("sourceModelRevision") is not None:
        raise ValueError("current 84/36 evidence invents source-model provenance")
    if comparison.get("outcome") != "V0_3_SUPERIOR" or comparison.get("productionPromotionAllowed") is not False:
        raise ValueError("current 84/36 comparison mismatch")
    if bundle.get("benchmark", {}).get("acceptedCorrectCount") != 84 or bundle.get("benchmark", {}).get("abstainedCount") != 36:
        raise ValueError("current 84/36 bundle counts mismatch")
    for field in ("certified", "signed", "installed", "normalRuntimeActivated", "personalRuntimeActivationAuthorized", "productionAuthorization", "productionPromotionAllowed"):
        if bundle.get(field) is not False:
            raise ValueError(f"current 84/36 bundle forbidden flag enabled: {field}")

    promotion = value.get("promotion", {})
    if promotion.get("blockers") != EXPECTED_BLOCKERS:
        raise ValueError("current 84/36 promotion blocker mismatch")
    for field in ("reproducibleConversionEvidence", "certified", "signed", "installed", "normalRuntimeActivated", "personalRuntimeActivationAuthorized", "productionAuthorization", "productionPromotionAllowed"):
        if promotion.get(field) is not False:
            raise ValueError(f"current 84/36 manifest forbidden promotion flag enabled: {field}")
    boundary = value.get("authorityBoundary", {})
    if boundary.get("hybridAuthorityRequiredForThisResult") is not True:
        raise ValueError("current 84/36 hybrid-authority limitation missing")
    if boundary.get("intrinsicMotorsOwnedByMorimil") is not True or boundary.get("requestStateReleased") is not True:
        raise ValueError("current 84/36 ownership/state boundary missing")
    for field in ("externalMotorMayBecomeIdentity", "memoryWriteCapability", "identityAuthority", "lifecycleAuthority"):
        if boundary.get(field) is not False:
            raise ValueError(f"current 84/36 manifest authority boundary violated: {field}")


def check(path: Path | None = None) -> int:
    manifest = load_manifest(path)
    validate_manifest(manifest, (path or default_manifest_path()).resolve().parents[2])
    print("MORIMIL CURRENT 84/36 PHYSICAL EVIDENCE V2: PASS")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["check"])
    parser.add_argument("--manifest", type=Path)
    args = parser.parse_args()
    return check(args.manifest)


if __name__ == "__main__":
    raise SystemExit(main())
