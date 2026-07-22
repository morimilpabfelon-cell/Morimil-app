package com.morimil.app.reasoning

import com.morimil.app.reasoning.intrinsic.DeliberativeMotorV0
import com.morimil.app.reasoning.intrinsic.IntuitiveMotorV0
import com.morimil.app.reasoning.intrinsic.MetacognitiveMotorV0

/**
 * Isolated research-only registration of Morimil's three intrinsic motor roles.
 *
 * Hybrid authority is always enabled here. This runtime has no installer, provider,
 * memory writer, identity writer, lifecycle authority or normal-runtime activation.
 */
class IntrinsicTriMotorResearchRuntimeV0 private constructor(
    private val coordinator: IntrinsicTriMotorCoordinator
) {
    val runtimeVersion: String = VERSION

    fun availableRoles(): Set<ReasoningMotorRole> = coordinator.availableRoles()

    suspend fun reason(
        request: IntrinsicReasoningRequest
    ): Result<TriMotorReasoningResult> = coordinator.reason(request)

    companion object {
        const val VERSION = "morimil.intrinsic-trimotor.research-runtime.v0"

        fun create(
            intuitiveMotor: IntuitiveMotorV0,
            deliberativeMotor: DeliberativeMotorV0,
            metacognitiveMotor: MetacognitiveMotorV0
        ): IntrinsicTriMotorResearchRuntimeV0 {
            val motors = listOf(
                intuitiveMotor,
                deliberativeMotor,
                metacognitiveMotor
            )
            val roles = motors.map(IntrinsicReasoningMotor::role)

            require(roles.toSet() == ReasoningMotorRole.entries.toSet()) {
                "research_runtime_requires_exact_trimotor_roles"
            }
            require(motors.all { motor -> motor.capabilityVersion.isNotBlank() }) {
                "research_runtime_motor_version_missing"
            }

            return IntrinsicTriMotorResearchRuntimeV0(
                coordinator = IntrinsicTriMotorCoordinator(
                    motors = motors,
                    runtimePolicy = HybridAuthorityRuntimePolicy(
                        hybridAuthorityRuntimeEnabled = true
                    )
                )
            )
        }
    }
}
