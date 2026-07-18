package com.morimil.app.canvas

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MorimilCanvasBundleVerifierTest {
    @Test
    fun bundledCanvasPassesFullIntegrityVerification() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val result = MorimilCanvasBundleVerifier.verify(context.assets)

        assertTrue(result.exceptionOrNull()?.message, result.isSuccess)
        val metadata = result.getOrThrow()
        assertEquals(MorimilCanvasContract.EXPECTED_VERSION, metadata.version)
        assertTrue(metadata.fileCount > 0)
        assertTrue(metadata.totalBytes > 0)
    }
}
