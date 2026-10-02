package com.risediary.app.data.repository

import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.DataWriteConflictException
import com.risediary.app.data.dao.FlightDao
import com.risediary.app.data.dao.LengthRecordDao
import com.risediary.app.data.dao.TagDao
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.data.entity.Tag
import com.risediary.app.util.LocalCalendarContext
import java.lang.reflect.Proxy
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe

/** Tests the production repository decision; DAO proxies expose a changed snapshot and count real delete calls. */
class RepositoryWriteConflictTest {
    private val oldFlight = Flight(7, 1_000L, 61_000L, 60, 1, 2f,
        ejaculationDistanceCm = null, methodTags = "[]", moodNote = "old", createdAt = 1_000L, updatedAt = 1_000L)
    private val oldLength = LengthRecord(7, 1_000L, 10f, 15f, "old")
    private val oldTag = Tag(7, "old", "#123456", 0)

    @Test fun changedFlightWithSameIdIsNotDeleted() = runBlocking {
        assertFlightDeleteRefused(oldFlight.copy(moodNote = "restored content"))
    }
    @Test fun missingFlightIsNotSilentlyReportedDeleted() = runBlocking { assertFlightDeleteRefused(null) }
    @Test fun changedLengthWithSameIdIsNotDeleted() = runBlocking {
        assertLengthDeleteRefused(oldLength.copy(erectLengthCm = 16f))
    }
    @Test fun missingLengthIsNotSilentlyReportedDeleted() = runBlocking { assertLengthDeleteRefused(null) }
    @Test fun changedTagWithSameIdIsNotDeleted() = runBlocking {
        assertTagDeleteRefused(oldTag.copy(name = "restored tag"))
    }
    @Test fun missingTagIsNotSilentlyReportedDeleted() = runBlocking { assertTagDeleteRefused(null) }

    @Test fun unchangedFlightIsDeletedOnce() = runBlocking {
        val deletes = AtomicInteger()
        val repo = RoomFlightRepository(dao(FlightDao::class.java, { oldFlight }, deletes),
            LocalCalendarContext(Clock.systemUTC()), DataMaintenanceGate())
        repo.delete(oldFlight)
        assertEquals(1, deletes.get())
    }
    @Test fun unchangedLengthIsDeletedOnce() = runBlocking {
        val deletes = AtomicInteger()
        val repo = RoomLengthRecordRepository(dao(LengthRecordDao::class.java, { oldLength }, deletes),
            LocalCalendarContext(Clock.systemUTC()), DataMaintenanceGate())
        repo.delete(oldLength)
        assertEquals(1, deletes.get())
    }
    @Test fun unchangedTagIsDeletedOnce() = runBlocking {
        val deletes = AtomicInteger()
        val repo = tagRepository(dao(TagDao::class.java, { oldTag }, deletes))
        repo.delete(oldTag)
        assertEquals(1, deletes.get())
    }

    private suspend fun assertFlightDeleteRefused(current: Flight?) {
        val deletes = AtomicInteger()
        val repo = RoomFlightRepository(dao(FlightDao::class.java, { current }, deletes),
            LocalCalendarContext(Clock.systemUTC()), DataMaintenanceGate())
        val failure = runCatching { repo.delete(oldFlight) }.exceptionOrNull()
        assertNotNull("A changed or absent row must reject an old deletion request", failure)
        assertTrue("Conflict must use the recoverable conflict result", failure is DataWriteConflictException)
        assertEquals("The guarded repository must not call DAO delete", 0, deletes.get())
    }
    private suspend fun assertLengthDeleteRefused(current: LengthRecord?) {
        val deletes = AtomicInteger()
        val repo = RoomLengthRecordRepository(dao(LengthRecordDao::class.java, { current }, deletes),
            LocalCalendarContext(Clock.systemUTC()), DataMaintenanceGate())
        val failure = runCatching { repo.delete(oldLength) }.exceptionOrNull()
        assertNotNull("A changed or absent row must reject an old deletion request", failure)
        assertTrue("Conflict must use the recoverable conflict result", failure is DataWriteConflictException)
        assertEquals("The guarded repository must not call DAO delete", 0, deletes.get())
    }
    private suspend fun assertTagDeleteRefused(current: Tag?) {
        val deletes = AtomicInteger()
        val repo = tagRepository(dao(TagDao::class.java, { current }, deletes))
        val failure = runCatching { repo.delete(oldTag) }.exceptionOrNull()
        assertNotNull("A changed or absent row must reject an old deletion request", failure)
        assertTrue("Conflict must use the recoverable conflict result", failure is DataWriteConflictException)
        assertEquals("The guarded repository must not call DAO delete", 0, deletes.get())
    }

    private fun tagRepository(dao: TagDao): RoomTagRepository {
        // delete uses only the DAO and the gate. Allocate the actual repository without constructing its
        // unused Android Room database; this is not a replacement implementation of delete.
        val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null) as Unsafe
        val repo = unsafe.allocateInstance(RoomTagRepository::class.java) as RoomTagRepository
        repo.javaClass.getDeclaredField("dao").apply { isAccessible = true }.set(repo, dao)
        repo.javaClass.getDeclaredField("maintenanceGate").apply { isAccessible = true }.set(repo, DataMaintenanceGate())
        return repo
    }

    private fun <T : Any> dao(type: Class<T>, current: () -> Any?, deletes: AtomicInteger): T = requireNotNull(type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            when (method.name) {
                "getAllFlow" -> flowOf(emptyList<Any>())
                "getById" -> current()
                "delete" -> { deletes.incrementAndGet(); Unit }
                else -> throw AssertionError("Unexpected DAO call ${method.name}")
            }
        }
    ))
}