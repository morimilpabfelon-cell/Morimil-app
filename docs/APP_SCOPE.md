# Morimil App Scope

## Phase 5C Scope

```text
Add a controlled GitHub write proposal gate.
Build and validate proposed file changes locally.
Require human approval before a proposal can pass review.
Keep actual GitHub write execution blocked.
```

## Explicit Non-Scope

```text
No repo creation, EXCEPT the single onboarding-time fork of the Genesis
  repo under the token owner's own account (see docs/GENESIS_FORK_MODEL.md).
No file upload execution.
No PR creation.
No merge.
No delete.
No workflow dispatch.
No background sync.
No autonomous mutation.
No PC command execution from the app.
No production release.
```

## Repository Boundary

```text
Morimil-app can preview its own GitHub repository metadata.
Morimil-app can create local write proposals for docs/proposals/.
Morimil Genesis repository remains read-only and is not modified.
```

## Allowed Proposal Target

```text
owner: morimilpabfelon-cell
repo: Morimil-app
branch: main
path prefix: docs/proposals/
```
