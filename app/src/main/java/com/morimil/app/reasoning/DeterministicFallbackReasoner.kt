package com.morimil.app.reasoning

/**
 * Local fallback used when no model backend is available or the model fails.
 *
 * This class must stay conservative. It should preserve continuity and state
 * instead of pretending full high-level model reasoning.
 */
object DeterministicFallbackReasoner {
    fun reply(
        input: String,
        intent: ReasoningIntent,
        memoryContext: String,
        capsuleContext: String,
        modelError: String? = null
    ): String {
        val errorLine = modelError
            ?.takeIf { it.isNotBlank() }
            ?.let { "\n\nFallo del motor: ${it.take(220)}" }
            .orEmpty()
        val memorySignal = compactSignal(memoryContext)
        val capsuleSignal = compactSignal(capsuleContext)

        return when (intent) {
            ReasoningIntent.MEMORY_QUERY ->
                "Estoy en modo local degradado. Puedo usar memoria local recuperada, pero no voy a fingir razonamiento superior sin motor activo.\n\nMemoria local relevante:\n$memorySignal\n\nCapsulas locales:\n$capsuleSignal$errorLine"

            ReasoningIntent.STATUS_CHECK ->
                "Estoy operativo en modo local degradado. La continuidad basica se mantiene: entrada recibida, memoria local disponible y respuesta conservadora generada sin depender de API. Para diagnostico profundo necesito el motor local/API o una pantalla de salud conectada al Kernel.$errorLine"

            ReasoningIntent.PROJECT_WORK, ReasoningIntent.ARCHITECTURE_REVIEW ->
                "Estoy en modo local degradado. Puedo mantener el hilo y preparar un plan basico, pero no debo inventar una revision tecnica profunda sin motor activo. Primer paso seguro: revisar memoria local y dejar la tarea lista para procesar cuando el backend de razonamiento vuelva.\n\nResumen local disponible:\n$memorySignal$errorLine"

            ReasoningIntent.TOOL_OR_AGENT_REQUEST ->
                "Estoy en modo local degradado. No ejecuto herramientas ni delegaciones desde fallback. Puedo registrar la intencion y dejar una propuesta pendiente para revision humana o para cuando el motor operativo este disponible.$errorLine"

            ReasoningIntent.SELF_IMPROVEMENT ->
                "Estoy en modo local degradado. La auto-mejora no debe ejecutarse sin evaluacion, diff, aprobacion y rollback. Puedo registrar la necesidad de mejora, pero no modificar memoria profunda ni nucleo desde fallback.$errorLine"

            ReasoningIntent.GENERAL_CHAT, ReasoningIntent.UNKNOWN ->
                "Estoy en modo local degradado. Puedo responder de forma basica desde memoria local, pero no tengo motor avanzado activo para razonamiento profundo.\n\nEntrada recibida: ${input.take(280)}$errorLine"
        }
    }

    private fun compactSignal(value: String): String {
        val clean = value.trim()
        if (clean.isBlank()) return "- Sin contexto local disponible."
        return clean
            .lines()
            .filter { it.isNotBlank() }
            .take(8)
            .joinToString("\n") { line -> line.take(220) }
            .ifBlank { "- Sin contexto local disponible." }
    }
}
