package com.risediary.app.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Native SQLite contract regression; needs a phone, not just AndroidTest compilation. */
@RunWith(AndroidJUnit4::class)
class BackupStagingStoreDeviceTest {
    @Test fun createsStagingDatabaseBeforeReadingAnyBackup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "staging-contract-${UUID.randomUUID()}.db")
        try {
            BackupStagingStore(file).use { stage ->
                assertEquals(0, stage.count(BackupFiles.FLIGHTS))
                val row = StagedRow(BackupFiles.FLIGHTS, 7, "legacy record", null, 1000, 2000)
                stage.put(row)
                assertEquals(row, stage.match(row.copy(id = 99)))
                assertEquals(listOf(row), stage.batch(BackupFiles.FLIGHTS))
                stage.action(row.kind, row.id, "added")
                assertEquals(1, stage.actionCount(row.kind, "added"))
            }
            BackupStagingStore(file).use { stage ->
                assertEquals(1, stage.count(BackupFiles.FLIGHTS))
                stage.clear()
                assertEquals(0, stage.count(BackupFiles.FLIGHTS))
                assertEquals(0, stage.actionCount(BackupFiles.FLIGHTS, "added"))
            }
        } finally {
            android.database.sqlite.SQLiteDatabase.deleteDatabase(file)
        }
    }
}
