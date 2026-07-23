"""Fail-closed provenance and feasibility preflight for Morimil's LOTUS research."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

TOOL_VERSION = "morimil.lotus-feasibility-preflight.v0"
SCHEMA_VERSION = "morimil.lotus.feasibility-preflight.v0"

UPSTREAM_REPOSITORY = "https://github.com/yingfan-bot/lotus.git"
UPSTREAM_COMMIT = "eb77e2f7909c5006f58ff0ad7cd6629b942caa9e"
UPSTREAM_LICENSE = "MIT"

HUB_MODEL_ID = "yingfanbot/gsm-lotus-llama3b"
HUB_MODEL_REVISION = "b392d2cb7aaa73475b93028221523c47f49f66a2"
HUB_MODEL_LICENSE_TAG = "mit"
HUB_BASE_MODEL_ID = "meta-llama/Llama-3.2-3B-Instruct"
HUB_API_URL = (
    f"https://huggingface.co/api/models/{HUB_MODEL_ID}/revision/"
    f"{HUB_MODEL_REVISION}?blobs=true"
)

UPSTREAM_LOOP_ITERATIONS = 6
UPSTREAM_LATENT_WIDTH = 25

REQUIRED_SOURCE_FILES = {
    "LICENSE": "9f1abe28a25b5f6f95c58a81a647de3fa72ff6388d228c59bddd079255709989",
    "README.md": "ab5b17d8d196ab523e990ce7b162999631bbe07fe99a7501ef82ede7954100fc",
    "environment.yml": "f5b4795d39f4ea6098e53341c2134b311b90b938a7aa6a01837a54ca3096bd92",
    "requirements.txt": "5c134f2a5f3cf484ed8eee43a7c79e9eeba1d96358ed9013f9d107507c4846f1",
    "scripts/lotus.py": "a0d03ac726a4063e3e2d033fda7efb47b266ce3166f2bdc9149e077be46bbc5b",
    "scripts/eval.py": "e5c2db7ac48c8f8806842565ca01f2ea736ca585ec08c5f9af048540b6bb0fdc",
    "args/base/lotus.yaml": "18b9061735341aee50aa1fb61f644874e13db9b5dead212671c993ff01cb0ad9",
    "args/gsm8k_lotus_llama3b.yaml": (
        "323078a966c2240ac4cf99ce0c2086adba232273819ef3ba819d3089d8052220"
    ),
}

EXPECTED_HUB_FILES = {
    "model-00001-of-00002.safetensors": {
        "sizeBytes": 4_965_817_528,
        "sha256": "3f1584a6c78ba8d1a3913c2c69b76821af9159bd555f9c8a4a8bb5a4e3ffb346",
    },
    "model-00002-of-00002.safetensors": {
        "sizeBytes": 1_459_729_952,
        "sha256": "e120a6207fcf88e80ae3f269dd49716966aedd2a51db2522b4013c89d1ad75b4",
    },
    "tokenizer.json": {
        "sizeBytes": 17_210_491,
        "sha256": "33b6804858a48cd76783fcefe02ce74635cfca73d6154610484193e4b7a8441a",
    },
}

BLOCKERS = [
    "DESKTOP_REPRODUCTION_NOT_RUN",
    "NON_MATH_TRANSFER_UNPROVEN",
    "BASE_MODEL_LICENSE_CHAIN_REVIEW_REQUIRED",
    "TRAINING_DATA_LICENSE_CHAIN_REVIEW_REQUIRED",
    "GEMMA3N_ADAPTATION_NOT_IMPLEMENTED",
    "ANDROID_LATENT_RUNTIME_NOT_IMPLEMENTED",
    "ANDROID_HIDDEN_STATE_REINJECTION_UNAVAILABLE",
    "PHYSICAL_ARM64_EVIDENCE_MISSING",
]

SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
GIT_COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")


class PreflightError(RuntimeError):
    pass


@dataclass(frozen=True)
class SourceSnapshot:
    repository: str
    commit: str
    clean: bool
    file_sha256: dict[str, str]


@dataclass(frozen=True)
class HubSnapshot:
    model_id: str
    revision: str
    gated: bool
    license_tag: str
    base_model_id: str
    files: dict[str, dict[str, Any]]


def fail(message: str) -> None:
    raise PreflightError(message)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def run_git(checkout: Path, *arguments: str) -> str:
    try:
        result = subprocess.run(
            ["git", "-C", str(checkout), *arguments],
            check=True,
            capture_output=True,
            text=True,
        )
    except (OSError, subprocess.CalledProcessError) as error:
        fail(f"git inspection failed: {error}")
    return result.stdout.strip()


def normalize_repository(value: str) -> str:
    repository = value.strip().removesuffix("/")
    if repository.startswith("git@github.com:"):
        repository = "https://github.com/" + repository.split(":", 1)[1]
    if repository.startswith("http://github.com/"):
        repository = "https://" + repository.removeprefix("http://")
    if not repository.endswith(".git"):
        repository += ".git"
    return repository


def inspect_source_checkout(checkout: Path) -> SourceSnapshot:
    checkout = checkout.resolve()
    if not checkout.is_dir():
        fail(f"LOTUS checkout not found: {checkout}")

    commit = run_git(checkout, "rev-parse", "HEAD")
    if commit != UPSTREAM_COMMIT:
        fail(f"LOTUS commit mismatch: expected {UPSTREAM_COMMIT}, found {commit!r}")

    repository = normalize_repository(run_git(checkout, "remote", "get-url", "origin"))
    if repository != UPSTREAM_REPOSITORY:
        fail(
            "LOTUS origin mismatch: "
            f"expected {UPSTREAM_REPOSITORY}, found {repository!r}"
        )

    clean = not run_git(checkout, "status", "--porcelain")
    if not clean:
        fail("LOTUS checkout must be clean")

    file_hashes: dict[str, str] = {}
    for relative, expected_sha256 in REQUIRED_SOURCE_FILES.items():
        path = checkout / relative
        if not path.is_file():
            fail(f"required LOTUS source file is missing: {relative}")
        actual_sha256 = sha256_file(path)
        if actual_sha256 != expected_sha256:
            fail(
                f"LOTUS source hash mismatch for {relative}: "
                f"expected {expected_sha256}, found {actual_sha256}"
            )
        file_hashes[relative] = actual_sha256

    return SourceSnapshot(
        repository=repository,
        commit=commit,
        clean=clean,
        file_sha256=file_hashes,
    )


def _license_from_payload(payload: dict[str, Any]) -> str:
    card_data = payload.get("cardData")
    if isinstance(card_data, dict):
        value = card_data.get("license")
        if isinstance(value, str) and value:
            return value
    for tag in payload.get("tags") or []:
        if isinstance(tag, str) and tag.startswith("license:"):
            return tag.split(":", 1)[1]
    fail("Hugging Face model license tag is missing")


def _base_model_from_payload(payload: dict[str, Any]) -> str:
    for tag in payload.get("tags") or []:
        if isinstance(tag, str) and tag.startswith("base_model:finetune:"):
            return tag.split("base_model:finetune:", 1)[1]
    fail("Hugging Face base-model lineage tag is missing")


def extract_hub_snapshot(payload: dict[str, Any]) -> HubSnapshot:
    revision = payload.get("sha")
    if revision != HUB_MODEL_REVISION:
        fail(
            "Hugging Face revision mismatch: "
            f"expected {HUB_MODEL_REVISION}, found {revision!r}"
        )

    model_id = payload.get("id") or payload.get("modelId")
    if model_id != HUB_MODEL_ID:
        fail(f"Hugging Face model mismatch: {model_id!r}")

    license_tag = _license_from_payload(payload)
    if license_tag != HUB_MODEL_LICENSE_TAG:
        fail(
            f"Hugging Face license tag mismatch: "
            f"expected {HUB_MODEL_LICENSE_TAG!r}, found {license_tag!r}"
        )

    base_model_id = _base_model_from_payload(payload)
    if base_model_id != HUB_BASE_MODEL_ID:
        fail(
            f"Hugging Face base model mismatch: "
            f"expected {HUB_BASE_MODEL_ID!r}, found {base_model_id!r}"
        )

    siblings = {
        item.get("rfilename"): item
        for item in payload.get("siblings") or []
        if isinstance(item, dict) and isinstance(item.get("rfilename"), str)
    }
    verified_files: dict[str, dict[str, Any]] = {}
    for filename, expected in EXPECTED_HUB_FILES.items():
        item = siblings.get(filename)
        if item is None:
            fail(f"Hugging Face file is missing: {filename}")
        lfs = item.get("lfs")
        if not isinstance(lfs, dict):
            fail(f"Hugging Face LFS metadata is missing: {filename}")
        actual_size = item.get("size", lfs.get("size"))
        actual_sha256 = lfs.get("sha256")
        if actual_size != expected["sizeBytes"]:
            fail(f"Hugging Face file size mismatch: {filename}")
        if actual_sha256 != expected["sha256"]:
            fail(f"Hugging Face file SHA-256 mismatch: {filename}")
        verified_files[filename] = {
            "sizeBytes": actual_size,
            "sha256": f"sha256:{actual_sha256}",
        }

    return HubSnapshot(
        model_id=model_id,
        revision=revision,
        gated=bool(payload.get("gated", False)),
        license_tag=license_tag,
        base_model_id=base_model_id,
        files=verified_files,
    )


def fetch_hub_snapshot(
    opener: Callable[..., Any] = urllib.request.urlopen,
) -> HubSnapshot:
    request = urllib.request.Request(
        HUB_API_URL,
        headers={"User-Agent": f"Morimil-app/{TOOL_VERSION}"},
    )
    try:
        with opener(request, timeout=30) as response:
            payload = json.load(response)
    except (OSError, ValueError) as error:
        fail(f"could not read Hugging Face public metadata: {error}")
    if not isinstance(payload, dict):
        fail("Hugging Face metadata root must be an object")
    return extract_hub_snapshot(payload)


def build_evidence(
    source: SourceSnapshot,
    hub: HubSnapshot,
    *,
    captured_at_utc: str | None = None,
) -> dict[str, Any]:
    validate_source_snapshot(source)
    validate_hub_snapshot(hub)
    captured_at = captured_at_utc or datetime.now(timezone.utc).isoformat().replace(
        "+00:00", "Z"
    )
    return {
        "schemaVersion": SCHEMA_VERSION,
        "status": "research-only",
        "evidenceKind": "lotus-reference-provenance-and-feasibility-preflight",
        "captureToolVersion": TOOL_VERSION,
        "capturedAtUtc": captured_at,
        "upstreamSource": {
            "repository": source.repository,
            "commit": source.commit,
            "cleanCheckout": source.clean,
            "licenseId": UPSTREAM_LICENSE,
            "requiredFileSha256": {
                path: f"sha256:{digest}"
                for path, digest in sorted(source.file_sha256.items())
            },
        },
        "huggingFaceReference": {
            "modelId": hub.model_id,
            "revision": hub.revision,
            "gated": hub.gated,
            "declaredLicenseTag": hub.license_tag,
            "baseModelId": hub.base_model_id,
            "verifiedFiles": hub.files,
            "weightsDownloaded": False,
        },
        "upstreamExperimentProfile": {
            "domain": "gsm8k-mathematical-reasoning",
            "backbone": "Llama-3.2-3B-Instruct",
            "latentWidth": UPSTREAM_LATENT_WIDTH,
            "loopIterations": UPSTREAM_LOOP_ITERATIONS,
            "python": "3.12",
            "pytorch": "2.7.0",
            "cuda": "12.8",
            "profileIsMorimilDefault": False,
        },
        "runtimeFeasibility": {
            "upstreamDesktopLatentLoopPresent": True,
            "upstreamHiddenStateReinjectionPresent": True,
            "upstreamSharedBackboneLoopPresent": True,
            "morimilCurrentAdapterStateKind": "TEXTUAL_CONVERSATION",
            "morimilCurrentAdapterSupportsHiddenStateReinjection": False,
            "morimilCurrentAdapterSharesBackboneInsideLatentLoop": False,
            "gemma3nAdaptationStatus": "NOT_IMPLEMENTED",
            "androidLatentRuntimeStatus": "NOT_IMPLEMENTED",
            "desktopReproductionStatus": "NOT_RUN",
            "nonMathTransferStatus": "UNPROVEN",
            "preflightDecision": "REFERENCE_VERIFIED_EXPERIMENT_BLOCKED",
        },
        "execution": {
            "gpuUsed": False,
            "trainingRun": False,
            "modelInferenceRun": False,
            "remoteTeacherInvoked": False,
            "privateDataUsed": False,
        },
        "authorityBoundary": {
            "memoryWriteCapability": False,
            "identityAuthority": False,
            "genesisAuthority": False,
            "lifecycleAuthority": False,
            "installationAuthority": False,
            "normalRuntimeActivated": False,
        },
        "promotion": {
            "certified": False,
            "signed": False,
            "installed": False,
            "promotionAllowed": False,
            "blockers": list(BLOCKERS),
        },
    }


def validate_source_snapshot(source: SourceSnapshot) -> None:
    if source.repository != UPSTREAM_REPOSITORY:
        fail("source repository mismatch")
    if source.commit != UPSTREAM_COMMIT or not GIT_COMMIT_PATTERN.fullmatch(source.commit):
        fail("source commit mismatch")
    if not source.clean:
        fail("source checkout is not clean")
    if source.file_sha256 != REQUIRED_SOURCE_FILES:
        fail("source file digest set mismatch")


def validate_hub_snapshot(hub: HubSnapshot) -> None:
    if hub.model_id != HUB_MODEL_ID or hub.revision != HUB_MODEL_REVISION:
        fail("Hugging Face reference identity mismatch")
    if hub.gated:
        fail("the pinned public LOTUS reference unexpectedly became gated")
    if hub.license_tag != HUB_MODEL_LICENSE_TAG:
        fail("Hugging Face license tag mismatch")
    if hub.base_model_id != HUB_BASE_MODEL_ID:
        fail("Hugging Face base-model lineage mismatch")
    expected_files = {
        filename: {
            "sizeBytes": metadata["sizeBytes"],
            "sha256": f"sha256:{metadata['sha256']}",
        }
        for filename, metadata in EXPECTED_HUB_FILES.items()
    }
    if hub.files != expected_files:
        fail("Hugging Face file identity mismatch")


def _require_false(section: dict[str, Any], fields: tuple[str, ...]) -> None:
    for field in fields:
        if section.get(field) is not False:
            fail(f"forbidden claim appeared: {field}")


def validate_evidence(value: dict[str, Any]) -> None:
    if value.get("schemaVersion") != SCHEMA_VERSION:
        fail("evidence schema mismatch")
    if value.get("status") != "research-only":
        fail("evidence status mismatch")
    if (
        value.get("evidenceKind")
        != "lotus-reference-provenance-and-feasibility-preflight"
    ):
        fail("evidence kind mismatch")

    source = value.get("upstreamSource")
    hub = value.get("huggingFaceReference")
    profile = value.get("upstreamExperimentProfile")
    feasibility = value.get("runtimeFeasibility")
    execution = value.get("execution")
    boundary = value.get("authorityBoundary")
    promotion = value.get("promotion")
    sections = (source, hub, profile, feasibility, execution, boundary, promotion)
    if not all(isinstance(section, dict) for section in sections):
        fail("evidence sections are missing")

    source_hashes = source.get("requiredFileSha256")
    if not isinstance(source_hashes, dict):
        fail("source file hashes are missing")
    validate_source_snapshot(
        SourceSnapshot(
            repository=source.get("repository"),
            commit=source.get("commit"),
            clean=source.get("cleanCheckout") is True,
            file_sha256={
                path: str(digest).removeprefix("sha256:")
                for path, digest in source_hashes.items()
            },
        )
    )
    validate_hub_snapshot(
        HubSnapshot(
            model_id=hub.get("modelId"),
            revision=hub.get("revision"),
            gated=hub.get("gated") is True,
            license_tag=hub.get("declaredLicenseTag"),
            base_model_id=hub.get("baseModelId"),
            files=hub.get("verifiedFiles"),
        )
    )
    if source.get("licenseId") != UPSTREAM_LICENSE:
        fail("upstream source license mismatch")
    if hub.get("weightsDownloaded") is not False:
        fail("preflight cannot claim or require downloaded weights")

    expected_profile = {
        "domain": "gsm8k-mathematical-reasoning",
        "backbone": "Llama-3.2-3B-Instruct",
        "latentWidth": UPSTREAM_LATENT_WIDTH,
        "loopIterations": UPSTREAM_LOOP_ITERATIONS,
        "python": "3.12",
        "pytorch": "2.7.0",
        "cuda": "12.8",
        "profileIsMorimilDefault": False,
    }
    if profile != expected_profile:
        fail("upstream experiment profile mismatch")

    expected_feasibility = {
        "upstreamDesktopLatentLoopPresent": True,
        "upstreamHiddenStateReinjectionPresent": True,
        "upstreamSharedBackboneLoopPresent": True,
        "morimilCurrentAdapterStateKind": "TEXTUAL_CONVERSATION",
        "morimilCurrentAdapterSupportsHiddenStateReinjection": False,
        "morimilCurrentAdapterSharesBackboneInsideLatentLoop": False,
        "gemma3nAdaptationStatus": "NOT_IMPLEMENTED",
        "androidLatentRuntimeStatus": "NOT_IMPLEMENTED",
        "desktopReproductionStatus": "NOT_RUN",
        "nonMathTransferStatus": "UNPROVEN",
        "preflightDecision": "REFERENCE_VERIFIED_EXPERIMENT_BLOCKED",
    }
    if feasibility != expected_feasibility:
        fail("runtime feasibility claims mismatch")

    _require_false(
        execution,
        (
            "gpuUsed",
            "trainingRun",
            "modelInferenceRun",
            "remoteTeacherInvoked",
            "privateDataUsed",
        ),
    )
    _require_false(
        boundary,
        (
            "memoryWriteCapability",
            "identityAuthority",
            "genesisAuthority",
            "lifecycleAuthority",
            "installationAuthority",
            "normalRuntimeActivated",
        ),
    )
    _require_false(
        promotion,
        ("certified", "signed", "installed", "promotionAllowed"),
    )
    if promotion.get("blockers") != BLOCKERS:
        fail("promotion blocker set mismatch")


def atomic_write_json(path: Path, value: Any) -> None:
    payload = (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode()
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("wb", dir=path.parent, delete=False) as handle:
        temporary = Path(handle.name)
        handle.write(payload)
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temporary, path)


def capture(checkout: Path, output: Path) -> dict[str, Any]:
    evidence = build_evidence(
        inspect_source_checkout(checkout),
        fetch_hub_snapshot(),
    )
    validate_evidence(evidence)
    atomic_write_json(output, evidence)
    return evidence


def check(evidence_path: Path, checkout: Path | None = None) -> dict[str, Any]:
    try:
        value = json.loads(evidence_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"could not read evidence: {error}")
    if not isinstance(value, dict):
        fail("evidence root must be an object")
    validate_evidence(value)
    if checkout is not None:
        observed = inspect_source_checkout(checkout)
        recorded = value["upstreamSource"]
        if observed.commit != recorded["commit"]:
            fail("checked LOTUS commit differs from evidence")
        if {
            path: f"sha256:{digest}"
            for path, digest in sorted(observed.file_sha256.items())
        } != recorded["requiredFileSha256"]:
            fail("checked LOTUS source files differ from evidence")
    return value


def _self_test_snapshots() -> tuple[SourceSnapshot, HubSnapshot]:
    source = SourceSnapshot(
        repository=UPSTREAM_REPOSITORY,
        commit=UPSTREAM_COMMIT,
        clean=True,
        file_sha256=dict(REQUIRED_SOURCE_FILES),
    )
    hub = HubSnapshot(
        model_id=HUB_MODEL_ID,
        revision=HUB_MODEL_REVISION,
        gated=False,
        license_tag=HUB_MODEL_LICENSE_TAG,
        base_model_id=HUB_BASE_MODEL_ID,
        files={
            filename: {
                "sizeBytes": metadata["sizeBytes"],
                "sha256": f"sha256:{metadata['sha256']}",
            }
            for filename, metadata in EXPECTED_HUB_FILES.items()
        },
    )
    return source, hub


def self_test() -> int:
    source, hub = _self_test_snapshots()
    evidence = build_evidence(
        source,
        hub,
        captured_at_utc="2026-07-23T00:00:00Z",
    )
    validate_evidence(evidence)

    forged = json.loads(json.dumps(evidence))
    forged["runtimeFeasibility"]["morimilCurrentAdapterSupportsHiddenStateReinjection"] = True
    try:
        validate_evidence(forged)
    except PreflightError:
        pass
    else:
        fail("self-test accepted a forged Android hidden-state capability")

    forged = json.loads(json.dumps(evidence))
    forged["promotion"]["promotionAllowed"] = True
    try:
        validate_evidence(forged)
    except PreflightError:
        pass
    else:
        fail("self-test accepted a forged promotion")

    print("MORIMIL LOTUS FEASIBILITY PREFLIGHT SELF-TEST: PASS")
    return 0


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    commands = root.add_subparsers(dest="command", required=True)

    capture_command = commands.add_parser("capture")
    capture_command.add_argument("checkout", type=Path)
    capture_command.add_argument("--output", type=Path, required=True)

    check_command = commands.add_parser("check")
    check_command.add_argument("evidence", type=Path)
    check_command.add_argument("--checkout", type=Path)

    commands.add_parser("self-test")
    return root


def main(argv: list[str] | None = None) -> int:
    arguments = parser().parse_args(argv)
    try:
        if arguments.command == "capture":
            value = capture(arguments.checkout, arguments.output)
            print(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2))
            return 0
        if arguments.command == "check":
            value = check(arguments.evidence, arguments.checkout)
            print(
                "MORIMIL LOTUS FEASIBILITY PREFLIGHT: "
                f"{value['runtimeFeasibility']['preflightDecision']}"
            )
            return 0
        if arguments.command == "self-test":
            return self_test()
        fail(f"unsupported command: {arguments.command}")
    except PreflightError as error:
        print(f"MORIMIL LOTUS FEASIBILITY PREFLIGHT: FAIL: {error}", file=sys.stderr)
        return 1
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
