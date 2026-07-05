package com.morimil.app.data.repository

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryAppendGateTest {
    @Test
    fun appendGateSerializesConcurrentBlocks() = runBlocking {
        val insideGate = AtomicInteger(0)
        val maxInsideGate = AtomicInteger(0)

        coroutineScope {
            (1..24).map {
                async(Dispatchers.Default) {
                    MemoryAppendGate.withAppendLock {
                        val current = insideGate.incrementAndGet()
                        maxInsideGate.updateAndGet { previous -> maxOf(previous, current) }
                        delay(2)
                        insideGate.decrementAndGet()
                    }
                }
            }.awaitAll()
        }

        assertEquals(1, maxInsideGate.get())
    }
}
