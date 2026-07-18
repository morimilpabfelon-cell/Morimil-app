package com.morimil.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.util.Base64
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewAssetLoader
import com.morimil.app.canvas.MorimilCanvasBridge
import com.morimil.app.canvas.MorimilCanvasBundleMetadata
import com.morimil.app.canvas.MorimilCanvasBundleVerifier
import com.morimil.app.canvas.MorimilCanvasCommandStore
import com.morimil.app.canvas.MorimilCanvasContract
import com.morimil.app.canvas.MorimilCanvasEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

private data class PendingCanvasExport(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MorimilCanvasScreen() {
    val context = LocalContext.current
    val pendingCommand by MorimilCanvasCommandStore.pending.collectAsStateWithLifecycle()
    var bundleMetadata by remember { mutableStateOf<MorimilCanvasBundleMetadata?>(null) }
    var bundleError by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Verificando Morimil Canvas…") }
    var bridgeReady by remember { mutableStateOf(false) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var activeBridge by remember { mutableStateOf<MorimilCanvasBridge?>(null) }
    var pendingExport by remember { mutableStateOf<PendingCanvasExport?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val export = pendingExport
        val destination = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && export != null && destination != null) {
            runCatching {
                context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                    output.write(export.bytes)
                } ?: error("No se pudo abrir el archivo de destino")
            }.onSuccess {
                status = "Exportado: ${export.fileName}"
            }.onFailure { error ->
                status = "Error al exportar: ${error.message ?: error::class.java.simpleName}"
            }
        } else if (export != null) {
            status = "Exportación cancelada"
        }
        pendingExport = null
    }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            MorimilCanvasBundleVerifier.verify(context.assets)
        }
        result.onSuccess { metadata ->
            bundleMetadata = metadata
            status = "Canvas ${metadata.version} verificado"
        }.onFailure { error ->
            bundleError = error.message ?: error::class.java.simpleName
            status = "Morimil Canvas bloqueado"
        }
    }

    LaunchedEffect(pendingCommand, bridgeReady, activeBridge) {
        val request = pendingCommand ?: return@LaunchedEffect
        val bridge = activeBridge ?: return@LaunchedEffect
        if (!bridgeReady) return@LaunchedEffect
        runCatching { bridge.send(request.commandJson) }
            .onFailure { error ->
                status = "Comando rechazado: ${error.message ?: error::class.java.simpleName}"
            }
        MorimilCanvasCommandStore.markHandled(request)
    }

    DisposableEffect(Unit) {
        onDispose {
            activeBridge?.close()
            activeBridge = null
            activeWebView?.destroy()
            activeWebView = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Morimil Canvas", style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            bundleMetadata?.let { metadata ->
                Text(
                    "${metadata.fileCount} archivos · ${metadata.totalBytes / 1024} KB",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        when {
            bundleError != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No se cargó el lienzo", style = MaterialTheme.typography.titleMedium)
                    Text(bundleError.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            }
            bundleMetadata == null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                val assetLoader = remember {
                    WebViewAssetLoader.Builder()
                        .addPathHandler(
                            "/assets/",
                            WebViewAssetLoader.AssetsPathHandler(context)
                        )
                        .build()
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { webContext ->
                        WebView(webContext).apply {
                            activeWebView = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.setSupportMultipleWindows(false)
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.mediaPlaybackRequiresUserGesture = true

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): WebResourceResponse? {
                                    val localResponse = assetLoader.shouldInterceptRequest(request.url)
                                    if (localResponse != null) return localResponse
                                    return if (request.url.scheme in setOf("http", "https")) {
                                        WebResourceResponse(
                                            "text/plain",
                                            "utf-8",
                                            403,
                                            "Blocked",
                                            emptyMap(),
                                            ByteArrayInputStream(ByteArray(0))
                                        )
                                    } else {
                                        null
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): Boolean {
                                    return !request.url.toString().startsWith(
                                        "${MorimilCanvasContract.APP_ORIGIN}/assets/${MorimilCanvasContract.ASSET_ROOT}/"
                                    )
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    status = "Lienzo local cargado"
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError
                                ) {
                                    if (request.isForMainFrame) {
                                        status = "Error del lienzo: ${error.description}"
                                    }
                                }
                            }

                            val bridge = MorimilCanvasBridge(
                                webView = this,
                                onEvent = { event ->
                                    handleCanvasEvent(
                                        event = event,
                                        onStatus = { status = it },
                                        onReady = { bridgeReady = true },
                                        onExport = { export ->
                                            val allowedMimeTypes = setOf(
                                                "image/png",
                                                "image/svg+xml",
                                                "application/vnd.excalidraw+json"
                                            )
                                            require(export.mimeType in allowedMimeTypes) {
                                                "Tipo de exportación no permitido"
                                            }
                                            val bytes = Base64.decode(export.base64, Base64.DEFAULT)
                                            require(bytes.size == export.declaredSize) {
                                                "El tamaño exportado no coincide"
                                            }
                                            pendingExport = PendingCanvasExport(
                                                fileName = export.fileName,
                                                mimeType = export.mimeType,
                                                bytes = bytes
                                            )
                                            exportLauncher.launch(
                                                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                                    addCategory(Intent.CATEGORY_OPENABLE)
                                                    type = export.mimeType
                                                    putExtra(Intent.EXTRA_TITLE, export.fileName)
                                                }
                                            )
                                        }
                                    )
                                },
                                onFailure = { error ->
                                    status = "Puente rechazado: ${error.message ?: error::class.java.simpleName}"
                                }
                            )
                            bridge.install()
                            activeBridge = bridge
                            loadUrl(MorimilCanvasContract.APP_URL)
                        }
                    },
                    update = { webView ->
                        if (webView.url.isNullOrBlank()) {
                            webView.loadUrl(MorimilCanvasContract.APP_URL)
                        }
                    }
                )
            }
        }
    }
}

private fun handleCanvasEvent(
    event: MorimilCanvasEvent,
    onStatus: (String) -> Unit,
    onReady: () -> Unit,
    onExport: (com.morimil.app.canvas.MorimilCanvasExport) -> Unit
) {
    runCatching {
        when (event.type) {
            "ready" -> {
                onReady()
                onStatus("Morimil Canvas listo")
            }
            "changed" -> onStatus("Cambios locales")
            "saved" -> onStatus("Guardado local")
            "command_result" -> onStatus("Comando completado")
            "error" -> onStatus("Error: ${event.error ?: "desconocido"}")
            "export_ready" -> onExport(MorimilCanvasContract.parseExport(event))
        }
    }.onFailure { error ->
        onStatus("Evento rechazado: ${error.message ?: error::class.java.simpleName}")
    }
}
