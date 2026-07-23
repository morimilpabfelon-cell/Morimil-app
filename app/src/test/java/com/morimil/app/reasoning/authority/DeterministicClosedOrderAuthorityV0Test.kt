package com.morimil.app.reasoning.authority

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicClosedOrderAuthorityV0Test {
    @Test
    fun solvesAllTwelveFrozenBenchmarkOrderShapes() {
        val names = listOf("Ana", "Bruno", "Carla", "Diego", "Elena", "Fabio")

        for (index in 1..12) {
            val first = names[index % names.size]
            val second = names[(index + 1) % names.size]
            val third = names[(index + 2) % names.size]
            val prompt = "$first llegó antes que $second y $second antes que $third. " +
                "¿Quién llegó primero?"

            val result = DeterministicClosedOrderAuthorityV0.solve(prompt)

            assertTrue("case=$index reason=${result.reason}", result.success)
            assertEquals(first.uppercase(), result.value)
            assertEquals("deterministic_closed_order_unique_topology", result.reason)
        }
    }

    @Test
    fun supportsUniqueLongerChainAndLastQuestion() {
        val result = DeterministicClosedOrderAuthorityV0.solve(
            "Ana llegó antes que Bruno y Bruno antes que Carla y Carla antes que Diego. " +
                "¿Quién llegó último?"
        )

        assertTrue(result.success)
        assertEquals("DIEGO", result.value)
        assertEquals("order=ana>bruno>carla>diego;query=ultimo", result.trace)
    }

    @Test
    fun rejectsEveryAmbiguousOrMalformedShape() {
        val prompts = listOf(
            "Ana llegó antes que Carla y Bruno antes que Carla. ¿Quién llegó primero?",
            "Ana llegó antes que Bruno y Bruno antes que Ana. ¿Quién llegó primero?",
            "Ana llegó antes que Ana y Ana antes que Carla. ¿Quién llegó primero?",
            "Ana llegó antes que Bruno. ¿Quién llegó primero?",
            "Ana llegó antes que Bruno y Bruno llegó antes que Carla. ¿Quién llegó primero?",
            "Ana llegó antes que Bruno y Bruno antes que Carla. ¿Quién llegó segundo?",
            "Ana llegó antes que Bruno y Bruno antes que Carla. ¿Quién llegó primero? Explica.",
            "Todos los A son B. Todos los B son C."
        )

        prompts.forEach { prompt ->
            val result = DeterministicClosedOrderAuthorityV0.solve(prompt)
            assertFalse("prompt=$prompt", result.success)
            assertEquals(null, result.value)
        }
    }

    @Test
    fun rejectsPromptAboveBoundBeforeParsing() {
        val result = DeterministicClosedOrderAuthorityV0.solve("x".repeat(513))

        assertFalse(result.success)
        assertEquals("deterministic_closed_order_prompt_too_long", result.reason)
    }
}
