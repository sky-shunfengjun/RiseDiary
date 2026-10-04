package com.risediary.app.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import java.io.File

/** Generated test media only: two seconds of silent black H.264, never a user file. */
internal object TestVideoFixtures {
    fun create(context: Context): File {
        val output = File(context.cacheDir, "risediary-test-fixtures/black.mp4")
        output.parentFile!!.mkdirs()
        if (output.length() > 0) return output
        val codec = MediaCodec.createEncoderByType("video/avc")
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var track = -1
        var samples = 0
        try {
            val format = MediaFormat.createVideoFormat("video/avc", 320, 240).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, 150_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 15)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val frame = ByteArray(320 * 240 * 3 / 2) { if (it < 320 * 240) 16 else 128.toByte() }
            val info = MediaCodec.BufferInfo()
            var frameIndex = 0
            var eosQueued = false
            var finished = false
            val deadline = SystemClock.elapsedRealtime() + 15_000
            while (!finished && SystemClock.elapsedRealtime() < deadline) {
                if (!eosQueued) {
                    val input = codec.dequeueInputBuffer(10_000)
                    if (input >= 0) {
                        if (frameIndex < 30) {
                            codec.getInputBuffer(input)!!.apply { clear(); put(frame) }
                            codec.queueInputBuffer(input, 0, frame.size, frameIndex * 1_000_000L / 15, 0)
                            frameIndex++
                        } else {
                            codec.queueInputBuffer(input, 0, 0, 2_000_000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosQueued = true
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted)
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    index >= 0 -> {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0) {
                            check(muxerStarted)
                            val buffer = codec.getOutputBuffer(index)!!
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buffer, info)
                            samples++
                        }
                        finished = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
            check(finished && samples > 0) { "Test video encoder did not complete" }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
        }
        return output
    }
}
