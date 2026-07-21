package com.morimil.app.reasoning.intrinsic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliberativeRuntimeCapabilitiesV03Test {
    @Test
    fun textualBaselineCannotClaimLatentRecurrence() {
        val capabilities = textualCapabilities()

        assertEquals(
            DeliberativeStateKindV03.TEXTUAL_CONVERSATION,
            capabilities.stateKind
        )
        assertFalse(capabilities.latentRecurrenceClaimAllowed)
        assertFalse(capabilities.supportsHiddenStateReinjection)
        assertFalse(capabilities.sharesBackboneParametersInsideLatentLoop)
        assertFalse(capabilities.supportsLatentReadout)
    }

    @Test
    fun genuineLatentResearchDeclarationRequiresBothCoreMechanisms() {
        val capabilities = latentCapabilities()

        assertEquals(DeliberativeStateKindV03.LATENT_RECURRENT, capabilities.stateKind)
        assertTrue(capabilities.supportsHiddenStateReinjection)
        assertTrue(capabilities.sharesBackboneParametersInsideLatentLoop)
        assertTrue(capabilities.latentRecurrenceClaimAllowed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun textualRuntimeCannotClaimHiddenStateReinjection() {
        textualCapabilities(supportsHiddenStateReinjection = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun textualRuntimeCannotClaimLatentLoopParameterSharing() {
        textualCapabilities(sharesBackboneParametersInsideLatentLoop = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun latentRuntimeWithoutHiddenStateReinjectionIsRejected() {
        latentCapabilities(supportsHiddenStateReinjection = false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun latentRuntimeWithoutSharedBackboneParametersIsRejected() {
        latentCapabilities(sharesBackboneParametersInsideLatentLoop = false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun latentRuntimeRequiresAtLeastTwoIterations() {
        latentCapabilities(maximumIterations = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun researchRuntimeRejectsMoreThanEightIterations() {
        latentCapabilities(maximumIterations = 9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun researchRuntimeCannotPersistWorkingState() {
        latentCapabilities(persistsWorkingState = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun researchRuntimeCannotWriteMorimilMemory() {
        latentCapabilities(memoryWriteCapability = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun researchRuntimeCannotClaimIdentityAuthority() {
        latentCapabilities(identityAuthority = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun researchRuntimeCannotBecomeInstallable() {
        latentCapabilities(installable = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun researchRuntimeCannotEnableProduction() {
        latentCapabilities(productionEnabled = true)
    }

    @Test
    fun capabilityContractExposesNoProviderMemoryOrLifecycleWriter() {
        val forbidden = listOf(
            "Provider",
            "Endpoint",
            "Credential",
            "Http",
            "Socket",
            "Repository",
            "Dao",
            "MemoryUseCase",
            "IdentityWriter",
            "Lifecycle",
            "Installer",
            "Persistence"
        )
        val contractTypes = listOf(
            DeliberativeStateKindV03::class.java,
            DeliberativeLoopStopReasonV03::class.java,
            DeliberativeRuntimeCapabilitiesV03::class.java
        )
        val exposedTypeNames = contractTypes.flatMap { type ->
            type.methods.flatMap { method ->
                method.parameterTypes.toList() + method.returnType
            } + type.declaredFields.map { field -> field.type }
        }.map { type -> type.name }

        assertTrue(exposedTypeNames.isNotEmpty())
        assertTrue(
            exposedTypeNames.none { name -> forbidden.any { token -> name.contains(token) } }
        )
    }

    @Test
    fun allStopReasonsRemainExplicitAndBounded() {
        assertEquals(
            listOf(
                "CONVERGED",
                "BUDGET_EXHAUSTED",
                "MEMORY_LIMIT",
                "THERMAL_LIMIT",
                "ENERGY_LIMIT",
                "INVALID_STATE",
                "ENGINE_FAILURE"
            ),
            DeliberativeLoopStopReasonV03.entries.map { reason -> reason.name }
        )
    }

    private fun textualCapabilities(
        maximumIterations: Int = 8,
        supportsHiddenStateReinjection: Boolean = false,
        sharesBackboneParametersInsideLatentLoop: Boolean = false
    ): DeliberativeRuntimeCapabilitiesV03 {
        return DeliberativeRuntimeCapabilitiesV03(
            stateKind = DeliberativeStateKindV03.TEXTUAL_CONVERSATION,
            maximumIterations = maximumIterations,
            supportsVariableIterations = true,
            supportsHiddenStateReinjection = supportsHiddenStateReinjection,
            sharesBackboneParametersInsideLatentLoop =
                sharesBackboneParametersInsideLatentLoop,
            supportsLatentReadout = false,
            supportsConvergenceEvidence = true
        )
    }

    private fun latentCapabilities(
        maximumIterations: Int = 8,
        supportsHiddenStateReinjection: Boolean = true,
        sharesBackboneParametersInsideLatentLoop: Boolean = true,
        persistsWorkingState: Boolean = false,
        memoryWriteCapability: Boolean = false,
        identityAuthority: Boolean = false,
        installable: Boolean = false,
        productionEnabled: Boolean = false
    ): DeliberativeRuntimeCapabilitiesV03 {
        return DeliberativeRuntimeCapabilitiesV03(
            stateKind = DeliberativeStateKindV03.LATENT_RECURRENT,
            maximumIterations = maximumIterations,
            supportsVariableIterations = true,
            supportsHiddenStateReinjection = supportsHiddenStateReinjection,
            sharesBackboneParametersInsideLatentLoop =
                sharesBackboneParametersInsideLatentLoop,
            supportsLatentReadout = true,
            supportsConvergenceEvidence = true,
            persistsWorkingState = persistsWorkingState,
            memoryWriteCapability = memoryWriteCapability,
            identityAuthority = identityAuthority,
            installable = installable,
            productionEnabled = productionEnabled
        )
    }
}
