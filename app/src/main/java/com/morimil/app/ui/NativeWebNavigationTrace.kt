package com.morimil.app.ui

import com.morimil.app.web.NativeWebRequest

internal object NativeWebNavigationTrace {
    fun started(request: NativeWebRequest, searchUrl: String): List<NativeNavigationEvent> {
        val now = System.currentTimeMillis()
        return listOf(
            NativeNavigationEvent(
                type = "SESSION_STARTED",
                detail = "inicio de navegacion temporal",
                timestampMillis = now
            ),
            NativeNavigationEvent(
                type = "SEARCH_OPENED",
                detail = "busqueda abierta",
                url = searchUrl,
                timestampMillis = now
            )
        )
    }

    fun append(
        events: List<NativeNavigationEvent>,
        type: String,
        detail: String,
        url: String? = null,
        title: String? = null,
        host: String? = null,
        score: Int? = null,
        reason: String? = null
    ): List<NativeNavigationEvent> {
        val event = NativeNavigationEvent(
            type = type,
            detail = detail.take(MAX_NAVIGATION_DETAIL_CHARS),
            url = url?.take(MAX_NAVIGATION_URL_CHARS),
            title = title?.take(MAX_NAVIGATION_TITLE_CHARS),
            host = host?.take(MAX_NAVIGATION_HOST_CHARS),
            score = score,
            reason = reason?.take(MAX_NAVIGATION_REASON_CHARS),
            timestampMillis = System.currentTimeMillis()
        )
        return (events + event).takeLast(MAX_NAVIGATION_EVENTS)
    }

    fun text(
        request: NativeWebRequest,
        events: List<NativeNavigationEvent>
    ): String {
        return buildString {
            appendLine("WEB_NAVIGATION_TRACE")
            appendLine("scope=temporary_session")
            appendLine("requestedAtMillis=${request.requestedAtMillis}")
            appendLine("events:")
            events.takeLast(MAX_NAVIGATION_TRACE_EVENTS).forEach { event ->
                append("- type=${event.type}")
                append(" timestamp=${event.timestampMillis}")
                event.host?.let { append(" host=$it") }
                event.score?.let { append(" score=$it") }
                event.reason?.let { append(" reason=$it") }
                event.url?.let { append(" url=$it") }
                event.title?.let { append(" title=$it") }
                append(" detail=${event.detail}")
                appendLine()
            }
        }.take(MAX_NAVIGATION_TRACE_CHARS)
    }

    private const val MAX_NAVIGATION_EVENTS = 32
    private const val MAX_NAVIGATION_TRACE_EVENTS = 12
    private const val MAX_NAVIGATION_TRACE_CHARS = 4_000
    private const val MAX_NAVIGATION_DETAIL_CHARS = 220
    private const val MAX_NAVIGATION_URL_CHARS = 420
    private const val MAX_NAVIGATION_TITLE_CHARS = 180
    private const val MAX_NAVIGATION_HOST_CHARS = 120
    private const val MAX_NAVIGATION_REASON_CHARS = 160
}
