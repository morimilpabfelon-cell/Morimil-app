# Morimil cognitive topology

Morimil has exactly three intrinsic cognitive motors:

1. `INTUITIVE`
2. `DELIBERATIVE`
3. `METACOGNITIVE`

These roles belong to Morimil. They remain stable while their internal implementations, capability versions and approved artifacts may evolve.

Ollama, remote APIs, Gemma packages and future compatible runtimes are not additional Morimil motors. They are replaceable compute providers.

## Morimil-owned organism

Morimil owns and governs:

- local identity;
- Genesis continuity;
- doctrine and policy;
- living memory and knowledge capsules;
- context composition;
- intrinsic tri-motor coordination;
- hybrid authority;
- abstention;
- traces;
- learning-candidate promotion and rollback.

The kernel coordinates those capabilities. The kernel is not a fourth motor.

## Intrinsic motors

### Intuitive

Fast local reasoning, bounded deterministic work, uncertainty estimation and escalation decisions.

### Deliberative

Deep multi-step reasoning. It remains blocked in the normal runtime until its evidence, provenance and personal authorization gates pass.

### Metacognitive

Independent checking, contradiction detection, uncertainty review and abstention. It also remains blocked in the normal runtime until its gates pass.

## Temporary compute helpers

### Local helper

The supported local-helper route is Ollama over USB/ADB reverse:

```text
http://127.0.0.1:11434/v1/chat/completions
```

Loopback does not make Ollama intrinsic. The process runs outside Morimil's Android process and may run on another physical device. It therefore receives only the current user task.

Private-LAN Ollama is not an authorized route in the current runtime.

### Remote helper

A configured HTTPS API may be consulted after the applicable routing and approval gates. It also receives only the current user task.

## Immutable confidentiality boundary

No temporary helper may receive:

- Morimil identity or alias context;
- doctrine or policy text;
- living memory;
- knowledge capsules;
- Genesis state;
- prior private conversation history;
- tools, secrets or credentials unrelated to that provider;
- lifecycle or promotion authority.

There is no full-context consent switch for helpers. The boundary is structural, not optional.

## Output boundary

A helper returns unverified advisory text.

The transcript must store it as:

```text
AUXILIARY_ADVISORY
```

It must carry a visible external-advisory label and must not:

- be stored as author `morimil`;
- be spoken by text-to-speech as Morimil;
- re-enter later prompts as trusted Morimil history;
- become living or canonical memory;
- alter identity, Genesis or lifecycle state.

Only an intrinsically finalized result may be represented as Morimil's own answer.

## Correct chain

```text
User task
  -> Morimil kernel
  -> intrinsic motors first
  -> optional temporary helper receives current task only
  -> helper returns unverified advisory
  -> advisory remains visibly external and outside trusted history
```

## Incorrect chains

```text
Ollama/API -> becomes Motor 2 or Motor 3
Ollama/API -> receives full Morimil context
Ollama/API -> speaks as Morimil
Ollama/API -> becomes a superior motor
```

All four are architecture violations.
