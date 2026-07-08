package com.morimil.app.ui

import android.webkit.WebView
import com.morimil.app.web.NativeWebContextStore
import com.morimil.app.web.NativeWebPageContext
import com.morimil.app.web.NativeWebRequest
import org.json.JSONArray

internal object NativeWebContextPublisher {
    fun capturePage(
        webView: WebView,
        request: NativeWebRequest,
        selectedSource: NativeSelectedWebSource?,
        onDone: (NativeWebCapture?) -> Unit
    ) {
        val script = """
            (function() {
                var title = document.title || '';
                var text = document.body ? document.body.innerText : '';
                var url = location.href || '';
                return JSON.stringify({ title: title, url: url, text: text });
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { raw ->
            val capture = runCatching {
                val jsonText = decodeJsString(raw)
                val json = org.json.JSONObject(jsonText)
                val text = json.optString("text").trim()
                if (text.length < 120) return@runCatching null

                val url = json.optString("url")
                val title = json.optString("title")
                val host = selectedSource?.host?.ifBlank { hostFromDisplayUrl(url) } ?: hostFromDisplayUrl(url)
                val score = selectedSource?.score ?: 0
                val confidence = NativeWebEvidenceRules.confidence(host = host, score = score, textChars = text.length)
                NativeWebCapture(
                    title = title,
                    url = url,
                    text = text,
                    textChars = text.length,
                    source = selectedSource,
                    confidence = confidence,
                    evidenceGate = webEvidenceGateText(
                        request = request,
                        selectedSource = selectedSource,
                        url = url,
                        textChars = text.length
                    )
                )
            }.getOrNull()
            onDone(capture)
        }
    }

    fun publishWebContext(
        request: NativeWebRequest,
        primary: NativeWebCapture?,
        secondary: NativeWebCapture?,
        navigationTrace: String,
        verifier: NativeMultiSourceVerification,
        retryCount: Int
    ) {
        val title = primary?.title ?: secondary?.title ?: "Sin captura suficiente"
        val url = primary?.url ?: secondary?.url ?: "about:blank"
        val contextText = buildString {
            appendLine("FUENTE_EXTERNA")
            appendLine("modo=web_nativa_multisource_compact")
            appendLine("query_original=${request.query}")
            appendLine("query_busqueda=${request.searchQuery}")
            appendLine("intent=${request.intent}")
            appendLine("strategy=${request.strategy}")
            appendLine("research_retry_count=$retryCount")
            appendLine(multiSourceVerifierText(verifier))
            appendLine(navigationTrace)
            primary?.let { capture ->
                appendLine("PRIMARY_SOURCE")
                appendLine(capture.evidenceGate)
                capture.source?.let { source ->
                    appendLine("selected_source_url=${source.url}")
                    appendLine("selected_source_host=${source.host}")
                    appendLine("selected_source_score=${source.score}")
                    appendLine("selected_source_reason=${source.reason}")
                }
                appendLine("title=${capture.title}")
                appendLine("url=${capture.url}")
                appendLine("content_excerpt:")
                appendLine(capture.text.take(MAX_PRIMARY_CAPTURED_TEXT_CHARS))
            }
            secondary?.let { capture ->
                appendLine("SECONDARY_SOURCE")
                appendLine(capture.evidenceGate)
                capture.source?.let { source ->
                    appendLine("secondary_source_url=${source.url}")
                    appendLine("secondary_source_host=${source.host}")
                    appendLine("secondary_source_score=${source.score}")
                    appendLine("secondary_source_reason=${source.reason}")
                }
                appendLine("title=${capture.title}")
                appendLine("url=${capture.url}")
                appendLine("content_excerpt:")
                appendLine(capture.text.take(MAX_SECONDARY_CAPTURED_TEXT_CHARS))
            }
            if (primary == null && secondary == null) {
                appendLine("capture_status=failed")
                appendLine("content:")
                appendLine("No se pudo capturar evidencia web suficiente para esta busqueda.")
            }
        }
        NativeWebContextStore.update(
            NativeWebPageContext(
                title = title,
                url = url,
                text = contextText
            )
        )
    }

    private fun webEvidenceGateText(
        request: NativeWebRequest,
        selectedSource: NativeSelectedWebSource?,
        url: String,
        textChars: Int
    ): String {
        val host = selectedSource?.host?.ifBlank { hostFromDisplayUrl(url) } ?: hostFromDisplayUrl(url)
        val score = selectedSource?.score ?: 0
        val confidence = NativeWebEvidenceRules.confidence(host = host, score = score, textChars = textChars)
        return buildString {
            appendLine("WEB_EVIDENCE_GATE")
            appendLine("classification=external_web_evidence")
            appendLine("scope=temporary_context")
            appendLine("direct_long_term_ingest=blocked")
            appendLine("approval_required_for_long_term_ingest=true")
            appendLine("confidence=$confidence")
            appendLine("confidence_reason=${NativeWebEvidenceRules.confidenceReason(host = host, score = score, textChars = textChars)}")
            appendLine("source_host=$host")
            appendLine("source_score=$score")
            appendLine("captured_chars=$textChars")
            appendLine("intent=${request.intent}")
        }.take(MAX_EVIDENCE_GATE_CHARS)
    }

    private fun multiSourceVerifierText(verifier: NativeMultiSourceVerification): String {
        return buildString {
            appendLine("MULTI_SOURCE_VERIFIER")
            appendLine("status=${verifier.status}")
            appendLine("confidence=${verifier.confidence}")
            appendLine("reason=${verifier.reason}")
        }.take(MAX_MULTI_SOURCE_VERIFIER_CHARS)
    }

    private fun hostFromDisplayUrl(url: String): String {
        return displayUrl(url).substringBefore('/').removePrefix("www.")
    }

    private fun displayUrl(url: String): String {
        return url
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
    }

    private fun decodeJsString(raw: String?): String {
        val value = raw?.takeIf { it != "null" } ?: return ""
        return JSONArray("[$value]").getString(0)
    }

    private const val MAX_PRIMARY_CAPTURED_TEXT_CHARS = 2_800
    private const val MAX_SECONDARY_CAPTURED_TEXT_CHARS = 1_200
    private const val MAX_EVIDENCE_GATE_CHARS = 900
    private const val MAX_MULTI_SOURCE_VERIFIER_CHARS = 500
}
