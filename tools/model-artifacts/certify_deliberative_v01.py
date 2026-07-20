#!/usr/bin/env python3
"""Offline certifier for Morimil deliberative artifact v0.1.

This tool never downloads, installs, signs, or runs a model. It consumes local
inputs, computes reproducible digests, emits the exact manifest expected by the
Android verifier, and can independently verify a previously emitted manifest.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import tempfile
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

CERTIFIER_VERSION = "morimil.deliberative.certifier.v0.1"
REPORT_SCHEMA = "morimil.deliberative.certification.report.v0.1"
SNAPSHOT_DIGEST_PROFILE = "morimil.source.snapshot.tree.v0.1"

MANIFEST_SCHEMA = "morimil.deliberative.artifact.manifest.v0.2"
SIGNED_DOMAIN = "morimil.deliberative.artifact.signature.v0.2"
CONTRACT_VERSION = "morimil.deliberative.artifact.contract.v0.1"
ARTIFACT_VERSION = "morimil-deliberative-v0.1"
ARTIFACT_FILENAME = "morimil-deliberative-v0.1.litertlm"
FORMAT_ID = "litertlm.v1"
RUNTIME_ABI = "litertlm.kotlin.android.v0.14.0"
ARCHITECTURE_ID = "google.gemma3.text.1b.it"
TOKENIZER_ID = "google.gemma3.tokenizer"
CONTEXT_WINDOW_TOKENS = 4096
QUANTIZATION_PROFILE = "litertlm.int4.per-channel"
MODALITY = "text-only"
EXECUTION_BACKEND = "cpu"
DELIBERATION_PROFILE = "morimil.request-scoped.textual-recurrence.v0"
SOURCE_MODEL_ID = "google/gemma-3-1b-it"
LICENSE_ID = "gemma"
BLUEPRINT_VERSION = "morimil.reasoning_growth.v1"
TECHNIQUES = (
    "ADAPTIVE_REASONING_EFFORT",
    "DIRECT_LANGUAGE_MODEL_HEAD",
    "LOOPED_LATENT_DEPTH",
)

MAX_ARTIFACT_BYTES = 4 * 1024 * 1024 * 1024
MAX_SNAPSHOT_FILES = 100_000
MAX_SNAPSHOT_BYTES = 50 * 1024 * 1024 * 1024
BUFFER_SIZE = 1024 * 1024

SHA256_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
REVISION_RE = re.compile(r"^[0-9a-f]{40}$")

RECIPE_EXPECTED: Mapping[str, Any] = {
    "schemaVersion": "morimil.deliberative.conversion.recipe.v0.1",
    "artifactVersion": ARTIFACT_VERSION,
    "sourceModelId": SOURCE_MODEL_ID,
    "architectureId": ARCHITECTURE_ID,
    "tokenizerId": TOKENIZER_ID,
    "formatId": FORMAT_ID,
    "runtimeAbi": RUNTIME_ABI,
    "sdkVersion": "0.14.0",
    "contextWindowTokens": CONTEXT_WINDOW_TOKENS,
    "quantizationProfile": QUANTIZATION_PROFILE,
    "modality": MODALITY,
    "executionBackend": EXECUTION_BACKEND,
    "snapshotDigestProfile": SNAPSHOT_DIGEST_PROFILE,
    "acceptedInputKinds": [
        "official-prebuilt-litertlm",
        "reproducibly-converted-litertlm",
    ],
    "acquisitionPolicy": "manual-license-gated-local-input-only",
    "networkPolicy": "certifier-offline-no-download",
    "signingPolicy": "separate-genesis-ed25519-envelope-required",
    "conversionTranscriptRequired": True,
}


class CertificationError(ValueError):
    """Fail-closed certification error."""


@dataclass(frozen=True)
class SnapshotResult:
    digest: str
    file_count: int
    total_bytes: int


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise CertificationError(message)


def _require_nfc(value: str, label: str) -> None:
    _require(value == unicodedata.normalize("NFC", value), f"{label}_not_nfc")


def _frame(value: str) -> bytes:
    _require_nfc(value, "hash_field")
    payload = value.encode("utf-8")
    return str(len(payload)).encode("ascii") + b":" + payload + b"\n"


def hash_fields(domain: str, fields: Iterable[str]) -> str:
    digest = hashlib.sha256()
    digest.update(_frame(domain))
    for field in fields:
        digest.update(_frame(field))
    return "sha256:" + digest.hexdigest()


def sha256_file(path: Path) -> str:
    _require(path.is_file(), f"missing_file:{path}")
    _require(not path.is_symlink(), f"symlink_not_allowed:{path}")
    before = path.stat()
    digest = hashlib.sha256()
    total = 0
    with path.open("rb") as stream:
        while True:
            block = stream.read(BUFFER_SIZE)
            if not block:
                break
            total += len(block)
            digest.update(block)
    after = path.stat()
    _require(total == before.st_size == after.st_size, f"file_changed_while_hashing:{path}")
    _require(before.st_mtime_ns == after.st_mtime_ns, f"file_changed_while_hashing:{path}")
    return "sha256:" + digest.hexdigest()


def _safe_relative_path(value: str) -> str:
    _require_nfc(value, "relative_path")
    _require(value != "", "relative_path_empty")
    _require("\x00" not in value, "relative_path_nul")
    _require("\\" not in value, "relative_path_backslash")
    _require(not value.startswith("/"), "relative_path_absolute")
    parts = value.split("/")
    _require(all(part not in ("", ".", "..") for part in parts), "relative_path_invalid_segment")
    _require(".git" not in parts, "git_metadata_not_allowed")
    return value


def _utf8_sort_key(value: str) -> bytes:
    _require_nfc(value, "sort_value")
    return value.encode("utf-8")


def canonical_snapshot_digest(root: Path) -> SnapshotResult:
    _require(not root.is_symlink(), "source_snapshot_symlink_not_allowed")
    root = root.resolve(strict=True)
    _require(root.is_dir(), "source_snapshot_not_directory")

    entries: list[tuple[str, int, str]] = []
    total_bytes = 0

    def fail_walk(error: OSError) -> None:
        raise CertificationError(f"source_snapshot_walk_failed:{error}") from error

    for current_root, dir_names, file_names in os.walk(
        root,
        followlinks=False,
        onerror=fail_walk,
    ):
        current = Path(current_root)

        for name in list(dir_names):
            candidate = current / name
            _require(not candidate.is_symlink(), f"snapshot_symlink_not_allowed:{candidate}")
            _require(name != ".git", "git_metadata_not_allowed")

        for name in file_names:
            candidate = current / name
            _require(not candidate.is_symlink(), f"snapshot_symlink_not_allowed:{candidate}")
            _require(candidate.is_file(), f"snapshot_non_file_entry:{candidate}")
            relative = candidate.relative_to(root).as_posix()
            relative = _safe_relative_path(relative)
            size = candidate.stat().st_size
            _require(size >= 0, "snapshot_negative_file_size")
            total_bytes += size
            _require(total_bytes <= MAX_SNAPSHOT_BYTES, "source_snapshot_too_large")
            entries.append((relative, size, sha256_file(candidate)))
            _require(len(entries) <= MAX_SNAPSHOT_FILES, "source_snapshot_too_many_files")

    _require(entries, "source_snapshot_empty")
    entries.sort(key=lambda item: _utf8_sort_key(item[0]))

    fields: list[str] = [str(len(entries))]
    for relative, size, digest in entries:
        fields.extend((relative, str(size), digest))

    return SnapshotResult(
        digest=hash_fields(SNAPSHOT_DIGEST_PROFILE, fields),
        file_count=len(entries),
        total_bytes=total_bytes,
    )


def _reject_duplicate_pairs(pairs: Sequence[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise CertificationError(f"duplicate_json_key:{key}")
        result[key] = value
    return result


def load_json_object(path: Path) -> dict[str, Any]:
    _require(path.is_file(), f"missing_json_file:{path}")
    _require(not path.is_symlink(), f"symlink_not_allowed:{path}")
    try:
        with path.open("r", encoding="utf-8") as stream:
            value = json.load(stream, object_pairs_hook=_reject_duplicate_pairs)
    except (json.JSONDecodeError, UnicodeDecodeError) as error:
        raise CertificationError(f"invalid_json:{path.name}:{error}") from error
    _require(isinstance(value, dict), f"json_root_not_object:{path.name}")
    return value


def validate_recipe(path: Path) -> str:
    recipe = load_json_object(path)
    _require(recipe == RECIPE_EXPECTED, "conversion_recipe_contract_mismatch")
    return sha256_file(path)


def _validate_revision(value: str, *, allow_placeholder: bool = False) -> str:
    _require(bool(REVISION_RE.fullmatch(value)), "source_revision_invalid")
    if not allow_placeholder:
        _require(value != "0" * 40, "source_revision_placeholder_forbidden")
    return value


def _validate_tokenizer(root: Path, relative_path: str) -> tuple[Path, str]:
    relative_path = _safe_relative_path(relative_path)
    root = root.resolve(strict=True)
    candidate = root / Path(*relative_path.split("/"))
    _require(not candidate.is_symlink(), "tokenizer_symlink_not_allowed")
    tokenizer = candidate.resolve(strict=True)
    try:
        tokenizer.relative_to(root)
    except ValueError as error:
        raise CertificationError("tokenizer_outside_source_snapshot") from error
    _require(tokenizer.is_file(), "tokenizer_not_file")
    return tokenizer, sha256_file(tokenizer)


def _artifact_facts(path: Path) -> tuple[int, str]:
    _require(not path.is_symlink(), "artifact_symlink_not_allowed")
    path = path.resolve(strict=True)
    _require(path.is_file(), "artifact_not_file")
    _require(path.name == ARTIFACT_FILENAME, "artifact_filename_mismatch")
    size = path.stat().st_size
    _require(size in range(1, MAX_ARTIFACT_BYTES + 1), "artifact_size_out_of_policy")
    return size, sha256_file(path)


def build_manifest(
    *,
    artifact_sha256: str,
    artifact_size_bytes: int,
    tokenizer_sha256: str,
    source_revision: str,
    source_snapshot_sha256: str,
    conversion_recipe_sha256: str,
) -> dict[str, Any]:
    for label, value in (
        ("artifact_sha256", artifact_sha256),
        ("tokenizer_sha256", tokenizer_sha256),
        ("source_snapshot_sha256", source_snapshot_sha256),
        ("conversion_recipe_sha256", conversion_recipe_sha256),
    ):
        _require(bool(SHA256_RE.fullmatch(value)), f"{label}_invalid")
    _validate_revision(source_revision, allow_placeholder=True)
    _require(artifact_size_bytes in range(1, MAX_ARTIFACT_BYTES + 1), "artifact_size_out_of_policy")

    return {
        "schemaVersion": MANIFEST_SCHEMA,
        "contractVersion": CONTRACT_VERSION,
        "artifactVersion": ARTIFACT_VERSION,
        "artifactSha256": artifact_sha256,
        "artifactSizeBytes": artifact_size_bytes,
        "formatId": FORMAT_ID,
        "runtimeAbi": RUNTIME_ABI,
        "architectureId": ARCHITECTURE_ID,
        "tokenizerId": TOKENIZER_ID,
        "tokenizerSha256": tokenizer_sha256,
        "contextWindowTokens": CONTEXT_WINDOW_TOKENS,
        "quantizationProfile": QUANTIZATION_PROFILE,
        "modality": MODALITY,
        "executionBackend": EXECUTION_BACKEND,
        "deliberationProfile": DELIBERATION_PROFILE,
        "sourceModelId": SOURCE_MODEL_ID,
        "sourceModelRevision": source_revision,
        "sourceModelSnapshotSha256": source_snapshot_sha256,
        "conversionRecipeSha256": conversion_recipe_sha256,
        "licenseId": LICENSE_ID,
        "blueprintVersion": BLUEPRINT_VERSION,
        "techniques": list(TECHNIQUES),
    }


def manifest_digest(manifest: Mapping[str, Any]) -> str:
    techniques = manifest.get("techniques")
    _require(isinstance(techniques, list), "manifest_techniques_not_array")
    _require(all(isinstance(item, str) for item in techniques), "manifest_technique_not_string")
    ordered_techniques = sorted(techniques, key=_utf8_sort_key)

    field_names = (
        "schemaVersion",
        "contractVersion",
        "artifactVersion",
        "artifactSha256",
        "artifactSizeBytes",
        "formatId",
        "runtimeAbi",
        "architectureId",
        "tokenizerId",
        "tokenizerSha256",
        "contextWindowTokens",
        "quantizationProfile",
        "modality",
        "executionBackend",
        "deliberationProfile",
        "sourceModelId",
        "sourceModelRevision",
        "sourceModelSnapshotSha256",
        "conversionRecipeSha256",
        "licenseId",
        "blueprintVersion",
    )
    fields: list[str] = []
    for name in field_names:
        _require(name in manifest, f"manifest_field_missing:{name}")
        value = manifest[name]
        _require(isinstance(value, (str, int)) and not isinstance(value, bool),
                 f"manifest_field_type_invalid:{name}")
        fields.append(str(value))
    fields.append(str(len(ordered_techniques)))
    fields.extend(ordered_techniques)
    return hash_fields(SIGNED_DOMAIN, fields)


def expected_manifest_from_inputs(
    *,
    artifact: Path,
    source_snapshot: Path,
    tokenizer_relative_path: str,
    recipe: Path,
    source_revision: str,
) -> tuple[dict[str, Any], SnapshotResult]:
    source_revision = _validate_revision(source_revision)
    artifact_size, artifact_digest = _artifact_facts(artifact)
    snapshot = canonical_snapshot_digest(source_snapshot)
    _, tokenizer_digest = _validate_tokenizer(source_snapshot, tokenizer_relative_path)
    recipe_digest = validate_recipe(recipe)
    manifest = build_manifest(
        artifact_sha256=artifact_digest,
        artifact_size_bytes=artifact_size,
        tokenizer_sha256=tokenizer_digest,
        source_revision=source_revision,
        source_snapshot_sha256=snapshot.digest,
        conversion_recipe_sha256=recipe_digest,
    )
    return manifest, snapshot


def _atomic_write_json(path: Path, value: Mapping[str, Any], *, overwrite: bool) -> None:
    _require(not path.is_symlink(), f"output_symlink_not_allowed:{path}")
    parent = path.parent.resolve(strict=True)
    _require(parent.is_dir(), f"output_parent_missing:{parent}")
    path = parent / path.name
    _require(overwrite or not path.exists(), f"output_exists:{path}")
    payload = json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False) + "\n"

    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.",
        suffix=".tmp",
        dir=path.parent,
        text=True,
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


def _is_within(path: Path, parent: Path) -> bool:
    try:
        path.resolve(strict=False).relative_to(parent.resolve(strict=True))
        return True
    except ValueError:
        return False


def build_report(
    *,
    digest: str,
    snapshot: SnapshotResult,
    tokenizer_relative_path: str,
    recipe_file_name: str,
) -> dict[str, Any]:
    return {
        "schemaVersion": REPORT_SCHEMA,
        "certifierVersion": CERTIFIER_VERSION,
        "manifestDigest": digest,
        "snapshotDigestProfile": SNAPSHOT_DIGEST_PROFILE,
        "sourceSnapshotFileCount": snapshot.file_count,
        "sourceSnapshotTotalBytes": snapshot.total_bytes,
        "tokenizerRelativePath": _safe_relative_path(tokenizer_relative_path),
        "artifactFileName": ARTIFACT_FILENAME,
        "recipeFileName": recipe_file_name,
        "licenseAcceptanceAttested": True,
        "signatureRequired": True,
        "signed": False,
    }


def certify(args: argparse.Namespace) -> int:
    _require(args.license_accepted, "explicit_license_attestation_required")
    _require(args.manifest_out != args.report_out, "output_paths_must_differ")
    _require(not _is_within(args.artifact, args.source_snapshot),
             "artifact_must_not_be_inside_source_snapshot")
    _require(not _is_within(args.recipe, args.source_snapshot),
             "recipe_must_not_be_inside_source_snapshot")
    _require(not _is_within(args.manifest_out, args.source_snapshot),
             "manifest_output_must_not_be_inside_source_snapshot")
    _require(not _is_within(args.report_out, args.source_snapshot),
             "report_output_must_not_be_inside_source_snapshot")
    manifest, snapshot = expected_manifest_from_inputs(
        artifact=args.artifact,
        source_snapshot=args.source_snapshot,
        tokenizer_relative_path=args.tokenizer_relative_path,
        recipe=args.recipe,
        source_revision=args.source_revision,
    )
    digest = manifest_digest(manifest)
    report = build_report(
        digest=digest,
        snapshot=snapshot,
        tokenizer_relative_path=args.tokenizer_relative_path,
        recipe_file_name=args.recipe.name,
    )
    _atomic_write_json(args.manifest_out, manifest, overwrite=args.overwrite)
    _atomic_write_json(args.report_out, report, overwrite=args.overwrite)
    print(digest)
    return 0


def verify(args: argparse.Namespace) -> int:
    expected, snapshot = expected_manifest_from_inputs(
        artifact=args.artifact,
        source_snapshot=args.source_snapshot,
        tokenizer_relative_path=args.tokenizer_relative_path,
        recipe=args.recipe,
        source_revision=args.source_revision,
    )
    actual = load_json_object(args.manifest)
    _require(actual == expected, "manifest_does_not_match_local_inputs")
    digest = manifest_digest(actual)

    if args.report is not None:
        report = load_json_object(args.report)
        expected_report = build_report(
            digest=digest,
            snapshot=snapshot,
            tokenizer_relative_path=args.tokenizer_relative_path,
            recipe_file_name=args.recipe.name,
        )
        _require(report == expected_report, "report_does_not_match_local_inputs")

    print(digest)
    return 0


def _path(value: str) -> Path:
    return Path(value)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Offline certifier for morimil-deliberative-v0.1.litertlm"
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--artifact", type=_path, required=True)
    common.add_argument("--source-snapshot", type=_path, required=True)
    common.add_argument("--tokenizer-relative-path", required=True)
    common.add_argument("--recipe", type=_path, required=True)
    common.add_argument("--source-revision", required=True)

    certify_parser = subparsers.add_parser("certify", parents=[common])
    certify_parser.add_argument("--manifest-out", type=_path, required=True)
    certify_parser.add_argument("--report-out", type=_path, required=True)
    certify_parser.add_argument(
        "--license-accepted",
        action="store_true",
        help="Attest that the operator accepted the applicable Gemma license.",
    )
    certify_parser.add_argument("--overwrite", action="store_true")
    certify_parser.set_defaults(handler=certify)

    verify_parser = subparsers.add_parser("verify", parents=[common])
    verify_parser.add_argument("--manifest", type=_path, required=True)
    verify_parser.add_argument("--report", type=_path)
    verify_parser.set_defaults(handler=verify)

    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return int(args.handler(args))
    except (CertificationError, FileNotFoundError, OSError) as error:
        print(f"certification_failed:{error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
