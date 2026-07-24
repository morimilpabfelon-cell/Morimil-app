package com.morimil.app.reasoning.intrinsic

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class Arm64NativeCallGateV0Test {
    @Test
    fun cancellationAndCloseCannotReleaseNativeSectionBeforeBlockingCallReturns() = runBlocking {
        val gate = Arm64NativeCallGateV0()
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        var closed = false

        val job = launch(Dispatchers.Default) {
            gate.run {
                entered.countDown()
                check(unblock.await(5, TimeUnit.SECONDS)) {
                    "native_call_gate_test_unblock_timeout"
                }
                "complete"
            }
        }

        assertTrue(entered.await(5, TimeUnit.SECONDS))
        job.cancel()
        assertEquals(1, gate.activeCallCount)
        assertEquals(
            "arm64_trimotor_native_call_still_active",
            runCatching { gate.requireIdle() }.exceptionOrNull()?.message
        )

        val closeJob = launch(Dispatchers.Default) {
            gate.closeWhenIdle {
                closed = true
            }
        }
        delay(100)
        assertFalse(closed)

        unblock.countDown()
        job.cancelAndJoin()
        closeJob.join()
        assertTrue(closed)
        assertEquals(0, gate.activeCallCount)
        gate.requireIdle()
    }

    @Test
    fun concurrentNativeCallFailsClosed() = runBlocking {
        val gate = Arm64NativeCallGateV0()
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)

        val first = launch(Dispatchers.Default) {
            gate.run {
                entered.countDown()
                check(unblock.await(5, TimeUnit.SECONDS)) {
                    "native_call_gate_test_unblock_timeout"
                }
            }
        }

        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val second = runCatching {
            gate.run { "second" }
        }
        assertTrue(second.isFailure)
        assertEquals(
            "arm64_trimotor_concurrent_native_call",
            second.exceptionOrNull()?.message
        )

        unblock.countDown()
        first.join()
        assertEquals(0, gate.activeCallCount)
        gate.requireIdle()
    }

    @Test
    fun completedCallReleasesGate() = runBlocking {
        val gate = Arm64NativeCallGateV0()

        val result = gate.run { "complete" }

        assertEquals("complete", result)
        assertEquals(0, gate.activeCallCount)
        gate.requireIdle()
    }
}
