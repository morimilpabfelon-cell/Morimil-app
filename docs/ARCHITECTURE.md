# Morimil App Architecture

## Current Runtime

Morimil App is the Android body for a phone-local Morimil instance. Genesis is the seed, the phone stores the living memory, and reasoning APIs are interchangeable transport for the current turn.

```text
Platform: Android native
Language: Kotlin
UI: Jetpack Compose
Persistence: Room / SQLite
Primary branch: provider-neutral-motor
Genesis repository: read-only seed source, not mutated by this app
```

## Main Screens

```text
Chat:
  conversation UI, push-to-talk voice input, manual TTS, local memory writes

Motor/API:
  provider-neutral reasoning runtime configuration
  up to 10 encrypted reasoning API slots
  compatible model discovery and manual model override

Genesis:
  bundled seed reader, verifier, and local birth state

Workspace:
  phone-local instance/workspace identity

Projects:
  persisted project state

Memory:
  living snapshot, event review queue, recalls, visual graph canvas, backlinks,
  rest cycle history, cognitive migrations, memory organs, capsules, and integrity audit

PC:
  placeholder for future approved PC handoff; no command execution yet
```

## Persistence Model

Morimil uses two local Room databases.

```text
MorimilDatabase:
  local_instance_identity
  genesis_core
  memory_messages
  memory_events
  memory_snapshots
  decision_log
  project_state
  user_workspace

MemoryOrganDatabase:
  autobiographical_snapshots
  knowledge_capsules
  memory_links
  recall_schedules
  migration_records
```

The phone-local databases are the runtime memory body. Reasoning providers do not own memory, identity, doctrine, or continuity.

## Living Memory

Memory events are append-only. User messages, assistant messages, reviews, rest cycles, cognitive migrations, rollbacks, and quarantine markers are recorded as new events instead of rewriting old ones.

```text
append path:
  classify event
  verify recent memory tail
  compute v3 event hash through MemoryIntegrityCore
  insert event
  rebuild living snapshot

explicit audit path:
  load full event chain
  verify through MemoryIntegrityCore
```

Runtime appends verify only the recent tail so memory growth does not make every message rehash the entire history. Full-chain verification is available as an explicit audit.

If the recent tail is untrusted, the runtime appends a `memory_integrity.quarantine` boundary and continues from the last trusted hash instead of blocking memory forever.

## Integrity Core

All current event and capsule hash operations go through the shared core facade:

```text
core/memory/MemoryIntegrityCore.kt
  event chain verification
  event tail inspection
  memory event v3 hashing
  capsule chain verification
  capsule v2 hashing
  shared integrity constants
```

Lower-level helpers remain type-specific:

```text
MemoryHasher:
  stable SHA-256 field canonicalization

MemoryEventIntegrity:
  memory event canonicalization v1/v2/v3 compatibility

MemoryIntegrityVerifier:
  event chain and tail verification

CapsuleHasher:
  knowledge capsule canonicalization v1/v2 compatibility
```

Current runtime events use `signatureAlgorithm = unsigned_runtime_v1`. This is integrity evidence, not non-exportable cryptographic signing. AndroidKeyStore/Ed25519 signing remains future work unless implemented explicitly.

## Memory Organs

The memory organ layer is local and visible in the Memory screen.

```text
autobiographical snapshot:
  current self summary, stable traits, goals, constraints

knowledge capsules:
  stable consolidated knowledge with capsule hash chain

recall schedules:
  spaced recall prompts connected to memory events

memory links:
  graph edges between memory nodes with relation, strength, reason, verification

migration records:
  rest cycle and cognitive migration proposals, approvals, execution, rollback notes
```

The Memory UI exposes capsules, consolidated categories, links, migrations, and manual integrity audit. Backlinks are navigable from recent memory events. The visual graph canvas builds a read-only Obsidian-style view from `memory_links`, loads connected memory events by hash, distinguishes external node types, and lets memory-event nodes open their connected memories.

## Rest Cycle

The rest cycle is local. It summarizes meaningful recent memory, records a migration entry, appends a rest-cycle event, links the event to source memories, and rebuilds the living snapshot.

Scheduling is handled by `RestCycleWorker` through WorkManager. The periodic worker runs every 6 hours with a flexible window, no network requirement, battery/storage safeguards, initial delay, retry backoff, and a stable work tag.

Important rest-cycle consolidations require user approval before execution. The Memory screen separates pending approvals from cycle history and shows plan, steps, affected artifacts, result notes, and rollback strategy. Rollback is append-only compensation, not deletion of prior memory.

## Recall Schedule

Recall schedules are active local reminders for memory reinforcement. Recalls are prioritized by due time, importance, and memory kind. The UI separates overdue and future recalls and lets the user reinforce, postpone, degrade, or open the target memory event.

## Cognitive Migrations

Cognitive migrations refine memory without rewriting the past.

```text
flow:
  propose plan
  require approval
  execute by appending a cognitive_migration.executed event
  rollback by appending a cognitive_migration.rollback event
```

The v2 planner produces logical diffs, capsule proposals, backlink proposals, risk level, affected artifacts, and rollback strategy. Original memory events remain immutable.

## Reasoning Motor

The reasoning motor is provider-neutral.

```text
Reasoning slot:
  display name
  endpoint
  wire format
  model
  encrypted runtime key reference
  active/inactive state

Supported runtime shape:
  compatible model discovery
  messages/chat/responses-style request formats
  manual endpoint/model override
  up to 10 configured slots
```

Provider names are not identity. They are transport options for reasoning over the local context Morimil sends for a turn.

## Boundaries

```text
Allowed now:
  local Android UI
  local Room/SQLite memory
  local voice controls
  local Genesis seed install/read/verify
  provider-neutral reasoning configuration
  local recall/rest/migration workflows
  local integrity audit
  local visual memory graph navigation

Blocked now:
  autonomous PC command execution
  background PC shell access
  autonomous GitHub mutation from the app
  production release
  provider-owned memory or identity
```

## Build And Verification

Use Android Studio or Gradle with JDK 17.

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Instrumentation migration tests require a connected device or emulator.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```
