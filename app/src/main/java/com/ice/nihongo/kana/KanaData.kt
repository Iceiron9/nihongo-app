package com.ice.nihongo.kana

/** 五十音資料：平假名、片假名，含濁音、半濁音、拗音。 */
object KanaData {
    private const val ROMA =
        "a i u e o ka ki ku ke ko sa shi su se so ta chi tsu te to na ni nu ne no " +
        "ha hi fu he ho ma mi mu me mo ya yu yo ra ri ru re ro wa wo n " +
        "ga gi gu ge go za ji zu ze zo da ji zu de do ba bi bu be bo pa pi pu pe po"
    private const val HIRA =
        "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん" +
        "がぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽ"

    // 拗音：き＋ゃ → きゃ
    private val YOON_BASE = listOf(
        "き" to "k", "し" to "sh", "ち" to "ch", "に" to "n", "ひ" to "h", "み" to "m", "り" to "r",
        "ぎ" to "g", "じ" to "j", "び" to "b", "ぴ" to "p"
    )

    fun toKatakana(s: String) = s.map { if (it in 'ぁ'..'ゖ') it + 0x60 else it }.joinToString("")
    fun toHiragana(s: String) = s.map { if (it in 'ァ'..'ヶ') it - 0x60 else it }.joinToString("")

    /** 平假名清單：(假名, 羅馬拼音) */
    fun hiragana(): List<Pair<String, String>> {
        val roma = ROMA.split(" ")
        val basic = HIRA.mapIndexed { i, c -> c.toString() to roma[i] }
        val yoon = YOON_BASE.flatMap { (k, r) ->
            listOf("ゃ" to "a", "ゅ" to "u", "ょ" to "o").map { (small, v) ->
                val ro = when {
                    r == "sh" || r == "ch" || r == "j" -> r + v
                    else -> r + "y" + v
                }
                k + small to ro
            }
        }
        return basic + yoon
    }

    /** 全部卡片：平假名 + 片假名 */
    fun all(): List<Pair<String, String>> {
        val h = hiragana()
        return h + h.map { (k, r) -> toKatakana(k) to r }
    }
}
