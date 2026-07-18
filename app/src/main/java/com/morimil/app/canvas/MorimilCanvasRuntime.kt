package com.morimil.app.canvas

import android.content.res.AssetManager
import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

object MorimilCanvasContract {
    const val ASSET_ROOT = "morimil-canvas"
    const val ENTRYPOINT = "index.html"
    const val APP_ORIGIN = "https://appassets.androidplatform.net"
    const val APP_URL = "$APP_ORIGIN/assets/$ASSET_ROOT/$ENTRYPOINT"
    const val HOST_OBJECT_NAME = "MorimilHost"
    const val BUNDLE_SCHEMA = "morimil.canvas.bundle.v1"
    const val BRIDGE_SCHEMA = "morimil.canvas.bridge.v1"
    const val EXPECTED_VERSION = "0.3.0"
    const val MAX_COMMAND_CHARS = 2_000_000
    const val MAX_EVENT_CHARS = 22_000_000
    const val MAX_EXPORT_BYTES = 15 * 1024 * 1024

    private val commandTypes = setOf(
        "get_status",
        "get_scene",
        "new_document",
        "save",
        "load_scene",
        "add_elements",
        "export"
    )

    fun validateCommand(raw: String): String {
        require(raw.length <= MAX_COMMAND_CHARS) { "Canvas command exceeds 2 MB" }
        val command = JSONObject(raw)
        val type = command.optString("type")
        require(type in commandTypes) { "Unsupported canvas command: $type" }

        when (type) {
            "new_document" -> {
                require(command.optString("title").length <= 120) { "Canvas title exceeds 120 characters" }
            }
            "load_scene" -> {
                val elements = command.optJSONArray("elements")
                    ?: throw IllegalArgumentException("load_scene requires elements")
                require(elements.length() <= 10_000) { "Canvas scene exceeds 10 000 elements" }
            }
            "add_elements" -> {
                val elements = command.optJSONArray("elements")
                    ?: throw IllegalArgumentException("add_elements requires elements")
                require(elements.length() <= 2_000) { "Canvas command exceeds 2 000 elements" }
            }
            "export" -> {
                require(command.optString("format") in setOf("png", "svg", "excalidraw")) {
                    "Unsupported canvas export format"
                }
            }
        }
        return command.toString()
    }

    fun parseEvent(raw: String): MorimilCanvasEvent {
        require(raw.length <= MAX_EVENT_CHARS) { "Canvas event exceeds the host limit" }
        val event = JSONObject(raw)
        require(event.optString("schema") == BRIDGE_SCHEMA) { "Invalid canvas bridge schema" }
        val type = event.optString("type")
        require(type in setOf("ready", "changed", "saved", "command_result", "error", "export_ready")) {
            "Unsupported canvas event: $type"
        }
        return MorimilCanvasEvent(
            type = type,
            requestId = event.optString("requestId").takeIf { it.isNotBlank() },
            documentId = event.optString("documentId").takeIf { it.isNotBlank() },
            payload = event.optJSONObject("payload"),
            error = event.optString("error").takeIf { it.isNotBlank() }
        )
    }

    fun parseExport(event: MorimilCanvasEvent): MorimilCanvasExport {
        require(event.type == "export_ready") { "Event is not an export" }
        val payload = event.payload ?: throw IllegalArgumentException("Export payload is missing")
        val fileName = sanitizeFileName(payload.optString("fileName"))
        val mimeType = payload.optString("mimeType").takeIf { it.isNotBlank() }
            ?: "application/octet-stream"
        val declaredSize = payload.optLong("size", -1L)
        require(declaredSize in 0..MAX_EXPORT_BYTES.toLong()) { "Export size is invalid" }
        val base64 = payload.optString("base64")
        require(base64.isNotBlank()) { "Export data is missing" }
        return MorimilCanvasExport(fileName, mimeType, declaredSize.toInt(), base64)
    }

    private fun sanitizeFileName(value: String): String {
        val clean = value
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "-")
            .trim('.', ' ')
            .take(120)
        return clean.ifBlank { "morimil-canvas-export" }
    }
}

data class MorimilCanvasEvent(
    val type: String,
    val requestId: String?,
    val documentId: String?,
    val payload: JSONObject?,
    val error: String?
)

data class MorimilCanvasExport(
    val fileName: String,
    val mimeType: String,
    val declaredSize: Int,
    val base64: String
)

data class MorimilCanvasBundleMetadata(
    val version: String,
    val totalBytes: Long,
    val fileCount: Int
)

data class MorimilCanvasCommandRequest(
    val sequence: Long,
    val commandJson: String
)

object MorimilCanvasCommandStore {
    private val sequence = AtomicLong(0)
    private val mutablePending = MutableStateFlow<MorimilCanvasCommandRequest?>(null)
    val pending: StateFlow<MorimilCanvasCommandRequest?> = mutablePending.asStateFlow()

    fun submit(commandJson: String): MorimilCanvasCommandRequest {
        val request = MorimilCanvasCommandRequest(
            sequence = sequence.incrementAndGet(),
            commandJson = MorimilCanvasContract.validateCommand(commandJson)
        )
        mutablePending.value = request
        return request
    }

    fun markHandled(request: MorimilCanvasCommandRequest) {
        if (mutablePending.value?.sequence == request.sequence) {
            mutablePending.value = null
        }
    }
}

object MorimilCanvasBundleVerifier {
    private const val MAX_BUNDLE_BYTES = 6L * 1024L * 1024L
    private const val MAX_FILE_COUNT = 200

    fun verify(assetManager: AssetManager): Result<MorimilCanvasBundleMetadata> = runCatching {
        val manifestPath = "${MorimilCanvasContract.ASSET_ROOT}/morimil-canvas.manifest.json"
        val manifestText = assetManager.open(manifestPath, AssetManager.ACCESS_STREAMING)
            .bufferedReader()
            .use { it.readText() }
        val manifest = JSONObject(manifestText)

        require(manifest.optString("schema") == MorimilCanvasContract.BUNDLE_SCHEMA) {
            "Invalid Morimil Canvas bundle schema"
        }
        require(manifest.optString("version") == MorimilCanvasContract.EXPECTED_VERSION) {
            "Unexpected Morimil Canvas version"
        }
        require(manifest.optString("entrypoint") == MorimilCanvasContract.ENTRYPOINT) {
            "Unexpected Morimil Canvas entrypoint"
        }
        require(manifest.optString("bridgeSchema") == MorimilCanvasContract.BRIDGE_SCHEMA) {
            "Unexpected Morimil Canvas bridge schema"
        }

        val declaredTotal = manifest.optLong("totalBytes", -1L)
        require(declaredTotal in 1..MAX_BUNDLE_BYTES) { "Invalid Morimil Canvas bundle size" }
        val files = manifest.optJSONArray("files") ?: JSONArray()
        require(files.length() in 1..MAX_FILE_COUNT) { "Invalid Morimil Canvas file count" }

        var verifiedTotal = 0L
        repeat(files.length()) { index ->
            val item = files.getJSONObject(index)
            val path = item.getString("path")
            requireSafeRelativePath(path)
            val expectedSize = item.getLong("size")
            val expectedHash = item.getString("sha256")
            require(expectedSize >= 0) { "Negative canvas asset size" }
            require(expectedHash.matches(Regex("[0-9a-f]{64}"))) { "Invalid canvas asset hash" }

            val assetPath = "${MorimilCanvasContract.ASSET_ROOT}/$path"
            val (actualSize, actualHash) = digestAsset(assetManager, assetPath)
            require(actualSize == expectedSize) { "Canvas asset size mismatch: $path" }
            require(actualHash == expectedHash) { "Canvas asset hash mismatch: $path" }
            verifiedTotal += actualSize
            require(verifiedTotal <= MAX_BUNDLE_BYTES) { "Morimil Canvas bundle exceeds 6 MB" }
        }

        require(verifiedTotal == declaredTotal) { "Morimil Canvas total size mismatch" }
        MorimilCanvasBundleMetadata(
            version = manifest.getString("version"),
            totalBytes = verifiedTotal,
            fileCount = files.length()
        )
    }

    private fun requireSafeRelativePath(path: String) {
        val segments = path.split('/')
        require(
            path.isNotBlank() &&
                !path.startsWith('/') &&
                segments.none { it.isBlank() || it == "." || it == ".." }
        ) { "Unsafe canvas asset path: $path" }
    }

    private fun digestAsset(assetManager: AssetManager, path: String): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        assetManager.open(path, AssetManager.ACCESS_STREAMING).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                size += count
            }
        }
        val hash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        return size to hash
    }
}

class MorimilCanvasBridge(
    private val webView: WebView,
    private val onEvent: (MorimilCanvasEvent) -> Unit,
    private val onFailure: (Throwable) -> Unit
) {
    fun install() {
        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            "This Android WebView does not support secure message listeners"
        }
        WebViewCompat.addWebMessageListener(
            webView,
            MorimilCanvasContract.HOST_OBJECT_NAME,
            setOf(MorimilCanvasContract.APP_ORIGIN),
            object : WebViewCompat.WebMessageListener {
                override fun onPostMessage(
                    view: WebView,
                    message: WebMessageCompat,
                    sourceOrigin: Uri,
                    isMainFrame: Boolean,
                    replyProxy: JavaScriptReplyProxy
                ) {
                    if (!isMainFrame || sourceOrigin.toString() != MorimilCanvasContract.APP_ORIGIN) return
                    runCatching {
                        MorimilCanvasContract.parseEvent(message.data ?: "")
                    }.onSuccess(onEvent).onFailure(onFailure)
                }
            }
        )
    }

    fun send(commandJson: String) {
        val command = MorimilCanvasContract.validateCommand(commandJson)
        webView.evaluateJavascript(
            "window.MorimilCanvas && window.MorimilCanvas.execute($command);",
            null
        )
    }

    fun close() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(webView, MorimilCanvasContract.HOST_OBJECT_NAME)
        }
    }
}
