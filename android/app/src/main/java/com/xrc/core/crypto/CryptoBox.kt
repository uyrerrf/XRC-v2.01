// ============================================================
// FILE: android/app/src/main/java/com/xrc/core/crypto/CryptoBox.kt
// ============================================================
package com.xrc.core.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * CryptoBox — AES-256-GCM encryption wrapper for XRC payloads.
 *
 * Provides:
 * - Symmetric encryption/decryption (AES-256-GCM)
 * - Key derivation from seed + device ID
 * - Secure random generation
 * - Authenticated encryption with AEAD
 */
class CryptoBox(private val masterKey: ByteArray? = null) {

    companion object {
        private const val ALGORITHM = "AES"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 256         // bits
        private const val IV_SIZE = 12           // bytes (96 bits)
        private const val TAG_SIZE = 128         // bits (16 bytes auth tag)
        private const val TAG_SIZE_BITS = 128

        private val secureRandom = SecureRandom()

        /**
         * Generate a new AES-256 key.
         */
        fun generateKey(): ByteArray {
            val keyGen = KeyGenerator.getInstance(ALGORITHM)
            keyGen.init(KEY_SIZE, secureRandom)
            return keyGen.generateKey().encoded
        }

        /**
         * Derive an AES key from a seed + device ID using SHA-256.
         */
        fun deriveKey(seed: ByteArray, deviceId: String): ByteArray {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.update(seed)
            digest.update(deviceId.toByteArray(Charsets.UTF_8))
            return digest.digest()
        }
    }

    private val key: ByteArray = masterKey ?: generateKey()

    /**
     * Encrypt plaintext bytes with AES-256-GCM.
     * Returns Base64-encoded string: iv + ciphertext + tag.
     */
    fun encrypt(plaintext: ByteArray): String {
        val iv = ByteArray(IV_SIZE).apply { secureRandom.nextBytes(this) }
        val secretKey = SecretKeySpec(key, ALGORITHM)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_SIZE_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val ciphertext = cipher.doFinal(plaintext)
        val encrypted = ByteArray(IV_SIZE + ciphertext.size)
        System.arraycopy(iv, 0, encrypted, 0, IV_SIZE)
        System.arraycopy(ciphertext, 0, encrypted, IV_SIZE, ciphertext.size)

        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * Decrypt a Base64-encoded ciphertext string.
     * Extracts IV from first 12 bytes.
     */
    fun decrypt(encryptedBase64: String): ByteArray? {
        return try {
            val encrypted = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (encrypted.size < IV_SIZE) return null

            val iv = encrypted.copyOfRange(0, IV_SIZE)
            val ciphertext = encrypted.copyOfRange(IV_SIZE, encrypted.size)
            val secretKey = SecretKeySpec(key, ALGORITHM)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_SIZE_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Encrypt a string to Base64 string.
     */
    fun encryptString(plaintext: String): String {
        return encrypt(plaintext.toByteArray(Charsets.UTF_8))
    }

    /**
     * Decrypt a Base64 string to plain string.
     */
    fun decryptString(encryptedBase64: String): String? {
        return decrypt(encryptedBase64)?.toString(Charsets.UTF_8)
    }
}
