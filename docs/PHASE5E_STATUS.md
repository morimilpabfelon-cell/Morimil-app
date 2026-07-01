# Morimil App Phase 5E Status

## Verdict

```text
MORIMIL_APP_PHASE5E_ALIGNMENT_AND_FIXES_APPLIED
```

## Why this phase exists

A full audit found the app diverged from the intended flow in two ways: it
never actually read Genesis from GitHub (only a stale bundled snapshot with
an invented schema), and there was no concept of a locally-named instance
tied to its own fork. This phase closes both gaps, fixes three concrete
bugs found during review, and implements the confirmed onboarding flow:
"el Bloque Genesis es la semilla, el celular es la tierra" -- the app forks
Genesis under the user's own GitHub account automatically during
first-install onboarding, then names the local instance tied to that fork.

## Bugs fixed

```text
1. Bottom nav icon collision: Proposal, Projects, and Handoff (label "PC")
   all rendered the same "P" icon (derived from label.first()). Each tab now
   has an explicit, unique 2-letter icon code.
2. Version drift: ROADMAP.md was missing Phase 5D entirely; build.gradle.kts
   was frozen at versionName "0.2.0-phase2" three phases behind; the bundled
   genesis asset said current_app_phase "phase_4_genesis_reader" one phase
   behind. All three now reflect Phase 5D/5E accurately.
3. Workspace proposal validation lived only in the UI (UserWorkspaceScreen),
   not enforced at the ViewModel/Repository layer -- any future entry point
   could have bypassed the "Genesis cannot be a workspace target" check.
   Moved into MemoryRepository (later superseded entirely by fix #4).
4. Workspace/fork split-brain: after the fork model was added, onboarding
   created a fork (stored on LocalInstanceIdentityEntity) but the separate
   Workspace screen still let the user free-type an unrelated owner/repo
   into UserWorkspaceEntity -- two disconnected repos competing to
   represent the same instance, breaking the "Genesis is the seed, your
   fork is your own chain" model. Fixed: birthLocalIdentity now syncs
   UserWorkspaceEntity's repo fields to the fork automatically; the
   Workspace screen no longer accepts free-text repo entry, it only shows
   the fork (read-only) plus an editable local display name.
```

## What was added

```text
GenesisIdentity.kt + MobileAppCapabilities.kt (replaces GenesisBlock.kt):
  splits the real Genesis contract (fetched from GitHub, matches
  identity/orchestrator.identity.json field-for-field: agent_id, alias,
  role, owner, risk_tier, allowed_actions, disallowed_actions, doctrine_ref,
  policy_ref) from the app's own local capability flags (what THIS app
  phase implements -- local_memory, voice, github_read, github_write, pc,
  production_release). These were previously and incorrectly merged into
  one invented shape.

GenesisReader.kt (rewritten): tries a live GET against
  raw.githubusercontent.com/morimilpabfelon-cell/Morimil/main/identity/
  orchestrator.identity.json first; falls back to a corrected bundled
  snapshot (morimil_genesis_identity.json, matching the real schema) only
  if the network read fails. Always reports which source was used
  (GITHUB_LIVE or BUNDLED_FALLBACK) so the user is never misled about
  what they're looking at. Runs on Dispatchers.IO.

LocalInstanceIdentityEntity.kt + Room migration 2->3: the phone's own named
  instance, born exactly once (INSERT ... ABORT, not REPLACE). Now also
  stores the fork reference (forkOwner, forkRepo, forkHtmlUrl) so the rest
  of the app always knows where this instance's own GitHub state lives.

GitHubForkClient.kt: the app's first actual GitHub write execution,
  scoped as tightly as every other write-adjacent piece of this app.
  Source repo is hardcoded (morimilpabfelon-cell/Morimil, nothing else is
  forkable through this client). Destination account is derived from the
  token via GET /user, never typed by the user, so there's no way to
  redirect a fork to an unintended account. doctrine/GENESIS_FORK_MODEL.md
  and APP_SCOPE.md were updated with a narrow, named exception documenting
  exactly this and nothing more -- every other write boundary in the app
  (Phase 5C's write-proposal-only gate, no PR/merge/delete/dispatch) is
  unchanged.

OnboardingScreen.kt (v2): shown on first install, before the main tab UI,
  only while no local identity exists. Fetches and shows Genesis read-only,
  takes a GitHub token (reusing SecretVault), forks Genesis under that
  token's account, then names the instance tied to that fork -- one guided
  flow, no manual GitHub navigation.
```

## Applied files

```text
app/build.gradle.kts (modified -- version bump)
app/src/main/AndroidManifest.xml (modified -- INTERNET usage comment, now covers fork)
app/src/main/assets/morimil_genesis_block.json (deleted)
app/src/main/assets/morimil_genesis_identity.json (new -- corrected schema)
app/src/main/java/com/morimil/app/data/genesis/GenesisBlock.kt (deleted)
app/src/main/java/com/morimil/app/data/genesis/GenesisIdentity.kt (new)
app/src/main/java/com/morimil/app/data/genesis/MobileAppCapabilities.kt (new)
app/src/main/java/com/morimil/app/data/genesis/GenesisReader.kt (rewritten)
app/src/main/java/com/morimil/app/data/local/LocalInstanceIdentityEntity.kt (new)
app/src/main/java/com/morimil/app/data/local/MemoryDao.kt (modified)
app/src/main/java/com/morimil/app/data/local/MorimilDatabase.kt (modified -- v2->v3)
app/src/main/java/com/morimil/app/data/repository/MemoryRepository.kt (modified)
app/src/main/java/com/morimil/app/github/GitHubForkClient.kt (new)
app/src/main/java/com/morimil/app/security/SecretVault.kt (modified -- method rename)
app/src/main/java/com/morimil/app/ui/GitHubSyncGateScreen.kt (modified -- call site update)
app/src/main/java/com/morimil/app/ui/MorimilViewModel.kt (modified)
app/src/main/java/com/morimil/app/ui/MorimilApp.kt (modified -- onboarding gate, fork display)
app/src/main/java/com/morimil/app/ui/OnboardingScreen.kt (new)
app/src/main/java/com/morimil/app/ui/UserWorkspaceScreen.kt (modified)
docs/ROADMAP.md (modified -- Phase 5D added)
docs/GENESIS_FORK_MODEL.md (modified -- narrow fork exception documented)
docs/APP_SCOPE.md (modified -- narrow fork exception documented)
docs/PHASE5E_STATUS.md (this file)
```

## Confirmed resolved

```text
Both morimilpabfelon-cell/Morimil and Morimil-app are confirmed public.
The live Genesis fetch was re-tested against the real repo and returned
the exact schema GenesisReader.kt expects -- verified working, not assumed.
```

## Verified before delivery

```text
All 24 .kt files: brace/paren balanced (no truncated/malformed files)
All dependency-free files: compiled clean with a real kotlinc, exit 0
Every internal `import com.morimil.app...` resolves to a real declared
  class/object/interface in the repo
Room migration 2->3: every column type/nullability matches the entity
  field-for-field (same method used to verify migration 1->2 during audit)
Zero business-contamination strings (ionPAY, Ion Exchange, etc.)
```

## Known open item

```text
UserRepoProposalValidator.kt is no longer called from anywhere (its only
caller was removed in fix #4 above). Left in place, unused but correct,
in case a future feature needs to validate a SECOND repo beyond the
instance's own fork. Not a bug -- flagged so it isn't a silent surprise.

GitHubForkClient.kt uses org.json.JSONObject, which is provided by the
Android runtime, not available in this sandbox's plain kotlinc (same
limitation confirmed against the pre-existing GitHubReadOnlyClient.kt,
which fails identically even though it was already in the repo untouched).
Structural checks (brace balance, import resolution, viewModel call-site
resolution) and careful manual review were done in place of a real
compile. Please confirm a real build in Android Studio before relying on
this in production.
```

## Explicitly NOT done in this phase -- flagged, not silently skipped

```text
"Conversa contigo" (real AI conversation): the Chat screen still returns a
  hardcoded canned reply. Wiring a real LLM (e.g. Claude API) into the app
  is a new phase on its own -- it needs API key handling (same SecretVault
  pattern already used for GitHub), a real request/response loop, and
  safety/moderation design. Not a small fix; scoping this is the natural
  next step once this phase is confirmed working.

"Detecta desviaciones" (deviation detection): no mechanism exists anywhere
  in the app today that flags when a conversation or action drifts from the
  Genesis doctrine. This depends on the real-AI phase above being built
  first, since deviation detection needs something to be watching the
  actual conversation.
```

## Manual verification (Android Studio, since this sandbox cannot run Gradle)

```powershell
cd C:\Users\morim\Morimil-app
.\gradlew.bat :app:assembleDebug
```

## Next gate

```text
MORIMIL_APP_PHASE5E_ALIGNMENT_REVIEW
```
