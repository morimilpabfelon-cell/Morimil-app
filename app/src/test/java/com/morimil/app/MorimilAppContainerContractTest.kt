package com.morimil.app

import org.junit.Assert.assertTrue
import org.junit.Test

class MorimilAppContainerContractTest {
    @Test
    fun appContainerExposesCriticalRuntimeDependenciesFromOneRoot() {
        val methodNames = MorimilAppContainer::class.java.methods.map { method -> method.name }.toSet()

        assertTrue(methodNames.contains("getMemoryDatabase"))
        assertTrue(methodNames.contains("getOrganDatabase"))
        assertTrue(methodNames.contains("getMemorySignatureEpochPolicy"))
        assertTrue(methodNames.contains("getMemoryEventSigner"))
        assertTrue(methodNames.contains("getMemoryIntegrityCore"))
        assertTrue(methodNames.contains("getMemoryRepository"))
        assertTrue(methodNames.contains("getRestCycleRepository"))
        assertTrue(methodNames.contains("getMemoryOrganRepository"))
        assertTrue(methodNames.contains("getCognitiveMigrationRepository"))
        assertTrue(methodNames.contains("getRecallScheduleRepository"))
    }
}
