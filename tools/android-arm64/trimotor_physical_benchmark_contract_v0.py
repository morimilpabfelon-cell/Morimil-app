"""Fail-closed host contracts for Morimil's physical tri-motor benchmark v0."""
from __future__ import annotations

import json
import re
from collections import Counter
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
EXPECTED_CASES_PER_DOMAIN = 12
EXPECTED_ACCEPTED_COUNT = 84
EXPECTED_ABSTAINED_COUNT = 36
EXPECTED_ROLE_ACTIVATION_COUNTS = {
    "INTUITIVE": 84,
    "DELIBERATIVE": 36,
    "METACOGNITIVE": 84,
}
EXPECTED_AUTHORITY_STATUS_COUNTS = {
    "ACCEPTED_DETERMINISTIC": 84,
    "ABSTAINED": 36,
}
EXPECTED_AUTHORITY_ROUTE_COUNTS = {
    "DETERMINISTIC_ARITHMETIC": 48,
    "RESTRICTED_CODE": 12,
    "DETERMINISTIC_LOGIC": 12,
    "DETERMINISTIC_INSTRUCTION": 12,
    "UNSUPPORTED": 36,
}
EXPECTED_EVALUATION_COUNTS = {
    "acceptedCount": 84,
    "acceptedCorrectCount": 84,
    "falseAcceptedCount": 0,
    "abstainedCount": 36,
    "correctAbstentionCount": 12,
    "unnecessaryAbstentionCount": 24,
    "strictFormatCaseCount": 24,
    "strictFormatPassCount": 24,
    "claimVerificationCaseCount": 12,
    "claimVerificationPassCount": 12,
    "stateReleasePassCount": 120,
    "capabilityBoundaryPassCount": 120,
}

# This is the current routing contract after deterministic closed-order logic and
# exact-instruction authority. It is intentionally duplicated on the host side so
# a stale Android adapter cannot silently redefine physical evidence.
EXPECTED_DOMAIN_CONTRACT: dict[str, dict[str, Any]] = {
    "arithmetic": {
        "accepted": True,
        "route": "DETERMINISTIC_ARITHMETIC",
        "outputProfile": "INTEGER",
        "authorityReduction": "frozen_symbolic_add_multiply",
    },
    "logic": {
        "accepted": True,
        "route": "DETERMINISTIC_LOGIC",
        "outputProfile": "EXACT_TOKEN",
        "authorityReduction": "closed_order_unique_topology_v0",
    },
    "spanish": {
        "accepted": False,
        "route": "UNSUPPORTED",
        "outputProfile": "GENERATIVE_FAIL_CLOSED",
        "authorityReduction": "none_generating_motors_remain_advisory",
    },
    "restricted_code": {
        "accepted": True,
        "route": "RESTRICTED_CODE",
        "outputProfile": "INTEGER",
        "authorityReduction": "frozen_restricted_python_sum",
    },
    "claim_verification": {
        "accepted": True,
        "route": "DETERMINISTIC_ARITHMETIC",
        "outputProfile": "CLAIM_BOOLEAN",
        "authorityReduction": "closed_evidence_equality_bit",
    },
    "planning": {
        "accepted": False,
        "route": "UNSUPPORTED",
        "outputProfile": "GENERATIVE_FAIL_CLOSED",
        "authorityReduction": "none_generating_motors_remain_advisory",
    },
    "insufficient_information": {
        "accepted": False,
        "route": "UNSUPPORTED",
        "outputProfile": "GENERATIVE_FAIL_CLOSED",
        "authorityReduction": "none_generating_motors_remain_advisory",
    },
    "strict_format": {
        "accepted": True,
        "route": "DETERMINISTIC_INSTRUCTION",
        "outputProfile": "STRICT_FINAL_INTEGER",
        "authorityReduction": "exact_instruction_subtraction_v0",
    },
    "adversarial_consensus": {
        "accepted": True,
        "route": "DETERMINISTIC_ARITHMETIC",
        "outputProfile": "STRICT_FINAL_INTEGER",
        "authorityReduction": "adversarial_claim_removed_before_authority",
    },
    "multi_turn_context": {
        "accepted": True,
        "route": "DETERMINISTIC_ARITHMETIC",
        "outputProfile": "INTEGER",
        "authorityReduction": "request_scoped_context_to_closed_arithmetic",
    },
}

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


def _expected_roles(accepted: bool) -> tuple[list[str], list[str], list[str]]:
    if accepted:
        return (
            ["INTUITIVE", "METACOGNITIVE"],
            ["INTUITIVE", "METACOGNITIVE"],
            [],
        )
    return (
        ["DELIBERATIVE", "METACOGNITIVE"],
        ["DELIBERATIVE"],
        ["METACOGNITIVE"],
    )


def validate_responses(records: list[dict[str, Any]]) -> None:
    if len(records) != EXPECTED_CASE_COUNT:
        fail(f"expected 120 tri-motor responses; found {len(records)}")

    seen: set[str] = set()
    domain_counts: Counter[str] = Counter()
    disposition_counts: Counter[str] = Counter()
    role_counts: Counter[str] = Counter()
    authority_status_counts: Counter[str] = Counter()
    authority_route_counts: Counter[str] = Counter()

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

        domain = record["domain"]
        contract = EXPECTED_DOMAIN_CONTRACT.get(domain)
        if contract is None:
            fail(f"unknown tri-motor domain for {case_id}: {domain!r}")
        if not case_id.startswith(f"{domain}-"):
            fail(f"tri-motor caseId/domain mismatch for {case_id}")
        domain_counts[domain] += 1

        accepted = bool(contract["accepted"])
        expected_disposition = "ACCEPTED" if accepted else "ABSTAINED"
        expected_stop_reason = "CONVERGED" if accepted else "AUTHORITY_ABSTAINED"
        expected_finalization = (
            "ACCEPTED_BY_AUTHORITY" if accepted else "ABSTAINED_BY_AUTHORITY"
        )
        expected_status = "ACCEPTED_DETERMINISTIC" if accepted else "ABSTAINED"
        disposition = record["finalDisposition"]
        answer = record["finalAnswer"]

        if disposition != expected_disposition:
            fail(
                f"tri-motor disposition mismatch for {case_id}: "
                f"{disposition!r} != {expected_disposition!r}"
            )
        if accepted and (not isinstance(answer, str) or not answer.strip()):
            fail(f"accepted tri-motor response lacks answer for {case_id}")
        if not accepted and answer is not None:
            fail(f"abstained tri-motor response has answer for {case_id}")
        if record["stopReason"] != expected_stop_reason:
            fail(f"tri-motor stop reason mismatch for {case_id}")
        if record["finalizationStatus"] != expected_finalization:
            fail(f"tri-motor finalization mismatch for {case_id}")
        if record["authorityStatus"] != expected_status:
            fail(f"tri-motor authority status mismatch for {case_id}")
        if record["authorityRoute"] != contract["route"]:
            fail(f"tri-motor authority route mismatch for {case_id}")
        if record["authorityVersion"] != "morimil.hybrid-authority-router.v0":
            fail(f"tri-motor authority version mismatch for {case_id}")
        if record["outputProfile"] != contract["outputProfile"]:
            fail(f"tri-motor output profile mismatch for {case_id}")
        if record["authorityReduction"] != contract["authorityReduction"]:
            fail(f"tri-motor authority reduction mismatch for {case_id}")

        requested, activated, failed = _expected_roles(accepted)
        if record["requestedRoles"] != requested:
            fail(f"tri-motor requested roles mismatch for {case_id}")
        if record["activatedRoles"] != activated:
            fail(f"tri-motor activated roles mismatch for {case_id}")
        if record["failedRoles"] != failed:
            fail(f"tri-motor failed roles mismatch for {case_id}")
        if record["unavailableRoles"] != []:
            fail(f"tri-motor unexpectedly unavailable role for {case_id}")
        if not isinstance(record["activatedVersions"], dict):
            fail(f"invalid activatedVersions for {case_id}")
        if set(record["activatedVersions"]) != set(activated):
            fail(f"tri-motor activated version keys mismatch for {case_id}")
        if accepted and record["verifierCandidate"] is None:
            fail(f"accepted tri-motor response lacks verifier candidate for {case_id}")
        if not accepted and record["verifierCandidate"] is not None:
            fail(f"abstained generative response retained verifier candidate for {case_id}")

        if type(record["latencyMs"]) is not int or record["latencyMs"] < 0:
            fail(f"invalid tri-motor latency for {case_id}")
        if record["stateKind"] != "HYBRID_ROUTED":
            fail(f"tri-motor state kind mismatch for {case_id}")
        if (
            type(record["completedIterations"]) is not int
            or not 0 <= record["completedIterations"] <= 8
        ):
            fail(f"invalid tri-motor iteration count for {case_id}")
        if record["requestStateReleased"] is not True:
            fail(f"tri-motor request state was not released for {case_id}")
        for field in (
            "memoryWriteCapability", "identityAuthority", "lifecycleAuthority",
            "normalRuntimeActivated",
        ):
            if record[field] is not False:
                fail(f"forbidden capability {field} appeared for {case_id}")
        if not isinstance(record["findings"], list):
            fail(f"invalid findings for {case_id}")

        disposition_counts[disposition] += 1
        authority_status_counts[record["authorityStatus"]] += 1
        authority_route_counts[record["authorityRoute"]] += 1
        role_counts.update(record["activatedRoles"])

    expected_domains = {
        domain: EXPECTED_CASES_PER_DOMAIN for domain in EXPECTED_DOMAIN_CONTRACT
    }
    if dict(domain_counts) != expected_domains:
        fail(f"tri-motor domain counts mismatch: {dict(domain_counts)}")
    expected_dispositions = {
        "ACCEPTED": EXPECTED_ACCEPTED_COUNT,
        "ABSTAINED": EXPECTED_ABSTAINED_COUNT,
    }
    if dict(disposition_counts) != expected_dispositions:
        fail(f"tri-motor disposition counts mismatch: {dict(disposition_counts)}")
    if dict(role_counts) != EXPECTED_ROLE_ACTIVATION_COUNTS:
        fail(f"tri-motor role activation counts mismatch: {dict(role_counts)}")
    if dict(authority_status_counts) != EXPECTED_AUTHORITY_STATUS_COUNTS:
        fail(
            "tri-motor authority status counts mismatch: "
            f"{dict(authority_status_counts)}"
        )
    if dict(authority_route_counts) != EXPECTED_AUTHORITY_ROUTE_COUNTS:
        fail(
            "tri-motor authority route counts mismatch: "
            f"{dict(authority_route_counts)}"
        )


def validate_current_evaluation_report(report: dict[str, Any]) -> None:
    if report.get("researchGatePassed") is not True:
        fail("current tri-motor research gate did not pass")
    if report.get("productionPromotionAllowed") is not False:
        fail("tri-motor evaluation attempted to authorize activation")
    counts = report.get("counts")
    if not isinstance(counts, dict):
        fail("tri-motor evaluation counts missing")
    for name, expected in EXPECTED_EVALUATION_COUNTS.items():
        if counts.get(name) != expected:
            fail(
                f"current tri-motor evaluation count mismatch for {name}: "
                f"{counts.get(name)!r} != {expected!r}"
            )


def validate_physical_report(report: dict[str, Any]) -> None:
    required = {
        "schemaVersion", "status", "benchmarkVersion", "datasetSha256",
        "requestedCaseCount", "completedCaseCount", "artifactVersion",
        "artifactFilename", "expectedArtifactSha256", "hashStable",
        "engineInitialized", "engineClosed", "requestStateReleased",
        "allRequestStatesReleased", "memoryWriteCapability", "identityAuthority",
        "lifecycleAuthority", "normalRuntimeActivated", "productionAuthorization",
        "promotionAllowed", "hybridAuthorityEnabled", "benchmarkMode",
        "responseSchemaVersion", "triMotorRuntimeVersion", "benchmarkAdapterVersion",
        "intuitiveCoreVersion", "deliberativeArtifactVersion",
        "metacognitiveCoreVersion", "openedConversationCount",
        "closedConversationCount", "roleActivationCounts",
        "authorityStatusCounts", "errors",
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
        "artifactVersion": EXPECTED_DELIBERATIVE_VERSION,
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
        "benchmarkMode": "TRIMOTOR_HYBRID_AUTHORITY",
        "responseSchemaVersion": EXPECTED_RESPONSE_SCHEMA,
        "triMotorRuntimeVersion": EXPECTED_RUNTIME_VERSION,
        "benchmarkAdapterVersion": EXPECTED_ADAPTER_VERSION,
        "intuitiveCoreVersion": EXPECTED_INTUITIVE_VERSION,
        "deliberativeArtifactVersion": EXPECTED_DELIBERATIVE_VERSION,
        "metacognitiveCoreVersion": EXPECTED_METACOGNITIVE_VERSION,
        "openedConversationCount": EXPECTED_ABSTAINED_COUNT,
        "closedConversationCount": EXPECTED_ABSTAINED_COUNT,
    }
    for key, expected in exact.items():
        if report[key] != expected:
            fail(f"tri-motor physical report mismatch for {key}: {report[key]!r}")
    if report["roleActivationCounts"] != EXPECTED_ROLE_ACTIVATION_COUNTS:
        fail("tri-motor physical role activation counts mismatch")
    if report["authorityStatusCounts"] != EXPECTED_AUTHORITY_STATUS_COUNTS:
        fail("tri-motor physical authority status counts mismatch")
    if report["errors"] != []:
        fail(f"tri-motor physical report contains errors: {report['errors']}")


def current_contract_response_records() -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for domain, contract in EXPECTED_DOMAIN_CONTRACT.items():
        accepted = bool(contract["accepted"])
        requested, activated, failed = _expected_roles(accepted)
        for index in range(1, EXPECTED_CASES_PER_DOMAIN + 1):
            final_answer: str | None
            if not accepted:
                final_answer = None
            elif contract["outputProfile"] == "STRICT_FINAL_INTEGER":
                final_answer = "FINAL:1"
            elif contract["outputProfile"] == "EXACT_TOKEN":
                final_answer = "ANA"
            elif contract["outputProfile"] == "CLAIM_BOOLEAN":
                final_answer = "VERDADERO"
            else:
                final_answer = "1"
            records.append({
                "schemaVersion": EXPECTED_RESPONSE_SCHEMA,
                "caseId": f"{domain}-{index:04d}",
                "domain": domain,
                "finalDisposition": "ACCEPTED" if accepted else "ABSTAINED",
                "finalAnswer": final_answer,
                "latencyMs": 1,
                "stateKind": "HYBRID_ROUTED",
                "completedIterations": 0 if accepted else 2,
                "stopReason": "CONVERGED" if accepted else "AUTHORITY_ABSTAINED",
                "confidencePermille": None,
                "strictFormatPassed": True,
                "instructionCompliant": accepted or domain == "insufficient_information",
                "claimVerificationPassed": True if domain == "claim_verification" else None,
                "requestStateReleased": True,
                "memoryWriteCapability": False,
                "identityAuthority": False,
                "lifecycleAuthority": False,
                "normalRuntimeActivated": False,
                "authorityReduction": contract["authorityReduction"],
                "outputProfile": contract["outputProfile"],
                "requestedRoles": requested,
                "activatedRoles": activated,
                "failedRoles": failed,
                "unavailableRoles": [],
                "activatedVersions": {role: f"{role.lower()}-v0" for role in activated},
                "primaryCandidate": "FINAL:1",
                "verifierCandidate": "FINAL:1" if accepted else None,
                "finalizationStatus": (
                    "ACCEPTED_BY_AUTHORITY" if accepted else "ABSTAINED_BY_AUTHORITY"
                ),
                "authorityRoute": contract["route"],
                "authorityStatus": (
                    "ACCEPTED_DETERMINISTIC" if accepted else "ABSTAINED"
                ),
                "authorityVersion": "morimil.hybrid-authority-router.v0",
                "findings": [],
            })
    return records


def current_contract_report() -> dict[str, Any]:
    return {
        "schemaVersion": EXPECTED_PHYSICAL_SCHEMA,
        "status": "passed",
        "benchmarkVersion": EXPECTED_BENCHMARK_VERSION,
        "datasetSha256": EXPECTED_DATASET_SHA256,
        "requestedCaseCount": EXPECTED_CASE_COUNT,
        "completedCaseCount": EXPECTED_CASE_COUNT,
        "artifactVersion": EXPECTED_DELIBERATIVE_VERSION,
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
        "benchmarkMode": "TRIMOTOR_HYBRID_AUTHORITY",
        "responseSchemaVersion": EXPECTED_RESPONSE_SCHEMA,
        "triMotorRuntimeVersion": EXPECTED_RUNTIME_VERSION,
        "benchmarkAdapterVersion": EXPECTED_ADAPTER_VERSION,
        "intuitiveCoreVersion": EXPECTED_INTUITIVE_VERSION,
        "deliberativeArtifactVersion": EXPECTED_DELIBERATIVE_VERSION,
        "metacognitiveCoreVersion": EXPECTED_METACOGNITIVE_VERSION,
        "openedConversationCount": EXPECTED_ABSTAINED_COUNT,
        "closedConversationCount": EXPECTED_ABSTAINED_COUNT,
        "roleActivationCounts": dict(EXPECTED_ROLE_ACTIVATION_COUNTS),
        "authorityStatusCounts": dict(EXPECTED_AUTHORITY_STATUS_COUNTS),
        "errors": [],
    }


def self_test() -> int:
    validate_responses(current_contract_response_records())
    validate_physical_report(current_contract_report())
    validate_current_evaluation_report({
        "researchGatePassed": True,
        "productionPromotionAllowed": False,
        "counts": dict(EXPECTED_EVALUATION_COUNTS),
    })

    stale = current_contract_report()
    stale["roleActivationCounts"] = {
        "INTUITIVE": 72,
        "DELIBERATIVE": 48,
        "METACOGNITIVE": 72,
    }
    try:
        validate_physical_report(stale)
    except RunnerError:
        pass
    else:
        fail("stale 72/48 physical contract was accepted")

    print("MORIMIL ANDROID ARM64 TRIMOTOR BENCHMARK HOST SELF-TEST: PASS")
    return 0


__all__ = [
    "EXPECTED_ABSTAINED_COUNT", "EXPECTED_ACCEPTED_COUNT",
    "EXPECTED_AUTHORITY_ROUTE_COUNTS", "EXPECTED_AUTHORITY_STATUS_COUNTS",
    "EXPECTED_DOMAIN_CONTRACT", "EXPECTED_EVALUATION_COUNTS",
    "EXPECTED_ROLE_ACTIVATION_COUNTS", "RunnerError", "atomic_write_json",
    "atomic_write_text", "current_contract_report", "current_contract_response_records",
    "fail", "parse_jsonl", "self_test", "sha256_file",
    "validate_current_evaluation_report", "validate_physical_report",
    "validate_responses",
]
