package com.morimil.app.data.repository

import com.morimil.app.core.memory.MemoryIntegrityCore
import com.morimil.app.data.local.AutobiographicalSnapshotEntity
import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class MemoryOrganRepository(
    database: MemoryOrganDatabase,
    private val memoryIntegrityCore: MemoryIntegrityCore
) {
    private val dao = database.memoryOrganDao()

    val selfSnapshot: Flow<AutobiographicalSnapshotEntity?> = dao.observeCurrentSelfSnapshot()
    val knowledgeCapsules: Flow<List<KnowledgeCapsuleEntity>> = dao.observeRecentKnowledgeCapsules()

    suspend fun auditKnowledgeCapsuleChain(): Boolean {
        return memoryIntegrityCore.verifyCapsuleChain(dao.loadKnowledgeCapsuleChain())
    }

    suspend fun updateSelfSnapshot(
        genesisCoreId: String,
        alias: String,
        selfSummary: String,
        stableTraits: String,
        activeGoals: String,
        importantConstraints: String,
        sourceEventHash: String?
    ) {
        dao.upsertSelfSnapshot(
            AutobiographicalSnapshotEntity(
                snapshotId = "current",
                genesisCoreId = genesisCoreId,
                alias = alias,
                selfSummary = selfSummary.trim(),
                stableTraits = stableTraits.trim(),
                activeGoals = activeGoals.trim(),
                importantConstraints = importantConstraints.trim(),
                sourceEventHash = sourceEventHash,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun captureKnowledgeCapsuleFromText(
        genesisCoreId: String,
        text: String,
        sourceEventHash: String? = null
    ): KnowledgeCapsuleEntity? {
        val clean = text.trim()
        if (!hasExplicitCapsuleIntent(clean)) return null

        val title = inferTitle(clean)
        val category = inferCategory(clean)
        val tags = inferTags(clean, category)
        val claims = inferClaims(clean)
        if (claims.isEmpty() && clean.length < 120) return null

        return appendKnowledgeCapsule(
            genesisCoreId = genesisCoreId,
            capsuleCategory = category,
            capsuleType = "knowledge_capsule",
            title = title,
            source = "user_approved_notes",
            privacyVisibility = "private_local",
            summary = clean.take(1600),
            claims = claims,
            tags = tags,
            confidence = 92,
            sourceEventHash = sourceEventHash
        )
    }

    suspend fun appendKnowledgeCapsule(
        genesisCoreId: String,
        capsuleCategory: String,
        capsuleType: String = "knowledge_capsule",
        title: String,
        source: String = "user_approved_notes",
        privacyVisibility: String = "private_local",
        summary: String,
        claims: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        confidence: Int,
        sourceEventHash: String?
    ): KnowledgeCapsuleEntity {
        val cleanTitle = title.trim().ifBlank { "untitled" }
        val now = System.currentTimeMillis()
        val existingChain = dao.loadKnowledgeCapsuleChain()
        require(memoryIntegrityCore.verifyCapsuleChain(existingChain)) {
            "Knowledge capsule chain integrity failed. Refusing to write a new capsule."
        }

        val versionsForTitle = dao.loadCapsulesByTitle(cleanTitle)
        val nextVersion = (versionsForTitle.maxOfOrNull { it.capsuleVersion } ?: 0) + 1
        val previousCapsuleHash = existingChain.lastOrNull()?.capsuleHash
        val cleanClaims = buildClaimsJson(claims)
        val cleanTags = JSONArray(tags.map { it.trim() }.filter { it.isNotBlank() }).toString()
        val cleanSummary = summary.trim()
        val cleanConfidence = confidence.coerceIn(1, 100)
        val capsuleId = "${slug(cleanTitle)}-v$nextVersion"
        val evidenceJson = JSONObject()
            .put("schema", "morimil.knowledge_capsule_evidence.v1")
            .put("source", source)
            .put("source_event_hash", sourceEventHash)
            .put("user_approved", source.contains("approved", ignoreCase = true))
            .put("status", "active")
            .put("summary_excerpt", cleanSummary.take(240))
            .toString()
        val capsuleHash = memoryIntegrityCore.hashCapsuleV2(
            genesisCoreId = genesisCoreId,
            capsuleId = capsuleId,
            capsuleVersion = nextVersion,
            capsuleCategory = capsuleCategory,
            capsuleType = capsuleType,
            status = "active",
            title = cleanTitle,
            source = source,
            privacyVisibility = privacyVisibility,
            summary = cleanSummary,
            claimsJson = cleanClaims,
            tags = cleanTags,
            evidenceJson = evidenceJson,
            confidence = cleanConfidence,
            sourceEventHash = sourceEventHash,
            previousCapsuleHash = previousCapsuleHash,
            createdAtMillis = now
        )

        if (versionsForTitle.any { it.status == "active" }) {
            dao.markActiveCapsulesSuperseded(cleanTitle, now)
        }
        val capsule = KnowledgeCapsuleEntity(
            capsuleId = capsuleId,
            genesisCoreId = genesisCoreId,
            capsuleVersion = nextVersion,
            capsuleCategory = capsuleCategory,
            capsuleType = capsuleType,
            status = "active",
            title = cleanTitle,
            source = source,
            privacyVisibility = privacyVisibility,
            summary = cleanSummary,
            claimsJson = cleanClaims,
            tags = cleanTags,
            evidenceJson = evidenceJson,
            confidence = cleanConfidence,
            sourceEventHash = sourceEventHash,
            previousCapsuleHash = previousCapsuleHash,
            capsuleHash = capsuleHash,
            hashAlgorithm = MemoryIntegrityCore.HASH_ALGORITHM_SHA256,
            canonicalization = MemoryIntegrityCore.CAPSULE_CANONICALIZATION_V2,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        dao.insertKnowledgeCapsule(capsule)
        return capsule
    }

    suspend fun buildKnowledgeCapsuleContext(limit: Int = 8): String {
        val capsules = dao.loadKnowledgeCapsules(limit)
        if (capsules.isEmpty()) {
            return "No knowledge capsules yet."
        }
        return capsules.joinToString("\n") { capsule ->
            "- [${capsule.capsuleCategory}/${capsule.status}/v${capsule.capsuleVersion}/${capsule.privacyVisibility}/c${capsule.confidence}/${capsule.capsuleHash.take(19)}] " +
                "${capsule.title}: ${capsule.summary.take(500)} claims=${capsule.claimsJson.take(420)} tags=${capsule.tags}"
        }
    }

    private fun hasExplicitCapsuleIntent(text: String): Boolean {
        val lower = text.lowercase()
        return listOf(
            "crea una capsula",
            "crea una capsula",
            "guardar como capsula",
            "guardar como capsula",
            "guarda esto como conocimiento",
            "knowledge capsule",
            "type\": \"knowledge_capsule",
            "manual interno",
            "documentacion estable",
            "documentacion estable",
            "regla estable",
            "mis reglas personales",
            "arquitectura del sistema de memoria",
            "errores frecuentes que debe evitar"
        ).any { lower.contains(it) }
    }

    private fun inferTitle(text: String): String {
        val jsonTitle = Regex("\"title\"\\s*:\\s*\"([^\"]{4,100})\"", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
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

        val lower = text.lowercase()
        return when {
            lower.contains("reglas personales") -> "Reglas personales de Morimil"
            lower.contains("android") -> "Documentacion tecnica Android"
            lower.contains("arquitectura") && lower.contains("memoria") -> "Arquitectura del sistema de memoria"
            lower.contains("manual interno") || lower.contains("politic") -> "Manual interno de politicas"
            lower.contains("errores frecuentes") || lower.contains("debe evitar") -> "Errores frecuentes que debe evitar"
            lower.contains("diseno") || lower.contains("diseno") -> "Diseno de la app Morimil"
            else -> text.lineSequence().firstOrNull()?.take(80)?.ifBlank { null } ?: "Knowledge capsule"
        }
    }

    private fun inferCategory(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("reglas personales") || lower.contains("preferencia") -> "personal_rules"
            lower.contains("android") || lower.contains("documentacion") || lower.contains("documentacion") -> "technical_docs"
            lower.contains("arquitectura") && lower.contains("memoria") -> "memory_architecture"
            lower.contains("manual interno") || lower.contains("politic") -> "internal_policy"
            lower.contains("error") || lower.contains("debe evitar") -> "error_prevention"
            lower.contains("diseno") || lower.contains("diseno") -> "app_design"
            else -> "general_knowledge"
        }
    }

    private fun inferTags(text: String, category: String): List<String> {
        val lower = text.lowercase()
        val tags = linkedSetOf("knowledge", category)
        if (lower.contains("android")) tags += "android"
        if (lower.contains("memoria")) tags += "memory"
        if (lower.contains("arquitectura")) tags += "architecture"
        if (lower.contains("politic")) tags += "policy"
        if (lower.contains("error")) tags += "errors"
        if (lower.contains("diseno") || lower.contains("diseno")) tags += "design"
        if (lower.contains("morimil")) tags += "morimil"
        if (lower.contains("api")) tags += "api"
        if (lower.contains("local")) tags += "local_first"
        return tags.toList()
    }

    private fun inferClaims(text: String): List<String> {
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
                val lower = it.lowercase()
                listOf("debe", "necesita", "funciona", "guarda", "recuerda", "evitar", "regla", "arquitectura", "memoria", "local").any { key ->
                    lower.contains(key)
                }
            }
            .distinct()
            .take(16)
    }

    private fun buildClaimsJson(claims: List<String>): String {
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

    private fun slug(value: String): String {
        return value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "knowledge-capsule" }
    }
}
