package com.morimil.app.reasoning.intrinsic

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Serializes benchmark-only calls into LiteRT-LM's native conversation layer.
 *
 * A coroutine timeout or cancellation must never make Morimil close the conversation
 * while the native callback thread can still be active. The surrounding request may
 * observe cancellation only after the native call has returned and this gate is idle.
 */
internal class Arm64NativeCallGateV0 {
    private val active = AtomicInteger(0)

    val activeCallCount: Int
        get() = active.get()

    suspend fun <T> run(block: () -> T): T {
        check(active.compareAndSet(0, 1)) {
            "arm64_trimotor_concurrent_native_call"
        }
        return try {
            withContext(NonCancellable) {
                block()
            }
        } finally {
            check(active.compareAndSet(1, 0)) {
                "arm64_trimotor_native_call_state_corrupt"
            }
        }
    }

    fun requireIdle() {
        check(active.get() == 0) {
            "arm64_trimotor_native_call_still_active"
        }
    }
}
