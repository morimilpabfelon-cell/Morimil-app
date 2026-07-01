# Morimil App Genesis Fork Model

## Verdict

```text
MORIMIL_APP_GENESIS_FORK_MODEL_DEFINED
```

## Core Rule

```text
The Genesis Block is the starting point.
The Genesis Block is not the runtime storage target.
The phone is the primary local runtime state.
A user-owned GitHub repository is optional.
Every user workspace derived from Genesis is treated as a fork-like descendant.
```

## Architecture

```text
Genesis Block:
  source doctrine
  identity seed
  starting contract
  read-only from the app

Phone:
  primary local state
  Room/SQLite memory
  voice interaction
  local decisions
  user workspace configuration

Optional GitHub Repo:
  created or selected only by user choice
  stores approved user workspace state
  never mutates Genesis
  never mutates Morimil-app as runtime data
```

## Non-Negotiable Boundary

```text
No runtime memory is written into Morimil Genesis.
No user project state is written into Morimil Genesis.
No automatic GitHub repo creation.
No automatic GitHub file upload.
No hidden background sync.
No cross-user shared state.
```
