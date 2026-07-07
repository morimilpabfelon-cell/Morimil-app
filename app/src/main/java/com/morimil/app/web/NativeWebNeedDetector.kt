package com.morimil.app.web

import java.text.Normalizer
import java.util.Locale

object NativeWebNeedDetector {
    fun shouldOpen(input: String): Boolean {
        val normalized = normalize(input)
        if (normalized.isBlank()) return false
        return EXPLICIT_WEB_TERMS.any { term -> normalized.contains(term) }
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .trim()
    }

    /**
     * Native web is an owner-requested tool, not a background habit.
     * Broad content words such as recipe, price or news are intentionally not
     * triggers by themselves because web pages are untrusted external input.
     */
    private val EXPLICIT_WEB_TERMS = listOf(
        "busca",
        "buscar",
        "buscame",
        "buscame",
        "buscalo",
        "buscala",
        "buscalos",
        "buscalas",
        "investiga",
        "internet",
        "web",
        "google",
        "en linea",
        "online",
        "consulta la red",
        "navega"
    )
}
