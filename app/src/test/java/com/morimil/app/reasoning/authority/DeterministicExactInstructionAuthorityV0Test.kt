package com.morimil.app.reasoning.authority

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicExactInstructionAuthorityV0Test {
    @Test
    fun acceptsCanonicalLiteralOutputs() {
        val cases = mapOf(
            "Devuelve exactamente FINAL:AZUL y nada más." to "AZUL",
            "Devuelve exactamente FINAL:7 y nada mas." to "7",
            "Devuelve exactamente FINAL:-12 y nada más" to "-12",
            "Devuelve exactamente FINAL:O y nada más." to "O"
        )

        cases.forEach { (prompt, expected) ->
            val result = DeterministicExactInstructionAuthorityV0.solve(prompt)

            assertTrue(result.success)
            assertEquals(expected, result.value)
            assertEquals("deterministic_exact_instruction_literal", result.reason)
        }
    }

    @Test
    fun computesSubtractionAndOwnsFinalFormat() {
        val cases = mapOf(
            "Calcula 12 - 5 y devuelve exactamente FINAL:<resultado>." to "7",
            "Calcula -4 - 6 y devuelve exactamente FINAL:<resultado>." to "-10",
            "Calcula 0 - -3 y devuelve exactamente FINAL:<resultado>" to "3"
        )

        cases.forEach { (prompt, expected) ->
            val result = DeterministicExactInstructionAuthorityV0.solve(prompt)

            assertTrue(result.success)
            assertEquals(expected, result.value)
            assertEquals("deterministic_exact_instruction_subtraction", result.reason)
        }
    }

    @Test
    fun rejectsNonCanonicalLiteralOrPlaceholderForms() {
        val prompts = listOf(
            "Devuelve exactamente FINAL:azul y nada más.",
            "Devuelve exactamente final:AZUL y nada más.",
            "Devuelve exactamente FINAL:007 y nada más.",
            "Devuelve exactamente FINAL:-0 y nada más.",
            "Devuelve exactamente FINAL:AZUL.",
            "Calcula 12 - 5 y devuelve exactamente final:<resultado>.",
            "Calcula 12 - 5 y devuelve exactamente FINAL:<RESULTADO>."
        )

        prompts.forEach { prompt ->
            val result = DeterministicExactInstructionAuthorityV0.solve(prompt)

            assertFalse(result.success)
            assertEquals(null, result.value)
        }
    }

    @Test
    fun rejectsExtraTextMultilineUnsupportedOperationsAndOversizeInput() {
        val prompts = listOf(
            "Devuelve exactamente FINAL:AZUL y nada más. Explica por qué.",
            "Devuelve exactamente FINAL:AZUL y nada más.\nFINAL:ROJO",
            "Calcula 12 + 5 y devuelve exactamente FINAL:<resultado>.",
            "Calcula 12 - 5 y devuelve exactamente FINAL:<resultado>. Luego explica.",
            "Devuelve exactamente FINAL:AZUL y nada más." + " ".repeat(257)
        )

        prompts.forEach { prompt ->
            val result = DeterministicExactInstructionAuthorityV0.solve(prompt)

            assertFalse(result.success)
            assertEquals(null, result.value)
        }
    }
}
