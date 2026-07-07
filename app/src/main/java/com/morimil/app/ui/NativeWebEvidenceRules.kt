package com.morimil.app.ui

internal object NativeWebEvidenceRules {
    fun confidence(host: String, score: Int, textChars: Int): String {
        val trusted = isTrustedTechnicalHost(host)
        return when {
            textChars < 400 -> "LOW"
            trusted && score >= 85 && textChars >= 900 -> "HIGH"
            trusted && score >= 65 && textChars >= 700 -> "MEDIUM"
            score >= 80 && textChars >= 1_200 -> "MEDIUM"
            else -> "LOW"
        }
    }

    fun confidenceReason(host: String, score: Int, textChars: Int): String {
        return when {
            textChars < 400 -> "captura demasiado pequena"
            isTrustedTechnicalHost(host) && score >= 85 -> "fuente tecnica prioritaria con score alto"
            isTrustedTechnicalHost(host) -> "fuente tecnica prioritaria"
            score >= 80 -> "score alto pero fuente no primaria"
            else -> "evidencia util solo como contexto temporal"
        }
    }

    fun isTrustedTechnicalHost(host: String): Boolean {
        return host == "developer.android.com" ||
            host == "kotlinlang.org" ||
            host == "docs.gradle.org" ||
            host == "docs.github.com" ||
            host == "github.com" ||
            host == "stackoverflow.com"
    }

    fun keywordOverlap(first: String, second: String): Int {
        val firstWords = importantWords(first)
        val secondWords = importantWords(second)
        return firstWords.intersect(secondWords).size
    }

    private fun importantWords(text: String): Set<String> {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9áéíóúñ_./-]+"), " ")
            .split(' ')
            .asSequence()
            .map { it.trim('.', '/', '-') }
            .filter { it.length >= 5 }
            .filterNot { it in COMMON_WEB_WORDS }
            .take(220)
            .toSet()
    }

    private val COMMON_WEB_WORDS = setOf(
        "about",
        "after",
        "android",
        "before",
        "click",
        "cookie",
        "documentation",
        "error",
        "github",
        "google",
        "kotlin",
        "learn",
        "login",
        "official",
        "privacy",
        "search",
        "settings",
        "source",
        "terms",
        "using",
        "video"
    )
}
