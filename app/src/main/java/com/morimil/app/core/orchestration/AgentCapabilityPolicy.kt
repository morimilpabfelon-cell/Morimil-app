package com.morimil.app.core.orchestration

import com.morimil.app.core.immunity.ImmuneDecision
import com.morimil.app.core.immunity.MorimilImmunePolicy
import com.morimil.app.core.tools.ToolCapabilityDecision
import com.morimil.app.core.tools.ToolCapabilityRegistry
import org.json.JSONArray
import java.util.Locale

data class DelegationPlan(
    val assignedAgentId: String,
    val targetDeviceId: String?,
    val allowedActions: List<String>,
    val allowedTransports: List<String>,
    val approvalRequired: Boolean,
    val riskLevel: String,
    val contextSummary: String,
    val immuneDecision: String,
    val immuneReasons: List<String>,
    val immuneMatchedSignals: List<String>
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
        val inferredAgentId = preferredAgentId ?: inferAgent(cleanGoal)
        val baseActions = allowedActionsFor(inferredAgentId)
        val toolsetResult = ToolCapabilityRegistry.validateActionIds(baseActions)
        val baseTransports = transportsFor(inferredAgentId)
        val immuneResult = MorimilImmunePolicy.evaluateToolRequest(
            goal = cleanGoal,
            allowedActions = baseActions,
            approvalRequired = true
        )
        val immuneBlocked = immuneResult.decision == ImmuneDecision.DENY || immuneResult.decision == ImmuneDecision.QUARANTINE
        val registryBlocked = toolsetResult.decision == ToolCapabilityDecision.DENY
        val blocked = immuneBlocked || registryBlocked
        val agentId = if (blocked) AGENT_SECURITY else inferredAgentId
        val actions = if (blocked) emptyList() else baseActions
        val transports = if (blocked) emptyList() else baseTransports
        val risk = maxRisk(maxRisk(riskFor(inferredAgentId, baseActions), immuneResult.riskLevel), toolsetResult.riskLevel)
        return DelegationPlan(
            assignedAgentId = agentId,
            targetDeviceId = targetDeviceId,
            allowedActions = actions,
            allowedTransports = transports,
            approvalRequired = true,
            riskLevel = risk,
            contextSummary = buildContextSummary(
                cleanGoal,
                agentId,
                risk,
                immuneResult.decision,
                immuneResult.reasons,
                toolsetResult.decision,
                toolsetResult.reasons
            ),
            immuneDecision = immuneResult.decision.name.lowercase(Locale.ROOT),
            immuneReasons = immuneResult.reasons,
            immuneMatchedSignals = immuneResult.matchedSignals
        )
    }

    fun encodeJson(values: List<String>): String {
        return JSONArray(values.map { it.trim() }.filter { it.isNotBlank() }).toString()
    }

    fun isImmuneBlocked(immuneDecision: String): Boolean {
        return immuneDecision == ImmuneDecision.DENY.name.lowercase(Locale.ROOT) ||
            immuneDecision == ImmuneDecision.QUARANTINE.name.lowercase(Locale.ROOT)
    }

    private fun inferAgent(goal: String): String {
        val lower = goal.lowercase()
        return when {
            listOf("gradle", "android", "assemble", "test", "apk").any { it in lower } -> AGENT_ANDROID_BUILD
            listOf("github", "repo", "pull request", "commit", "branch").any { it in lower } -> AGENT_GITHUB
            listOf("seguridad", "security", "audit", "riesgo").any { it in lower } -> AGENT_SECURITY
            listOf("diseño", "diseno", "design", "ui", "figma", "visual").any { it in lower } -> AGENT_DESIGN
            listOf("investiga", "research", "web", "mercado").any { it in lower } -> AGENT_RESEARCH
            listOf("archivo", "file", "leer", "diff").any { it in lower } -> AGENT_FILE_AUDIT
            else -> AGENT_PC_EXECUTOR
        }
    }

    private fun allowedActionsFor(agentId: String): List<String> {
        return ToolCapabilityRegistry.actionsForAgent(agentId)
    }

    private fun transportsFor(agentId: String): List<String> {
        return when (agentId) {
            AGENT_RESEARCH -> listOf(TRANSPORT_INTERNET, TRANSPORT_MANUAL)
            AGENT_PC_EXECUTOR, AGENT_ANDROID_BUILD -> listOf(TRANSPORT_WIFI, TRANSPORT_USB, TRANSPORT_INTERNET, TRANSPORT_MANUAL)
            else -> listOf(TRANSPORT_WIFI, TRANSPORT_BLUETOOTH, TRANSPORT_USB, TRANSPORT_INTERNET, TRANSPORT_MANUAL)
        }
    }

    private fun riskFor(agentId: String, actions: List<String>): String {
        val registryRisk = ToolCapabilityRegistry.riskForActions(actions)
        return if (agentId == AGENT_PC_EXECUTOR) maxRisk(registryRisk, "high") else registryRisk
    }

    private fun buildContextSummary(
        goal: String,
        agentId: String,
        risk: String,
        immuneDecision: ImmuneDecision,
        immuneReasons: List<String>,
        toolDecision: ToolCapabilityDecision,
        toolReasons: List<String>
    ): String {
        val reasons = immuneReasons.joinToString(separator = ",").take(120).ifBlank { "none" }
        val toolRegistryReasons = toolReasons.joinToString(separator = ",").take(120).ifBlank { "none" }
        return "Morimil delega como orquestador. agente=$agentId riesgo=$risk " +
            "immune_decision=${immuneDecision.name.lowercase(Locale.ROOT)} immune_reasons=$reasons " +
            "tool_registry_decision=${toolDecision.name.lowercase(Locale.ROOT)} tool_registry_reasons=$toolRegistryReasons " +
            "objetivo=${goal.take(180)}"
    }

    private fun maxRisk(left: String, right: String): String {
        return if (riskRank(right) > riskRank(left)) right else left
    }

    private fun riskRank(risk: String): Int {
        return when (risk.lowercase(Locale.ROOT)) {
            "low" -> 0
            "medium" -> 1
            "high" -> 2
            "critical" -> 3
            else -> 1
        }
    }
}
