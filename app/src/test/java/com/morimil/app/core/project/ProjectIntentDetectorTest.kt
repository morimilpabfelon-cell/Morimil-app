package com.morimil.app.core.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectIntentDetectorTest {
    @Test
    fun detectsCompanyNameAfterDeMarker() {
        val intent = ProjectIntentDetector.detect("Morimil, vamos a hacer una empresa de IonPay para billetera digital")

        assertEquals("IonPay", intent?.displayName)
        assertTrue(intent?.mission.orEmpty().contains("IonPay"))
    }

    @Test
    fun detectsNamedProject() {
        val intent = ProjectIntentDetector.detect("quiero crear un proyecto llamado High メメ Tide")

        assertEquals("High メメ Tide", intent?.displayName)
    }

    @Test
    fun detectsExistingCompanyDevelopmentLanguage() {
        val intent = ProjectIntentDetector.detect("hola estoy desarrollando una empresa de tecnologia llamada ABITS-137 Technologies")

        assertEquals("ABITS-137 Technologies", intent?.displayName)
    }

    @Test
    fun ignoresExplicitNotYetLanguage() {
        val intent = ProjectIntentDetector.detect("estamos hablando de una empresa pero todavia no la vamos a crear")

        assertNull(intent)
    }

    @Test
    fun ignoresCasualProjectDiscussion() {
        val intent = ProjectIntentDetector.detect("estamos hablando de una empresa interesante para algun dia")

        assertNull(intent)
    }
}
