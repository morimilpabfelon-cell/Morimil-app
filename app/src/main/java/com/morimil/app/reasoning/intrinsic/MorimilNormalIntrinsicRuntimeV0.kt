package com.morimil.app.reasoning.intrinsic

import com.morimil.app.reasoning.HybridAuthorityRuntimePolicy
import com.morimil.app.reasoning.IntrinsicReasoningMotor
import com.morimil.app.reasoning.IntrinsicTriMotorCoordinator
import com.morimil.app.reasoning.ReasoningMotorRole

/**
 * First normal-runtime registration of Morimil-owned intrinsic computation.
 *
 * Only the bounded local Intuitive motor is active. Deliberative,
 * Metacognitive and hybrid-authority runtime activation remain outside this
 * registry until separate evidence and approval exist.
 */
object MorimilNormalIntrinsicRuntimeV0 {
    const val VERSION = "morimil.intrinsic.normal-runtime.v0"

    val registeredRoles: Set<ReasoningMotorRole> = setOf(ReasoningMotorRole.INTUITIVE)

    fun createCoordinator(): IntrinsicTriMotorCoordinator {
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
