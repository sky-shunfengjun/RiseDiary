package com.risediary.app.data

/** One atomic read, never a combination of display defaults and persisted secrets. */
data class SecuritySettingsSnapshot(
    val onboardingCompleted: Boolean = false,
    val lockEnabled: Boolean = false,
    val credential: String = "",
    val attempts: Int = 0,
    val lockoutUntil: Long = 0,
    val biometricEnabled: Boolean = false,
    val backgroundAutoLock: Boolean = false,
    val backgroundMode: BackgroundLockMode = BackgroundLockMode.ALWAYS
) {
    val credentialValid: Boolean get() = isValidLockCredential(credential)
    fun requireConsistent(): SecuritySettingsSnapshot = apply {
        check(!lockEnabled || credentialValid) { "应用锁凭据无法读取，请重试" }
    }
}

internal fun isValidLockCredential(value: String): Boolean =
    value.matches(Regex("[0-9]{4}")) ||
        (value.startsWith("hmac:") && runCatching {
            java.util.Base64.getDecoder().decode(value.removePrefix("hmac:")).size == 32
        }.getOrDefault(false))

data class QuantitySettingsSnapshot(
    val predictionMaxTicks: Int = 80
)