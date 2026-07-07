package com.morimil.app.web

/**
 * Builds a small local search plan before the WebView navigates.
 *
 * This keeps the native browser observable, but gives Morimil a stronger
 * search entry point than passing the raw chat sentence directly to Brave.
 */
object WebSearchPlanner {
    fun create(rawQuery: String): WebSearchPlan? {
        val original = normalize(rawQuery).take(MAX_ORIGINAL_QUERY_CHARS)
        if (original.isBlank()) return null

        val intent = detectIntent(original)
        val searchQuery = optimizeQuery(original, intent).take(MAX_SEARCH_QUERY_CHARS)

        return WebSearchPlan(
            originalQuery = original,
            searchQuery = searchQuery,
            intent = intent,
            strategy = strategyFor(intent)
        )
    }

    private fun optimizeQuery(query: String, intent: WebSearchIntent): String {
        val lower = query.lowercase()
        val quotedError = firstUsefulQuotedPhrase(query)
        val base = quotedError ?: query

        val suffixes = buildList {
            when (intent) {
                WebSearchIntent.ANDROID_CODE -> {
                    add("Android")
                    add("Kotlin")
                    if (!lower.contains("jetpack compose")) add("Jetpack Compose")
                }
                WebSearchIntent.GRADLE_ERROR -> {
                    add("Gradle")
                    add("Android")
                    add("Kotlin")
                }
                WebSearchIntent.GITHUB_PROJECT -> {
                    add("GitHub")
                    if (!lower.contains("android")) add("Android")
                }
                WebSearchIntent.DOCUMENTATION -> {
                    add("documentation")
                    add("official")
                }
                WebSearchIntent.GENERAL -> Unit
            }
        }

        return normalize((listOf(base) + suffixes).joinToString(" "))
    }

    private fun detectIntent(query: String): WebSearchIntent {
        val lower = query.lowercase()
        return when {
            lower.contains("gradle") ||
                lower.contains("assembledebug") ||
                lower.contains("compiledebugkotlin") ||
                lower.contains("build failed") -> WebSearchIntent.GRADLE_ERROR

            lower.contains("android") ||
                lower.contains("kotlin") ||
                lower.contains("compose") ||
                lower.contains("room") ||
                lower.contains("workmanager") ||
                lower.contains("webview") -> WebSearchIntent.ANDROID_CODE

            lower.contains("github") ||
                lower.contains("pull request") ||
                lower.contains("commit") ||
                lower.contains("branch") -> WebSearchIntent.GITHUB_PROJECT

            lower.contains("documentacion") ||
                lower.contains("documentación") ||
                lower.contains("docs") ||
                lower.contains("official") -> WebSearchIntent.DOCUMENTATION

            else -> WebSearchIntent.GENERAL
        }
    }

    private fun strategyFor(intent: WebSearchIntent): String {
        return when (intent) {
            WebSearchIntent.ANDROID_CODE -> "priorizar documentacion oficial, issues relevantes y soluciones recientes de Android/Kotlin."
            WebSearchIntent.GRADLE_ERROR -> "buscar el mensaje exacto del error antes de abrir resultados generales."
            WebSearchIntent.GITHUB_PROJECT -> "priorizar documentacion oficial de GitHub y referencias tecnicas del flujo de repositorio."
            WebSearchIntent.DOCUMENTATION -> "priorizar fuentes oficiales y evitar blogs repetidos si existe documentacion primaria."
            WebSearchIntent.GENERAL -> "buscar fuentes claras, comparar resultados y abrir la pagina mas util."
        }
    }

    private fun firstUsefulQuotedPhrase(query: String): String? {
        val regex = Regex("\"([^\"]{12,160})\"")
        return regex.find(query)?.groupValues?.getOrNull(1)?.let(::normalize)
    }

    private fun normalize(value: String): String {
        return value
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private const val MAX_ORIGINAL_QUERY_CHARS = 280
    private const val MAX_SEARCH_QUERY_CHARS = 180
}

data class WebSearchPlan(
    val originalQuery: String,
    val searchQuery: String,
    val intent: WebSearchIntent,
    val strategy: String
)

enum class WebSearchIntent {
    GENERAL,
    ANDROID_CODE,
    GRADLE_ERROR,
    GITHUB_PROJECT,
    DOCUMENTATION
}
