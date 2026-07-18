# Morimil App Architecture Map

This document is an operational map, not branding documentation. Its purpose is to prevent disconnected organs: any new organ must have a caller, composition root registration, runtime path, UI path or worker path, validation command, and explicit risk classification.

Last audited baseline: `main` after `cba13a2` / build green from local `:app:assembleDebug`.

## Rule for future changes

A feature is not accepted as implemented until all of the following are true:

1. Main file exists.
2. It is registered in `MorimilAppContainer` or another explicit composition root.
3. It has a real caller from UI, worker, use case, repository, or runtime.
4. It has a validation path that can be executed locally.
5. If it can mutate memory, identity, doctrine, policy, migrations, secrets, or external effects, it must require owner approval or be append-only with audit.

## High-level runtime chain

```text
MainActivity
  -> MorimilApp
    -> OnboardingScreen OR MainTabsScaffold
      -> Chat / Motor / Genesis / Workspace / Projects / Memory / PC / Mejoras

MorimilViewModel
  -> MorimilAppContainer
    -> local databases, repositories, use cases, reasoning kernel, signing, schedulers
```

## Composition root

| Organ | Main registration | Status | Risk |
|---|---|---:|---:|
| Memory database | `MorimilAppContainer.memoryDatabase` | Connected | High |
| Organ database | `MorimilAppContainer.organDatabase` | Connected | High |
| Memory signer | `MorimilAppContainer.memoryEventSigner` | Connected | Critical |
| Memory integrity core | `MorimilAppContainer.memoryIntegrityCore` | Connected | Critical |
| Memory repository | `MorimilAppContainer.memoryRepository` | Connected | Critical |
| Rest cycle repository/use case | `MorimilAppContainer.restCycleRepository`, `runRestCycleUseCase` | Connected | High |
| Recall schedule repository | `MorimilAppContainer.recallScheduleRepository` | Connected | Medium |
| Cognitive migration repository/use case | `MorimilAppContainer.cognitiveMigrationRepository`, `proposeCognitiveMigrationUseCase` | Connected | Critical |
| Project vault repository | `MorimilAppContainer.projectVaultRepository` | Registered | Medium |
| Agent orchestration repository | `MorimilAppContainer.agentOrchestrationRepository` | Registered | High |
| Genesis reader | `MorimilAppContainer.genesisReader` | Connected | High |
| Secret vault | `MorimilAppContainer.secretVault` | Connected | Critical |
| Reasoning kernel | `MorimilAppContainer.reasoningKernel` | Connected | Critical |
| Kernel trace repository | `MorimilAppContainer.kernelTraceRepository` | Connected | Medium |

## UI entrypoints

| Screen / tab | File | Caller | Connected organ |
|---|---|---|---|
| App entry | `MainActivity.kt` | Android launcher | `MorimilApp()` |
| Root UI | `MorimilApp.kt` | `MainActivity` | Chooses onboarding or tabs from local identity |
| Tabs | `MainTabsScaffold.kt` | `MorimilApp` | Chat, Motor, Genesis, Workspace, Projects, Memory, PC, Mejoras |
| Improvements | `ImprovementsScreen.kt` | `MainTabsScaffold` | `ImprovementProposalStore` + audit history |
| Memory | `MemoryScreen` | `MainTabsScaffold` | `MemoryViewModel` |
| Chat | `ChatScreen` | `MainTabsScaffold` | `ChatViewModel` + `ReasoningKernel` |
| Motor | `MotorScreen` | `MainTabsScaffold` | `MotorViewModel` + reasoning profile runtime |
| PC handoff | `PcHandoffScreen` | `MainTabsScaffold` | `PcHandoffViewModel` |

## Organs

### 1. Local Genesis and birth

- Main files:
  - `MorimilViewModel.kt`
  - `MemoryRepository.kt`
  - `GenesisReader.kt`
- Runtime path:
  - `MorimilApp` checks `localIdentity`.
  - If null, onboarding runs birth.
  - `MorimilViewModel.bornInstance()` installs Genesis bundle, verifies manifest hash, creates local identity, inserts Genesis Core, workspace, and birth memory event.
- Guardrail:
  - Birth is one-time only through `hasExistingBirth()` and database count requirements.
- Risk:
  - Critical. Identity continuity starts here.
- Validation:
  - Fresh install -> onboarding -> birth -> app enters tabs.
  - Memory tab must show Genesis/local memory state.

### 2. Living memory append chain

- Main files:
  - `MemoryRepository.kt`
  - `MemoryIntegrityCore.kt`
  - `AndroidKeyStoreMemoryEventSigner.kt`
  - `SharedPreferencesMemorySignatureEpochPolicy.kt`
- Runtime path:
  - User and assistant messages pass through `AppendLivingMemoryUseCase` / `MemoryRepository`.
  - Events are hash-linked using previous event hash.
  - Events are signed before insert.
  - Snapshot is rebuilt after append.
- Caller:
  - `ReasoningKernel.reason()` appends user message and assistant response.
  - `MorimilViewModel.recordMemoryReview()` appends review events.
  - Rest cycle and migration flows append system events.
- Guardrail:
  - `MemoryAppendGate` serializes append operations.
  - Tail verification quarantines untrusted memory tail before continuing.
- Risk:
  - Critical. This is the local continuity spine.
- Validation:
  - Send chat message.
  - Open Memory.
  - Run integrity audit.
  - Confirm no quarantine unless intentionally corrupting test data.

### 3. Knowledge capsules

- Main files:
  - `MemoryOrganRepository.kt`
  - `KnowledgeIntakeClassifier.kt`
- Runtime path:
  - `ReasoningKernel.reason()` sends user text to `captureKnowledgeCapsuleFromText()`.
  - Capsule is created only when explicit capsule intent is detected.
  - Existing capsule chain must verify before write.
- Caller:
  - `ReasoningKernel`.
- Guardrail:
  - Capsule chain verification refuses writes if integrity fails.
  - Previous active capsule versions are superseded.
- Risk:
  - High. It affects long-term knowledge context.
- Validation:
  - Send explicit capsule-style memory instruction.
  - Confirm capsule appears in Memory/context.
  - Run capsule chain audit.

### 4. Memory links / graph substrate

- Main files:
  - `MemoryLinkRepository.kt`
  - `MemoryBacklinkGraphBuilder.kt`
- Runtime path:
  - Capsules link to source memory events.
  - Rest cycles link consolidated event to source events.
  - Recall schedules link review schedules to memory events.
- Caller:
  - `ReasoningKernel`, `RestCycleRepository`, `RecallScheduleRepository`, `MorimilViewModel.selectMemoryEvent()`.
- Guardrail:
  - Links are local/private, cloud sync disabled, export disabled.
- Risk:
  - Medium/high. Graph errors distort context but should not mutate Genesis.
- Validation:
  - Select memory event in UI.
  - Confirm connected graph events load.

### 5. Reasoning kernel

- Main files:
  - `ReasoningKernel.kt`
  - `ModelBackendRouter.kt`
  - `ReasoningClient.kt`
  - `SystemPromptBuilder.kt`
  - `DeterministicFallbackReasoner.kt`
  - `KernelTraceRepository.kt`
- Runtime path:
  - `MorimilViewModel.sendMessage()` builds request.
  - `ReasoningKernel.reason()` detects intent, routes backend, appends user memory, builds context, calls model or fallback, appends assistant response, records trace, runs rest cycle and recall seed.
- Caller:
  - Chat UI via `ChatViewModel` / `MorimilViewModel`.
- Guardrail:
  - Remote API escalation requires owner approval for superior runtime.
  - Unusable backend returns deterministic degraded reply.
  - External web/native context is temporary, not doctrine or identity.
- Risk:
  - Critical. This is the current reasoning spine.
- Validation:
  - Prompt simple local/fallback.
  - Prompt requiring superior runtime.
  - Confirm escalation gate blocks remote model until approved.

### 6. Rest cycle / local consolidation

- Main files:
  - `RestCycleRepository.kt`
  - `RunRestCycleUseCase.kt`
  - `RestCycleWorker.kt`
  - `RestCycleScheduler` object in `RestCycleWorker.kt`
- Runtime path:
  - Startup: `MorimilViewModel.init` calls `runRestCycleUseCase()`.
  - Manual: `MorimilViewModel.runRestCycleNow()`.
  - Scheduled: `RestCycleWorker` obtains `runRestCycleUseCase` from container.
  - Repository verifies chain, reconciles organs, creates maintenance report, plans migration if approval is needed, appends rest cycle event if allowed.
- Caller:
  - App startup, Memory UI/manual action, WorkManager periodic worker.
- Guardrail:
  - Minimum interval and minimum meaningful event count.
  - Chain verification before consolidation.
  - Important rest cycle can become planned migration requiring approval.
- Risk:
  - High. It summarizes and reorganizes local continuity.
- Validation:
  - Open app after birth.
  - Use manual rest cycle button.
  - Confirm migration record and/or rest cycle event.
  - Confirm WorkManager status in UI.

### 7. Recall schedule runtime

- Main files:
  - `RecallScheduleRepository.kt`
  - `RecallSchedulePolicy.kt`
- Runtime path:
  - Startup and kernel turns call `seedFromRecentMemoryIfNeeded()`.
  - Repository selects high-priority memory events and creates recall schedules.
  - UI actions reinforce, postpone, or degrade recall.
- Caller:
  - `MorimilViewModel.init`, `ReasoningKernel.reason()`, Memory UI.
- Guardrail:
  - Scheduling policy filters by memory kind, importance, confidence, and user confirmation.
- Risk:
  - Medium. Bad scheduling creates noise, but should not mutate core identity.
- Validation:
  - Generate meaningful memory event.
  - Seed recall.
  - Confirm active recall schedules.
  - Test reinforce/postpone/degrade.

### 8. Cognitive migration

- Main files:
  - `CognitiveMigrationRepository.kt`
  - `ProposeCognitiveMigrationUseCase.kt`
  - `CognitiveMigrationPlanner.kt`
  - `MigrationRecordRepository.kt`
  - `CoreConstitutionGuard.kt`
- Runtime path:
  - `MorimilViewModel.proposeCognitiveMigration()` creates a planned migration from selected memory events.
  - `approveCognitiveMigration()` marks it approved.
  - `executeCognitiveMigration()` appends execution event only after approval.
  - `rollbackCognitiveMigration()` records append-only compensation.
- Caller:
  - Memory UI / migration controls through `MorimilViewModel`.
- Guardrail:
  - Proposal starts with `approvedByUser=false`.
  - Execution requires `status=approved`.
  - Backup and rollback are marked required/available.
  - Constitution guard blocks Genesis Core mutation and reviews doctrine/policy changes.
- Risk:
  - Critical. This can change how memory is refined.
- Validation:
  - Propose migration.
  - Confirm status planned.
  - Approve in UI.
  - Execute.
  - Rollback.
  - Run memory audit after each step.

### 9. Improvements governance

- Main files:
  - `ImprovementProposalStore.kt`
  - `ImprovementsScreen.kt`
  - `ImprovementDecisionHistoryEntity`
- Runtime path:
  - Improvements UI scans current signals: chat errors, internal runtime issues, memory attention.
  - Observed proposals are stored in SharedPreferences for display.
  - Decisions are written to Room as auditable history.
- Caller:
  - Improvements tab.
- Guardrail:
  - Approved proposals show plan and validation, but do not execute automatic changes.
  - Observed proposals have duplicate and cooldown controls.
  - Decisions use `approveWithAudit()` / `denyWithAudit()`.
- Risk:
  - Medium/high. This is governance, not execution, but bad UX could imply false execution.
- Validation:
  - Trigger signal.
  - Analyze current signals.
  - Approve/deny.
  - Confirm audit section persists decision.

### 10. Core constitution guard

- Main files:
  - `CoreConstitutionGuard.kt`
  - `MigrationRecordRepository.kt`
- Runtime path:
  - Migration plans are evaluated before insertion.
  - Genesis Core mutation is denied.
  - Doctrine/policy amendment requires reviewed migration, verified chain, backup, rollback, and human approval.
- Caller:
  - `MigrationRecordRepository.planMigration()`.
- Guardrail:
  - Critical plans can be denied or marked review-required.
  - Blocked migrations can write audit evidence through memory repository when available.
- Risk:
  - Critical. This is constitutional safety.
- Validation:
  - Unit tests for deny/review/allow branches.
  - Attempt fake Genesis Core migration in test only and confirm denial.

### 11. Secret vault / reasoning credentials

- Main files:
  - `SecretVault.kt`
  - `ReasoningConfigStore.kt`
  - `MotorViewModel.kt`
- Runtime path:
  - Motor screen stores runtime profile.
  - `MorimilViewModel.sendMessage()` reads active slot and secret vault key.
  - `ReasoningKernel` receives runtime access without persisting it into memory context.
- Caller:
  - Motor UI and Chat send path.
- Guardrail:
  - Secrets must never be written into memory events, logs, kernel trace text, or docs.
- Risk:
  - Critical.
- Validation:
  - Configure slot.
  - Send prompt.
  - Inspect UI/log output for no leaked key.

### 12. Genesis Ultra canonical memory boundary

- Main files:
  - `GenesisUltraCanonicalMemory.kt`
  - `GenesisUltraMemoryEntities.kt`
  - `GenesisUltraMemoryDao.kt`
- Runtime path:
  - Accepts only a cryptographically recovered atomic-birth type-state.
  - Starts post-birth memory at sequence `1` and links to the exact sequence `0` birth artifact.
  - Requires the committed active Body/Key Epoch and a valid 64-byte Ed25519 signature.
  - Writes only `genesis_ultra_memory_events`; it never duplicates the root or writes legacy `memory_events`.
- Caller:
  - Deliberately limited to conformance/instrumentation tests while the birth gate is closed.
  - Production onboarding composition remains pending and must also provide secure Android key storage.
- Guardrail:
  - No unsigned fallback, no update/delete DAO, full-chain restart verification, and atomic rollback on failure.
  - Reference fields are refused until the neutral Genesis hash profile binds them.
- Risk:
  - Critical. This is the continuity stream of the one Instance.
- Validation:
  - Room migration `10 -> 11`.
  - API 30/35 signed append, root-link, no-legacy-duplication, rollback and coordinated-tamper tests.

## Known unresolved strategic gaps

| Gap | Why it matters | Priority |
|---|---|---:|
| Encrypted signed export / dry-run restore | Without this, phone loss can destroy continuity. | P0 |
| Production release hardening | Current project is development-phase, not release-grade. | P0 |
| PC executor automation boundary | Repo states this is not included yet. Must be designed with explicit approval and no irreversible actions by default. | P1 |
| Architecture map enforcement | This document exists, but no automated check enforces it yet. | P1 |
| Deeper audit of Project Vault and Agent Orchestration | Registered in container, but not fully audited in this map version. | P1 |
| On-device runtime | Should not start before export/reincarnation exists. | P2 |

## PR checklist for future organs

Before merging any new organ:

```text
[ ] Main file exists.
[ ] Registered in composition root or explicitly justified as stateless utility.
[ ] Has a caller.
[ ] Has UI, worker, use case, repository, or runtime path.
[ ] Has validation command.
[ ] Has risk level.
[ ] Memory mutation is append-only or approval-gated.
[ ] External effect is owner-approved.
[ ] Secrets are not logged, stored in memory, or shown in traces.
[ ] Build passes: .\gradlew.bat :app:assembleDebug
```

## Next audit target

P0 next work is not model expansion. It is continuity protection:

```text
Export cifrado y firmado
  -> manifest
  -> hashes
  -> signature verification
  -> dry-run restore
  -> explicit restore approval
  -> rollback/compensation event
```

Until that exists, any deeper autonomy increases blast radius without solving survival.
