package com.morimil.app.reasoning

import com.morimil.app.reasoning.authority.HybridAuthorityDecision
import com.morimil.app.reasoning.authority.HybridAuthorityRoute
import com.morimil.app.reasoning.authority.HybridAuthorityRouterV0
import com.morimil.app.reasoning.authority.HybridAuthorityStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI-safe representation of the hybrid authority result.
 *
 * Candidate replies, verifier replies, raw findings and internal traces are intentionally
 * excluded. The presentation only exposes the bounded status, route and a fixed explanation.
 */
enum class HybridAuthorityPresentationStatus {
    DISABLED,
    ACCEPTED_DETERMINISTIC,
    ACCEPTED_STRICT_CONSENSUS,
    ABSTAINED
}

data class HybridAuthorityPresentation(
    val status: HybridAuthorityPresentationStatus,
    val headline: String,
    val routeLabel: String,
    val explanation: String,
    val authorityVersion: String?
)

object HybridAuthorityPresentationV0 {
    const val VERSION = "morimil.hybrid-authority-presentation.v0"

    fun disabled(): HybridAuthorityPresentation {
        return HybridAuthorityPresentation(
            status = HybridAuthorityPresentationStatus.DISABLED,
            headline = "Autoridad híbrida desactivada",
            routeLabel = "ruta normal",
            explanation = "El gate híbrido no participa en la ejecución normal.",
            authorityVersion = null
        )
    }

    fun from(
        finalizationStatus: TriMotorFinalizationStatus,
        authorityDecision: HybridAuthorityDecision?
    ): HybridAuthorityPresentation {
        return when (finalizationStatus) {
            TriMotorFinalizationStatus.LEGACY_UNROUTED -> {
                require(authorityDecision == null) { "legacy_finalization_must_not_have_authority_decision" }
                disabled()
            }

            TriMotorFinalizationStatus.ACCEPTED_BY_AUTHORITY -> {
                val decision = requireNotNull(authorityDecision) {
                    "accepted_finalization_requires_authority_decision"
                }
                require(decision.accepted) { "accepted_finalization_requires_accepted_content" }
                validateAuthorityVersion(decision)
                accepted(decision)
            }

            TriMotorFinalizationStatus.ABSTAINED_BY_AUTHORITY -> {
                val decision = requireNotNull(authorityDecision) {
                    "abstained_finalization_requires_authority_decision"
                }
                require(!decision.accepted) { "abstained_finalization_must_not_have_accepted_content" }
                require(decision.status == HybridAuthorityStatus.ABSTAINED) {
                    "abstained_finalization_requires_abstained_status"
                }
                validateAuthorityVersion(decision)
                HybridAuthorityPresentation(
                    status = HybridAuthorityPresentationStatus.ABSTAINED,
                    headline = "Morimil se abstuvo",
                    routeLabel = routeLabel(decision.route),
                    explanation = "No hubo evidencia suficiente para aceptar una respuesta.",
                    authorityVersion = decision.authorityVersion
                )
            }
        }
    }

    private fun accepted(decision: HybridAuthorityDecision): HybridAuthorityPresentation {
        val presentationStatus = when (decision.status) {
            HybridAuthorityStatus.ACCEPTED_DETERMINISTIC -> {
                require(
                    decision.route == HybridAuthorityRoute.DETERMINISTIC_ARITHMETIC ||
                        decision.route == HybridAuthorityRoute.RESTRICTED_CODE ||
                        decision.route == HybridAuthorityRoute.DETERMINISTIC_CLAIM_CHECK ||
                        decision.route == HybridAuthorityRoute.DETERMINISTIC_LOGIC
                ) { "deterministic_status_requires_deterministic_route" }
                HybridAuthorityPresentationStatus.ACCEPTED_DETERMINISTIC
            }

            HybridAuthorityStatus.ACCEPTED_STRICT_CONSENSUS -> {
                require(decision.route == HybridAuthorityRoute.STRICT_GENERATIVE_CONSENSUS) {
                    "consensus_status_requires_consensus_route"
                }
                HybridAuthorityPresentationStatus.ACCEPTED_STRICT_CONSENSUS
            }

            HybridAuthorityStatus.ABSTAINED ->
                error("accepted_decision_cannot_have_abstained_status")
        }
        val explanation = when (presentationStatus) {
            HybridAuthorityPresentationStatus.ACCEPTED_DETERMINISTIC ->
                "Una autoridad determinista verificó la salida."

            HybridAuthorityPresentationStatus.ACCEPTED_STRICT_CONSENSUS ->
                "La salida coincidió bajo consenso estricto."

            HybridAuthorityPresentationStatus.DISABLED,
            HybridAuthorityPresentationStatus.ABSTAINED ->
                error("accepted_presentation_status_invalid")
        }
        return HybridAuthorityPresentation(
            status = presentationStatus,
            headline = "Respuesta verificada",
            routeLabel = routeLabel(decision.route),
            explanation = explanation,
            authorityVersion = decision.authorityVersion
        )
    }

    private fun validateAuthorityVersion(decision: HybridAuthorityDecision) {
        require(decision.authorityVersion == HybridAuthorityRouterV0.VERSION) {
            "authority_version_mismatch"
        }
    }

    private fun routeLabel(route: HybridAuthorityRoute): String {
        return when (route) {
            HybridAuthorityRoute.DETERMINISTIC_ARITHMETIC -> "aritmética determinista"
            HybridAuthorityRoute.RESTRICTED_CODE -> "código restringido"
            HybridAuthorityRoute.DETERMINISTIC_CLAIM_CHECK -> "verificación determinista"
            HybridAuthorityRoute.DETERMINISTIC_LOGIC -> "orden lógico determinista"
            HybridAuthorityRoute.STRICT_GENERATIVE_CONSENSUS -> "consenso estricto"
            HybridAuthorityRoute.UNSUPPORTED -> "ruta no compatible"
        }
    }
}

/** Process-local UI status only. It is not memory, doctrine, policy or audit evidence. */
object HybridAuthorityPresentationStore {
    private val mutablePresentation = MutableStateFlow(HybridAuthorityPresentationV0.disabled())
    val lastPresentation: StateFlow<HybridAuthorityPresentation> = mutablePresentation.asStateFlow()

    fun resetDisabled() {
        mutablePresentation.value = HybridAuthorityPresentationV0.disabled()
    }

    fun publish(
        finalizationStatus: TriMotorFinalizationStatus,
        authorityDecision: HybridAuthorityDecision?
    ) {
        mutablePresentation.value = HybridAuthorityPresentationV0.from(
            finalizationStatus = finalizationStatus,
            authorityDecision = authorityDecision
        )
    }
}
