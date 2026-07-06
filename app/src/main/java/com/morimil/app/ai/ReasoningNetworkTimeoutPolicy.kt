package com.morimil.app.ai

object ReasoningNetworkTimeoutPolicy {
    const val CONNECT_TIMEOUT_MS = 15_000
    const val READ_TIMEOUT_MS = 120_000

    fun userMessage(): String {
        return "Tiempo agotado esperando al motor de razonamiento. La conexion existe, pero el proveedor tardo demasiado en responder. Reduce el contexto, usa un modelo mas rapido o intenta de nuevo."
    }
}
