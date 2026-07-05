package com.morimil.app.core.memory

import com.morimil.app.data.local.MemoryEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryEventIntegrityTest {
    private val integrity = MemoryEventIntegrity()

    @Test
    fun v3HashIsDeterministic() {
        val first = sampleHash(previousEventHash = null)
        val second = sampleHash(previousEventHash = null)

        assertEquals("sha256:a69fcd21d68505ea6504a9c6a4466268b2b7ccd271f49c0aac2d5ba89398ce1d", first)
        assertEquals(first, second)
    }

    @Test
    fun tamperedEventBodyFailsIntegrity() {
        val event = sampleEvent(previousEventHash = null)
        val tampered = event.copy(body = "Morimil recuerda otra cosa.")

        assertNull(integrity.memoryEventIntegrityFailure(event, expectedPreviousHash = null))
        assertEquals("event_hash_mismatch", integrity.memoryEventIntegrityFailure(tampered, expectedPreviousHash = null))
    }

    @Test
    fun chainVerificationAcceptsValidChainAndRejectsBrokenLink() {
        val first = sampleEvent(previousEventHash = null)
        val second = sampleEvent(
            previousEventHash = first.eventHash,
            body = "Morimil consolida la memoria local.",
            createdAtMillis = SAMPLE_CREATED_AT_MILLIS + 1,
            eventType = "conversation.assistant_message",
            actor = "morimil",
            source = "chat",
            memoryKind = "conversation",
            tagsJson = "[\"memory\",\"conversation\"]",
            confidence = 90,
            userConfirmed = false,
            importance = 80
        )
        val brokenSecond = second.copy(previousEventHash = "sha256:wrong-previous")

        assertTrue(integrity.verifyMemoryEventChain(listOf(first, second)))
        assertFalse(integrity.verifyMemoryEventChain(listOf(first, brokenSecond)))
    }

    @Test
    fun tailVerificationCanStartAfterExistingHistory() {
        val checkpointHash = "sha256:trusted-checkpoint"
        val firstTailEvent = sampleEvent(
            previousEventHash = checkpointHash,
            createdAtMillis = SAMPLE_CREATED_AT_MILLIS + 10,
            body = "Morimil verifica solo la cola reciente antes de escribir."
        )
        val secondTailEvent = sampleEvent(
            previousEventHash = firstTailEvent.eventHash,
            body = "La auditoria completa queda fuera del camino caliente.",
            createdAtMillis = SAMPLE_CREATED_AT_MILLIS + 11,
            eventType = "conversation.assistant_message",
            actor = "morimil",
            source = "chat",
            memoryKind = "conversation",
            tagsJson = "[\"memory\",\"tail_verification\"]",
            confidence = 90,
            userConfirmed = false,
            importance = 80
        )

        assertTrue(
            integrity.verifyMemoryEventChain(
                listOf(firstTailEvent, secondTailEvent),
                requireGenesisStart = false
            )
        )
        assertFalse(integrity.verifyMemoryEventChain(listOf(firstTailEvent, secondTailEvent)))
    }

    @Test
    fun signedEventFailsWhenSignatureDoesNotMatchHash() {
        val signedIntegrity = MemoryEventIntegrity(
            signatureVerifier = object : MemoryEventSignatureVerifier {
                override fun signatureIntegrityFailure(
                    eventHash: String,
                    signatureAlgorithm: String?,
                    eventSignature: String?
                ): String? {
                    if (signatureAlgorithm == MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_UNSIGNED) {
                        return null
                    }
                    if (signatureAlgorithm != MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_ANDROID_KEYSTORE_EC) {
                        return "unsupported_signature_algorithm:$signatureAlgorithm"
                    }
                    return if (eventSignature == "valid:$eventHash") null else "event_signature_mismatch"
                }
            }
        )
        val unsigned = sampleEvent(previousEventHash = null)
        val signed = unsigned.copy(
            signatureAlgorithm = MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_ANDROID_KEYSTORE_EC,
            eventSignature = "valid:${unsigned.eventHash}"
        )
        val badSignature = signed.copy(eventSignature = "valid:sha256:other")

        assertNull(signedIntegrity.memoryEventIntegrityFailure(signed, expectedPreviousHash = null))
        assertEquals(
            "event_signature_mismatch",
            signedIntegrity.memoryEventIntegrityFailure(badSignature, expectedPreviousHash = null)
        )
    }

    @Test
    fun signedEpochRejectsUnsignedEventsAfterEpoch() {
        val first = sampleEvent(previousEventHash = null)
        val signedFirst = first.signedForTest()
        val strippedSecond = sampleEvent(
            previousEventHash = signedFirst.eventHash,
            body = "Este evento posterior no puede degradarse a unsigned.",
            createdAtMillis = SAMPLE_CREATED_AT_MILLIS + 1
        )
        val signedIntegrity = MemoryEventIntegrity(
            signatureVerifier = validTestSignatureVerifier(),
            signatureEpochPolicy = FixedSignatureEpochPolicy(signedFirst.eventHash)
        )

        assertFalse(signedIntegrity.verifyMemoryEventChain(listOf(signedFirst, strippedSecond)))
    }

    @Test
    fun signedEpochRejectsMissingEpochAnchor() {
        val first = sampleEvent(previousEventHash = null)
        val second = sampleEvent(
            previousEventHash = first.eventHash,
            body = "Cadena recompuesta sin el ancla de firma.",
            createdAtMillis = SAMPLE_CREATED_AT_MILLIS + 1
        )
        val signedIntegrity = MemoryEventIntegrity(
            signatureVerifier = validTestSignatureVerifier(),
            signatureEpochPolicy = FixedSignatureEpochPolicy("sha256:missing-signed-epoch")
        )

        assertFalse(signedIntegrity.verifyMemoryEventChain(listOf(first, second)))
    }

    @Test
    fun partialTailVerificationDoesNotRequireEpochAnchorInsideWindow() {
        val checkpointHash = "sha256:trusted-after-signed-epoch"
        val firstTailEvent = sampleEvent(
            previousEventHash = checkpointHash,
            createdAtMillis = SAMPLE_CREATED_AT_MILLIS + 20,
            body = "Ventana parcial posterior al ancla."
        )
        val secondTailEvent = sampleEvent(
            previousEventHash = firstTailEvent.eventHash,
            body = "La cola parcial no debe exigir ver todo el pasado.",
            createdAtMillis = SAMPLE_CREATED_AT_MILLIS + 21
        )
        val signedIntegrity = MemoryEventIntegrity(
            signatureVerifier = validTestSignatureVerifier(),
            signatureEpochPolicy = FixedSignatureEpochPolicy("sha256:epoch-outside-tail")
        )

        assertTrue(
            signedIntegrity.verifyMemoryEventChain(
                events = listOf(firstTailEvent, secondTailEvent),
                requireGenesisStart = false
            )
        )
    }

    @Test
    fun signedEpochAcceptsSignedEventsAtAndAfterEpoch() {
        val first = sampleEvent(previousEventHash = null).signedForTest()
        val second = sampleEvent(
            previousEventHash = first.eventHash,
            body = "Evento posterior firmado.",
            createdAtMillis = SAMPLE_CREATED_AT_MILLIS + 1
        ).signedForTest()
        val signedIntegrity = MemoryEventIntegrity(
            signatureVerifier = validTestSignatureVerifier(),
            signatureEpochPolicy = FixedSignatureEpochPolicy(first.eventHash)
        )

        assertTrue(signedIntegrity.verifyMemoryEventChain(listOf(first, second)))
    }

    @Test
    fun signedEpochRejectsLegacyMarkerAfterEpoch() {
        val first = sampleEvent(previousEventHash = null).signedForTest()
        val legacyAfterEpoch = sampleEvent(
            previousEventHash = first.eventHash,
            body = "Legacy no puede aparecer despues de la epoca firmada.",
            createdAtMillis = SAMPLE_CREATED_AT_MILLIS + 1
        ).copy(
            eventHash = MemoryEventIntegrity.LEGACY_EVENT_HASH,
            signatureAlgorithm = MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_UNSIGNED,
            eventSignature = null
        )
        val signedIntegrity = MemoryEventIntegrity(
            signatureVerifier = validTestSignatureVerifier(),
            signatureEpochPolicy = FixedSignatureEpochPolicy(first.eventHash)
        )

        assertFalse(signedIntegrity.verifyMemoryEventChain(listOf(first, legacyAfterEpoch)))
    }

    @Test
    fun defaultVerifierRejectsSignedEventWhenPlatformVerifierIsUnavailable() {
        val event = sampleEvent(previousEventHash = null).copy(
            signatureAlgorithm = MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_ANDROID_KEYSTORE_EC,
            eventSignature = "platform-signature"
        )

        assertEquals(
            "signature_verifier_unavailable:${MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_ANDROID_KEYSTORE_EC}",
            integrity.memoryEventIntegrityFailure(event, expectedPreviousHash = null)
        )
    }

    private fun sampleEvent(
        previousEventHash: String?,
        body: String = SAMPLE_BODY,
        createdAtMillis: Long = SAMPLE_CREATED_AT_MILLIS,
        eventType: String = "conversation.user_message",
        actor: String = "user",
        source: String = "chat",
        memoryKind: String = "decision",
        tagsJson: String = "[\"memory\",\"decision\"]",
        evidenceJson: String = SAMPLE_EVIDENCE_JSON,
        confidence: Int = 94,
        userConfirmed: Boolean = true,
        importance: Int = 92
    ): MemoryEventEntity {
        val eventHash = integrity.hashMemoryEventV3(
            genesisCoreId = SAMPLE_GENESIS_CORE_ID,
            genesisCoreHash = SAMPLE_GENESIS_CORE_HASH,
            previousEventHash = previousEventHash,
            eventType = eventType,
            actor = actor,
            source = source,
            contextTag = SAMPLE_CONTEXT_TAG,
            privacyVisibility = SAMPLE_PRIVACY,
            memoryKind = memoryKind,
            tagsJson = tagsJson,
            evidenceJson = evidenceJson,
            confidence = confidence,
            userConfirmed = userConfirmed,
            body = body,
            importance = importance,
            createdAtMillis = createdAtMillis
        )
        return MemoryEventEntity(
            genesisCoreId = SAMPLE_GENESIS_CORE_ID,
            genesisCoreHash = SAMPLE_GENESIS_CORE_HASH,
            previousEventHash = previousEventHash,
            eventHash = eventHash,
            hashAlgorithm = "sha256",
            canonicalization = MemoryEventIntegrity.MEMORY_EVENT_CANONICALIZATION_V3,
            signatureAlgorithm = "unsigned_runtime_v1",
            eventSignature = null,
            eventType = eventType,
            actor = actor,
            source = source,
            contextTag = SAMPLE_CONTEXT_TAG,
            privacyVisibility = SAMPLE_PRIVACY,
            memoryKind = memoryKind,
            tagsJson = tagsJson,
            evidenceJson = evidenceJson,
            confidence = confidence,
            userConfirmed = userConfirmed,
            body = body,
            importance = importance,
            createdAtMillis = createdAtMillis
        )
    }

    private fun sampleHash(previousEventHash: String?): String {
        return sampleEvent(previousEventHash = previousEventHash).eventHash
    }

    private fun MemoryEventEntity.signedForTest(): MemoryEventEntity {
        return copy(
            signatureAlgorithm = MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_ANDROID_KEYSTORE_EC,
            eventSignature = "valid:$eventHash"
        )
    }

    private fun validTestSignatureVerifier(): MemoryEventSignatureVerifier {
        return object : MemoryEventSignatureVerifier {
            override fun signatureIntegrityFailure(
                eventHash: String,
                signatureAlgorithm: String?,
                eventSignature: String?
            ): String? {
                if (signatureAlgorithm == MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_UNSIGNED) {
                    return null
                }
                if (signatureAlgorithm != MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_ANDROID_KEYSTORE_EC) {
                    return "unsupported_signature_algorithm:$signatureAlgorithm"
                }
                return if (eventSignature == "valid:$eventHash") null else "event_signature_mismatch"
            }
        }
    }

    private class FixedSignatureEpochPolicy(
        private val eventHash: String?
    ) : MemorySignatureEpochPolicy {
        override fun signedEpochEventHash(): String? = eventHash
    }

    companion object {
        private const val SAMPLE_GENESIS_CORE_ID = "primary_genesis"
        private const val SAMPLE_GENESIS_CORE_HASH = "sha256:genesis-core-test"
        private const val SAMPLE_CONTEXT_TAG = "local_runtime"
        private const val SAMPLE_PRIVACY = "private_local"
        private const val SAMPLE_BODY = "Morimil recuerda que la memoria local vive en el telefono."
        private const val SAMPLE_EVIDENCE_JSON = "{\"schema\":\"morimil.memory_evidence.v1\",\"classifier\":\"test\"}"
        private const val SAMPLE_CREATED_AT_MILLIS = 1_720_000_000_000L
    }
}
