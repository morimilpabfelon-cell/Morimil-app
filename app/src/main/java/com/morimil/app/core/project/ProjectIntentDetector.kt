package com.morimil.app.core.project

data class ProjectCreationIntent(
    val displayName: String,
    val mission: String,
    val sourcePhrase: String
)

object ProjectIntentDetector {
    private val startMarkers = listOf(
        "vamos a crear",
        "quiero crear",
        "crea una",
        "crear una",
        "inicia una",
        "iniciar una",
        "abre una boveda",
        "crear boveda",
        "new company",
        "create a company",
        "start a project"
    )

    private val projectNouns = listOf(
        "empresa",
        "startup",
        "compania",
        "compañia",
        "proyecto",
        "boveda",
        "bóveda",
        "app",
        "producto",
        "company",
        "project",
        "vault"
    )

    private val nameMarkers = listOf(
        "llamada",
        "llamado",
        "llamar",
        "nombre",
        "llamada:",
        "llamado:",
        "called",
        "named"
    )

    fun detect(text: String): ProjectCreationIntent? {
        val cleanText = text.trim()
        if (cleanText.length < 8) return null
        val lower = cleanText.lowercase()
        val hasStartMarker = startMarkers.any { marker -> marker in lower }
        val hasProjectNoun = projectNouns.any { noun -> noun in lower }
        if (!hasStartMarker || !hasProjectNoun) return null

        val displayName = extractName(cleanText) ?: return null
        val mission = buildMission(displayName, cleanText)
        return ProjectCreationIntent(
            displayName = displayName,
            mission = mission,
            sourcePhrase = cleanText.take(280)
        )
    }

    private fun extractName(text: String): String? {
        val markerMatch = nameMarkers
            .mapNotNull { marker ->
                val index = text.lowercase().indexOf(marker)
                if (index >= 0) marker to index else null
            }
            .minByOrNull { it.second }

        val rawName = if (markerMatch != null) {
            text.substring(markerMatch.second + markerMatch.first.length)
        } else {
            text.substringAfterLast(" de ", missingDelimiterValue = text)
                .substringAfterLast(" for ", missingDelimiterValue = text)
        }

        val cleaned = rawName
            .trim(' ', '.', ',', ':', ';', '"', '\'', '`')
            .split(" ")
            .take(4)
            .joinToString(" ")
            .trim(' ', '.', ',', ':', ';', '"', '\'', '`')

        if (cleaned.length < 2) return null
        if (cleaned.lowercase() in projectNouns) return null
        return cleaned
    }

    private fun buildMission(displayName: String, sourceText: String): String {
        return "Crear y ordenar la boveda operativa de $displayName. Morimil debe definir roadmap inicial, crear enjambre minimo, asignar tareas, revisar resultados y conservar solo decisiones/aprendizajes utiles en memoria viva. Instruccion original: ${sourceText.take(180)}"
    }
}
