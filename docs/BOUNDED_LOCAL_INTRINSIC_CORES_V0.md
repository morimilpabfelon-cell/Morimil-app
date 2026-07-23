# Bounded local intrinsic cores v0

Status: executable bounded cores. The Intuitive core is active in normal runtime; the Metacognitive core remains research-only.

## Purpose

This change supplies the first real, non-fake implementations behind the Intuitive and Metacognitive motor shells introduced by PR #36.

They are deliberately bounded and deterministic. They do not claim general language understanding, neural independence or a completed tri-motor organism.

```text
BoundedLocalIntuitiveCoreV0
BoundedLocalMetacognitiveCoreV0
```

Both cores use Morimil's existing `HybridAuthorityRouterV0` only for routes where exact local computation already exists:

```text
ARITHMETIC
RESTRICTED_CODE
CLAIM_VERIFICATION
```

The following task kinds fail closed:

```text
LOGIC
SPANISH
INSTRUCTION
UNKNOWN
```

## Intuitive behavior

The Intuitive core reads the original authority prompt and produces a result only when the bounded deterministic authority accepts it.

Examples:

```text
Calcula 15 menos 2 por 6...
  -> FINAL:3

print(sum([1, 2, 3]))
  -> FINAL:6
```

It does not execute Python or arbitrary code. Restricted-code results come from fixed parsers and exact local semantics.

## Metacognitive behavior

The Metacognitive core receives the original prompt but no primary candidate. It recomputes the bounded task independently from that prompt.

```text
primary Deliberative candidate:  FINAL:13
blind Metacognitive result:      FINAL:3
final Morimil authority result:  FINAL:3
```

This is genuine deterministic recomputation, not model consensus. For generative tasks it refuses to manufacture agreement and returns failure to the coordinator, which causes authority abstention where appropriate.

## Research runtime factory

`BoundedLocalTriMotorResearchRuntimeFactoryV0` creates the isolated runtime using:

```text
IntuitiveMotorV0(BoundedLocalIntuitiveCoreV0)
DeliberativeMotorV0(explicitly supplied core)
MetacognitiveMotorV0(BoundedLocalMetacognitiveCoreV0)
```

The factory does not locate, download, install, sign or activate a Deliberative artifact. The caller must supply the Deliberative motor explicitly.

## Normal runtime activation

`MorimilNormalIntrinsicRuntimeV0` registers exactly:

```text
INTUITIVE -> IntuitiveMotorV0(BoundedLocalIntuitiveCoreV0)
```

Normal Chat now tries this local motor first. It can answer only the deterministic routes listed above. Unsupported, malformed or ambiguous requests fail closed and continue through the existing temporary-external or deterministic fallback path.

This activation does not register Deliberative or Metacognitive and does not enable `HybridAuthorityRuntimePolicy`.

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

They are stateless computation organs. Morimil retains identity, memory, continuity and final authority. Normal-runtime registration is performed outside the cores by a narrow registry with an exact role allowlist.

## What this establishes

```text
real bounded Intuitive computation:          yes
real blind Metacognitive recomputation:      yes
exact deterministic correction:              yes
request-persistent core state:                no
general neural Intuitive model:              no
general neural Metacognitive model:          no
physical tri-motor benchmark:                 not executed
normal Chat bounded Intuitive activation:     true
normal Chat Deliberative activation:           false
normal Chat Metacognitive activation:          false
```

## Next evidence gate

The next valid activation step is not to broaden the Intuitive parser silently. It is to register another intrinsic role only after its artifact, resource limits, behavior and authority boundary pass a separate review and physical Android validation.
