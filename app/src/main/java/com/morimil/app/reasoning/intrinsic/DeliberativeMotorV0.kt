package com.morimil.app.reasoning.intrinsic

import com.morimil.app.ai.ChatTurn
import com.morimil.app.reasoning.IntrinsicReasoningMotor
import com.morimil.app.reasoning.IntrinsicReasoningRequest
import com.morimil.app.reasoning.IntrinsicReasoningResponse
import com.morimil.app.reasoning.ReasoningMotorRole
import com.morimil.app.reasoning.growth.MorimilIntrinsicMotorBlueprints
import com.morimil.app.reasoning.model.ReasoningTaskComplexity

/**
 * Opaque, request-scoped working state owned by a local Morimil inference core.
 * Implementations must not use it as autobiographical memory or persist it.
 */
interface DeliberativeLatentState

data class DeliberativeCoreInput(
    val systemPrompt: String,
    val history: List<ChatTurn>,
    val taskComplexity: ReasoningTaskComplexity
)

data class DeliberativePassOutcome(
    val state: DeliberativeLatentState,
    val certaintyPermille: Int,
    val stabilityPermille: Int
) {
    init {
        require(certaintyPermille in METRIC_RANGE) {
            "Deliberative certainty must be between 0 and 1000."
        }
        require(stabilityPermille in METRIC_RANGE) {
            "Deliberative stability must be between 0 and 1000."
        }
    }

    private companion object {
        val METRIC_RANGE = 0..1_000
    }
}

/**
 * Local computation boundary for Morimil-owned weights or another intrinsic
 * implementation. It intentionally exposes no provider, endpoint, credential,
 * memory writer, identity writer, lifecycle control or installation capability.
 */
interface MorimilDeliberativeCore {
    val artifactVersion: String
    val artifactSha256: String

    suspend fun initialize(input: DeliberativeCoreInput): Result<DeliberativeLatentState>

    suspend fun refine(
        state: DeliberativeLatentState,
        pass: Int
    ): Result<DeliberativePassOutcome>

    suspend fun decode(state: DeliberativeLatentState): Result<String>

    /** Releases request-scoped compute resources; it never changes Morimil lifecycle state. */
    suspend fun release(state: DeliberativeLatentState) = Unit
}

data class DeliberativeEffortBudget(
    val minimumPasses: Int,
    val maximumPasses: Int,
    val certaintyTargetPermille: Int,
    val stabilityTargetPermille: Int
) {
    init {
        require(minimumPasses in 1..MAX_ALLOWED_PASSES) {
            "Minimum deliberative passes must be bounded."
        }
        require(maximumPasses in minimumPasses..MAX_ALLOWED_PASSES) {
            "Maximum deliberative passes must be bounded and not below the minimum."
        }
        require(certaintyTargetPermille in 1..1_000) {
            "Certainty target must be between 1 and 1000."
        }
        require(stabilityTargetPermille in 1..1_000) {
            "Stability target must be between 1 and 1000."
        }
    }

    private companion object {
        const val MAX_ALLOWED_PASSES = 8
    }
}

/** Adaptive effort inspired by Inkling, with a strict local compute ceiling. */
object DeliberativeEffortPolicyV0 {
    fun budgetFor(complexity: ReasoningTaskComplexity): DeliberativeEffortBudget {
        return when (complexity) {
            ReasoningTaskComplexity.LIGHT_LOCAL,
            ReasoningTaskComplexity.MEMORY_LOCAL,
            ReasoningTaskComplexity.UNKNOWN -> budget(1, 2, 825, 825)

            ReasoningTaskComplexity.WEB_CONTEXT_LOCAL -> budget(2, 3, 850, 850)
            ReasoningTaskComplexity.DEEP_ANALYSIS -> budget(2, 5, 875, 875)
            ReasoningTaskComplexity.CODE_REVIEW -> budget(2, 6, 900, 875)
            ReasoningTaskComplexity.TOOL_OR_AGENT -> budget(3, 7, 900, 900)
            ReasoningTaskComplexity.ARCHITECTURE_CRITICAL -> budget(3, 8, 925, 900)
        }
    }

    private fun budget(
        minimumPasses: Int,
        maximumPasses: Int,
        certaintyTargetPermille: Int,
        stabilityTargetPermille: Int
    ): DeliberativeEffortBudget {
        return DeliberativeEffortBudget(
            minimumPasses = minimumPasses,
            maximumPasses = maximumPasses,
            certaintyTargetPermille = certaintyTargetPermille,
            stabilityTargetPermille = stabilityTargetPermille
        )
    }
}

enum class DeliberativeStopReason {
    CONVERGED,
    BUDGET_EXHAUSTED
}

/**
 * First executable shell of Morimil's deliberative motor.
 *
 * It adapts effort to task difficulty, applies recurrent latent refinement and
 * decodes only the final state. No latent state is returned or retained.
 */
class DeliberativeMotorV0(
    private val core: MorimilDeliberativeCore
) : IntrinsicReasoningMotor {
    override val role: ReasoningMotorRole = ReasoningMotorRole.DELIBERATIVE
    override val capabilityVersion: String

    init {
        require(CORE_VERSION_PATTERN.matches(core.artifactVersion)) {
            "The intrinsic core artifact version is invalid."
        }
        require(SHA256_PATTERN.matches(core.artifactSha256)) {
            "The intrinsic core artifact digest must be a canonical SHA-256 value."
        }
        capabilityVersion = "$MOTOR_VERSION+${core.artifactVersion}"
    }

    override suspend fun compute(
        request: IntrinsicReasoningRequest
    ): Result<IntrinsicReasoningResponse> = runCatching {
        require(request.candidateReply == null) {
            "The deliberative motor cannot act as a candidate verifier."
        }

        var state: DeliberativeLatentState? = null
        try {
            val budget = DeliberativeEffortPolicyV0.budgetFor(request.taskComplexity)
            val input = DeliberativeCoreInput(
                systemPrompt = request.systemPrompt,
                history = request.history.toList(),
                taskComplexity = request.taskComplexity
            )
            state = core.initialize(input).getOrThrow()
            var completedPasses = 0
            var stopReason = DeliberativeStopReason.BUDGET_EXHAUSTED

            while (completedPasses < budget.maximumPasses) {
                val currentState = requireNotNull(state)
                val outcome = core.refine(
                    state = currentState,
                    pass = completedPasses + 1
                ).getOrThrow()
                state = outcome.state
                completedPasses += 1

                val minimumDepthReached = completedPasses >= budget.minimumPasses
                val converged = outcome.certaintyPermille >= budget.certaintyTargetPermille &&
                    outcome.stabilityPermille >= budget.stabilityTargetPermille
                if (minimumDepthReached && converged) {
                    stopReason = DeliberativeStopReason.CONVERGED
                    break
                }
            }

            val reply = core.decode(requireNotNull(state)).getOrThrow().trim()
            require(reply.isNotBlank()) {
                "The intrinsic deliberative core produced an empty final reply."
            }

            IntrinsicReasoningResponse(
                content = reply,
                findings = listOf(
                    "intrinsic_motor:deliberative",
                    "motor_version:$capabilityVersion",
                    "blueprint_version:${MorimilIntrinsicMotorBlueprints.VERSION}",
                    "deliberation_passes:$completedPasses",
                    "deliberation_stop:${stopReason.name.lowercase()}"
                )
            )
        } finally {
            state?.let { core.release(it) }
        }
    }

    companion object {
        const val MOTOR_VERSION = "morimil.deliberative.v0"

        private val CORE_VERSION_PATTERN = Regex("[a-z0-9][a-z0-9._-]{0,63}")
        private val SHA256_PATTERN = Regex("sha256:[0-9a-f]{64}")
    }
}
