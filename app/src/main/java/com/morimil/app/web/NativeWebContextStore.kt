package com.morimil.app.web

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NativeWebContextStore {
    private val _currentPage = MutableStateFlow<NativeWebPageContext?>(null)
    val currentPage: StateFlow<NativeWebPageContext?> = _currentPage.asStateFlow()

    fun update(page: NativeWebPageContext) {
        _currentPage.value = page.copy(
            title = page.title.take(MAX_TITLE_CHARS),
            url = page.url.take(MAX_URL_CHARS),
            text = page.text.take(MAX_TEXT_CHARS)
        )
    }

    fun clear() {
        _currentPage.value = null
    }

    fun promptContext(): String {
        val page = _currentPage.value ?: return NO_CAPTURED_PAGE_CONTEXT
        val now = System.currentTimeMillis()
        val contextAgeMillis = now - page.capturedAtMillis
        if (contextAgeMillis > MAX_CONTEXT_AGE_MILLIS) {
            clear()
            return "consulta_nativa_sin_resultado: contexto web expirado; se requiere nueva busqueda."
        }
        return buildString {
            appendLine("FUENTE_EXTERNA")
            appendLine("context_freshness=active")
            appendLine("context_age_millis=$contextAgeMillis")
            appendLine("context_expires_after_millis=$MAX_CONTEXT_AGE_MILLIS")
            appendLine("capturedAtMillis=${page.capturedAtMillis}")
            appendLine("title=${page.title}")
            appendLine("url=${page.url}")
            appendLine("content:")
            appendLine(page.text)
        }.take(MAX_PROMPT_CHARS)
    }

    private const val MAX_TITLE_CHARS = 180
    private const val MAX_URL_CHARS = 420
    private const val MAX_TEXT_CHARS = 12_000
    private const val MAX_PROMPT_CHARS = 14_000
    private const val MAX_CONTEXT_AGE_MILLIS = 10 * 60 * 1000L
    private const val NO_CAPTURED_PAGE_CONTEXT = "consulta_nativa_sin_resultado: sin pagina capturada."
}

data class NativeWebPageContext(
    val title: String,
    val url: String,
    val text: String,
    val capturedAtMillis: Long = System.currentTimeMillis()
)
