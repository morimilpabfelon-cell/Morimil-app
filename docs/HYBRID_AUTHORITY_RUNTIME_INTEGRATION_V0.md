# Hybrid authority runtime integration v0

Status: structural integration only. Disabled by default. No model installation or production promotion.

## Purpose

This layer carries structured authority data through the intrinsic coordinator without allowing a generated verifier reply to replace the primary reply automatically when hybrid authority is enabled.

The coordinator preserves separately:

- primary candidate;
- verifier candidate;
- authority decision;
- authority route;
- finalization status;
- findings and motor versions.

## Feature flag

```kotlin
HybridAuthorityRuntimePolicy(
    hybridAuthorityRuntimeEnabled = false
)
```

The default application construction uses the default coordinator policy, so current runtime behavior remains unchanged.

When disabled:

- the existing activation policy remains unchanged;
- the metacognitive motor receives the primary candidate;
- a nonblank verifier reply preserves the legacy replacement behavior;
- `finalizationStatus` is `LEGACY_UNROUTED`;
- `authorityDecision` is `null`.

When enabled in explicit research construction:

- the verifier runs blind with `candidateReply = null`;
- deterministic routes treat neural outputs as advisory;
- generative routes require strict `FINAL:<value>` consensus;
- disagreement, invalid format, unknown task kind, or missing prompt produces abstention;
- abstention returns no generated final reply (`reply == ""`) and records `ABSTAINED_BY_AUTHORITY`.

## Conservative task classification

`ReasoningTaskKindClassifierV0` recognizes only bounded patterns already supported by the router:

- arithmetic;
- restricted Python semantics;
- locally checkable claims;
- bounded logic;
- bounded Spanish relation questions;
- exact-output instructions.

Ambiguous input remains `UNKNOWN`. The kernel records the classification and passes the clean user input as the authority prompt. Classification alone does not enable the router.

## Safety boundary

This integration adds no:

- model weights;
- artifact loader or installer;
- network or provider configuration;
- memory, identity, continuity, lifecycle, or persistence writer;
- signing key;
- production feature activation.

The router decides only over request-scoped candidate text. Morimil retains identity, local memory, continuity, goals, lifecycle, and final authority.

## Required before enabling

Runtime enablement remains blocked by:

1. exact signed artifact provenance;
2. Android arm64 functional validation;
3. memory, latency, thermal, and battery measurements;
4. long-context and resource-release testing;
5. adversarial and out-of-distribution evaluation;
6. explicit UI handling for structured abstention;
7. production review of every deterministic parser and route.
