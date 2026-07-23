# Deterministic exact instruction authority v0

Status: bounded deterministic authority available to the local Intuitive core.

## Supported forms

```text
Devuelve exactamente FINAL:AZUL y nada más.
Calcula 12 - 5 y devuelve exactamente FINAL:<resultado>.
```

The parser consumes the complete prompt. It does not infer omitted requirements or accept nearby wording.

## Literal output contract

A literal value must be either:

```text
[A-Z][A-Z0-9_-]{0,31}
```

or a canonical integer:

```text
0
-?[1-9][0-9]{0,63}
```

The `FINAL:` marker must have exact casing. Lowercase tokens, leading-zero integers, `-0`, spaces inside the marker and additional text fail closed.

## Subtraction contract

The calculation form supports only two canonical integers separated by `-` and the exact placeholder:

```text
FINAL:<resultado>
```

Morimil calculates the subtraction locally and emits `FINAL:<calculated integer>`. Generated candidates and verifier candidates are advisory and cannot change the result.

## Mandatory abstention

The authority rejects:

- addition, multiplication, division or arbitrary expressions;
- altered placeholders or marker casing;
- explanations before or after the instruction;
- multiple lines or NUL characters;
- non-canonical integers or tokens;
- prompts longer than the fixed limit;
- every instruction outside the two complete grammars.

Unsupported instructions reach `UNSUPPORTED` and preserve the existing fallback path in normal Chat.

## Runtime boundary

The normal runtime continues to register only:

```text
INTUITIVE
```

This authority adds no Deliberative or Metacognitive activation, model weights, provider, network access, persistence, memory write, identity authority, Genesis mutation, lifecycle control, installer or downloader.

## Benchmark boundary

The twelve frozen `strict_format` benchmark cases now use this authority directly instead of being rewritten into the arithmetic route. The frozen dataset and 84/36 adapter counts do not change.

This code change is not a new physical benchmark result. A separate opt-in Android ARM64 execution is required for new physical evidence.
