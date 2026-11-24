package com.ice.nihongo.media

import com.ice.nihongo.kana.KanaData

/** 分詞結果中的一個詞 */
data class Tok(
    val surface: String,   // 句中的寫法
    val base: String,      // 原形
    val reading: String,   // 讀音（片假名），未知時為空
    val pos: String,       // 詞性：名詞、動詞…
    val known: Boolean     // 是否在辭典裡
)

/** 日文分詞器介面，實際由 KuromojiAnalyzer 實作 */
fun interface Analyzer {
    fun tokens(text: String): List<Tok>
}

/**
 * 離線自動校正：
 * 1. 清掉 Whisper 常見的「幻聽」句子與重複迴圈
 * 2. 用辭典打分數，判斷一句話通不通順
 * 3. 有兩個版本時，挑比較通順、也和整首歌上下文比較一致的那個
 */
class Corrector(private val an: Analyzer) {

    // Whisper 在音樂、空白處最常編出來的句子
    private val hallucinations = listOf(
        "ご視聴ありがとうございました", "ご清聴ありがとうございました", "チャンネル登録",
        "字幕", "最後までご覧いただき", "おやすみなさい。", "お疲れ様でした。"
    )
    private val noise = Regex("""[♪♫♬〜～]+|\((?:音楽|拍手|笑)\)|（(?:音楽|拍手|笑)）|\[(?:音楽|拍手|笑)]""")

    /** 去掉雜訊與重複，回傳乾淨的句子 */
    fun clean(segs: List<Segment>): List<Segment> {
        val out = mutableListOf<Segment>()
        for (s in segs) {
            var t = s.text.replace(noise, " ").replace(Regex("""\s+"""), " ").trim()
            t = collapseRepeats(t)
            if (t.isEmpty()) continue
            if (hallucinations.any { t.contains(it) } && s.confidence < 0.8f) continue
            // 同一句連續出現 3 次以上，多半是 Whisper 卡住了，只留 2 次
            val last2 = out.takeLast(2)
            if (last2.size == 2 && last2.all { it.text == t }) continue
            out += s.copy(text = t)
        }
        return out
    }

    /** 「あいうあいうあいうあいう」這種同一段重複 4 次以上的，縮成 2 次 */
    fun collapseRepeats(t: String): String {
        var s = t
        for (len in 1..minOf(12, s.length / 4)) {
            val re = Regex("(.{$len})\\1{3,}")
            s = s.replace(re) { it.groupValues[1].repeat(2) }
        }
        return s
    }

    /** 分數越低越通順：辭典裡沒有的詞扣分重，零碎的單字扣分輕，和上下文相同的詞加分 */
    fun score(sentence: String, context: Set<String> = emptySet()): Double {
        val toks = an.tokens(sentence).filter { it.pos != "記号" && it.surface.isNotBlank() }
        if (toks.isEmpty()) return 10.0
        var s = 0.0
        for (t in toks) {
            if (!t.known) s += 3.0
            if (t.surface.length == 1 && t.pos == "名詞" && t.surface[0] !in 'ぁ'..'ゖ') s += 0.7
            if (t.base in context) s -= 0.4
        }
        return s / toks.size
    }

    /** 這句是不是需要重新辨識 */
    fun needsRetry(seg: Segment): Boolean =
        seg.confidence < 0.55f || score(seg.text) > 0.9

    /** 全曲重要詞彙：出現 2 次以上的名詞、動詞、形容詞，當作上下文 */
    fun contextWords(lines: List<String>): Set<String> =
        lines.flatMap { l -> an.tokens(l).filter { it.known && it.pos in CONTENT }.map { it.base } }
            .groupingBy { it }.eachCount().filter { it.value >= 2 }.keys

    /** 兩個版本挑一個；分數差不多時，選信心比較高的 */
    fun choose(a: Segment, b: Segment, context: Set<String>): Segment {
        val sa = score(a.text, context)
        val sb = score(b.text, context)
        return when {
            sb < sa - 0.15 -> b
            sa < sb - 0.15 -> a
            else -> if (b.confidence > a.confidence) b else a
        }
    }

    /** 挑單字用：名詞、動詞、形容詞，附平假名讀音 */
    fun words(sentence: String): List<Pair<String, String>> =
        an.tokens(sentence)
            .filter { it.known && it.pos in CONTENT && it.base !in TRIVIAL && !isOnlyKanaParticle(it) }
            .map { it.base to KanaData.toHiragana(it.reading) }
            .distinctBy { it.first }

    /** 整句轉成平假名，給打字練習用 */
    fun reading(sentence: String): String =
        an.tokens(sentence).joinToString("") { t ->
            KanaData.toHiragana(t.reading.ifEmpty { t.surface })
        }.filter { it in 'ぁ'..'ゖ' || it == 'ー' }

    private fun isOnlyKanaParticle(t: Tok) = t.base.length == 1 && t.base[0] in 'ぁ'..'ゖ'

    companion object {
        val CONTENT = setOf("名詞", "動詞", "形容詞", "副詞")
        // 太基本、不值得做成卡片的詞
        val TRIVIAL = setOf("する", "いる", "ある", "なる", "れる", "られる", "こと", "もの", "よう", "の", "ん", "さ")
    }
}
