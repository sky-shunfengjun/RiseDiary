package com.risediary.app.ui.lock

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.UserPreferences
import com.risediary.app.security.BiometricAuthResult
import com.risediary.app.security.BiometricAuthenticator
import com.risediary.app.security.PinSecurity
import com.risediary.app.security.PinVerification
import dagger.hilt.android.lifecycle.HiltViewModel
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

    private val _title = MutableStateFlow("输入 PIN 码")
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
                else restartCreation(LockMode.CREATE, "两次 PIN 不一致，请重试")
            }
            LockMode.CHANGE_OLD -> verifySavedPin(
                entered = entered,
                onSuccess = { advanceTo(LockMode.CHANGE_NEW) },
                error = "旧 PIN 码错误"
            )
            LockMode.CHANGE_NEW -> {
                firstPin = entered
                advanceTo(LockMode.CHANGE_CONFIRM)
            }
            LockMode.CHANGE_CONFIRM -> {
                if (entered == firstPin) saveAndDone(entered)
                else restartCreation(LockMode.CHANGE_NEW, "两次 PIN 不一致，请重试")
            }
            LockMode.VERIFY -> verifySavedPin(
                entered = entered,
                onSuccess = { _done.value = true },
                error = "PIN 码错误"
            )
            LockMode.DISABLE_VERIFY -> verifySavedPin(
                entered = entered,
                onSuccess = ::clearLock,
                error = "PIN 码错误"
            )
        }
    }

    private fun verifySavedPin(entered: String, onSuccess: () -> Unit, error: String) {
        viewModelScope.launch {
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
                PinVerification.NO_MATCH -> onMismatch(error)
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

    private suspend fun onMismatch(message: String) {
        val attempts = _attempts.value + 1
        _pin.value = ""
        if (attempts >= MAX_ATTEMPTS) {
            val lockoutUntil = clock.millis() + LOCKOUT_MILLIS
            _attempts.value = MAX_ATTEMPTS
            if (!saveFailureState(MAX_ATTEMPTS, lockoutUntil)) return
            _errorMessage.value = "错误次数过多，请等待 30 秒"
            startCountdownIfNeeded(lockoutUntil)
        } else {
            _attempts.value = attempts
            if (!saveFailureState(attempts, 0L)) return
            _errorMessage.value = "$message，还剩 ${MAX_ATTEMPTS - attempts} 次"
        }
    }

    private fun startCountdownIfNeeded(lockoutUntil: Long) {
        countdownJob?.cancel()
        val remainingMillis = lockoutUntil - clock.millis()
        if (remainingMillis <= 0) {
            _lockoutRemaining.value = 0
            if (_attempts.value >= MAX_ATTEMPTS) {
                viewModelScope.launch { clearFailures() }
            }
            return
        }

        countdownJob = viewModelScope.launch {
            while (clock.millis() < lockoutUntil) {
                _lockoutRemaining.value =
                    ((lockoutUntil - clock.millis() + 999L) / 1000L).toInt()
                delay(250L)
            }
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
        _errorMessage.value = SECURITY_ERROR_MESSAGE
    }

    private fun advanceTo(nextMode: LockMode) {
        _pin.value = ""
        _mode.value = nextMode
        updateTitle()
    }

    private fun restartCreation(mode: LockMode, message: String) {
        firstPin = ""
        _mode.value = mode
        _pin.value = ""
        _errorMessage.value = message
        updateTitle()
    }

    private fun updateTitle() {
        _title.value = when (_mode.value) {
            LockMode.CREATE -> "设置 PIN 码"
            LockMode.CREATE_CONFIRM -> "再次输入 PIN 码"
            LockMode.VERIFY -> "输入 PIN 码"
            LockMode.CHANGE_OLD -> "请输入旧 PIN 码"
            LockMode.CHANGE_NEW -> "设置新 PIN 码"
            LockMode.CHANGE_CONFIRM -> "再次输入新 PIN 码"
            LockMode.DISABLE_VERIFY -> "输入当前 PIN 以关闭应用锁"
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
                title = "验证身份",
                subtitle = "使用指纹解锁起飞日记",
                negativeButtonText = "使用 PIN 码",
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
                            _errorMessage.value = "暂时无法使用指纹，请输入 PIN 码"
                        }
                        BiometricAuthResult.Cancelled -> {
                            biometricRequestInProgress = false
                        }
                    }
                },
                onFailedAttempt = {
                    _errorMessage.value = "指纹识别失败，请重试"
                }
            )
    }

    private companion object {
        const val PIN_LENGTH = 4
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 30_000L
        const val SECURITY_ERROR_MESSAGE = "设备安全模块暂时不可用，请稍后重试"
    }
}
