package com.ice.nihongo.media

import com.atilika.kuromoji.ipadic.Tokenizer

/** 用 Kuromoji（內建 IPADIC 辭典）做日文分詞與讀音，完全離線。 */
class KuromojiAnalyzer : Analyzer {
    private val tokenizer by lazy { Tokenizer() }

    override fun tokens(text: String): List<Tok> =
        tokenizer.tokenize(text).map { t ->
            val reading = t.reading?.takeIf { it != "*" } ?: ""
            val base = t.baseForm?.takeIf { it != "*" } ?: t.surface
            Tok(t.surface, base, reading, t.partOfSpeechLevel1 ?: "", t.isKnown)
        }

    companion object {
        /** 整個 App 共用一個，辭典載入一次約需 1~2 秒 */
        val shared by lazy { KuromojiAnalyzer() }
    }
}
