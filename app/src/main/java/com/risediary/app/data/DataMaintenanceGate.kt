package com.risediary.app.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.risediary.app.data.backup.BackupRecoveryJournal
import com.risediary.app.data.backup.newBackupRecoveryJournal
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

/** Only data replacement is exclusive; timer and update persistence use separate stores. */
@Singleton
class DataMaintenanceGate() {
    internal var recoveryJournal: BackupRecoveryJournal? = null
        private set
    @Inject constructor(@ApplicationContext context: Context) : this() {
        attachRecoveryJournal(newBackupRecoveryJournal(context))
    }
    internal constructor(journal: BackupRecoveryJournal) : this() { attachRecoveryJournal(journal) }
    enum class State { IDLE, WORKING, RECOVERY_REQUIRED }
    private val writes = Mutex()
    private val operation = Mutex()
    private val guard = Any()
    private var generation = 0L
    private val mutableState = MutableStateFlow(State.IDLE)
    val state = mutableState.asStateFlow()
    private val mutableNotices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices = mutableNotices.asSharedFlow()

    private fun attachRecoveryJournal(journal: BackupRecoveryJournal) = synchronized(guard) {
        if (recoveryJournal == null) recoveryJournal = journal
        if (recoveryJournal?.pending() == true) mutableState.value = State.RECOVERY_REQUIRED
    }

    /** Directly constructed callers share the same startup protection as the injected gate. */
    internal fun recoveryJournal(context: Context): BackupRecoveryJournal = synchronized(guard) {
        if (recoveryJournal == null) attachRecoveryJournal(newBackupRecoveryJournal(context))
        checkNotNull(recoveryJournal)
    }

    /** Call while holding the write permit, so replacement cannot occur between the check and write. */
    fun <T : Any> requireCurrent(expected: T, current: T?) {
        if (expected != current) {
            val message = "记录已变化或移除，未执行此次操作；草稿保留，请返回后重新打开。"
            mutableNotices.tryEmit(message)
            throw DataWriteConflictException(message)
        }
    }

    fun snapshotGeneration(): Long = synchronized(guard) { generation }

    /** The caller holds write(); an old form cannot repopulate newly restored or cleared data. */
    fun requireGeneration(expected: Long) = synchronized(guard) {
        if (expected != generation || mutableState.value != State.IDLE) rejectBusyWrite()
    }

    private class Permit(val owner: DataMaintenanceGate, val generation: Long) :
        AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Permit>
    }
    private class Inside(val owner: DataMaintenanceGate) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Inside>
    }
    private fun rejectBusyWrite(): Nothing {
        mutableNotices.tryEmit(if (mutableState.value == State.IDLE)
            "数据已更新，本次操作未执行，请重新打开后重试。"
        else "当前数据暂时只读，请在操作完成或还原成功后重试。")
        throw DataMaintenanceBusyException()
    }
    private fun capture(): Permit = synchronized(guard) {
        if (mutableState.value != State.IDLE) rejectBusyWrite()
        Permit(this, generation)
    }
    private fun validate(permit: Permit) = synchronized(guard) {
        if (mutableState.value != State.IDLE || permit.generation != generation) {
            rejectBusyWrite()
        }
    }

    fun launchWrite(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit): Job {
        val permit = try { capture() } catch (_: DataMaintenanceBusyException) {
            return scope.launch { throw DataMaintenanceBusyException() }
        }
        return scope.launch(permit) { write { block() } }
    }

    suspend fun <T> write(block: suspend () -> T): T {
        if (currentCoroutineContext()[Inside]?.owner === this) return block()
        val permit = currentCoroutineContext()[Permit]?.takeIf { it.owner === this } ?: capture()
        validate(permit)
        return writes.withLock {
            validate(permit)
            withContext(Inside(this)) { block() }
        }
    }

    /** Maintenance bypass is only valid inside this lock; normal writes cannot join it. */
    internal suspend fun <T> maintenance(recovery: Boolean = false, block: suspend () -> T): T {
        if (!operation.tryLock()) throw DataMaintenanceBusyException()
        try {
            synchronized(guard) {
                if (!recovery && mutableState.value != State.IDLE) throw DataMaintenanceBusyException()
                generation++
                mutableState.value = State.WORKING
            }
            return withContext(NonCancellable) {
                writes.withLock {
                    try { block() } finally {
                        synchronized(guard) {
                            if (mutableState.value == State.WORKING) {
                                mutableState.value = if (recoveryJournal?.pending() == true) State.RECOVERY_REQUIRED else State.IDLE
                            }
                        }
                    }
                }
            }
        } finally { operation.unlock() }
    }
    internal fun requireRecovery() { mutableState.value = State.RECOVERY_REQUIRED }
}

/** Cancellation semantics prevent unhandled UI jobs from crashing the app. */
class DataMaintenanceBusyException : CancellationException("数据恢复或清除中，请稍后再保存")
class DataWriteConflictException(message: String) : CancellationException(message)
