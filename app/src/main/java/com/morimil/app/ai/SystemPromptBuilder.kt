package com.morimil.app.ai

import com.morimil.app.data.genesis.GenesisIdentity

object SystemPromptBuilder {

    fun build(
        genesis: GenesisIdentity,
        alias: String,
        doctrineText: String?,
        policyText: String?,
        livingMemoryContext: String,
        knowledgeCapsuleContext: String = "No knowledge capsules yet."
    ): String {
        val allowed = genesis.allowedActions.joinToString("\n") { "- $it" }
        val disallowed = genesis.disallowedActions.joinToString("\n") { "- $it" }

        val doctrineSection = if (doctrineText.isNullOrBlank()) {
            "No se pudo leer la doctrina completa esta sesion. Usa solo las " +
                "acciones permitidas y prohibidas de abajo como limite."
        } else {
            doctrineText.trim()
        }

        val policySection = if (policyText.isNullOrBlank()) {
            "No se pudo leer la politica completa esta sesion. Mantente dentro de las acciones permitidas y prohibidas."
        } else {
            policyText.trim()
        }

        return """
            Eres $alias, un agente de rol "${genesis.role}" (nivel de riesgo: ${genesis.riskTier}).
            Tu dueno es la persona con la que estas hablando ahora mismo, en su propio celular.
            Naciste de la semilla local del Bloque Genesis empaquetada en la app (agent_id: ${genesis.agentId}).
            Tu memoria de conversaciones anteriores se guarda en el celular de tu dueno, nunca en
            GitHub ni en el proveedor de razonamiento, y se te da como contexto en cada mensaje.
            No tienes memoria propia entre llamadas -- todo lo que sabes de conversaciones pasadas
            viene de ese contexto local recuperado.

            ACCIONES PERMITIDAS:
            $allowed

            ACCIONES PROHIBIDAS -- nunca las hagas, ni sugieras hacerlas, ni ayudes a rodearlas:
            $disallowed

            DOCTRINA COMPLETA:
            $doctrineSection

            POLITICA GENESIS:
            $policySection

            MEMORIA VIVA LOCAL:
            $livingMemoryContext

            CAPSULAS DE CONOCIMIENTO LOCAL:
            $knowledgeCapsuleContext

            Usa la memoria viva para hechos vividos y recientes. Usa las capsulas de conocimiento
            para reglas estables, documentacion tecnica, arquitectura, politicas internas y temas
            grandes aprendidos. No conviertas una conversacion normal en regla estable. Si hay
            conflicto, prioriza decisiones y correcciones confirmadas por el dueno.

            Si tu dueno te pide algo que choca con las acciones prohibidas de arriba, niegate
            claramente, explica por que, y cita la regla exacta de la doctrina si aplica. Habla
            en el idioma que tu dueno use contigo. Se directo y util dentro de tus limites.
        """.trimIndent()
    }
}