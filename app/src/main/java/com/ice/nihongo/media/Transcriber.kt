package com.ice.nihongo.media

/**
 * 整個辨識流程：
 * 1. 快速辨識一遍（greedy）
 * 2. 清掉雜訊、重複
 * 3. 信心低或不通順的句子，把那一段音訊切出來，
 *    用前兩句當提示（讓 Whisper 依上下文判斷）、beam search 重新辨識一次
 * 4. 用辭典和全曲上下文，在兩個版本中挑比較好的
 */
class Transcriber(
    private val rec: Recognizer,
    private val corrector: Corrector,
    private val maxRetries: Int = 40
) {
    fun run(
        audio: FloatArray,
        title: String = "",
        progress: (String) -> Unit = {}
    ): List<Segment> {
        val basePrompt = if (title.isBlank()) "日本語の歌詞です。" else "$title。日本語の歌詞です。"

        progress("Whisper 辨識中…")
        val first = corrector.clean(rec.recognize(audio, 1, basePrompt))
        if (first.isEmpty()) return first

        val context = corrector.contextWords(first.map { it.text })
        val result = first.toMutableList()
        val targets = first.indices.filter { corrector.needsRetry(first[it]) }.take(maxRetries)

        targets.forEachIndexed { n, i ->
            progress("依上下文校正 ${n + 1}/${targets.size}…")
            val seg = result[i]
            val prev = result.subList(maxOf(0, i - 2), i).joinToString("") { it.text }
            val slice = slice(audio, seg.startMs - 300, seg.endMs + 300)
            if (slice.size < 8000) return@forEachIndexed // 太短（< 0.5 秒）就不重跑
            val again = rec.recognize(slice, 5, basePrompt + prev)
            if (again.isEmpty()) return@forEachIndexed
            val merged = Segment(
                seg.startMs, seg.endMs,
                corrector.collapseRepeats(again.joinToString("") { it.text.trim() }),
                again.map { it.confidence }.average().toFloat()
            )
            result[i] = corrector.choose(seg, merged, context)
        }
        return result
    }

    companion object {
        const val RATE = 16_000

        fun slice(audio: FloatArray, fromMs: Long, toMs: Long): FloatArray {
            val a = (maxOf(0L, fromMs) * RATE / 1000).toInt().coerceAtMost(audio.size)
            val b = (toMs * RATE / 1000).toInt().coerceIn(a, audio.size)
            return audio.copyOfRange(a, b)
        }
    }
}
