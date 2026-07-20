package com.morimil.app.reasoning.authority

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridAuthorityRouterV0Test {
    private val router = HybridAuthorityRouterV0()

    @Test
    fun deterministicRoutesOverrideWrongOrFalseConsensusModelOutputs() {
        val cases = listOf(
            Case(
                HybridAuthorityTaskKind.ARITHMETIC,
                "Calcula 7 por 8. Devuelve exactamente FINAL:numero.",
                "FINAL:14",
                "FINAL:14",
                "FINAL:56"
            ),
            Case(
                HybridAuthorityTaskKind.ARITHMETIC,
                "Calcula 12 + 3 por 4 respetando prioridad de operaciones. Devuelve exactamente FINAL:numero.",
                "FINAL:15",
                "FINAL:12",
                "FINAL:24"
            ),
            Case(
                HybridAuthorityTaskKind.ARITHMETIC,
                "Calcula 18 dividido entre 3, y luego suma 7. Devuelve exactamente FINAL:numero.",
                "FINAL:11",
                "FINAL:12",
                "FINAL:13"
            ),
            Case(
                HybridAuthorityTaskKind.ARITHMETIC,
                "Calcula 15 menos 2 por 6 respetando prioridad. Devuelve exactamente FINAL:numero.",
                "FINAL:13",
                "FINAL:13",
                "FINAL:3"
            ),
            Case(
                HybridAuthorityTaskKind.RESTRICTED_CODE,
                "Que imprime Python con print(sum([2, 3, 5]))?",
                "FINAL:10",
                "FINAL:10",
                "FINAL:10"
            ),
            Case(
                HybridAuthorityTaskKind.RESTRICTED_CODE,
                "Que imprime Python con print(len('morimil'))?",
                "FINAL:6",
                "FINAL:7",
                "FINAL:7"
            ),
            Case(
                HybridAuthorityTaskKind.RESTRICTED_CODE,
                "Python: x = 3; x *= 4; print(x).",
                "FINAL:12",
                "FINAL:12",
                "FINAL:12"
            ),
            Case(
                HybridAuthorityTaskKind.RESTRICTED_CODE,
                "Python: print([i*i for i in range(4)][-1]).",
                "FINAL:9",
                "FINAL:9",
                "FINAL:9"
            ),
            Case(
                HybridAuthorityTaskKind.CLAIM_VERIFICATION,
                "Una respuesta afirma que 9 por 6 es 42.",
                "FINAL:36",
                "FINAL:54",
                "FINAL:54"
            ),
            Case(
                HybridAuthorityTaskKind.CLAIM_VERIFICATION,
                "Una respuesta afirma que len([1,2,3,4]) es 3.",
                "FINAL:4",
                "FINAL:4",
                "FINAL:4"
            ),
            Case(
                HybridAuthorityTaskKind.CLAIM_VERIFICATION,
                "Una respuesta afirma que todos los numeros pares son impares. Es correcta?",
                "FINAL:NO",
                "FINAL:NO",
                "FINAL:NO"
            ),
            Case(
                HybridAuthorityTaskKind.CLAIM_VERIFICATION,
                "Una respuesta afirma que 100 dividido entre 5 es 25.",
                "FINAL:20",
                "FINAL:2",
                "FINAL:20"
            )
        )

        cases.forEach { case ->
            val decision = router.decide(case.request())

            assertEquals(case.expected, decision.acceptedContent)
            assertEquals(HybridAuthorityStatus.ACCEPTED_DETERMINISTIC, decision.status)
            assertTrue(decision.accepted)
        }
    }

    @Test
    fun strictConsensusAcceptsLogicSpanishAndInstructionOnlyWhenOutputsMatch() {
        val accepted = listOf(
            Case(HybridAuthorityTaskKind.LOGIC, "logic-1", "FINAL:NO", "FINAL:NO", "FINAL:NO"),
            Case(HybridAuthorityTaskKind.LOGIC, "logic-2", "FINAL:SI", "FINAL:SI", "FINAL:SI"),
            Case(HybridAuthorityTaskKind.LOGIC, "logic-3", "FINAL:SI", "FINAL:SI", "FINAL:SI"),
            Case(HybridAuthorityTaskKind.LOGIC, "logic-4", "FINAL:NO", "FINAL:NO", "FINAL:NO"),
            Case(HybridAuthorityTaskKind.SPANISH, "spanish-2", "FINAL:B", "FINAL:B", "FINAL:B"),
            Case(HybridAuthorityTaskKind.SPANISH, "spanish-3", "FINAL:A", "FINAL:A", "FINAL:A"),
            Case(HybridAuthorityTaskKind.SPANISH, "spanish-4", "FINAL:B", "FINAL:B", "FINAL:B"),
            Case(HybridAuthorityTaskKind.INSTRUCTION, "instruction-1", "FINAL:AZUL", "FINAL:AZUL", "FINAL:AZUL"),
            Case(HybridAuthorityTaskKind.INSTRUCTION, "instruction-2", "FINAL:7", "FINAL:7", "FINAL:7"),
            Case(HybridAuthorityTaskKind.INSTRUCTION, "instruction-3", "FINAL:VERDE", "FINAL:VERDE", "FINAL:VERDE"),
            Case(HybridAuthorityTaskKind.INSTRUCTION, "instruction-4", "FINAL:O", "FINAL:O", "FINAL:O")
        )

        accepted.forEach { case ->
            val decision = router.decide(case.request())

            assertEquals(case.expected, decision.acceptedContent)
            assertEquals(HybridAuthorityStatus.ACCEPTED_STRICT_CONSENSUS, decision.status)
        }
    }

    @Test
    fun invalidVerifierFormatForSpanishProducesAbstention() {
        val decision = router.decide(
            HybridAuthorityRequest(
                taskKind = HybridAuthorityTaskKind.SPANISH,
                prompt = "Ana llego antes que Luis y Luis antes que Marta.",
                directReply = "FINAL:C",
                verifierReply = "Ana llego primero, Luis segundo y Marta tercero. FINAL:A"
            )
        )

        assertFalse(decision.accepted)
        assertNull(decision.acceptedContent)
        assertEquals(HybridAuthorityStatus.ABSTAINED, decision.status)
        assertTrue(decision.findings.any { it.contains("missing_valid_output") })
    }

    @Test
    fun strictConsensusRejectsDisagreementAndWhitespaceInsideFinalProtocol() {
        val disagreement = router.decide(
            HybridAuthorityRequest(
                taskKind = HybridAuthorityTaskKind.LOGIC,
                prompt = "logic disagreement",
                directReply = "FINAL:SI",
                verifierReply = "FINAL:NO"
            )
        )
        val whitespace = router.decide(
            HybridAuthorityRequest(
                taskKind = HybridAuthorityTaskKind.INSTRUCTION,
                prompt = "format check",
                directReply = "FINAL: 7",
                verifierReply = "FINAL:7"
            )
        )

        assertFalse(disagreement.accepted)
        assertTrue(disagreement.findings.any { it.contains("disagreement") })
        assertFalse(whitespace.accepted)
        assertTrue(whitespace.findings.any { it.contains("missing_valid_output") })
    }

    @Test
    fun unknownAndUnsupportedTasksFailClosed() {
        val unknown = router.decide(
            HybridAuthorityRequest(
                taskKind = HybridAuthorityTaskKind.UNKNOWN,
                prompt = "unknown",
                directReply = "FINAL:YES",
                verifierReply = "FINAL:YES"
            )
        )
        val unsupportedArithmetic = router.decide(
            HybridAuthorityRequest(
                taskKind = HybridAuthorityTaskKind.ARITHMETIC,
                prompt = "Resuelve una integral simbolica.",
                directReply = "FINAL:1",
                verifierReply = "FINAL:1"
            )
        )

        assertFalse(unknown.accepted)
        assertFalse(unsupportedArithmetic.accepted)
        assertEquals(HybridAuthorityRoute.UNSUPPORTED, unknown.route)
        assertEquals(HybridAuthorityRoute.DETERMINISTIC_ARITHMETIC, unsupportedArithmetic.route)
    }

    private data class Case(
        val kind: HybridAuthorityTaskKind,
        val prompt: String,
        val direct: String,
        val verified: String,
        val expected: String
    ) {
        fun request(): HybridAuthorityRequest {
            return HybridAuthorityRequest(
                taskKind = kind,
                prompt = prompt,
                directReply = direct,
                verifierReply = verified
            )
        }
    }
}
