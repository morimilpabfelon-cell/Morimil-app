# Morimil App Genesis Fork Model

## Verdict

```text
MORIMIL_APP_GENESIS_FORK_MODEL_DEFINED
```

## Core Rule

```text
The Genesis Block is the seed. The phone is the soil.
The Genesis Block is not the runtime storage target.
The phone is the primary local runtime state.
Onboarding forks Genesis under the user's own GitHub account automatically,
  once, with a token the user provides -- this is how the instance is
  planted. It is not optional; it is how "crea instancia local" happens.
A SEPARATE, additional user-owned GitHub repository for extra workspace
  state (Phase 5D) remains fully optional and user-chosen.
Every instance's fork is its own -- no cross-instance shared state, ever.
```

## Architecture

```text
Genesis Block:
  source doctrine
  identity seed
  starting contract
  read-only from the app -- the app only ever reads it, never writes to it

Onboarding fork (automatic, once):
  created by the app under the token owner's own account
  source repo hardcoded to morimilpabfelon-cell/Morimil
  destination account derived from the token, never typed by the user
  this fork becomes the instance's home -- not Genesis, not Morimil-app

Phone:
  primary local state
  Room/SQLite memory
  voice interaction
  local decisions
  local instance identity (alias, born once)

Optional Additional GitHub Repo (Phase 5D, separate from the onboarding fork):
  created or selected only by user choice
  stores approved extra workspace state
  never mutates Genesis
  never mutates Morimil-app as runtime data
```

## Non-Negotiable Boundary

```text
No runtime memory is written into Morimil Genesis.
No user project state is written into Morimil Genesis.
No automatic GitHub repo creation, EXCEPT: a fork of the Genesis repo
  (morimilpabfelon-cell/Morimil) specifically, created once during
  first-install onboarding, under the token owner's own account. The
  source repo is hardcoded in app code; the destination account is
  derived from the token via GET /user, never typed by the user. No
  other repo creation, and no repo creation outside onboarding, is
  permitted through this exception.
No automatic GitHub file upload.
No hidden background sync.
No cross-user shared state.
```
