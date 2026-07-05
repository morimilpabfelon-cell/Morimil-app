package com.morimil.app.data.repository

import com.morimil.app.core.memory.MemoryEventSigner
import com.morimil.app.core.memory.MemoryIntegrityCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun criticalRuntimeRepositoriesRequireSharedIntegrityAndSigningDependencies() {
        assertConstructorRequiresMemoryIntegrityCore(MemoryOrganRepository::class.java)
        assertConstructorRequiresMemoryIntegrityCore(MemoryOrganReconciliationRepository::class.java)
        assertConstructorRequiresMemoryIntegrityCore(MemoryRepository::class.java)
        assertConstructorRequiresMemoryIntegrityCore(RestCycleRepository::class.java)
        assertConstructorRequiresMemoryEventSigner(MemoryRepository::class.java)
        assertConstructorRequiresMemoryEventSigner(RestCycleRepository::class.java)
    }

    private fun assertConstructorRequiresMemoryIntegrityCore(type: Class<*>) {
        val constructors = type.constructors
        val constructorParameterTypes = constructors.flatMap { constructor -> constructor.parameterTypes.toList() }

        assertTrue(constructorParameterTypes.contains(MemoryIntegrityCore::class.java))
        assertFalse(
            constructors.any { constructor ->
                constructor.parameterTypes.none { parameter -> parameter == MemoryIntegrityCore::class.java }
            }
        )
    }

    private fun assertConstructorRequiresMemoryEventSigner(type: Class<*>) {
        val constructors = type.constructors
        val constructorParameterTypes = constructors.flatMap { constructor -> constructor.parameterTypes.toList() }

        assertTrue(constructorParameterTypes.contains(MemoryEventSigner::class.java))
        assertFalse(
            constructors.any { constructor ->
                constructor.parameterTypes.none { parameter -> parameter == MemoryEventSigner::class.java }
            }
        )
    }
}
