package com.ice.nihongo.whisper

import android.content.Context
import com.ice.nihongo.media.Recognizer
import com.ice.nihongo.media.Segment
import java.io.File

/** whisper_jni.cpp 裡的函式 */
object WhisperLib {
    init { System.loadLibrary("nihongo_whisper") }
    external fun init(modelPath: String): Long
    external fun transcribe(ctx: Long, audio: FloatArray, threads: Int, beam: Int, prompt: String): ByteArray
    external fun free(ctx: Long)
}

/**
 * 模型放在 APK 的 assets 裡，第一次啟動時複製到手機儲存空間（約 10~20 秒），之後直接讀取。
 */
class WhisperEngine(private val ctx: Context) : Recognizer {
    private var handle = 0L

    val ready get() = handle != 0L

    @Synchronized
    fun load() {
        if (handle != 0L) return
        val name = MODEL
        val f = File(ctx.filesDir, name)
        val expected = ctx.assets.openFd("models/$name").use { it.length }
        if (!f.exists() || f.length() != expected) {
            val tmp = File(ctx.filesDir, "$name.tmp")
            ctx.assets.open("models/$name").use { i -> tmp.outputStream().use { o -> i.copyTo(o, 1 shl 20) } }
            tmp.renameTo(f)
        }
        handle = WhisperLib.init(f.absolutePath)
        check(handle != 0L) { "Whisper 模型載入失敗" }
    }

    override fun recognize(audio: FloatArray, beam: Int, prompt: String): List<Segment> {
        load()
        // JNI 字串不支援 emoji 之類的 4 位元組字元，先過濾掉
        val safePrompt = prompt.filter { !it.isSurrogate() }.takeLast(200)
        val threads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)
        val raw = String(WhisperLib.transcribe(handle, audio, threads, beam, safePrompt), Charsets.UTF_8)
        return parse(raw)
    }

    companion object {
        const val MODEL = "ggml-small-q5_1.bin"

        fun parse(raw: String): List<Segment> =
            raw.lines().filter { it.isNotBlank() }.mapNotNull { line ->
                val p = line.split("|", limit = 4)
                if (p.size < 4) return@mapNotNull null
                Segment(
                    p[0].toLongOrNull() ?: 0, p[1].toLongOrNull() ?: 0,
                    p[3].trim(), p[2].toFloatOrNull() ?: 0f
                )
            }
    }
}
