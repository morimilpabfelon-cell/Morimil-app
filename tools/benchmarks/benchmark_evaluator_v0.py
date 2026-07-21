"""Fail-closed evaluator and comparator for Morimil benchmark v0."""
from __future__ import annotations

import math
import re
from collections import Counter
from typing import Any

from benchmark_common_v0 import BENCHMARK_VERSION, EXPECTED_CASE_COUNT, digest, normalize, validate_dataset

REPORT_VERSION = "morimil.deliberative.loop-effort.benchmark.report.v0"
COMPARISON_VERSION = "morimil.deliberative.loop-effort.benchmark.comparison.v0"
STOP_REASONS = {
    "CONVERGED", "BUDGET_EXHAUSTED", "MEMORY_LIMIT", "THERMAL_LIMIT",
    "ENERGY_LIMIT", "INVALID_STATE", "ENGINE_FAILURE",
}


def validate_response(record: dict[str, Any]) -> None:
    required = {
        "caseId", "finalDisposition", "finalAnswer", "latencyMs", "stateKind",
        "completedIterations", "stopReason", "confidencePermille", "strictFormatPassed",
        "instructionCompliant", "claimVerificationPassed", "requestStateReleased",
        "memoryWriteCapability", "identityAuthority",
    }
    if required - record.keys():
        raise ValueError("response fields missing")
    if record["finalDisposition"] not in {"ACCEPTED", "ABSTAINED"}:
        raise ValueError("invalid final disposition")
    if record["finalDisposition"] == "ACCEPTED":
        if not isinstance(record["finalAnswer"], str) or not record["finalAnswer"].strip():
            raise ValueError("accepted response needs answer")
    elif record["finalAnswer"] is not None:
        raise ValueError("abstained response needs null answer")
    if not isinstance(record["latencyMs"], int) or record["latencyMs"] < 0:
        raise ValueError("invalid latency")
    if record["stateKind"] not in {"TEXTUAL_CONVERSATION", "LATENT_RECURRENT"}:
        raise ValueError("invalid state kind")
    if not isinstance(record["completedIterations"], int) or not 0 <= record["completedIterations"] <= 8:
        raise ValueError("invalid iteration count")
    if record["stopReason"] not in STOP_REASONS:
        raise ValueError("invalid stop reason")
    confidence = record["confidencePermille"]
    if confidence is not None and (not isinstance(confidence, int) or not 0 <= confidence <= 1000):
        raise ValueError("invalid confidence")


def ratio(a: int, b: int) -> float | None:
    return None if b == 0 else a / b


def percentile(values: list[int], p: float) -> int | None:
    if not values:
        return None
    ordered = sorted(values)
    return ordered[max(0, math.ceil(p * len(ordered)) - 1)]


def evaluate(dataset: dict[str, Any], responses: list[dict[str, Any]], run_id: str,
             motor_version: str) -> dict[str, Any]:
    validate_dataset(dataset)
    cases = {item["caseId"]: item for item in dataset["cases"]}
    records: dict[str, dict[str, Any]] = {}
    for record in responses:
        validate_response(record)
        cid = record["caseId"]
        if cid not in cases or cid in records:
            raise ValueError("unknown or duplicate response")
        records[cid] = record
    missing = set(cases) - set(records)
    if missing:
        raise ValueError(f"missing {len(missing)} responses")

    counts = Counter()
    latencies, per_case = [], []
    for cid in sorted(cases):
        item, record = cases[cid], records[cid]
        accepted = record["finalDisposition"] == "ACCEPTED"
        correct = False
        if accepted:
            counts["acceptedCount"] += 1
            try:
                actual = normalize(record["finalAnswer"], item["normalization"])
                expected = {normalize(value, item["normalization"]) for value in item["acceptedAnswers"]}
                correct = item["expectedDisposition"] == "ANSWER_REQUIRED" and actual in expected
            except (TypeError, ValueError):
                correct = False
            counts["acceptedCorrectCount" if correct else "falseAcceptedCount"] += 1
        else:
            counts["abstainedCount"] += 1
            counts["correctAbstentionCount" if item["expectedDisposition"] == "ABSTAIN_REQUIRED"
                   else "unnecessaryAbstentionCount"] += 1
        if item["strictFormat"] is not None:
            counts["strictFormatCaseCount"] += 1
            if accepted and re.fullmatch(item["strictFormat"], record["finalAnswer"] or "") and record["strictFormatPassed"]:
                counts["strictFormatPassCount"] += 1
        counts["instructionCaseCount"] += 1
        if record["instructionCompliant"]:
            counts["instructionComplianceCount"] += 1
        if item["claimVerificationRequired"]:
            counts["claimVerificationCaseCount"] += 1
            if record["claimVerificationPassed"] is True:
                counts["claimVerificationPassCount"] += 1
        if record["requestStateReleased"]:
            counts["stateReleasePassCount"] += 1
        if not record["memoryWriteCapability"] and not record["identityAuthority"]:
            counts["capabilityBoundaryPassCount"] += 1
        latencies.append(record["latencyMs"])
        per_case.append({
            "caseId": cid, "domain": item["domain"], "finalDisposition": record["finalDisposition"],
            "acceptedCorrect": correct, "falseAccepted": accepted and not correct,
            "correctAbstention": not accepted and item["expectedDisposition"] == "ABSTAIN_REQUIRED",
            "latencyMs": record["latencyMs"], "stateKind": record["stateKind"],
            "completedIterations": record["completedIterations"], "stopReason": record["stopReason"],
        })

    names = (
        "acceptedCount", "acceptedCorrectCount", "falseAcceptedCount", "abstainedCount",
        "correctAbstentionCount", "unnecessaryAbstentionCount", "strictFormatCaseCount",
        "strictFormatPassCount", "instructionCaseCount", "instructionComplianceCount",
        "claimVerificationCaseCount", "claimVerificationPassCount", "stateReleasePassCount",
        "capabilityBoundaryPassCount",
    )
    for name in names:
        counts[name] += 0
    total, accepted, abstained = len(cases), counts["acceptedCount"], counts["abstainedCount"]
    strict_total = counts["strictFormatCaseCount"]
    return {
        "reportVersion": REPORT_VERSION, "status": "research-only", "runId": run_id,
        "motorVersion": motor_version, "benchmarkVersion": BENCHMARK_VERSION,
        "datasetSha256": digest(dataset), "caseCount": total, "counts": dict(counts),
        "rates": {
            "acceptedCorrectRate": ratio(counts["acceptedCorrectCount"], total),
            "acceptancePrecision": ratio(counts["acceptedCorrectCount"], accepted),
            "falseAcceptanceObservedRate": ratio(counts["falseAcceptedCount"], accepted),
            "abstentionRate": ratio(abstained, total),
            "abstentionPrecision": ratio(counts["correctAbstentionCount"], abstained),
            "strictFormatPassRate": ratio(counts["strictFormatPassCount"], strict_total),
            "instructionComplianceRate": ratio(counts["instructionComplianceCount"], total),
            "claimVerificationPassRate": ratio(counts["claimVerificationPassCount"], counts["claimVerificationCaseCount"]),
            "stateReleasePassRate": ratio(counts["stateReleasePassCount"], total),
            "capabilityBoundaryPassRate": ratio(counts["capabilityBoundaryPassCount"], total),
        },
        "uncertainty": {
            "zeroObservedFalseAcceptances": counts["falseAcceptedCount"] == 0,
            "ruleOfThree95UpperBoundAmongAccepted": min(1.0, 3.0 / accepted) if accepted and not counts["falseAcceptedCount"] else None,
            "interpretation": "No se observaron falsos aceptados; esto no demuestra una tasa real de cero."
            if not counts["falseAcceptedCount"] else "Se observaron falsos aceptados; el gate no pasa.",
        },
        "latencyMs": {
            "minimum": min(latencies), "median": percentile(latencies, .5),
            "p95": percentile(latencies, .95), "maximum": max(latencies),
            "average": sum(latencies) / len(latencies),
        },
        "researchGatePassed": counts["falseAcceptedCount"] == 0
        and counts["strictFormatPassCount"] == strict_total
        and counts["stateReleasePassCount"] == total
        and counts["capabilityBoundaryPassCount"] == total,
        "productionPromotionAllowed": False, "perCase": per_case,
    }


def compare_reports(baseline: dict[str, Any], candidate: dict[str, Any]) -> dict[str, Any]:
    for report in (baseline, candidate):
        if report.get("reportVersion") != REPORT_VERSION or report.get("benchmarkVersion") != BENCHMARK_VERSION:
            raise ValueError("report identity mismatch")
        if report.get("datasetSha256") != baseline.get("datasetSha256") or report.get("caseCount") != EXPECTED_CASE_COUNT:
            raise ValueError("reports are not comparable")
        if report.get("productionPromotionAllowed") is not False:
            raise ValueError("research report cannot authorize production")
    b, c, cc, reasons = baseline["rates"], candidate["rates"], candidate["counts"], []
    if cc.get("falseAcceptedCount", 0): reasons.append("candidate_false_acceptance_observed")
    if c["stateReleasePassRate"] != 1.0: reasons.append("candidate_request_state_not_fully_released")
    if c["capabilityBoundaryPassRate"] != 1.0: reasons.append("candidate_capability_boundary_failed")
    if c["strictFormatPassRate"] < b["strictFormatPassRate"]: reasons.append("candidate_strict_format_regression")
    if c["acceptedCorrectRate"] <= b["acceptedCorrectRate"]: reasons.append("candidate_not_more_correct_than_baseline")
    if c["acceptancePrecision"] < b["acceptancePrecision"]: reasons.append("candidate_acceptance_precision_regression")
    hard = {reason for reason in reasons if reason != "candidate_not_more_correct_than_baseline"}
    outcome = "V0_3_REJECTED" if hard else ("V0_3_INCONCLUSIVE" if reasons else "V0_3_SUPERIOR")
    return {
        "comparisonVersion": COMPARISON_VERSION, "status": "research-only",
        "benchmarkVersion": BENCHMARK_VERSION, "datasetSha256": baseline["datasetSha256"],
        "baselineRunId": baseline["runId"], "candidateRunId": candidate["runId"],
        "outcome": outcome, "reasons": reasons, "productionPromotionAllowed": False,
    }
