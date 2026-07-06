package com.morimil.app.net

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class NetEvidenceProviderTest {
    @Test
    fun directUrlAddsFetchedText() = runBlocking {
        val provider = NetEvidenceProvider(
            fetcher = NetRawFetcher { _ ->
                NetFetchResult(ok = true, body = "<html><body><h1>Fuente viva</h1><p>Texto externo util.</p></body></html>")
            },
            renderedFetcher = NetRenderedFetcher { _ -> NetRenderedResult(ok = false, error = "not_needed") }
        )

        val context = provider.build("revisa h" + "ttps://example.com")

        assertTrue(context.contains("FUENTE_EXTERNA="))
        assertTrue(context.contains("MODO_LECTURA=http"))
        assertTrue(context.contains("Fuente viva"))
        assertTrue(context.contains("Texto externo util."))
    }

    @Test
    fun failedHttpUsesNativeBrowserFallback() = runBlocking {
        val provider = NetEvidenceProvider(
            fetcher = NetRawFetcher { _ -> NetFetchResult(ok = false, error = "http_403") },
            renderedFetcher = NetRenderedFetcher { _ ->
                NetRenderedResult(ok = true, text = "Pagina renderizada con texto util.")
            }
        )

        val context = provider.build("revisa h" + "ttps://example.com")

        assertTrue(context.contains("MODO_LECTURA=browser"))
        assertTrue(context.contains("Pagina renderizada con texto util."))
    }

    @Test
    fun failedFetchReturnsDiagnosticsInsteadOfSilence() = runBlocking {
        val provider = NetEvidenceProvider(
            fetcher = NetRawFetcher { _ -> NetFetchResult(ok = false, error = "http_403") },
            renderedFetcher = NetRenderedFetcher { _ -> NetRenderedResult(ok = false, error = "browser_403") }
        )

        val context = provider.build("busca informacion actual")

        assertTrue(context.contains("consulta_nativa_sin_resultado"))
        assertTrue(context.contains("DIAGNOSTICO="))
        assertTrue(context.contains("http_403"))
        assertTrue(context.contains("browser_403"))
    }
}
