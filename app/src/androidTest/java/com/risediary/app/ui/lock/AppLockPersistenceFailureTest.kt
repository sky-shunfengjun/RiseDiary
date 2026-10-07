package com.risediary.app.ui.lock

import com.risediary.app.testing.retainForTest
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.UserPreferences
import com.risediary.app.security.BiometricAuthenticator
import com.risediary.app.security.PinSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Clock

/** Real PIN verifier + AppLockViewModel; only persistence failure is injected. */
@RunWith(AndroidJUnit4::class)
class AppLockPersistenceFailureTest {
    @Test
    fun retryingFailedPinChangeRequiresTheCurrentPinBeforeChangingIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val persistence = WriteFailingStore()
        val preferences = UserPreferences(persistence, DataMaintenanceGate())
        val security = PinSecurity()
        val previous = withContext(Dispatchers.Default) { security.createCredential("1234") }
        preferences.setAppLock(true, previous)
        val owner = ViewModelStore()
        try {
            val vm = withContext(Dispatchers.Main) {
                AppLockViewModel(preferences, security, BiometricAuthenticator(context), Clock.systemUTC(), context)
                    .also { owner.retainForTest("lock", it); it.init(LockMode.CHANGE_OLD) }
            }
            await { vm.ready.value }
            withContext(Dispatchers.Main) { listOf(1, 2, 3, 4).forEach(vm::onDigit) }
            await { vm.mode.value == LockMode.CHANGE_NEW }
            persistence.failWrites = true
            withContext(Dispatchers.Main) {
                listOf(9, 9, 9, 9).forEach(vm::onDigit)
                listOf(9, 9, 9, 9).forEach(vm::onDigit)
            }
            await { !vm.ready.value }
            assertFalse(vm.done.value)
            persistence.failWrites = false
            val replacement = withContext(Dispatchers.Default) { security.createCredential("5678") }
            preferences.setAppLock(true, replacement)
            withContext(Dispatchers.Main) { vm.retryLoad() }
            await { vm.ready.value }
            assertEquals(LockMode.CHANGE_OLD, vm.mode.value)
            assertNull(vm.verifiedCredential)
            assertEquals(replacement, preferences.securitySettings.first().credential)
        } finally {
            withContext(Dispatchers.Main) { owner.clear() }
        }
    }
    @Test
    fun correctPinDoesNotUnlockIfClearingFailuresCannotBeSavedAndRetryCanRecover() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val persistence = WriteFailingStore()
        val preferences = UserPreferences(persistence, DataMaintenanceGate())
        val security = PinSecurity()
        val credential = withContext(Dispatchers.Default) { security.createCredential("1234") }
        preferences.setAppLock(true, credential)
        preferences.setAppLockFailureState(3, 0)
        val owner = ViewModelStore()
        try {
            val vm = withContext(Dispatchers.Main) {
                AppLockViewModel(preferences, security, BiometricAuthenticator(context), Clock.systemUTC(), context)
                    .also { owner.retainForTest("lock", it); it.init(LockMode.VERIFY) }
            }
            await { vm.ready.value }
            persistence.failWrites = true
            withContext(Dispatchers.Main) { listOf(1, 2, 3, 4).forEach(vm::onDigit) }
            await { vm.errorMessage.value != null }
            assertFalse(vm.done.value)
            assertNull(vm.verifiedCredential)
            assertFalse("Persistence errors must expose retry instead of leaving input stuck", vm.ready.value)
            assertEquals(3, preferences.securitySettings.first().attempts)

            persistence.failWrites = false
            withContext(Dispatchers.Main) { vm.retryLoad() }
            await { vm.ready.value }
            withContext(Dispatchers.Main) { listOf(1, 2, 3, 4).forEach(vm::onDigit) }
            await { vm.done.value }
            assertEquals(credential, vm.verifiedCredential)
            assertEquals(0, preferences.securitySettings.first().attempts)
        } finally {
            withContext(Dispatchers.Main) { owner.clear() }
        }
    }

    @Test
    fun failureToStoreMismatchCannotEmitUnlockProofOrCompletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val persistence = WriteFailingStore()
        val preferences = UserPreferences(persistence, DataMaintenanceGate())
        val security = PinSecurity()
        preferences.setAppLock(true, withContext(Dispatchers.Default) { security.createCredential("1234") })
        val owner = ViewModelStore()
        try {
            val vm = withContext(Dispatchers.Main) {
                AppLockViewModel(preferences, security, BiometricAuthenticator(context), Clock.systemUTC(), context)
                    .also { owner.retainForTest("lock", it); it.init(LockMode.VERIFY) }
            }
            await { vm.ready.value }
            persistence.failWrites = true
            withContext(Dispatchers.Main) { listOf(9, 8, 7, 6).forEach(vm::onDigit) }
            await { !vm.ready.value }
            assertFalse(vm.done.value)
            assertNull(vm.verifiedCredential)
            assertTrue(vm.errorMessage.value != null)
        } finally {
            withContext(Dispatchers.Main) { owner.clear() }
        }
    }

    private suspend fun await(predicate: () -> Boolean) = withTimeout(5_000) {
        while (!withContext(Dispatchers.Main) { predicate() }) delay(10)
    }

    private class WriteFailingStore : DataStore<Preferences> {
        private val values = MutableStateFlow(emptyPreferences())
        var failWrites = false
        override val data: Flow<Preferences> = values
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (failWrites) throw IOException("storage unavailable")
            val result = transform(values.value)
            values.value = result
            return result
        }
    }
}