package com.morimil.app.core.immunity

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

enum class ImmuneDecision {
    ALLOW,
    REVIEW_REQUIRED,
    DENY,
    QUARANTINE
}

enum class ImmuneSurface {
    USER_MESSAGE,
    KNOWLEDGE_SOURCE,
    AGENT_RESULT,
    TOOL_REQUEST,
    SECRET_REQUEST
}

data class ImmunePolicyResult(
    val decision: ImmuneDecision,
    val riskLevel: String,
    val reasons: List<String>,
    val matchedSignals: List<String>,
    val sanitizedSummary: String
)

object MorimilImmunePolicy {
    private val revealSecretVerbs = listOf(
        "revela", "muestra", "imprime", "copia", "exfiltra", "envia", "exporta", "dime", "leer",
        "reveal", "show", "print", "copy", "exfiltrate", "send", "export", "read"
    )
    private val secretSignals = listOf(
        "api key", "apikey", "token", "credencial", "credential", "password", "contrasena",
        "secret", "secreto", "bearer", "sk-"
    )
    private val promptInjectionSignals = listOf(
        "ignora instrucciones", "ignora las reglas", "olvida las reglas", "ignore previous",
        "ignore all previous", "system prompt", "developer message", "jailbreak", "bypass",
        "override policy", "saltate", "no le digas al usuario", "do not tell the user",
        "oculta esto", "hidden instruction"
    )
    private val destructiveSignals = listOf(
        "borra la memoria", "borrar memoria", "delete memory", "wipe memory", "elimina historial",
        "borrar historial", "drop database", "delete database", "rm -rf", "format disk",
        "reset memory", "signature stripping"
    )
    private val autonomousDangerSignals = listOf(
        "sin aprobacion", "sin permiso", "without approval", "no pidas permiso", "hazlo oculto",
        "autonomamente", "execute without", "ejecuta sin", "commit y push", "mergea main",
        "merge to main", "push directo", "push direct", "usa credenciales"
    )
    private val forbiddenToolActions = listOf(
        "read_secret", "write_secret", "reveal_secret", "execute_shell", "run_shell", "delete_file",
        "delete_memory", "drop_database", "commit", "push", "merge", "deploy", "network_exfiltrate"
    )

    fun evaluateText(surface: ImmuneSurface, text: String): ImmunePolicyResult {
        val clean = text.trim()
        val normalized = normalize(clean)
        val reasons = mutableListOf<String>()
        val signals = mutableListOf<String>()
        var decision = ImmuneDecision.ALLOW
        var risk = "low"

        if (containsAny(normalized, promptInjectionSignals)) {
            reasons += "prompt_injection_attempt"
            signals += matches(normalized, promptInjectionSignals)
            decision = when (surface) {
                ImmuneSurface.KNOWLEDGE_SOURCE, ImmuneSurface.AGENT_RESULT -> ImmuneDecision.QUARANTINE
                else -> ImmuneDecision.DENY
            }
            risk = "critical"
        }

        if (containsAny(normalized, secretSignals) && containsAny(normalized, revealSecretVerbs)) {
            reasons += "secret_exfiltration_request"
            signals += matches(normalized, secretSignals)
            signals += matches(normalized, revealSecretVerbs)
            decision = ImmuneDecision.DENY
            risk = "critical"
        } else if (surface == ImmuneSurface.SECRET_REQUEST && containsAny(normalized, secretSignals)) {
            reasons += "credential_access_requires_explicit_approval"
            signals += matches(normalized, secretSignals)
            decision = maxDecision(decision, ImmuneDecision.REVIEW_REQUIRED)
            risk = maxRisk(risk, "high")
        }

        if (containsAny(normalized, destructiveSignals)) {
            reasons += "memory_or_system_destruction_request"
            signals += matches(normalized, destructiveSignals)
            decision = ImmuneDecision.DENY
            risk = "critical"
        }

        if (containsAny(normalized, autonomousDangerSignals)) {
            reasons += "autonomous_or_hidden_action_request"
            signals += matches(normalized, autonomousDangerSignals)
            decision = ImmuneDecision.DENY
            risk = "critical"
        }

        return ImmunePolicyResult(
            decision = decision,
            riskLevel = risk,
            reasons = reasons.distinct(),
            matchedSignals = signals.distinct(),
            sanitizedSummary = clean.replace(Regex("\\s+"), " ").take(220)
        )
    }

    fun evaluateToolRequest(
        goal: String,
        allowedActions: List<String>,
        approvalRequired: Boolean
    ): ImmunePolicyResult {
        val textResult = evaluateText(ImmuneSurface.TOOL_REQUEST, goal)
        if (textResult.decision == ImmuneDecision.DENY || textResult.decision == ImmuneDecision.QUARANTINE) {
            return textResult
        }

        val forbiddenActions = allowedActions.filter { action ->
            forbiddenToolActions.any { forbidden -> normalize(action).contains(forbidden) }
        }
        if (forbiddenActions.isNotEmpty()) {
            return ImmunePolicyResult(
                decision = ImmuneDecision.DENY,
                riskLevel = "critical",
                reasons = textResult.reasons + "forbidden_tool_action",
                matchedSignals = textResult.matchedSignals + forbiddenActions,
                sanitizedSummary = textResult.sanitizedSummary
            )
        }

        if (!approvalRequired) {
            return ImmunePolicyResult(
                decision = ImmuneDecision.DENY,
                riskLevel = "critical",
                reasons = textResult.reasons + "tool_request_without_human_approval",
                matchedSignals = textResult.matchedSignals,
                sanitizedSummary = textResult.sanitizedSummary
            )
        }

        if (allowedActions.isNotEmpty()) {
            return ImmunePolicyResult(
                decision = ImmuneDecision.REVIEW_REQUIRED,
                riskLevel = maxRisk(textResult.riskLevel, "medium"),
                reasons = textResult.reasons + "tool_request_requires_human_review",
                matchedSignals = textResult.matchedSignals,
                sanitizedSummary = textResult.sanitizedSummary
            )
        }

        return textResult
    }

    fun evaluateSecretRequest(reason: String, userApproved: Boolean): ImmunePolicyResult {
        val textResult = evaluateText(ImmuneSurface.SECRET_REQUEST, reason)
        if (textResult.decision == ImmuneDecision.DENY || textResult.decision == ImmuneDecision.QUARANTINE) {
            return textResult
        }
        if (!userApproved) {
            return ImmunePolicyResult(
                decision = ImmuneDecision.DENY,
                riskLevel = "critical",
                reasons = textResult.reasons + "credential_use_without_owner_approval",
                matchedSignals = textResult.matchedSignals,
                sanitizedSummary = textResult.sanitizedSummary
            )
        }
        return ImmunePolicyResult(
            decision = ImmuneDecision.REVIEW_REQUIRED,
            riskLevel = maxRisk(textResult.riskLevel, "high"),
            reasons = textResult.reasons + "credential_use_owner_approved_runtime_only",
            matchedSignals = textResult.matchedSignals,
            sanitizedSummary = textResult.sanitizedSummary
        )
    }

    fun evidenceJson(surface: ImmuneSurface, result: ImmunePolicyResult): String {
        return JSONObject()
            .put("schema", "morimil.immune_policy.v1")
            .put("surface", surface.name.lowercase())
            .put("decision", result.decision.name.lowercase())
            .put("risk_level", result.riskLevel)
            .put("reasons", JSONArray(result.reasons))
            .put("matched_signals", JSONArray(result.matchedSignals))
            .put("sanitized_summary", result.sanitizedSummary)
            .toString()
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
    }

    private fun containsAny(value: String, signals: List<String>): Boolean {
        return signals.any { value.contains(normalize(it)) }
    }

    private fun matches(value: String, signals: List<String>): List<String> {
        return signals.filter { value.contains(normalize(it)) }
    }

    private fun maxDecision(left: ImmuneDecision, right: ImmuneDecision): ImmuneDecision {
        return if (decisionRank(right) > decisionRank(left)) right else left
    }

    private fun decisionRank(decision: ImmuneDecision): Int {
        return when (decision) {
            ImmuneDecision.ALLOW -> 0
            ImmuneDecision.REVIEW_REQUIRED -> 1
            ImmuneDecision.QUARANTINE -> 2
            ImmuneDecision.DENY -> 3
        }
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
