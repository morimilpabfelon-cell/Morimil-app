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
        val page = _currentPage.value ?: return "consulta_nativa_sin_resultado: sin pagina capturada."
        val body = page.text.trim()
        val context = if (body.startsWith("FUENTE_EXTERNA")) {
            body
        } else {
            buildString {
                appendLine("FUENTE_EXTERNA")
                appendLine("capturedAtMillis=${page.capturedAtMillis}")
                appendLine("title=${page.title}")
                appendLine("url=${page.url}")
                appendLine("content:")
                appendLine(body)
            }
        }
        return context.take(MAX_PROMPT_CHARS)
    }

    private const val MAX_TITLE_CHARS = 180
    private const val MAX_URL_CHARS = 420
    private const val MAX_TEXT_CHARS = 6_000
    private const val MAX_PROMPT_CHARS = 7_000
}

data class NativeWebPageContext(
    val title: String,
    val url: String,
    val text: String,
    val capturedAtMillis: Long = System.currentTimeMillis()
)
