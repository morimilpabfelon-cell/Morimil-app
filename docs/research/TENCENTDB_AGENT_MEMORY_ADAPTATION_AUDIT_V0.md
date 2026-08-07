# Document status: RESEARCH_ONLY

# TAM-00 — TencentDB Agent Memory adaptation audit v0

## Evidence boundary

```text
AUDIT_ID=TAM-00
MORIMIL_REPOSITORY=morimilpabfelon-cell/Morimil-app
MORIMIL_BASE_SHA=222dea68ee7bdd742d5678a7e96beea83e35910f
EXTERNAL_REPOSITORY=TencentCloud/TencentDB-Agent-Memory
EXTERNAL_DEFAULT_BRANCH=feat/server_team
EXTERNAL_COMMIT=0aff21a2d9f2b8a0354aaa80a2e586aab4054562
EXTERNAL_SUBJECT=feat: release v2.0.0
EXTERNAL_LICENSE=MIT
AUDIT_DATE_UTC=2026-08-04
RUNTIME_CHANGED=false
SCHEMA_CHANGED=false
WORKFLOWS_CHANGED=false
DEPENDENCIES_CHANGED=false
GENESIS_CHANGED=false
```

This document is an adaptation audit only. It does not activate, vendor, execute, import, or grant authority to TencentDB Agent Memory. It records which ideas may be reimplemented inside Morimil's existing authority model and which designs are prohibited.

## Morimil authority that cannot be displaced

Morimil is the continuous personal Instance. `Morimil-app` is the current Android Body. The external repository is neither Morimil nor a peer authority.

The only permitted direction is:

```text
GenesisUltraRuntimeIdentityRepository
        +
CanonicalMemoryRepository
        |
        v
CanonicalConsumerReadPort
        |
        v
verified rebuildable projections
```

The external project cannot become:

- an identity source;
- a canonical-memory writer;
- a Guardian or Body authority;
- a continuity or succession authority;
- a replacement for Genesis Ultra;
- a second autobiographical store;
- a transparent proxy that injects private context into providers.

The following invariants remain binding:

```text
instanceId != bodyId
ACTIVE_WRITERS_MAX=1
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
F3_3=NO_GO
```

## Current Morimil frontier relevant to adaptation

At the audited Morimil base:

- committed Genesis Ultra identity is recovered through `GenesisUltraRuntimeIdentityRepository`;
- canonical memory is signed, verified, provenance-bound, and read through `CanonicalMemoryRepository`;
- `CanonicalConsumerReadPort` is the common downstream read boundary;
- COG-001 through COG-004 use a bounded durable cross-database protocol;
- ProjectVault remains a separate protected outbox protocol;
- Recall still reads `genesis_core`, `local_instance_identity`, and `memory_events` through legacy DAO calls;
- RestCycle still plans and commits through legacy identity and memory paths;
- F3.3 remains open and irreversible retirement has not begun;
- reasoning still has an automatic provider-continuation path after output truncation;
- Body export, restore, succession, revocation, and physical operational birth remain unimplemented.

Therefore the first useful adaptation target is not a new memory service. It is F1-RECALL-001: rebuild Recall as a deterministic projection of verified canonical input.

## External repository characterization

The pinned external tree is a Node.js/TypeScript monorepo with several independently deployable components. The inspected manifests and documentation expose local and cloud-oriented storage, SDKs, a proxy, a management panel, plugins, vector search, LLM extraction, telemetry, and optional Redis, MongoDB, COS, Kafka, ClickHouse, and Tencent VectorDB integrations.

`MemoryCore/package.json` identifies an L0→L1→L2→L3 pipeline and includes SQLite/vector search, OpenClaw integration, remote/local LLM dependencies, optional cloud stores, build scripts, Vitest scripts, migration/export commands, and post-install patching.

This architecture solves a different problem from Morimil's canonical continuity. It is useful as a pattern catalogue, not as a runtime to place beside Morimil's signed memory.

## Revalidated source facts

The following facts were verified against the pinned external commit:

1. The default branch is `feat/server_team`.
2. The branch head is `0aff21a2d9f2b8a0354aaa80a2e586aab4054562` with subject `feat: release v2.0.0`.
3. The root license declares MIT.
4. `MemoryCore/package.json` exposes build and Vitest commands and declares Node `>=22.16.0`.
5. The build command references `MemoryCore/scripts/seed-v2/tsconfig.json`.
6. That referenced `tsconfig.json` is absent at the pinned commit.

## Executable-verification status

A fresh local clone and command execution were attempted from the audit environment, but outbound DNS resolution for `github.com` was unavailable. The external npm suites were therefore not rerun in this delivery.

The prior handoff reported the following results on the same pinned commit. These entries are retained strictly as `REPORTED_NOT_REEXECUTED`, not as fresh PASS/FAIL evidence:

| Component | Command | Historical result | Current evidence class |
| --- | --- | --- | --- |
| MemoryCore | `npm test` | no test files | `REPORTED_NOT_REEXECUTED` |
| MemoryCore | `npm run build` | missing `scripts/seed-v2/tsconfig.json` | missing path independently revalidated |
| MemoryKnowledge | `npm test` | no test files | `REPORTED_NOT_REEXECUTED` |
| MemoryKnowledge | `npm run typecheck` | TypeScript error | `REPORTED_NOT_REEXECUTED` |
| MemoryProxy | `npm test` | no test files | `REPORTED_NOT_REEXECUTED` |
| MemoryProxy | `npx tsc --noEmit` | four errors | `REPORTED_NOT_REEXECUTED` |
| MemoryPanel | `npm test` | no test files | `REPORTED_NOT_REEXECUTED` |
| MemoryPanel | `npm run typecheck` | pass | `REPORTED_NOT_REEXECUTED` |

No external quality claim is promoted into Morimil evidence without a reproducible dataset, runner, expected outputs, scoring method, and exact command transcript.

## Binding adoption decision

```text
VENDOR_EXTERNAL_REPOSITORY=false
RUN_AS_MORIMIL_MEMORY=false
ADD_TENCENTDB_RUNTIME_DEPENDENCY=false
COPY_EXTERNAL_STORAGE_MODEL=false
COPY_EXTERNAL_AUTH_MODEL=false
COPY_EXTERNAL_PROXY=false
COPY_EXTERNAL_PROMPTS=false
REIMPLEMENT_SELECTED_PATTERNS=true
```

This decision is architectural, not a judgment that the external project has no value. Running it as Morimil memory would create incompatible authority, retention, credential, network, and recovery boundaries.

## Designs rejected for Morimil

The following must not be introduced as authoritative paths:

- unsigned JSONL or SQLite records treated as canonical history;
- vector indexes treated as ground truth;
- shared compatibility identifiers in place of canonical `instanceId`;
- cloud Redis, MongoDB, COS, or Tencent VectorDB as a parallel memory authority;
- LLM extraction directly promoting facts into autobiographical memory;
- a proxy that transparently injects private memory into arbitrary providers;
- plaintext-recoverable user or provider keys;
- prompts copied as security policy;
- prompt-injection detection disabled by configuration;
- `curl -k`, weakened TLS, redirects, or uncontrolled proxy use;
- private content in logs or telemetry;
- fire-and-forget retries without a durable journal;
- mutable action tags or unaudited CI dependencies;
- code copied only because the license permits it.

## Patterns approved for native reimplementation

### 1. L0–L3 as disposable projections

Layered representations may be useful for recall and bounded context, but every layer must be derived from a verified canonical snapshot. Deleting all layers must not damage identity or autobiographical truth.

Minimum projection envelope:

```text
instanceId
writerBodyId
writerEpochId
snapshotDigest
sourceEventHash
sourceSequence
contentDigest
projectionSchemaVersion
```

### 2. Deterministic local ranking

BM25-style lexical ranking and reciprocal-rank fusion may be reimplemented locally when:

- the input is a verified projection;
- tie-breaking is deterministic;
- no network or embedding provider is required;
- the result is non-authoritative;
- corruption and foreign-Instance input fail before mutation.

### 3. Explicit budgets

Recall and context construction should expose hard limits for:

- candidate count;
- selected item count;
- characters/bytes;
- elapsed planning time;
- per-source contribution;
- total context output.

Budget exhaustion must be observable and must not silently expand provider egress.

### 4. Durable queues and visible blocked state

Retry, dead-letter, and blocked-state ideas are useful only when implemented over Morimil's durable operation protocol or an equally audited owner-specific outbox. No in-memory queue may represent authoritative progress.

### 5. Signed manifests and dry-run import

Manifest, checkpoint, preflight, dry-run, and receipt patterns are candidates for F5 export/restore. They must bind the same `instanceId`, canonical lineage, active writer epoch, cryptographic roots, and rollback boundary.

### 6. Skills separated from autobiography

Skills may be immutable, versioned capability packages with human review and revocable permissions. They must remain outside autobiographical memory and must never inherit identity, canonical-memory, or unrestricted tool authority.

### 7. Read-only auxiliary knowledge systems

Wiki and code-graph systems may exist on a stronger Body or PC when sources are pinned, verified, provenance-visible, and read-only with respect to Morimil's identity and canonical memory.

## Required security envelope for every projection

A projection implementation must demonstrate:

1. same-Instance verification before reads are accepted;
2. active writer Body and epoch binding when mutation is staged;
3. deterministic IDs independent of wall-clock time;
4. exact source digests and sequences;
5. rebuild after deletion;
6. rejection of corrupt, foreign, unknown-schema, and stale-epoch input;
7. idempotent replay;
8. no write to `genesis_core`, `local_instance_identity`, or `memory_events`;
9. no reverse flow into Genesis, doctrine, policy, or canonical memory;
10. process-death recovery where two durable stores are involved.

## TAM delivery sequence

Each item is a separate PR and must start from the then-current verified `main`.

1. **TAM-00:** this adaptation audit only.
2. **TAM-01 / F1-RECALL-001:** Recall consumes `CanonicalConsumerReadPort`; eliminate legacy read sources; deterministic projection IDs; replay, corruption, foreign-Instance, stale-epoch, and process-kill coverage.
3. **TAM-02:** deterministic lexical ranking and bounded context budgets; no embeddings and no network.
4. **TAM-03 / F1-REST-001:** RestCycle planning from canonical input; derived proposals only; no LLM canonical writes.
5. **TAM-04 / REST-002:** durable commit/recovery using journal or outbox with idempotent finalization.
6. **TAM-05:** non-authoritative Atom/Scenario envelope with provenance, versioning, rebuild, and delete tests.
7. **TAM-06:** Skills outside autobiographical memory with immutable versions, review, and revocable capabilities.
8. **TAM-07:** read-only Wiki/CodeGraph on an eligible Body with pinned sources.
9. **TAM-08 / F5:** export, import, restore, and succession patterns with preflight, dry-run, and signed receipts.

TAM-01 is the next implementation candidate only after TAM-00 is merged and post-merge truth is verified.

## Gate before copying any external file

MIT licensing is necessary but insufficient. Copying bytes requires a separate record containing:

1. exact external path and commit;
2. license and notice obligations;
3. reason native reimplementation would be materially inferior;
4. security review of the exact bytes;
5. adaptation to Kotlin/Android and Morimil authority;
6. unit, integration, corruption, and recovery tests;
7. dependency and SBOM impact;
8. proof that no identity, memory, credential, or network authority is introduced.

No external runtime file clears this gate in TAM-00. Native independent implementation remains the default.

## Acceptance result

```text
TAM_00_SCOPE=AUDIT_ONLY
EXTERNAL_AUTHORITY_ADOPTED=false
RUNTIME_CHANGED=false
SCHEMA_CHANGED=false
WORKFLOWS_CHANGED=false
DEPENDENCIES_CHANGED=false
GENESIS_CHANGED=false
TAM_01_RECOMMENDED=true
TAM_01_TARGET=F1-RECALL-001
F3_3=NO_GO
OPERATIONAL_BIRTH=false
```

TAM-00 does not close F1, F3, F4, F5, F6, or operational birth. It supplies a bounded research decision and a testable next frontier.