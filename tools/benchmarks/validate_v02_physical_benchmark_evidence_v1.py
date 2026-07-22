"""Fail-closed validator for Morimil v0.2 physical benchmark evidence v1."""
from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
import zipfile
from pathlib import Path
from typing import Any

SCHEMA_VERSION = "morimil.deliberative.v0.2.physical-benchmark-evidence.v1"
SOURCE_MAIN_COMMIT = "b9cdfa0371f6520528090655c4eb795a1aae70d9"
RUN_ID = "morimil-v0.2-physical-20260721-192940-2e071af0"
EXPECTED_MANIFEST_DIGEST = "sha256:ca11d9d77ae86aac31374ff72855d36ce640561a437d7afb7b10575417089f09"
MANIFEST_PATH = (
    "docs/model-artifacts/"
    "morimil-deliberative-v0.2-physical-benchmark-evidence-v1.json"
)
EXPECTED_ARCHIVE_SHA256 = (
    "sha256:d8c835c29fb79a1d9c49966977771f11da3d93d155a95fa4653868db5baf4ef9"
)
EXPECTED_BASE64_CONCAT_SHA256 = (
    "sha256:5105b05f29aa0e2ab2d2fa1f9cb18b4925982d966296821fbe20e1006e37419f"
)
EXPECTED_BASE64_PARTS = [
    {
        "path": "docs/research/evidence/morimil-v0.2-physical-20260721-192940-2e071af0/archive-base64/part-0001.txt",
        "sha256": "sha256:aab05122dc2c01b46c202cba5607460727f73e3a09f0a0188d034fe556fefb9b",
        "sizeBytes": 4001,
    },
    {
        "path": "docs/research/evidence/morimil-v0.2-physical-20260721-192940-2e071af0/archive-base64/part-0002.txt",
        "sha256": "sha256:c9d3bbd25ef8a64724981552f371607e36fad2e2ea1ec36f771b321e940e5382",
        "sizeBytes": 4001,
    },
    {
        "path": "docs/research/evidence/morimil-v0.2-physical-20260721-192940-2e071af0/archive-base64/part-0003.txt",
        "sha256": "sha256:eaa134cf5a3a4c567ffbe9816acc97b509dbe0b5cdb5affab30077035c66ca65",
        "sizeBytes": 4001,
    },
    {
        "path": "docs/research/evidence/morimil-v0.2-physical-20260721-192940-2e071af0/archive-base64/part-0004.txt",
        "sha256": "sha256:b47fcaca9323c0df661efc808e347954d610b7d1837748a0978afe28f367bb70",
        "sizeBytes": 4001,
    },
    {
        "path": "docs/research/evidence/morimil-v0.2-physical-20260721-192940-2e071af0/archive-base64/part-0005a.txt",
        "sha256": "sha256:1d62fb24c4c2be566b1bc9226afaab08d1a1d7bae5719c3555bddea21f049a08",
        "sizeBytes": 1001,
    },
    {
        "path": "docs/research/evidence/morimil-v0.2-physical-20260721-192940-2e071af0/archive-base64/part-0005b.txt",
        "sha256": "sha256:ef41fddaeca1d4d32ea9d2866f7837ba81f184e74ca18015cbe3af4ae30950ea",
        "sizeBytes": 1001,
    },
    {
        "path": "docs/research/evidence/morimil-v0.2-physical-20260721-192940-2e071af0/archive-base64/part-0005c.txt",
        "sha256": "sha256:d29f8c70f0183b410bb41af496a9d4de59d8fc9f056190d0505625c08fb7c27a",
        "sizeBytes": 805,
    },
]
EXPECTED_CONTENTS = {
    "bundle-v0.2.json": "sha256:89a1dcb398545f81592b3ec994cc0d377418c1ec3c574f27c94f08bfcfee142a",
    "dataset-v0.json": "sha256:f5531706637d2358ca7da9181ab3bd4ccebed634a774c66ebb538bce5cf651fc",
    "instrumentation-output.txt": "sha256:019857fd678cf54c2dae2aa186462a775ff7f05f4c0f012c7b0035f94ca00082",
    "physical-execution-v0.2.json": "sha256:ca856b63f0daadbb6449f144f633ac9c00e9b59ae912b7996cfd4e3324fb1d38",
    "report-v0.2.json": "sha256:2a371f906cebd74a05a50883563b665a5b99fac9864c44c794bb245b73304a12",
    "responses-v0.2.jsonl": "sha256:f5fb72210ca6b60f4093f216c4a313ebbc9f6f4a1a0bcb80b2fa9ce53b606246",
}
EXPECTED_BLOCKERS = [
    "BENCHMARK_QUALITY_GATE_FAILED",
    "SOURCE_MODEL_REVISION_MISSING",
    "REPRODUCIBLE_CONVERSION_EVIDENCE_MISSING",
    "CERTIFICATION_MISSING",
    "SIGNATURE_MISSING",
    "INSTALLATION_AUTHORIZATION_MISSING",
    "PRODUCTION_AUTHORIZATION_MISSING",
]


def repository_root() -> Path:
    return Path(__file__).resolve().parents[2]


def default_manifest_path() -> Path:
    return repository_root() / MANIFEST_PATH


def canonical_bytes(value: Any) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        + "\n"
    ).encode("utf-8")


def digest_bytes(value: bytes) -> str:
    return "sha256:" + hashlib.sha256(value).hexdigest()


def digest(value: Any) -> str:
    return digest_bytes(canonical_bytes(value))


def load_json_bytes(value: bytes, label: str) -> dict[str, Any]:
    parsed = json.loads(value.decode("utf-8"))
    if not isinstance(parsed, dict):
        raise ValueError(f"{label} must contain a JSON object")
    return parsed


def load_manifest(path: Path) -> dict[str, Any]:
    return load_json_bytes(path.read_bytes(), path.name)


def load_jsonl_bytes(value: bytes) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for number, line in enumerate(value.decode("utf-8").splitlines(), 1):
        if not line.strip():
            continue
        parsed = json.loads(line)
        if not isinstance(parsed, dict):
            raise ValueError(f"responses line {number} is not an object")
        records.append(parsed)
    return records


def unique_case_ids(values: list[dict[str, Any]], label: str) -> set[str]:
    ids = [value.get("caseId") for value in values]
    if any(not isinstance(case_id, str) or not case_id for case_id in ids):
        raise ValueError(f"{label} contains an invalid caseId")
    if len(ids) != len(set(ids)):
        raise ValueError(f"{label} contains duplicate caseIds")
    return set(ids)


def derive_domain_breakdown(per_case: list[dict[str, Any]]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for domain in sorted({str(case["domain"]) for case in per_case}):
        cases = [case for case in per_case if case["domain"] == domain]
        result.append({
            "domain": domain,
            "caseCount": len(cases),
            "acceptedCorrectCount": sum(case["acceptedCorrect"] is True for case in cases),
            "falseAcceptedCount": sum(case["falseAccepted"] is True for case in cases),
            "correctAbstentionCount": sum(case["correctAbstention"] is True for case in cases),
            "unnecessaryAbstentionCount": sum(
                case["finalDisposition"] == "ABSTAINED"
                and case["correctAbstention"] is not True
                for case in cases
            ),
            "acceptedCount": sum(case["finalDisposition"] == "ACCEPTED" for case in cases),
            "abstainedCount": sum(case["finalDisposition"] == "ABSTAINED" for case in cases),
        })
    return result


def decode_archive(manifest: dict[str, Any], root: Path) -> dict[str, bytes]:
    storage = manifest.get("storage", {})
    parts_record = storage.get("archiveBase64Parts", {})
    parts = parts_record.get("parts")
    if not isinstance(parts, list) or len(parts) != len(EXPECTED_BASE64_PARTS):
        raise ValueError("manifest Base64 part set mismatch")

    encoded_parts: list[bytes] = []
    for recorded, expected in zip(parts, EXPECTED_BASE64_PARTS, strict=True):
        if recorded.get("path") != expected["path"]:
            raise ValueError("manifest Base64 part path mismatch")
        if recorded.get("sizeBytes") != expected["sizeBytes"]:
            raise ValueError("manifest Base64 part size mismatch")
        if recorded.get("sha256") != expected["sha256"]:
            raise ValueError("manifest Base64 part digest mismatch")
        path = root / expected["path"]
        if not path.is_file():
            raise ValueError(f"versioned Base64 part is missing: {path.name}")
        raw = path.read_bytes()
        if len(raw) != expected["sizeBytes"]:
            raise ValueError(f"Base64 part size mismatch: {path.name}")
        if digest_bytes(raw) != expected["sha256"]:
            raise ValueError(f"Base64 part digest mismatch: {path.name}")
        encoded_parts.append(b"".join(raw.split()))

    encoded = b"".join(encoded_parts)
    if parts_record.get("concatenatedBase64SizeBytes") != 18804:
        raise ValueError("manifest concatenated Base64 size mismatch")
    if len(encoded) != 18804:
        raise ValueError("concatenated Base64 size mismatch")
    if parts_record.get("concatenatedBase64Sha256") != EXPECTED_BASE64_CONCAT_SHA256:
        raise ValueError("manifest concatenated Base64 digest mismatch")
    if digest_bytes(encoded) != EXPECTED_BASE64_CONCAT_SHA256:
        raise ValueError("concatenated Base64 digest mismatch")
    try:
        archive = base64.b64decode(encoded, validate=True)
    except Exception as error:
        raise ValueError("Base64 archive cannot be decoded") from error

    decoded = storage.get("decodedArchive", {})
    if len(archive) != 14102 or decoded.get("sizeBytes") != 14102:
        raise ValueError("decoded ZIP size mismatch")
    if digest_bytes(archive) != EXPECTED_ARCHIVE_SHA256:
        raise ValueError("decoded ZIP digest mismatch")
    if decoded.get("sha256") != EXPECTED_ARCHIVE_SHA256:
        raise ValueError("manifest decoded ZIP digest mismatch")

    with zipfile.ZipFile(io.BytesIO(archive)) as handle:
        if set(handle.namelist()) != set(EXPECTED_CONTENTS):
            raise ValueError("ZIP evidence file set mismatch")
        entries = {name: handle.read(name) for name in handle.namelist()}

    recorded = storage.get("archiveContents")
    if not isinstance(recorded, dict) or set(recorded) != set(EXPECTED_CONTENTS):
        raise ValueError("manifest archive content set mismatch")
    for name, expected_hash in EXPECTED_CONTENTS.items():
        if digest_bytes(entries[name]) != expected_hash:
            raise ValueError(f"ZIP evidence digest mismatch for {name}")
        if recorded[name].get("sha256") != expected_hash:
            raise ValueError(f"manifest evidence digest mismatch for {name}")
        if recorded[name].get("sizeBytes") != len(entries[name]):
            raise ValueError(f"manifest evidence size mismatch for {name}")

    convenience = storage.get("convenienceFiles", {})
    for key, name in (
        ("bundle", "bundle-v0.2.json"),
        ("transcript", "instrumentation-output.txt"),
    ):
        record = convenience.get(key, {})
        path = root / str(record.get("path", ""))
        if not path.is_file() or path.read_bytes() != entries[name]:
            raise ValueError(f"convenience file mismatch for {name}")
        if record.get("sha256") != EXPECTED_CONTENTS[name]:
            raise ValueError(f"convenience digest mismatch for {name}")
        if record.get("sizeBytes") != len(entries[name]):
            raise ValueError(f"convenience size mismatch for {name}")
    return entries


def validate_manifest(value: dict[str, Any], *, root: Path | None = None) -> None:
    root = root or repository_root()
    if value.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError("schema version mismatch")
    if value.get("status") != "research-only":
        raise ValueError("evidence must remain research-only")
    if value.get("evidenceKind") != "physical-benchmark-completed-quality-gate-failed":
        raise ValueError("evidence kind mismatch")
    if value.get("sourceMainCommit") != SOURCE_MAIN_COMMIT:
        raise ValueError("source main commit mismatch")
    if value.get("runId") != RUN_ID:
        raise ValueError("run id mismatch")

    entries = decode_archive(value, root)
    dataset = load_json_bytes(entries["dataset-v0.json"], "dataset")
    bundle = load_json_bytes(entries["bundle-v0.2.json"], "bundle")
    report = load_json_bytes(entries["report-v0.2.json"], "report")
    physical = load_json_bytes(
        entries["physical-execution-v0.2.json"], "physical report"
    )
    responses = load_jsonl_bytes(entries["responses-v0.2.jsonl"])
    transcript = entries["instrumentation-output.txt"].decode("utf-8")

    cases = dataset.get("cases")
    per_case = report.get("perCase")
    telemetry = physical.get("caseTelemetry")
    if not all(isinstance(group, list) for group in (cases, per_case, telemetry)):
        raise ValueError("case evidence lists missing")
    if not (len(cases) == len(responses) == len(per_case) == len(telemetry) == 120):
        raise ValueError("physical evidence must contain exactly 120 cases")
    dataset_ids = unique_case_ids(cases, "dataset")
    response_ids = unique_case_ids(responses, "responses")
    report_ids = unique_case_ids(per_case, "report")
    telemetry_ids = unique_case_ids(telemetry, "physical telemetry")
    if not (dataset_ids == response_ids == report_ids == telemetry_ids):
        raise ValueError("caseId sets differ across evidence files")

    if bundle.get("runId") != RUN_ID or bundle.get("status") != "COMPLETED":
        raise ValueError("bundle completion state mismatch")
    if bundle.get("benchmark", {}).get("researchGatePassed") is not False:
        raise ValueError("bundle must record failed research gate")
    if bundle.get("productionPromotionAllowed") is not False:
        raise ValueError("bundle cannot allow production")
    for key, filename in (
        ("dataset", "dataset-v0.json"),
        ("responses", "responses-v0.2.jsonl"),
        ("report", "report-v0.2.json"),
        ("physicalExecution", "physical-execution-v0.2.json"),
        ("transcript", "instrumentation-output.txt"),
    ):
        item = bundle.get("files", {}).get(key, {})
        if (
            item.get("name") != filename
            or item.get("sha256") != EXPECTED_CONTENTS[filename]
        ):
            raise ValueError(f"bundle evidence mismatch for {key}")

    expected_counts = {
        "abstainedCount": 43,
        "acceptedCorrectCount": 37,
        "acceptedCount": 77,
        "capabilityBoundaryPassCount": 120,
        "claimVerificationCaseCount": 12,
        "claimVerificationPassCount": 12,
        "correctAbstentionCount": 12,
        "falseAcceptedCount": 40,
        "instructionCaseCount": 120,
        "instructionComplianceCount": 77,
        "stateReleasePassCount": 120,
        "strictFormatCaseCount": 24,
        "strictFormatPassCount": 12,
        "unnecessaryAbstentionCount": 31,
    }
    if report.get("counts") != expected_counts:
        raise ValueError("benchmark counts mismatch")
    benchmark = value.get("benchmark", {})
    if benchmark.get("runStatus") != "COMPLETED_FAILED_QUALITY_GATE":
        raise ValueError("benchmark run status mismatch")
    if benchmark.get("qualityGatePassed") is not False:
        raise ValueError("quality gate must remain failed")
    if benchmark.get("counts") != expected_counts:
        raise ValueError("manifest benchmark counts differ from raw report")
    if benchmark.get("rates") != report.get("rates"):
        raise ValueError("manifest benchmark rates differ from raw report")
    if benchmark.get("latencyMs") != report.get("latencyMs"):
        raise ValueError("manifest benchmark latency differs from raw report")
    if benchmark.get("domainBreakdown") != derive_domain_breakdown(per_case):
        raise ValueError("domain breakdown mismatch")
    if report.get("researchGatePassed") is not False:
        raise ValueError("raw report quality gate must remain failed")
    if report.get("productionPromotionAllowed") is not False:
        raise ValueError("raw report cannot allow production")

    response_by_id = {record["caseId"]: record for record in responses}
    telemetry_by_id = {record["caseId"]: record for record in telemetry}
    for evaluation in per_case:
        case_id = evaluation["caseId"]
        response = response_by_id[case_id]
        sample = telemetry_by_id[case_id]
        for key in (
            "finalDisposition",
            "latencyMs",
            "stateKind",
            "completedIterations",
            "stopReason",
        ):
            if evaluation.get(key) != response.get(key):
                raise ValueError(f"response/report mismatch for {case_id}: {key}")
        if sample.get("latencyMilliseconds") != response.get("latencyMs"):
            raise ValueError(f"response/telemetry latency mismatch for {case_id}")
        if sample.get("conversationClosed") is not True:
            raise ValueError(f"conversation was not closed for {case_id}")
        if response.get("requestStateReleased") is not True:
            raise ValueError(f"request state was not released for {case_id}")
        if response.get("memoryWriteCapability") is not False:
            raise ValueError(f"memory capability appeared for {case_id}")
        if response.get("identityAuthority") is not False:
            raise ValueError(f"identity authority appeared for {case_id}")

    if physical.get("status") != "passed":
        raise ValueError("physical infrastructure did not pass")
    if physical.get("completedCaseCount") != 120:
        raise ValueError("physical completed case count mismatch")
    if physical.get("hashStable") is not True:
        raise ValueError("artifact hash was not stable")
    if (
        physical.get("engineInitialized") is not True
        or physical.get("engineClosed") is not True
    ):
        raise ValueError("engine lifecycle incomplete")
    if physical.get("allConversationsClosed") is not True:
        raise ValueError("conversation lifecycle incomplete")
    if physical.get("errors") != []:
        raise ValueError("physical report contains errors")
    for key in ("memoryWriteCapability", "identityAuthority", "lifecycleAuthority"):
        if physical.get(key) is not False:
            raise ValueError("physical authority boundary violated")
    if "OK (2 tests)" not in transcript or "INSTRUMENTATION_CODE: -1" not in transcript:
        raise ValueError("instrumentation transcript is incomplete")

    execution = value.get("execution", {})
    if execution.get("infrastructureStatus") != "PASSED":
        raise ValueError("manifest infrastructure status mismatch")
    if execution.get("completedCaseCount") != 120:
        raise ValueError("manifest completed case count mismatch")
    if execution.get("artifactHashStable") is not True:
        raise ValueError("manifest artifact hash state mismatch")
    if (
        execution.get("engineInitialized") is not True
        or execution.get("engineClosed") is not True
        or execution.get("allConversationsClosed") is not True
    ):
        raise ValueError("manifest lifecycle state mismatch")
    if execution.get("errors") != []:
        raise ValueError("manifest execution errors mismatch")

    promotion = value.get("promotion", {})
    if promotion.get("blockers") != EXPECTED_BLOCKERS:
        raise ValueError("promotion blockers mismatch")
    for key in (
        "reproducibleConversionEvidence",
        "certified",
        "signed",
        "installed",
        "normalRuntimeActivated",
        "productionAuthorization",
        "productionPromotionAllowed",
    ):
        if promotion.get(key) is not False:
            raise ValueError("physical evidence cannot authorize production")
    boundary = value.get("authorityBoundary", {})
    if boundary.get("intrinsicMotorOwnedByMorimil") is not True:
        raise ValueError("intrinsic ownership missing")
    if boundary.get("requestStateReleased") is not True:
        raise ValueError("request state boundary missing")
    for key in (
        "externalMotorMayBecomeIdentity",
        "memoryWriteCapability",
        "identityAuthority",
        "lifecycleAuthority",
    ):
        if boundary.get(key) is not False:
            raise ValueError("Morimil authority boundary violated")
    if value.get("artifact", {}).get("sourceModelRevision") is not None:
        raise ValueError("source model revision must remain unknown")
    if digest(value) != EXPECTED_MANIFEST_DIGEST:
        raise ValueError("immutable evidence manifest digest mismatch")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "command", choices=("check", "print-digest"), nargs="?", default="check"
    )
    parser.add_argument("--manifest", type=Path, default=default_manifest_path())
    args = parser.parse_args()
    manifest = load_manifest(args.manifest)
    validate_manifest(manifest)
    if args.command == "print-digest":
        print(digest(manifest))
    else:
        print("Morimil v0.2 physical benchmark evidence v1: VALID")
        print("Physical infrastructure: PASSED")
        print("Cases completed: 120/120")
        print("Quality gate: FAILED (40 false acceptances)")
        print("Production promotion: BLOCKED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
