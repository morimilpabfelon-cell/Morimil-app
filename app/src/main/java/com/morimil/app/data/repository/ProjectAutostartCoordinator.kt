package com.morimil.app.data.repository

import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.core.project.ProjectCreationIntent
import com.morimil.app.core.project.ProjectIntentDetector
import com.morimil.app.data.local.ProjectVaultEntity

class ProjectAutostartCoordinator(
    private val projectVaultRepository: ProjectVaultRepository,
    private val agentInstanceLifecycleRepository: AgentInstanceLifecycleRepository
) {
    suspend fun startFromMessageIfNeeded(
        message: String,
        existingVaults: List<ProjectVaultEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): String? {
        val intent = ProjectIntentDetector.detect(message) ?: return null
        if (existingVaults.any { vault -> vault.displayName.equals(intent.displayName, ignoreCase = true) }) {
            return null
        }

        val projectType = ProjectVaultRepository.inferProjectType(intent.displayName, intent.mission)
        val vaultId = projectVaultRepository.createProjectVaultFromIntent(
            displayName = intent.displayName,
            mission = intent.mission,
            projectType = projectType,
            sourceContext = "chat_project_intent:${intent.sourcePhrase}",
            nowMillis = nowMillis
        )

        initialAgentPlans(intent, projectType).forEachIndexed { index, plan ->
            val agentId = agentInstanceLifecycleRepository.createAgentForVault(
                vaultId = vaultId,
                templateAgentId = plan.templateAgentId,
                briefing = plan.briefing,
                nowMillis = nowMillis + index + 1
            )
            agentInstanceLifecycleRepository.assignTaskToAgent(
                agentInstanceId = agentId,
                goal = plan.initialTask,
                nowMillis = nowMillis + index + 10
            )
        }
        return vaultId
    }

    private fun initialAgentPlans(intent: ProjectCreationIntent, projectType: String): List<InitialAgentPlan> {
        val base = listOf(
            InitialAgentPlan(
                templateAgentId = AgentCapabilityPolicy.AGENT_RESEARCH,
                briefing = "Investigar el contexto inicial de ${intent.displayName}. Mantener memoria de trabajo local y reportar solo aprendizajes utiles para Morimil.",
                initialTask = "Mapear problema, usuario objetivo, competencia y riesgos iniciales de ${intent.displayName}."
            ),
            InitialAgentPlan(
                templateAgentId = AgentCapabilityPolicy.AGENT_SECURITY,
                briefing = "Auditar riesgos de ${intent.displayName}. No ejecutar acciones externas; producir advertencias y controles.",
                initialTask = "Identificar riesgos tecnicos, regulatorios, operativos y de aprobacion humana para ${intent.displayName}."
            )
        )

        val specialist = when (projectType) {
            "fintech" -> InitialAgentPlan(
                templateAgentId = AgentCapabilityPolicy.AGENT_FILE_AUDIT,
                briefing = "Preparar arquitectura inicial de ${intent.displayName} con foco fintech, seguridad y trazabilidad.",
                initialTask = "Proponer arquitectura base, modulo de pagos/billetera, controles de auditoria y flujo de aprobaciones para ${intent.displayName}."
            )
            "creative_studio" -> InitialAgentPlan(
                templateAgentId = AgentCapabilityPolicy.AGENT_DESIGN,
                briefing = "Desarrollar direccion visual y flujo creativo inicial para ${intent.displayName}.",
                initialTask = "Proponer identidad visual, pipeline creativo y primeros entregables de ${intent.displayName}."
            )
            "software" -> InitialAgentPlan(
                templateAgentId = AgentCapabilityPolicy.AGENT_GITHUB,
                briefing = "Preparar base tecnica/repositorio de ${intent.displayName} sin tocar ramas protegidas ni ejecutar sin aprobacion.",
                initialTask = "Definir estructura de repositorio, riesgos de build y primer backlog tecnico de ${intent.displayName}."
            )
            else -> InitialAgentPlan(
                templateAgentId = AgentCapabilityPolicy.AGENT_DESIGN,
                briefing = "Convertir la vision de ${intent.displayName} en producto entendible y ejecutable.",
                initialTask = "Proponer oferta, experiencia inicial, marca funcional y primeros hitos de ${intent.displayName}."
            )
        }

        return base + specialist
    }

    private data class InitialAgentPlan(
        val templateAgentId: String,
        val briefing: String,
        val initialTask: String
    )
}
