package com.morimil.app.reasoning.growth

import com.morimil.app.reasoning.ReasoningMotorRole

enum class ResearchLineage {
    LOTUS,
    INKLING,
    MORIMIL
}

enum class IntrinsicMotorTechnique {
    LOOPED_LATENT_DEPTH,
    DIRECT_LANGUAGE_MODEL_HEAD,
    ADAPTIVE_REASONING_EFFORT,
    CALIBRATED_UNCERTAINTY,
    CLAIM_RUBRIC_VERIFICATION,
    LOCAL_FIRST_EXECUTION,
    INTRINSIC_EXTERNAL_BOUNDARY
}

data class TechniqueAttribution(
    val technique: IntrinsicMotorTechnique,
    val lineage: ResearchLineage,
    val researchReference: String,
    val adaptationGoal: String
)

data class IntrinsicMotorBlueprint(
    val role: ReasoningMotorRole,
    val techniques: List<TechniqueAttribution>
) {
    val requiredTechniques: Set<IntrinsicMotorTechnique>
        get() = techniques.map { it.technique }.toSet()
}

/**
 * Morimil-owned adaptations of selected research ideas. These blueprints carry
 * no model weights, provider client, endpoint or API credential.
 */
object MorimilIntrinsicMotorBlueprints {
    const val VERSION = "morimil.reasoning_growth.v1"
    const val LOTUS_REFERENCE = "https://arxiv.org/abs/2606.31779"
    const val INKLING_REFERENCE = "https://thinkingmachines.ai/news/introducing-inkling/"
    const val MORIMIL_REFERENCE = "morimil://reasoning-kernel/intrinsic-boundary/v1"

    val all: Map<ReasoningMotorRole, IntrinsicMotorBlueprint> = listOf(
        IntrinsicMotorBlueprint(
            role = ReasoningMotorRole.INTUITIVE,
            techniques = listOf(
                attribution(
                    IntrinsicMotorTechnique.ADAPTIVE_REASONING_EFFORT,
                    ResearchLineage.INKLING,
                    INKLING_REFERENCE,
                    "Use the smallest sufficient reasoning budget and escalate only when needed."
                ),
                attribution(
                    IntrinsicMotorTechnique.CALIBRATED_UNCERTAINTY,
                    ResearchLineage.INKLING,
                    INKLING_REFERENCE,
                    "Express uncertainty instead of manufacturing unsupported certainty."
                ),
                attribution(
                    IntrinsicMotorTechnique.LOCAL_FIRST_EXECUTION,
                    ResearchLineage.MORIMIL,
                    MORIMIL_REFERENCE,
                    "Remain available locally without requiring a temporary external provider."
                )
            )
        ),
        IntrinsicMotorBlueprint(
            role = ReasoningMotorRole.DELIBERATIVE,
            techniques = listOf(
                attribution(
                    IntrinsicMotorTechnique.LOOPED_LATENT_DEPTH,
                    ResearchLineage.LOTUS,
                    LOTUS_REFERENCE,
                    "Reuse internal depth for multi-step reasoning without exposing latent state as memory."
                ),
                attribution(
                    IntrinsicMotorTechnique.DIRECT_LANGUAGE_MODEL_HEAD,
                    ResearchLineage.LOTUS,
                    LOTUS_REFERENCE,
                    "Produce the final answer through a direct output head after latent deliberation."
                ),
                attribution(
                    IntrinsicMotorTechnique.ADAPTIVE_REASONING_EFFORT,
                    ResearchLineage.INKLING,
                    INKLING_REFERENCE,
                    "Choose deliberation depth from task difficulty and resource budget."
                )
            )
        ),
        IntrinsicMotorBlueprint(
            role = ReasoningMotorRole.METACOGNITIVE,
            techniques = listOf(
                attribution(
                    IntrinsicMotorTechnique.CLAIM_RUBRIC_VERIFICATION,
                    ResearchLineage.INKLING,
                    INKLING_REFERENCE,
                    "Review claims against explicit task rubrics before accepting an answer."
                ),
                attribution(
                    IntrinsicMotorTechnique.CALIBRATED_UNCERTAINTY,
                    ResearchLineage.INKLING,
                    INKLING_REFERENCE,
                    "Detect uncertainty, disagreement and insufficient evidence."
                ),
                attribution(
                    IntrinsicMotorTechnique.INTRINSIC_EXTERNAL_BOUNDARY,
                    ResearchLineage.MORIMIL,
                    MORIMIL_REFERENCE,
                    "Keep external advice temporary and outside intrinsic motor state."
                )
            )
        )
    ).associateBy { it.role }

    fun requireBlueprint(role: ReasoningMotorRole): IntrinsicMotorBlueprint {
        return requireNotNull(all[role]) { "Missing intrinsic motor blueprint for $role." }
    }

    private fun attribution(
        technique: IntrinsicMotorTechnique,
        lineage: ResearchLineage,
        researchReference: String,
        adaptationGoal: String
    ): TechniqueAttribution {
        return TechniqueAttribution(
            technique = technique,
            lineage = lineage,
            researchReference = researchReference,
            adaptationGoal = adaptationGoal
        )
    }
}
