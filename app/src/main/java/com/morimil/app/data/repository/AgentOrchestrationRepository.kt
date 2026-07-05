package com.morimil.app.data.repository

import com.morimil.app.core.identity.StableIdDigest
import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.data.local.AgentProfileEntity
import com.morimil.app.data.local.DelegatedTaskEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.OrchestratorDeviceEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

class AgentOrchestrationRepository(organDatabase: MemoryOrganDatabase) {
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
            status = AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL,
            riskLevel = plan.riskLevel,
            resultSummary = null,
            errorSummary = null,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            completedAtMillis = null
        )
        dao.insertDelegatedTask(task)
        return taskId
    }

    suspend fun approveDelegatedTask(taskId: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val approvalId = "approval_${StableIdDigest.shortSha256Hex("task_approval", listOf(taskId, nowMillis.toString()))}"
        return dao.approveDelegatedTask(
            taskId = taskId,
            approvalId = approvalId,
            status = AgentCapabilityPolicy.STATUS_APPROVED,
            updatedAtMillis = nowMillis
        ) > 0
    }

    suspend fun rejectDelegatedTask(taskId: String, reason: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        return dao.rejectDelegatedTask(
            taskId = taskId,
            status = AgentCapabilityPolicy.STATUS_REJECTED,
            errorSummary = reason.take(240),
            updatedAtMillis = nowMillis,
            completedAtMillis = nowMillis
        ) > 0
    }

    companion object {
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