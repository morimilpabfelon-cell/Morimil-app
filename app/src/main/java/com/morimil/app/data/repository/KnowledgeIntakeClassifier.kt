package com.morimil.app.data.repository

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.Normalizer

object KnowledgeIntakeClassifier {
    fun hasExplicitCapsuleIntent(text: String): Boolean {
        val lower = normalize(text)
        return listOf(
            "crea una capsula",
            "crear una capsula",
            "guardar como capsula",
            "guarda esto como capsula",
            "guarda esto como conocimiento",
            "knowledge capsule",
            "type\": \"knowledge_capsule",
            "manual interno",
            "documentacion estable",
            "regla estable",
            "mis reglas personales",
            "arquitectura del sistema de memoria",
            "errores frecuentes que debe evitar"
        ).any { lower.contains(it) }
    }

    fun inferTitle(text: String): String {
        val jsonTitle = quotedJsonField(text, "title")
        if (!jsonTitle.isNullOrBlank()) return jsonTitle

        val quoted = Regex("\"([^\"]{6,90})\"").find(text)?.groupValues?.getOrNull(1)
        if (!quoted.isNullOrBlank() && !quoted.contains("knowledge_capsule")) return quoted

        val afterTitle = Regex("title\\s*[:=]\\s*([^\\n\\r,}]{4,100})", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('"')
        if (!afterTitle.isNullOrBlank()) return afterTitle

        val lower = normalize(text)
        return when {
            lower.contains("reglas personales") -> "Reglas personales de Morimil"
            lower.contains("android") -> "Documentacion tecnica Android"
            lower.contains("arquitectura") && lower.contains("memoria") -> "Arquitectura del sistema de memoria"
            lower.contains("manual interno") || lower.contains("politic") -> "Manual interno de politicas"
            lower.contains("errores frecuentes") || lower.contains("debe evitar") -> "Errores frecuentes que debe evitar"
            lower.contains("diseno") -> "Diseno de la app Morimil"
            else -> text.lineSequence().firstOrNull()?.take(80)?.ifBlank { null } ?: "Knowledge capsule"
        }
    }

    fun inferCategory(text: String): String {
        val lower = normalize(text)
        return when {
            lower.contains("reglas personales") || lower.contains("preferencia") -> "personal_rules"
            lower.contains("android") || lower.contains("documentacion") -> "technical_docs"
            lower.contains("arquitectura") && lower.contains("memoria") -> "memory_architecture"
            lower.contains("manual interno") || lower.contains("politic") -> "internal_policy"
            lower.contains("error") || lower.contains("debe evitar") -> "error_prevention"
            lower.contains("diseno") -> "app_design"
            else -> "general_knowledge"
        }
    }

    fun inferTags(text: String, category: String): List<String> {
        val lower = normalize(text)
        val tags = linkedSetOf("knowledge", category)
        if (lower.contains("android")) tags += "android"
        if (lower.contains("memoria")) tags += "memory"
        if (lower.contains("arquitectura")) tags += "architecture"
        if (lower.contains("politic")) tags += "policy"
        if (lower.contains("error")) tags += "errors"
        if (lower.contains("diseno")) tags += "design"
        if (lower.contains("morimil")) tags += "morimil"
        if (lower.contains("api")) tags += "api"
        if (lower.contains("local")) tags += "local_first"
        return tags.toList()
    }

    fun inferClaims(text: String): List<String> {
        val explicitClaims = Regex("\"claims\"\\s*:\\s*\\[(.*?)\\]", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
        if (!explicitClaims.isNullOrBlank()) {
            val quoted = Regex("\"([^\"]{8,260})\"").findAll(explicitClaims).map { it.groupValues[1].trim() }.toList()
            if (quoted.isNotEmpty()) return quoted.take(16)
        }

        return text
            .split('.', '\n', ';')
            .map { it.trim().trim('-', '*') }
            .filter { it.length in 24..260 }
            .filter {
                val lower = normalize(it)
                listOf("debe", "necesita", "funciona", "guarda", "recuerda", "evitar", "regla", "arquitectura", "memoria", "local").any { key ->
                    lower.contains(key)
                }
            }
            .distinct()
            .take(16)
    }

    fun buildClaimsJson(claims: List<String>): String {
        val root = JSONArray()
        claims.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(16)
            .forEach { claim ->
                root.put(
                    JSONObject()
                        .put("claim", claim)
                        .put("confidence", 90)
                        .put("source", "user_approved")
                )
            }
        return root.toString()
    }

    fun buildTagsJson(tags: List<String>): String {
        return JSONArray(tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()).toString()
    }

    fun buildEvidenceJson(
        source: String,
        sourceEventHash: String?,
        summary: String
    ): String {
        val sourceType = inferSourceType(summary, source, sourceEventHash)
        val sourceRef = inferSourceRef(summary, sourceEventHash)
        val userApproved = source.contains("approved", ignoreCase = true)
        return JSONObject()
            .put("schema", "morimil.knowledge_source.v1")
            .put("source", source)
            .put("source_type", sourceType)
            .put("source_ref", sourceRef)
            .put("source_event_hash", sourceEventHash ?: JSONObject.NULL)
            .put("document_hash", sha256(summary))
            .put("scope", inferScope(summary))
            .put("approval_state", if (userApproved) "user_approved" else "needs_review")
            .put("user_approved", userApproved)
            .put("requires_human_review", requiresHumanReview(summary))
            .put("provenance_confidence", provenanceConfidence(sourceType, userApproved))
            .put("conflict_policy", "newer_same-title_capsules_supersede; semantic_conflicts_require_review")
            .put("status", "active")
            .put("summary_excerpt", summary.take(240))
            .toString()
    }

    fun slug(value: String): String {
        return normalize(value).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "knowledge-capsule" }
    }

    private fun inferSourceType(text: String, source: String, sourceEventHash: String?): String {
        val explicit = quotedJsonField(text, "source_type") ?: labeledField(text, "source_type")
        if (!explicit.isNullOrBlank()) return explicit.trim().take(80)

        val lower = normalize("$source\n$text")
        return when {
            "github.com" in lower || "https://" in lower || "http://" in lower -> "url"
            "commit" in lower && ("github" in lower || "repo" in lower) -> "github_commit"
            "app/src/" in lower || "docs/" in lower || ".kt" in lower || ".md" in lower -> "repo_file"
            "manual interno" in lower || "regla estable" in lower -> "manual_rule"
            sourceEventHash != null -> "chat"
            else -> "manual_note"
        }
    }

    private fun inferSourceRef(text: String, sourceEventHash: String?): String {
        listOf("source_ref", "url", "file", "path", "commit").forEach { field ->
            val value = quotedJsonField(text, field) ?: labeledField(text, field)
            if (!value.isNullOrBlank()) return value.trim().take(260)
        }
        return sourceEventHash ?: "manual://current-chat"
    }

    private fun inferScope(text: String): String {
        val explicit = quotedJsonField(text, "scope") ?: labeledField(text, "scope")
        if (!explicit.isNullOrBlank()) return explicit.trim().take(80)

        val lower = normalize(text)
        return when {
            "vault_id" in lower || "boveda" in lower || "proyecto" in lower || "project" in lower -> "project"
            "morimil-app" in lower || "morimil app" in lower -> "morimil_app"
            else -> "global"
        }
    }

    private fun requiresHumanReview(text: String): Boolean {
        val lower = normalize(text)
        return listOf("seguridad", "politica", "arquitectura", "nunca", "siempre", "contradice", "reemplaza", "borra", "ejecuta").any {
            lower.contains(it)
        }
    }

    private fun provenanceConfidence(sourceType: String, userApproved: Boolean): Int {
        val base = when (sourceType) {
            "github_commit" -> 92
            "repo_file" -> 88
            "manual_rule" -> 86
            "chat" -> 82
            "url" -> 76
            else -> 70
        }
        return if (userApproved) maxOf(base, 90) else base
    }

    private fun quotedJsonField(text: String, field: String): String? {
        return Regex("\"$field\"\\s*:\\s*\"([^\"]{1,260})\"", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun labeledField(text: String, field: String): String? {
        return Regex("(?im)^\\s*$field\\s*[:=]\\s*(.{1,260})$")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('"')
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return "sha256:" + bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
    }
}
