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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.web.NativeWebRequest
import com.morimil.app.web.NativeWebRequestStore
import com.morimil.app.web.NativeWebSearchAuditEntry
import com.morimil.app.web.NativeWebSearchAuditStore
import com.morimil.app.web.WebSearchIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.UUID

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
    val auditScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var activeRequest by remember { mutableStateOf<NativeWebRequest?>(null) }
    var selectedSource by remember { mutableStateOf<NativeSelectedWebSource?>(null) }
    var candidateSources by remember { mutableStateOf(emptyList<NativeSelectedWebSource>()) }
    var rejectedSourceUrls by remember { mutableStateOf(emptySet<String>()) }
    var primaryCapture by remember { mutableStateOf<NativeWebCapture?>(null) }
    var retryCount by remember { mutableStateOf(0) }
    var navigationEvents by remember { mutableStateOf(emptyList<NativeNavigationEvent>()) }
    var phase by remember { mutableStateOf(WebBridgePhase.IDLE) }
    var currentUrl by remember { mutableStateOf(BRAVE_HOME_URL) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isWebMinimized by remember { mutableStateOf(false) }
    var webStatus by remember { mutableStateOf("Listo. Sin busqueda activa.") }

    fun refreshNavigationState(webView: WebView) {
        currentUrl = webView.url ?: currentUrl
        canGoBack = webView.canGoBack()
        canGoForward = webView.canGoForward()
    }

    fun resetNavigationTrace(request: NativeWebRequest) {
        navigationEvents = NativeWebNavigationTrace.started(
            request = request,
            searchUrl = toSearchUrl(request.searchQuery)
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
        navigationEvents = NativeWebNavigationTrace.append(
            events = navigationEvents,
            type = type,
            detail = detail,
            url = url,
            title = title,
            host = host,
            score = score,
            reason = reason
        )
    }

    fun writeSearchAudit(
        request: NativeWebRequest,
        primary: NativeWebCapture?,
        secondary: NativeWebCapture?,
        verifier: NativeMultiSourceVerification,
        retryCount: Int
    ) {
        val context = activeWebView?.context?.applicationContext ?: return
        val fallbackCount = navigationEvents.count { it.type == "SOURCE_FALLBACK" }
        val result = when {
            primary != null && secondary != null -> "primary_secondary_captured"
            primary != null -> "primary_captured"
            secondary != null -> "secondary_captured"
            else -> "capture_failed"
        }
        val entry = NativeWebSearchAuditEntry(
            auditId = "web-${request.requestedAtMillis}-${UUID.randomUUID()}",
            queryOriginal = request.query,
            querySearch = request.searchQuery,
            intent = request.intent.name,
            strategy = request.strategy,
            primaryUrl = primary?.url,
            primaryHost = primary?.source?.host,
            primaryScore = primary?.source?.score,
            primaryReason = primary?.source?.reason,
            secondaryUrl = secondary?.url,
            secondaryHost = secondary?.source?.host,
            secondaryScore = secondary?.source?.score,
            secondaryReason = secondary?.source?.reason,
            verifierStatus = verifier.status,
            verifierConfidence = verifier.confidence,
            verifierReason = verifier.reason,
            retryCount = retryCount,
            fallbackCount = fallbackCount,
            navigationEventCount = navigationEvents.size,
            result = result,
            createdAtMillis = System.currentTimeMillis()
        )
        auditScope.launch(Dispatchers.IO) {
            runCatching { NativeWebSearchAuditStore.append(context, entry) }
        }
    }

    fun openFallbackCandidate(
        webView: WebView,
        request: NativeWebRequest,
        failedSource: NativeSelectedWebSource?,
        reason: String
    ): Boolean {
        val failedUrl = failedSource?.url
        val blockedUrls = if (failedUrl != null) rejectedSourceUrls + failedUrl else rejectedSourceUrls
        rejectedSourceUrls = blockedUrls
        val fallback = candidateSources.firstOrNull { candidate ->
            candidate.url !in blockedUrls && candidate.url != failedUrl
        } ?: return false
        selectedSource = fallback
        phase = WebBridgePhase.PRIMARY_SOURCE_PAGE
        webStatus = "Probando fuente alternativa: ${fallback.host}."
        recordNavigationEvent(
            request = request,
            type = "SOURCE_FALLBACK",
            detail = "fuente alternativa por $reason",
            url = fallback.url,
            title = fallback.title,
            host = fallback.host,
            score = fallback.score,
            reason = "${fallback.reason}; fallback_reason=$reason"
        )
        webView.loadUrl(fallback.url)
        return true
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
        rejectedSourceUrls = emptySet()
        primaryCapture = null
        phase = WebBridgePhase.SEARCH_RESULTS
        webStatus = "Reintentando busqueda ($retryCount): $reason"
        isWebMinimized = false
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
            navigationTrace = NativeWebNavigationTrace.text(request, navigationEvents),
            verifier = verifier,
            retryCount = retryCount
        )
        writeSearchAudit(
            request = request,
            primary = primary,
            secondary = secondary,
            verifier = verifier,
            retryCount = retryCount
        )
        NativeWebRequestStore.markHandled(request)
        phase = WebBridgePhase.IDLE
        webStatus = "Contexto listo para Morimil."
        onPageReady(request.query)
    }

    LaunchedEffect(pendingRequest) {
        val request = pendingRequest ?: return@LaunchedEffect
        activeRequest = request
        selectedSource = null
        candidateSources = emptyList()
        rejectedSourceUrls = emptySet()
        primaryCapture = null
        retryCount = 0
        resetNavigationTrace(request)
        phase = WebBridgePhase.SEARCH_RESULTS
        isWebMinimized = false
        webStatus = "Buscando: ${request.searchQuery}"
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
                .background(NativeWebWindowColor)
        ) {
            NativeWebBrowserChrome(
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
                    webStatus = "Recargando pagina."
                    isWebMinimized = false
                    activeWebView?.reload()
                },
                onHome = {
                    activeRequest = null
                    selectedSource = null
                    candidateSources = emptyList()
                    rejectedSourceUrls = emptySet()
                    primaryCapture = null
                    retryCount = 0
                    navigationEvents = emptyList()
                    phase = WebBridgePhase.IDLE
                    webStatus = "Listo. Sin busqueda activa."
                    isWebMinimized = false
                    activeWebView?.loadUrl(BRAVE_HOME_URL)
                }
            )
            NativeWebStatusStrip(
                statusText = webStatus,
                isMinimized = isWebMinimized,
                isKeyboardVisible = isKeyboardVisible,
                onToggleMinimized = { isWebMinimized = !isWebMinimized }
            )
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        when {
                            isWebMinimized -> Modifier.height(1.dp)
                            isKeyboardVisible -> Modifier.height(WEBVIEW_KEYBOARD_HEIGHT)
                            else -> Modifier.aspectRatio(16f / 9f)
                        }
                    )
                    .alpha(if (isWebMinimized) 0f else 1f),
                factory = { context ->
                    WebView(context).apply {
                        activeWebView = this
                        configureAsDesktopBrowser()
                        setOnTouchListener { _, _ -> true }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                currentUrl = url ?: currentUrl
                                webStatus = when (phase) {
                                    WebBridgePhase.SEARCH_RESULTS -> "Buscando resultados en Brave."
                                    WebBridgePhase.PRIMARY_SOURCE_PAGE -> "Abriendo fuente primaria${selectedSource?.host?.let { ": $it" } ?: ""}."
                                    WebBridgePhase.SECONDARY_SOURCE_PAGE -> "Abriendo fuente secundaria${selectedSource?.host?.let { ": $it" } ?: ""}."
                                    WebBridgePhase.IDLE -> "Cargando pagina."
                                }
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
                                        webStatus = "Seleccionando fuente util."
                                        selectBestUsefulResults(view, request) { selected ->
                                            val primary = selected.firstOrNull()
                                            if (primary == null) {
                                                webStatus = "Sin candidato util; evaluando reintento."
                                                recordNavigationEvent(
                                                    request = request,
                                                    type = "NO_USEFUL_CANDIDATE",
                                                    detail = "sin candidato util; se evalua reintento"
                                                )
                                                if (attemptResearchRetry(view, request, "sin candidato util")) {
                                                    return@selectBestUsefulResults
                                                }
                                                webStatus = "Capturando resultados visibles."
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
                                                rejectedSourceUrls = emptySet()
                                                selectedSource = primary
                                                webStatus = "Fuente primaria seleccionada: ${primary.host}."
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
                                        webStatus = "Capturando fuente primaria${selectedSource?.host?.let { ": $it" } ?: ""}."
                                        NativeWebContextPublisher.capturePage(view, request, selectedSource) { capture ->
                                            if (capture == null) {
                                                val failedSource = selectedSource
                                                webStatus = "Captura primaria insuficiente."
                                                recordNavigationEvent(
                                                    request = request,
                                                    type = "CAPTURE_REJECTED",
                                                    detail = "captura primaria insuficiente",
                                                    url = failedSource?.url,
                                                    title = failedSource?.title,
                                                    host = failedSource?.host,
                                                    score = failedSource?.score,
                                                    reason = failedSource?.reason
                                                )
                                                if (openFallbackCandidate(view, request, failedSource, "captura primaria insuficiente")) {
                                                    return@capturePage
                                                }
                                                if (attemptResearchRetry(view, request, "captura primaria insuficiente")) {
                                                    return@capturePage
                                                }
                                                val verifier = NativeMultiSourceVerification(
                                                    status = "primary_capture_failed",
                                                    confidence = "LOW",
                                                    reason = "captura primaria insuficiente despues de probar fuentes alternativas y reintento"
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
                                                webStatus = "Fuente secundaria seleccionada: ${secondary.host}."
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
                                        webStatus = "Capturando fuente secundaria${selectedSource?.host?.let { ": $it" } ?: ""}."
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
                                    val nativeRequest = activeRequest
                                    if (nativeRequest != null && phase == WebBridgePhase.PRIMARY_SOURCE_PAGE) {
                                        val failedSource = selectedSource
                                        recordNavigationEvent(
                                            request = nativeRequest,
                                            type = "SOURCE_NAVIGATION_ERROR",
                                            detail = "error de navegacion en fuente primaria",
                                            url = failedSource?.url ?: request.url?.toString().orEmpty(),
                                            title = failedSource?.title,
                                            host = failedSource?.host,
                                            score = failedSource?.score,
                                            reason = failedSource?.reason
                                        )
                                        if (openFallbackCandidate(view, nativeRequest, failedSource, "error de navegacion")) {
                                            return
                                        }
                                    }
                                    isLoading = false
                                    webStatus = "Error de navegacion. Puedes refrescar o buscar otra vez."
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
                        rejectedSourceUrls = emptySet()
                        primaryCapture = null
                        retryCount = 0
                        resetNavigationTrace(request)
                        phase = WebBridgePhase.SEARCH_RESULTS
                        isWebMinimized = false
                        webStatus = "Buscando: ${request.searchQuery}"
                        webView.loadUrl(toSearchUrl(request.searchQuery))
                    }
                }
            )
        }
    }
}

@Composable
private fun NativeWebStatusStrip(
    statusText: String,
    isMinimized: Boolean,
    isKeyboardVisible: Boolean,
    onToggleMinimized: () -> Unit
) {
    val visibleStatus = if (isKeyboardVisible && !isMinimized) {
        "$statusText Teclado: compacto."
    } else {
        statusText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NativeWebWindowColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "WEB: $visibleStatus",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        TextButton(
            onClick = onToggleMinimized,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .width(34.dp)
                .height(24.dp)
        ) {
            Text(text = if (isMinimized) "▴" else "▾", fontSize = 13.sp)
        }
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
    val queryText = org.json.JSONObject.quote(request.searchQuery.lowercase())
    val script = """
        (function() {
            var intent = "$intentName";
            var searchQuery = $queryText;
            var links = Array.prototype.slice.call(document.querySelectorAll('a'));
            var candidates = [];
            var seenHosts = {};
            var blockedHosts = {
                'youtube.com': true,
                'youtu.be': true,
                'facebook.com': true,
                'instagram.com': true,
                'tiktok.com': true,
                'x.com': true,
                'twitter.com': true,
                'pinterest.com': true
            };
            var softBadHosts = {
                'medium.com': true,
                'dev.to': true,
                'hashnode.dev': true,
                'quora.com': true,
                'reddit.com': true
            };
            var stopWords = {
                'the': true, 'and': true, 'for': true, 'con': true, 'que': true, 'una': true,
                'para': true, 'como': true, 'documentation': true, 'official': true,
                'documentacion': true, 'documentación': true, 'busca': true, 'buscar': true
            };

            function cleanUrl(rawHref) {
                if (!rawHref) return '';
                var match = rawHref.match(/[?&]q=([^&]+)/) || rawHref.match(/[?&]url=([^&]+)/);
                if (match && match[1]) return decodeURIComponent(match[1]);
                return rawHref;
            }

            function hostOf(url) {
                try { return new URL(url).hostname.replace(/^www\./, ''); } catch (e) { return ''; }
            }

            function pathOf(url) {
                try { return new URL(url).pathname.toLowerCase(); } catch (e) { return ''; }
            }

            function tokenize(value) {
                return value
                    .toLowerCase()
                    .replace(/[^a-z0-9áéíóúñ_.-]+/g, ' ')
                    .split(' ')
                    .filter(function(term) { return term.length >= 3 && !stopWords[term]; });
            }

            function keywordOverlap(lower) {
                var terms = tokenize(searchQuery);
                var count = 0;
                var used = {};
                for (var i = 0; i < terms.length; i++) {
                    var term = terms[i];
                    if (!used[term] && lower.indexOf(term) >= 0) {
                        used[term] = true;
                        count += 1;
                    }
                }
                return count;
            }

            function isBlocked(url, host, text) {
                var lower = (url + ' ' + host + ' ' + text).toLowerCase();
                var path = pathOf(url);
                if (!url || !host) return true;
                if (url.indexOf('http://') !== 0 && url.indexOf('https://') !== 0) return true;
                if (host === 'search.brave.com') return true;
                if (blockedHosts[host]) return true;
                if (lower.indexOf('javascript:') === 0) return true;
                if (path.indexOf('/images') >= 0 || path.indexOf('/videos') >= 0 || path.indexOf('/maps') >= 0) return true;
                if (path.indexOf('/shopping') >= 0 || path.indexOf('/news') === 0) return true;
                if (lower.indexOf('login') >= 0 || lower.indexOf('signin') >= 0 || lower.indexOf('sign-in') >= 0) return true;
                if (lower.indexOf('signup') >= 0 || lower.indexOf('account') >= 0 || lower.indexOf('oauth') >= 0) return true;
                if (lower.indexOf('captcha') >= 0 || lower.indexOf('subscribe') >= 0) return true;
                return false;
            }

            function addReason(reasons, reason) {
                if (reasons.indexOf(reason) < 0) reasons.push(reason);
            }

            function scoreCandidate(url, host, text, index) {
                var lower = (url + ' ' + host + ' ' + text).toLowerCase();
                var path = pathOf(url);
                var overlap = keywordOverlap(lower);
                var score = 18 + Math.max(0, 20 - index);
                var reasons = ['resultado visible'];

                if (overlap > 0) {
                    score += Math.min(35, overlap * 7);
                    addReason(reasons, 'coincide con la consulta');
                }

                if (intent === 'ANDROID_CODE') {
                    if (host === 'developer.android.com') { score += 100; reasons = ['documentacion oficial Android']; }
                    else if (host === 'kotlinlang.org') { score += 75; reasons = ['documentacion oficial Kotlin']; }
                    else if (host === 'github.com') { score += 40; addReason(reasons, 'referencia tecnica GitHub'); }
                    else if (host === 'stackoverflow.com') { score += 25; addReason(reasons, 'respuesta tecnica comunitaria'); }
                } else if (intent === 'GRADLE_ERROR') {
                    if (host === 'docs.gradle.org') { score += 95; reasons = ['documentacion oficial Gradle']; }
                    else if (host === 'developer.android.com') { score += 70; reasons = ['documentacion oficial Android']; }
                    else if (host === 'stackoverflow.com') { score += 35; addReason(reasons, 'error similar resuelto'); }
                    else if (host === 'github.com') { score += 30; addReason(reasons, 'issue o codigo relacionado'); }
                } else if (intent === 'GITHUB_PROJECT') {
                    if (host === 'docs.github.com') { score += 100; reasons = ['documentacion oficial GitHub']; }
                    else if (host === 'github.com') { score += 45; addReason(reasons, 'resultado GitHub directo'); }
                } else if (intent === 'DOCUMENTATION') {
                    if (host.indexOf('docs.') === 0 || path.indexOf('/docs') >= 0 || lower.indexOf('documentation') >= 0) {
                        score += 70;
                        addReason(reasons, 'fuente documental');
                    }
                }

                if (host.indexOf('docs.') === 0 || path.indexOf('/docs') >= 0) {
                    score += 20;
                    addReason(reasons, 'ruta de documentacion');
                }
                if (lower.indexOf('official') >= 0 || lower.indexOf('oficial') >= 0) {
                    score += 12;
                    addReason(reasons, 'marcada como oficial');
                }
                if (text.length < 12 && host !== 'developer.android.com' && host !== 'docs.github.com' && host !== 'docs.gradle.org') {
                    score -= 18;
                    addReason(reasons, 'texto visible corto');
                }
                if (softBadHosts[host] || host.indexOf('medium.com') > 0) {
                    score -= 25;
                    addReason(reasons, 'fuente secundaria no oficial');
                }
                if (host === 'stackoverflow.com') {
                    score -= 8;
                    addReason(reasons, 'comunidad, no fuente primaria');
                }
                if (lower.indexOf('sponsored') >= 0 || lower.indexOf(' ad ') >= 0 || lower.indexOf('anuncio') >= 0) {
                    score -= 45;
                    addReason(reasons, 'posible anuncio');
                }

                return { score: score, reason: reasons.join('; ') };
            }

            for (var i = 0; i < links.length; i++) {
                var text = (links[i].innerText || links[i].textContent || '').trim();
                var url = cleanUrl(links[i].href || '');
                var host = hostOf(url);
                if (isBlocked(url, host, text)) continue;

                var scored = scoreCandidate(url, host, text, i);
                if (scored.score < 28) continue;

                var hostKey = host;
                if (seenHosts[hostKey] && seenHosts[hostKey] >= scored.score) continue;
                seenHosts[hostKey] = scored.score;

                var candidate = {
                    url: url,
                    title: text.substring(0, 160),
                    host: host,
                    score: scored.score,
                    reason: scored.reason
                };
                var duplicateIndex = -1;
                for (var j = 0; j < candidates.length; j++) {
                    if (candidates[j].host === candidate.host) duplicateIndex = j;
                }
                if (duplicateIndex >= 0) {
                    if (candidate.score > candidates[duplicateIndex].score) candidates[duplicateIndex] = candidate;
                } else {
                    candidates.push(candidate);
                }
            }

            candidates.sort(function(a, b) { return b.score - a.score; });
            return JSON.stringify({ candidates: candidates.slice(0, 5) });
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

private fun decodeJsString(raw: String?): String {
    val value = raw?.takeIf { it != "null" } ?: return ""
    return JSONArray("[$value]").getString(0)
}

private const val BRAVE_HOME_URL = "about:blank"
private const val BRAVE_SEARCH_URL = "https://search.brave.com/search?q="
private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
private const val MAX_RESEARCH_RETRIES = 1
private const val MAX_RETRY_QUERY_CHARS = 240
private val WEBVIEW_KEYBOARD_HEIGHT = 112.dp
