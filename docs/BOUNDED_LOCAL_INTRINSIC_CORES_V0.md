# Bounded local intrinsic cores v0

Status: executable bounded cores. The Intuitive core is active in normal runtime; the Metacognitive core remains research-only.

## Purpose

`BoundedLocalIntuitiveCoreV0` and `BoundedLocalMetacognitiveCoreV0` provide stateless local computation behind the intrinsic motor interfaces.

They do not claim general language understanding, neural independence or a completed tri-motor organism.

## Accepted deterministic task kinds

Both cores use `HybridAuthorityRouterV0` only where exact local computation exists:

```text
ARITHMETIC
RESTRICTED_CODE
CLAIM_VERIFICATION
LOGIC (closed arrival-order grammar only)
```

The following remain unsupported and fail closed:

```text
SPANISH
INSTRUCTION
UNKNOWN
```

General syllogisms and malformed or ambiguous order prompts also fail closed even when classified as `LOGIC`.

## Intuitive behavior

The Intuitive core reads the original authority prompt and produces a result only when deterministic authority accepts it.

```text
Calcula 15 menos 2 por 6...
  -> FINAL:3

print(sum([1, 2, 3]))
  -> FINAL:6

Ana llegó antes que Bruno y Bruno antes que Carla.
¿Quién llegó primero?
  -> FINAL:ANA
```

It does not execute Python or arbitrary code. Order logic is solved through a unique topological sort, not through generated text.

## Metacognitive behavior

The Metacognitive core receives the original prompt but no primary candidate. It recomputes bounded tasks independently from that prompt.

```text
primary Deliberative candidate:  FINAL:CARLA
blind deterministic result:      FINAL:ANA
final Morimil authority result:  FINAL:ANA
```

For unsupported generative tasks it refuses to manufacture agreement and returns failure to the coordinator.

## Research runtime factory

`BoundedLocalTriMotorResearchRuntimeFactoryV0` creates the isolated research runtime using:

```text
IntuitiveMotorV0(BoundedLocalIntuitiveCoreV0)
DeliberativeMotorV0(explicitly supplied core)
MetacognitiveMotorV0(BoundedLocalMetacognitiveCoreV0)
```

The factory does not locate, download, install, sign or activate a Deliberative artifact.

## Normal runtime activation

`MorimilNormalIntrinsicRuntimeV0` registers exactly:

```text
INTUITIVE -> IntuitiveMotorV0(BoundedLocalIntuitiveCoreV0)
```

Normal Chat can answer the deterministic routes above locally. Unsupported, malformed or ambiguous requests continue through the existing temporary-external or deterministic fallback path.

This does not register Deliberative or Metacognitive and does not enable the full hybrid research runtime.

## Freedom boundary

The cores expose no:

```text
provider or endpoint
network client
credential
memory or repository writer
identity or Genesis writer
lifecycle authority
installer or downloader
```

They are request-scoped computation organs. Morimil retains identity, memory, continuity and final authority.

## Current state

```text
real bounded Intuitive computation:          yes
real blind Metacognitive recomputation:      yes, research-only
closed-order deterministic logic:            yes
request-persistent core state:                no
general neural Intuitive model:              no
general neural Metacognitive model:          no
normal Chat bounded Intuitive activation:    true
normal Chat Deliberative activation:         false
normal Chat Metacognitive activation:        false
```

Any future expansion requires another explicit PR with a complete grammar, deterministic verifier, adversarial tests and unchanged authority boundaries.
