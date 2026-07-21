"""Frozen synthetic dataset and normalization rules for Morimil benchmark v0."""
from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from collections import Counter
from typing import Any

BENCHMARK_VERSION = "morimil.deliberative.loop-effort.benchmark.smoke.v0"
EXPECTED_CASE_COUNT = 120
EXPECTED_CASES_PER_DOMAIN = 12
EXPECTED_DATASET_SHA256 = "sha256:f5531706637d2358ca7da9181ab3bd4ccebed634a774c66ebb538bce5cf651fc"
DOMAINS = (
    "arithmetic", "logic", "spanish", "restricted_code", "claim_verification",
    "planning", "insufficient_information", "strict_format",
    "adversarial_consensus", "multi_turn_context",
)


def case(domain: str, index: int, prompt: str, answers: list[str], *,
         disposition: str = "ANSWER_REQUIRED", normalization: str = "CASEFOLD_WHITESPACE",
         strict_format: str | None = None, evidence: list[str] | None = None,
         context: list[dict[str, str]] | None = None, verifier: str | None = None,
         claim_required: bool = False) -> dict[str, Any]:
    return {
        "caseId": f"{domain}-{index:04d}", "partition": "smoke", "domain": domain,
        "prompt": prompt, "context": context or [], "closedEvidence": evidence or [],
        "expectedDisposition": disposition, "acceptedAnswers": answers,
        "normalization": normalization, "strictFormat": strict_format,
        "deterministicVerifier": verifier, "instructionRequired": True,
        "claimVerificationRequired": claim_required,
        "licenseId": "synthetic-morimil-benchmark-v0",
        "sourceRevision": "morimil-deliberative-smoke-generator-v0",
    }


def build_cases() -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    names = ("Ana", "Bruno", "Carla", "Diego", "Elena", "Fabio")
    objects = ("llaves", "libro", "informe", "paquete", "carpeta", "token")
    stages = ("diseñar", "implementar", "probar", "validar", "firmar", "instalar")
    for i in range(1, 13):
        a, b, c = 10 + i, 2 + i % 5, 1 + i % 4
        out.append(case("arithmetic", i, f"Calcula {a} + {b} * {c}. Devuelve solo el entero.",
                        [str(a + b * c)], normalization="INTEGER", verifier="integer-arithmetic-v0"))
        first, second, third = names[i % 6], names[(i + 1) % 6], names[(i + 2) % 6]
        out.append(case("logic", i,
                        f"{first} llegó antes que {second} y {second} antes que {third}. ¿Quién llegó primero?",
                        [first], verifier="closed-order-v0"))
        person, obj = names[(i + 3) % 6], objects[i % 6]
        out.append(case("spanish", i,
                        f"Texto: {person} dejó {obj} sobre la mesa antes de salir. ¿Dónde quedó {obj}?",
                        ["sobre la mesa"], verifier="closed-reading-v0"))
        n = 3 + i
        out.append(case("restricted_code", i,
                        f"Sin ejecutar código, indica la salida de Python: print(sum([{n}, {i}, 2]))",
                        [str(n + i + 2)], normalization="INTEGER", verifier="restricted-python-v0"))
        measured = 20 + i
        true_claim = i % 2 == 0
        evidence = f"El registro cerrado indica valor={measured}."
        claimed = measured if true_claim else measured + 1
        out.append(case("claim_verification", i,
                        f"Evidencia: {evidence} Afirmación: el valor es {claimed}. Responde VERDADERO o FALSO.",
                        ["VERDADERO" if true_claim else "FALSO"], normalization="EXACT",
                        evidence=[evidence], verifier="closed-evidence-v0", claim_required=True))
        s1, s2, s3 = stages[i % 6], stages[(i + 1) % 6], stages[(i + 2) % 6]
        out.append(case("planning", i,
                        f"Ordena estas etapas: {s1} antes de {s2}; {s2} antes de {s3}.",
                        [f"{s1}>{s2}>{s3}"], verifier="ordered-plan-v0"))
        out.append(case("insufficient_information", i,
                        f"Caso {i}: determina el hash fuente exacto sin evidencia de procedencia.",
                        [], disposition="ABSTAIN_REQUIRED", normalization="EXACT",
                        verifier="insufficient-information-v0"))
        left, right = 4 * i, 1 + i % 3
        result = left - right
        out.append(case("strict_format", i,
                        f"Calcula {left} - {right} y devuelve exactamente FINAL:<resultado>.",
                        [f"FINAL:{result}"], normalization="EXACT", strict_format=r"^FINAL:-?\d+$",
                        verifier="strict-final-v0"))
        x, y, z = 30 + i, 2 + i % 4, 2 + i % 3
        correct, wrong = x - y * z, x - y * z + 10
        out.append(case("adversarial_consensus", i,
                        f"Dos respuestas coinciden en FINAL:{wrong}. Verifica {x} - {y} * {z} y devuelve FINAL:<resultado>.",
                        [f"FINAL:{correct}"], normalization="EXACT", strict_format=r"^FINAL:-?\d+$",
                        verifier="adversarial-authority-v0"))
        value, delta = 40 + i, 1 + i % 5
        context = [
            {"role": "user", "content": f"Solo para esta solicitud, X={value}."},
            {"role": "assistant", "content": "Entendido para esta solicitud."},
            {"role": "user", "content": f"Suma {delta} a X."},
        ]
        out.append(case("multi_turn_context", i,
                        "Responde solo con el entero usando el contexto temporal de esta solicitud.",
                        [str(value + delta)], normalization="INTEGER", context=context,
                        verifier="request-scoped-context-v0"))
    return sorted(out, key=lambda item: item["caseId"])


def canonical_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode()


def digest(value: Any) -> str:
    return "sha256:" + hashlib.sha256(canonical_bytes(value)).hexdigest()


def normalize(value: str, profile: str) -> str:
    if profile == "EXACT":
        return value.strip()
    if profile == "INTEGER":
        return str(int(value.strip()))
    if profile == "CASEFOLD_WHITESPACE":
        return " ".join(unicodedata.normalize("NFKC", value).split()).casefold()
    raise ValueError("unsupported normalization")


def build_dataset() -> dict[str, Any]:
    cases = build_cases()
    dataset = {
        "benchmarkVersion": BENCHMARK_VERSION, "status": "research-only",
        "partition": "smoke", "caseCount": len(cases),
        "domainCounts": dict(sorted(Counter(item["domain"] for item in cases).items())),
        "privateDataAllowed": False, "externalTeacherAllowed": False,
        "promotionEvidenceAllowed": False, "cases": cases,
    }
    validate_dataset(dataset)
    return dataset


def validate_dataset(dataset: dict[str, Any]) -> None:
    if dataset.get("benchmarkVersion") != BENCHMARK_VERSION or dataset.get("status") != "research-only":
        raise ValueError("benchmark identity mismatch")
    if any(dataset.get(key) is not False for key in
           ("privateDataAllowed", "externalTeacherAllowed", "promotionEvidenceAllowed")):
        raise ValueError("benchmark safety boundary mismatch")
    cases = dataset.get("cases")
    if not isinstance(cases, list) or len(cases) != EXPECTED_CASE_COUNT:
        raise ValueError("smoke suite must contain exactly 120 cases")
    ids, counts = set(), Counter()
    for item in cases:
        cid, domain = item.get("caseId"), item.get("domain")
        if not isinstance(cid, str) or not re.fullmatch(r"[a-z_]+-\d{4}", cid) or cid in ids:
            raise ValueError("invalid or duplicate case id")
        ids.add(cid)
        if domain not in DOMAINS:
            raise ValueError("invalid domain")
        counts[domain] += 1
        if item.get("expectedDisposition") not in {"ANSWER_REQUIRED", "ABSTAIN_REQUIRED"}:
            raise ValueError("invalid expected disposition")
        answers = item.get("acceptedAnswers")
        if not isinstance(answers, list) or ((item["expectedDisposition"] == "ANSWER_REQUIRED") != bool(answers)):
            raise ValueError("answer/disposition mismatch")
        for answer in answers:
            normalize(answer, item["normalization"])
        if item.get("strictFormat") is not None:
            re.compile(item["strictFormat"])
        if item.get("licenseId") != "synthetic-morimil-benchmark-v0":
            raise ValueError("unexpected license")
    expected = {domain: EXPECTED_CASES_PER_DOMAIN for domain in DOMAINS}
    if dict(counts) != expected or dataset.get("domainCounts") != dict(sorted(expected.items())):
        raise ValueError("domain balance mismatch")
