# Morimil App Architecture V2

Morimil-app v2 is the stronger body for the bundled Genesis seed.

## Core rule

Genesis is the seed. The app is the body. Local memory is the lived nervous system. The reasoning motor is transport only.

## Non-negotiable boundaries

- Genesis is installed locally from the bundled seed.
- Genesis is not loaded from GitHub at runtime.
- One local birth only.
- Memory is append-only.
- Memory append requires verified chain state before writing.
- The motor can reason but cannot become identity.
- UI is presentation, not authority.

## Active runtime organs

- Genesis reader and verifier.
- Local birth gate.
- Local Room memory.
- Hash-linked memory events.
- Living memory snapshot.
- Configurable reasoning motor.
- Local encrypted runtime storage.
- Runtime gate shell.

## Memory event contract

Active fields:

- genesisCoreId
- genesisCoreHash
- previousEventHash
- eventHash
- hashAlgorithm
- canonicalization
- signatureAlgorithm
- eventSignature
- eventType
- actor
- source
- contextTag
- privacyVisibility
- body
- importance
- createdAtMillis

## Hash versions

The ledger supports two canonical forms:

- `morimil.memory_event_hash.v1`: existing events from main. It verifies the original fields only.
- `morimil.memory_event_hash.v2`: new events. It includes `source`, `contextTag`, and `privacyVisibility` inside the event hash.

This avoids breaking old memory while strengthening new memory.

## Memory organs

Active sidecar organs:

- AutobiographicalSnapshotEntity
- KnowledgeCapsuleEntity
- MemoryOrganDao
- MemoryOrganDatabase
- MemoryOrganRepository

They live in a secondary Room database so the primary ledger is not destabilized while these organs mature.

## Next organs

These are allowed only when backed by tables, DAO, runtime code, and tests:

- recall schedule
- rest cycle
- migration record
- memory links
- interaction state

## Forbidden dependencies

- Genesis must not depend on UI.
- Memory must not depend on UI.
- Motor must not write raw memory directly.
- UI must not mutate raw database tables.
- Provider settings must not mutate Genesis.

## Gate before final merge

The final v2 branch is not ready until these pass:

- first birth creates local identity, Genesis copy, birth event, and snapshot
- second birth fails
- memory append verifies chain first
- changed event body breaks chain verification
- runtime gate denies Genesis editing
- reasoning wire tests pass
