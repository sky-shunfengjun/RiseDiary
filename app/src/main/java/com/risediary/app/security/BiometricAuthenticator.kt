package com.risediary.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class BiometricAuthResult {
    Success,
    Cancelled,
    Error,
}

@Singleton
class BiometricAuthenticator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onResult: (BiometricAuthResult) -> Unit,
        onFailedAttempt: () -> Unit = {},
    ) {
        if (!isAvailable()) {
            onResult(BiometricAuthResult.Error)
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        runCatching {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .setNegativeButtonText(negativeButtonText)
                .build()
            BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        onResult(BiometricAuthResult.Success)
                    }

                    override fun onAuthenticationFailed() {
                        onFailedAttempt()
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {
                        val cancelled = errorCode == BiometricPrompt.ERROR_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        onResult(
                            if (cancelled) BiometricAuthResult.Cancelled
                            else BiometricAuthResult.Error
                        )
                    }
                }
            ).authenticate(promptInfo)
        }.onFailure {
            onResult(BiometricAuthResult.Error)
        }
    }

    private companion object {
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG
    }
}
