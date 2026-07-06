package com.morimil.app.core.constitution

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

enum class CoreConstitutionDecision {
    ALLOW,
    REVIEW_REQUIRED,
    DENY
}

enum class CoreConstitutionScope {
    READ_CONTEXT,
    THINK,
    PROPOSE_CHANGE,
    APPEND_MEMORY,
    AMEND_DOCTRINE,
    AMEND_POLICY,
    MUTATE_GENESIS_CORE,
    EXTERNAL_EFFECT
}

data class CoreConstitutionResult(
    val decision: CoreConstitutionDecision,
    val riskLevel: String,
    val reasons: List<String>,
    val requiredControls: List<String>
)

object CoreConstitutionGuard {
    fun evaluateIntent(
        scope: CoreConstitutionScope,
        humanApproved: Boolean,
        throughReviewedMigration: Boolean,
        chainVerified: Boolean
    ): CoreConstitutionResult {
        return when (scope) {
            CoreConstitutionScope.READ_CONTEXT,
            CoreConstitutionScope.THINK,
            CoreConstitutionScope.PROPOSE_CHANGE -> allow("read_think_propose_is_safe")

            CoreConstitutionScope.APPEND_MEMORY -> {
                if (chainVerified) allow("append_only_memory_with_verified_chain") else deny(
                    reason = "memory_chain_not_verified",
                    controls = listOf("verify_chain", "quarantine_untrusted_tail")
                )
            }

            CoreConstitutionScope.AMEND_DOCTRINE,
            CoreConstitutionScope.AMEND_POLICY -> evaluateCoreAmendment(
                humanApproved = humanApproved,
                throughReviewedMigration = throughReviewedMigration,
                chainVerified = chainVerified
            )

            CoreConstitutionScope.MUTATE_GENESIS_CORE -> deny(
                reason = "genesis_core_is_immutable",
                controls = listOf("create_reviewed_amendment", "append_signed_migration_record")
            )

            CoreConstitutionScope.EXTERNAL_EFFECT -> {
                if (humanApproved) review(
                    reason = "external_effect_requires_human_approval",
                    controls = listOf("approved_scope", "signed_audit_event")
                ) else deny(
                    reason = "external_effect_without_human_approval",
                    controls = listOf("request_owner_approval", "limit_scope")
                )
            }
        }
    }

    fun evaluateMigrationPlan(
        migrationType: String,
        affectedArtifacts: List<String>,
        chainVerified: Boolean,
        backupRequired: Boolean,
        approvalRequired: Boolean,
        rollbackAvailable: Boolean,
        approvedByUser: Boolean
    ): CoreConstitutionResult {
        val normalizedType = normalize(migrationType)
        val normalizedArtifacts = affectedArtifacts.map { normalize(it) }
        val scope = inferScope(normalizedType, normalizedArtifacts)
        if (scope == null) {
            return allow("non_core_migration")
        }
        if (scope == CoreConstitutionScope.MUTATE_GENESIS_CORE) {
            return evaluateIntent(
                scope = CoreConstitutionScope.MUTATE_GENESIS_CORE,
                humanApproved = approvedByUser,
                throughReviewedMigration = true,
                chainVerified = chainVerified
            )
        }
        val missingControls = buildList {
            if (!chainVerified) add("verified_memory_chain")
            if (!approvalRequired) add("human_approval_required")
            if (!backupRequired) add("backup_required")
            if (!rollbackAvailable) add("rollback_available")
        }
        if (missingControls.isNotEmpty()) {
            return deny(
                reason = "core_migration_missing_controls",
                controls = missingControls
            )
        }
        return if (approvedByUser) {
            allow("approved_core_amendment_migration")
        } else {
            review(
                reason = "core_amendment_waiting_for_human_review",
                controls = listOf("human_approval", "signed_migration_event")
            )
        }
    }

    fun evidenceJson(result: CoreConstitutionResult, migrationType: String? = null): String {
        return JSONObject()
            .put("schema", "morimil.core_constitution_guard.v1")
            .put("decision", result.decision.name.lowercase(Locale.ROOT))
            .put("risk_level", result.riskLevel)
            .put("reasons", JSONArray(result.reasons))
            .put("required_controls", JSONArray(result.requiredControls))
            .put("migration_type", migrationType ?: JSONObject.NULL)
            .toString()
    }

    private fun evaluateCoreAmendment(
        humanApproved: Boolean,
        throughReviewedMigration: Boolean,
        chainVerified: Boolean
    ): CoreConstitutionResult {
        val missingControls = buildList {
            if (!throughReviewedMigration) add("reviewed_migration")
            if (!chainVerified) add("verified_memory_chain")
            if (!humanApproved) add("human_approval")
        }
        return if (missingControls.isEmpty()) {
            allow("core_amendment_controls_satisfied")
        } else {
            review(
                reason = "core_amendment_requires_controls",
                controls = missingControls
            )
        }
    }

    private fun inferScope(migrationType: String, affectedArtifacts: List<String>): CoreConstitutionScope? {
        val combined = (listOf(migrationType) + affectedArtifacts).joinToString(" ")
        return when {
            listOf("genesis_core", "genesis core", "birth_block").any { it in combined } -> CoreConstitutionScope.MUTATE_GENESIS_CORE
            listOf("doctrine", "doctrina", "core_doctrine").any { it in combined } -> CoreConstitutionScope.AMEND_DOCTRINE
            listOf("policy", "politica", "constitution", "constitucion", "core_policy").any { it in combined } -> CoreConstitutionScope.AMEND_POLICY
            else -> null
        }
    }

    private fun allow(reason: String): CoreConstitutionResult {
        return CoreConstitutionResult(
            decision = CoreConstitutionDecision.ALLOW,
            riskLevel = "low",
            reasons = listOf(reason),
            requiredControls = emptyList()
        )
    }

    private fun review(reason: String, controls: List<String>): CoreConstitutionResult {
        return CoreConstitutionResult(
            decision = CoreConstitutionDecision.REVIEW_REQUIRED,
            riskLevel = "high",
            reasons = listOf(reason),
            requiredControls = controls
        )
    }

    private fun deny(reason: String, controls: List<String>): CoreConstitutionResult {
        return CoreConstitutionResult(
            decision = CoreConstitutionDecision.DENY,
            riskLevel = "critical",
            reasons = listOf(reason),
            requiredControls = controls
        )
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
    }
}
