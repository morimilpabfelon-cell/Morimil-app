package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "orchestrator_devices",
    indices = [
        Index(value = ["deviceType"], name = "index_orchestrator_devices_deviceType"),
        Index(value = ["authorizationStatus"], name = "index_orchestrator_devices_authorizationStatus"),
        Index(value = ["pairingState"], name = "index_orchestrator_devices_pairingState")
    ]
)
data class OrchestratorDeviceEntity(
    @PrimaryKey
    val deviceId: String,
    val displayName: String,
    val deviceType: String,
    val ownershipScope: String,
    val trustedOwner: String,
    val allowedTransportsJson: String,
    val authorizationStatus: String,
    val authorizationRequired: Boolean,
    val riskLevel: String,
    val pairingState: String,
    val lastSeenAtMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "agent_profiles",
    indices = [
        Index(value = ["role"], name = "index_agent_profiles_role"),
        Index(value = ["status"], name = "index_agent_profiles_status"),
        Index(value = ["riskLevel"], name = "index_agent_profiles_riskLevel")
    ]
)
data class AgentProfileEntity(
    @PrimaryKey
    val agentId: String,
    val displayName: String,
    val role: String,
    val description: String,
    val capabilitySetJson: String,
    val allowedToolsetJson: String,
    val allowedTransportsJson: String,
    val riskLevel: String,
    val requiresHumanApproval: Boolean,
    val status: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "delegated_tasks",
    indices = [
        Index(value = ["assignedAgentId"], name = "index_delegated_tasks_assignedAgentId"),
        Index(value = ["targetDeviceId"], name = "index_delegated_tasks_targetDeviceId"),
        Index(value = ["status"], name = "index_delegated_tasks_status"),
        Index(value = ["riskLevel"], name = "index_delegated_tasks_riskLevel"),
        Index(value = ["createdAtMillis"], name = "index_delegated_tasks_createdAtMillis")
    ]
)
data class DelegatedTaskEntity(
    @PrimaryKey
    val taskId: String,
    val createdBy: String,
    val assignedAgentId: String,
    val targetDeviceId: String?,
    val goal: String,
    val contextSummary: String,
    val inputRefsJson: String,
    val allowedActionsJson: String,
    val allowedTransportsJson: String,
    val approvalRequired: Boolean,
    val approvalId: String?,
    val status: String,
    val riskLevel: String,
    val resultSummary: String?,
    val errorSummary: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val completedAtMillis: Long?
)

@Entity(
    tableName = "task_approvals",
    indices = [
        Index(value = ["taskId"], name = "index_task_approvals_taskId"),
        Index(value = ["decision"], name = "index_task_approvals_decision")
    ]
)
data class TaskApprovalEntity(
    @PrimaryKey
    val approvalId: String,
    val taskId: String,
    val requestedBy: String,
    val decision: String,
    val decisionReason: String,
    val approvedScopeJson: String,
    val decidedAtMillis: Long
)

@Entity(
    tableName = "task_results",
    indices = [
        Index(value = ["taskId"], name = "index_task_results_taskId"),
        Index(value = ["status"], name = "index_task_results_status")
    ]
)
data class TaskResultEntity(
    @PrimaryKey
    val resultId: String,
    val taskId: String,
    val status: String,
    val exitCode: Int?,
    val summary: String,
    val artifactRefsJson: String,
    val diffCreated: Boolean,
    val recordedAtMillis: Long
)

@Entity(
    tableName = "asi_cycle_records",
    indices = [
        Index(value = ["status"], name = "index_asi_cycle_records_status"),
        Index(value = ["createdAtMillis"], name = "index_asi_cycle_records_createdAtMillis")
    ]
)
data class AsiCycleRecordEntity(
    @PrimaryKey
    val cycleId: String,
    val problem: String,
    val diagnosis: String,
    val improvementPlan: String,
    val delegatedTaskIdsJson: String,
    val verificationResult: String?,
    val memoryUpdateRefsJson: String,
    val futureRule: String?,
    val status: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)