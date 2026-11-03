package com.risediary.app.data.library

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryStorage internal constructor(val files: LibraryFileStorage) {
    @Inject constructor(@ApplicationContext context: Context) : this(LibraryFileStorage(File(context.filesDir, "RiseDiaryLibrary")))
}
