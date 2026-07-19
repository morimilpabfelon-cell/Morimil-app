package com.morimil.app.ai

/**
 * Builds authentication headers only for validated remote temporary providers.
 * Local USB/ADB reasoning never receives a stored API credential.
 */
internal object ReasoningRequestHeaders {
    fun build(config: ReasoningProviderConfig, runtimeKey: String): Map<String, String> {
        if (!config.requiresRuntimeKey) return emptyMap()
        val cleanKey = runtimeKey.trim()
        require(cleanKey.isNotBlank()) { "Falta la llave de razonamiento." }

        return when (config.wireFormat) {
            ReasoningWireFormat.MESSAGES -> mapOf(
                MESSAGES_VERSION_HEADER to MESSAGES_VERSION,
                API_KEY_HEADER to cleanKey
            )
            ReasoningWireFormat.CHAT,
            ReasoningWireFormat.RESPONSES -> mapOf(
                AUTHORIZATION_HEADER to "Bearer $cleanKey"
            )
        }
    }

    private const val AUTHORIZATION_HEADER = "Authorization"
    private const val API_KEY_HEADER = "x-api-key"
    private const val MESSAGES_VERSION_HEADER = "anthropic-version"
    private const val MESSAGES_VERSION = "2023-06-01"
}
