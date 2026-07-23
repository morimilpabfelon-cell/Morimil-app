package com.morimil.app.reasoning.authority

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridAuthorityRouterV0Test {
    private val router = HybridAuthorityRouterV0()

    @Test
    fun deterministicScalarRoutesOverrideWrongConsensus() {
        val cases = listOf(
            Case(
                HybridAuthorityTaskKind.ARITHMETIC,
                "Calcula 15 menos 2 por 6 respetando prioridad.",
                "FINAL:13",
                "FINAL:13",
                "FINAL:3",
                HybridAuthorityRoute.DETERMINISTIC_ARITHMETIC
            ),
            Case(
                HybridAuthorityTaskKind.RESTRICTED_CODE,
                "Que imprime Python con print(sum([2, 3, 5]))?",
                "FINAL:99",
                "FINAL:99",
                "FINAL:10",
                HybridAuthorityRoute.RESTRICTED_CODE
            ),
            Case(
                HybridAuthorityTaskKind.CLAIM_VERIFICATION,
                "Una respuesta afirma que 9 por 6 es 42.",
                "FINAL:42",
                "FINAL:42",
                "FINAL:54",
                HybridAuthorityRoute.DETERMINISTIC_CLAIM_CHECK
            )
        )

        cases.forEach { case ->
            val decision = router.decide(case.request())

            assertEquals(case.expected, decision.acceptedContent)
            assertEquals(case.route, decision.route)
            assertEquals(HybridAuthorityStatus.ACCEPTED_DETERMINISTIC, decision.status)
            assertTrue(decision.accepted)
        }
    }

    @Test
    fun closedOrderLogicOverridesMatchingWrongGeneratedReplies() {
        val first = router.decide(
            HybridAuthorityRequest(
                taskKind = HybridAuthorityTaskKind.LOGIC,
                prompt = "Ana llegó antes que Bruno y Bruno antes que Carla. ¿Quién llegó primero?",
                directReply = "FINAL:CARLA",
                verifierReply = "FINAL:CARLA"
            )
        )
        val last = router.decide(
            HybridAuthorityRequest(
                taskKind = HybridAuthorityTaskKind.LOGIC,
                prompt = "Ana llegó antes que Bruno y Bruno antes que Carla. ¿Quién llegó último?",
                directReply = "FINAL:ANA",
                verifierReply = "FINAL:ANA"
            )
        )

        assertEquals("FINAL:ANA", first.acceptedContent)
        assertEquals("FINAL:CARLA", last.acceptedContent)
        assertEquals(HybridAuthorityRoute.DETERMINISTIC_LOGIC, first.route)
        assertEquals(HybridAuthorityRoute.DETERMINISTIC_LOGIC, last.route)
        assertEquals(HybridAuthorityStatus.ACCEPTED_DETERMINISTIC, first.status)
        assertTrue(first.findings.any { it.contains("order=ana>bruno>carla") })
    }

    @Test
    fun closedOrderLogicRejectsCyclesTiesAndExtraText() {
        val prompts = listOf(
            "Ana llegó antes que Bruno y Bruno antes que Ana. ¿Quién llegó primero?",
            "Ana llegó antes que Carla y Bruno antes que Carla. ¿Quién llegó primero?",
            "Ana llegó antes que Bruno y Bruno antes que Carla. ¿Quién llegó primero? Explica.",
            "Ana llegó antes que Ana y Ana antes que Carla. ¿Quién llegó primero?",
            "Todos los A son B. Todos los B son C."
        )

        prompts.forEach { prompt ->
            val decision = router.decide(
                HybridAuthorityRequest(
                    taskKind = HybridAuthorityTaskKind.LOGIC,
                    prompt = prompt,
                    directReply = "FINAL:ANA",
                    verifierReply = "FINAL:ANA"
                )
            )

            assertFalse(decision.accepted)
            assertNull(decision.acceptedContent)
            assertEquals(HybridAuthorityRoute.UNSUPPORTED, decision.route)
            assertEquals(HybridAuthorityStatus.ABSTAINED, decision.status)
            assertTrue(decision.findings.contains("hybrid_authority_task_unknown"))
        }
    }

    @Test
    fun strictConsensusRemainsInternalForSpanishAndInstructionRouterCalls() {
        val spanish = router.decide(
            HybridAuthorityRequest(
                taskKind = HybridAuthorityTaskKind.SPANISH,
                prompt = "spanish fixture",
                directReply = "FINAL:B",
                verifierReply = "FINAL:B"
            )
        )
        val instruction = router.decide(
            HybridAuthorityRequest(
                taskKind = HybridAuthorityTaskKind.INSTRUCTION,
                prompt = "instruction fixture",
                directReply = "FINAL:7",
                verifierReply = "FINAL:7"
            )
        )

        assertEquals("FINAL:B", spanish.acceptedContent)
        assertEquals("FINAL:7", instruction.acceptedContent)
        assertEquals(HybridAuthorityStatus.ACCEPTED_STRICT_CONSENSUS, spanish.status)
        assertEquals(HybridAuthorityRoute.STRICT_GENERATIVE_CONSENSUS, spanish.route)
    }

    @Test
    fun strictConsensusRejectsDisagreementAndInvalidFormat() {
        val disagreement = router.decide(
            HybridAuthorityRequest(
                taskKind = HybridAuthorityTaskKind.SPANISH,
                prompt = "spanish disagreement",
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
    fun unknownAndUnsupportedScalarTasksFailClosed() {
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
        val expected: String,
        val route: HybridAuthorityRoute
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
