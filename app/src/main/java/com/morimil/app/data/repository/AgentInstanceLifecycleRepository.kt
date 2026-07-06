package com.morimil.app.data.repository

import com.morimil.app.core.identity.StableIdDigest
import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.data.local.AgentInstanceEntity
import com.morimil.app.data.local.DelegatedTaskEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.ProjectVaultEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

class AgentInstanceLifecycleRepository(
    organDatabase: MemoryOrganDatabase,
    private val memoryRepository: MemoryRepository
) {
    private val dao = organDatabase.memoryOrganDao()

    val agentInstances: Flow<List<AgentInstanceEntity>> = dao.observeAgentInstances()

    suspend fun createAgentForVault(
        vaultId: String,
        templateAgentId: String = AgentCapabilityPolicy.AGENT_FILE_AUDIT,
        briefing: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val vault = requireVault(vaultId)
        val cleanBriefing = briefing?.trim().takeUnless { it.isNullOrBlank() }
            ?: "Trabajador temporal creado para ${vault.displayName}. Debe operar solo dentro de esta boveda, reportar resultados y esperar aprobacion humana antes de ejecutar cambios reales."
        val agentInstanceId = buildAgentInstanceId(vaultId, templateAgentId, nowMillis)
        val instance = AgentInstanceEntity(
            agentInstanceId = agentInstanceId,
            projectVaultId = vaultId,
            templateAgentId = templateAgentId,
            displayName = buildAgentDisplayName(vault.displayName, templateAgentId),
            briefing = cleanBriefing,
            constraintsJson = buildConstraintsJson(vault),
            status = STATUS_THINKING,
            qualityScore = 50,
            errorCount = 0,
            currentTaskId = null,
            lastHeartbeatAtMillis = nowMillis,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            retiredAtMillis = null,
            retireReason = null
        )
        dao.insertAgentInstance(instance)
        dao.refreshProjectVaultActiveAgentCount(vaultId, nowMillis)
        recordAgentEvent(EVENT_AGENT_CREATED, "created", vault, instance, nowMillis, cleanBriefing, 94)
        recordAgentEvent(EVENT_AGENT_BRIEFED, "briefed", vault, instance, nowMillis, cleanBriefing, 92)
        return agentInstanceId
    }

    suspend fun assignTaskToAgent(
        agentInstanceId: String,
        goal: String,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val instance = requireAgent(agentInstanceId)
        val vault = requireVault(instance.projectVaultId)
        val cleanGoal = goal.trim().ifBlank { "Preparar avance verificable para ${vault.displayName}" }
        val plan = AgentCapabilityPolicy.planDelegation(cleanGoal, instance.templateAgentId, targetDeviceId = null)
        val taskId = buildProjectTaskId(nowMillis, agentInstanceId, cleanGoal)
        val task = DelegatedTaskEntity(
            taskId = taskId,
            createdBy = "morimil_project_vault",
            assignedAgentId = agentInstanceId,
            targetDeviceId = plan.targetDeviceId,
            goal = cleanGoal,
            contextSummary = "vault=${vault.displayName}; vault_id=${vault.vaultId}; template_agent=${instance.templateAgentId}; ${plan.contextSummary}",
            inputRefsJson = "[]",
            allowedActionsJson = AgentCapabilityPolicy.encodeJson(plan.allowedActions),
            allowedTransportsJson = AgentCapabilityPolicy.encodeJson(plan.allowedTransports),
            approvalRequired = true,
            approvalId = null,
            status = AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL,
            riskLevel = plan.riskLevel,
            resultSummary = null,
            errorSummary = null,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            completedAtMillis = null
        )
        dao.insertDelegatedTask(task)
        dao.assignAgentInstanceTask(
            agentInstanceId = agentInstanceId,
            taskId = taskId,
            status = STATUS_AWAITING_REVIEW,
            lastHeartbeatAtMillis = nowMillis,
            updatedAtMillis = nowMillis
        )
        dao.refreshProjectVaultActiveAgentCount(vault.vaultId, nowMillis)
        recordTaskEvent(EVENT_TASK_ASSIGNED, "assigned", vault, instance, task, nowMillis, "Tarea asignada; requiere aprobacion antes de ejecutar.", 96)
        return taskId
    }

    suspend fun submitAgentResult(
        agentInstanceId: String,
        summary: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val instance = requireAgent(agentInstanceId)
        val vault = requireVault(instance.projectVaultId)
        val cleanSummary = summary.trim().ifBlank { "Resultado pendiente de revision humana." }
        val taskId = instance.currentTaskId
        if (taskId != null) {
            dao.updateDelegatedTaskResult(
                taskId = taskId,
                status = STATUS_AWAITING_REVIEW,
                resultSummary = cleanSummary,
                updatedAtMillis = nowMillis,
                completedAtMillis = null
            )
        }
        val updated = dao.updateAgentInstanceLifecycle(
            agentInstanceId = agentInstanceId,
            status = STATUS_AWAITING_REVIEW,
            qualityScore = instance.qualityScore,
            errorCount = instance.errorCount,
            currentTaskId = taskId,
            lastHeartbeatAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            retiredAtMillis = null,
            retireReason = null
        ) > 0
        if (updated) {
            dao.refreshProjectVaultActiveAgentCount(vault.vaultId, nowMillis)
            recordAgentEvent(EVENT_AGENT_RESULT_SUBMITTED, "result_submitted", vault, instance.copy(status = STATUS_AWAITING_REVIEW), nowMillis, cleanSummary, 95)
        }
        return updated
    }

    suspend fun evaluateAgent(
        agentInstanceId: String,
        status: String,
        qualityScore: Int,
        note: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val instance = requireAgent(agentInstanceId)
        val vault = requireVault(instance.projectVaultId)
        val cleanStatus = normalizeReviewStatus(status)
        val cleanScore = qualityScore.coerceIn(0, 100)
        val updated = dao.updateAgentInstanceLifecycle(
            agentInstanceId = agentInstanceId,
            status = cleanStatus,
            qualityScore = cleanScore,
            errorCount = instance.errorCount,
            currentTaskId = instance.currentTaskId,
            lastHeartbeatAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            retiredAtMillis = null,
            retireReason = null
        ) > 0
        if (updated) {
            dao.refreshProjectVaultActiveAgentCount(vault.vaultId, nowMillis)
            recordAgentEvent(EVENT_AGENT_EVALUATED, "evaluated", vault, instance.copy(status = cleanStatus, qualityScore = cleanScore), nowMillis, note, 94)
        }
        return updated
    }

    suspend fun retireAgent(agentInstanceId: String, reason: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        return closeAgent(agentInstanceId, STATUS_RETIRED, EVENT_AGENT_RETIRED, "retired", reason, nowMillis, addError = false)
    }

    suspend fun quarantineAgent(agentInstanceId: String, reason: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        return closeAgent(agentInstanceId, STATUS_QUARANTINED, EVENT_AGENT_QUARANTINED, "quarantined", reason, nowMillis, addError = true)
    }

    suspend fun promoteAgent(agentInstanceId: String, reason: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val instance = requireAgent(agentInstanceId)
        val vault = requireVault(instance.projectVaultId)
        val updated = dao.updateAgentInstanceLifecycle(
            agentInstanceId = agentInstanceId,
            status = STATUS_PROMOTED,
            qualityScore = maxOf(instance.qualityScore, 90),
            errorCount = instance.errorCount,
            currentTaskId = instance.currentTaskId,
            lastHeartbeatAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            retiredAtMillis = null,
            retireReason = reason.take(240)
        ) > 0
        if (updated) {
            dao.refreshProjectVaultActiveAgentCount(vault.vaultId, nowMillis)
            recordAgentEvent(EVENT_AGENT_PROMOTED, "promoted", vault, instance.copy(status = STATUS_PROMOTED, qualityScore = maxOf(instance.qualityScore, 90)), nowMillis, reason, 98)
        }
        return updated
    }

    private suspend fun closeAgent(
        agentInstanceId: String,
        status: String,
        eventType: String,
        action: String,
        reason: String,
        nowMillis: Long,
        addError: Boolean
    ): Boolean {
        val instance = requireAgent(agentInstanceId)
        val vault = requireVault(instance.projectVaultId)
        val updated = dao.updateAgentInstanceLifecycle(
            agentInstanceId = agentInstanceId,
            status = status,
            qualityScore = instance.qualityScore,
            errorCount = instance.errorCount + if (addError) 1 else 0,
            currentTaskId = instance.currentTaskId,
            lastHeartbeatAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            retiredAtMillis = nowMillis,
            retireReason = reason.take(240)
        ) > 0
        if (updated) {
            dao.refreshProjectVaultActiveAgentCount(vault.vaultId, nowMillis)
            recordAgentEvent(eventType, action, vault, instance.copy(status = status), nowMillis, reason, if (addError) 99 else 92)
        }
        return updated
    }

    private suspend fun requireVault(vaultId: String): ProjectVaultEntity {
        return dao.loadProjectVault(vaultId) ?: error("Project vault not found: $vaultId")
    }

    private suspend fun requireAgent(agentInstanceId: String): AgentInstanceEntity {
        return dao.loadAgentInstance(agentInstanceId) ?: error("Agent instance not found: $agentInstanceId")
    }

    private suspend fun recordAgentEvent(
        eventType: String,
        action: String,
        vault: ProjectVaultEntity,
        agent: AgentInstanceEntity,
        nowMillis: Long,
        note: String,
        importance: Int
    ) {
        memoryRepository.recordSystemMemoryEvent(
            eventType = eventType,
            body = "Project agent $action: vault=${vault.displayName}; agent=${agent.displayName}; status=${agent.status}; template=${agent.templateAgentId}; note=${note.take(180)}",
            importance = importance,
            evidenceJson = JSONObject()
                .put("schema", "morimil.project_agent_lifecycle.v1")
                .put("event_type", eventType)
                .put("action", action)
                .put("recorded_at_millis", nowMillis)
                .put("policy", "decision_equals_memory")
                .put("vault", vault.toEvidenceJson())
                .put("agent_instance", agent.toEvidenceJson())
                .put("note", note)
                .toString()
        )
    }

    private suspend fun recordTaskEvent(
        eventType: String,
        action: String,
        vault: ProjectVaultEntity,
        agent: AgentInstanceEntity,
        task: DelegatedTaskEntity,
        nowMillis: Long,
        note: String,
        importance: Int
    ) {
        memoryRepository.recordSystemMemoryEvent(
            eventType = eventType,
            body = "Project task $action: vault=${vault.displayName}; agent=${agent.displayName}; task=${task.taskId}; risk=${task.riskLevel}; goal=${task.goal}",
            importance = importance,
            evidenceJson = JSONObject()
                .put("schema", "morimil.project_task_assignment.v1")
                .put("event_type", eventType)
                .put("action", action)
                .put("recorded_at_millis", nowMillis)
                .put("policy", "decision_equals_memory")
                .put("vault", vault.toEvidenceJson())
                .put("agent_instance", agent.toEvidenceJson())
                .put("delegated_task", task.toEvidenceJson())
                .put("note", note)
                .toString()
        )
    }

    private fun ProjectVaultEntity.toEvidenceJson(): JSONObject {
        return JSONObject()
            .put("vault_id", vaultId)
            .put("display_name", displayName)
            .put("project_type", projectType)
            .put("status", status)
            .put("health_status", healthStatus)
            .put("progress_percent", progressPercent)
    }

    private fun AgentInstanceEntity.toEvidenceJson(): JSONObject {
        return JSONObject()
            .put("agent_instance_id", agentInstanceId)
            .put("project_vault_id", projectVaultId)
            .put("template_agent_id", templateAgentId)
            .put("display_name", displayName)
            .put("briefing", briefing)
            .put("constraints_json", constraintsJson)
            .put("status", status)
            .put("quality_score", qualityScore)
            .put("error_count", errorCount)
            .put("current_task_id", currentTaskId ?: JSONObject.NULL)
            .put("created_at_millis", createdAtMillis)
            .put("updated_at_millis", updatedAtMillis)
    }

    private fun DelegatedTaskEntity.toEvidenceJson(): JSONObject {
        return JSONObject()
            .put("task_id", taskId)
            .put("assigned_agent_id", assignedAgentId)
            .put("goal", goal)
            .put("context_summary", contextSummary)
            .put("allowed_actions_json", allowedActionsJson)
            .put("allowed_transports_json", allowedTransportsJson)
            .put("approval_required", approvalRequired)
            .put("status", status)
            .put("risk_level", riskLevel)
    }

    private fun buildConstraintsJson(vault: ProjectVaultEntity): String {
        return JSONObject()
            .put("scope", "project_vault_only")
            .put("vault_id", vault.vaultId)
            .put("vault_name", vault.displayName)
            .put("no_autonomous_execution", true)
            .put("human_approval_required", true)
            .put("main_memory_owner", "morimil")
            .toString()
    }

    private fun buildAgentDisplayName(vaultName: String, templateAgentId: String): String {
        val role = templateAgentId.removeSuffix("_agent").replace('_', ' ')
        return "$vaultName $role worker"
    }

    private fun normalizeReviewStatus(status: String): String {
        return when (status) {
            STATUS_WORKING, STATUS_THINKING, STATUS_AWAITING_REVIEW -> status
            else -> STATUS_AWAITING_REVIEW
        }
    }

    companion object {
        const val STATUS_WORKING = "working"
        const val STATUS_THINKING = "thinking"
        const val STATUS_AWAITING_REVIEW = "awaiting_review"
        const val STATUS_QUARANTINED = "error_quarantined"
        const val STATUS_RETIRED = "retired"
        const val STATUS_PROMOTED = "promoted"

        private const val EVENT_AGENT_CREATED = "project.agent_created"
        private const val EVENT_AGENT_BRIEFED = "project.agent_briefed"
        private const val EVENT_TASK_ASSIGNED = "project.task_assigned"
        private const val EVENT_AGENT_RESULT_SUBMITTED = "project.agent_result_submitted"
        private const val EVENT_AGENT_EVALUATED = "project.agent_evaluated"
        private const val EVENT_AGENT_RETIRED = "project.agent_retired"
        private const val EVENT_AGENT_QUARANTINED = "project.agent_quarantined"
        private const val EVENT_AGENT_PROMOTED = "project.agent_promoted"

        fun buildAgentInstanceId(vaultId: String, templateAgentId: String, nowMillis: Long): String {
            val suffix = StableIdDigest.shortSha256Hex(
                namespace = "agent_instance",
                parts = listOf(vaultId, templateAgentId, nowMillis.toString())
            )
            return "agent_instance_${nowMillis}_$suffix"
        }

        fun buildProjectTaskId(createdAtMillis: Long, agentInstanceId: String, goal: String): String {
            val suffix = StableIdDigest.shortSha256Hex(
                namespace = "project_delegated_task",
                parts = listOf(createdAtMillis.toString(), agentInstanceId, goal)
            )
            return "ptask_${createdAtMillis}_$suffix"
        }
    }
}
