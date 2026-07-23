package com.morimil.app.reasoning.intrinsic

import com.morimil.app.reasoning.HybridAuthorityRuntimePolicy
import com.morimil.app.reasoning.IntrinsicReasoningMotor
import com.morimil.app.reasoning.IntrinsicTriMotorCoordinator
import com.morimil.app.reasoning.ReasoningMotorRole

/**
 * First normal-runtime registration of Morimil-owned intrinsic computation.
 *
 * Only the bounded local Intuitive motor is active. Deliberative remains blocked
 * by [MorimilNormalDeliberativeActivationGateV0], while Metacognitive and hybrid
 * authority remain outside normal runtime until separate evidence and approval exist.
 */
object MorimilNormalIntrinsicRuntimeV0 {
    const val VERSION = "morimil.intrinsic.normal-runtime.v0"

    val registeredRoles: Set<ReasoningMotorRole> = setOf(ReasoningMotorRole.INTUITIVE)

    val deliberativeActivationDecision: NormalDeliberativeActivationDecisionV0
        get() = MorimilNormalDeliberativeActivationGateV0.currentCandidateDecision

    fun createCoordinator(): IntrinsicTriMotorCoordinator {
        val deliberativeDecision = deliberativeActivationDecision
        check(!deliberativeDecision.activationAllowed) {
            "deliberative_candidate_requires_explicit_registry_revision"
        }
        check(ReasoningMotorRole.DELIBERATIVE !in registeredRoles) {
            "deliberative_role_cannot_register_while_activation_is_blocked"
        }
        check(ReasoningMotorRole.METACOGNITIVE !in registeredRoles) {
            "metacognitive_role_requires_separate_activation_review"
        }

        val motors: List<IntrinsicReasoningMotor> = listOf(
            IntuitiveMotorV0(BoundedLocalIntuitiveCoreV0())
        )
        val actualRoles = motors.map(IntrinsicReasoningMotor::role).toSet()

        require(actualRoles == registeredRoles) {
            "normal_intrinsic_runtime_roles_changed:$actualRoles"
        }

        return IntrinsicTriMotorCoordinator(
            motors = motors,
            runtimePolicy = HybridAuthorityRuntimePolicy(
                hybridAuthorityRuntimeEnabled = false
            )
        )
    }
}
