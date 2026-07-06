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
            }
        )

        val context = provider.build("revisa h" + "ttps://example.com")

        assertTrue(context.contains("FUENTE_EXTERNA="))
        assertTrue(context.contains("Fuente viva"))
        assertTrue(context.contains("Texto externo util."))
    }

    @Test
    fun failedFetchReturnsDiagnosticsInsteadOfSilence() = runBlocking {
        val provider = NetEvidenceProvider(
            fetcher = NetRawFetcher { _ -> NetFetchResult(ok = false, error = "http_403") }
        )

        val context = provider.build("busca informacion actual")

        assertTrue(context.contains("consulta_nativa_sin_resultado"))
        assertTrue(context.contains("DIAGNOSTICO="))
        assertTrue(context.contains("http_403"))
    }
}
