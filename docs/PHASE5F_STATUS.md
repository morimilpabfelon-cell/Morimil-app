# Morimil App Phase 5F Status

## Verdict

```text
MORIMIL_APP_PHASE5F_REAL_CONVERSATION_APPLIED
```

## Why this phase exists

Every prior phase built plumbing (memory, genesis reading, forking) but the
Chat screen always returned one hardcoded canned reply -- there was no real
intelligence in the app at all. This phase wires in the actual conversation.

## How memory and the model fit together

```text
Memory: phone-local, Room/SQLite. Grows every turn. This IS "aprende poco
  a poco" -- it is the only thing that gives Morimil continuity.

Model (Claude, via Anthropic API): stateless between calls. It has no
  memory of its own. Every single request sends the recent phone-local
  history as context, which is what makes the reply feel continuous.

Neither one replaces the other. Memory without a model can't respond.
A model without memory forgets everything between messages.
```

## What was added

```text
ai/ClaudeApiClient.kt: calls https://api.anthropic.com/v1/messages.
  Same HttpURLConnection pattern already proven in GitHubReadOnlyClient
  and GitHubForkClient -- no new networking library. Model is
  claude-sonnet-5, max_tokens 1024, fixed internally (not user-configurable
  in the UI, by explicit request).

ai/SystemPromptBuilder.kt: builds the system prompt from the REAL fetched
  Genesis identity (role, allowed_actions, disallowed_actions) plus the
  real doctrine text (see below) -- never an invented or hardcoded prompt.

GenesisReader.kt (extended): now also fetches the doctrine text referenced
  by the identity's own doctrine_ref field (dynamically, e.g.
  "doctrine/doctrine.md"), live from GitHub. No bundled fallback for
  doctrine specifically -- a guessed/stale doctrine copy is worse than
  none, so a failed fetch just means the system prompt falls back to the
  allowed/disallowed action lists alone (which do have a verified bundled
  fallback).

SecretVault.kt (generalized): was GitHub-token-only; now stores any named
  secret (github_token, anthropic_api_key) under the same proven
  AndroidKeystore AES-GCM encryption. Convenience wrappers kept for both
  call sites so nothing else needed to change.

MorimilViewModel.kt: sendMessage now actually calls Claude, with a real
  isSending state (for the "escribiendo..." indicator) and chatError state
  (shown inline, never silently swallowed).

ChatScreen (in MorimilApp.kt): gated behind an Anthropic API key -- if none
  is stored yet, shows a save-key step first, matching the same pattern
  already used for the GitHub token in onboarding/Sync.
```

## A real bug found and fixed during this phase

```text
Original implementation read the message history AFTER calling
repository.addUserMessage(), assuming the just-inserted message would
already be reflected in the messages StateFlow. Room's Flow emission is
not guaranteed synchronous with the write completing -- there is a real
possibility the user's own current message would be missing from what
gets sent to Claude. Fixed: the current turn is now built directly from
the known message text and explicitly appended, never relying on the
StateFlow having already caught up.
```

## What this does NOT do -- flagged, not silently skipped

```text
No on-device model. No local "learning" separate from the phone's own
  stored memory feeding the cloud model as context each turn.

No automated deviation classifier. The two safeguards in this phase are:
  (1) the system prompt itself, built from the real doctrine and action
  lists, instructing the model to refuse anything disallowed; and (2) the
  full conversation is visible and reviewable in the existing Memory tab
  at any time. A more sophisticated automated flagging system was not
  built -- it would need real design work of its own to avoid false
  positives/negatives, and is a reasonable next step to discuss.

No per-user configuration of model or context window size -- both are
  fixed internally (claude-sonnet-5, last 50 messages) per explicit
  request, not exposed in the UI.
```

## Manual test command

```powershell
cd C:\Users\morim\Morimil-app
.\gradlew.bat :app:assembleDebug
```

## Next gate

```text
MORIMIL_APP_PHASE5F_REAL_CONVERSATION_REVIEW
```
