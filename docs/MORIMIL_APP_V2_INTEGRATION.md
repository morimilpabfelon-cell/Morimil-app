# Morimil-app V2 Integration

This document describes how to integrate changes into the current `provider-neutral-motor` runtime without regressing the living-memory system.

## Integration Rule

Do not replace the current app blindly. Integrate into the verified Android body and preserve these invariants:

- one local birth
- bundled Genesis seed
- append-only memory
- provider-neutral reasoning
- local Room/SQLite memory ownership
- explicit approval for sensitive consolidation/migration actions
- no autonomous PC execution from the phone app

## Current Active Scope

```text
database:
  MorimilDatabase v8
  MemoryOrganDatabase v5

reasoning:
  provider-neutral slots
  up to 10 encrypted API/local endpoint configurations
  compatible model discovery
  messages/chat/responses request styles
  manual endpoint/model override

memory:
  append-only hash-linked events
  recent-tail verification on append
  explicit full-chain audit
  quarantine boundary instead of permanent memory brick
  living snapshot rebuild

organs:
  autobiographical snapshot
  knowledge capsules
  recall schedules
  rest cycle
  memory links/backlinks
  migration records
  health report
```

## Not Changed By Integration

- Genesis manifest.
- Genesis core hash.
- Bundled Genesis files.
- Local birth boundary.
- Append-only memory rule.
- User approval requirement for sensitive migrations.
- Phone app does not run arbitrary PC shell commands.

## Database Integration Map

Primary memory ledger:

```text
MorimilDatabase v8:
  memory_messages
  decision_log
  project_state
  user_workspace
  local_instance_identity
  genesis_core
  memory_events
  memory_snapshots
```

Higher memory organs:

```text
MemoryOrganDatabase v5:
  autobiographical_snapshots
  knowledge_capsules
  recall_schedules
  memory_links
  migration_records
```

When adding DB fields:

1. Add the entity/DAO change.
2. Add a Room migration.
3. Export/update Room schema JSON.
4. Add or update migration tests.
5. Preserve old user data in the migration path.
6. Prefer append-only compensation over destructive correction.

## Reasoning Integration Map

The old integration note understated the current wire support. The active runtime supports a broader neutral model:

```text
ReasoningProvider:
  request construction
  response parsing
  wire-format inference

ReasoningModelDiscoveryClient:
  model list discovery where endpoint supports it

SecretVault:
  encrypted runtime key references

Motor/API UI:
  slot management, model selection, endpoint selection
```

Supported request shapes:

- messages-style
- chat/completions-style
- responses-style

Integration must not reintroduce a single-provider client as the runtime path. Provider labels are allowed as presets or display names; identity and doctrine stay provider-neutral.

## Memory Integration Map

The main ledger keeps legacy hash compatibility and current v3 event hashing:

```text
legacy compatibility:
  previousEventHash
  genesisCoreHash
  eventHash
  hashAlgorithm
  canonicalization
  signatureAlgorithm
  eventSignature

current runtime metadata:
  source
  contextTag
  privacyVisibility
  memoryKind
  tagsJson
  evidenceJson
  confidence
  userConfirmed
```

Runtime events explicitly write unsigned signatures as `unsigned_runtime_v1`. Do not document this as Ed25519 or hardware-backed signing until AndroidKeyStore signing is implemented and tested.

## Organ Integration Map

The following are already active and should be integrated as real runtime organs:

```text
knowledge capsules:
  consolidated knowledge with capsule hash chain

recalls:
  due/future reinforcement tied to memory events

rest cycle:
  WorkManager maintenance, audit, reconciliation, consolidation,
  history, approval, and append-only rollback notes

links/backlinks:
  graph edges stored in memory_links and surfaced in Memory UI

cognitive migrations:
  proposal, approval, execution, rollback, audit trail
```

Do not describe these organs as absent unless the specific feature is truly not implemented. Future work can still improve the organ, but the runtime contract exists.

## Cross-Database Rule

The app currently uses two SQLite files. Because Room cannot make a single transaction across both, cross-DB references must be treated carefully:

- event hashes are append-only references
- orphaned links can be marked `orphaned`
- orphaned recalls can be degraded
- capsule and migration gaps should be reported by rest-cycle maintenance
- sensitive consolidation should require approval

If a future change unifies DBs, it must include migration tests and a rollback plan.

## Required Verification

Run JVM tests and build:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Run instrumented tests on a connected device/emulator when touching Room migrations, WorkManager scheduling, or Android-only APIs:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Useful focused migration command when explicit migration tests are present:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.morimil.app.data.local.MorimilDatabaseMigrationTest,com.morimil.app.data.local.MemoryOrganDatabaseMigrationTest"
```

## Integration Rejection Checklist

Reject or revise a change if it:

- rewrites memory events instead of appending.
- bypasses the shared memory append gate.
- runs full-chain audit on every chat message.
- hardcodes one reasoning provider as identity.
- stores raw API keys outside encrypted runtime storage.
- claims hardware-backed signatures without implementation.
- creates cross-DB references without reconciliation.
- changes Room schema without migration tests.
- adds PC execution without approval/governance boundaries.
