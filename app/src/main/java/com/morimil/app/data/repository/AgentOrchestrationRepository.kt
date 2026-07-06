package com.morimil.app.data.repository

import com.morimil.app.core.identity.StableIdDigest
import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.core.orchestration.DelegationPlan
import com.morimil.app.data.local.AgentProfileEntity
import com.morimil.app.data.local.DelegatedTaskEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.OrchestratorDeviceEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class AgentOrchestrationRepository(
    organDatabase: MemoryOrganDatabase,
    private val memoryRepository: MemoryRepository
) {
    private val dao = organDatabase.memoryOrganDao()

    val orchestratorDevices: Flow<List<OrchestratorDeviceEntity>> = dao.observeOrchestratorDevices()
    val agentProfiles: Flow<List<AgentProfileEntity>> = dao.observeAgentProfiles()
    val delegatedTasks: Flow<List<DelegatedTaskEntity>> = dao.observeDelegatedTasks()

    suspend fun seedDefaultOrchestrationIfNeeded(nowMillis: Long = System.currentTimeMillis()) {
        if (dao.countAgentProfiles() == 0) {
            dao.insertAgentProfiles(defaultAgents(nowMillis))
        }
        if (dao.countOrchestratorDevices() == 0) {
            dao.insertOrchestratorDevices(defaultDevices(nowMillis))
        }
    }

    suspend fun proposeDelegatedTask(
        goal: String,
        preferredAgentId: String? = null,
        targetDeviceId: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        seedDefaultOrchestrationIfNeeded(nowMillis)
        val plan = AgentCapabilityPolicy.planDelegation(goal, preferredAgentId, targetDeviceId)
        val immuneBlocked = AgentCapabilityPolicy.isImmuneBlocked(plan.immuneDecision)
        val taskId = buildTaskId(nowMillis, plan.assignedAgentId, goal)
        val task = DelegatedTaskEntity(
            taskId = taskId,
            createdBy = "morimil_orchestrator",
            assignedAgentId = plan.assignedAgentId,
            targetDeviceId = plan.targetDeviceId,
            goal = goal.trim().ifBlank { "Preparar trabajo delegado" },
            contextSummary = plan.contextSummary,
            inputRefsJson = "[]",
            allowedActionsJson = AgentCapabilityPolicy.encodeJson(plan.allowedActions),
            allowedTransportsJson = AgentCapabilityPolicy.encodeJson(plan.allowedTransports),
            approvalRequired = plan.approvalRequired,
            approvalId = null,
            status = if (immuneBlocked) AgentCapabilityPolicy.STATUS_REJECTED else AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL,
            riskLevel = plan.riskLevel,
            resultSummary = null,
            errorSummary = if (immuneBlocked) immuneErrorSummary(plan) else null,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            completedAtMillis = if (immuneBlocked) nowMillis else null
        )
        dao.insertDelegatedTask(task)
        if (immuneBlocked) {
            recordImmuneToolBlocked(task, plan, nowMillis)
        } else {
            recordDelegatedTaskDecision(
                eventType = EVENT_TASK_PROPOSED,
                action = "proposed",
                task = task,
                nowMillis = nowMillis,
                importance = 95
            )
        }
        return taskId
    }

    suspend fun approveDelegatedTask(taskId: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val existingTask = dao.loadDelegatedTask(taskId) ?: return false
        if (existingTask.errorSummary?.startsWith(IMMUNE_BLOCK_PREFIX) == true) {
            recordImmuneApprovalDenied(existingTask, nowMillis)
            return false
        }
        val approvalId = "approval_${StableIdDigest.shortSha256Hex("task_approval", listOf(taskId, nowMillis.toString()))}"
        val updated = dao.approveDelegatedTask(
            taskId = taskId,
            approvalId = approvalId,
            status = AgentCapabilityPolicy.STATUS_APPROVED,
            updatedAtMillis = nowMillis
        ) > 0
        if (updated) {
            dao.loadDelegatedTask(taskId)?.let { task ->
                recordDelegatedTaskDecision(
                    eventType = EVENT_TASK_APPROVED,
                    action = "approved",
                    task = task,
                    nowMillis = nowMillis,
                    importance = 100
                )
            }
        }
        return updated
    }

    suspend fun rejectDelegatedTask(taskId: String, reason: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val cleanReason = reason.take(240)
        val updated = dao.rejectDelegatedTask(
            taskId = taskId,
            status = AgentCapabilityPolicy.STATUS_REJECTED,
            errorSummary = cleanReason,
            updatedAtMillis = nowMillis,
            completedAtMillis = nowMillis
        ) > 0
        if (updated) {
            dao.loadDelegatedTask(taskId)?.let { task ->
                recordDelegatedTaskDecision(
                    eventType = EVENT_TASK_REJECTED,
                    action = "rejected",
                    task = task,
                    nowMillis = nowMillis,
                    reason = cleanReason,
                    importance = 95
                )
            }
        }
        return updated
    }

    private suspend fun recordDelegatedTaskDecision(
        eventType: String,
        action: String,
        task: DelegatedTaskEntity,
        nowMillis: Long,
        reason: String? = null,
        importance: Int
    ) {
        memoryRepository.recordSystemMemoryEvent(
            eventType = eventType,
            body = buildDecisionBody(action, task, reason),
            importance = importance,
            evidenceJson = buildDecisionEvidenceJson(eventType, action, task, nowMillis, reason)
        )
    }

    private suspend fun recordImmuneToolBlocked(task: DelegatedTaskEntity, plan: DelegationPlan, nowMillis: Long) {
        memoryRepository.recordSystemMemoryEvent(
            eventType = EVENT_IMMUNE_TOOL_BLOCKED,
            body = "Sistema inmunologico bloqueo delegacion: task_id=${task.taskId}; " +
                "decision=${plan.immuneDecision}; risk=${task.riskLevel}; reasons=${plan.immuneReasons.joinToString(",").take(180)}; goal=${task.goal.take(180)}",
            importance = 100,
            evidenceJson = buildImmuneEvidenceJson(task, plan, nowMillis)
        )
    }

    private suspend fun recordImmuneApprovalDenied(task: DelegatedTaskEntity, nowMillis: Long) {
        memoryRepository.recordSystemMemoryEvent(
            eventType = EVENT_IMMUNE_APPROVAL_DENIED,
            body = "Sistema inmunologico rechazo aprobacion de tarea bloqueada: task_id=${task.taskId}; risk=${task.riskLevel}; goal=${task.goal.take(180)}",
            importance = 100,
            evidenceJson = JSONObject()
                .put("schema", "morimil.immune_policy_decision.v1")
                .put("event_type", EVENT_IMMUNE_APPROVAL_DENIED)
                .put("recorded_at_millis", nowMillis)
                .put("policy", "blocked_tasks_cannot_be_approved")
                .put("delegated_task", task.toEvidenceJson())
                .toString()
        )
    }

    private fun buildDecisionBody(action: String, task: DelegatedTaskEntity, reason: String?): String {
        val reasonText = reason?.let { "; reason=$it" }.orEmpty()
        return "Decision de orquestacion: task_${action}; " +
            "task_id=${task.taskId}; agent=${task.assignedAgentId}; " +
            "target_device=${task.targetDeviceId ?: "none"}; risk=${task.riskLevel}; " +
            "approval_required=${task.approvalRequired}; status=${task.status}; " +
            "goal=${task.goal}$reasonText"
    }

    private fun buildDecisionEvidenceJson(
        eventType: String,
        action: String,
        task: DelegatedTaskEntity,
        nowMillis: Long,
        reason: String?
    ): String {
        return JSONObject()
            .put("schema", "morimil.orchestration_decision.v1")
            .put("event_type", eventType)
            .put("action", action)
            .put("recorded_at_millis", nowMillis)
            .put("policy", "decision_equals_memory")
            .put("reason", nullable(reason))
            .put("delegated_task", task.toEvidenceJson())
            .toString()
    }

    private fun buildImmuneEvidenceJson(task: DelegatedTaskEntity, plan: DelegationPlan, nowMillis: Long): String {
        return JSONObject()
            .put("schema", "morimil.immune_policy_decision.v1")
            .put("event_type", EVENT_IMMUNE_TOOL_BLOCKED)
            .put("recorded_at_millis", nowMillis)
            .put("policy", "immune_gate_before_tool_or_agent_execution")
            .put("decision", plan.immuneDecision)
            .put("reasons", JSONArray(plan.immuneReasons))
            .put("matched_signals", JSONArray(plan.immuneMatchedSignals))
            .put("delegated_task", task.toEvidenceJson())
            .toString()
    }

    private fun immuneErrorSummary(plan: DelegationPlan): String {
        val reasons = plan.immuneReasons.joinToString(separator = ",").ifBlank { plan.immuneDecision }
        return "$IMMUNE_BLOCK_PREFIX:$reasons".take(240)
    }

    private fun DelegatedTaskEntity.toEvidenceJson(): JSONObject {
        return JSONObject()
            .put("task_id", taskId)
            .put("created_by", createdBy)
            .put("assigned_agent_id", assignedAgentId)
            .put("target_device_id", nullable(targetDeviceId))
            .put("goal", goal)
            .put("context_summary", contextSummary)
            .put("input_refs_json", inputRefsJson)
            .put("allowed_actions_json", allowedActionsJson)
            .put("allowed_transports_json", allowedTransportsJson)
            .put("approval_required", approvalRequired)
            .put("approval_id", nullable(approvalId))
            .put("status", status)
            .put("risk_level", riskLevel)
            .put("result_summary", nullable(resultSummary))
            .put("error_summary", nullable(errorSummary))
            .put("created_at_millis", createdAtMillis)
            .put("updated_at_millis", updatedAtMillis)
            .put("completed_at_millis", nullable(completedAtMillis))
    }

    private fun nullable(value: String?): Any = value ?: JSONObject.NULL

    private fun nullable(value: Long?): Any = value ?: JSONObject.NULL

    companion object {
        private const val EVENT_TASK_PROPOSED = "orchestration.task_proposed"
        private const val EVENT_TASK_APPROVED = "orchestration.task_approved"
        private const val EVENT_TASK_REJECTED = "orchestration.task_rejected"
        private const val EVENT_IMMUNE_TOOL_BLOCKED = "immune.tool_blocked"
        private const val EVENT_IMMUNE_APPROVAL_DENIED = "immune.approval_denied"
        private const val IMMUNE_BLOCK_PREFIX = "immune_policy_blocked"

        fun buildTaskId(createdAtMillis: Long, agentId: String, goal: String): String {
            val suffix = StableIdDigest.shortSha256Hex(
                namespace = "delegated_task",
                parts = listOf(createdAtMillis.toString(), agentId, goal)
            )
            return "dtask_${createdAtMillis}_$suffix"
        }

        private fun defaultAgents(nowMillis: Long): List<AgentProfileEntity> {
            return listOf(
                agent(AgentCapabilityPolicy.AGENT_GITHUB, "GitHub Agent", "github", "Lee repositorios, revisa ramas y propone diffs.", listOf("read_repository", "inspect_branch", "propose_diff"), "medium", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_ANDROID_BUILD, "Android Build Agent", "android_build", "Corre tests/builds aprobados y resume fallos.", listOf("run_gradle_tests", "run_assemble_debug"), "medium", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_FILE_AUDIT, "File Audit Agent", "file_audit", "Lee archivos permitidos y propone parches.", listOf("read_allowed_files", "propose_patch"), "medium", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_RESEARCH, "Research Agent", "research", "Investiga fuentes externas y produce informes.", listOf("research_web", "summarize_sources"), "low", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_DESIGN, "Design Agent", "design", "Propone mejoras visuales y revisa UI.", listOf("inspect_ui", "produce_design_notes"), "low", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_SECURITY, "Security Agent", "security", "Audita permisos, riesgos y politica.", listOf("audit_permissions", "audit_risk"), "low", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_PC_EXECUTOR, "PC Executor Agent", "pc_executor", "Prepara ejecucion en equipos autorizados; no ejecuta sin aprobacion.", listOf("prepare_command", "await_human_approval", "report_result"), "high", nowMillis)
            )
        }

        private fun agent(
            agentId: String,
            displayName: String,
            role: String,
            description: String,
            capabilities: List<String>,
            riskLevel: String,
            nowMillis: Long
        ): AgentProfileEntity {
            return AgentProfileEntity(
                agentId = agentId,
                displayName = displayName,
                role = role,
                description = description,
                capabilitySetJson = JSONArray(capabilities).toString(),
                allowedToolsetJson = JSONArray(capabilities).toString(),
                allowedTransportsJson = AgentCapabilityPolicy.encodeJson(
                    listOf(
                        AgentCapabilityPolicy.TRANSPORT_WIFI,
                        AgentCapabilityPolicy.TRANSPORT_BLUETOOTH,
                        AgentCapabilityPolicy.TRANSPORT_USB,
                        AgentCapabilityPolicy.TRANSPORT_INTERNET,
                        AgentCapabilityPolicy.TRANSPORT_MANUAL
                    )
                ),
                riskLevel = riskLevel,
                requiresHumanApproval = true,
                status = AgentCapabilityPolicy.STATUS_ACTIVE,
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis
            )
        }

        private fun defaultDevices(nowMillis: Long): List<OrchestratorDeviceEntity> {
            return listOf(
                device("android_body", "Telefono Morimil", "android_phone", listOf(AgentCapabilityPolicy.TRANSPORT_WIFI, AgentCapabilityPolicy.TRANSPORT_BLUETOOTH, AgentCapabilityPolicy.TRANSPORT_MANUAL), "authorized", "paired_local", "low", nowMillis),
                device("personal_pc", "PC principal", "windows_pc", listOf(AgentCapabilityPolicy.TRANSPORT_WIFI, AgentCapabilityPolicy.TRANSPORT_USB, AgentCapabilityPolicy.TRANSPORT_INTERNET), "pending_authorization", "not_paired", "high", nowMillis),
                device("personal_laptop", "Laptop personal", "laptop", listOf(AgentCapabilityPolicy.TRANSPORT_WIFI, AgentCapabilityPolicy.TRANSPORT_BLUETOOTH, AgentCapabilityPolicy.TRANSPORT_INTERNET), "pending_authorization", "not_paired", "medium", nowMillis),
                device("personal_tablet", "Tablet personal", "tablet", listOf(AgentCapabilityPolicy.TRANSPORT_WIFI, AgentCapabilityPolicy.TRANSPORT_BLUETOOTH, AgentCapabilityPolicy.TRANSPORT_MANUAL), "pending_authorization", "not_paired", "medium", nowMillis)
            )
        }

        private fun device(
            deviceId: String,
            displayName: String,
            deviceType: String,
            transports: List<String>,
            authorizationStatus: String,
            pairingState: String,
            riskLevel: String,
            nowMillis: Long
        ): OrchestratorDeviceEntity {
            return OrchestratorDeviceEntity(
                deviceId = deviceId,
                displayName = displayName,
                deviceType = deviceType,
                ownershipScope = "user_owned",
                trustedOwner = "founder",
                allowedTransportsJson = AgentCapabilityPolicy.encodeJson(transports),
                authorizationStatus = authorizationStatus,
                authorizationRequired = authorizationStatus != "authorized",
                riskLevel = riskLevel,
                pairingState = pairingState,
                lastSeenAtMillis = if (authorizationStatus == "authorized") nowMillis else null,
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis
            )
        }
    }
}
