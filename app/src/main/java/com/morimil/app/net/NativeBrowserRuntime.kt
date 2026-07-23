package com.morimil.app.net

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

object NativeBrowserRuntime {
    @Volatile
    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun renderedFetcher(): NetRenderedFetcher {
        return NetRenderedFetcher { rawUrl ->
            val context = appContext ?: return@NetRenderedFetcher NetRenderedResult(
                ok = false,
                error = "browser_context_missing"
            )
            NativeBrowserReader(context).read(rawUrl)
        }
    }
}

internal class NativeBrowserReader(
    private val context: Context,
    private val timeoutMillis: Long = 25_000L,
    private val settleDelayMillis: Long = 1_000L,
    private val documentLoader: SafeWebDocumentLoader = SafeWebDocumentLoader()
) {
    suspend fun read(rawUrl: String): NetRenderedResult {
        val initialPolicy = NetSourcePolicy.validateUrl(rawUrl)
        if (!initialPolicy.allowed) {
            return NetRenderedResult(ok = false, error = "browser_source_denied:${initialPolicy.reason}")
        }

        val fetched = withContext(Dispatchers.IO) { documentLoader.fetch(rawUrl) }
        val document = fetched.document
            ?: return NetRenderedResult(
                ok = false,
                error = "browser_fetch_failed:${fetched.error.orEmpty().take(160)}"
            )
        return renderIsolated(document)
    }

    private suspend fun renderIsolated(document: SafeWebDocument): NetRenderedResult =
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                var finished = false
                var webView: WebView? = null
                var timeout: Runnable? = null

                fun complete(result: NetRenderedResult) {
                    if (finished) return
                    finished = true
                    timeout?.let(handler::removeCallbacks)
                    runCatching {
                        webView?.stopLoading()
                        webView?.destroy()
                    }
                    webView = null
                    if (continuation.isActive) continuation.resume(result)
                }

                timeout = Runnable {
                    complete(NetRenderedResult(ok = false, error = "browser_timeout"))
                }

                continuation.invokeOnCancellation {
                    handler.post {
                        complete(NetRenderedResult(ok = false, error = "browser_cancelled"))
                    }
                }

                webView = WebView(context).apply {
                    configureForIsolatedResearch()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            if (request?.isForMainFrame == true) {
                                complete(NetRenderedResult(ok = false, error = "browser_navigation_blocked"))
                            }
                            return true
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse {
                            return blockedWebResponse("isolated_browser_network_denied")
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                complete(
                                    NetRenderedResult(
                                        ok = false,
                                        error = "browser_main_frame_error:${error?.description?.toString().orEmpty()}"
                                    )
                                )
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            val loadedView = view ?: return
                            val finalPolicy = NetSourcePolicy.validateUrl(document.finalUrl)
                            if (!finalPolicy.allowed) {
                                complete(
                                    NetRenderedResult(
                                        ok = false,
                                        error = "browser_final_url_denied:${finalPolicy.reason}"
                                    )
                                )
                                return
                            }
                            handler.postDelayed({
                                if (finished) return@postDelayed
                                extractVisibleText(loadedView) { text ->
                                    complete(
                                        NetRenderedResult(
                                            ok = text.isNotBlank(),
                                            text = text,
                                            error = if (text.isBlank()) "browser_empty_text" else null
                                        )
                                    )
                                }
                            }, settleDelayMillis)
                        }
                    }
                }

                timeout?.let { handler.postDelayed(it, timeoutMillis) }
                runCatching {
                    webView?.loadDataWithBaseURL(
                        document.finalUrl,
                        document.html,
                        "text/html",
                        Charsets.UTF_8.name(),
                        document.finalUrl
                    )
                }.onFailure { error ->
                    complete(
                        NetRenderedResult(
                            ok = false,
                            error = "browser_load_error:${error::class.java.simpleName}:${error.message.orEmpty()}"
                        )
                    )
                }
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.configureForIsolatedResearch() {
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        settings.javaScriptEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.domStorageEnabled = false
        settings.loadsImagesAutomatically = false
        settings.blockNetworkImage = true
        settings.blockNetworkLoads = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.mediaPlaybackRequiresUserGesture = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.safeBrowsingEnabled = true
    }

    private fun extractVisibleText(webView: WebView, callback: (String) -> Unit) {
        val script = """
            (function() {
                var title = (document.title || '').slice(0, 500);
                var body = document.body ? document.body.innerText : '';
                return (title + '\n' + body).trim().slice(0, $MAX_RENDERED_TEXT_CHARS);
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { encoded ->
            callback(decodeJavascriptString(encoded).trim().take(MAX_RENDERED_TEXT_CHARS))
        }
    }

    private fun decodeJavascriptString(value: String?): String {
        if (value.isNullOrBlank() || value == "null") return ""
        return runCatching {
            JSONObject("{\"value\":$value}").optString("value")
        }.getOrDefault(value.trim('"'))
    }

    companion object {
        private const val MAX_RENDERED_TEXT_CHARS = 40_000
    }
}

internal fun blockedWebResponse(reason: String): WebResourceResponse {
    return WebResourceResponse(
        "text/plain",
        Charsets.UTF_8.name(),
        403,
        "Blocked",
        mapOf("X-Morimil-Block-Reason" to reason.take(120)),
        ByteArrayInputStream(ByteArray(0))
    )
}

data class NetRenderedResult(
    val ok: Boolean,
    val text: String = "",
    val error: String? = null
)

fun interface NetRenderedFetcher {
    suspend fun fetch(rawUrl: String): NetRenderedResult
}
