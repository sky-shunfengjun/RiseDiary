package com.risediary.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

enum class PinVerification {
    MATCH,
    LEGACY_MATCH,
    NO_MATCH
}

@Singleton
class PinSecurity @Inject constructor() {

    fun createCredential(pin: String): String =
        PREFIX + Base64.encodeToString(sign(pin), Base64.NO_WRAP)

    fun verify(pin: String, storedCredential: String): PinVerification {
        if (!storedCredential.startsWith(PREFIX)) {
            return if (constantTimeEquals(pin, storedCredential)) {
                PinVerification.LEGACY_MATCH
            } else {
                PinVerification.NO_MATCH
            }
        }

        val expected = runCatching {
            Base64.decode(storedCredential.removePrefix(PREFIX), Base64.NO_WRAP)
        }.getOrNull() ?: return PinVerification.NO_MATCH
        return if (MessageDigest.isEqual(expected, sign(pin))) {
            PinVerification.MATCH
        } else {
            PinVerification.NO_MATCH
        }
    }

    private fun sign(pin: String): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(getOrCreateKey())
        return mac.doFinal(pin.toByteArray(Charsets.UTF_8))
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(ALGORITHM, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
            generateKey()
        }
    }

    private fun constantTimeEquals(first: String, second: String): Boolean =
        MessageDigest.isEqual(
            first.toByteArray(Charsets.UTF_8),
            second.toByteArray(Charsets.UTF_8)
        )

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "rise_diary_pin_hmac"
        const val ALGORITHM = "HmacSHA256"
        const val PREFIX = "hmac:"
    }
}
