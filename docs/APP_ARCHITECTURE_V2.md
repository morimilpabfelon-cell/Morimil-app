# Morimil App Architecture V2

This document describes the current `provider-neutral-motor` Android runtime. It is an active runtime contract, not an early plan.

## Core Rule

Genesis is the seed. The app is the body. Local Room/SQLite memory is the lived nervous system. Reasoning providers are transport only.

```text
identity:
  Genesis + local birth + local memory

reasoning:
  provider-neutral motor slots

memory:
  append-only local events plus higher memory organs

authority:
  user approval gates for sensitive consolidation and migration actions
```

## Current Runtime Shape

```text
Platform: Android native
Language: Kotlin
UI: Jetpack Compose
Persistence: Room / SQLite
Main DB: MorimilDatabase v8
Organ DB: MemoryOrganDatabase v5
Reasoning: provider-neutral Motor/API screen
Branch: provider-neutral-motor
```

## Non-Negotiable Boundaries

- Genesis is installed locally from the bundled seed.
- Genesis is not loaded from GitHub at runtime.
- One local birth remains the identity boundary.
- Memory is append-only.
- Runtime append verifies the recent trusted tail before writing.
- Full-chain integrity audit is explicit or scheduled through rest-cycle maintenance.
- Reasoning providers can reason over context, but cannot become identity or memory.
- UI presents state and calls ViewModel/repository flows; it is not the authority layer.
- PC execution is not implemented inside the phone app.

## Active Runtime Organs

The following are implemented runtime pieces, not merely planned organs:

- Genesis reader and verifier.
- Local birth/workspace identity.
- Local Room memory.
- Hash-linked memory events.
- Living memory snapshot.
- Provider-neutral reasoning motor.
- Up to 10 encrypted reasoning API slots.
- Compatible model discovery.
- Voice input and TTS controls.
- Autobiographical snapshot.
- Knowledge capsules.
- Recall schedule runtime.
- WorkManager rest cycle runtime.
- Cognitive migration records.
- Approval and rollback flow for migrations.
- Memory links and backlinks.
- Read-only visual memory graph canvas.
- Manual memory integrity audit.
- Organism health report.

## Database Contract

`MorimilDatabase` is version 8 and owns the primary local memory ledger:

```text
memory_messages
decision_log
project_state
user_workspace
local_instance_identity
genesis_core
memory_events
memory_snapshots
```

`MemoryOrganDatabase` is version 5 and owns higher memory organs:

```text
autobiographical_snapshots
knowledge_capsules
recall_schedules
memory_links
migration_records
```

The two DBs are separate by current design. This isolates the first living-memory ledger from higher-level organs while Morimil is still evolving. The tradeoff is that Room cannot provide one atomic transaction across both DB files. Rest-cycle maintenance therefore performs reconciliation across event hashes, links, recalls, capsules, and migration records.

## Memory Event Contract

Active `memory_events` fields include:

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
- memoryKind
- tagsJson
- evidenceJson
- confidence
- userConfirmed
- body
- importance
- createdAtMillis

Runtime events currently write `signatureAlgorithm = unsigned_runtime_v1`. The active protection is hash-chain integrity evidence, not AndroidKeyStore-backed signature resistance. Non-exportable key signing remains a hardening layer until implemented directly.

## Hash Versions

The runtime keeps compatibility with legacy memory while strengthening current events:

```text
morimil.memory_event_hash.v1:
  legacy main event fields

morimil.memory_event_hash.v2:
  adds source, contextTag, privacyVisibility

morimil.memory_event_hash.v3:
  current event canonicalization with memory kind, tags, evidence,
  confidence, and user confirmation
```

Capsules use their own capsule hash canonicalization through the shared memory integrity core.

## Memory Integrity

Hashing and verification are centralized through the core memory integrity layer:

```text
MemoryIntegrityCore
MemoryHasher
MemoryEventIntegrity
MemoryIntegrityVerifier
CapsuleHasher
```

Append flow:

```text
classify memory event
resolve append tail under shared append gate
verify recent trusted tail
compute event hash
insert append-only event
rebuild snapshot
```

Full-chain audit is not run on every message. It runs through explicit Memory audit or rest-cycle maintenance, so the app does not become slower with every new memory.

If a tail cannot be trusted, Morimil appends a quarantine boundary and continues from a trusted anchor instead of permanently blocking memory writes.

## Reasoning Motor

The motor is provider-neutral.

```text
slots:
  up to 10 encrypted API/local endpoint configurations

wire formats:
  messages-style
  chat/completions-style
  OpenAI responses-style

model discovery:
  compatible endpoint/model detection plus manual override
```

Provider names are not doctrine and not identity. They are replaceable reasoning transports.

## Memory Organs

Recall, rest, links, and migrations exist as runtime organs.

```text
recall_schedules:
  spaced reinforcement prompts tied to memory event hashes

rest cycle:
  WorkManager maintenance, full audit, reconciliation, consolidation,
  history, approval, and append-only rollback notes

memory_links:
  graph edges between memory nodes with relation, strength, reason,
  privacy, export flags, and verification state

migration_records:
  proposal, approval, execution, rollback, risk, steps, errors,
  and affected artifact tracking
```

## UI Surfaces

```text
Chat:
  conversation, voice controls, health chip/card, local memory writes

Motor/API:
  neutral reasoning provider slots and model discovery

Genesis:
  local bundled seed reader/verifier

Workspace:
  local instance identity

Memory:
  event review queue, recalls, rest-cycle history, cognitive migrations,
  capsules, links, backlinks, visual graph, integrity audit, health report

PC:
  handoff planning only; no autonomous shell execution
```

## Verification Gate

Before treating a DB or memory change as safe, run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

For Room migrations, use a connected device/emulator:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

The current migration coverage includes full-chain migration tests for `MorimilDatabase` 1 -> 8 and `MemoryOrganDatabase` 1 -> 5. Explicit per-DB tests may also exist as `MorimilDatabaseMigrationTest` and `MemoryOrganDatabaseMigrationTest` when that hardening patch is applied.

## Still Future

- Autonomous PC command execution.
- Phone-to-PC worker governance.
- Production release hardening.
- AndroidKeyStore-backed event signatures.
- Cross-DB unification or stronger transaction strategy.
- Editable graph edges from the visual canvas.
