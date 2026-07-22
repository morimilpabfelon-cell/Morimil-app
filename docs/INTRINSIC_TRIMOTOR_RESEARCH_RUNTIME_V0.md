# Intrinsic tri-motor research runtime v0

Status: **research-only, isolated, normal runtime disabled**

## Purpose

This runtime registers exactly Morimil's three intrinsic computation roles:

```text
INTUITIVE
DELIBERATIVE
METACOGNITIVE
```

It enables `HybridAuthorityRuntimePolicy` only inside the isolated research object. It does not modify `MorimilAppContainer`, install an artifact, activate normal Chat construction or grant a model authority over Morimil.

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
- cannot accept its own output.

## Authority flow

```text
request
  -> activation policy
  -> intuitive or deliberative primary candidate
  -> blind metacognitive candidate when verification is requested
  -> HybridAuthorityRouterV0
  -> deterministic acceptance, strict consensus or abstention
```

The runtime always enables hybrid authority. Neural agreement is not accepted on deterministic routes. Unknown, unsupported, malformed or disagreeing generative results abstain.

## Construction

```kotlin
val researchRuntime = IntrinsicTriMotorResearchRuntimeV0.create(
    intuitiveMotor = IntuitiveMotorV0(intuitiveCore),
    deliberativeMotor = DeliberativeMotorV0(deliberativeCore),
    metacognitiveMotor = MetacognitiveMotorV0(metacognitiveCore)
)
```

The caller must supply real local cores. This PR does not invent weights, download a model or claim that Intuitive and Metacognitive neural artifacts already exist.

## Normal-runtime boundary

The application container remains unchanged and continues to construct:

```kotlin
IntrinsicTriMotorCoordinator()
```

with no intrinsic motors registered. The default hybrid-authority policy remains disabled. Moving this research runtime into normal Morimil use requires separate evidence and explicit authorization.

## Validation in this PR

Tests require:

- exactly all three intrinsic roles are registered;
- light arithmetic may use Intuitive, but deterministic authority overrides an incorrect candidate;
- deep logic uses Deliberative plus blind Metacognitive verification;
- matching strict candidates may be accepted;
- disagreement produces an empty final reply and structured abstention;
- Intuitive cannot verify a candidate;
- Metacognitive cannot see a candidate;
- the research-runtime surface exposes no provider, memory, identity, lifecycle, installer or downloader capability;
- the normal `HybridAuthorityRuntimePolicy()` default remains false.

## Explicitly not completed

```text
real Intuitive core artifact:              not supplied
real Metacognitive core artifact:          not supplied
120-case tri-motor benchmark:              not executed
physical Android tri-motor run:            not executed
normal runtime activation:                 false
artifact certification/signature/install:  false
```

The next evidence step is to supply explicit local research cores and run the frozen 120-case benchmark against Morimil's final authority result rather than the raw Gemma reply.
