"""Fail-closed host contracts for Morimil's physical tri-motor benchmark v0."""
from __future__ import annotations

import json
import re
from typing import Any

from physical_benchmark_contract_v0 import (
    EXPECTED_BENCHMARK_VERSION,
    EXPECTED_CASE_COUNT,
    EXPECTED_DATASET_SHA256,
    EXPECTED_FILENAME,
    EXPECTED_SHA256,
    RunnerError,
    atomic_write_json,
    atomic_write_text,
    fail,
    sha256_file,
)

EXPECTED_PHYSICAL_SCHEMA = "morimil.android-arm64-trimotor-benchmark.v0"
EXPECTED_RESPONSE_SCHEMA = "morimil.trimotor.benchmark.response.v0"
EXPECTED_RUNTIME_VERSION = "morimil.intrinsic-trimotor.research-runtime.v0"
EXPECTED_ADAPTER_VERSION = "morimil.trimotor.benchmark-adapter.v0"
EXPECTED_INTUITIVE_VERSION = "morimil.intuitive.bounded-local-core.v0"
EXPECTED_METACOGNITIVE_VERSION = "morimil.metacognitive.bounded-local-core.v0"
EXPECTED_DELIBERATIVE_VERSION = "morimil-deliberative-v0.2"
EXPECTED_ROLES = {"INTUITIVE", "DELIBERATIVE", "METACOGNITIVE"}
EXPECTED_RESPONSE_FIELDS = {
    "schemaVersion", "caseId", "domain", "finalDisposition", "finalAnswer",
    "latencyMs", "stateKind", "completedIterations", "stopReason",
    "confidencePermille", "strictFormatPassed", "instructionCompliant",
    "claimVerificationPassed", "requestStateReleased", "memoryWriteCapability",
    "identityAuthority", "lifecycleAuthority", "normalRuntimeActivated",
    "authorityReduction", "outputProfile", "requestedRoles", "activatedRoles",
    "failedRoles", "unavailableRoles", "activatedVersions", "primaryCandidate",
    "verifierCandidate", "finalizationStatus", "authorityRoute",
    "authorityStatus", "authorityVersion", "findings",
}


def parse_jsonl(text: str) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for number, line in enumerate(text.splitlines(), 1):
        if not line.strip():
            continue
        try:
            value = json.loads(line)
        except json.JSONDecodeError as error:
            fail(f"invalid tri-motor JSONL line {number}: {error}")
        if not isinstance(value, dict):
            fail(f"tri-motor JSONL line {number} is not an object")
        records.append(value)
    validate_responses(records)
    return records


def validate_responses(records: list[dict[str, Any]]) -> None:
    if len(records) != EXPECTED_CASE_COUNT:
        fail(f"expected 120 tri-motor responses; found {len(records)}")
    seen: set[str] = set()
    role_union: set[str] = set()
    for record in records:
        missing = EXPECTED_RESPONSE_FIELDS - record.keys()
        case_id = record.get("caseId")
        if missing:
            fail(f"tri-motor response fields missing for {case_id}: {sorted(missing)}")
        if record["schemaVersion"] != EXPECTED_RESPONSE_SCHEMA:
            fail(f"tri-motor response schema mismatch for {case_id}")
        if not isinstance(case_id, str) or not re.fullmatch(r"[a-z_]+-\d{4}", case_id):
            fail(f"invalid tri-motor caseId: {case_id!r}")
        if case_id in seen:
            fail(f"duplicate tri-motor caseId: {case_id}")
        seen.add(case_id)
        disposition, answer = record["finalDisposition"], record["finalAnswer"]
        if disposition not in {"ACCEPTED", "ABSTAINED"}:
            fail(f"invalid tri-motor disposition for {case_id}")
        if disposition == "ACCEPTED" and (
            not isinstance(answer, str) or not answer.strip()
        ):
            fail(f"accepted tri-motor response lacks answer for {case_id}")
        if disposition == "ABSTAINED" and answer is not None:
            fail(f"abstained tri-motor response has answer for {case_id}")
        if type(record["latencyMs"]) is not int or record["latencyMs"] < 0:
            fail(f"invalid tri-motor latency for {case_id}")
        if record["stateKind"] != "HYBRID_ROUTED":
            fail(f"tri-motor state kind mismatch for {case_id}")
        if type(record["completedIterations"]) is not int or not 0 <= record["completedIterations"] <= 8:
            fail(f"invalid tri-motor iteration count for {case_id}")
        if record["stopReason"] not in {"CONVERGED", "AUTHORITY_ABSTAINED"}:
            fail(f"invalid tri-motor stop reason for {case_id}")
        if record["requestStateReleased"] is not True:
            fail(f"tri-motor request state was not released for {case_id}")
        for field in (
            "memoryWriteCapability", "identityAuthority", "lifecycleAuthority",
            "normalRuntimeActivated",
        ):
            if record[field] is not False:
                fail(f"forbidden capability {field} appeared for {case_id}")
        for field in ("requestedRoles", "activatedRoles", "failedRoles", "unavailableRoles"):
            value = record[field]
            if not isinstance(value, list) or any(role not in EXPECTED_ROLES for role in value):
                fail(f"invalid {field} for {case_id}")
        role_union.update(record["activatedRoles"])
        if not isinstance(record["activatedVersions"], dict):
            fail(f"invalid activatedVersions for {case_id}")
        if not isinstance(record["findings"], list):
            fail(f"invalid findings for {case_id}")
        if disposition == "ACCEPTED" and record["finalizationStatus"] != "ACCEPTED_BY_AUTHORITY":
            fail(f"accepted response bypassed authority for {case_id}")
        if disposition == "ABSTAINED" and record["stopReason"] != "AUTHORITY_ABSTAINED":
            fail(f"abstention lacks authority stop reason for {case_id}")
    if role_union != EXPECTED_ROLES:
        fail(f"tri-motor role coverage mismatch: {sorted(role_union)}")


def validate_physical_report(report: dict[str, Any]) -> None:
    required = {
        "schemaVersion", "status", "benchmarkVersion", "datasetSha256",
        "requestedCaseCount", "completedCaseCount", "artifactVersion",
        "artifactFilename", "expectedArtifactSha256", "hashStable",
        "engineInitialized", "engineClosed", "requestStateReleased",
        "allRequestStatesReleased", "memoryWriteCapability", "identityAuthority",
        "lifecycleAuthority", "normalRuntimeActivated", "productionAuthorization",
        "promotionAllowed", "hybridAuthorityEnabled", "triMotorRuntimeVersion",
        "benchmarkAdapterVersion", "intuitiveCoreVersion",
        "deliberativeArtifactVersion", "metacognitiveCoreVersion",
        "openedConversationCount", "closedConversationCount",
        "roleActivationCounts", "authorityStatusCounts", "errors",
    }
    missing = required - report.keys()
    if missing:
        fail(f"tri-motor physical report fields missing: {sorted(missing)}")
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
        "requestStateReleased": True,
        "allRequestStatesReleased": True,
        "memoryWriteCapability": False,
        "identityAuthority": False,
        "lifecycleAuthority": False,
        "normalRuntimeActivated": False,
        "productionAuthorization": False,
        "promotionAllowed": False,
        "hybridAuthorityEnabled": True,
        "triMotorRuntimeVersion": EXPECTED_RUNTIME_VERSION,
        "benchmarkAdapterVersion": EXPECTED_ADAPTER_VERSION,
        "intuitiveCoreVersion": EXPECTED_INTUITIVE_VERSION,
        "deliberativeArtifactVersion": EXPECTED_DELIBERATIVE_VERSION,
        "metacognitiveCoreVersion": EXPECTED_METACOGNITIVE_VERSION,
    }
    for key, expected in exact.items():
        if report[key] != expected:
            fail(f"tri-motor physical report mismatch for {key}: {report[key]!r}")
    if report["openedConversationCount"] != report["closedConversationCount"]:
        fail("tri-motor physical report records a conversation leak")
    counts = report["roleActivationCounts"]
    if not isinstance(counts, dict) or set(counts) != EXPECTED_ROLES:
        fail("tri-motor role activation counts are incomplete")
    if any(type(counts[role]) is not int or counts[role] <= 0 for role in EXPECTED_ROLES):
        fail("every tri-motor role must activate at least once")
    if report["errors"] != []:
        fail(f"tri-motor physical report contains errors: {report['errors']}")


def self_test() -> int:
    domains = (
        "arithmetic", "logic", "spanish", "restricted_code",
        "claim_verification", "planning", "insufficient_information",
        "strict_format", "adversarial_consensus", "multi_turn_context",
    )
    bounded = {
        "arithmetic", "restricted_code", "claim_verification", "strict_format",
        "adversarial_consensus", "multi_turn_context",
    }
    records = []
    for domain in domains:
        for index in range(1, 13):
            accepted = domain in bounded
            activated = ["INTUITIVE", "METACOGNITIVE"] if accepted else ["DELIBERATIVE"]
            records.append({
                "schemaVersion": EXPECTED_RESPONSE_SCHEMA,
                "caseId": f"{domain}-{index:04d}", "domain": domain,
                "finalDisposition": "ACCEPTED" if accepted else "ABSTAINED",
                "finalAnswer": "1" if accepted else None, "latencyMs": 1,
                "stateKind": "HYBRID_ROUTED", "completedIterations": 0 if accepted else 2,
                "stopReason": "CONVERGED" if accepted else "AUTHORITY_ABSTAINED",
                "confidencePermille": None, "strictFormatPassed": True,
                "instructionCompliant": accepted, "claimVerificationPassed": None,
                "requestStateReleased": True, "memoryWriteCapability": False,
                "identityAuthority": False, "lifecycleAuthority": False,
                "normalRuntimeActivated": False, "authorityReduction": "self_test",
                "outputProfile": "INTEGER", "requestedRoles": activated,
                "activatedRoles": activated,
                "failedRoles": [] if accepted else ["METACOGNITIVE"],
                "unavailableRoles": [], "activatedVersions": {},
                "primaryCandidate": "FINAL:1", "verifierCandidate": "FINAL:1" if accepted else None,
                "finalizationStatus": "ACCEPTED_BY_AUTHORITY" if accepted else "ABSTAINED_BY_AUTHORITY",
                "authorityRoute": "DETERMINISTIC_ARITHMETIC" if accepted else "UNSUPPORTED",
                "authorityStatus": "ACCEPTED_DETERMINISTIC" if accepted else "ABSTAINED",
                "authorityVersion": "morimil.hybrid-authority-router.v0", "findings": [],
            })
    validate_responses(records)
    validate_physical_report({
        "schemaVersion": EXPECTED_PHYSICAL_SCHEMA, "status": "passed",
        "benchmarkVersion": EXPECTED_BENCHMARK_VERSION,
        "datasetSha256": EXPECTED_DATASET_SHA256,
        "requestedCaseCount": 120, "completedCaseCount": 120,
        "artifactVersion": EXPECTED_DELIBERATIVE_VERSION,
        "artifactFilename": EXPECTED_FILENAME,
        "expectedArtifactSha256": f"sha256:{EXPECTED_SHA256}",
        "hashStable": True, "engineInitialized": True, "engineClosed": True,
        "requestStateReleased": True, "allRequestStatesReleased": True,
        "memoryWriteCapability": False, "identityAuthority": False,
        "lifecycleAuthority": False, "normalRuntimeActivated": False,
        "productionAuthorization": False, "promotionAllowed": False,
        "hybridAuthorityEnabled": True,
        "triMotorRuntimeVersion": EXPECTED_RUNTIME_VERSION,
        "benchmarkAdapterVersion": EXPECTED_ADAPTER_VERSION,
        "intuitiveCoreVersion": EXPECTED_INTUITIVE_VERSION,
        "deliberativeArtifactVersion": EXPECTED_DELIBERATIVE_VERSION,
        "metacognitiveCoreVersion": EXPECTED_METACOGNITIVE_VERSION,
        "openedConversationCount": 48, "closedConversationCount": 48,
        "roleActivationCounts": {"INTUITIVE": 72, "DELIBERATIVE": 48, "METACOGNITIVE": 72},
        "authorityStatusCounts": {"ACCEPTED_DETERMINISTIC": 72, "ABSTAINED": 48},
        "errors": [],
    })
    print("MORIMIL ANDROID ARM64 TRIMOTOR BENCHMARK HOST SELF-TEST: PASS")
    return 0


__all__ = [
    "RunnerError", "atomic_write_json", "atomic_write_text", "fail", "parse_jsonl",
    "self_test", "sha256_file", "validate_physical_report", "validate_responses",
]
