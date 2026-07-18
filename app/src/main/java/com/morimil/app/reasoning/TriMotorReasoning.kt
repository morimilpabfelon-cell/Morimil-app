package com.morimil.app.reasoning

import com.morimil.app.reasoning.model.ReasoningTaskComplexity

/** Stable reasoning roles. Providers and model families remain replaceable. */
enum class ReasoningMotorRole {
    INTUITIVE,
    DELIBERATIVE,
    METACOGNITIVE
}

enum class ReasoningMotorBinding {
    MORIMIL_INTRINSIC,
    TEMPORARY_AUXILIARY
}

data class SpecializedReasoningRequest(
    val base: AuxiliaryReasoningRequest,
    val candidateReply: String? = null
)

data class SpecializedReasoningResponse(
    val content: String,
    val findings: List<String> = emptyList()
)

/**
 * A computation-only capability. Implementations receive transient inputs and
 * expose no memory, identity, lifecycle or persistence writer.
 */
interface SpecializedReasoningMotor {
    val role: ReasoningMotorRole
    val binding: ReasoningMotorBinding

    suspend fun compute(request: SpecializedReasoningRequest): Result<SpecializedReasoningResponse>
}

/** Keeps the current temporary helper usable as the first intuitive motor. */
class AuxiliaryReasoningMotorAdapter(
    private val delegate: AuxiliaryReasoningMotor,
    override val role: ReasoningMotorRole = ReasoningMotorRole.INTUITIVE
) : SpecializedReasoningMotor {
    override val binding: ReasoningMotorBinding = ReasoningMotorBinding.TEMPORARY_AUXILIARY

    override suspend fun compute(
        request: SpecializedReasoningRequest
    ): Result<SpecializedReasoningResponse> {
        return delegate.compute(request.base).map { reply ->
            SpecializedReasoningResponse(content = reply)
        }
    }
}

data class TriMotorActivationPlan(
    val preferredPrimary: ReasoningMotorRole,
    val primaryOrder: List<ReasoningMotorRole>,
    val verificationRequested: Boolean
) {
    val requestedRoles: List<ReasoningMotorRole>
        get() = buildList {
            add(preferredPrimary)
            if (verificationRequested) add(ReasoningMotorRole.METACOGNITIVE)
        }.distinct()
}

object TriMotorActivationPolicy {
    fun plan(complexity: ReasoningTaskComplexity): TriMotorActivationPlan {
        return when (complexity) {
            ReasoningTaskComplexity.LIGHT_LOCAL,
            ReasoningTaskComplexity.MEMORY_LOCAL,
            ReasoningTaskComplexity.UNKNOWN -> intuitivePlan(verificationRequested = false)

            ReasoningTaskComplexity.WEB_CONTEXT_LOCAL ->
                intuitivePlan(verificationRequested = true)

            ReasoningTaskComplexity.DEEP_ANALYSIS,
            ReasoningTaskComplexity.CODE_REVIEW,
            ReasoningTaskComplexity.ARCHITECTURE_CRITICAL,
            ReasoningTaskComplexity.TOOL_OR_AGENT -> deliberativePlan()
        }
    }

    private fun intuitivePlan(verificationRequested: Boolean): TriMotorActivationPlan {
        return TriMotorActivationPlan(
            preferredPrimary = ReasoningMotorRole.INTUITIVE,
            primaryOrder = listOf(
                ReasoningMotorRole.INTUITIVE,
                ReasoningMotorRole.DELIBERATIVE
            ),
            verificationRequested = verificationRequested
        )
    }

    private fun deliberativePlan(): TriMotorActivationPlan {
        return TriMotorActivationPlan(
            preferredPrimary = ReasoningMotorRole.DELIBERATIVE,
            primaryOrder = listOf(
                ReasoningMotorRole.DELIBERATIVE,
                ReasoningMotorRole.INTUITIVE
            ),
            verificationRequested = true
        )
    }
}

data class TriMotorReasoningResult(
    val reply: String,
    val requestedRoles: List<ReasoningMotorRole>,
    val activatedRoles: List<ReasoningMotorRole>,
    val activatedBindings: List<ReasoningMotorActivation>,
    val unavailableRoles: List<ReasoningMotorRole>,
    val failedRoles: List<ReasoningMotorRole>,
    val findings: List<String>
)

data class ReasoningMotorActivation(
    val role: ReasoningMotorRole,
    val binding: ReasoningMotorBinding
)

class TriMotorReasoningCoordinator(
    motors: List<SpecializedReasoningMotor>
) {
    private val motorsByRole: Map<ReasoningMotorRole, SpecializedReasoningMotor>

    init {
        require(motors.isNotEmpty()) { "At least one reasoning motor is required." }
        require(motors.size <= ReasoningMotorRole.entries.size) {
            "At most three reasoning motors are allowed."
        }
        require(motors.map { it.role }.distinct().size == motors.size) {
            "Each reasoning motor role must be unique."
        }
        motorsByRole = motors.associateBy { it.role }
    }

    suspend fun reason(
        complexity: ReasoningTaskComplexity,
        request: AuxiliaryReasoningRequest
    ): Result<TriMotorReasoningResult> {
        val plan = TriMotorActivationPolicy.plan(complexity)
        val unavailable = linkedSetOf<ReasoningMotorRole>()
        val failed = linkedSetOf<ReasoningMotorRole>()

        val primary = runPrimary(
            plan = plan,
            request = request,
            unavailable = unavailable,
            failed = failed
        ).getOrElse { error -> return Result.failure(error) }

        val activated = mutableListOf(
            ReasoningMotorActivation(primary.role, primary.motor.binding)
        )
        val findings = primary.response.findings.toMutableList()
        var finalReply = primary.response.content

        if (plan.verificationRequested) {
            val verifier = motorsByRole[ReasoningMotorRole.METACOGNITIVE]
            if (verifier == null) {
                unavailable += ReasoningMotorRole.METACOGNITIVE
            } else {
                verifier.compute(
                    SpecializedReasoningRequest(
                        base = request,
                        candidateReply = finalReply
                    )
                ).onSuccess { verification ->
                    if (verification.content.isNotBlank()) {
                        finalReply = verification.content
                    }
                    activated += ReasoningMotorActivation(
                        role = ReasoningMotorRole.METACOGNITIVE,
                        binding = verifier.binding
                    )
                    findings += verification.findings
                }.onFailure {
                    failed += ReasoningMotorRole.METACOGNITIVE
                }
            }
        }

        return Result.success(
            TriMotorReasoningResult(
                reply = finalReply,
                requestedRoles = plan.requestedRoles,
                activatedRoles = activated.map { it.role }.distinct(),
                activatedBindings = activated.distinct(),
                unavailableRoles = unavailable.toList(),
                failedRoles = failed.toList(),
                findings = findings.distinct()
            )
        )
    }

    private suspend fun runPrimary(
        plan: TriMotorActivationPlan,
        request: AuxiliaryReasoningRequest,
        unavailable: MutableSet<ReasoningMotorRole>,
        failed: MutableSet<ReasoningMotorRole>
    ): Result<PrimaryComputation> {
        for (role in plan.primaryOrder) {
            val motor = motorsByRole[role]
            if (motor == null) {
                unavailable += role
                continue
            }
            val result = motor.compute(SpecializedReasoningRequest(base = request))
            val response = result.getOrNull()
            if (response != null && response.content.isNotBlank()) {
                return Result.success(
                    PrimaryComputation(
                        role = role,
                        motor = motor,
                        response = response
                    )
                )
            }
            failed += role
        }
        return Result.failure(IllegalStateException("No primary reasoning motor produced a reply."))
    }

    private data class PrimaryComputation(
        val role: ReasoningMotorRole,
        val motor: SpecializedReasoningMotor,
        val response: SpecializedReasoningResponse
    )
}
