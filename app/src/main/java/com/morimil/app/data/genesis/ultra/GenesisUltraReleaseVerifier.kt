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

class GenesisUltraTrustedEd25519Key(
    val signerType: String,
    val signerId: String,
    val keyEpochId: String,
    val publicKeyRef: String,
    rawPublicKey: ByteArray
) {
    val rawPublicKey: ByteArray = rawPublicKey.copyOf()
}

class GenesisUltraEd25519SignatureVerifier(
    trustedKeys: Collection<GenesisUltraTrustedEd25519Key>
) : GenesisUltraSignatureVerifier {
    private val keysByIdentity: Map<TrustedKeyIdentity, ByteArray>

    init {
        val prepared = trustedKeys.map { trusted ->
            require(trusted.rawPublicKey.size == ED25519_PUBLIC_KEY_BYTES) { "trusted_ed25519_key_size_invalid" }
            require(GenesisUltraHashProfile.sha256(trusted.rawPublicKey) == trusted.publicKeyRef) {
                "trusted_ed25519_public_key_ref_mismatch"
            }
            TrustedKeyIdentity(
                signerType = trusted.signerType,
                signerId = trusted.signerId,
                keyEpochId = trusted.keyEpochId,
                publicKeyRef = trusted.publicKeyRef
            ) to trusted.rawPublicKey.copyOf()
        }
        require(prepared.map { (identity, _) -> identity }.distinct().size == prepared.size) {
            "duplicate_trusted_ed25519_identity"
        }
        keysByIdentity = prepared.toMap()
    }

    override fun verify(envelope: GenesisUltraSignatureEnvelope, signingBytes: ByteArray): Boolean {
        val identity = TrustedKeyIdentity(
            signerType = envelope.signerType,
            signerId = envelope.signerId,
            keyEpochId = envelope.keyEpochId,
            publicKeyRef = envelope.publicKeyRef
        )
        val rawPublicKey = keysByIdentity[identity]?.copyOf() ?: return false
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

    private data class TrustedKeyIdentity(
        val signerType: String,
        val signerId: String,
        val keyEpochId: String,
        val publicKeyRef: String
    )

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
