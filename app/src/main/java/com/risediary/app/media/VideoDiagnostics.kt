package com.risediary.app.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class VideoDiagnosticSnapshot(
    val decoder: String? = null,
    val width: Int = 0, val height: Int = 0, val frameRate: Float = 0f,
    val droppedFrames: Int = 0,
    val buffering: String = "等待播放", val bufferEvents: Int = 0,
    val bufferedMillis: Long = 0,
    val errorCode: Int? = null,
    val prepared: Boolean = false
) {
    fun report(): String = "解码器：${decoder ?: "尚未取得"}\n分辨率：${width}×${height}\n帧率：${if (frameRate > 0) frameRate else "未知"}\n掉帧：$droppedFrames\n状态：$buffering\n播放中等待次数：$bufferEvents\n已缓冲：${bufferedMillis / 1000f} 秒\n错误编号：${errorCode ?: "无"}\n资源：${if (prepared) "已准备" else "已释放"}"
}

/** Opt-in, memory-only and deliberately unable to accept a URI or file name. */
@Singleton
class VideoDiagnostics @Inject constructor() {
    private var owner: Any? = null
    private val active = MutableStateFlow(false)
    val enabled = active.asStateFlow()
    private val current = MutableStateFlow(VideoDiagnosticSnapshot())
    val snapshot = current.asStateFlow()
    fun setEnabled(value: Boolean) {
        active.value = value
        current.value = VideoDiagnosticSnapshot()
    }
    internal fun attach(token: Any, fresh: Boolean) {
        val changed = owner !== token
        owner = token
        if (active.value) current.value = if (fresh || changed) VideoDiagnosticSnapshot(prepared = true)
            else current.value.copy(prepared = true)
    }
    internal fun update(token: Any, block: (VideoDiagnosticSnapshot) -> VideoDiagnosticSnapshot) {
        if (active.value && owner === token) current.value = block(current.value)
    }
    internal fun release(token: Any) {
        if (owner === token) update(token) { it.copy(prepared = false) }
    }
}
