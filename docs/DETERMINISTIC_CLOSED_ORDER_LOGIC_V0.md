# Deterministic closed-order logic v0

Status: **bounded deterministic authority**

## Scope

This authority supports one explicit Spanish relation grammar:

```text
Ana llegó antes que Bruno y Bruno antes que Carla. ¿Quién llegó primero?
```

It can answer only `primero` or `último`. Entities are short single tokens and the prompt must contain between two and eight `antes que` relations.

## Decision procedure

1. Normalize accents and case locally.
2. Parse the entire prompt with no trailing or unexplained text.
3. Build a directed graph where `A antes que B` becomes `A -> B`.
4. Require a unique topological ordering.
5. Return the first or last entity from that unique order.

The motor replies and verifier replies remain advisory. Matching generated answers cannot override the graph result.

## Mandatory abstention

The authority abstains when any of the following is present:

- unsupported wording or question;
- fewer than two or more than eight relations;
- malformed or repeated relations;
- self-relations;
- a cycle;
- more than one valid topological order;
- extra prose after the question;
- a prompt above the fixed size limit.

Unsupported logic is reported through the general unsupported route. It is not sent to strict generative consensus.

## Runtime boundary

`LOGIC` reaches final authority only through this grammar. General syllogisms, free-form reasoning, Spanish reading comprehension, planning and exact-format instructions remain outside this authority and fail closed.

This change adds no model, provider, network access, memory writer, identity authority, Genesis authority, lifecycle authority or artifact installation capability.

## Benchmark impact

The frozen 120-case benchmark contains twelve `closed-order-v0` cases with exactly this grammar. They move from generative abstention to deterministic evaluation:

```text
bounded cases:    72 -> 84
generative cases: 48 -> 36
```

The frozen dataset itself and its SHA-256 remain unchanged.
