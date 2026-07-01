# Morimil App Roadmap

## Phase 1 - Native Android Skeleton

```text
accepted
```

## Phase 2 - Local Living Memory

```text
accepted
```

## Phase 3 - Voice Interface

```text
accepted
```

## Phase 4 - Genesis Reader

```text
accepted
```

## Phase 5A - Keystore Credential Gate

```text
accepted
```

## Phase 5B - Controlled GitHub Read-Only Sync Preview

```text
accepted
manual GET repo metadata
no writes
no background sync
```

## Phase 5C - Controlled GitHub Write Proposal

```text
ready for review
generate local file upload proposal
validate allowed target and path
human confirmation required
no autonomous mutation
no GitHub write execution
```

## Phase 5D - User Workspace Bootstrap

```text
ready for review
local phone-owned workspace state (Room/SQLite)
optional user-owned repo proposal
Genesis and Morimil-app blocked as workspace storage targets
human approval required for any repo proposal
```

## Phase 5E - Genesis-to-Fork Onboarding

```text
ready for review
live Genesis identity + doctrine fetch from GitHub, bundled fallback
automatic fork of Genesis under the user's own account during onboarding
local instance identity born once, tied to that fork
workspace synced to the fork automatically -- one repo per instance, never two
```

## Phase 5F - Real Conversation

```text
ready for review
Claude API integration (Anthropic API key stored via SecretVault)
system prompt built from the real fetched Genesis identity and doctrine
recent phone-local memory (up to 50 messages) sent as context each turn
no on-device model, no separate deviation classifier -- the system prompt
  and the full conversation log in the Memory tab are the two safeguards
```

## Phase 6 - PC Handoff

```text
prepare commands for PC
track result notes
sync status
no command execution from mobile
```
