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
- only arithmetic, restricted-code semantics and locally checkable claims may reach an accepting authority route;
- `LOGIC`, `SPANISH` and `INSTRUCTION` remain motor-selection classifications but are downgraded to `UNKNOWN` at the final-authority boundary;
- matching generated `FINAL:<value>` replies remain advisory and cannot authorize a final answer;
- unsupported task kind, missing prompt, malformed deterministic input or unavailable deterministic proof produces abstention;
- abstention returns no generated final reply (`reply == ""`) and records `ABSTAINED_BY_AUTHORITY`.

## Conservative task classification

`ReasoningTaskKindClassifierV0` recognizes:

- arithmetic;
- restricted Python semantics;
- locally checkable claims;
- bounded logic-like prompts;
- bounded Spanish relation prompts;
- exact-output instructions.

Classification does not grant authority. The first three categories may be reduced to exact local computation. Logic, Spanish and instruction classifications remain useful for activation and telemetry only; they cannot be finalized until a separate deterministic verifier exists for the specific task.

Ambiguous input remains `UNKNOWN`. The kernel records the classification and passes the clean user input as the authority prompt.

## Safety boundary

This integration adds no:

- model weights;
- artifact loader or installer;
- network or provider configuration;
- memory, identity, continuity, lifecycle, or persistence writer;
- signing key;
- production feature activation.

The authority layer decides only over request-scoped candidate text and exact local reductions. Agreement between generated candidates is not proof. Morimil retains identity, local memory, continuity, goals, lifecycle and final authority.

## Required before broader acceptance

Any new accepting route requires:

1. a deterministic or independently checkable verifier for the exact task;
2. adversarial tests demonstrating zero false acceptance in the bounded domain;
3. explicit output grammar and normalization rules;
4. resource-release and capability-boundary tests;
5. physical Android validation where local inference is involved;
6. explicit UI handling for structured abstention;
7. review of every parser, reduction and authority transition.
