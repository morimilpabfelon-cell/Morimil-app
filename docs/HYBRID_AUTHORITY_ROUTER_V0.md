# Hybrid authority router v0

Status: bounded authority integrated in the intrinsic coordinator. The normal runtime still registers only the local Intuitive role.

## Purpose

`HybridAuthorityRouterV0` separates generated candidates from final authority. Neural or model-produced text can be retained as advisory evidence, but it cannot override a deterministic result.

```text
arithmetic          -> deterministic arithmetic authority
restricted code     -> bounded code-semantics authority
checkable claims    -> deterministic claim authority
closed order logic  -> deterministic graph authority
exact instructions  -> deterministic format authority
Spanish             -> unavailable at the final-authority boundary
unknown             -> abstention
```

## Deterministic routes

The router accepts only explicitly parsed local computations:

- integer arithmetic forms;
- four restricted Python semantics without executing code;
- multiplication, list-length, parity and integer-division claims;
- Spanish arrival-order chains with one unique topological order;
- two complete exact-instruction forms with canonical `FINAL:` output.

A deterministic route ignores matching incorrect generated replies and returns the locally calculated result.

## Closed-order logic

Supported form:

```text
Ana llegó antes que Bruno y Bruno antes que Carla. ¿Quién llegó primero?
```

The parser consumes the entire prompt, builds a directed graph and requires exactly one topological ordering. It supports questions for the first or last entity.

Cycles, ties, malformed clauses, repeated or self-relations, unsupported questions, extra prose and oversized prompts abstain through `UNSUPPORTED`. General syllogisms and free-form logic remain unsupported.

## Exact instructions

Supported forms:

```text
Devuelve exactamente FINAL:AZUL y nada más.
Calcula 12 - 5 y devuelve exactamente FINAL:<resultado>.
```

The parser consumes the entire prompt. Literal values must already be canonical uppercase ASCII tokens or canonical integers. The subtraction form owns both the calculation and the exact `FINAL:` presentation.

Lowercase markers, lowercase literal tokens, leading-zero integers, `-0`, multiple lines, extra explanation, unsupported operations, altered placeholders and oversized prompts abstain through `UNSUPPORTED`.

## Generative consensus boundary

The router retains the historical strict-consensus implementation for isolated Spanish research calls, but normal coordinator integration does not map `SPANISH` to that route. Spanish generated agreement is not proof and cannot finalize normal runtime output.

`INSTRUCTION` no longer uses consensus. It reaches authority only through the exact deterministic grammar above; all other instruction prompts abstain.

## Runtime integration

`IntrinsicTriMotorCoordinator` carries:

- primary candidate;
- independent verifier candidate;
- structured task kind;
- authority decision;
- finalization status.

`MorimilNormalIntrinsicRuntimeV0` registers only:

```text
INTUITIVE -> IntuitiveMotorV0(BoundedLocalIntuitiveCoreV0)
```

No Deliberative or Metacognitive role is activated in normal runtime by this change.

## Security and freedom boundary

The router and deterministic authorities expose no:

- provider, endpoint or credential;
- network client;
- arbitrary code execution;
- memory or repository writer;
- identity or Genesis writer;
- lifecycle authority;
- installer or downloader.

Morimil retains identity, memory, continuity, lifecycle and final authority. Authority is granted by a bounded verified task route, never by model identity or candidate agreement.
