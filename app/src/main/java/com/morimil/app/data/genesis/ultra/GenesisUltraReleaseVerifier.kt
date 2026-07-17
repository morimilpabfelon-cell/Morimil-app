package com.morimil.app.data.genesis.ultra

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec


data class GenesisUltraReleaseBundle(
    val manifestJson: String,
    val signatureJson: String,
    val files: Map<String, ByteArray>
)

class GenesisUltraVerifiedRelease internal constructor(
    val manifest: GenesisUltraSeedManifest,
    val signature: GenesisUltraSignatureEnvelope,
    val verifiedRootHash: String,
    val verifiedFileCount: Int
)

fun interface GenesisUltraSignatureVerifier {
    fun verify(envelope: GenesisUltraSignatureEnvelope, signingBytes: ByteArray): Boolean
}

class GenesisUltraEd25519SignatureVerifier(
    trustedPublicKeys: Map<String, ByteArray>
) : GenesisUltraSignatureVerifier {
    private val keysByRef = trustedPublicKeys.mapValues { (_, bytes) -> bytes.copyOf() }

    override fun verify(envelope: GenesisUltraSignatureEnvelope, signingBytes: ByteArray): Boolean {
        val rawPublicKey = keysByRef[envelope.publicKeyRef]?.copyOf() ?: return false
        if (rawPublicKey.size != ED25519_PUBLIC_KEY_BYTES) return false
        if (GenesisUltraHashProfile.sha256(rawPublicKey) != envelope.publicKeyRef) return false
        return try {
            val encodedPublicKey = X509_ED25519_PREFIX + rawPublicKey
            val publicKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(X509EncodedKeySpec(encodedPublicKey))
            val verifier = Signature.getInstance("Ed25519")
            verifier.initVerify(publicKey)
            verifier.update(signingBytes)
            verifier.verify(GenesisUltraHashProfile.decodeLowerHex(envelope.signatureValue))
        } catch (_: GeneralSecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private companion object {
        const val ED25519_PUBLIC_KEY_BYTES = 32
        val X509_ED25519_PREFIX = GenesisUltraHashProfile.decodeLowerHex("302a300506032b6570032100")
    }
}

class GenesisUltraReleaseVerifier(
    private val signatureVerifier: GenesisUltraSignatureVerifier
) {
    fun verify(bundle: GenesisUltraReleaseBundle): GenesisUltraVerifiedRelease {
        val manifest = GenesisUltraContractParser.parseSeedManifest(bundle.manifestJson)
        val declaredFiles = manifest.files.associateBy { file -> file.path }
        require(declaredFiles.size == manifest.files.size) { "duplicate_seed_path" }

        bundle.files.keys.forEach(GenesisUltraHashProfile::requireSafeRelativePath)
        val actualPaths = bundle.files.keys.toSet()
        val declaredPaths = declaredFiles.keys
        require(actualPaths == declaredPaths) {
            val missing = declaredPaths.minus(actualPaths).sorted()
            val unexpected = actualPaths.minus(declaredPaths).sorted()
            "release_file_set_mismatch:missing=$missing:unexpected=$unexpected"
        }

        manifest.files.forEach { file ->
            val bytes = bundle.files.getValue(file.path)
            val actualDigest = GenesisUltraHashProfile.sha256(bytes)
            require(actualDigest == file.digest) { "release_file_digest_mismatch:${file.path}" }
        }

        val computedRoot = GenesisUltraHashProfile.seedRoot(manifest)
        require(computedRoot == manifest.rootHash) { "seed_root_hash_mismatch" }

        val envelope = GenesisUltraContractParser.parseSignatureEnvelope(bundle.signatureJson)
        require(envelope.signerType == "guardian") { "seed_release_signer_must_be_guardian" }
        require(envelope.signedDomain == GenesisUltraHashProfile.SEED_ROOT_DOMAIN) {
            "seed_release_signed_domain_mismatch"
        }
        require(envelope.signedDigest == computedRoot) { "seed_release_signed_digest_mismatch" }

        val signingBytes = GenesisUltraHashProfile.signatureEnvelopePreimage(envelope)
        require(signatureVerifier.verify(envelope, signingBytes)) { "seed_release_signature_invalid_or_untrusted" }

        return GenesisUltraVerifiedRelease(
            manifest = manifest,
            signature = envelope,
            verifiedRootHash = computedRoot,
            verifiedFileCount = manifest.files.size
        )
    }
}
