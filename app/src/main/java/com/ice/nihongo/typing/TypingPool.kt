package com.ice.nihongo.typing

/** 打字練習的題目來源：匯入的字幕句子 + 單字卡的讀音 + 內建例句 */
object TypingPool {
    private val builtIn = listOf(
        "ありがとうございます", "にほんごをべんきょうします", "きょうはいいてんきですね",
        "おはようございます", "いただきます", "よろしくおねがいします", "がんばってください"
    )
    private val imported = LinkedHashSet<String>()
    private var lastPick = ""

    @Synchronized
    fun add(kana: String) {
        val k = kana.filter { it in 'ぁ'..'ゖ' || it == 'ー' }
        if (k.length in 3..30) imported += k
    }

    @Synchronized
    fun size() = imported.size

    /** 有匯入的句子就優先出這些題目 */
    @Synchronized
    fun next(extra: List<String> = emptyList()): String {
        val pool = (if (imported.isNotEmpty()) imported.toList() else builtIn) + extra.filter { it.length >= 2 }
        val choices = pool.filter { it != lastPick }.ifEmpty { pool }
        return choices.random().also { lastPick = it }
    }
}
