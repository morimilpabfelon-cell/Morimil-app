"""Strict evidence validation for Morimil's current 84/36 physical tri-motor run."""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from trimotor_physical_benchmark_contract_v0 import (
    EXPECTED_ABSTAINED_COUNT,
    EXPECTED_ACCEPTED_COUNT,
    EXPECTED_AUTHORITY_STATUS_COUNTS,
    EXPECTED_DOMAIN_CONTRACT,
    EXPECTED_EVALUATION_COUNTS,
    EXPECTED_ROLE_ACTIVATION_COUNTS,
    RunnerError,
    current_contract_response_records,
    fail,
    parse_jsonl,
    sha256_file,
    validate_current_evaluation_report,
    validate_physical_report,
)

RESPONSES_FILENAME = "responses-trimotor-v0.2.jsonl"
REPORT_FILENAME = "report-trimotor-v0.2.json"
PHYSICAL_FILENAME = "physical-execution-trimotor-v0.2.json"
BUNDLE_FILENAME = "bundle-trimotor-v0.2.json"
EXPECTED_BUNDLE_SCHEMA = "morimil.trimotor.benchmark.physical-bundle.v0"
EXPECTED_COMPARISON_OUTCOME = "V0_3_SUPERIOR"
NAMES = ["Ana", "Bruno", "Carla", "Diego", "Elena", "Fabio"]


def _case_index(record: dict[str, Any]) -> int:
    case_id = record["caseId"]
    try:
        index = int(case_id.rsplit("-", 1)[1])
    except (AttributeError, ValueError, IndexError):
        fail(f"invalid current-contract case id: {case_id!r}")
    if not 1 <= index <= 12:
        fail(f"current-contract case index out of range: {case_id}")
    return index


def expected_final_answer(record: dict[str, Any]) -> str | None:
    domain = record["domain"]
    index = _case_index(record)
    if domain in {"spanish", "planning", "insufficient_information"}:
        return None
    if domain == "arithmetic":
        left = 10 + index
        middle = 2 + index % 5
        right = 1 + index % 4
        return str(left + middle * right)
    if domain == "logic":
        return NAMES[index % 6].upper()
    if domain == "restricted_code":
        return str((3 + index) + index + 2)
    if domain == "claim_verification":
        return "VERDADERO" if index % 2 == 0 else "FALSO"
    if domain == "strict_format":
        return f"FINAL:{4 * index - (1 + index % 3)}"
    if domain == "adversarial_consensus":
        x = 30 + index
        y = 2 + index % 4
        z = 2 + index % 3
        return f"FINAL:{x - y * z}"
    if domain == "multi_turn_context":
        return str((40 + index) + (1 + index % 5))
    fail(f"unsupported current-contract domain: {domain!r}")


def validate_exact_responses(records: list[dict[str, Any]]) -> None:
    # Structural, routing and capability validation happens first.
    from trimotor_physical_benchmark_contract_v0 import validate_responses

    validate_responses(records)
    for record in records:
        case_id = record["caseId"]
        expected = expected_final_answer(record)
        if record["finalAnswer"] != expected:
            fail(
                f"current tri-motor final answer mismatch for {case_id}: "
                f"{record['finalAnswer']!r} != {expected!r}"
            )
        if record["strictFormatPassed"] is not True:
            fail(f"current tri-motor strict-format failure for {case_id}")
        expected_instruction = (
            expected is not None or record["domain"] == "insufficient_information"
        )
        if record["instructionCompliant"] is not expected_instruction:
            fail(f"current tri-motor instruction compliance mismatch for {case_id}")
        expected_claim = True if record["domain"] == "claim_verification" else None
        if record["claimVerificationPassed"] is not expected_claim:
            fail(f"current tri-motor claim-verification mismatch for {case_id}")
        if expected is not None and record["completedIterations"] != 0:
            fail(f"bounded current-contract case deliberated unexpectedly: {case_id}")
        if expected is None and record["completedIterations"] <= 0:
            fail(f"generative current-contract case did not deliberate: {case_id}")


def validate_evaluation_report(report: dict[str, Any]) -> None:
    validate_current_evaluation_report(report)
    per_case = report.get("perCase")
    if not isinstance(per_case, list) or len(per_case) != 120:
        fail("current tri-motor evaluation perCase contract mismatch")
    for item in per_case:
        if not isinstance(item, dict):
            fail("current tri-motor evaluation perCase item is not an object")
        domain = item.get("domain")
        contract = EXPECTED_DOMAIN_CONTRACT.get(domain)
        if contract is None:
            fail(f"current tri-motor evaluation domain unknown: {domain!r}")
        accepted = bool(contract["accepted"])
        if item.get("finalDisposition") != ("ACCEPTED" if accepted else "ABSTAINED"):
            fail(f"current tri-motor evaluation disposition mismatch: {item.get('caseId')}")
        if item.get("acceptedCorrect") is not accepted:
            fail(f"current tri-motor evaluation correctness mismatch: {item.get('caseId')}")
        if item.get("falseAccepted") is not False:
            fail(f"current tri-motor evaluation false acceptance: {item.get('caseId')}")
        expected_correct_abstention = domain == "insufficient_information"
        if item.get("correctAbstention") is not expected_correct_abstention:
            fail(f"current tri-motor abstention classification mismatch: {item.get('caseId')}")


def _load_object(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        fail(f"cannot load {label}: {error}")
    if not isinstance(value, dict):
        fail(f"{label} is not an object")
    return value


def _validate_bundle(bundle: dict[str, Any], run_dir: Path) -> None:
    if bundle.get("schemaVersion") != EXPECTED_BUNDLE_SCHEMA:
        fail("current tri-motor bundle schema mismatch")
    if bundle.get("status") != "COMPLETED":
        fail("current tri-motor bundle is not completed")
    if bundle.get("runId") != run_dir.name:
        fail("current tri-motor bundle runId/directory mismatch")

    benchmark = bundle.get("benchmark")
    if not isinstance(benchmark, dict):
        fail("current tri-motor bundle benchmark section missing")
    expected_benchmark = {
        "caseCount": 120,
        "responseCount": 120,
        "researchGatePassed": True,
        "falseAcceptedCount": 0,
        "acceptedCorrectCount": EXPECTED_ACCEPTED_COUNT,
        "abstainedCount": EXPECTED_ABSTAINED_COUNT,
        "strictFormatPassCount": 24,
        "strictFormatCaseCount": 24,
    }
    for key, expected in expected_benchmark.items():
        if benchmark.get(key) != expected:
            fail(f"current tri-motor bundle benchmark mismatch for {key}")

    trimotor = bundle.get("trimotor")
    if not isinstance(trimotor, dict):
        fail("current tri-motor bundle trimotor section missing")
    if trimotor.get("roleActivationCounts") != EXPECTED_ROLE_ACTIVATION_COUNTS:
        fail("current tri-motor bundle role counts mismatch")
    if trimotor.get("authorityStatusCounts") != EXPECTED_AUTHORITY_STATUS_COUNTS:
        fail("current tri-motor bundle authority status counts mismatch")
    if trimotor.get("openedConversationCount") != EXPECTED_ABSTAINED_COUNT:
        fail("current tri-motor bundle opened conversation count mismatch")
    if trimotor.get("closedConversationCount") != EXPECTED_ABSTAINED_COUNT:
        fail("current tri-motor bundle closed conversation count mismatch")

    comparison = bundle.get("comparison")
    if not isinstance(comparison, dict):
        fail("current tri-motor bundle comparison section missing")
    if comparison.get("outcome") != EXPECTED_COMPARISON_OUTCOME:
        fail("current tri-motor bundle comparison outcome mismatch")

    boundary = bundle.get("authorityBoundary")
    if not isinstance(boundary, dict) or boundary != {
        "requestStateReleased": True,
        "memoryWriteCapability": False,
        "identityAuthority": False,
        "lifecycleAuthority": False,
    }:
        fail("current tri-motor bundle authority boundary mismatch")
    for field in (
        "certified", "signed", "installed", "normalRuntimeActivated",
        "personalRuntimeActivationAuthorized", "productionAuthorization",
        "productionPromotionAllowed",
    ):
        if bundle.get(field) is not False:
            fail(f"current tri-motor bundle forbidden flag enabled: {field}")

    files = bundle.get("files")
    if not isinstance(files, dict) or not files:
        fail("current tri-motor bundle file manifest missing")
    for label, metadata in files.items():
        if not isinstance(metadata, dict):
            fail(f"current tri-motor bundle file metadata invalid: {label}")
        name = metadata.get("name")
        if not isinstance(name, str) or Path(name).name != name:
            fail(f"current tri-motor bundle filename invalid: {label}")
        path = run_dir / name
        if not path.is_file():
            fail(f"current tri-motor bundle file missing: {path}")
        if metadata.get("sha256") != sha256_file(path):
            fail(f"current tri-motor bundle file digest mismatch: {label}")
        if metadata.get("sizeBytes") != path.stat().st_size:
            fail(f"current tri-motor bundle file size mismatch: {label}")


def validate_run_directory(run_dir: Path) -> None:
    run_dir = run_dir.resolve()
    if not run_dir.is_dir():
        fail(f"current tri-motor run directory missing: {run_dir}")
    responses_path = run_dir / RESPONSES_FILENAME
    report_path = run_dir / REPORT_FILENAME
    physical_path = run_dir / PHYSICAL_FILENAME
    bundle_path = run_dir / BUNDLE_FILENAME
    for path in (responses_path, report_path, physical_path, bundle_path):
        if not path.is_file():
            fail(f"current tri-motor evidence file missing: {path}")

    try:
        response_text = responses_path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as error:
        fail(f"cannot load current tri-motor responses: {error}")
    records = parse_jsonl(response_text)
    validate_exact_responses(records)

    report = _load_object(report_path, "current tri-motor evaluation report")
    validate_evaluation_report(report)
    if report.get("runId") != run_dir.name:
        fail("current tri-motor evaluation runId/directory mismatch")

    physical = _load_object(physical_path, "current tri-motor physical report")
    validate_physical_report(physical)

    bundle = _load_object(bundle_path, "current tri-motor bundle")
    _validate_bundle(bundle, run_dir)


def exact_current_response_records() -> list[dict[str, Any]]:
    records = current_contract_response_records()
    for record in records:
        answer = expected_final_answer(record)
        record["finalAnswer"] = answer
        record["strictFormatPassed"] = True
        record["instructionCompliant"] = (
            answer is not None or record["domain"] == "insufficient_information"
        )
        record["claimVerificationPassed"] = (
            True if record["domain"] == "claim_verification" else None
        )
    return records


def self_test() -> int:
    records = exact_current_response_records()
    validate_exact_responses(records)

    corrupted = [dict(record) for record in records]
    corrupted[0]["finalAnswer"] = "999999"
    try:
        validate_exact_responses(corrupted)
    except RunnerError:
        pass
    else:
        fail("incorrect current-contract answer was accepted")

    validate_current_evaluation_report({
        "researchGatePassed": True,
        "productionPromotionAllowed": False,
        "counts": dict(EXPECTED_EVALUATION_COUNTS),
    })
    print("MORIMIL CURRENT TRIMOTOR PHYSICAL EVIDENCE SELF-TEST: PASS")
    return 0


__all__ = [
    "exact_current_response_records", "expected_final_answer", "self_test",
    "validate_evaluation_report", "validate_exact_responses", "validate_run_directory",
]
