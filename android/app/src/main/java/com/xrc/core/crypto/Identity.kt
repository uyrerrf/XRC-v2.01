// ============================================================
// FILE: android/app/src/main/java/com/xrc/core/crypto/Identity.kt
// ============================================================
package com.xrc.core.crypto

import android.content.Context
import android.provider.Settings
import android.util.Base64
import java.security.*

/**
 * Identity — Ed25519-based device identity for XRC.
 *
 * Each device generates a persistent Ed25519 keypair.
 * The public key serves as the device's unique identity.
 * This identity is registered with the C2 server on first contact.
 */
class Identity(private val context: Context) {

    companion object {
        private const val ALGORITHM = "Ed25519"
        private const val PREFS_KEY_PRIVATE = "xrc_identity_private"
        private const val PREFS_KEY_PUBLIC = "xrc_identity_public"

        /**
         * Get or create a stable device ID from Android settings.
         */
        fun getDeviceId(context: Context): String {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            // Hash the Android ID for privacy
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(androidId.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(32)
        }
    }

    private val prefs = context.getSharedPreferences("xrc_identity", Context.MODE_PRIVATE)

    private var keyPair: KeyPair? = null

    /**
     * Initialize or load the device identity keypair.
     */
    fun initialize() {
        val privateKeyB64 = prefs.getString(PREFS_KEY_PRIVATE, null)
        val publicKeyB64 = prefs.getString(PREFS_KEY_PUBLIC, null)

        if (privateKeyB64 != null && publicKeyB64 != null) {
            // Load existing keys
            try {
                val keyFactory = KeyFactory.getInstance(ALGORITHM)
                val privKeySpec = java.security.spec.PKCS8EncodedKeySpec(
                    Base64.decode(privateKeyB64, Base64.NO_WRAP)
                )
                val pubKeySpec = java.security.spec.X509EncodedKeySpec(
                    Base64.decode(publicKeyB64, Base64.NO_WRAP)
                )
                keyPair = KeyPair(
                    keyFactory.generatePublic(pubKeySpec),
                    keyFactory.generatePrivate(privKeySpec)
                )
                return
            } catch (e: Exception) {
                // Corrupted keys — regenerate below
            }
        }

        // Generate new keypair
        val kpGen = KeyPairGenerator.getInstance(ALGORITHM)
        keyPair = kpGen.generateKeyPair()

        // Save to preferences
        prefs.edit()
            .putString(
                PREFS_KEY_PRIVATE,
                Base64.encodeToString(keyPair!!.private.encoded, Base64.NO_WRAP)
            )
            .putString(
                PREFS_KEY_PUBLIC,
                Base64.encodeToString(keyPair!!.public.encoded, Base64.NO_WRAP)
            )
            .apply()
    }

    /**
     * Get the device's public key as Base64.
     */
    fun getPublicKeyBase64(): String {
        return Base64.encodeToString(keyPair!!.public.encoded, Base64.NO_WRAP)
    }

    /**
     * Get the device's private key.
     */
    fun getPrivateKey(): PrivateKey = keyPair!!.private

    /**
     * Get the device's public key.
     */
    fun getPublicKey(): PublicKey = keyPair!!.public

    /**
     * Sign a message with the device's private key.
     */
    fun sign(message: ByteArray): ByteArray {
        val signature = Signature.getInstance(ALGORITHM)
        signature.initSign(keyPair!!.private)
        signature.update(message)
        return signature.sign()
    }

    /**
     * Verify a signature with the device's public key.
     */
    fun verify(message: ByteArray, signatureBytes: ByteArray): Boolean {
        val signature = Signature.getInstance(ALGORITHM)
        signature.initVerify(keyPair!!.public)
        signature.update(message)
        return signature.verify(signatureBytes)
    }
}
