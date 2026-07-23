package com.morimil.app.reasoning

import com.morimil.app.reasoning.authority.HybridAuthorityDecision
import com.morimil.app.reasoning.authority.HybridAuthorityRoute
import com.morimil.app.reasoning.authority.HybridAuthorityRouterV0
import com.morimil.app.reasoning.authority.HybridAuthorityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HybridAuthorityPresentationV0Test {
    @Test
    fun legacyRuntimeIsPresentedAsDisabled() {
        val presentation = HybridAuthorityPresentationV0.from(
            finalizationStatus = TriMotorFinalizationStatus.LEGACY_UNROUTED,
            authorityDecision = null
        )

        assertEquals(HybridAuthorityPresentationStatus.DISABLED, presentation.status)
        assertEquals("ruta normal", presentation.routeLabel)
        assertNull(presentation.authorityVersion)
    }

    @Test
    fun deterministicAcceptanceUsesBoundedUiText() {
        val presentation = HybridAuthorityPresentationV0.from(
            finalizationStatus = TriMotorFinalizationStatus.ACCEPTED_BY_AUTHORITY,
            authorityDecision = HybridAuthorityDecision(
                route = HybridAuthorityRoute.DETERMINISTIC_ARITHMETIC,
                status = HybridAuthorityStatus.ACCEPTED_DETERMINISTIC,
                acceptedContent = "FINAL:42",
                authorityVersion = HybridAuthorityRouterV0.VERSION,
                findings = listOf("private_internal_trace=6*7")
            )
        )

        assertEquals(
            HybridAuthorityPresentationStatus.ACCEPTED_DETERMINISTIC,
            presentation.status
        )
        assertEquals("aritmética determinista", presentation.routeLabel)
        assertFalse(presentation.explanation.contains("6*7"))
        assertFalse(presentation.explanation.contains("FINAL:42"))
    }

    @Test
    fun deterministicLogicUsesSpecificSafeRouteLabel() {
        val presentation = HybridAuthorityPresentationV0.from(
            finalizationStatus = TriMotorFinalizationStatus.ACCEPTED_BY_AUTHORITY,
            authorityDecision = HybridAuthorityDecision(
                route = HybridAuthorityRoute.DETERMINISTIC_LOGIC,
                status = HybridAuthorityStatus.ACCEPTED_DETERMINISTIC,
                acceptedContent = "FINAL:ANA",
                authorityVersion = HybridAuthorityRouterV0.VERSION,
                findings = listOf("order=ana>bruno>carla")
            )
        )

        assertEquals(
            HybridAuthorityPresentationStatus.ACCEPTED_DETERMINISTIC,
            presentation.status
        )
        assertEquals("orden lógico determinista", presentation.routeLabel)
        assertFalse(presentation.explanation.contains("ana>bruno>carla"))
        assertFalse(presentation.explanation.contains("FINAL:ANA"))
    }

    @Test
    fun deterministicInstructionUsesSpecificSafeRouteLabel() {
        val presentation = HybridAuthorityPresentationV0.from(
            finalizationStatus = TriMotorFinalizationStatus.ACCEPTED_BY_AUTHORITY,
            authorityDecision = HybridAuthorityDecision(
                route = HybridAuthorityRoute.DETERMINISTIC_INSTRUCTION,
                status = HybridAuthorityStatus.ACCEPTED_DETERMINISTIC,
                acceptedContent = "FINAL:7",
                authorityVersion = HybridAuthorityRouterV0.VERSION,
                findings = listOf("12-5=7;format=FINAL:<resultado>")
            )
        )

        assertEquals(
            HybridAuthorityPresentationStatus.ACCEPTED_DETERMINISTIC,
            presentation.status
        )
        assertEquals("instrucción exacta determinista", presentation.routeLabel)
        assertFalse(presentation.explanation.contains("12-5=7"))
        assertFalse(presentation.explanation.contains("FINAL:7"))
    }

    @Test
    fun abstentionDoesNotExposeRawFindings() {
        val presentation = HybridAuthorityPresentationV0.from(
            finalizationStatus = TriMotorFinalizationStatus.ABSTAINED_BY_AUTHORITY,
            authorityDecision = HybridAuthorityDecision.abstain(
                route = HybridAuthorityRoute.STRICT_GENERATIVE_CONSENSUS,
                reason = "secret_raw_reason",
                findings = listOf("candidate_a=hidden", "candidate_b=hidden")
            )
        )

        assertEquals(HybridAuthorityPresentationStatus.ABSTAINED, presentation.status)
        assertEquals("consenso estricto", presentation.routeLabel)
        assertFalse(presentation.explanation.contains("secret_raw_reason"))
        assertFalse(presentation.explanation.contains("candidate_a"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun acceptedFinalizationRejectsAbstainedDecision() {
        HybridAuthorityPresentationV0.from(
            finalizationStatus = TriMotorFinalizationStatus.ACCEPTED_BY_AUTHORITY,
            authorityDecision = HybridAuthorityDecision.abstain(
                route = HybridAuthorityRoute.UNSUPPORTED,
                reason = "unsupported"
            )
        )
    }
}
