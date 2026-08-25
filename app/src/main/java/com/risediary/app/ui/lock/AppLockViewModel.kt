package com.risediary.app.ui.lock

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import com.risediary.app.R
import com.risediary.app.data.UserPreferences
import com.risediary.app.security.BiometricAuthResult
import com.risediary.app.security.BiometricAuthenticator
import com.risediary.app.security.PinSecurity
import com.risediary.app.security.PinVerification
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject

enum class LockMode {
    CREATE,
    CREATE_CONFIRM,
    VERIFY,
    CHANGE_OLD,
    CHANGE_NEW,
    CHANGE_CONFIRM,
    DISABLE_VERIFY,
}

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val preferences: UserPreferences,
    private val pinSecurity: PinSecurity,
    private val biometricAuthenticator: BiometricAuthenticator,
    private val clock: Clock,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _pin = MutableStateFlow("")
    val pin: StateFlow<String> = _pin.asStateFlow()

    private val _mode = MutableStateFlow(LockMode.VERIFY)
    val mode: StateFlow<LockMode> = _mode.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _attempts = MutableStateFlow(0)
    val attempts: StateFlow<Int> = _attempts.asStateFlow()

    private val _lockoutRemaining = MutableStateFlow(0)
    val lockoutRemaining: StateFlow<Int> = _lockoutRemaining.asStateFlow()

    private val _title = MutableStateFlow(context.getString(R.string.app_lock_title_verify))
    val title: StateFlow<String> = _title.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    val biometricUnlockEnabled = preferences.biometricUnlockEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    private var firstPin = ""
    private var savedCredential = ""
    private var loadJob: Job? = null
    private var countdownJob: Job? = null
    private var biometricRequestInProgress = false
    private var verifyInFlight = false
    private var lockoutDeadlineElapsed: Long = 0L

    val biometricAvailable: Boolean
        get() = biometricAuthenticator.isAvailable()

    fun init(mode: LockMode) {
        if (loadJob?.isActive == true && _mode.value == mode) return
        _mode.value = mode
        _pin.value = ""
        _errorMessage.value = null
        firstPin = ""
        _done.value = false
        _ready.value = false
        updateTitle()

        loadJob = viewModelScope.launch {
            savedCredential = preferences.appLockPin.first()
            val lockEnabled = preferences.appLockEnabled.first()
            if (mode == LockMode.CREATE && lockEnabled) {
                _ready.value = true
                _done.value = true
                return@launch
            }
            _attempts.value = preferences.appLockAttempts.first()
            val lockoutUntil = preferences.appLockoutUntil.first()
            startCountdownIfNeeded(lockoutUntil)
            _ready.value = true
        }
    }

    fun onDigit(digit: Int) {
        if (!_ready.value || _lockoutRemaining.value > 0 || digit !in 0..9) return
        if (_pin.value.length < PIN_LENGTH) {
            _pin.value += digit.toString()
            _errorMessage.value = null
            if (_pin.value.length == PIN_LENGTH) handleComplete()
        }
    }

    fun onDelete() {
        if (!_ready.value || _lockoutRemaining.value > 0) return
        _pin.value = _pin.value.dropLast(1)
        _errorMessage.value = null
    }

    fun clearInput() {
        _pin.value = ""
        _errorMessage.value = null
    }

    fun consumeDone() {
        _done.value = false
    }

    private fun handleComplete() {
        val entered = _pin.value
        when (_mode.value) {
            LockMode.CREATE -> {
                firstPin = entered
                advanceTo(LockMode.CREATE_CONFIRM)
            }
            LockMode.CREATE_CONFIRM -> {
                if (entered == firstPin) saveAndDone(entered)
                else restartCreation(LockMode.CREATE, R.string.app_lock_error_pin_mismatch)
            }
            LockMode.CHANGE_OLD -> verifySavedPin(
                entered = entered,
                onSuccess = { advanceTo(LockMode.CHANGE_NEW) },
                errorRes = R.string.app_lock_error_old_pin
            )
            LockMode.CHANGE_NEW -> {
                firstPin = entered
                advanceTo(LockMode.CHANGE_CONFIRM)
            }
            LockMode.CHANGE_CONFIRM -> {
                if (entered == firstPin) saveAndDone(entered)
                else restartCreation(LockMode.CHANGE_NEW, R.string.app_lock_error_pin_mismatch)
            }
            LockMode.VERIFY -> verifySavedPin(
                entered = entered,
                onSuccess = { _done.value = true },
                errorRes = R.string.app_lock_error_pin
            )
            LockMode.DISABLE_VERIFY -> verifySavedPin(
                entered = entered,
                onSuccess = ::clearLock,
                errorRes = R.string.app_lock_error_pin
            )
        }
    }

    private fun verifySavedPin(entered: String, onSuccess: () -> Unit, errorRes: Int) {
        if (verifyInFlight) return
        verifyInFlight = true
        viewModelScope.launch {
            try {
                val verification = try {
                    withContext(Dispatchers.Default) {
                        pinSecurity.verify(entered, savedCredential)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    showSecurityError()
                    return@launch
                }

                when (verification) {
                    PinVerification.MATCH -> {
                        clearFailures()
                        onSuccess()
                    }
                    PinVerification.LEGACY_MATCH -> {
                        val upgradedCredential = createCredentialOrNull(entered) ?: return@launch
                        if (!saveAppLockCredential(upgradedCredential)) return@launch
                        savedCredential = upgradedCredential
                        clearFailures()
                        onSuccess()
                    }
                    PinVerification.NO_MATCH -> onMismatch(errorRes)
                }
            } finally {
                verifyInFlight = false
            }
        }
    }

    private fun saveAndDone(pin: String) {
        viewModelScope.launch {
            val credential = createCredentialOrNull(pin) ?: return@launch
            if (!saveAppLockCredential(credential)) return@launch
            savedCredential = credential
            clearFailures()
            _done.value = true
        }
    }

    private fun clearLock() {
        viewModelScope.launch {
            try {
                preferences.setAppLock(enabled = false, pin = "")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showSecurityError()
                return@launch
            }
            savedCredential = ""
            _done.value = true
        }
    }

    private suspend fun onMismatch(messageRes: Int) {
        val attempts = _attempts.value + 1
        _pin.value = ""
        if (attempts >= MAX_ATTEMPTS) {
            val lockoutUntil = clock.millis() + LOCKOUT_MILLIS
            _attempts.value = MAX_ATTEMPTS
            _errorMessage.value = context.getString(R.string.app_lock_error_lockout)
            // Enforce the in-memory countdown even if persisting the failure
            // state fails; a silent DataStore failure must not disable the gate.
            lockoutDeadlineElapsed = SystemClock.elapsedRealtime() + LOCKOUT_MILLIS
            startCountdownIfNeeded(lockoutUntil)
            saveFailureState(MAX_ATTEMPTS, lockoutUntil)
        } else {
            _attempts.value = attempts
            _errorMessage.value = context.getString(
                R.string.app_lock_error_attempts_left,
                context.getString(messageRes),
                MAX_ATTEMPTS - attempts
            )
            saveFailureState(attempts, 0L)
        }
    }

    private fun startCountdownIfNeeded(lockoutUntil: Long) {
        countdownJob?.cancel()
        if (lockoutDeadlineElapsed == 0L) {
            // Restored after process death: derive the deadline from the persisted
            // wall-clock value once, then count down monotonically.
            lockoutDeadlineElapsed = SystemClock.elapsedRealtime() +
                (lockoutUntil - clock.millis()).coerceAtLeast(0L)
        }
        if (SystemClock.elapsedRealtime() >= lockoutDeadlineElapsed) {
            lockoutDeadlineElapsed = 0L
            _lockoutRemaining.value = 0
            if (_attempts.value >= MAX_ATTEMPTS) {
                viewModelScope.launch { clearFailures() }
            }
            return
        }

        countdownJob = viewModelScope.launch {
            while (SystemClock.elapsedRealtime() < lockoutDeadlineElapsed) {
                _lockoutRemaining.value =
                    ((lockoutDeadlineElapsed - SystemClock.elapsedRealtime() + 999L) / 1000L)
                        .toInt()
                delay(250L)
            }
            lockoutDeadlineElapsed = 0L
            clearFailures()
            _errorMessage.value = null
        }
    }

    private suspend fun clearFailures() {
        _attempts.value = 0
        _lockoutRemaining.value = 0
        try {
            preferences.clearAppLockFailures()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            showSecurityError()
        }
    }

    private suspend fun createCredentialOrNull(pin: String): String? = try {
        withContext(Dispatchers.Default) {
            pinSecurity.createCredential(pin)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        showSecurityError()
        null
    }

    private suspend fun saveAppLockCredential(credential: String): Boolean = try {
        preferences.setAppLock(enabled = true, pin = credential)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        showSecurityError()
        false
    }

    private suspend fun saveFailureState(attempts: Int, lockoutUntil: Long): Boolean = try {
        preferences.setAppLockFailureState(attempts, lockoutUntil)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        showSecurityError()
        false
    }

    private fun showSecurityError() {
        _pin.value = ""
        _errorMessage.value = context.getString(R.string.app_lock_error_security)
    }

    private fun advanceTo(nextMode: LockMode) {
        _pin.value = ""
        _mode.value = nextMode
        updateTitle()
    }

    private fun restartCreation(mode: LockMode, messageRes: Int) {
        firstPin = ""
        _mode.value = mode
        _pin.value = ""
        _errorMessage.value = context.getString(messageRes)
        updateTitle()
    }

    private fun updateTitle() {
        _title.value = when (_mode.value) {
            LockMode.CREATE -> context.getString(R.string.app_lock_title_create)
            LockMode.CREATE_CONFIRM -> context.getString(R.string.app_lock_title_confirm)
            LockMode.VERIFY -> context.getString(R.string.app_lock_title_verify)
            LockMode.CHANGE_OLD -> context.getString(R.string.app_lock_title_change_old)
            LockMode.CHANGE_NEW -> context.getString(R.string.app_lock_title_change_new)
            LockMode.CHANGE_CONFIRM -> context.getString(R.string.app_lock_title_change_confirm)
            LockMode.DISABLE_VERIFY -> context.getString(R.string.app_lock_title_disable)
        }
    }

    fun authenticateWithBiometric(activity: FragmentActivity) {
        if (
            !_ready.value ||
            _mode.value != LockMode.VERIFY ||
            !biometricUnlockEnabled.value ||
            !biometricAuthenticator.isAvailable() ||
            biometricRequestInProgress
        ) return
        biometricRequestInProgress = true
        biometricAuthenticator.authenticate(
                activity = activity,
                title = context.getString(R.string.app_lock_biometric_title),
                subtitle = context.getString(R.string.app_lock_biometric_subtitle),
                negativeButtonText = context.getString(R.string.app_lock_biometric_negative),
                onResult = { result ->
                    when (result) {
                        BiometricAuthResult.Success -> {
                            biometricRequestInProgress = false
                            viewModelScope.launch {
                                clearFailures()
                                _done.value = true
                            }
                        }
                        BiometricAuthResult.Error -> {
                            biometricRequestInProgress = false
                            _errorMessage.value = context.getString(R.string.app_lock_biometric_error)
                        }
                        BiometricAuthResult.Cancelled -> {
                            biometricRequestInProgress = false
                        }
                    }
                },
                onFailedAttempt = {
                    _errorMessage.value = context.getString(R.string.app_lock_biometric_retry)
                }
            )
    }

    private companion object {
        const val PIN_LENGTH = 4
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 30_000L
    }
}
