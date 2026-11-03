package com.risediary.app.media

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.risediary.app.data.library.LibraryFileStorage
import java.io.File
import java.io.FileNotFoundException

/** App-private, read-only, seekable bytes. It never enters the device-video grant registry. */
class LibraryContentProvider : ContentProvider() {
    override fun onCreate() = true
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r" || uri.pathSegments.size != 2 || uri.pathSegments[0] != "video")
            throw FileNotFoundException("无效素材")
        val file = try {
            LibraryFileStorage(File(requireNotNull(context).filesDir, "RiseDiaryLibrary")).media(uri.pathSegments[1])
        } catch (_: IllegalArgumentException) { throw FileNotFoundException("无效素材编号") }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun getType(uri: Uri) = "video/mp4"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
}
