package com.example.finlern.data

import android.util.Base64
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionManager {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val KEY_SIZE = 256
    private const val DEFAULT_BUFFER_SIZE = 1024 * 1024 // 1 MB default

    data class EncryptedData(
        val iv: String,
        val encryptedKey: String
    )

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(KEY_SIZE)
        return keyGenerator.generateKey()
    }

    private fun generateIV(): IvParameterSpec {
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv) // Secure random IV
        return IvParameterSpec(iv)
    }

    /**
     * Encrypts the input stream and writes it to the output stream.
     * @param input The source data stream to encrypt.
     * @param output The destination stream for encrypted data.
     * @param bufferSize Size of the buffer for streaming (default 1 MB).
     * @return EncryptedData containing IV and encrypted key.
     */
    fun encrypt(input: InputStream, output: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE): EncryptedData {
        val key = generateKey()
        val iv = generateIV()
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)

        try {
            CipherOutputStream(output, cipher).use { cipherOut ->
                input.copyTo(cipherOut, bufferSize)
            }
            return EncryptedData(
                iv = Base64.encodeToString(iv.iv, Base64.NO_WRAP),
                encryptedKey = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
            )
        } catch (e: Exception) {
            throw EncryptionException("Failed to encrypt data: ${e.message}", e)
        }
    }

    /**
     * Decrypts the input stream and writes it to the output stream.
     * @param input The encrypted data stream to decrypt.
     * @param output The destination stream for decrypted data.
     * @param iv Base64-encoded initialization vector.
     * @param encryptedKey Base64-encoded encrypted key.
     * @param bufferSize Size of the buffer for streaming (default 1 MB).
     */
    fun decrypt(input: InputStream, output: OutputStream, iv: String, encryptedKey: String, bufferSize: Int = DEFAULT_BUFFER_SIZE) {
        val key = SecretKeySpec(Base64.decode(encryptedKey, Base64.NO_WRAP), "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        val ivSpec = IvParameterSpec(Base64.decode(iv, Base64.NO_WRAP))

        try {
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
            CipherInputStream(input, cipher).use { cipherIn ->
                cipherIn.copyTo(output, bufferSize)
            }
        } catch (e: Exception) {
            throw EncryptionException("Failed to decrypt data: ${e.message}", e)
        }
    }
}

class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)