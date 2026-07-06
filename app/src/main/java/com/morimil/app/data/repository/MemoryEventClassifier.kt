package com.morimil.app.data.repository

import java.text.Normalizer

object MemoryEventClassifier {
    fun scoreImportance(body: String): Int {
        return classify(
            eventType = "conversation.user_message",
            actor = "user",
            body = body
        ).importance
    }

    fun classify(eventType: String, actor: String, body: String): MemoryClassification {
        val lower = normalize(body)
        val compact = lower.trim().replace(Regex("\\s+"), " ")
        val tokenCount = compact.split(" ").filter { token -> token.isNotBlank() }.size
        val isUser = actor == "user"
        val isSystem = actor == "system"

        val isExplicitMemoryCommand = listOf(
            "recuerda", "decision", "decid", "regla", "prefer", "corrige",
            "correccion", "aprende", "importante", "memoria fuerte", "aprob"
        ).any { token -> lower.contains(token) }

        val isTinyAck = compact in setOf(
            "ok", "okay", "dale", "si", "ya", "listo", "gracias", "hola",
            "hello", "bien", "correcto", "perfecto", "jaja", "aja"
        )
        val isLowSignalChat = !isSystem && !eventType.startsWith("memory_review") && (
            isTinyAck || (tokenCount <= 3 && !isExplicitMemoryCommand)
        )

        val tags = linkedSetOf<String>()
        if (lower.contains("genesis")) tags += "genesis"
        if (lower.contains("memoria") || lower.contains("recuerd")) tags += "memory"
        if (lower.contains("api") || lower.contains("motor") || lower.contains("razon")) tags += "reasoning"
        if (lower.contains("app") || lower.contains("celular") || lower.contains("local")) tags += "local_app"
        if (listOf("proyecto", "repo", "github", "empresa", "startup", "compania", "compañia", "boveda", "bóveda", "producto").any { token -> lower.contains(token) }) tags += "project"
        if (lower.contains("privad") || lower.contains("segur")) tags += "privacy"
        if (lower.contains("grafo") || lower.contains("backlink") || lower.contains("obsidian")) tags += "memory_graph"

        val kind = when {
            eventType.startsWith("genesis") -> "identity"
            eventType == "memory_review.aprobado" -> "approval"
            eventType == "memory_review.ruido_degradado" -> "chat_noise"
            eventType == "memory_review.correccion_requerida" -> "correction"
            isLowSignalChat -> "chat_noise"
            listOf("corrige", "correccion", "no es asi", "te equivocas", "esta mal").any { token -> lower.contains(token) } -> "correction"
            listOf("error", "fallo", "bug", "no funciona").any { token -> lower.contains(token) } -> "error_detected"
            listOf("rechazo", "no apruebo", "cancel", "no lo hagas").any { token -> lower.contains(token) } -> "rejection"
            listOf("decision", "decid", "queda", "regla", "nunca", "siempre", "se define", "memoria fuerte").any { token -> lower.contains(token) } -> "decision"
            listOf("prefiero", "me gusta", "quiero", "no quiero", "mi forma").any { token -> lower.contains(token) } -> "preference"
            listOf("aprend", "actualiza", "libro", "program", "investiga").any { token -> lower.contains(token) } -> "learning"
            listOf("apruebo", "aprobado").any { token -> lower.contains(token) } && isUser -> "approval"
            eventType.startsWith("decision") -> "decision"
            else -> "conversation"
        }

        tags += when (kind) {
            "identity" -> "identity"
            "correction" -> "correction"
            "error_detected" -> "error"
            "approval" -> "approval"
            "rejection" -> "rejection"
            "decision" -> "decision"
            "preference" -> "preference"
            "learning" -> "learning"
            "chat_noise" -> "noise"
            else -> "conversation"
        }

        val importance = when (kind) {
            "identity" -> 100
            "decision", "correction" -> 92
            "approval", "rejection" -> 86
            "preference" -> 88
            "learning", "error_detected" -> 82
            "chat_noise" -> 8
            "conversation" -> if (body.length > 240) 62 else 38
            else -> 50
        }

        val userConfirmed = isUser && kind in setOf(
            "decision", "correction", "preference", "approval", "rejection", "learning"
        )

        val confidence = when {
            kind == "chat_noise" -> 40
            userConfirmed -> 94
            isSystem -> 90
            isUser && kind != "conversation" -> 88
            else -> 70
        }

        return MemoryClassification(
            memoryKind = kind,
            tags = tags.toList(),
            confidence = confidence,
            userConfirmed = userConfirmed,
            importance = importance
        )
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
    }
}

data class MemoryClassification(
    val memoryKind: String,
    val tags: List<String>,
    val confidence: Int,
    val userConfirmed: Boolean,
    val importance: Int
)
