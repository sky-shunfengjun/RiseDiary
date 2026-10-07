package com.risediary.app.media

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Only the debug app and this test package can read the generated fixtures. */
class TestVideoProvider : ContentProvider() {
    override fun onCreate() = true
    private fun verifyCaller() {
        val packages = requireNotNull(context).packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        if (packages.none { it == "com.risediary.app.dev" || it == requireNotNull(context).packageName }) {
            throw SecurityException("Test fixture access only")
        }
    }
    override fun getType(uri: Uri): String {
        verifyCaller()
        return if (uri.path == "/backup") "application/zip" else "video/mp4"
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        verifyCaller()
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("black.mp4")) }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        verifyCaller()
        val file = when (uri.path) {
            "/readable" -> TestVideoFixtures.create(requireNotNull(context))
            "/backup" -> File(requireNotNull(context).cacheDir, "risediary-test-fixtures/backup.zip").apply { parentFile!!.mkdirs() }
            "/denied" -> throw SecurityException("Access denied")
            else -> throw FileNotFoundException("Missing fixture")
        }
        if (uri.path != "/backup" && mode != "r") throw SecurityException("Read only")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()

    companion object {
        val READABLE: Uri = Uri.parse("content://com.risediary.app.dev.test.video/readable")
        val MISSING: Uri = Uri.parse("content://com.risediary.app.dev.test.video/missing")
        val DENIED: Uri = Uri.parse("content://com.risediary.app.dev.test.video/denied")
        val BACKUP: Uri = Uri.parse("content://com.risediary.app.dev.test.video/backup")
    }
}
