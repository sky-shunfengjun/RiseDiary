package com.risediary.app.data.backup

import androidx.datastore.preferences.core.*
import com.risediary.app.data.entity.*
import com.risediary.app.data.draft.RecordDraftEntity
import com.risediary.app.service.TimerSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*
import java.security.MessageDigest

class RecoveryStreamingTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun journalLargerThanOldLimitUsesStreamsAndReplaysOneRowAtATime() = runBlocking {
        val file=temporary.newFile()
        file.delete()
        val note="n".repeat(1024*1024)
        val storage=StreamingDisk(file)
        val journal=BackupRecoveryJournal(storage)
        journal.persist(object : RecoverySource {
            override val preferences=emptyPreferences()
            override val timer=TimerSession()
            override fun flights(emit: (Flight)->Unit) {
                repeat(35) { index -> emit(Flight(index+1L,1000,61000,60,null,2f,"milliliters",null,"[]",note,createdAt=1000,updatedAt=1000,
                    globalId="11111111-1111-1111-1111-"+index.toString().padStart(12,'0'))) }
            }
            override fun lengths(emit: (LengthRecord)->Unit)=Unit
            override fun tags(emit: (Tag)->Unit)=Unit
            override fun achievements(emit: (Achievement)->Unit)=Unit
            override fun drafts(emit: (RecordDraftEntity)->Unit)=Unit
        })
        assertTrue(file.length()>64L*1024*1024)
        var rows=0
        journal.replay(object : RecoverySink {
            override suspend fun flight(row: Flight) { rows++; assertEquals(rows.toLong(),row.id); assertEquals(note,row.moodNote) }
            override suspend fun length(row: LengthRecord)=Unit
            override suspend fun tag(row: Tag)=Unit
            override suspend fun achievement(row: Achievement)=Unit
            override suspend fun draft(row: RecordDraftEntity)=Unit
            override suspend fun settings(preferences: Preferences,timer: TimerSession)=Unit
        })
        assertEquals(35,rows); assertTrue(journal.pending())
    }
    @Test fun previousVersionOneProtectionEnvelopeRemainsReadable() {
        // Hand-built historical v1: four empty tables, empty preferences/drafts, default timer.
        val payload=ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
            repeat(6) { out.writeInt(0) }; out.writeInt(2); out.writeChar('{'.code); out.writeChar('}'.code)
        } }.toByteArray()
        val file=temporary.newFile()
        DataOutputStream(file.outputStream()).use {
            it.writeInt(0x52444331); it.writeInt(1); it.writeInt(payload.size)
            it.write(MessageDigest.getInstance("SHA-256").digest(payload)); it.write(payload)
        }
        val restored=BackupRecoveryJournal(StreamingDisk(file)).load()!!
        assertTrue(restored.flights.isEmpty()); assertTrue(restored.lengthRecords.isEmpty())
        assertEquals(TimerSession(),restored.timer)
    }
    private class StreamingDisk(private val file: File): BackupRecoveryStorage {
        override fun exists()=file.exists()
        override fun read(): ByteArray=error("Whole-file read is forbidden")
        override fun write(bytes: ByteArray)=error("Whole-file write is forbidden")
        override fun openRead(): InputStream=file.inputStream()
        override fun writeStream(block: (OutputStream)->Unit) { file.outputStream().buffered().use(block) }
        override fun delete() { file.delete() }
    }
}
