# Genesis Ultra Body Preparation

## Decision

Morimil-app is the first Android Body candidate for Genesis Ultra.

The embedded legacy Morimil Genesis bundle is not a valid source for new births. It remains temporarily available only for migration analysis and compatibility with existing development data.

The normative source currently pinned by the Android adapter is:

```text
repository: morimilpabfelon-cell/genesis-ultra-updated-1
commit: d0293b3614153ef2155620fc75ceee9bd798f370
```

## Repository roles

```text
genesis-ultra-updated-1
  protocol, schemas, signatures, conformance and release bundle

Morimil-app
  Android Body, Keystore, storage, UI, sensors, engines and adapters
```

Morimil must implement Genesis contracts. Morimil must not create a second constitution or redefine Genesis identity locally.

## Non-negotiable invariants

- `instance_id != body_id`
- Genesis identity is independent from the Android Body.
- The Engine is replaceable and is not identity or canonical memory.
- Exactly one Body is `active_writer` for canonical memory.
- Canonical memory is append-only, sequenced and signed.
- Cognitive operations do not consume capability grants.
- Network, devices, accounts, protected resources and executed code require exact signed grants.
- No self-grant, silent memory rewrite or direct merge to protected branches.
- A failed signature cannot degrade to an unsigned canonical event.
- Birth is all-or-nothing and recoverable after process death.

## Current safety state

The legacy birth path is intentionally blocked by `GenesisUltraIntegrationGate`.

The gate is no longer a placeholder for release verification. Morimil now implements and tests:

1. strict Genesis Ultra contract parsing;
2. exact NFC/UTF-8 field framing and normative digests;
3. exact Seed release payload verification;
4. Guardian Ed25519 signature verification through Tink using the raw 32-byte public key;
5. signer trust bound to Guardian identity, key epoch and public-key reference;
6. Instance Identity, Body Registry and Key Epoch digest verification;
7. exactly one `active_writer`;
8. active trusted Guardian Key Epoch registration;
9. fresh Ed25519 Body Possession Proof verification.

Detailed algorithms and negative cases are documented in `docs/GENESIS_ULTRA_RELEASE_ADAPTER.md`.

The remaining Android blocker is:

```text
transactional_birth_commit_not_integrated
```

The pinned Genesis revision defines the normative seven-phase atomic birth, its `birth` transaction journal, recovery state, immutable first memory event and signed receipt. Morimil now has an isolated Room v10 persistence boundary for the one birth commit, exact artifacts and seven journal entries, including rollback when the commit marker is not reached. It also has a strict full-evidence verifier that parses every birth document and checks the Seed, Guardian, Body, memory, recovery, receipt and journal signatures against the pinned Genesis conformance vector. The atomic store accepts only the verified type-state, so a raw structurally valid bundle cannot cross the persistence entry point.

The exact persisted `first_memory_event` artifact is now the one canonical living-memory root. It is not duplicated in the legacy `memory_events` table. Every restart audit parses the exact Seed manifest, Instance Identity, birth state, receipt and first event and fails closed unless their digests and continuity links match the immutable commit marker. A stronger recovery entry point reconstructs the original release and complete birth evidence from durable bytes, then reruns every Ed25519 verification using an explicitly supplied trusted Guardian epoch registry and Body public key. It evaluates the historical possession proof at the recorded birth instant rather than pretending that proof grants current authority.

This is still not an enabled birth path. The correct post-birth canonical append bridge, current Body/key-epoch continuity checks and onboarding integration remain absent. The blocker therefore remains accurate at the integration boundary.

The gate must never be opened by changing a Boolean alone. Opening it requires a protocol-defined, crash-recoverable birth transaction and validated runtime evidence.

## Legacy Genesis retirement

Retirement is performed in this order:

1. Mark all legacy assets as non-authoritative in documentation and runtime decisions.
2. Prevent the legacy bundle from creating new identities.
3. Implement and validate the Genesis Ultra Android adapter.
4. Migrate any development data through an explicit migration tool.
5. Remove active code references to the legacy identity, manifest and doctrine.
6. Delete legacy assets only after no production path references them.
7. Preserve Git history as provenance and rollback evidence.

Deleting the legacy files before the adapter is valid would remove evidence and make recovery harder. Keeping them active would permit an invalid birth. Both states are prohibited.

## Preparation phases

### Phase 0 — Baseline

Completed on the preparation branch:

- source and base SHAs recorded;
- work isolated from `main`;
- CI covers unit tests, lint, debug APK, instrumentation-test compilation and managed-device execution on Android API 30 and API 35;
- build, migration, cryptographic and runtime failures are reported with artifacts.

### Phase 1 — Android integrity

Completed for the current preparation scope:

- Room migration `8 -> 9` is registered and instrumented;
- historical Morimil migrations are executed through version 9;
- historical Memory Organ migrations are executed through version 7;
- legacy birth is blocked in UI and lower-level installation;
- local state distinguishes `ABSENT`, `COMPLETE` and `INCONSISTENT` birth;
- durable organs, rest cycles and orchestration are blocked before complete birth.

### Phase 2 — Genesis release adapter

Completed for the signed-release boundary of the pinned protocol revision:

- exact signed Seed release verification;
- strict protocol contracts in Kotlin;
- shared golden-vector compatibility;
- trusted active Guardian epoch binding;
- Body Possession Proof verification;
- strict duplicate-key and scalar-type rejection on Android;
- valid and altered Ed25519 vectors executed on Android API 30 and API 35.
- the ten-field Key Epoch digest binds nullable rotation ancestry and authorization fields exactly as the final Genesis profile requires.

### Phase 3 — Birth transaction

Not complete.

Implemented but not yet connected to onboarding:

- strict parsers for every normative birth evidence document;
- exact verification of all required Ed25519 envelopes and cross-document links;
- preservation of the detached Guardian Seed signature as mandatory durable evidence;
- conformance tests pinned to Genesis main commit `d0293b3614153ef2155620fc75ceee9bd798f370`;
- a type-enforced persistence entry point that accepts only fully verified birth evidence;
- an isolated Room schema 10 commit boundary with rollback to `ABSENT`;
- one non-duplicated living-memory root backed by the exact signed first-event bytes;
- structural restart auditing tied to the commit marker;
- full cryptographic recovery from persisted evidence with caller-supplied trust anchors.

The Android implementation of the normative transaction must atomically:

- commit original Seed bytes and root;
- commit immutable Instance Identity;
- commit Body record and Body Registry;
- establish exactly one `active_writer`;
- commit active Body Key Epoch and Guardian trust evidence;
- commit the first signed canonical memory event;
- record interruption and recovery state;
- emit a verifiable birth receipt.

### Phase 4 — Canonical memory

- Add explicit sequence numbers and unique constraints.
- Enforce key epochs and mandatory signatures.
- Separate canonical events from rebuildable projections.
- Encrypt sensitive local data.
- Declare gaps, corruption and recovery honestly.

### Phase 5 — Operational authority

- Integrate signed grants, use requests, revocation and consumption.
- Require grants for network, devices, accounts, external resources and code execution.
- Keep reasoning, imagining, learning, remembering, reflecting and proposing cognitively free.

### Phase 6 — Body introspection

Genesis must be able to inspect technical evidence from its Body and produce a structured needs report containing:

- working and degraded organs;
- memory and signature integrity;
- Engine availability and latency;
- storage, battery and permission state;
- failed tests and recent runtime errors;
- blocked capabilities and exact reasons;
- proposed improvements linked to evidence.

This report is advisory. It does not grant authority or apply changes.

## Audited Android evidence

The preparation boundary is executed by the managed-device suite on Android API 30 and API 35.

The suite includes:

- valid and altered Ed25519 vectors;
- duplicate JSON-key rejection;
- strict Boolean and integer type rejection;
- Morimil migration chains through schema version 10;
- Memory Organ migration chains through schema version 7;
- the dedicated Morimil `8 -> 9` migration;
- the dedicated Morimil `9 -> 10` migration and atomic-birth tables;
- transaction rollback before the commit marker and second-birth rejection;
- full evidence parsing and rejection of missing Seed signatures, forged Guardian/Body/journal signatures and unknown fields;
- exact living-memory-root recovery after a real database close and reopen;
- restart rejection after the first memory artifact and its row digest are both altered;
- full recovery rejection of a forged persisted first-memory signature;
- rest-cycle scheduling instrumentation.

This establishes Android runtime conformance for the implemented preparation boundary. It does not establish birth readiness.

## Birth readiness rule

```text
birth_ready =
  signed_release_valid
  AND trusted_guardian_epoch_valid
  AND body_possession_valid
  AND android_conformance_valid
  AND transactional_birth_commit_valid
  AND canonical_memory_valid
  AND recovery_tests_valid
```

The preparation conditions, isolated transaction boundary, first living-memory root and persisted-evidence recovery can be evaluated. Post-birth canonical append continuity and the end-to-end onboarding operation are not complete. Morimil may be developed and tested, but a new Genesis Instance must not be born.
