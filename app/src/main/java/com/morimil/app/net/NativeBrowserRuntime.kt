package com.morimil.app.net

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.net.URL
import kotlin.coroutines.resume

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

class NativeBrowserReader(
    private val context: Context,
    private val timeoutMillis: Long = 25_000L,
    private val settleDelayMillis: Long = 1_000L
) {
    suspend fun read(rawUrl: String): NetRenderedResult = suspendCancellableCoroutine { continuation ->
        val parsed = runCatching { URL(rawUrl) }.getOrNull()
        if (parsed?.protocol != "https") {
            continuation.resume(NetRenderedResult(ok = false, error = "browser_non_https_source"))
            return@suspendCancellableCoroutine
        }

        val handler = Handler(Looper.getMainLooper())
        handler.post {
            var finished = false
            var webView: WebView? = null

            fun complete(result: NetRenderedResult) {
                if (finished) return
                finished = true
                runCatching {
                    webView?.stopLoading()
                    webView?.destroy()
                }
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            val timeout = Runnable {
                complete(NetRenderedResult(ok = false, error = "browser_timeout"))
            }

            continuation.invokeOnCancellation {
                handler.post {
                    runCatching {
                        webView?.stopLoading()
                        webView?.destroy()
                    }
                }
            }

            webView = WebView(context).apply {
                configureForReadOnlyResearch()
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) {
                            complete(
                                NetRenderedResult(
                                    ok = false,
                                    error = "browser_main_frame_error:${error?.description.orEmpty()}"
                                )
                            )
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        val loadedView = view ?: return
                        handler.postDelayed({
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

            handler.postDelayed(timeout, timeoutMillis)
            runCatching { webView?.loadUrl(rawUrl) }
                .onFailure { error ->
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
    private fun WebView.configureForReadOnlyResearch() {
        CookieManager.getInstance().setAcceptCookie(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = false
        settings.blockNetworkImage = true
        settings.mediaPlaybackRequiresUserGesture = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }

    private fun extractVisibleText(webView: WebView, callback: (String) -> Unit) {
        val script = """
            (function() {
                var title = document.title || '';
                var body = document.body ? document.body.innerText : '';
                return (title + '\n' + body).trim();
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

data class NetRenderedResult(
    val ok: Boolean,
    val text: String = "",
    val error: String? = null
)

fun interface NetRenderedFetcher {
    suspend fun fetch(rawUrl: String): NetRenderedResult
}
