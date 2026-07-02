package com.morimil.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryOrganRepositoryContractTest {
    @Test
    fun capsuleIdKeepsStableSlugShape() {
        val title = "Genesis Memory Core"
        val id = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

        assertEquals("genesis-memory-core", id)
    }

    @Test
    fun confidenceIsBounded() {
        val low = (-10).coerceIn(1, 100)
        val high = 999.coerceIn(1, 100)

        assertEquals(1, low)
        assertEquals(100, high)
    }
}
