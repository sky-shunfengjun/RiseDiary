package com.risediary.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import com.risediary.app.R

/** Copies the original message, never the ellipsized display; keeps the error on screen. */
@Composable
internal fun Modifier.copyErrorOnLongPress(message: String, enabled: Boolean = true): Modifier {
    if (!enabled || message.isBlank()) return this
    val context = LocalContext.current
    val actionLabel = stringResource(R.string.error_copy_full_text)
    val copied = stringResource(R.string.error_copied)
    val clipLabel = stringResource(R.string.error_clipboard_label)
    val copy = remember(context, message, copied, clipLabel) {
        {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, message))
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }
        }
    }
    return pointerInput(copy) {
        detectTapGestures(onLongPress = { copy() })
    }.semantics {
        onLongClick(actionLabel) { copy(); true }
    }
}
