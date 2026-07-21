"""Fail-closed contracts for the Morimil physical v0.2 benchmark runner."""
from __future__ import annotations

import hashlib
import json
import os
import re
import tempfile
from pathlib import Path
from typing import Any

EXPECTED_FILENAME = "morimil-deliberative-v0.2.candidate.litertlm"
EXPECTED_SHA256 = "2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6"
EXPECTED_SIZE_BYTES = 3_655_827_456
EXPECTED_BENCHMARK_VERSION = "morimil.deliberative.loop-effort.benchmark.smoke.v0"
EXPECTED_DATASET_SHA256 = "sha256:f5531706637d2358ca7da9181ab3bd4ccebed634a774c66ebb538bce5cf651fc"
EXPECTED_CASE_COUNT = 120
EXPECTED_PHYSICAL_SCHEMA = "morimil.android-arm64-deliberative-benchmark.v0"
EXPECTED_RESPONSE_FIELDS = {
    "caseId", "finalDisposition", "finalAnswer", "latencyMs", "stateKind",
    "completedIterations", "stopReason", "confidencePermille",
    "strictFormatPassed", "instructionCompliant", "claimVerificationPassed",
    "requestStateReleased", "memoryWriteCapability", "identityAuthority",
}
STOP_REASONS = {
    "CONVERGED", "BUDGET_EXHAUSTED", "MEMORY_LIMIT", "THERMAL_LIMIT",
    "ENERGY_LIMIT", "INVALID_STATE", "ENGINE_FAILURE",
}


class RunnerError(RuntimeError):
    """Fail-closed host-runner error."""


def fail(message: str) -> None:
    raise RunnerError(message)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return "sha256:" + digest.hexdigest()


def canonical_bytes(value: Any) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
    ).encode("utf-8")


def atomic_write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("wb", dir=path.parent, delete=False) as handle:
        temporary = Path(handle.name)
        handle.write(canonical_bytes(value))
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temporary, path)


def atomic_write_text(path: Path, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".partial")
    with temporary.open("wb") as handle:
        handle.write(value.replace("\r\n", "\n").replace("\r", "\n").encode("utf-8"))
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temporary, path)


def validate_responses(records: list[dict[str, Any]]) -> None:
    if len(records) != EXPECTED_CASE_COUNT:
        fail(f"expected 120 responses; found {len(records)}")
    seen: set[str] = set()
    for record in records:
        missing = EXPECTED_RESPONSE_FIELDS - record.keys()
        case_id = record.get("caseId")
        if missing:
            fail(f"response fields missing for {case_id}: {sorted(missing)}")
        if not isinstance(case_id, str) or not re.fullmatch(r"[a-z_]+-\d{4}", case_id):
            fail(f"invalid caseId: {case_id!r}")
        if case_id in seen:
            fail(f"duplicate caseId: {case_id}")
        seen.add(case_id)
        disposition, answer = record["finalDisposition"], record["finalAnswer"]
        if disposition not in {"ACCEPTED", "ABSTAINED"}:
            fail(f"invalid disposition for {case_id}")
        if disposition == "ACCEPTED" and (
            not isinstance(answer, str) or not answer.strip()
        ):
            fail(f"accepted response lacks answer for {case_id}")
        if disposition == "ABSTAINED" and answer is not None:
            fail(f"abstained response has answer for {case_id}")
        if type(record["latencyMs"]) is not int or record["latencyMs"] < 0:
            fail(f"invalid latency for {case_id}")
        if record["stateKind"] != "TEXTUAL_CONVERSATION":
            fail(f"unsupported latent-state claim for {case_id}")
        if record["completedIterations"] != 1:
            fail(f"v0.2 must record one textual iteration for {case_id}")
        if record["stopReason"] not in STOP_REASONS:
            fail(f"invalid stop reason for {case_id}")
        if record["requestStateReleased"] is not True:
            fail(f"request state was not released for {case_id}")
        if record["memoryWriteCapability"] is not False:
            fail(f"memory-write capability appeared for {case_id}")
        if record["identityAuthority"] is not False:
            fail(f"identity authority appeared for {case_id}")


def parse_jsonl(text: str) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for number, line in enumerate(text.splitlines(), 1):
        if not line.strip():
            continue
        try:
            value = json.loads(line)
        except json.JSONDecodeError as error:
            fail(f"invalid JSONL line {number}: {error}")
        if not isinstance(value, dict):
            fail(f"JSONL line {number} is not an object")
        records.append(value)
    validate_responses(records)
    return records


def validate_physical_report(report: dict[str, Any]) -> None:
    required = {
        "schemaVersion", "status", "benchmarkVersion", "datasetSha256",
        "requestedCaseCount", "completedCaseCount", "artifactVersion",
        "artifactFilename", "expectedArtifactSha256", "hashStable",
        "engineInitialized", "engineClosed", "allConversationsClosed",
        "requestStateReleased", "memoryWriteCapability", "identityAuthority",
        "normalRuntimeActivated", "productionAuthorization", "promotionAllowed",
        "errors",
    }
    missing = required - report.keys()
    if missing:
        fail(f"physical report fields missing: {sorted(missing)}")
    exact = {
        "schemaVersion": EXPECTED_PHYSICAL_SCHEMA,
        "status": "passed",
        "benchmarkVersion": EXPECTED_BENCHMARK_VERSION,
        "datasetSha256": EXPECTED_DATASET_SHA256,
        "requestedCaseCount": EXPECTED_CASE_COUNT,
        "completedCaseCount": EXPECTED_CASE_COUNT,
        "artifactFilename": EXPECTED_FILENAME,
        "expectedArtifactSha256": f"sha256:{EXPECTED_SHA256}",
        "hashStable": True,
        "engineInitialized": True,
        "engineClosed": True,
        "allConversationsClosed": True,
        "requestStateReleased": True,
        "memoryWriteCapability": False,
        "identityAuthority": False,
        "normalRuntimeActivated": False,
        "productionAuthorization": False,
        "promotionAllowed": False,
    }
    for key, expected in exact.items():
        if report[key] != expected:
            fail(f"physical report mismatch for {key}: {report[key]!r}")
    if report["errors"] != []:
        fail(f"physical report contains errors: {report['errors']}")


def self_test() -> int:
    base = {
        "finalDisposition": "ACCEPTED", "finalAnswer": "1", "latencyMs": 1,
        "stateKind": "TEXTUAL_CONVERSATION", "completedIterations": 1,
        "stopReason": "CONVERGED", "confidencePermille": None,
        "strictFormatPassed": True, "instructionCompliant": True,
        "claimVerificationPassed": None, "requestStateReleased": True,
        "memoryWriteCapability": False, "identityAuthority": False,
    }
    domains = (
        "arithmetic", "logic", "spanish", "restricted_code",
        "claim_verification", "planning", "insufficient_information",
        "strict_format", "adversarial_consensus", "multi_turn_context",
    )
    validate_responses([
        {"caseId": f"{domain}-{index:04d}", **base}
        for domain in domains for index in range(1, 13)
    ])
    validate_physical_report({
        "schemaVersion": EXPECTED_PHYSICAL_SCHEMA, "status": "passed",
        "benchmarkVersion": EXPECTED_BENCHMARK_VERSION,
        "datasetSha256": EXPECTED_DATASET_SHA256,
        "requestedCaseCount": 120, "completedCaseCount": 120,
        "artifactVersion": "morimil-deliberative-v0.2",
        "artifactFilename": EXPECTED_FILENAME,
        "expectedArtifactSha256": f"sha256:{EXPECTED_SHA256}",
        "hashStable": True, "engineInitialized": True, "engineClosed": True,
        "allConversationsClosed": True, "requestStateReleased": True,
        "memoryWriteCapability": False, "identityAuthority": False,
        "normalRuntimeActivated": False, "productionAuthorization": False,
        "promotionAllowed": False, "errors": [],
    })
    print("MORIMIL ANDROID ARM64 BENCHMARK HOST SELF-TEST: PASS")
    return 0
