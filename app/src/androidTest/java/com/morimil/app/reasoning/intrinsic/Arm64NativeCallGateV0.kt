package com.morimil.app.reasoning.intrinsic

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val lifecycleMutex = Mutex()
    private val active = AtomicInteger(0)

    val activeCallCount: Int
        get() = active.get()

    suspend fun <T> run(block: () -> T): T {
        check(lifecycleMutex.tryLock()) {
            "arm64_trimotor_concurrent_native_call"
        }
        check(active.compareAndSet(0, 1)) {
            lifecycleMutex.unlock()
            "arm64_trimotor_native_call_state_corrupt"
        }
        return try {
            withContext(NonCancellable) {
                block()
            }
        } finally {
            val released = active.compareAndSet(1, 0)
            lifecycleMutex.unlock()
            check(released) {
                "arm64_trimotor_native_call_state_corrupt"
            }
        }
    }

    suspend fun closeWhenIdle(block: () -> Unit) {
        withContext(NonCancellable) {
            lifecycleMutex.withLock {
                requireIdle()
                block()
            }
        }
    }

    fun requireIdle() {
        check(active.get() == 0) {
            "arm64_trimotor_native_call_still_active"
        }
    }
}
