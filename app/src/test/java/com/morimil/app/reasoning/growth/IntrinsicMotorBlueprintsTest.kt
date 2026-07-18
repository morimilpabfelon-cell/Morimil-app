package com.morimil.app.reasoning.growth

import com.morimil.app.reasoning.ReasoningMotorRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntrinsicMotorBlueprintsTest {
    @Test
    fun everyIntrinsicRoleHasOneBlueprintWithUniqueTechniques() {
        assertEquals(ReasoningMotorRole.entries.toSet(), MorimilIntrinsicMotorBlueprints.all.keys)

        MorimilIntrinsicMotorBlueprints.all.values.forEach { blueprint ->
            assertTrue(blueprint.techniques.isNotEmpty())
            assertEquals(
                blueprint.techniques.size,
                blueprint.techniques.map { it.technique }.distinct().size
            )
        }
    }

    @Test
    fun lotusAndInklingIdeasAreAssignedWithoutBecomingProviders() {
        val intuitive = MorimilIntrinsicMotorBlueprints.requireBlueprint(
            ReasoningMotorRole.INTUITIVE
        )
        val deliberative = MorimilIntrinsicMotorBlueprints.requireBlueprint(
            ReasoningMotorRole.DELIBERATIVE
        )
        val metacognitive = MorimilIntrinsicMotorBlueprints.requireBlueprint(
            ReasoningMotorRole.METACOGNITIVE
        )

        assertTrue(intuitive.techniques.any { it.lineage == ResearchLineage.INKLING })
        assertTrue(deliberative.techniques.any { it.lineage == ResearchLineage.LOTUS })
        assertTrue(deliberative.techniques.any { it.lineage == ResearchLineage.INKLING })
        assertTrue(metacognitive.techniques.any { it.lineage == ResearchLineage.INKLING })
        assertTrue(
            MorimilIntrinsicMotorBlueprints.all.values
                .flatMap { it.techniques }
                .none { technique ->
                    technique.researchReference.contains("apiKey", ignoreCase = true) ||
                        technique.researchReference.contains("endpoint", ignoreCase = true)
                }
        )
    }
}
