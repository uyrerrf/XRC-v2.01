package com.xrc.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.SecureRandom

class CryptoBoxTest {

    private val key: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    @Test
    fun encryptDecrypt_roundTrip_succeeds() {
        val plain = "XRC-command-1".toByteArray()
        val cipher = CryptoBox.encrypt(plain, key)
        val result = CryptoBox.decrypt(cipher, key)
        assertArrayEquals(plain, result)
    }

    @Test
    fun decrypt_tamperedCipher_throws() {
        val cipher = CryptoBox.encrypt("payload".toByteArray(), key)
        cipher[cipher.size - 1] = (cipher[cipher.size - 1].toInt() xor 0xFF).toByte()
        assertThrows(Exception::class.java) {
            CryptoBox.decrypt(cipher, key)
        }
    }
}
