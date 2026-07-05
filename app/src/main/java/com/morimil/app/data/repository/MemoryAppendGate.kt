package com.morimil.app.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object MemoryAppendGate {
    private val mutex = Mutex()

    suspend fun <T> withAppendLock(block: suspend () -> T): T {
        return mutex.withLock { block() }
    }
}
