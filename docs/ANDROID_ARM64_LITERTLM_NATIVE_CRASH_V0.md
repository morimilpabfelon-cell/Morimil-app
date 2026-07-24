# Android ARM64 LiteRT-LM native crash v0

Status: **physical benchmark failed closed**

## Observed execution

The current 84/36 benchmark was executed on a physical ARM64 Android 16 device with the exact pinned candidate artifact:

```text
sizeBytes: 3655827456
sha256: 2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6
```

The build, installation, private staging, artifact size verification and artifact hash verification completed successfully. During the opt-in instrumentation test, the application process terminated with:

```text
signal: SIGSEGV
code: SEGV_MAPERR
fault address: 0x0
cause: null pointer dereference
thread: callback_thread
native library: liblitertlm_jni.so
build id: 63d5401d32aa9cf7395658881ab897d0
```

This is not accepted as benchmark evidence. The host runner preserved diagnostics and returned a stopped status. Deliberative normal-runtime activation remains blocked.

## Lifecycle risk addressed

The benchmark bridge previously wrapped synchronous `Conversation.sendMessage()` in a coroutine timeout. A coroutine cancellation could then enter request cleanup and call `Conversation.close()` without an explicit proof that the native callback thread had returned.

The observed native backtrace does not prove that this lifecycle interaction caused the crash. It is nevertheless an unsafe boundary and is removed before another physical attempt.

`Arm64NativeCallGateV0` now enforces:

1. one native conversation call at a time;
2. non-cancellable completion of a call already admitted to LiteRT-LM;
3. no conversation close while a native call is active;
4. fail-closed rejection of concurrent native calls;
5. instrumentation logs for conversation open/close and native call start/complete.

The outer host process retains the benchmark-wide timeout. If LiteRT-LM never returns, the physical run fails rather than closing native resources underneath an active callback.

## Authority boundary

This change is instrumentation-only. It does not:

- register `DELIBERATIVE` in normal runtime;
- alter the 84/36 authority contract;
- write memory or identity;
- modify Genesis or lifecycle authority;
- grant production authorization;
- alter the pinned model artifact.

A new physical run is required. Passing CI or the lifecycle unit tests is not physical evidence that the third-party native crash is fixed.
