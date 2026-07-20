# Hybrid authority UI presentation v0

This layer presents the bounded hybrid-authority result in Chat without changing runtime authority.

## Visible states

```text
DISABLED
ACCEPTED_DETERMINISTIC
ACCEPTED_STRICT_CONSENSUS
ABSTAINED
```

The default remains `DISABLED` because `HybridAuthorityRuntimePolicy.hybridAuthorityRuntimeEnabled` remains false in normal construction.

The Chat surface displays only:

- a fixed headline;
- a fixed route label;
- a fixed user-facing explanation;
- the public authority version when a decision exists.

It does not display primary replies, verifier replies, raw findings or internal traces.

## Runtime path

```text
ReasoningKernel
  -> IntrinsicTriMotorCoordinator
  -> TriMotorFinalizationStatus + HybridAuthorityDecision
  -> HybridAuthorityPresentationV0
  -> process-local HybridAuthorityPresentationStore
  -> ChatScreenWithAuthorityStatus
```

The store is UI state only. It is not written to living memory, doctrine, policy, databases or audit evidence.

## Fail-closed behavior

Inconsistent combinations are rejected. Examples:

- legacy finalization with an authority decision;
- accepted finalization without accepted content;
- abstained finalization with accepted content;
- abstained finalization whose decision status is not `ABSTAINED`.

An abstention is presented as a bounded refusal to accept a response. Raw internal reasons are not surfaced.

## Boundary

This change does not:

- enable the hybrid-authority runtime;
- load, install, certify, sign or promote the Gemma 3n candidate;
- change model selection;
- grant memory, identity, continuity, goal or lifecycle authority;
- persist presentation status as autobiographical memory.
