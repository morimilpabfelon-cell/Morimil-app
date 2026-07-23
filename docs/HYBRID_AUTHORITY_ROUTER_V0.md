# Hybrid authority router v0

Status: bounded authority integrated in the intrinsic coordinator. The normal runtime still registers only the local Intuitive role.

## Purpose

`HybridAuthorityRouterV0` separates generated candidates from final authority. Neural or model-produced text can be retained as advisory evidence, but it cannot override a deterministic result.

```text
arithmetic         -> deterministic arithmetic authority
restricted code    -> bounded code-semantics authority
checkable claims   -> deterministic claim authority
closed order logic -> deterministic graph authority
Spanish            -> unavailable at the final-authority boundary
instructions       -> unavailable at the final-authority boundary
unknown            -> abstention
```

## Deterministic routes

The router accepts only explicitly parsed local computations:

- integer arithmetic forms;
- four restricted Python semantics without executing code;
- multiplication, list-length, parity and integer-division claims;
- Spanish arrival-order chains with one unique topological order.

A deterministic route ignores matching incorrect generated replies and returns the locally calculated result.

## Closed-order logic

Supported form:

```text
Ana llegó antes que Bruno y Bruno antes que Carla. ¿Quién llegó primero?
```

The parser consumes the entire prompt, builds a directed graph and requires exactly one topological ordering. It supports questions for the first or last entity.

Cycles, ties, malformed clauses, repeated or self-relations, unsupported questions, extra prose and oversized prompts abstain through `UNSUPPORTED`. General syllogisms and free-form logic remain unsupported.

## Generative consensus boundary

The router retains the historical strict-consensus implementation for isolated research calls, but normal coordinator integration does not map `SPANISH` or `INSTRUCTION` to that route. Those task kinds are downgraded to `UNKNOWN` and abstain even when generated replies match.

Generated agreement is not proof and cannot finalize normal runtime output.

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
