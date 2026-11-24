package com.ice.nihongo.media

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * 用 Android 內建的解碼器，把任何音樂或影片的聲音
 * 轉成 Whisper 需要的 16kHz 單聲道 float。不需要另外裝 FFmpeg。
 */
object AudioDecoder {

    fun decode(ctx: Context, uri: Uri, progress: (Int) -> Unit = {}): FloatArray {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        val track = (0 until ex.trackCount).firstOrNull {
            ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run { ex.release(); error("這個檔案裡沒有聲音軌") }
        ex.selectTrack(track)
        val inFmt = ex.getTrackFormat(track)
        val durationUs = if (inFmt.containsKey(MediaFormat.KEY_DURATION)) inFmt.getLong(MediaFormat.KEY_DURATION) else 0L

        val codec = MediaCodec.createDecoderByType(inFmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(inFmt, null, null, 0)
        codec.start()

        var rate = inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var isFloat = false
        val pcm = FloatBuffer16k()

        val info = MediaCodec.BufferInfo()
        var inDone = false
        var outDone = false
        var lastPct = -1
        try {
            while (!outDone) {
                if (!inDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            if (durationUs > 0) {
                                val pct = (ex.sampleTime * 100 / durationUs).toInt()
                                if (pct != lastPct) { lastPct = pct; progress(pct) }
                            }
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        buf.order(ByteOrder.LITTLE_ENDIAN)
                        pcm.rate = rate
                        if (isFloat) {
                            val fb = buf.asFloatBuffer()
                            while (fb.remaining() >= channels) {
                                var s = 0f
                                repeat(channels) { s += fb.get() }
                                pcm.add(s / channels)
                            }
                        } else {
                            val sb = buf.asShortBuffer()
                            while (sb.remaining() >= channels) {
                                var s = 0f
                                repeat(channels) { s += sb.get() / 32768f }
                                pcm.add(s / channels)
                            }
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            ex.release()
        }
        return pcm.toArray()
    }
}
