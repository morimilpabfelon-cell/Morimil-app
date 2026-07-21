package com.morimil.app.reasoning.intrinsic

/** The computation mechanism a deliberative runtime can honestly claim. */
enum class DeliberativeStateKindV03 {
    TEXTUAL_CONVERSATION,
    LATENT_RECURRENT
}

/** Terminal reason for one bounded request-scoped loop. */
enum class DeliberativeLoopStopReasonV03 {
    CONVERGED,
    BUDGET_EXHAUSTED,
    MEMORY_LIMIT,
    THERMAL_LIMIT,
    ENERGY_LIMIT,
    INVALID_STATE,
    ENGINE_FAILURE
}

/**
 * Research-only capability declaration for a future Morimil deliberative runtime.
 *
 * This type does not load, install, persist, authorize or execute a model. It makes
 * latent-recurrence claims fail closed and preserves Morimil's memory, identity and
 * lifecycle boundaries while the v0.3 direction is investigated.
 */
data class DeliberativeRuntimeCapabilitiesV03(
    val stateKind: DeliberativeStateKindV03,
    val maximumIterations: Int,
    val supportsVariableIterations: Boolean,
    val supportsHiddenStateReinjection: Boolean,
    val reusesBackboneWeightsAcrossIterations: Boolean,
    val supportsLatentReadout: Boolean,
    val supportsConvergenceEvidence: Boolean,
    val requestScopedOnly: Boolean = true,
    val persistsWorkingState: Boolean = false,
    val memoryWriteCapability: Boolean = false,
    val identityAuthority: Boolean = false,
    val installable: Boolean = false,
    val productionEnabled: Boolean = false
) {
    init {
        require(maximumIterations in 1..MAXIMUM_ALLOWED_ITERATIONS) {
            "Loop-effort iterations must remain between 1 and 8."
        }
        require(requestScopedOnly) {
            "The v0.3 research runtime must remain request-scoped."
        }
        require(!persistsWorkingState) {
            "The v0.3 research runtime must not persist working state."
        }
        require(!memoryWriteCapability) {
            "The v0.3 research runtime must not expose a memory writer."
        }
        require(!identityAuthority) {
            "The v0.3 research runtime must not expose identity authority."
        }
        require(!installable) {
            "The v0.3 research capability contract is not installable."
        }
        require(!productionEnabled) {
            "The v0.3 research capability contract cannot enable production."
        }

        when (stateKind) {
            DeliberativeStateKindV03.TEXTUAL_CONVERSATION -> {
                require(!supportsHiddenStateReinjection) {
                    "Textual recurrence cannot claim hidden-state reinjection."
                }
                require(!reusesBackboneWeightsAcrossIterations) {
                    "Textual recurrence cannot claim recurrent backbone reuse."
                }
                require(!supportsLatentReadout) {
                    "Textual recurrence cannot claim latent readout."
                }
            }

            DeliberativeStateKindV03.LATENT_RECURRENT -> {
                require(maximumIterations >= MINIMUM_LATENT_ITERATIONS) {
                    "Latent recurrence requires at least two bounded iterations."
                }
                require(supportsHiddenStateReinjection) {
                    "Latent recurrence requires hidden-state reinjection."
                }
                require(reusesBackboneWeightsAcrossIterations) {
                    "Latent recurrence requires reuse of the same backbone weights."
                }
            }
        }
    }

    val latentRecurrenceClaimAllowed: Boolean
        get() = stateKind == DeliberativeStateKindV03.LATENT_RECURRENT &&
            supportsHiddenStateReinjection &&
            reusesBackboneWeightsAcrossIterations

    companion object {
        const val CONTRACT_VERSION = "morimil.deliberative.loop-effort.research.v0.3"
        const val MAXIMUM_ALLOWED_ITERATIONS = 8
        private const val MINIMUM_LATENT_ITERATIONS = 2
    }
}
