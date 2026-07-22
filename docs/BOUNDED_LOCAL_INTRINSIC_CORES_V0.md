# Bounded local intrinsic cores v0

Status: research-only executable cores. Normal Morimil runtime activation remains disabled.

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
normal-runtime activation
```

They are stateless computation organs. Morimil retains identity, memory, continuity and final authority.

## What this establishes

```text
real bounded Intuitive computation:          yes
real blind Metacognitive recomputation:      yes
exact deterministic correction:              yes
request-persistent core state:                no
general neural Intuitive model:              no
general neural Metacognitive model:          no
physical tri-motor benchmark:                 not executed
normal Chat activation:                       false
```

## Next evidence gate

After merge, the next valid experiment is a benchmark adapter that feeds the frozen 120-case dataset through the bounded research runtime and records:

```text
primaryCandidate
verifierCandidate
authorityDecision
finalReply
falseAcceptedCount
abstention counts by domain
```

No installation or normal activation is authorized by this document.
