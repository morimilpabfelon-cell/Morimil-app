package com.morimil.app.reasoning

import com.morimil.app.ai.ChatTurn
import com.morimil.app.reasoning.authority.HybridAuthorityDecision
import com.morimil.app.reasoning.authority.HybridAuthorityRequest
import com.morimil.app.reasoning.authority.HybridAuthorityRoute
import com.morimil.app.reasoning.authority.HybridAuthorityRouterV0
import com.morimil.app.reasoning.model.ReasoningTaskComplexity

/** Stable reasoning roles. Providers and model families remain replaceable. */
enum class ReasoningMotorRole {
    INTUITIVE,
    DELIBERATIVE,
    METACOGNITIVE
}

data class IntrinsicReasoningRequest(
    val systemPrompt: String,
    val history: List<ChatTurn>,
    val taskComplexity: ReasoningTaskComplexity,
    val candidateReply: String? = null,
    val taskKind: ReasoningTaskKind = ReasoningTaskKind.UNKNOWN,
    val authorityPrompt: String? = null
)

data class IntrinsicReasoningResponse(
    val content: String,
    val findings: List<String> = emptyList()
)

/**
 * One of Morimil's own computation-only motors. Its capability version can grow
 * through a separately verified learning pipeline, while runtime inference
 * exposes no API configuration, memory, identity, lifecycle or persistence writer.
 */
interface IntrinsicReasoningMotor {
    val role: ReasoningMotorRole
    val capabilityVersion: String

    suspend fun compute(request: IntrinsicReasoningRequest): Result<IntrinsicReasoningResponse>
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
    val activatedVersions: Map<ReasoningMotorRole, String>,
    val unavailableRoles: List<ReasoningMotorRole>,
    val failedRoles: List<ReasoningMotorRole>,
    val findings: List<String>,
    val primaryCandidate: String = reply,
    val verifierCandidate: String? = null,
    val authorityDecision: HybridAuthorityDecision? = null,
    val finalizationStatus: TriMotorFinalizationStatus =
        TriMotorFinalizationStatus.LEGACY_UNROUTED
)

class IntrinsicTriMotorCoordinator(
    motors: List<IntrinsicReasoningMotor> = emptyList(),
    private val authorityRouter: HybridAuthorityRouterV0 = HybridAuthorityRouterV0(),
    private val runtimePolicy: HybridAuthorityRuntimePolicy = HybridAuthorityRuntimePolicy()
) {
    private val motorsByRole: Map<ReasoningMotorRole, IntrinsicReasoningMotor>

    init {
        require(motors.size <= ReasoningMotorRole.entries.size) {
            "At most three reasoning motors are allowed."
        }
        require(motors.map { it.role }.distinct().size == motors.size) {
            "Each reasoning motor role must be unique."
        }
        require(motors.all { it.capabilityVersion.isNotBlank() }) {
            "Each intrinsic motor must expose a non-empty capability version."
        }
        motorsByRole = motors.associateBy { it.role }
    }

    fun availableRoles(): Set<ReasoningMotorRole> = motorsByRole.keys

    suspend fun reason(request: IntrinsicReasoningRequest): Result<TriMotorReasoningResult> {
        val plan = TriMotorActivationPolicy.plan(request.taskComplexity)
        val unavailable = linkedSetOf<ReasoningMotorRole>()
        val failed = linkedSetOf<ReasoningMotorRole>()

        val primary = runPrimary(
            plan = plan,
            request = request,
            unavailable = unavailable,
            failed = failed
        ).getOrElse { error -> return Result.failure(error) }

        val activated = linkedMapOf(primary.role to primary.motor.capabilityVersion)
        val findings = primary.response.findings.toMutableList()
        val primaryCandidate = primary.response.content
        var verifierCandidate: String? = null
        var legacyReply = primaryCandidate

        if (plan.verificationRequested) {
            val verifier = motorsByRole[ReasoningMotorRole.METACOGNITIVE]
            if (verifier == null) {
                unavailable += ReasoningMotorRole.METACOGNITIVE
            } else {
                val candidateForVerifier = if (runtimePolicy.hybridAuthorityRuntimeEnabled) {
                    null
                } else {
                    primaryCandidate
                }
                verifier.compute(
                    request.copy(candidateReply = candidateForVerifier)
                ).onSuccess { verification ->
                    verifierCandidate = verification.content.takeIf(String::isNotBlank)
                    if (!runtimePolicy.hybridAuthorityRuntimeEnabled && verifierCandidate != null) {
                        legacyReply = requireNotNull(verifierCandidate)
                    }
                    activated[ReasoningMotorRole.METACOGNITIVE] = verifier.capabilityVersion
                    findings += verification.findings
                }.onFailure {
                    failed += ReasoningMotorRole.METACOGNITIVE
                }
            }
        }

        val authorityDecision = if (runtimePolicy.hybridAuthorityRuntimeEnabled) {
            decideWithAuthority(
                request = request,
                primaryCandidate = primaryCandidate,
                verifierCandidate = verifierCandidate
            )
        } else {
            null
        }

        val finalizationStatus: TriMotorFinalizationStatus
        val finalReply: String
        if (authorityDecision == null) {
            finalizationStatus = TriMotorFinalizationStatus.LEGACY_UNROUTED
            finalReply = legacyReply
        } else if (authorityDecision.accepted) {
            finalizationStatus = TriMotorFinalizationStatus.ACCEPTED_BY_AUTHORITY
            finalReply = requireNotNull(authorityDecision.acceptedContent)
            findings += authorityDecision.findings
        } else {
            finalizationStatus = TriMotorFinalizationStatus.ABSTAINED_BY_AUTHORITY
            finalReply = ""
            findings += authorityDecision.findings
        }

        return Result.success(
            TriMotorReasoningResult(
                reply = finalReply,
                requestedRoles = plan.requestedRoles,
                activatedRoles = activated.keys.toList(),
                activatedVersions = activated.toMap(),
                unavailableRoles = unavailable.toList(),
                failedRoles = failed.toList(),
                findings = findings.distinct(),
                primaryCandidate = primaryCandidate,
                verifierCandidate = verifierCandidate,
                authorityDecision = authorityDecision,
                finalizationStatus = finalizationStatus
            )
        )
    }

    private fun decideWithAuthority(
        request: IntrinsicReasoningRequest,
        primaryCandidate: String,
        verifierCandidate: String?
    ): HybridAuthorityDecision {
        val prompt = request.authorityPrompt
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: request.history
                .lastOrNull { turn -> turn.role.equals("user", ignoreCase = true) }
                ?.content
                ?.trim()
                ?.takeIf(String::isNotBlank)

        if (prompt == null) {
            return HybridAuthorityDecision.abstain(
                route = HybridAuthorityRoute.UNSUPPORTED,
                reason = "hybrid_authority_prompt_missing"
            )
        }

        return authorityRouter.decide(
            HybridAuthorityRequest(
                taskKind = request.taskKind.toHybridAuthorityTaskKind(),
                prompt = prompt,
                directReply = primaryCandidate,
                verifierReply = verifierCandidate
            )
        )
    }

    private suspend fun runPrimary(
        plan: TriMotorActivationPlan,
        request: IntrinsicReasoningRequest,
        unavailable: MutableSet<ReasoningMotorRole>,
        failed: MutableSet<ReasoningMotorRole>
    ): Result<PrimaryComputation> {
        for (role in plan.primaryOrder) {
            val motor = motorsByRole[role]
            if (motor == null) {
                unavailable += role
                continue
            }
            val result = motor.compute(request.copy(candidateReply = null))
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
        return Result.failure(
            IllegalStateException("No primary reasoning motor produced a reply.")
        )
    }

    private data class PrimaryComputation(
        val role: ReasoningMotorRole,
        val motor: IntrinsicReasoningMotor,
        val response: IntrinsicReasoningResponse
    )
}
