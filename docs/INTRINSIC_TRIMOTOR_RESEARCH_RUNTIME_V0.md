# Intrinsic tri-motor research runtime v0

Status: **full tri-motor remains research-only and isolated**

## Purpose

This runtime registers exactly Morimil's three intrinsic computation roles:

```text
INTUITIVE
DELIBERATIVE
METACOGNITIVE
```

It enables `HybridAuthorityRuntimePolicy` only inside the isolated research object. It does not install an artifact, grant a model authority over Morimil or activate the full tri-motor in normal Chat.

A separate later registry, `MorimilNormalIntrinsicRuntimeV0`, activates only the bounded local Intuitive role in normal runtime. That narrow activation does not make this research runtime production-active.

## Motor boundaries

### IntuitiveMotorV0

- uses one injected local `MorimilIntuitiveCoreV0`;
- accepts request-scoped input only;
- cannot act as a candidate verifier;
- returns a candidate, never a final authority decision;
- exposes no provider, credential, memory, identity, lifecycle or persistence capability.

### DeliberativeMotorV0

- reuses the existing bounded local deliberative motor;
- supports one to eight passes according to task complexity;
- releases its request-scoped state in `finally`;
- remains compatible with the immutable v0.2 physical research baseline;
- is not certified, signed, installed or active in normal runtime.

### MetacognitiveMotorV0

- uses one injected local `MorimilMetacognitiveCoreV0`;
- receives the task and authority prompt without the primary candidate;
- rejects candidate-aware verification;
- produces an independent candidate for the authority layer;
- cannot accept its own output;
- remains inactive in normal runtime.

## Authority flow

```text
request
  -> activation policy
  -> intuitive or deliberative primary candidate
  -> blind metacognitive candidate when verification is requested
  -> HybridAuthorityRouterV0
  -> deterministic acceptance, strict consensus or abstention
```

The research runtime always enables hybrid authority. Neural agreement is not accepted on deterministic routes. Unknown, unsupported, malformed or disagreeing generative results abstain.

## Construction

```kotlin
val researchRuntime = IntrinsicTriMotorResearchRuntimeV0.create(
    intuitiveMotor = IntuitiveMotorV0(intuitiveCore),
    deliberativeMotor = DeliberativeMotorV0(deliberativeCore),
    metacognitiveMotor = MetacognitiveMotorV0(metacognitiveCore)
)
```

The caller must supply real local cores. This runtime does not download a model or claim that a general neural Intuitive, Deliberative or Metacognitive artifact is installed.

## Normal-runtime boundary

Normal application construction now uses:

```kotlin
MorimilNormalIntrinsicRuntimeV0.createCoordinator()
```

That registry contains exactly one role:

```text
INTUITIVE -> IntuitiveMotorV0(BoundedLocalIntuitiveCoreV0)
```

The following remain disabled in normal Chat:

```text
DELIBERATIVE
METACOGNITIVE
full tri-motor research runtime
HybridAuthorityRuntimePolicy enablement
```

Unsupported or malformed requests from the bounded Intuitive core fail closed and retain the existing external or deterministic fallback behavior.

## Validation in the original research runtime

Tests require:

- exactly all three intrinsic roles are registered in the isolated research runtime;
- light arithmetic may use Intuitive, but deterministic authority overrides an incorrect candidate;
- deep logic uses Deliberative plus blind Metacognitive verification;
- matching strict candidates may be accepted;
- disagreement produces an empty final reply and structured abstention;
- Intuitive cannot verify a candidate;
- Metacognitive cannot see a candidate;
- the research-runtime surface exposes no provider, memory, identity, lifecycle, installer or downloader capability;
- the normal `HybridAuthorityRuntimePolicy()` default remains false.

## Current completion state

```text
bounded Intuitive core:                       implemented
bounded Intuitive normal-runtime activation:  true
bounded Metacognitive core:                   implemented, research-only
Deliberative normal-runtime activation:       false
Metacognitive normal-runtime activation:      false
full tri-motor normal-runtime activation:     false
general neural Intuitive artifact:            not supplied
general neural Metacognitive artifact:        not supplied
artifact certification/signature/install:     false
```

The next activation requires a separate PR with explicit evidence, resource limits, physical Android validation and an unchanged Morimil authority boundary.
