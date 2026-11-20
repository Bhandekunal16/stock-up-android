package com.example.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

data class SaltedHash(
    val saltBase64: String,
    val hashBase64: String,
)

interface StateSealer {
    fun seal(plainText: String): String
    fun unseal(cipherPayload: String): String?
}

/**
 * Hardware/AndroidKeyStore-backed AES-256-GCM sealer for local security configuration.
 */
class AndroidKeyStoreStateSealer(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : StateSealer {
    private val random = SecureRandom()

    override fun seal(plainText: String): String {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val plainBytes = plainText.toByteArray(Charsets.UTF_8)
        return try {
            val encrypted = cipher.doFinal(plainBytes)
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val ctB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            "$VERSION_PREFIX:$ivB64:$ctB64"
        } finally {
            Arrays.fill(plainBytes, 0.toByte())
        }
    }

    override fun unseal(cipherPayload: String): String? {
        val parts = cipherPayload.split(":")
        if (parts.size != 3 || parts[0] != VERSION_PREFIX) return null
        return try {
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[2], Base64.NO_WRAP)
            val secretKey = getExistingKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val decrypted = cipher.doFinal(encrypted)
            try {
                String(decrypted, Charsets.UTF_8)
            } finally {
                Arrays.fill(decrypted, 0.toByte())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getExistingKey(): SecretKey? {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return ks.getKey(keyAlias, null) as? SecretKey
    }

    private fun getOrCreateKey(): SecretKey {
        getExistingKey()?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "stockfolio_applock_master_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val VERSION_PREFIX = "v1"
    }
}

class AppLockCryptoManager(
    private val stateSealer: StateSealer = AndroidKeyStoreStateSealer(),
    private val iterations: Int = DEFAULT_ITERATIONS,
) {
    private val secureRandom = SecureRandom()

    fun hashSecret(secret: CharArray): SaltedHash {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val hashBytes = deriveKey(secret, salt)
        return try {
            SaltedHash(
                saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                hashBase64 = Base64.encodeToString(hashBytes, Base64.NO_WRAP),
            )
        } finally {
            Arrays.fill(hashBytes, 0.toByte())
            Arrays.fill(salt, 0.toByte())
        }
    }

    fun verifySecret(
        attempt: CharArray,
        saltBase64: String,
        expectedHashBase64: String,
    ): Boolean {
        val salt = try {
            Base64.decode(saltBase64, Base64.NO_WRAP)
        } catch (_: Exception) {
            return false
        }
        val expectedHash = try {
            Base64.decode(expectedHashBase64, Base64.NO_WRAP)
        } catch (_: Exception) {
            Arrays.fill(salt, 0.toByte())
            return false
        }
        val actualHash = deriveKey(attempt, salt)
        return try {
            MessageDigest.isEqual(actualHash, expectedHash)
        } finally {
            Arrays.fill(actualHash, 0.toByte())
            Arrays.fill(expectedHash, 0.toByte())
            Arrays.fill(salt, 0.toByte())
        }
    }

    fun sealState(plainJson: String): String = stateSealer.seal(plainJson)

    fun unsealState(cipherPayload: String): String? = stateSealer.unseal(cipherPayload)

    private fun deriveKey(secret: CharArray, salt: ByteArray): ByteArray {
        val algorithm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            "PBKDF2WithHmacSHA256"
        } else {
            "PBKDF2WithHmacSHA1"
        }
        val spec = PBEKeySpec(secret, salt, iterations, HASH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(algorithm)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256
        private const val DEFAULT_ITERATIONS = 50_000

        fun wipe(chars: CharArray) {
            Arrays.fill(chars, '\u0000')
        }
    }
}
