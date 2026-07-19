package com.morimil.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.morimil.app.net.NetSourcePolicy
import com.morimil.app.web.NativeWebContextStore
import com.morimil.app.web.NativeWebPageContext
import com.morimil.app.web.NativeWebRequestStore
import org.json.JSONArray
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NativeBrowserScreen() {
    var input by remember { mutableStateOf("https://www.google.com/search?q=Morimil") }
    var loadTarget by remember { mutableStateOf(input) }
    var status by remember { mutableStateOf("Navegador nativo listo.") }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    val capturedPage by NativeWebContextStore.currentPage.collectAsStateWithLifecycle()
    val pendingRequest by NativeWebRequestStore.pendingRequest.collectAsStateWithLifecycle()

    fun loadPublicTarget(rawValue: String) {
        val target = normalizeUrlOrSearch(rawValue)
        val decision = NetSourcePolicy.validateUrl(target)
        if (!decision.allowed) {
            status = "Navegacion bloqueada: ${decision.reason}"
            return
        }
        loadTarget = target
        activeWebView?.loadUrl(target)
    }

    LaunchedEffect(pendingRequest) {
        val request = pendingRequest ?: return@LaunchedEffect
        input = request.query
        status = "Morimil pidio buscar: ${request.query.take(120)}"
        loadPublicTarget(request.query)
        NativeWebRequestStore.markHandled(request)
    }

    DisposableEffect(Unit) {
        onDispose {
            activeWebView?.destroy()
            activeWebView = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Web nativa", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Carga una pagina publica. Solo al pulsar Capturar se guarda texto visible como contexto externo temporal para el siguiente turno.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("URL HTTPS o busqueda") }
            )
            Button(onClick = { loadPublicTarget(input) }) {
                Text("Ir")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                activeWebView?.goBack()
            }) {
                Text("Atras")
            }
            Button(onClick = {
                activeWebView?.reload()
            }) {
                Text("Recargar")
            }
            Button(onClick = {
                activeWebView?.let { view -> capturePage(view) { status = it } }
            }) {
                Text("Capturar")
            }
            Button(onClick = {
                NativeWebContextStore.clear()
                status = "Contexto web limpiado."
            }) {
                Text("Limpiar")
            }
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Contexto entregado al modelo", style = MaterialTheme.typography.titleSmall)
                Text(capturedPage?.title?.ifBlank { "Sin titulo" } ?: "Sin pagina capturada.")
                Text(capturedPage?.url ?: "Carga una pagina publica y pulsa Capturar.", style = MaterialTheme.typography.bodySmall)
                Text("caracteres=${capturedPage?.text?.length ?: 0}", style = MaterialTheme.typography.bodySmall)
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(520.dp),
            factory = { context ->
                WebView(context).apply {
                    activeWebView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.safeBrowsingEnabled = true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val target = request.url.toString()
                            val decision = NetSourcePolicy.validateUrl(target)
                            if (!decision.allowed) {
                                if (request.isForMainFrame) {
                                    status = "Navegacion bloqueada: ${decision.reason}"
                                }
                                return true
                            }
                            return false
                        }

                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val decision = NetSourcePolicy.validateUrl(request.url.toString())
                            return if (decision.allowed) {
                                null
                            } else {
                                WebResourceResponse(
                                    "text/plain",
                                    "UTF-8",
                                    ByteArrayInputStream(ByteArray(0))
                                )
                            }
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            status = "Cargando: ${url.orEmpty().take(120)}"
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            input = url.orEmpty().ifBlank { input }
                            val decision = NetSourcePolicy.validateUrl(url.orEmpty())
                            status = if (decision.allowed) {
                                "Pagina cargada. Revisa su contenido y pulsa Capturar para usarla como contexto temporal."
                            } else {
                                "Pagina no capturable: ${decision.reason}"
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
                    loadUrl(loadTarget)
                }
            },
            update = { webView ->
                if (webView.url != loadTarget && NetSourcePolicy.validateUrl(loadTarget).allowed) {
                    webView.loadUrl(loadTarget)
                }
            }
        )
    }
}

private fun normalizeUrlOrSearch(value: String): String {
    val clean = value.trim()
    if (clean.isBlank()) return "https://www.google.com"
    if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
    if (clean.contains(".") && !clean.contains(" ")) return "https://$clean"
    val encoded = java.net.URLEncoder.encode(clean, Charsets.UTF_8.name())
    return "https://www.google.com/search?q=$encoded"
}

private fun capturePage(webView: WebView, onStatus: (String) -> Unit) {
    val currentUrl = webView.url.orEmpty()
    val currentDecision = NetSourcePolicy.validateUrl(currentUrl)
    if (!currentDecision.allowed) {
        onStatus("Captura bloqueada: ${currentDecision.reason}")
        return
    }

    val script = """
        (function() {
            var title = (document.title || '').slice(0, 500);
            var text = document.body ? document.body.innerText : '';
            var url = location.href || '';
            return JSON.stringify({ title: title, url: url, text: text.slice(0, $MAX_CAPTURE_CHARS) });
        })();
    """.trimIndent()
    webView.evaluateJavascript(script) { raw ->
        runCatching {
            val jsonText = decodeJsString(raw)
            val json = org.json.JSONObject(jsonText)
            val capturedUrl = json.optString("url")
            val capturedDecision = NetSourcePolicy.validateUrl(capturedUrl)
            require(capturedDecision.allowed) { "captured_url_denied:${capturedDecision.reason}" }
            require(capturedUrl == webView.url) { "captured_url_changed" }
            val text = json.optString("text").trim().take(MAX_CAPTURE_CHARS)
            if (text.isBlank()) {
                onStatus("Pagina cargada, pero no se pudo extraer texto visible.")
            } else {
                NativeWebContextStore.update(
                    NativeWebPageContext(
                        title = json.optString("title").take(500),
                        url = capturedUrl,
                        text = text
                    )
                )
                onStatus("Pagina capturada para el chat: ${text.length} caracteres.")
            }
        }.onFailure { error ->
            onStatus("No se pudo capturar pagina: ${error.message ?: error::class.java.simpleName}")
        }
    }
}

private fun decodeJsString(raw: String?): String {
    val value = raw?.takeIf { it != "null" } ?: return "{}"
    return JSONArray("[$value]").getString(0)
}

private const val MAX_CAPTURE_CHARS = 12_000
