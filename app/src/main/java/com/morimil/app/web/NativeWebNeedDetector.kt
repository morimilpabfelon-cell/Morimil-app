package com.morimil.app.web

import java.text.Normalizer
import java.util.Locale

object NativeWebNeedDetector {
    fun shouldOpen(input: String): Boolean {
        val normalized = normalize(input)
        if (normalized.isBlank()) return false
        return TERMS.any { term -> normalized.contains(term) }
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .trim()
    }

    private val TERMS = listOf(
        "busca",
        "buscar",
        "investiga",
        "receta",
        "reseta",
        "noticia",
        "actual",
        "precio",
        "ultimo",
        "ultima",
        "caldo de gallina"
    )
}
