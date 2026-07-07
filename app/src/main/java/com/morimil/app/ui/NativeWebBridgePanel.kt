package com.morimil.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NativeWebBridgePanel(
    modifier: Modifier = Modifier,
    onPageReady: (String) -> Unit
) {
    val pendingRequest by NativeWebRequestStore.pendingRequest.collectAsStateWithLifecycle()
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var activeRequest by remember { mutableStateOf<NativeWebRequest?>(null) }
    var phase by remember { mutableStateOf(WebBridgePhase.IDLE) }
    var status by remember { mutableStateOf("Panel web listo.") }

    LaunchedEffect(pendingRequest) {
        val request = pendingRequest ?: return@LaunchedEffect
        activeRequest = request
        phase = WebBridgePhase.SEARCH_RESULTS
        status = "Buscando: ${request.query.take(120)}"
        activeWebView?.loadUrl(toSearchUrl(request.query))
    }

    DisposableEffect(Unit) {
        onDispose {
            activeWebView?.destroy()
            activeWebView = null
        }
    }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Web de Morimil", style = MaterialTheme.typography.titleSmall)
            Text(status, style = MaterialTheme.typography.bodySmall)
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                factory = { context ->
                    WebView(context).apply {
                        activeWebView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.setSupportMultipleWindows(false)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                status = when (phase) {
                                    WebBridgePhase.SEARCH_RESULTS -> "Leyendo resultados de busqueda..."
                                    WebBridgePhase.SOURCE_PAGE -> "Abriendo fuente seleccionada..."
                                    WebBridgePhase.IDLE -> "Cargando web..."
                                }
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                val request = activeRequest ?: return
                                when (phase) {
                                    WebBridgePhase.SEARCH_RESULTS -> {
                                        selectFirstUsefulResult(view) { selectedUrl ->
                                            if (selectedUrl.isNullOrBlank()) {
                                                capturePageText(view, request) { nextStatus, readyQuery ->
                                                    status = nextStatus
                                                    finishIfReady(request, readyQuery, onPageReady) { phase = it }
                                                }
                                            } else {
                                                phase = WebBridgePhase.SOURCE_PAGE
                                                status = "Fuente elegida: ${selectedUrl.take(120)}"
                                                view.loadUrl(selectedUrl)
                                            }
                                        }
                                    }
                                    WebBridgePhase.SOURCE_PAGE -> {
                                        capturePageText(view, request) { nextStatus, readyQuery ->
                                            status = nextStatus
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
                            ) {
                                if (request.isForMainFrame) {
                                    status = "Error web: ${error.description}"
                                }
                            }
                        }
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
    return "https://www.google.com/search?q=$encoded"
}

private fun selectFirstUsefulResult(webView: WebView, onSelected: (String?) -> Unit) {
    val script = """
        (function() {
            var links = Array.prototype.slice.call(document.querySelectorAll('a'));
            for (var i = 0; i < links.length; i++) {
                var href = links[i].href || '';
                var text = (links[i].innerText || links[i].textContent || '').trim();
                if (!href) continue;
                if (href.indexOf('/search?') >= 0) continue;
                if (href.indexOf('google.') >= 0 && href.indexOf('/url?') < 0) continue;
                if (href.indexOf('accounts.google') >= 0) continue;
                if (href.indexOf('policies.google') >= 0) continue;
                if (href.indexOf('support.google') >= 0) continue;
                if (href.indexOf('javascript:') === 0) continue;
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
    onDone: (String, String?) -> Unit
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
                onDone("Pagina abierta, texto insuficiente todavia.", null)
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
                onDone("Fuente leida: ${text.length} caracteres.", request.query)
            }
        }.onFailure { error ->
            onDone("No se pudo capturar web: ${error.message ?: error::class.java.simpleName}", null)
        }
    }
}

private fun decodeJsString(raw: String?): String {
    val value = raw?.takeIf { it != "null" } ?: return ""
    return JSONArray("[$value]").getString(0)
}

private const val MAX_CAPTURED_TEXT_CHARS = 10_000
