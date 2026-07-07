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
    var selectedSource by remember { mutableStateOf<NativeSelectedWebSource?>(null) }
    var navigationEvents by remember { mutableStateOf(emptyList<NativeNavigationEvent>()) }
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

    fun resetNavigationTrace(request: NativeWebRequest) {
        val searchUrl = toSearchUrl(request.searchQuery)
        val now = System.currentTimeMillis()
        navigationEvents = listOf(
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

    fun recordNavigationEvent(
        request: NativeWebRequest,
        type: String,
        detail: String,
        url: String? = null,
        title: String? = null,
        host: String? = null,
        score: Int? = null,
        reason: String? = null
    ) {
        if (activeRequest?.requestedAtMillis != request.requestedAtMillis) return
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
        navigationEvents = (navigationEvents + event).takeLast(MAX_NAVIGATION_EVENTS)
    }

    LaunchedEffect(pendingRequest) {
        val request = pendingRequest ?: return@LaunchedEffect
        activeRequest = request
        selectedSource = null
        resetNavigationTrace(request)
        phase = WebBridgePhase.SEARCH_RESULTS
        activeWebView?.loadUrl(toSearchUrl(request.searchQuery))
    }

    DisposableEffect(Unit) {
        onDispose {
            activeWebView?.destroy()
            activeWebView = null
        }
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
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
                    selectedSource = null
                    navigationEvents = emptyList()
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
                                activeRequest?.let { request ->
                                    recordNavigationEvent(
                                        request = request,
                                        type = "PAGE_STARTED",
                                        detail = "pagina iniciada",
                                        url = url.orEmpty()
                                    )
                                }
                                refreshNavigationState(view)
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                isLoading = false
                                currentUrl = url ?: view.url ?: currentUrl
                                refreshNavigationState(view)
                                val request = activeRequest ?: return
                                when (phase) {
                                    WebBridgePhase.SEARCH_RESULTS -> {
                                        selectBestUsefulResult(view, request) { selected ->
                                            if (selected == null) {
                                                recordNavigationEvent(
                                                    request = request,
                                                    type = "NO_USEFUL_CANDIDATE",
                                                    detail = "sin candidato util; se captura la pagina actual"
                                                )
                                                capturePageText(
                                                    webView = view,
                                                    request = request,
                                                    selectedSource = selectedSource,
                                                    navigationTrace = navigationTraceText(request, navigationEvents),
                                                    onDone = { readyQuery ->
                                                        finishIfReady(request, readyQuery, onPageReady) { phase = it }
                                                    }
                                                )
                                            } else {
                                                selectedSource = selected
                                                recordNavigationEvent(
                                                    request = request,
                                                    type = "SOURCE_SELECTED",
                                                    detail = "fuente seleccionada",
                                                    url = selected.url,
                                                    title = selected.title,
                                                    host = selected.host,
                                                    score = selected.score,
                                                    reason = selected.reason
                                                )
                                                phase = WebBridgePhase.SOURCE_PAGE
                                                view.loadUrl(selected.url)
                                            }
                                        }
                                    }
                                    WebBridgePhase.SOURCE_PAGE -> {
                                        capturePageText(
                                            webView = view,
                                            request = request,
                                            selectedSource = selectedSource,
                                            navigationTrace = navigationTraceText(request, navigationEvents),
                                            onDone = { readyQuery ->
                                                finishIfReady(request, readyQuery, onPageReady) { phase = it }
                                            }
                                        )
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
                        selectedSource = null
                        resetNavigationTrace(request)
                        phase = WebBridgePhase.SEARCH_RESULTS
                        webView.loadUrl(toSearchUrl(request.searchQuery))
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
                .height(22.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.width(126.dp).height(18.dp),
                color = BraveTabActive,
                shape = RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(text = "◈", fontSize = 7.sp, color = BraveAccent)
                    Text(
                        text = tabTitle(currentUrl),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = BraveToolbarText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 8.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = "×", fontSize = 8.sp, color = BraveToolbarText)
                }
            }
            BrowserChromeButton(label = "+", enabled = true, onClick = onHome)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "−", color = BraveToolbarMuted, fontSize = 8.sp)
            Text(text = "□", color = BraveToolbarMuted, fontSize = 7.sp)
            Text(text = "×", color = BraveToolbarMuted, fontSize = 8.sp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(BraveToolbar)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrowserChromeButton(label = "‹", enabled = canGoBack, onClick = onBack)
            BrowserChromeButton(label = "›", enabled = canGoForward, onClick = onForward)
            BrowserChromeButton(label = if (isLoading) "×" else "⟳", enabled = true, onClick = onRefresh)
            Text(text = "▱", color = BraveToolbarMuted, fontSize = 8.sp)
            Surface(
                modifier = Modifier.height(18.dp).weight(1f),
                color = BraveAddress,
                shape = RoundedCornerShape(9.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "⌕", color = BraveToolbarMuted, fontSize = 7.sp)
                    Text(
                        text = addressText(currentUrl),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = BraveToolbarText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 8.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = "◈", color = BraveAccent, fontSize = 7.sp)
                    Text(text = "▴", color = BraveToolbarMuted, fontSize = 7.sp)
                }
            }
            Text(text = "☆", color = BraveToolbarMuted, fontSize = 8.sp)
            Text(text = "≡", color = BraveToolbarMuted, fontSize = 8.sp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(BraveBookmarkBar)
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "▦", color = BraveToolbarMuted, fontSize = 7.sp)
            Text(
                text = "Para acceder de forma rápida, coloca marcadores aquí",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = BraveToolbarText,
                fontSize = 6.sp,
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
        modifier = Modifier.width(18.dp).height(18.dp)
    ) {
        Text(text = label, fontSize = 8.sp)
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
    settings.textZoom = 92
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

private fun selectBestUsefulResult(
    webView: WebView,
    request: NativeWebRequest,
    onSelected: (NativeSelectedWebSource?) -> Unit
) {
    val intentName = request.intent.name
    val script = """
        (function() {
            var intent = "$intentName";
            var links = Array.prototype.slice.call(document.querySelectorAll('a'));
            var best = null;

            function cleanUrl(rawHref) {
                if (!rawHref) return '';
                var match = rawHref.match(/[?&]q=([^&]+)/) || rawHref.match(/[?&]url=([^&]+)/);
                if (match && match[1]) return decodeURIComponent(match[1]);
                return rawHref;
            }

            function hostOf(url) {
                try { return new URL(url).hostname.replace(/^www\./, ''); } catch (e) { return ''; }
            }

            function isBlocked(url, host, text) {
                var lower = (url + ' ' + host + ' ' + text).toLowerCase();
                if (!url) return true;
                if (url.indexOf('http://') !== 0 && url.indexOf('https://') !== 0) return true;
                if (host === 'search.brave.com') return true;
                if (lower.indexOf('javascript:') === 0) return true;
                if (lower.indexOf('/images') >= 0 || lower.indexOf('/videos') >= 0 || lower.indexOf('/maps') >= 0) return true;
                if (lower.indexOf('login') >= 0 || lower.indexOf('signin') >= 0 || lower.indexOf('account') >= 0) return true;
                return false;
            }

            function scoreCandidate(url, host, text, index) {
                var lower = (url + ' ' + host + ' ' + text).toLowerCase();
                var score = 20 + Math.max(0, 18 - index);
                var reason = 'resultado visible';

                if (intent === 'ANDROID_CODE') {
                    if (host === 'developer.android.com') { score += 90; reason = 'documentacion oficial Android'; }
                    else if (host === 'kotlinlang.org') { score += 70; reason = 'documentacion oficial Kotlin'; }
                    else if (host === 'github.com') { score += 45; reason = 'repositorio tecnico GitHub'; }
                    else if (host === 'stackoverflow.com') { score += 35; reason = 'respuesta tecnica comunitaria'; }
                } else if (intent === 'GRADLE_ERROR') {
                    if (host === 'docs.gradle.org') { score += 85; reason = 'documentacion oficial Gradle'; }
                    else if (host === 'developer.android.com') { score += 70; reason = 'documentacion oficial Android'; }
                    else if (host === 'stackoverflow.com') { score += 50; reason = 'error similar resuelto'; }
                    else if (host === 'github.com') { score += 40; reason = 'issue o codigo relacionado'; }
                } else if (intent === 'GITHUB_PROJECT') {
                    if (host === 'docs.github.com') { score += 90; reason = 'documentacion oficial GitHub'; }
                    else if (host === 'github.com') { score += 55; reason = 'resultado GitHub directo'; }
                } else if (intent === 'DOCUMENTATION') {
                    if (host.indexOf('docs.') === 0 || lower.indexOf('documentation') >= 0 || lower.indexOf('/docs') >= 0) {
                        score += 60;
                        reason = 'fuente documental';
                    }
                }

                if (lower.indexOf('official') >= 0) score += 15;
                if (lower.indexOf('docs') >= 0 || lower.indexOf('documentation') >= 0) score += 12;
                if (host === 'medium.com' || host.indexOf('medium.com') > 0) score -= 18;
                if (host === 'youtube.com' || host === 'youtu.be') score -= 35;
                if (host === 'facebook.com' || host === 'instagram.com' || host === 'tiktok.com' || host === 'x.com') score -= 45;
                if (lower.indexOf('sponsored') >= 0 || lower.indexOf('ad ') >= 0) score -= 30;

                return { score: score, reason: reason };
            }

            for (var i = 0; i < links.length; i++) {
                var text = (links[i].innerText || links[i].textContent || '').trim();
                var url = cleanUrl(links[i].href || '');
                var host = hostOf(url);
                if (isBlocked(url, host, text)) continue;

                var scored = scoreCandidate(url, host, text, i);
                var candidate = {
                    url: url,
                    title: text.substring(0, 160),
                    host: host,
                    score: scored.score,
                    reason: scored.reason
                };
                if (best === null || candidate.score > best.score) best = candidate;
            }

            if (best === null) return JSON.stringify({ url: '', title: '', host: '', score: 0, reason: 'sin candidato util' });
            return JSON.stringify(best);
        })();
    """.trimIndent()

    webView.evaluateJavascript(script) { raw ->
        val selected = runCatching {
            val jsonText = decodeJsString(raw)
            val json = org.json.JSONObject(jsonText)
            NativeSelectedWebSource(
                url = json.optString("url"),
                title = json.optString("title"),
                host = json.optString("host"),
                score = json.optInt("score"),
                reason = json.optString("reason")
            )
        }.getOrNull()?.takeIf { it.url.isNotBlank() }
        onSelected(selected)
    }
}

private fun capturePageText(
    webView: WebView,
    request: NativeWebRequest,
    selectedSource: NativeSelectedWebSource?,
    navigationTrace: String,
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
                    appendLine("query_original=${request.query}")
                    appendLine("query_busqueda=${request.searchQuery}")
                    appendLine("intent=${request.intent}")
                    appendLine("strategy=${request.strategy}")
                    appendLine(
                        webEvidenceGateText(
                            request = request,
                            selectedSource = selectedSource,
                            url = json.optString("url"),
                            textChars = text.length
                        )
                    )
                    appendLine(navigationTrace)
                    selectedSource?.let { source ->
                        appendLine("selected_source_url=${source.url}")
                        appendLine("selected_source_host=${source.host}")
                        appendLine("selected_source_score=${source.score}")
                        appendLine("selected_source_reason=${source.reason}")
                    }
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

private fun webEvidenceGateText(
    request: NativeWebRequest,
    selectedSource: NativeSelectedWebSource?,
    url: String,
    textChars: Int
): String {
    val host = selectedSource?.host?.ifBlank { hostFromDisplayUrl(url) } ?: hostFromDisplayUrl(url)
    val score = selectedSource?.score ?: 0
    val confidence = evidenceConfidence(host = host, score = score, textChars = textChars)
    return buildString {
        appendLine("WEB_EVIDENCE_GATE")
        appendLine("classification=external_web_evidence")
        appendLine("scope=temporary_context")
        appendLine("direct_long_term_ingest=blocked")
        appendLine("approval_required_for_long_term_ingest=true")
        appendLine("confidence=$confidence")
        appendLine("confidence_reason=${evidenceConfidenceReason(host = host, score = score, textChars = textChars)}")
        appendLine("source_host=$host")
        appendLine("source_score=$score")
        appendLine("captured_chars=$textChars")
        appendLine("intent=${request.intent}")
    }.take(MAX_EVIDENCE_GATE_CHARS)
}

private fun evidenceConfidence(host: String, score: Int, textChars: Int): String {
    val trusted = isTrustedTechnicalHost(host)
    return when {
        textChars < 400 -> "LOW"
        trusted && score >= 85 && textChars >= 900 -> "HIGH"
        trusted && score >= 65 && textChars >= 700 -> "MEDIUM"
        score >= 80 && textChars >= 1_200 -> "MEDIUM"
        else -> "LOW"
    }
}

private fun evidenceConfidenceReason(host: String, score: Int, textChars: Int): String {
    return when {
        textChars < 400 -> "captura demasiado pequena"
        isTrustedTechnicalHost(host) && score >= 85 -> "fuente tecnica prioritaria con score alto"
        isTrustedTechnicalHost(host) -> "fuente tecnica prioritaria"
        score >= 80 -> "score alto pero fuente no primaria"
        else -> "evidencia util solo como contexto temporal"
    }
}

private fun isTrustedTechnicalHost(host: String): Boolean {
    return host == "developer.android.com" ||
        host == "kotlinlang.org" ||
        host == "docs.gradle.org" ||
        host == "docs.github.com" ||
        host == "github.com" ||
        host == "stackoverflow.com"
}

private fun hostFromDisplayUrl(url: String): String {
    return displayUrl(url).substringBefore('/').removePrefix("www.")
}

private fun navigationTraceText(
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
        clean == "about:blank" -> "Buscar en Brave o escribir una URL"
        clean == "search.brave.com" -> "Buscar en Brave o escribir una URL"
        else -> clean
    }
}

private fun tabTitle(url: String): String {
    val clean = displayUrl(url)
    return when {
        clean.isBlank() -> "Nueva pestaña"
        clean == "about:blank" -> "Nueva pestaña"
        clean.startsWith("search.brave.com") -> "Nueva pestaña"
        else -> clean.substringBefore('/').ifBlank { clean }
    }
}

private fun decodeJsString(raw: String?): String {
    val value = raw?.takeIf { it != "null" } ?: return ""
    return JSONArray("[$value]").getString(0)
}

private data class NativeSelectedWebSource(
    val url: String,
    val title: String,
    val host: String,
    val score: Int,
    val reason: String
)

private data class NativeNavigationEvent(
    val type: String,
    val detail: String,
    val url: String? = null,
    val title: String? = null,
    val host: String? = null,
    val score: Int? = null,
    val reason: String? = null,
    val timestampMillis: Long
)

private val BraveWindow = Color(0xFF1E1E23)
private val BraveToolbar = Color(0xFF313138)
private val BraveBookmarkBar = Color(0xFF39393F)
private val BraveTabActive = Color(0xFF3A3A41)
private val BraveAddress = Color(0xFF1D1D23)
private val BraveToolbarText = Color(0xFFE7E7EA)
private val BraveToolbarMuted = Color(0xFFA8A8AF)
private val BraveAccent = Color(0xFFFF5A1F)
private const val BRAVE_HOME_URL = "about:blank"
private const val BRAVE_SEARCH_URL = "https://search.brave.com/search?q="
private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
private const val MAX_CAPTURED_TEXT_CHARS = 10_000
private const val MAX_NAVIGATION_EVENTS = 32
private const val MAX_NAVIGATION_TRACE_EVENTS = 12
private const val MAX_NAVIGATION_TRACE_CHARS = 4_000
private const val MAX_NAVIGATION_DETAIL_CHARS = 220
private const val MAX_NAVIGATION_URL_CHARS = 420
private const val MAX_NAVIGATION_TITLE_CHARS = 180
private const val MAX_NAVIGATION_HOST_CHARS = 120
private const val MAX_NAVIGATION_REASON_CHARS = 160
private const val MAX_EVIDENCE_GATE_CHARS = 1_200
