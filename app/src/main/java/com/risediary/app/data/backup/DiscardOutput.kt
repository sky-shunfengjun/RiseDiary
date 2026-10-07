package com.risediary.app.data.backup

import java.io.OutputStream

/** Avoids newer Java library APIs on Android 12, without buffering discarded data. */
internal object DiscardOutput : OutputStream() {
    override fun write(value: Int) = Unit
    override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
}
