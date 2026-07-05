package com.morimil.app.core.orchestration

import org.json.JSONArray

data class DelegationPlan(
    val assignedAgentId: String,
    val targetDeviceId: String?,
    val allowedActions: List<String>,
    val allowedTransports: List<String>,
    val approvalRequired: Boolean,
    val riskLevel: String,
    val contextSummary: String
)

object AgentCapabilityPolicy {
    const val AGENT_GITHUB = "github_agent"
    const val AGENT_ANDROID_BUILD = "android_build_agent"
    const val AGENT_FILE_AUDIT = "file_audit_agent"
    const val AGENT_RESEARCH = "research_agent"
    const val AGENT_DESIGN = "design_agent"
    const val AGENT_SECURITY = "security_agent"
    const val AGENT_PC_EXECUTOR = "pc_executor_agent"

    const val TRANSPORT_WIFI = "wifi_lan"
    const val TRANSPORT_BLUETOOTH = "bluetooth_nearby"
    const val TRANSPORT_USB = "usb_local"
    const val TRANSPORT_INTERNET = "internet_relay"
    const val TRANSPORT_MANUAL = "manual_copy"

    const val STATUS_ACTIVE = "active"
    const val STATUS_PENDING_AUTHORIZATION = "pending_authorization"
    const val STATUS_AWAITING_APPROVAL = "awaiting_approval"
    const val STATUS_APPROVED = "approved"
    const val STATUS_REJECTED = "rejected"

    fun planDelegation(goal: String, preferredAgentId: String? = null, targetDeviceId: String? = null): DelegationPlan {
        val cleanGoal = goal.trim().ifBlank { "Preparar trabajo delegado" }
        val agentId = preferredAgentId ?: inferAgent(cleanGoal)
        val actions = allowedActionsFor(agentId)
        val risk = riskFor(agentId, actions)
        return DelegationPlan(
            assignedAgentId = agentId,
            targetDeviceId = targetDeviceId,
            allowedActions = actions,
            allowedTransports = transportsFor(agentId),
            approvalRequired = true,
            riskLevel = risk,
            contextSummary = buildContextSummary(cleanGoal, agentId, risk)
        )
    }

    fun encodeJson(values: List<String>): String {
        return JSONArray(values.map { it.trim() }.filter { it.isNotBlank() }).toString()
    }

    private fun inferAgent(goal: String): String {
        val lower = goal.lowercase()
        return when {
            listOf("gradle", "android", "assemble", "test", "apk").any { it in lower } -> AGENT_ANDROID_BUILD
            listOf("github", "repo", "pull request", "commit", "branch").any { it in lower } -> AGENT_GITHUB
            listOf("seguridad", "security", "audit", "riesgo").any { it in lower } -> AGENT_SECURITY
            listOf("diseÃ±o", "design", "ui", "figma", "visual").any { it in lower } -> AGENT_DESIGN
            listOf("investiga", "research", "web", "mercado").any { it in lower } -> AGENT_RESEARCH
            listOf("archivo", "file", "leer", "diff").any { it in lower } -> AGENT_FILE_AUDIT
            else -> AGENT_PC_EXECUTOR
        }
    }

    private fun allowedActionsFor(agentId: String): List<String> {
        return when (agentId) {
            AGENT_GITHUB -> listOf("read_repository", "inspect_branch", "propose_diff", "prepare_pr_notes")
            AGENT_ANDROID_BUILD -> listOf("run_gradle_tests", "run_assemble_debug", "summarize_build_output")
            AGENT_FILE_AUDIT -> listOf("read_allowed_files", "summarize_files", "propose_patch")
            AGENT_RESEARCH -> listOf("research_web", "summarize_sources", "produce_brief")
            AGENT_DESIGN -> listOf("inspect_ui", "propose_visual_changes", "produce_design_notes")
            AGENT_SECURITY -> listOf("audit_permissions", "audit_risk", "recommend_policy")
            else -> listOf("prepare_command", "await_human_approval", "report_result")
        }
    }

    private fun transportsFor(agentId: String): List<String> {
        return when (agentId) {
            AGENT_RESEARCH -> listOf(TRANSPORT_INTERNET, TRANSPORT_MANUAL)
            AGENT_PC_EXECUTOR, AGENT_ANDROID_BUILD -> listOf(TRANSPORT_WIFI, TRANSPORT_USB, TRANSPORT_INTERNET, TRANSPORT_MANUAL)
            else -> listOf(TRANSPORT_WIFI, TRANSPORT_BLUETOOTH, TRANSPORT_USB, TRANSPORT_INTERNET, TRANSPORT_MANUAL)
        }
    }

    private fun riskFor(agentId: String, actions: List<String>): String {
        return when {
            agentId == AGENT_PC_EXECUTOR -> "high"
            actions.any { it.contains("run_") || it.contains("propose_patch") } -> "medium"
            else -> "low"
        }
    }

    private fun buildContextSummary(goal: String, agentId: String, risk: String): String {
        return "Morimil delega como orquestador. agente=$agentId riesgo=$risk objetivo=${goal.take(180)}"
    }
}