package com.morimil.app.core.project

data class ProjectCreationIntent(
    val displayName: String,
    val mission: String,
    val sourcePhrase: String
)

object ProjectIntentDetector {
    private val startMarkers = listOf(
        "vamos a crear",
        "vamos a hacer",
        "quiero crear",
        "estoy creando",
        "estamos creando",
        "estoy desarrollando",
        "estamos desarrollando",
        "estoy construyendo",
        "estamos construyendo",
        "tengo una empresa",
        "tengo un proyecto",
        "crea una",
        "crear una",
        "hacer una",
        "inicia una",
        "iniciar una",
        "abre una boveda",
        "abre una bóveda",
        "crear boveda",
        "crear bóveda",
        "new company",
        "create a company",
        "start a project",
        "i am building",
        "we are building"
    )

    private val negativeMarkers = listOf(
        "no vamos a crear",
        "no voy a crear",
        "no la vamos a crear",
        "no lo vamos a crear",
        "todavia no",
        "todavía no",
        "solo estamos hablando",
        "solo estamos revisando",
        "aun no crear",
        "aún no crear",
        "do not create",
        "don't create",
        "not creating yet"
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

    private val namePatterns = listOf(
        Regex("(?:llamada|llamado|named|called|nombre)\\s+([\\p{L}\\p{N}_\\-メ]+(?:\\s+[\\p{L}\\p{N}_\\-メ]+){0,4})", RegexOption.IGNORE_CASE),
        Regex("(?:empresa|startup|compania|compañia|proyecto|boveda|bóveda|app|producto|company|project|vault)\\s+de\\s+([\\p{L}\\p{N}_\\-メ]+(?:\\s+[\\p{L}\\p{N}_\\-メ]+){0,4})", RegexOption.IGNORE_CASE)
    )

    private val stopWords = setOf(
        "que",
        "para",
        "porque",
        "donde",
        "cuando",
        "con",
        "sin",
        "y",
        "o",
        "pero",
        "como",
        "the",
        "for",
        "with",
        "and",
        "but",
        "as"
    )

    fun detect(text: String): ProjectCreationIntent? {
        val cleanText = text.trim()
        if (cleanText.length < 8) return null
        val lower = cleanText.lowercase()
        if (negativeMarkers.any { marker -> marker in lower }) return null

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
        val rawName = namePatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.getOrNull(1)
        } ?: return null

        val cleaned = rawName
            .trim(' ', '.', ',', ':', ';', '"', '\'', '`')
            .split(" ")
            .takeWhile { token -> token.lowercase().trim(',', '.', ';', ':') !in stopWords }
            .take(5)
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
