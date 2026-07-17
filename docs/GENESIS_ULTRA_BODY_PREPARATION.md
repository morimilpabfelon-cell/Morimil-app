# Genesis Ultra Body Preparation

## Decision

Morimil-app is the first Android Body for Genesis Ultra.

The embedded legacy Morimil Genesis bundle is not a valid source for new births. It remains temporarily available only for migration analysis and compatibility with existing development data.

The only normative source for the future birth protocol is:

```text
morimilpabfelon-cell/genesis-ultra-updated-1
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

The gate must remain closed until all of the following exist:

1. A frozen Genesis Ultra release candidate.
2. A signed release manifest and trusted Guardian root.
3. An Android release verifier with shared conformance vectors.
4. A transactional birth journal.
5. Instance, Body, Guardian and key-epoch records.
6. A Body Registry with one `active_writer`.
7. A signed first memory event and birth receipt.
8. Recovery tests for interruption and storage failure.

The gate must never be opened by changing a Boolean alone. Opening it requires replacing the temporary gate with a verifier that derives readiness from validated release and runtime evidence.

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

- Freeze the source SHA.
- Work only on isolated branches.
- Add CI for unit tests, lint, debug build and instrumentation-test compilation.
- Record known build and migration failures.

### Phase 1 — Android integrity

- Complete every Room migration path.
- Test upgrades from supported historical schemas.
- Remove duplicate or hidden Engine configurations.
- Identify all startup writes and prevent pre-birth identity state.

### Phase 2 — Genesis release adapter

- Consume a frozen signed Genesis Ultra release.
- Verify required artifacts, schemas, domains, digests and signatures.
- Map protocol contracts to Kotlin without changing their meaning.

### Phase 3 — Birth transaction

- Prepare Guardian trust.
- Generate and attest the Body key.
- Create Instance and Body identifiers.
- Register the Body and establish `active_writer`.
- Commit the first signed canonical event.
- Emit a verifiable birth receipt.

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

## Birth readiness rule

```text
birth_ready =
  signed_release_valid
  AND android_conformance_valid
  AND body_key_ready
  AND birth_transaction_ready
  AND canonical_memory_ready
  AND recovery_tests_valid
```

Until every condition is true, Morimil may be developed and tested, but a new Genesis instance must not be born.
