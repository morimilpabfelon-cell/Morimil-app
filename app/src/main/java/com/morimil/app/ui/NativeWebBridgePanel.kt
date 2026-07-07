package com.morimil.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.web.NativeWebContextStore
import com.morimil.app.web.NativeWebPageContext
import com.morimil.app.web.NativeWebRequest
import com.morimil.app.web.NativeWebRequestStore
import org.json.JSONArray

private enum class WebBridgePhase {
    IDLE,
    SEARCH_RESULTS,
    SOURCE_PAGE
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun NativeWebBridgePanel(
    modifier: Modifier = Modifier,
    onPageReady: (String) -> Unit
) {
    val pendingRequest by NativeWebRequestStore.pendingRequest.collectAsStateWithLifecycle()
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var activeRequest by remember { mutableStateOf<NativeWebRequest?>(null) }
    var phase by remember { mutableStateOf(WebBridgePhase.IDLE) }

    LaunchedEffect(pendingRequest) {
        val request = pendingRequest ?: return@LaunchedEffect
        activeRequest = request
        phase = WebBridgePhase.SEARCH_RESULTS
        activeWebView?.loadUrl(toSearchUrl(request.query))
    }

    DisposableEffect(Unit) {
        onDispose {
            activeWebView?.destroy()
            activeWebView = null
        }
    }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            factory = { context ->
                WebView(context).apply {
                    activeWebView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportMultipleWindows(false)
                    setOnTouchListener { _, _ -> true }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = Unit

                        override fun onPageFinished(view: WebView, url: String?) {
                            val request = activeRequest ?: return
                            when (phase) {
                                WebBridgePhase.SEARCH_RESULTS -> {
                                    selectFirstUsefulResult(view) { selectedUrl ->
                                        if (selectedUrl.isNullOrBlank()) {
                                            capturePageText(view, request) { readyQuery ->
                                                finishIfReady(request, readyQuery, onPageReady) { phase = it }
                                            }
                                        } else {
                                            phase = WebBridgePhase.SOURCE_PAGE
                                            view.loadUrl(selectedUrl)
                                        }
                                    }
                                }
                                WebBridgePhase.SOURCE_PAGE -> {
                                    capturePageText(view, request) { readyQuery ->
                                        finishIfReady(request, readyQuery, onPageReady) { phase = it }
                                    }
                                }
                                WebBridgePhase.IDLE -> Unit
                            }
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) = Unit
                    }
                    loadUrl(BRAVE_HOME_URL)
                }
            },
            update = { webView ->
                val request = pendingRequest
                if (request != null && activeRequest?.requestedAtMillis != request.requestedAtMillis) {
                    activeRequest = request
                    phase = WebBridgePhase.SEARCH_RESULTS
                    webView.loadUrl(toSearchUrl(request.query))
                }
            }
        )
    }
}

private fun finishIfReady(
    request: NativeWebRequest,
    readyQuery: String?,
    onPageReady: (String) -> Unit,
    setPhase: (WebBridgePhase) -> Unit
) {
    if (readyQuery != null) {
        NativeWebRequestStore.markHandled(request)
        setPhase(WebBridgePhase.IDLE)
        onPageReady(readyQuery)
    }
}

private fun toSearchUrl(query: String): String {
    val encoded = java.net.URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
    return "$BRAVE_SEARCH_URL$encoded"
}

private fun selectFirstUsefulResult(webView: WebView, onSelected: (String?) -> Unit) {
    val script = """
        (function() {
            var links = Array.prototype.slice.call(document.querySelectorAll('a'));
            for (var i = 0; i < links.length; i++) {
                var href = links[i].href || '';
                if (!href) continue;
                if (href.indexOf('javascript:') === 0) continue;
                if (href.indexOf('search.brave.com/search') >= 0) continue;
                if (href.indexOf('search.brave.com/images') >= 0) continue;
                if (href.indexOf('search.brave.com/videos') >= 0) continue;
                if (href.indexOf('search.brave.com/news') >= 0) continue;
                if (href.indexOf('search.brave.com/maps') >= 0) continue;
                var match = href.match(/[?&]q=([^&]+)/) || href.match(/[?&]url=([^&]+)/);
                if (match && match[1]) {
                    return decodeURIComponent(match[1]);
                }
                if (href.indexOf('http://') === 0 || href.indexOf('https://') === 0) {
                    return href;
                }
            }
            return '';
        })();
    """.trimIndent()
    webView.evaluateJavascript(script) { raw ->
        val selected = decodeJsString(raw).trim()
        onSelected(selected.ifBlank { null })
    }
}

private fun capturePageText(
    webView: WebView,
    request: NativeWebRequest,
    onDone: (String?) -> Unit
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
        runCatching {
            val jsonText = decodeJsString(raw)
            val json = org.json.JSONObject(jsonText)
            val text = json.optString("text").trim()
            if (text.length < 120) {
                onDone(null)
            } else {
                val contextText = buildString {
                    appendLine("FUENTE_EXTERNA")
                    appendLine("modo=web_nativa_navegada")
                    appendLine("query=${request.query}")
                    appendLine("title=${json.optString("title")}")
                    appendLine("url=${json.optString("url")}")
                    appendLine("content:")
                    appendLine(text.take(MAX_CAPTURED_TEXT_CHARS))
                }
                NativeWebContextStore.update(
                    NativeWebPageContext(
                        title = json.optString("title"),
                        url = json.optString("url"),
                        text = contextText
                    )
                )
                onDone(request.query)
            }
        }.onFailure {
            onDone(null)
        }
    }
}

private fun decodeJsString(raw: String?): String {
    val value = raw?.takeIf { it != "null" } ?: return ""
    return JSONArray("[$value]").getString(0)
}

private const val BRAVE_HOME_URL = "https://search.brave.com/"
private const val BRAVE_SEARCH_URL = "https://search.brave.com/search?q="
private const val MAX_CAPTURED_TEXT_CHARS = 10_000
