# Genesis Memory Core v1

## Verdict

```text
GENESIS_MEMORY_CORE_V1_DEFINED
```

## Purpose

The Genesis package defines how Morimil can remember, learn, and improve without losing identity.

Genesis is not overwritten by growth. Growth is appended and linked back to Genesis.

## Core Artifacts

```text
schemas/memory_event.v1.schema.json
schemas/knowledge_capsule.v1.schema.json
schemas/self_update_proposal.v1.schema.json
schemas/autobiographical_snapshot.v1.schema.json
schemas/recall_schedule.v1.schema.json
schemas/interaction_state.v1.schema.json
schemas/rest_cycle.v1.schema.json
schemas/memory_link.v1.schema.json
schemas/memory_snapshot.v1.schema.json
schemas/migration_record.v1.schema.json
schemas/genesis_manifest.v1.schema.json
doctrine/evolution_rules.md
policy/memory_policy.rego
```

## Runtime Model

```text
Genesis Core
  -> Memory Event Chain
  -> Knowledge Capsules
  -> Self-Update Proposals
  -> Autobiographical Snapshots
  -> Recall Schedules
  -> Interaction State
  -> Rest Cycle
  -> Memory Links
  -> Memory Snapshots
  -> Migration Records
  -> Genesis Manifest
```

## Learning Flow

```text
user asks Morimil to learn
  -> source is read or summarized
  -> knowledge capsule is created
  -> capsule links to memory events and projects through memory_link records
  -> if behavior should change, self-update proposal is created
  -> user/review gate accepts or rejects
  -> accepted change creates a migration_record before runtime mutation
```

## Non-Negotiables

```text
Genesis Core is immutable after birth.
Memory is private-local by default.
API providers do not own memory.
Learning does not become doctrine without review.
Conflicts preserve evidence and ask for a decision.
Genesis bundle must be verified before first birth.
Memory links and snapshots are append-only.
Migrations require explicit approval and rollback strategy.
```

## Memory Classes

```text
working memory: active context sent to the reasoning provider
episodic memory: lived events with hash continuity
semantic memory: learned concepts and rules
autobiographical memory: periodic narrative continuity
strategic memory: project direction and long-range decisions
associative memory: auditable links between memories and knowledge
rest cycle: scheduled consolidation while the user rests
```

## Rest Cycle Contract

The rest cycle is allowed to organize memory while the user is asleep or the device is idle.

```text
allowed:
  consolidate memories
  update decay and reinforcement
  generate autobiographical snapshots
  generate strategic memory snapshots
  create auditable memory links
  schedule recalls
  prepare next-wake context

blocked:
  mutate Genesis Core
  execute external actions
  delete memory silently
  claim biological emotion or consciousness
```

## Mobile Genesis Bundle

Before Morimil-app becomes the body, it must receive a complete Genesis bundle.

```text
assets/genesis/
  identity/orchestrator.identity.json
  doctrine/doctrine.md
  doctrine/evolution_rules.md
  policy/policy.rego
  policy/memory_policy.rego
  schemas/*.json
  genesis_manifest.json
```

At first birth, the app copies this bundle into private internal storage, verifies the manifest, creates the first hash-linked birth event, and starts append-only local memory.

Runtime v1 implements local SHA-256 event chaining. Ed25519 event signatures remain a planned integrity upgrade, not an active runtime guarantee.

Examples remain in the repository as design fixtures. They are not part of the installable mobile seed.

The manifest is generated from the source bundle with:

```text
npm run genesis:manifest
```

The app must treat the generated `genesis/genesis_manifest.json` as the bundle receipt, not as mutable runtime memory.
