package com.morimil.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var currentUrl by remember { mutableStateOf(BRAVE_HOME_URL) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    fun refreshNavigationState(webView: WebView) {
        currentUrl = webView.url ?: currentUrl
        canGoBack = webView.canGoBack()
        canGoForward = webView.canGoForward()
    }

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

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BraveWindow)
        ) {
            BrowserChrome(
                currentUrl = currentUrl,
                isLoading = isLoading,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onBack = {
                    activeWebView?.takeIf { it.canGoBack() }?.goBack()
                },
                onForward = {
                    activeWebView?.takeIf { it.canGoForward() }?.goForward()
                },
                onRefresh = {
                    activeWebView?.reload()
                },
                onHome = {
                    activeRequest = null
                    phase = WebBridgePhase.IDLE
                    activeWebView?.loadUrl(BRAVE_HOME_URL)
                }
            )
            AndroidView(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                factory = { context ->
                    WebView(context).apply {
                        activeWebView = this
                        configureAsDesktopBrowser()
                        setOnTouchListener { _, _ -> true }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                currentUrl = url ?: currentUrl
                                refreshNavigationState(view)
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                isLoading = false
                                currentUrl = url ?: view.url ?: currentUrl
                                refreshNavigationState(view)
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
                            ) {
                                if (request.isForMainFrame) {
                                    isLoading = false
                                    refreshNavigationState(view)
                                }
                            }
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
}

@Composable
private fun BrowserChrome(
    currentUrl: String,
    isLoading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BraveWindow),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = 5.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.height(22.dp).weight(1f),
                color = BraveTabActive,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "🛡", fontSize = 9.sp, color = BraveToolbarText)
                    Text(
                        text = tabTitle(currentUrl),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = BraveToolbarText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 9.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = "×", fontSize = 10.sp, color = BraveToolbarText)
                }
            }
            BrowserChromeButton(label = "+", enabled = true, onClick = onHome)
            Spacer(modifier = Modifier.width(18.dp))
            Text(text = "−", color = BraveToolbarMuted, fontSize = 9.sp)
            Text(text = "□", color = BraveToolbarMuted, fontSize = 8.sp)
            Text(text = "×", color = BraveToolbarMuted, fontSize = 9.sp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(BraveToolbar)
                .padding(horizontal = 5.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrowserChromeButton(label = "←", enabled = canGoBack, onClick = onBack)
            BrowserChromeButton(label = "→", enabled = canGoForward, onClick = onForward)
            BrowserChromeButton(label = if (isLoading) "×" else "↻", enabled = true, onClick = onRefresh)
            Text(text = "▱", color = BraveToolbarMuted, fontSize = 10.sp)
            Surface(
                modifier = Modifier.height(22.dp).weight(1f),
                color = BraveAddress,
                shape = RoundedCornerShape(13.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(text = "🔍", color = BraveToolbarMuted, fontSize = 8.sp)
                    Text(
                        text = addressText(currentUrl),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = BraveToolbarText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 9.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = "🛡", color = BraveToolbarMuted, fontSize = 8.sp)
                    Text(text = "▴", color = BraveToolbarMuted, fontSize = 8.sp)
                }
            }
            Text(text = "☆", color = BraveToolbarMuted, fontSize = 10.sp)
            Text(text = "☰", color = BraveToolbarMuted, fontSize = 10.sp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(BraveBookmarkBar)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "▦", color = BraveToolbarMuted, fontSize = 9.sp)
            Text(
                text = "Para acceder de forma rápida, coloca marcadores aquí",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = BraveToolbarText,
                fontSize = 8.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BrowserChromeButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.width(24.dp).height(22.dp)
    ) {
        Text(text = label, fontSize = 10.sp)
    }
}

private fun WebView.configureAsDesktopBrowser() {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.setSupportMultipleWindows(false)
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.builtInZoomControls = false
    settings.displayZoomControls = false
    settings.textZoom = 100
    settings.userAgentString = DESKTOP_USER_AGENT
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
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

private fun displayUrl(url: String): String {
    return url
        .removePrefix("https://")
        .removePrefix("http://")
        .removeSuffix("/")
}

private fun addressText(url: String): String {
    val clean = displayUrl(url)
    return when {
        clean.isBlank() -> "Buscar en Brave o escribir una URL"
        clean == "search.brave.com" -> "Buscar en Brave o escribir una URL"
        else -> clean
    }
}

private fun tabTitle(url: String): String {
    val clean = displayUrl(url)
    return when {
        clean.isBlank() -> "Nueva pestaña"
        clean.startsWith("search.brave.com") -> "Nueva pestaña"
        else -> clean.substringBefore('/').ifBlank { clean }
    }
}

private fun decodeJsString(raw: String?): String {
    val value = raw?.takeIf { it != "null" } ?: return ""
    return JSONArray("[$value]").getString(0)
}

private val BraveWindow = Color(0xFF1F1F24)
private val BraveToolbar = Color(0xFF303037)
private val BraveBookmarkBar = Color(0xFF38383F)
private val BraveTabActive = Color(0xFF3B3B43)
private val BraveAddress = Color(0xFF1E1E25)
private val BraveToolbarText = Color(0xFFE8E8EC)
private val BraveToolbarMuted = Color(0xFFB7B7BE)
private const val BRAVE_HOME_URL = "https://search.brave.com/"
private const val BRAVE_SEARCH_URL = "https://search.brave.com/search?q="
private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
private const val MAX_CAPTURED_TEXT_CHARS = 10_000
