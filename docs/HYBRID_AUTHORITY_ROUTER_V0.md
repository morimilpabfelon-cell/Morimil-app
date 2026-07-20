# Hybrid authority router v0

Status: research-only architecture. No model weights, installation, signature, runtime registration or production promotion are included.

## Purpose

`HybridAuthorityRouterV0` separates neural generation from authority. Gemma 3n E2B may propose or verify text, but Morimil decides which component is allowed to accept the result.

```text
arithmetic       -> deterministic arithmetic authority
restricted code  -> bounded code-semantics authority
checkable claims -> deterministic claim authority
logic            -> strict neural consensus or abstention
Spanish          -> strict neural consensus or abstention
instructions     -> strict neural consensus or abstention
unknown          -> abstention
```

A neural reply has no authority on a deterministic route. This prevents the observed false consensus where two Gemma 3n E2B conversations both returned `FINAL:13` for `15 - 2 * 6`.

## Strict generative protocol

A generative result is accepted only when both independent replies are exactly one logical line in this form:

```text
FINAL:<single integer or ASCII token>
```

Both normalized values must match. Extra explanation, embedded whitespace after `FINAL:`, disagreement or a missing reply produces abstention.

This is request-scoped textual consensus. It is not proof of independence, latent recurrence or correctness.

## Deterministic scope

The deterministic authorities intentionally support only bounded, explicitly parsed forms:

- integer multiplication, precedence examples, exact integer division followed by addition;
- four restricted Python semantics used by the research gate;
- multiplication, list-length, parity and exact integer-division claims.

Unsupported forms abstain. The implementation does not execute arbitrary Python, shell commands, network calls or dynamic code.

## Research evidence

`MorimilDeliberativeArtifactContractV02Candidate` records the exact acquired candidate:

```text
repository: google/gemma-3n-E2B-it-litert-lm
repository revision: c03b6f60b8da6c5400b6838a2cf26420f80c0a01
upstream file: gemma-3n-E2B-it-int4.litertlm
local file: morimil-deliberative-v0.2.candidate.litertlm
size: 3655827456 bytes
sha256: 2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6
```

The repository revision identifies the converted artifact repository. It is not the exact base-model source revision. The candidate profile therefore requires `sourceModelRevision == null` and rejects certification, signing, installation and promotion claims.

## Runtime boundary

This PR does not wire the router into `IntrinsicTriMotorCoordinator`. The current coordinator replaces a primary reply with metacognitive output and does not yet expose the structured task kind and two independent candidates required by the router.

Runtime integration remains blocked until:

- Android arm64 loading and resource-release validation;
- thermal, battery, memory and long-context measurement;
- a signed v0.2 artifact contract with reproducible source and conversion provenance;
- adversarial and out-of-distribution tests for every deterministic parser;
- a structured coordinator request that preserves primary, verifier and authority decisions separately.

## Freedom invariant

Gemma is a replaceable local computation component. It does not own Morimil identity, memory, continuity, goals, lifecycle or intrinsic state. The router grants authority by task route, not by model identity.
