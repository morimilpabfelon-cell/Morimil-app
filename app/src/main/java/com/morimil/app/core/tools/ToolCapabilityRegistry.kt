package com.morimil.app.core.tools

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class ToolAccessMode {
    READ_ONLY,
    PROPOSE_ONLY,
    LOCAL_WRITE,
    EXTERNAL_EFFECT,
    SECRET_ACCESS,
    IRREVERSIBLE
}

enum class ToolCapabilityDecision {
    ALLOW,
    APPROVAL_REQUIRED,
    DENY
}

data class ToolCapability(
    val actionId: String,
    val displayName: String,
    val accessMode: ToolAccessMode,
    val riskLevel: String,
    val defaultReadOnly: Boolean,
    val requiresHumanApproval: Boolean,
    val requiresCredential: Boolean,
    val requiresAuthorizedDevice: Boolean,
    val productionAffecting: Boolean,
    val irreversible: Boolean,
    val auditRequired: Boolean
)

data class ToolCapabilityResult(
    val actionId: String,
    val decision: ToolCapabilityDecision,
    val riskLevel: String,
    val reasons: List<String>,
    val requiredControls: List<String>
)

object ToolCapabilityRegistry {
    const val ACTION_MEMORY_READ = "memory_read"
    const val ACTION_READ_REPOSITORY = "read_repository"
    const val ACTION_INSPECT_BRANCH = "inspect_branch"
    const val ACTION_PREPARE_PR_NOTES = "prepare_pr_notes"
    const val ACTION_PROPOSE_DIFF = "propose_diff"
    const val ACTION_RUN_GRADLE_TESTS = "run_gradle_tests"
    const val ACTION_RUN_ASSEMBLE_DEBUG = "run_assemble_debug"
    const val ACTION_SUMMARIZE_BUILD_OUTPUT = "summarize_build_output"
    const val ACTION_READ_ALLOWED_FILES = "read_allowed_files"
    const val ACTION_SUMMARIZE_FILES = "summarize_files"
    const val ACTION_PROPOSE_PATCH = "propose_patch"
    const val ACTION_RESEARCH_WEB = "research_web"
    const val ACTION_SUMMARIZE_SOURCES = "summarize_sources"
    const val ACTION_PRODUCE_BRIEF = "produce_brief"
    const val ACTION_INSPECT_UI = "inspect_ui"
    const val ACTION_PROPOSE_VISUAL_CHANGES = "propose_visual_changes"
    const val ACTION_PRODUCE_DESIGN_NOTES = "produce_design_notes"
    const val ACTION_CANVAS_READ = "canvas_read"
    const val ACTION_CANVAS_PROPOSE = "canvas_propose"
    const val ACTION_CANVAS_APPLY = "canvas_apply"
    const val ACTION_CANVAS_EXPORT = "canvas_export"
    const val ACTION_AUDIT_PERMISSIONS = "audit_permissions"
    const val ACTION_AUDIT_RISK = "audit_risk"
    const val ACTION_RECOMMEND_POLICY = "recommend_policy"
    const val ACTION_PREPARE_COMMAND = "prepare_command"
    const val ACTION_AWAIT_HUMAN_APPROVAL = "await_human_approval"
    const val ACTION_REPORT_RESULT = "report_result"
    const val ACTION_SECRET_REASONING_RUNTIME_KEY = "secret_reasoning_runtime_key"
    const val ACTION_GITHUB_PUSH = "github_push"
    const val ACTION_GITHUB_COMMIT = "github_commit"
    const val ACTION_PRODUCTION_DEPLOY = "production_deploy"
    const val ACTION_DELETE_LOCAL_FILE = "delete_local_file"

    private val capabilities: Map<String, ToolCapability> = listOf(
        read(ACTION_MEMORY_READ, "Read local living memory"),
        read(ACTION_READ_REPOSITORY, "Read repository metadata"),
        read(ACTION_INSPECT_BRANCH, "Inspect branch state"),
        propose(ACTION_PREPARE_PR_NOTES, "Prepare PR notes"),
        propose(ACTION_PROPOSE_DIFF, "Propose diff"),
        localWrite(ACTION_RUN_GRADLE_TESTS, "Run Gradle tests", requiresAuthorizedDevice = true),
        localWrite(ACTION_RUN_ASSEMBLE_DEBUG, "Run assembleDebug", requiresAuthorizedDevice = true),
        read(ACTION_SUMMARIZE_BUILD_OUTPUT, "Summarize build output"),
        read(ACTION_READ_ALLOWED_FILES, "Read allowed files"),
        read(ACTION_SUMMARIZE_FILES, "Summarize files"),
        propose(ACTION_PROPOSE_PATCH, "Propose patch"),
        externalRead(ACTION_RESEARCH_WEB, "Research web"),
        read(ACTION_SUMMARIZE_SOURCES, "Summarize sources"),
        propose(ACTION_PRODUCE_BRIEF, "Produce research brief"),
        read(ACTION_INSPECT_UI, "Inspect UI"),
        propose(ACTION_PROPOSE_VISUAL_CHANGES, "Propose visual changes"),
        propose(ACTION_PRODUCE_DESIGN_NOTES, "Produce design notes"),
        read(ACTION_CANVAS_READ, "Read local canvas scene"),
        propose(ACTION_CANVAS_PROPOSE, "Propose canvas elements"),
        localWrite(ACTION_CANVAS_APPLY, "Apply local canvas changes", requiresAuthorizedDevice = true),
        localWrite(ACTION_CANVAS_EXPORT, "Export local canvas document", requiresAuthorizedDevice = true),
        read(ACTION_AUDIT_PERMISSIONS, "Audit permissions"),
        read(ACTION_AUDIT_RISK, "Audit risk"),
        propose(ACTION_RECOMMEND_POLICY, "Recommend policy"),
        propose(ACTION_PREPARE_COMMAND, "Prepare command"),
        propose(ACTION_AWAIT_HUMAN_APPROVAL, "Await human approval"),
        read(ACTION_REPORT_RESULT, "Report result"),
        secret(ACTION_SECRET_REASONING_RUNTIME_KEY, "Use reasoning runtime key"),
        blockedExternal(ACTION_GITHUB_PUSH, "Push to GitHub"),
        blockedExternal(ACTION_GITHUB_COMMIT, "Commit to GitHub"),
        blockedExternal(ACTION_PRODUCTION_DEPLOY, "Deploy to production"),
        blockedIrreversible(ACTION_DELETE_LOCAL_FILE, "Delete local file")
    ).associateBy { capability -> capability.actionId }

    private val agentActions: Map<String, List<String>> = mapOf(
        "github_agent" to listOf(ACTION_READ_REPOSITORY, ACTION_INSPECT_BRANCH, ACTION_PROPOSE_DIFF, ACTION_PREPARE_PR_NOTES),
        "android_build_agent" to listOf(ACTION_RUN_GRADLE_TESTS, ACTION_RUN_ASSEMBLE_DEBUG, ACTION_SUMMARIZE_BUILD_OUTPUT),
        "file_audit_agent" to listOf(ACTION_READ_ALLOWED_FILES, ACTION_SUMMARIZE_FILES, ACTION_PROPOSE_PATCH),
        "research_agent" to listOf(ACTION_RESEARCH_WEB, ACTION_SUMMARIZE_SOURCES, ACTION_PRODUCE_BRIEF),
        "design_agent" to listOf(
            ACTION_INSPECT_UI,
            ACTION_PROPOSE_VISUAL_CHANGES,
            ACTION_PRODUCE_DESIGN_NOTES,
            ACTION_CANVAS_READ,
            ACTION_CANVAS_PROPOSE
        ),
        "canvas_agent" to listOf(
            ACTION_CANVAS_READ,
            ACTION_CANVAS_PROPOSE,
            ACTION_CANVAS_APPLY,
            ACTION_CANVAS_EXPORT
        ),
        "security_agent" to listOf(ACTION_AUDIT_PERMISSIONS, ACTION_AUDIT_RISK, ACTION_RECOMMEND_POLICY),
        "pc_executor_agent" to listOf(ACTION_PREPARE_COMMAND, ACTION_AWAIT_HUMAN_APPROVAL, ACTION_REPORT_RESULT)
    )

    fun capabilityFor(actionId: String): ToolCapability? = capabilities[actionId]

    fun actionsForAgent(agentId: String): List<String> {
        return agentActions[agentId] ?: agentActions.getValue("pc_executor_agent")
    }

    fun capabilitiesForAgent(agentId: String): List<ToolCapability> {
        return actionsForAgent(agentId).mapNotNull { actionId -> capabilities[actionId] }
    }

    fun riskForActions(actionIds: List<String>): String {
        return actionIds.mapNotNull { actionId -> capabilities[actionId]?.riskLevel }
            .maxByOrNull { risk -> riskRank(risk) }
            ?: "medium"
    }

    fun validateActionIds(actionIds: List<String>): ToolCapabilityResult {
        val unknown = actionIds.filter { actionId -> actionId !in capabilities }
        if (unknown.isNotEmpty()) {
            return ToolCapabilityResult(
                actionId = unknown.first(),
                decision = ToolCapabilityDecision.DENY,
                riskLevel = "critical",
                reasons = listOf("unknown_tool_action"),
                requiredControls = listOf("register_tool_capability")
            )
        }
        return ToolCapabilityResult(
            actionId = "toolset",
            decision = ToolCapabilityDecision.ALLOW,
            riskLevel = riskForActions(actionIds),
            reasons = listOf("registered_tool_actions"),
            requiredControls = emptyList()
        )
    }

    fun evaluateAction(
        actionId: String,
        humanApproved: Boolean = false,
        authorizedDevice: Boolean = false,
        credentialApproved: Boolean = false
    ): ToolCapabilityResult {
        val capability = capabilities[actionId] ?: return ToolCapabilityResult(
            actionId = actionId,
            decision = ToolCapabilityDecision.DENY,
            riskLevel = "critical",
            reasons = listOf("unknown_tool_action"),
            requiredControls = listOf("register_tool_capability")
        )

        val missingControls = buildList {
            if (capability.requiresHumanApproval && !humanApproved) add("human_approval")
            if (capability.requiresAuthorizedDevice && !authorizedDevice) add("authorized_device")
            if (capability.requiresCredential && !credentialApproved) add("credential_approval")
        }
        if (capability.irreversible || capability.productionAffecting) {
            return ToolCapabilityResult(
                actionId = actionId,
                decision = ToolCapabilityDecision.DENY,
                riskLevel = "critical",
                reasons = listOf("irreversible_or_production_action_blocked"),
                requiredControls = listOf("manual_out_of_band_review")
            )
        }
        if (missingControls.isNotEmpty()) {
            return ToolCapabilityResult(
                actionId = actionId,
                decision = ToolCapabilityDecision.APPROVAL_REQUIRED,
                riskLevel = maxRisk(capability.riskLevel, "high"),
                reasons = listOf("tool_action_requires_controls"),
                requiredControls = missingControls
            )
        }
        return ToolCapabilityResult(
            actionId = actionId,
            decision = ToolCapabilityDecision.ALLOW,
            riskLevel = capability.riskLevel,
            reasons = listOf("tool_action_registered_and_controls_satisfied"),
            requiredControls = emptyList()
        )
    }

    fun evidenceJson(result: ToolCapabilityResult): String {
        return JSONObject()
            .put("schema", "morimil.tool_capability.v1")
            .put("action_id", result.actionId)
            .put("decision", result.decision.name.lowercase(Locale.ROOT))
            .put("risk_level", result.riskLevel)
            .put("reasons", JSONArray(result.reasons))
            .put("required_controls", JSONArray(result.requiredControls))
            .toString()
    }

    private fun read(actionId: String, displayName: String): ToolCapability {
        return capability(actionId, displayName, ToolAccessMode.READ_ONLY, "low")
    }

    private fun externalRead(actionId: String, displayName: String): ToolCapability {
        return capability(actionId, displayName, ToolAccessMode.READ_ONLY, "medium", auditRequired = true)
    }

    private fun propose(actionId: String, displayName: String): ToolCapability {
        return capability(actionId, displayName, ToolAccessMode.PROPOSE_ONLY, "medium", auditRequired = true)
    }

    private fun localWrite(actionId: String, displayName: String, requiresAuthorizedDevice: Boolean): ToolCapability {
        return capability(
            actionId = actionId,
            displayName = displayName,
            accessMode = ToolAccessMode.LOCAL_WRITE,
            riskLevel = "high",
            requiresHumanApproval = true,
            requiresAuthorizedDevice = requiresAuthorizedDevice,
            auditRequired = true
        )
    }

    private fun secret(actionId: String, displayName: String): ToolCapability {
        return capability(
            actionId = actionId,
            displayName = displayName,
            accessMode = ToolAccessMode.SECRET_ACCESS,
            riskLevel = "high",
            requiresHumanApproval = true,
            requiresCredential = true,
            auditRequired = true
        )
    }

    private fun blockedExternal(actionId: String, displayName: String): ToolCapability {
        return capability(
            actionId = actionId,
            displayName = displayName,
            accessMode = ToolAccessMode.EXTERNAL_EFFECT,
            riskLevel = "critical",
            requiresHumanApproval = true,
            productionAffecting = true,
            auditRequired = true
        )
    }

    private fun blockedIrreversible(actionId: String, displayName: String): ToolCapability {
        return capability(
            actionId = actionId,
            displayName = displayName,
            accessMode = ToolAccessMode.IRREVERSIBLE,
            riskLevel = "critical",
            requiresHumanApproval = true,
            irreversible = true,
            auditRequired = true
        )
    }

    private fun capability(
        actionId: String,
        displayName: String,
        accessMode: ToolAccessMode,
        riskLevel: String,
        requiresHumanApproval: Boolean = false,
        requiresCredential: Boolean = false,
        requiresAuthorizedDevice: Boolean = false,
        productionAffecting: Boolean = false,
        irreversible: Boolean = false,
        auditRequired: Boolean = false
    ): ToolCapability {
        return ToolCapability(
            actionId = actionId,
            displayName = displayName,
            accessMode = accessMode,
            riskLevel = riskLevel,
            defaultReadOnly = accessMode == ToolAccessMode.READ_ONLY,
            requiresHumanApproval = requiresHumanApproval,
            requiresCredential = requiresCredential,
            requiresAuthorizedDevice = requiresAuthorizedDevice,
            productionAffecting = productionAffecting,
            irreversible = irreversible,
            auditRequired = auditRequired
        )
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
