package com.morimil.app.reasoning.intrinsic

import com.morimil.app.ai.ChatTurn
import com.morimil.app.reasoning.IntrinsicReasoningMotor
import com.morimil.app.reasoning.IntrinsicReasoningRequest
import com.morimil.app.reasoning.IntrinsicReasoningResponse
import com.morimil.app.reasoning.ReasoningMotorRole
import com.morimil.app.reasoning.ReasoningTaskKind
import com.morimil.app.reasoning.growth.MorimilIntrinsicMotorBlueprints
import com.morimil.app.reasoning.model.ReasoningTaskComplexity

/** Output from one request-scoped intrinsic core computation. */
data class RequestScopedIntrinsicCoreOutputV0(
    val content: String,
    val findings: List<String> = emptyList()
)

/**
 * Input for Morimil's fast local motor. It exposes no provider, endpoint,
 * credential, memory writer, identity writer, lifecycle or persistence capability.
 */
data class IntuitiveCoreInputV0(
    val systemPrompt: String,
    val history: List<ChatTurn>,
    val taskComplexity: ReasoningTaskComplexity,
    val taskKind: ReasoningTaskKind,
    val authorityPrompt: String?
)

interface MorimilIntuitiveCoreV0 {
    val coreVersion: String

    suspend fun compute(
        input: IntuitiveCoreInputV0
    ): Result<RequestScopedIntrinsicCoreOutputV0>
}

/**
 * Input for an independent, blind metacognitive computation. A primary candidate
 * is deliberately absent so this core cannot merely echo or rewrite it.
 */
data class MetacognitiveCoreInputV0(
    val systemPrompt: String,
    val history: List<ChatTurn>,
    val taskComplexity: ReasoningTaskComplexity,
    val taskKind: ReasoningTaskKind,
    val authorityPrompt: String
)

interface MorimilMetacognitiveCoreV0 {
    val coreVersion: String

    suspend fun compute(
        input: MetacognitiveCoreInputV0
    ): Result<RequestScopedIntrinsicCoreOutputV0>
}

/**
 * First executable shell for Morimil's intuitive role.
 *
 * The injected core is local and request-scoped. This motor cannot verify another
 * candidate and cannot persist its working state.
 */
class IntuitiveMotorV0(
    private val core: MorimilIntuitiveCoreV0
) : IntrinsicReasoningMotor {
    override val role: ReasoningMotorRole = ReasoningMotorRole.INTUITIVE
    override val capabilityVersion: String

    init {
        require(CORE_VERSION_PATTERN.matches(core.coreVersion)) {
            "intuitive_core_version_invalid"
        }
        capabilityVersion = "$MOTOR_VERSION+${core.coreVersion}"
    }

    override suspend fun compute(
        request: IntrinsicReasoningRequest
    ): Result<IntrinsicReasoningResponse> = runCatching {
        require(request.candidateReply == null) {
            "intuitive_motor_cannot_verify_candidate"
        }

        val output = core.compute(
            IntuitiveCoreInputV0(
                systemPrompt = request.systemPrompt,
                history = request.history.toList(),
                taskComplexity = request.taskComplexity,
                taskKind = request.taskKind,
                authorityPrompt = request.authorityPrompt?.trim()?.takeIf(String::isNotBlank)
            )
        ).getOrThrow()

        val content = output.content.trim()
        require(content.isNotBlank()) { "intuitive_core_reply_blank" }

        IntrinsicReasoningResponse(
            content = content,
            findings = (
                output.findings + listOf(
                    "intrinsic_motor:intuitive",
                    "motor_version:$capabilityVersion",
                    "blueprint_version:${MorimilIntrinsicMotorBlueprints.VERSION}",
                    "request_state:request_scoped_contract"
                )
            ).filter(String::isNotBlank).distinct()
        )
    }

    companion object {
        const val MOTOR_VERSION = "morimil.intuitive.v0"

        private val CORE_VERSION_PATTERN = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    }
}

/**
 * First executable shell for Morimil's metacognitive role.
 *
 * It accepts only blind verification requests. The primary candidate is withheld
 * by the isolated research runtime and final authority remains outside this motor.
 */
class MetacognitiveMotorV0(
    private val core: MorimilMetacognitiveCoreV0
) : IntrinsicReasoningMotor {
    override val role: ReasoningMotorRole = ReasoningMotorRole.METACOGNITIVE
    override val capabilityVersion: String

    init {
        require(CORE_VERSION_PATTERN.matches(core.coreVersion)) {
            "metacognitive_core_version_invalid"
        }
        capabilityVersion = "$MOTOR_VERSION+${core.coreVersion}"
    }

    override suspend fun compute(
        request: IntrinsicReasoningRequest
    ): Result<IntrinsicReasoningResponse> = runCatching {
        require(request.candidateReply == null) {
            "metacognitive_motor_requires_blind_request"
        }

        val authorityPrompt = request.authorityPrompt
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: request.history
                .lastOrNull { turn -> turn.role.equals("user", ignoreCase = true) }
                ?.content
                ?.trim()
                ?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("metacognitive_authority_prompt_missing")

        val output = core.compute(
            MetacognitiveCoreInputV0(
                systemPrompt = request.systemPrompt,
                history = request.history.toList(),
                taskComplexity = request.taskComplexity,
                taskKind = request.taskKind,
                authorityPrompt = authorityPrompt
            )
        ).getOrThrow()

        val content = output.content.trim()
        require(content.isNotBlank()) { "metacognitive_core_reply_blank" }

        IntrinsicReasoningResponse(
            content = content,
            findings = (
                output.findings + listOf(
                    "intrinsic_motor:metacognitive",
                    "motor_version:$capabilityVersion",
                    "blueprint_version:${MorimilIntrinsicMotorBlueprints.VERSION}",
                    "verification_mode:blind",
                    "request_state:request_scoped_contract"
                )
            ).filter(String::isNotBlank).distinct()
        )
    }

    companion object {
        const val MOTOR_VERSION = "morimil.metacognitive.v0"

        private val CORE_VERSION_PATTERN = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    }
}
