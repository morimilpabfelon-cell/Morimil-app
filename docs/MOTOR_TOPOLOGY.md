# Morimil motor topology

Morimil has three different motor layers. They must not be described as one single API motor.

## 1. Morimil Core Motor

This is Morimil's own local reasoning organism: kernel, memory retrieval, doctrine, identity, rest cycle, traces, capsules, and governance. It is the part that grows with time.

It owns the conversation flow.
It owns memory composition.
It owns identity priority.
It decides how context is assembled.

This layer is not Ollama and is not a remote API.

## 2. Local Helper Model

This is an external local model used as compute help for Morimil's Core Motor.

Examples:

- Ollama over USB ADB reverse: `http://127.0.0.1:11434/v1/chat/completions`
- Ollama from Android emulator: `http://10.0.2.2:11434/v1/chat/completions`
- Ollama over private LAN: `http://192.168.x.x:11434/v1/chat/completions`

This layer is local assistance. It is not Morimil's identity.

## 3. Remote API Helper Model

This is an optional external cloud model used only when configured.

Examples:

- OpenAI-compatible chat completions
- Claude/Messages-compatible endpoints
- Responses-compatible endpoints
- Any future compatible provider

This layer is remote assistance. It is not superior to Morimil and must not be named as the main motor.

## Structural rule

Morimil Core Motor is the owner.
Local helper models and remote API helper models are compute providers.

A compute provider must never replace identity, doctrine, living memory, or core governance.

Correct chain:

```text
Morimil Core Motor
  -> chooses configured helper model if available
  -> receives reply from helper model
  -> records trace and memory through Morimil's local system
```

Incorrect chain:

```text
API/Ollama
  -> becomes Morimil
```

That is not the architecture.
