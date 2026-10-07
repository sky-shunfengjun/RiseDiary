package com.risediary.app.data.backup

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.io.File

internal data class StagedRow(val kind: String, val id: Long, val payload: String,
    val globalId: String?, val firstTime: Long = 0, val secondTime: Long = 0, val submission: String? = null)

/** Disk-backed identities and payloads. At most one bounded page is materialized. */
internal class BackupStagingStore(val file: File) : Closeable {
    private val db = SQLiteDatabase.openOrCreateDatabase(file, null)
    init {
        try {
            // journal_mode returns a row even when setting it; execSQL rejects result rows.
            db.rawQuery("PRAGMA journal_mode=OFF", null).use { cursor ->
                check(cursor.moveToFirst()) { "无法初始化恢复临时数据库" }
            }
            db.execSQL("PRAGMA synchronous=OFF")
            db.execSQL("CREATE TABLE IF NOT EXISTS rows(kind TEXT NOT NULL, id INTEGER NOT NULL, payload TEXT NOT NULL, globalId TEXT, firstTime INTEGER NOT NULL, secondTime INTEGER NOT NULL, submission TEXT, PRIMARY KEY(kind,id))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS identity ON rows(kind,globalId) WHERE globalId IS NOT NULL")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS submit_key ON rows(submission) WHERE kind='flights.json' AND submission IS NOT NULL")
            db.execSQL("CREATE INDEX IF NOT EXISTS legacy ON rows(kind,firstTime,secondTime,id)")
            db.execSQL("CREATE TABLE IF NOT EXISTS actions(kind TEXT NOT NULL, id INTEGER NOT NULL, action TEXT NOT NULL, PRIMARY KEY(kind,id))")
        } catch (failure: Throwable) {
            // A failed constructor never reaches the workflow's list of owned stores.
            runCatching(db::close).exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }
    fun put(row: StagedRow, update: Boolean = false) {
        val values = ContentValues().apply {
            put("kind", row.kind); put("id", row.id); put("payload", row.payload); put("globalId", row.globalId)
            put("firstTime", row.firstTime); put("secondTime", row.secondTime); put("submission", row.submission)
        }
        if (update) check(db.update("rows", values, "kind=? AND id=?", arrayOf(row.kind, row.id.toString())) == 1)
        else db.insertOrThrow("rows", null, values)
    }
    fun action(kind: String, id: Long, action: String) {
        db.execSQL("INSERT INTO actions(kind,id,action) VALUES(?,?,?) ON CONFLICT(kind,id) DO UPDATE SET action=CASE WHEN actions.action='added' THEN 'added' ELSE excluded.action END",
            arrayOf<Any>(kind, id, action))
    }
    fun actionCountFor(kind: String, id: Long): String = db.rawQuery("SELECT action FROM actions WHERE kind=? AND id=?",arrayOf(kind,id.toString())).use {
        if (it.moveToFirst()) it.getString(0) else ""
    }
    fun actionCount(kind: String, action: String): Int = scalar("SELECT COUNT(*) FROM actions WHERE kind=? AND action=?", arrayOf(kind,action)).toInt()
    fun count(kind: String): Int = scalar("SELECT COUNT(*) FROM rows WHERE kind=?", arrayOf(kind)).toInt()
    fun maxId(kind: String): Long = scalar("SELECT COALESCE(MAX(id),0) FROM rows WHERE kind=?", arrayOf(kind))
    private fun scalar(sql: String, args: Array<String>): Long = db.rawQuery(sql,args).use { it.moveToFirst(); it.getLong(0) }
    fun match(row: StagedRow): StagedRow? = query(
        if (row.globalId != null) "kind=? AND globalId=?" else "kind=? AND firstTime=? AND secondTime=?",
        if (row.globalId != null) arrayOf(row.kind,row.globalId) else arrayOf(row.kind,row.firstTime.toString(),row.secondTime.toString()))
    fun submission(key: String): StagedRow? = query("kind=? AND submission=?", arrayOf(BackupFiles.FLIGHTS,key))
    private fun query(where: String, args: Array<String>): StagedRow? = db.query("rows", null, where,args,null,null,"id ASC","1").use {
        if (it.moveToFirst()) read(it) else null
    }
    fun batch(kind: String, after: Long = -1): List<StagedRow> {
        val args = arrayOf(kind,after.toString())
        val sizes = db.rawQuery("SELECT length(payload)*2+256 FROM rows WHERE kind=? AND id>? ORDER BY id LIMIT 500",args).use { cursor ->
            buildList { while(cursor.moveToNext()) add(cursor.getLong(0)) }
        }
        if (sizes.isEmpty()) return emptyList()
        return db.query("rows",null,"kind=? AND id>?",args,null,null,"id ASC",backupBatchSize(sizes).toString()).use { cursor ->
            buildList { while (cursor.moveToNext()) add(read(cursor)) }
        }
    }
    suspend fun each(kind: String, consume: suspend (StagedRow) -> Unit) {
        var after = -1L
        while (true) {
            val rows = batch(kind, after)
            if (rows.isEmpty()) break
            for (row in rows) { consume(row); after = row.id }
        }
    }
    fun clear() { db.execSQL("DELETE FROM rows"); db.execSQL("DELETE FROM actions") }
    private fun read(cursor: android.database.Cursor) = StagedRow(
        cursor.getString(cursor.getColumnIndexOrThrow("kind")), cursor.getLong(cursor.getColumnIndexOrThrow("id")),
        cursor.getString(cursor.getColumnIndexOrThrow("payload")), cursor.getString(cursor.getColumnIndexOrThrow("globalId")),
        cursor.getLong(cursor.getColumnIndexOrThrow("firstTime")), cursor.getLong(cursor.getColumnIndexOrThrow("secondTime")),
        cursor.getString(cursor.getColumnIndexOrThrow("submission")))
    override fun close() = db.close()
}
internal object BackupFiles {
    const val FLIGHTS = "flights.json"
    const val LENGTHS = "length_records.json"
    const val TAGS = "tags.json"
    const val ACHIEVEMENTS = "achievements.json"
    const val SETTINGS = "settings.json"
    val arrays = listOf(FLIGHTS,LENGTHS,TAGS,ACHIEVEMENTS)
    val all = arrays + SETTINGS
    const val ENTRY_BYTES = 32 * 1024 * 1024
    const val TOTAL_BYTES = 128L * 1024 * 1024
    const val SPACE_RESERVE = 16L * 1024 * 1024
}
