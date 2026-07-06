package com.morimil.app.data.repository

import com.morimil.app.core.identity.StableIdDigest
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.ProjectVaultEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

class ProjectVaultRepository(
    organDatabase: MemoryOrganDatabase,
    private val memoryRepository: MemoryRepository
) {
    private val dao = organDatabase.memoryOrganDao()

    val projectVaults: Flow<List<ProjectVaultEntity>> = dao.observeProjectVaults()

    suspend fun createProjectVaultFromIntent(
        displayName: String,
        mission: String,
        projectType: String = inferProjectType(displayName, mission),
        sourceContext: String = "user_intent",
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val cleanName = displayName.trim().ifBlank { "Nuevo proyecto" }
        val cleanMission = mission.trim().ifBlank { "Construir y coordinar un proyecto nuevo." }
        val vaultId = buildVaultId(cleanName, nowMillis)
        val vault = ProjectVaultEntity(
            vaultId = vaultId,
            displayName = cleanName,
            companyName = cleanName,
            projectType = projectType,
            mission = cleanMission,
            status = STATUS_ACTIVE,
            roadmapSummary = buildInitialRoadmap(cleanName, cleanMission),
            progressPercent = 0,
            activeAgentCount = 0,
            healthStatus = HEALTH_PLANNING,
            sourceContext = sourceContext,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            completedAtMillis = null
        )
        dao.insertProjectVault(vault)
        recordVaultEvent(EVENT_VAULT_CREATED, vault, "created", cleanMission, nowMillis)
        return vaultId
    }

    suspend fun completeProjectVault(
        vaultId: String,
        finalSummary: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val updated = dao.completeProjectVault(
            vaultId = vaultId,
            status = STATUS_COMPLETED,
            healthStatus = HEALTH_COMPLETED,
            progressPercent = 100,
            roadmapSummary = finalSummary.trim().ifBlank { "Proyecto completado." },
            updatedAtMillis = nowMillis,
            completedAtMillis = nowMillis
        ) > 0

        if (updated) {
            dao.loadProjectVault(vaultId)?.let { vault ->
                recordVaultEvent(EVENT_VAULT_COMPLETED, vault, "completed", finalSummary, nowMillis, 96)
            }
        }
        return updated
    }

    suspend fun archiveProjectVault(
        vaultId: String,
        reason: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val updated = dao.archiveProjectVault(
            vaultId = vaultId,
            status = STATUS_ARCHIVED,
            healthStatus = HEALTH_ARCHIVED,
            updatedAtMillis = nowMillis
        ) > 0

        if (updated) {
            dao.loadProjectVault(vaultId)?.let { vault ->
                recordVaultEvent(EVENT_VAULT_ARCHIVED, vault, "archived", reason, nowMillis, 88)
            }
        }
        return updated
    }

    private suspend fun recordVaultEvent(
        eventType: String,
        vault: ProjectVaultEntity,
        action: String,
        note: String,
        nowMillis: Long,
        importance: Int = 92
    ) {
        memoryRepository.recordSystemMemoryEvent(
            eventType = eventType,
            body = "Boveda de proyecto $action: ${vault.displayName}; type=${vault.projectType}; status=${vault.status}; mission=${vault.mission}",
            importance = importance,
            evidenceJson = JSONObject()
                .put("schema", "morimil.project_vault_event.v1")
                .put("event_type", eventType)
                .put("action", action)
                .put("recorded_at_millis", nowMillis)
                .put("vault_id", vault.vaultId)
                .put("display_name", vault.displayName)
                .put("company_name", vault.companyName)
                .put("project_type", vault.projectType)
                .put("status", vault.status)
                .put("health_status", vault.healthStatus)
                .put("progress_percent", vault.progressPercent)
                .put("roadmap_summary", vault.roadmapSummary)
                .put("mission", vault.mission)
                .put("note", note)
                .toString()
        )
    }

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_ARCHIVED = "archived"
        const val HEALTH_PLANNING = "planning"
        const val HEALTH_COMPLETED = "completed"
        const val HEALTH_ARCHIVED = "archived"

        private const val EVENT_VAULT_CREATED = "project.vault_created"
        private const val EVENT_VAULT_COMPLETED = "project.vault_completed"
        private const val EVENT_VAULT_ARCHIVED = "project.vault_archived"

        fun buildVaultId(displayName: String, nowMillis: Long): String {
            val suffix = StableIdDigest.shortSha256Hex(
                namespace = "project_vault",
                parts = listOf(displayName.lowercase(), nowMillis.toString())
            )
            return "vault_${nowMillis}_$suffix"
        }

        fun inferProjectType(displayName: String, mission: String): String {
            val text = "$displayName $mission".lowercase()
            return when {
                listOf("pago", "payment", "billetera", "wallet", "fintech", "exchange").any { it in text } -> "fintech"
                listOf("anime", "animacion", "animation", "studio", "dibujo").any { it in text } -> "creative_studio"
                listOf("app", "android", "software", "repo").any { it in text } -> "software"
                else -> "company_project"
            }
        }

        fun buildInitialRoadmap(displayName: String, mission: String): String {
            return "1. Definir vision de $displayName. 2. Capturar contexto base. 3. Crear enjambre inicial. 4. Proponer arquitectura. 5. Validar avances con aprobacion humana. Mision: $mission"
        }
    }
}
