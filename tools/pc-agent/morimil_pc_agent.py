#!/usr/bin/env python3
"""Morimil PC companion agent.

Vertical v1: LAN file audit, read-only.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

AGENT_NAME = "morimil_pc_agent"
CAPABILITY_FILE_AUDIT = "file_audit.read_only"
MAX_REQUEST_BYTES = 64 * 1024
DEFAULT_MAX_FILES = 50_000
DEFAULT_MAX_BYTES_PER_FILE = 10 * 1024 * 1024


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def canonical_json(value: Any) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True)


def read_json_body(handler: BaseHTTPRequestHandler) -> dict[str, Any]:
    content_length = int(handler.headers.get("Content-Length", "0") or "0")
    if content_length <= 0:
        raise ValueError("missing json body")
    if content_length > MAX_REQUEST_BYTES:
        raise ValueError("request body too large")
    parsed = json.loads(handler.rfile.read(content_length).decode("utf-8"))
    if not isinstance(parsed, dict):
        raise ValueError("json body must be an object")
    return parsed


def extension_of(path: Path) -> str:
    suffix = path.suffix.lower()
    return suffix if suffix else "<none>"


def file_digest(path: Path, max_bytes_per_file: int) -> str | None:
    if path.stat().st_size > max_bytes_per_file:
        return None
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 256), b""):
            digest.update(chunk)
    return digest.hexdigest()


class FileAuditor:
    def __init__(self, root: Path) -> None:
        self.root = root.resolve()

    def run(self, max_files: int, max_bytes_per_file: int) -> dict[str, Any]:
        files_seen = 0
        files_hashed = 0
        total_bytes = 0
        skipped_large = 0
        extension_counts: dict[str, int] = {}
        largest_files: list[dict[str, Any]] = []
        aggregate = hashlib.sha256()

        for path in self.root.rglob("*"):
            if not path.is_file():
                continue
            resolved = path.resolve()
            if self.root not in resolved.parents and resolved != self.root:
                continue
            files_seen += 1
            if files_seen > max_files:
                break

            rel = resolved.relative_to(self.root).as_posix()
            size = resolved.stat().st_size
            total_bytes += size
            ext = extension_of(resolved)
            extension_counts[ext] = extension_counts.get(ext, 0) + 1

            largest_files.append({"path": rel, "bytes": size})
            largest_files = sorted(largest_files, key=lambda item: item["bytes"], reverse=True)[:10]

            digest = file_digest(resolved, max_bytes_per_file)
            if digest is None:
                skipped_large += 1
                aggregate.update(f"{rel}:{size}:large".encode("utf-8"))
            else:
                files_hashed += 1
                aggregate.update(f"{rel}:{size}:{digest}".encode("utf-8"))

        top_extensions = [
            {"extension": key, "count": value}
            for key, value in sorted(extension_counts.items(), key=lambda item: item[1], reverse=True)[:20]
        ]
        report = {
            "root_name": self.root.name,
            "file_count": files_seen,
            "files_hashed": files_hashed,
            "total_bytes": total_bytes,
            "skipped_large_files": skipped_large,
            "max_files_reached": files_seen > max_files,
            "top_extensions": top_extensions,
            "largest_files": largest_files,
            "project_fingerprint": aggregate.hexdigest(),
        }
        report["artifact_hash"] = sha256_text(canonical_json(report))
        return report


class AgentState:
    def __init__(self, root: Path, pairing_key: str, target_root_id: str) -> None:
        self.root = root.resolve()
        self.pairing_key = pairing_key
        self.target_root_id = target_root_id
        self.auditor = FileAuditor(self.root)


class Handler(BaseHTTPRequestHandler):
    state: AgentState

    def log_message(self, format: str, *args: Any) -> None:  # noqa: A003
        return

    def send_json(self, status: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, indent=2, sort_keys=True).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def paired(self) -> bool:
        return self.headers.get("X-Morimil-Pairing") == self.state.pairing_key

    def do_GET(self) -> None:  # noqa: N802
        if self.path == "/health":
            self.send_json(200, {"status": "ok", "agent": AGENT_NAME})
            return
        if self.path == "/capabilities":
            if not self.paired():
                self.send_json(401, {"status": "error", "error": "not_paired"})
                return
            self.send_json(200, {"status": "ok", "capabilities": [CAPABILITY_FILE_AUDIT]})
            return
        self.send_json(404, {"status": "error", "error": "not_found"})

    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/file-audit":
            self.send_json(404, {"status": "error", "error": "not_found"})
            return
        if not self.paired():
            self.send_json(401, {"status": "error", "error": "not_paired"})
            return
        try:
            response = self.handle_file_audit(read_json_body(self))
            self.send_json(200, response)
        except ValueError as error:
            self.send_json(400, {"status": "error", "error": str(error)})
        except Exception as error:  # pragma: no cover
            self.send_json(500, {"status": "error", "error": f"internal_error: {type(error).__name__}"})

    def handle_file_audit(self, request: dict[str, Any]) -> dict[str, Any]:
        request_id = str(request.get("request_id", "")).strip()
        capability = str(request.get("capability", "")).strip()
        target_root_id = str(request.get("target_root_id", "")).strip()
        include_contents = bool(request.get("include_contents", False))
        max_files = int(request.get("max_files", DEFAULT_MAX_FILES))
        max_bytes_per_file = int(request.get("max_bytes_per_file", DEFAULT_MAX_BYTES_PER_FILE))

        if not request_id:
            raise ValueError("missing request_id")
        if capability != CAPABILITY_FILE_AUDIT:
            raise ValueError("unsupported capability")
        if target_root_id != self.state.target_root_id:
            raise ValueError("target_root_id not allowed")
        if include_contents:
            raise ValueError("file_audit.read_only does not return file contents")
        if max_files < 1 or max_files > 100_000:
            raise ValueError("max_files out of range")
        if max_bytes_per_file < 1 or max_bytes_per_file > 100 * 1024 * 1024:
            raise ValueError("max_bytes_per_file out of range")

        report = self.state.auditor.run(max_files=max_files, max_bytes_per_file=max_bytes_per_file)
        envelope = {
            "status": "ok",
            "agent": AGENT_NAME,
            "capability": CAPABILITY_FILE_AUDIT,
            "request_id": request_id,
            "target_root_id": target_root_id,
            "created_at_ms": int(time.time() * 1000),
            "report": report,
            "artifact_hash": report["artifact_hash"],
        }
        envelope["envelope_hash"] = sha256_text(canonical_json(envelope))
        return envelope


def main() -> None:
    parser = argparse.ArgumentParser(description="Morimil LAN PC companion agent")
    parser.add_argument("--root", default=os.getcwd(), help="project root to audit")
    parser.add_argument("--host", default="0.0.0.0", help="bind host")
    parser.add_argument("--port", type=int, default=8787, help="bind port")
    parser.add_argument("--target-root-id", default="morimil_pc_root", help="root id expected by Android")
    parser.add_argument("--pairing-key", default=os.environ.get("MORIMIL_PAIRING_KEY"), help="pairing key")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    if not root.exists() or not root.is_dir():
        raise SystemExit(f"root is not a directory: {root}")

    pairing_key = args.pairing_key or uuid.uuid4().hex
    Handler.state = AgentState(root=root, pairing_key=pairing_key, target_root_id=args.target_root_id)
    server = ThreadingHTTPServer((args.host, args.port), Handler)

    print(f"Morimil PC agent listening on http://{args.host}:{args.port}")
    print(f"allowed_root={root}")
    print(f"target_root_id={args.target_root_id}")
    print(f"pairing_key={pairing_key}")
    server.serve_forever()


if __name__ == "__main__":
    main()
