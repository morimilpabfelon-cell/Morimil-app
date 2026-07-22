package com.morimil.app.reasoning.intrinsic

import com.morimil.app.reasoning.IntrinsicTriMotorResearchRuntimeV0
import com.morimil.app.reasoning.ReasoningTaskKind
import com.morimil.app.reasoning.authority.HybridAuthorityDecision
import com.morimil.app.reasoning.authority.HybridAuthorityRequest
import com.morimil.app.reasoning.authority.HybridAuthorityRouterV0
import com.morimil.app.reasoning.authority.HybridAuthorityStatus
import com.morimil.app.reasoning.authority.HybridAuthorityTaskKind

/**
 * First real local Intuitive core. It performs bounded deterministic computation
 * only where Morimil already owns an exact authority implementation.
 *
 * It contains no model weights, provider, network, persistence or lifecycle path.
 */
class BoundedLocalIntuitiveCoreV0(
    private val authorityRouter: HybridAuthorityRouterV0 = HybridAuthorityRouterV0()
) : MorimilIntuitiveCoreV0 {
    override val coreVersion: String = VERSION

    override suspend fun compute(
        input: IntuitiveCoreInputV0
    ): Result<RequestScopedIntrinsicCoreOutputV0> = runCatching {
        val prompt = resolvePrompt(
            authorityPrompt = input.authorityPrompt,
            history = input.history.map { turn -> turn.role to turn.content }
        )
        val decision = boundedDeterministicDecision(
            taskKind = input.taskKind,
            prompt = prompt,
            authorityRouter = authorityRouter
        )

        RequestScopedIntrinsicCoreOutputV0(
            content = requireNotNull(decision.acceptedContent),
            findings = decision.findings + listOf(
                "intrinsic_core:intuitive_bounded_local",
                "core_version:$coreVersion",
                "request_state:stateless"
            )
        )
    }

    companion object {
        const val VERSION = "morimil.intuitive.bounded-local-core.v0"
    }
}

/**
 * First real local Metacognitive core. It independently recomputes bounded tasks
 * from the original prompt. Its input type contains no primary candidate.
 *
 * Generative or unsupported tasks fail closed instead of manufacturing agreement.
 */
class BoundedLocalMetacognitiveCoreV0(
    private val authorityRouter: HybridAuthorityRouterV0 = HybridAuthorityRouterV0()
) : MorimilMetacognitiveCoreV0 {
    override val coreVersion: String = VERSION

    override suspend fun compute(
        input: MetacognitiveCoreInputV0
    ): Result<RequestScopedIntrinsicCoreOutputV0> = runCatching {
        val prompt = input.authorityPrompt.trim()
        require(prompt.isNotEmpty()) { "bounded_local_authority_prompt_missing" }
        val decision = boundedDeterministicDecision(
            taskKind = input.taskKind,
            prompt = prompt,
            authorityRouter = authorityRouter
        )

        RequestScopedIntrinsicCoreOutputV0(
            content = requireNotNull(decision.acceptedContent),
            findings = decision.findings + listOf(
                "intrinsic_core:metacognitive_bounded_local",
                "core_version:$coreVersion",
                "verification_mode:blind_deterministic_recomputation",
                "request_state:stateless"
            )
        )
    }

    companion object {
        const val VERSION = "morimil.metacognitive.bounded-local-core.v0"
    }
}

/**
 * Builds the isolated tri-motor research runtime with real bounded local Intuitive
 * and Metacognitive cores around one explicitly supplied Deliberative motor.
 */
object BoundedLocalTriMotorResearchRuntimeFactoryV0 {
    const val VERSION = "morimil.intrinsic-trimotor.bounded-local-factory.v0"

    fun create(
        deliberativeMotor: DeliberativeMotorV0
    ): IntrinsicTriMotorResearchRuntimeV0 {
        return IntrinsicTriMotorResearchRuntimeV0.create(
            intuitiveMotor = IntuitiveMotorV0(BoundedLocalIntuitiveCoreV0()),
            deliberativeMotor = deliberativeMotor,
            metacognitiveMotor = MetacognitiveMotorV0(BoundedLocalMetacognitiveCoreV0())
        )
    }
}

private fun boundedDeterministicDecision(
    taskKind: ReasoningTaskKind,
    prompt: String,
    authorityRouter: HybridAuthorityRouterV0
): HybridAuthorityDecision {
    val hybridTaskKind = when (taskKind) {
        ReasoningTaskKind.ARITHMETIC -> HybridAuthorityTaskKind.ARITHMETIC
        ReasoningTaskKind.RESTRICTED_CODE -> HybridAuthorityTaskKind.RESTRICTED_CODE
        ReasoningTaskKind.CLAIM_VERIFICATION -> HybridAuthorityTaskKind.CLAIM_VERIFICATION
        ReasoningTaskKind.LOGIC,
        ReasoningTaskKind.SPANISH,
        ReasoningTaskKind.INSTRUCTION,
        ReasoningTaskKind.UNKNOWN -> throw IllegalArgumentException(
            "bounded_local_task_kind_unsupported:${taskKind.name.lowercase()}"
        )
    }

    val decision = authorityRouter.decide(
        HybridAuthorityRequest(
            taskKind = hybridTaskKind,
            prompt = prompt,
            directReply = null,
            verifierReply = null
        )
    )
    check(
        decision.status == HybridAuthorityStatus.ACCEPTED_DETERMINISTIC &&
            decision.acceptedContent != null
    ) {
        val reason = decision.findings.firstOrNull() ?: "unknown"
        "bounded_local_authority_abstained:$reason"
    }
    return decision
}

private fun resolvePrompt(
    authorityPrompt: String?,
    history: List<Pair<String, String>>
): String {
    return authorityPrompt
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: history
            .lastOrNull { (role, _) -> role.equals("user", ignoreCase = true) }
            ?.second
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        ?: throw IllegalArgumentException("bounded_local_authority_prompt_missing")
}
