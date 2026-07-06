# Morimil Reasoning Kernel

Morimil must not depend on a remote API for operational continuity. Remote or local LLMs are model backends. They are not Morimil's identity, memory, policy, or final authority.

This document defines the first non-negotiable contract for the local reasoning layer.

## Current weakness being corrected

The app already has local identity, Room/SQLite memory, hash-linked memory events, memory organs, recall schedules, rest cycles, cognitive migrations, tool capability policy, and immune policy.

The weak point is that chat reasoning is still assembled directly around a model call. A model can generate text, but Morimil needs a local kernel that owns state, mode selection, policy, memory retrieval, fallback behavior, and traceability.

## Kernel rule

Every conversation turn must eventually pass through a single local `ReasoningKernel`.

The model may propose language or analysis. The kernel decides what mode is active, what memory is used, what policy applies, what result is accepted, and what is recorded.

## Operating modes

```text
ONLINE_SUPERIOR
Remote API is available and approved. Morimil still controls memory, prompt construction, policy and final acceptance.

LOCAL_OPERATIVE
A local model endpoint is available, usually localhost, 127.0.0.1 or emulator bridge. Morimil remains functional without remote API.

SAFE_DEGRADED
No usable model is available. Morimil must not pretend superior reasoning. It should answer from local state, memory summaries, deterministic rules, and explicit uncertainty.
```

The API can improve intelligence. The API cannot be required for continuity.

## Minimum reasoning state

Each turn must have a local state object with at least:

```text
input
mode
intent
memory context summary
capsule context summary
model backend selected
policy decision
critic findings
final reply or error
trace events
```

Without a `ReasoningState`, Morimil only chats. With it, Morimil reasons in an auditable way.

## Model hierarchy

```text
Deterministic policy and integrity checks
  > Genesis and local memory constraints
  > Tool and immune policy
  > Reasoning Kernel
  > Model backend
```

The model never owns memory writes, migrations, tool execution, secrets, production actions, or irreversible actions.

## Safe degraded behavior

If no model is available, Morimil should still be able to:

- accept the user message
- record local memory when allowed
- retrieve relevant memory context
- summarize what it can infer locally
- refuse to fabricate advanced reasoning
- queue or propose later processing
- preserve memory integrity
- run rest/recall maintenance when safe

## First implementation boundary

The first implementation must be conservative:

1. Add kernel state classes.
2. Add operating mode resolver.
3. Add deterministic fallback response.
4. Add `ReasoningKernel` as a central pipeline compatible with the current API client.
5. Do not rewrite memory schemas.
6. Do not remove existing ViewModel behavior until local compilation confirms the kernel is stable.

## Future expansion

After the foundation compiles, the next phases are:

```text
Fase 1: wire Chat -> ReasoningKernel
Fase 2: add local critic
Fase 3: add kernel trace event logging
Fase 4: add local embeddings/retrieval
Fase 5: add local model backend selection
Fase 6: add benchmark scores for memory recall, contradiction and API independence
Fase 7: add self-improvement proposal loop with approval, diff, audit and rollback
```

This contract protects Morimil from becoming a large prompt attached to a provider. Morimil must become a local cognitive runtime with replaceable model backends.
