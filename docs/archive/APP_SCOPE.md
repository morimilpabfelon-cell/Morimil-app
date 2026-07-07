# Morimil App Scope

## Current Scope

Morimil App is the native Android body for a phone-local Morimil instance.

```text
In scope:
  Android native application
  Genesis seed reader/verifier and local birth state
  Room/SQLite local memory
  append-only hash-linked memory events
  living memory snapshot
  memory organs
  knowledge capsules
  memory links/backlinks
  visual memory graph navigation
  recall schedule runtime
  rest cycle runtime
  cognitive migration flow
  local integrity audit
  voice controls
  provider-neutral reasoning motor configuration
  up to 10 encrypted reasoning API slots
  compatible model discovery
```

## Runtime Boundary

```text
Genesis:
  seed and doctrine source
  read-only from Morimil-app runtime

Phone:
  primary memory body
  identity, events, snapshots, organs, recalls, links, and migrations live here

Reasoning APIs:
  transport only
  no provider owns memory, identity, doctrine, or continuity

PC handoff:
  placeholder only in this app version
  no command execution from mobile yet
```

## Explicit Non-Scope

```text
No autonomous PC command execution.
No background shell access.
No autonomous GitHub mutation from the app.
No repository creation from the app.
No pull request creation from the app.
No merge.
No delete.
No workflow dispatch.
No background sync.
No production release.
No provider-owned memory.
No provider-owned identity.
No free-form graph editing yet.
```

## Local Write Policy

```text
Allowed local writes:
  Room/SQLite local identity
  Room/SQLite memory messages
  Room/SQLite memory events
  Room/SQLite snapshots
  Room/SQLite memory organs
  Room/SQLite recalls
  Room/SQLite links
  Room/SQLite migration records
  encrypted local reasoning slot secrets

Not allowed as automatic runtime writes:
  Morimil Genesis repository
  GitHub repositories
  PC filesystem
  cloud storage
```

## Integrity Claims

```text
Implemented:
  SHA-256 hash-linked memory events
  event chain and tail verification
  event quarantine boundary on local tail corruption
  SHA-256 hash-linked knowledge capsules
  shared MemoryIntegrityCore facade
  explicit UI integrity audit

Not implemented yet:
  non-exportable AndroidKeyStore Ed25519 event signing
  remote transparency log
  cloud backup
```

The current hash chain gives local tamper evidence. It is not claimed as hardware-backed signature resistance.

## Graph Claims

```text
Implemented:
  read-only visual graph canvas from memory_links
  recent overview graph without requiring a selected node
  focused graph mode for a selected memory event
  orphaned-link highlighting in the graph
  connected memory-event lookup by event hash
  tap navigation between memory-event nodes
  external linked nodes shown by node type
  textual backlinks beside the canvas

Not implemented yet:
  drag-and-drop graph editing
  manual edge creation from the canvas
  clustering/layout persistence
  graph search/filter controls
```

## Review Rule

Architecture docs must be updated whenever runtime boundaries change, especially around:

```text
memory ownership
reasoning providers
integrity guarantees
PC execution
GitHub writes
Genesis mutability
graph navigation and editing
```
