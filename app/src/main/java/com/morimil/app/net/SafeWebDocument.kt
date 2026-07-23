package com.morimil.app.net

internal data class SafeWebDocument(
    val finalUrl: String,
    val html: String
)

internal data class SafeWebDocumentResult(
    val ok: Boolean,
    val document: SafeWebDocument? = null,
    val error: String? = null
)

internal class SafeWebDocumentLoader(
    private val transport: SafeHttpTransport = SafeHttpTransport()
) {
    fun fetch(rawUrl: String): SafeWebDocumentResult {
        val response = transport.fetch(
            rawUrl = rawUrl,
            maxSuccessBytes = MAX_DOCUMENT_BYTES,
            maxErrorBytes = MAX_ERROR_BYTES,
            maxRedirects = MAX_REDIRECTS
        )
        if (!response.ok) {
            return SafeWebDocumentResult(ok = false, error = response.error ?: "document_fetch_failed")
        }
        if (response.mediaType !in ALLOWED_DOCUMENT_MEDIA_TYPES) {
            return SafeWebDocumentResult(
                ok = false,
                error = "unsupported_document_type:${response.mediaType}"
            )
        }

        val source = response.bodyText()
        if (source.isBlank()) return SafeWebDocumentResult(ok = false, error = "document_empty")
        val html = when (response.mediaType) {
            "text/plain" -> SafeWebDocumentHardener.wrapPlainText(source)
            else -> SafeWebDocumentHardener.hardenHtml(source)
        }
        return SafeWebDocumentResult(
            ok = true,
            document = SafeWebDocument(finalUrl = response.finalUrl, html = html)
        )
    }

    private companion object {
        const val MAX_DOCUMENT_BYTES = 2 * 1024 * 1024
        const val MAX_ERROR_BYTES = 64 * 1024
        const val MAX_REDIRECTS = 4
        val ALLOWED_DOCUMENT_MEDIA_TYPES = setOf(
            "text/html",
            "application/xhtml+xml",
            "text/plain"
        )
    }
}

internal object SafeWebDocumentHardener {
    fun hardenHtml(source: String): String {
        val cleaned = source
            .replace(CONTENT_SECURITY_POLICY_META_REGEX, "")
            .replace(META_REFRESH_REGEX, "")
            .replace(BASE_TAG_REGEX, "")
            .replace(SCRIPT_BLOCK_REGEX, "")
            .replace(SCRIPT_TAG_REGEX, "")
            .replace(INLINE_EVENT_HANDLER_REGEX, "")
            .replace(SRCDOC_ATTRIBUTE_REGEX, "")
        val securityHead = """
            <meta charset="utf-8">
            <meta http-equiv="Content-Security-Policy" content="$CONTENT_SECURITY_POLICY">
        """.trimIndent()
        val headMatch = HEAD_OPEN_REGEX.find(cleaned)
        return if (headMatch != null) {
            cleaned.replaceRange(headMatch.range.last + 1, headMatch.range.last + 1, securityHead)
        } else {
            "<!doctype html><html><head>$securityHead</head><body>$cleaned</body></html>"
        }
    }

    fun wrapPlainText(source: String): String {
        return """
            <!doctype html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta http-equiv="Content-Security-Policy" content="$CONTENT_SECURITY_POLICY">
                <style>body{font-family:sans-serif;white-space:pre-wrap;overflow-wrap:anywhere;padding:16px}</style>
            </head>
            <body>${escapeHtml(source)}</body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(value: String): String {
        return buildString(value.length) {
            value.forEach { character ->
                append(
                    when (character) {
                        '&' -> "&amp;"
                        '<' -> "&lt;"
                        '>' -> "&gt;"
                        '"' -> "&quot;"
                        '\'' -> "&#39;"
                        else -> character
                    }
                )
            }
        }
    }

    private const val CONTENT_SECURITY_POLICY =
        "default-src 'none'; img-src data:; style-src 'unsafe-inline'; " +
            "script-src 'none'; connect-src 'none'; frame-src 'none'; object-src 'none'; " +
            "media-src 'none'; worker-src 'none'; child-src 'none'; form-action 'none'; base-uri 'none'"
    private val HEAD_OPEN_REGEX = Regex("<head(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE)
    private val CONTENT_SECURITY_POLICY_META_REGEX = Regex(
        "<meta\\b[^>]*http-equiv\\s*=\\s*(['\"]?)content-security-policy\\1[^>]*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val META_REFRESH_REGEX = Regex(
        "<meta\\b[^>]*http-equiv\\s*=\\s*(['\"]?)refresh\\1[^>]*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val BASE_TAG_REGEX = Regex("<base\\b[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val SCRIPT_BLOCK_REGEX = Regex(
        "<script\\b[^>]*>.*?</script\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val SCRIPT_TAG_REGEX = Regex("<script\\b[^>]*/?>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val INLINE_EVENT_HANDLER_REGEX = Regex(
        "\\s+on[a-z0-9_-]+\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val SRCDOC_ATTRIBUTE_REGEX = Regex(
        "\\s+srcdoc\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
}
