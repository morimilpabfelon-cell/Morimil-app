package com.morimil.app.reasoning.authority

import java.math.BigInteger

/**
 * Routes one bounded reasoning result to the component that is allowed to decide it.
 *
 * Neural replies are advisory on deterministic routes. They never gain authority over
 * arithmetic, restricted code semantics, locally checkable claims or closed order logic.
 */
class HybridAuthorityRouterV0 {
    fun decide(request: HybridAuthorityRequest): HybridAuthorityDecision {
        require(request.prompt.isNotBlank()) { "hybrid_authority_prompt_blank" }

        return when (request.taskKind) {
            HybridAuthorityTaskKind.ARITHMETIC -> deterministicDecision(
                route = HybridAuthorityRoute.DETERMINISTIC_ARITHMETIC,
                result = DeterministicArithmeticAuthorityV0.solve(request.prompt),
                request = request
            )

            HybridAuthorityTaskKind.RESTRICTED_CODE -> deterministicDecision(
                route = HybridAuthorityRoute.RESTRICTED_CODE,
                result = RestrictedCodeAuthorityV0.solve(request.prompt),
                request = request
            )

            HybridAuthorityTaskKind.CLAIM_VERIFICATION -> deterministicDecision(
                route = HybridAuthorityRoute.DETERMINISTIC_CLAIM_CHECK,
                result = DeterministicClaimAuthorityV0.solve(request.prompt),
                request = request
            )

            HybridAuthorityTaskKind.LOGIC -> deterministicDecision(
                route = HybridAuthorityRoute.DETERMINISTIC_LOGIC,
                result = DeterministicClosedOrderAuthorityV0.solve(request.prompt),
                request = request
            )

            HybridAuthorityTaskKind.SPANISH,
            HybridAuthorityTaskKind.INSTRUCTION -> strictConsensusDecision(request)

            HybridAuthorityTaskKind.UNKNOWN -> HybridAuthorityDecision.abstain(
                route = HybridAuthorityRoute.UNSUPPORTED,
                reason = "hybrid_authority_task_unknown"
            )
        }
    }

    private fun deterministicDecision(
        route: HybridAuthorityRoute,
        result: DeterministicAuthorityResult,
        request: HybridAuthorityRequest
    ): HybridAuthorityDecision {
        val advisory = StrictFinalConsensusV0.evaluate(
            directReply = request.directReply,
            verifierReply = request.verifierReply
        )

        if (!result.success || result.value == null) {
            return HybridAuthorityDecision.abstain(
                route = route,
                reason = result.reason,
                findings = listOfNotNull(
                    result.trace,
                    advisory.finding()
                )
            )
        }

        return HybridAuthorityDecision(
            route = route,
            status = HybridAuthorityStatus.ACCEPTED_DETERMINISTIC,
            acceptedContent = "FINAL:${result.value}",
            authorityVersion = VERSION,
            findings = listOfNotNull(
                result.reason,
                result.trace,
                advisory.finding()
            ).distinct()
        )
    }

    private fun strictConsensusDecision(
        request: HybridAuthorityRequest
    ): HybridAuthorityDecision {
        val consensus = StrictFinalConsensusV0.evaluate(
            directReply = request.directReply,
            verifierReply = request.verifierReply
        )

        if (!consensus.accepted || consensus.value == null) {
            return HybridAuthorityDecision.abstain(
                route = HybridAuthorityRoute.STRICT_GENERATIVE_CONSENSUS,
                reason = consensus.reason,
                findings = listOf(consensus.finding())
            )
        }

        return HybridAuthorityDecision(
            route = HybridAuthorityRoute.STRICT_GENERATIVE_CONSENSUS,
            status = HybridAuthorityStatus.ACCEPTED_STRICT_CONSENSUS,
            acceptedContent = "FINAL:${consensus.value}",
            authorityVersion = VERSION,
            findings = listOf(consensus.finding())
        )
    }

    companion object {
        const val VERSION = "morimil.hybrid-authority-router.v0"
    }
}

enum class HybridAuthorityTaskKind {
    ARITHMETIC,
    RESTRICTED_CODE,
    CLAIM_VERIFICATION,
    LOGIC,
    SPANISH,
    INSTRUCTION,
    UNKNOWN
}

enum class HybridAuthorityRoute {
    DETERMINISTIC_ARITHMETIC,
    RESTRICTED_CODE,
    DETERMINISTIC_CLAIM_CHECK,
    DETERMINISTIC_LOGIC,
    STRICT_GENERATIVE_CONSENSUS,
    UNSUPPORTED
}

enum class HybridAuthorityStatus {
    ACCEPTED_DETERMINISTIC,
    ACCEPTED_STRICT_CONSENSUS,
    ABSTAINED
}

data class HybridAuthorityRequest(
    val taskKind: HybridAuthorityTaskKind,
    val prompt: String,
    val directReply: String? = null,
    val verifierReply: String? = null
)

data class HybridAuthorityDecision(
    val route: HybridAuthorityRoute,
    val status: HybridAuthorityStatus,
    val acceptedContent: String?,
    val authorityVersion: String,
    val findings: List<String>
) {
    val accepted: Boolean
        get() = acceptedContent != null

    companion object {
        fun abstain(
            route: HybridAuthorityRoute,
            reason: String,
            findings: List<String> = emptyList()
        ): HybridAuthorityDecision {
            return HybridAuthorityDecision(
                route = route,
                status = HybridAuthorityStatus.ABSTAINED,
                acceptedContent = null,
                authorityVersion = HybridAuthorityRouterV0.VERSION,
                findings = (listOf(reason) + findings).filter(String::isNotBlank).distinct()
            )
        }
    }
}

internal data class DeterministicAuthorityResult(
    val success: Boolean,
    val value: String?,
    val reason: String,
    val trace: String? = null
) {
    companion object {
        fun accepted(
            value: BigInteger,
            reason: String,
            trace: String
        ): DeterministicAuthorityResult {
            return DeterministicAuthorityResult(
                success = true,
                value = value.toString(),
                reason = reason,
                trace = trace
            )
        }

        fun acceptedToken(
            value: String,
            reason: String,
            trace: String
        ): DeterministicAuthorityResult {
            return DeterministicAuthorityResult(
                success = true,
                value = value.uppercase(),
                reason = reason,
                trace = trace
            )
        }

        fun unsupported(reason: String): DeterministicAuthorityResult {
            return DeterministicAuthorityResult(
                success = false,
                value = null,
                reason = reason
            )
        }
    }
}

internal data class StrictConsensusResult(
    val accepted: Boolean,
    val value: String?,
    val reason: String,
    val direct: StrictFinalValue?,
    val verified: StrictFinalValue?
) {
    fun finding(): String {
        return buildString {
            append(reason)
            direct?.let { append(";direct=").append(it.value) }
            verified?.let { append(";verified=").append(it.value) }
        }
    }
}

internal data class StrictFinalValue(val value: String)

internal object StrictFinalConsensusV0 {
    private val STRICT_FINAL = Regex("FINAL:([A-Za-z]+|-?\\d+)")

    fun evaluate(directReply: String?, verifierReply: String?): StrictConsensusResult {
        val direct = parse(directReply)
        val verified = parse(verifierReply)

        if (direct == null || verified == null) {
            return StrictConsensusResult(
                accepted = false,
                value = null,
                reason = "strict_consensus_missing_valid_output",
                direct = direct,
                verified = verified
            )
        }

        if (direct.value != verified.value) {
            return StrictConsensusResult(
                accepted = false,
                value = null,
                reason = "strict_consensus_disagreement",
                direct = direct,
                verified = verified
            )
        }

        return StrictConsensusResult(
            accepted = true,
            value = direct.value,
            reason = "strict_consensus_agreement",
            direct = direct,
            verified = verified
        )
    }

    private fun parse(reply: String?): StrictFinalValue? {
        if (reply == null || reply.indexOf('\u0000') >= 0) return null
        val logical = reply.trimEnd('\r', '\n')
        if (logical.contains('\n') || logical.contains('\r')) return null
        val match = STRICT_FINAL.matchEntire(logical) ?: return null
        val raw = match.groupValues[1]
        val normalized = if (INTEGER.matches(raw)) {
            raw.toBigInteger().toString()
        } else {
            raw.uppercase()
        }
        return StrictFinalValue(normalized)
    }

    private val INTEGER = Regex("-?\\d+")
}
