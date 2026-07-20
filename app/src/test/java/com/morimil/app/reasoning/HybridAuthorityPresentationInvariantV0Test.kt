package com.morimil.app.reasoning

import com.morimil.app.reasoning.authority.HybridAuthorityDecision
import com.morimil.app.reasoning.authority.HybridAuthorityRoute
import com.morimil.app.reasoning.authority.HybridAuthorityRouterV0
import com.morimil.app.reasoning.authority.HybridAuthorityStatus
import org.junit.Test

class HybridAuthorityPresentationInvariantV0Test {
    @Test(expected = IllegalArgumentException::class)
    fun deterministicStatusRequiresDeterministicRoute() {
        HybridAuthorityPresentationV0.from(
            finalizationStatus = TriMotorFinalizationStatus.ACCEPTED_BY_AUTHORITY,
            authorityDecision = HybridAuthorityDecision(
                route = HybridAuthorityRoute.STRICT_GENERATIVE_CONSENSUS,
                status = HybridAuthorityStatus.ACCEPTED_DETERMINISTIC,
                acceptedContent = "FINAL:42",
                authorityVersion = HybridAuthorityRouterV0.VERSION,
                findings = emptyList()
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun authorityVersionMustMatchRouterVersion() {
        HybridAuthorityPresentationV0.from(
            finalizationStatus = TriMotorFinalizationStatus.ACCEPTED_BY_AUTHORITY,
            authorityDecision = HybridAuthorityDecision(
                route = HybridAuthorityRoute.DETERMINISTIC_ARITHMETIC,
                status = HybridAuthorityStatus.ACCEPTED_DETERMINISTIC,
                acceptedContent = "FINAL:42",
                authorityVersion = "other.version",
                findings = emptyList()
            )
        )
    }
}
