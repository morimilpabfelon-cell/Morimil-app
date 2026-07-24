# Android ARM64 LiteRT-LM native crash v0

Status: **initial physical run failed closed; guarded repeat completed successfully**

## Initial observed execution

The current 84/36 benchmark was executed on a physical ARM64 Android 16 device with the exact pinned candidate artifact:

```text
sizeBytes: 3655827456
sha256: 2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6
```

The build, installation, private staging, artifact size verification and artifact hash verification completed successfully. During the first opt-in instrumentation attempt, the application process terminated with:

```text
signal: SIGSEGV
code: SEGV_MAPERR
fault address: 0x0
cause: null pointer dereference
thread: callback_thread
native library: liblitertlm_jni.so
build id: 63d5401d32aa9cf7395658881ab897d0
```

That attempt was not accepted as benchmark evidence. The host runner preserved diagnostics and returned a stopped status.

## Lifecycle risk addressed

The benchmark bridge previously wrapped synchronous `Conversation.sendMessage()` in a coroutine timeout. A coroutine cancellation could then enter request cleanup and call `Conversation.close()` without an explicit proof that the native callback thread had returned.

The observed native backtrace does not prove that this lifecycle interaction caused the crash. It was nevertheless an unsafe boundary and was removed before another physical attempt.

`Arm64NativeCallGateV0` enforces:

1. one native conversation call at a time;
2. non-cancellable completion of a call already admitted to LiteRT-LM;
3. no conversation close while a native call is active;
4. fail-closed rejection of concurrent native calls;
5. instrumentation logs for conversation open/close and native call start/complete.

The outer host process retains the benchmark-wide timeout. If LiteRT-LM never returns, the physical run fails rather than closing native resources underneath an active callback.

## Guarded physical repeat

A subsequent physical run on source commit `6dc018a610c8f2a7ca5bf76748b6d639044c6c4d` completed successfully:

```text
runId:                morimil-trimotor-v0.2-physical-20260723-203336-1d3baa47
completed cases:      120/120
accepted correct:     84
abstained:            36
false accepted:       0
opened conversations: 36
closed conversations: 36
engine closed:        true
all states released:  true
```

The successful repeat is evidence that the guarded configuration completed this run. It does not establish the lifecycle race as the definitive cause of the earlier third-party native crash.

## Authority boundary

This change and evidence are research-only. They do not:

- register `DELIBERATIVE` in normal runtime;
- grant independent authority to the deliberative candidate;
- write memory or identity;
- modify Genesis or lifecycle authority;
- grant installation or production authorization;
- alter the pinned model artifact.
