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
import com.morimil.app.web.NativeWebRequest
import com.morimil.app.web.NativeWebRequestStore
import com.morimil.app.web.WebSearchIntent
import org.json.JSONArray

private enum class WebBridgePhase {
    IDLE,
    SEARCH_RESULTS,
    PRIMARY_SOURCE_PAGE,
    SECONDARY_SOURCE_PAGE
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
    var candidateSources by remember { mutableStateOf(emptyList<NativeSelectedWebSource>()) }
    var primaryCapture by remember { mutableStateOf<NativeWebCapture?>(null) }
    var retryCount by remember { mutableStateOf(0) }
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

    fun attemptResearchRetry(
        webView: WebView,
        request: NativeWebRequest,
        reason: String
    ): Boolean {
        if (retryCount >= MAX_RESEARCH_RETRIES) return false
        retryCount += 1
        val retryQuery = retrySearchQuery(request = request, retryCount = retryCount)
        val retryUrl = toSearchUrl(retryQuery)
        selectedSource = null
        candidateSources = emptyList()
        primaryCapture = null
        phase = WebBridgePhase.SEARCH_RESULTS
        recordNavigationEvent(
            request = request,
            type = "RESEARCH_RETRY",
            detail = "reintento de investigacion $retryCount",
            url = retryUrl,
            reason = reason
        )
        webView.loadUrl(retryUrl)
        return true
    }

    fun finishWithContext(
        request: NativeWebRequest,
        primary: NativeWebCapture?,
        secondary: NativeWebCapture?,
        verifier: NativeMultiSourceVerification
    ) {
        NativeWebContextPublisher.publishWebContext(
            request = request,
            primary = primary,
            secondary = secondary,
            navigationTrace = navigationTraceText(request, navigationEvents),
            verifier = verifier,
            retryCount = retryCount
        )
        NativeWebRequestStore.markHandled(request)
        phase = WebBridgePhase.IDLE
        onPageReady(request.query)
    }

    LaunchedEffect(pendingRequest) {
        val request = pendingRequest ?: return@LaunchedEffect
        activeRequest = request
        selectedSource = null
        candidateSources = emptyList()
        primaryCapture = null
        retryCount = 0
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
                    candidateSources = emptyList()
                    primaryCapture = null
                    retryCount = 0
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
                                        selectBestUsefulResults(view, request) { selected ->
                                            val primary = selected.firstOrNull()
                                            if (primary == null) {
                                                recordNavigationEvent(
                                                    request = request,
                                                    type = "NO_USEFUL_CANDIDATE",
                                                    detail = "sin candidato util; se evalua reintento"
                                                )
                                                if (attemptResearchRetry(view, request, "sin candidato util")) {
                                                    return@selectBestUsefulResults
                                                }
                                                NativeWebContextPublisher.capturePage(view, request, selectedSource) { capture ->
                                                    val verifier = NativeMultiSourceVerification(
                                                        status = "single_source_fallback",
                                                        confidence = capture?.confidence ?: "LOW",
                                                        reason = "sin segunda fuente seleccionable despues de reintento"
                                                    )
                                                    finishWithContext(request, capture, null, verifier)
                                                }
                                            } else {
                                                candidateSources = selected
                                                selectedSource = primary
                                                recordNavigationEvent(
                                                    request = request,
                                                    type = "SOURCE_SELECTED",
                                                    detail = "fuente primaria seleccionada",
                                                    url = primary.url,
                                                    title = primary.title,
                                                    host = primary.host,
                                                    score = primary.score,
                                                    reason = primary.reason
                                                )
                                                phase = WebBridgePhase.PRIMARY_SOURCE_PAGE
                                                view.loadUrl(primary.url)
                                            }
                                        }
                                    }
                                    WebBridgePhase.PRIMARY_SOURCE_PAGE -> {
                                        NativeWebContextPublisher.capturePage(view, request, selectedSource) { capture ->
                                            if (capture == null) {
                                                recordNavigationEvent(
                                                    request = request,
                                                    type = "CAPTURE_REJECTED",
                                                    detail = "captura primaria insuficiente"
                                                )
                                                if (attemptResearchRetry(view, request, "captura primaria insuficiente")) {
                                                    return@capturePage
                                                }
                                                val verifier = NativeMultiSourceVerification(
                                                    status = "primary_capture_failed",
                                                    confidence = "LOW",
                                                    reason = "captura primaria insuficiente despues de reintento"
                                                )
                                                finishWithContext(request, null, null, verifier)
                                                return@capturePage
                                            }

                                            val secondary = NativeWebResearchPolicy.secondaryCandidateAfter(
                                                primary = capture.source,
                                                candidates = candidateSources
                                            )
                                            val decision = NativeWebResearchPolicy.multiSourceDecision(capture, secondary)
                                            if (decision.shouldOpenSecondary && secondary != null) {
                                                primaryCapture = capture
                                                selectedSource = secondary
                                                recordNavigationEvent(
                                                    request = request,
                                                    type = "SECONDARY_SOURCE_SELECTED",
                                                    detail = "fuente secundaria seleccionada para contraste",
                                                    url = secondary.url,
                                                    title = secondary.title,
                                                    host = secondary.host,
                                                    score = secondary.score,
                                                    reason = secondary.reason
                                                )
                                                phase = WebBridgePhase.SECONDARY_SOURCE_PAGE
                                                view.loadUrl(secondary.url)
                                            } else {
                                                finishWithContext(
                                                    request = request,
                                                    primary = capture,
                                                    secondary = null,
                                                    verifier = NativeWebResearchPolicy.toVerification(decision)
                                                )
                                            }
                                        }
                                    }
                                    WebBridgePhase.SECONDARY_SOURCE_PAGE -> {
                                        NativeWebContextPublisher.capturePage(view, request, selectedSource) { secondaryCapture ->
                                            val primary = primaryCapture
                                            val verifier = NativeWebResearchPolicy.verifySources(primary, secondaryCapture)
                                            if (verifier.confidence == "LOW" && attemptResearchRetry(view, request, "verificacion multisource debil")) {
                                                return@capturePage
                                            }
                                            finishWithContext(request, primary, secondaryCapture, verifier)
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
                        selectedSource = null
                        candidateSources = emptyList()
                        primaryCapture = null
                        retryCount = 0
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

private fun toSearchUrl(query: String): String {
    val encoded = java.net.URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
    return "$BRAVE_SEARCH_URL$encoded"
}

private fun retrySearchQuery(request: NativeWebRequest, retryCount: Int): String {
    val suffix = when (request.intent) {
        WebSearchIntent.GRADLE_ERROR -> " exact error official documentation solution"
        WebSearchIntent.ANDROID_CODE -> " official Android Kotlin Compose documentation"
        WebSearchIntent.GITHUB_PROJECT -> " official GitHub documentation troubleshooting"
        WebSearchIntent.DOCUMENTATION -> " official docs reference"
        WebSearchIntent.GENERAL -> " reliable source explanation"
    }
    return "${request.searchQuery} $suffix retry $retryCount"
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_RETRY_QUERY_CHARS)
}

private fun selectBestUsefulResults(
    webView: WebView,
    request: NativeWebRequest,
    onSelected: (List<NativeSelectedWebSource>) -> Unit
) {
    val intentName = request.intent.name
    val script = """
        (function() {
            var intent = "$intentName";
            var links = Array.prototype.slice.call(document.querySelectorAll('a'));
            var candidates = [];

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
                var duplicate = candidates.some(function(existing) {
                    return existing.url === candidate.url || existing.host === candidate.host;
                });
                if (!duplicate) candidates.push(candidate);
            }

            candidates.sort(function(a, b) { return b.score - a.score; });
            return JSON.stringify({ candidates: candidates.slice(0, 3) });
        })();
    """.trimIndent()

    webView.evaluateJavascript(script) { raw ->
        val selected = runCatching {
            val jsonText = decodeJsString(raw)
            val array = org.json.JSONObject(jsonText).optJSONArray("candidates") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val source = NativeSelectedWebSource(
                        url = json.optString("url"),
                        title = json.optString("title"),
                        host = json.optString("host"),
                        score = json.optInt("score"),
                        reason = json.optString("reason")
                    )
                    if (source.url.isNotBlank()) add(source)
                }
            }
        }.getOrDefault(emptyList())
        onSelected(selected)
    }
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
private const val MAX_NAVIGATION_EVENTS = 32
private const val MAX_NAVIGATION_TRACE_EVENTS = 12
private const val MAX_NAVIGATION_TRACE_CHARS = 4_000
private const val MAX_NAVIGATION_DETAIL_CHARS = 220
private const val MAX_NAVIGATION_URL_CHARS = 420
private const val MAX_NAVIGATION_TITLE_CHARS = 180
private const val MAX_NAVIGATION_HOST_CHARS = 120
private const val MAX_NAVIGATION_REASON_CHARS = 160
private const val MAX_RESEARCH_RETRIES = 1
private const val MAX_RETRY_QUERY_CHARS = 240
