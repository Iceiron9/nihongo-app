package com.ice.nihongo.media

/** 一句字幕。confidence 是 Whisper 的平均信心（0~1），匯入的字幕固定為 1。 */
data class Segment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float = 1f
)

/** 語音辨識引擎的介面，實際由 WhisperEngine 實作，測試時可換成假的。 */
interface Recognizer {
    fun recognize(audio: FloatArray, beam: Int, prompt: String): List<Segment>
}
