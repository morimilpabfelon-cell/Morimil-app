package com.morimil.app.data.genesis.ultra

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenesisUltraEd25519RuntimeTest {
    @Test
    fun officialVectorMatchesAndroidRuntimeSupport() {
        val publicKey = GenesisUltraHashProfile.decodeLowerHex(
            "d04ab232742bb4ab3a1368bd4615e4e6d0224ab71a016baf8520a332c9778737"
        )
        val envelope = GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = "genesis.signature.ed25519.v0.1",
            signerType = "body",
            signerId = "body_01HCRYPTO000000000000001",
            keyEpochId = "epoch_01HCRYPTO00000000000001",
            signedDomain = "genesis.crypto.vector.signature.v0.1",
            signedDigest = "sha256:" + "a".repeat(64),
            signatureValue = "389deebe9b4ff2142a6fbb2cc28c4766b85db9eb58c2e1ba5673b3feb50e79d432317e68aca86648ff1ec011ff9c36552c590f67e9ef38911721d4f25edde500",
            createdAt = "2026-07-15T02:30:00Z",
            publicKeyRef = "sha256:10ba682c8ad13513971e8b56881aab8bd702bb807796eca81932c735a94d6e6d"
        )
        val verifier = GenesisUltraEd25519SignatureVerifier(
            listOf(
                GenesisUltraTrustedEd25519Key(
                    signerType = envelope.signerType,
                    signerId = envelope.signerId,
                    keyEpochId = envelope.keyEpochId,
                    publicKeyRef = envelope.publicKeyRef,
                    rawPublicKey = publicKey
                )
            )
        )
        val verified = verifier.verify(
            envelope,
            GenesisUltraHashProfile.signatureEnvelopePreimage(envelope)
        )

        if (Build.VERSION.SDK_INT >= 33) {
            assertTrue("Ed25519 must verify on Android API 33+", verified)
        } else {
            assertFalse("Unsupported Android releases must fail closed", verified)
        }
    }
}
