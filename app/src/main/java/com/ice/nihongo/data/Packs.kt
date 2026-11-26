package com.ice.nihongo.data

import com.ice.nihongo.kana.KanaData

/** 單字包裡的一個字 */
data class PackWord(val front: String, val reading: String, val meaning: String)

/** 一個單字包：assets/packs 資料夾裡的 .tsv 檔，第一行「# 標題」，之後每行「表記<TAB>讀音<TAB>中文」 */
data class Pack(val title: String, val words: List<PackWord>)

object Packs {
    fun parse(text: String): Pack {
        var title = ""
        val words = mutableListOf<PackWord>()
        for (raw in text.removePrefix("﻿").lines()) {
            val line = raw.trimEnd('\r')
            if (line.isBlank()) continue
            if (line.startsWith("#")) {
                if (title.isEmpty()) title = line.removePrefix("#").trim()
                continue
            }
            val p = line.split('\t')
            if (p.size < 3 || p[0].isBlank()) continue
            words += PackWord(p[0].trim(), p[1].trim(), p[2].trim())
        }
        return Pack(title.ifEmpty { "單字包" }, words)
    }

    /** 卡片背面：讀音換行意思；純假名的字讀音和正面一樣，就只放意思 */
    fun back(w: PackWord): String =
        if (w.reading.isEmpty() || w.reading == w.front) w.meaning else "${w.reading}\n${w.meaning}"

    /** 把單字包轉成卡片，跳過已經有的字。新卡不排日期，每天依「每日新字數」依序出現。 */
    fun toCards(pack: Pack, existing: Set<String>): List<Card> =
        pack.words.filter { it.front !in existing }.distinctBy { it.front }
            .map { w -> Card(deck = "vocab", front = w.front, back = back(w), source = pack.title) }

    /** 卡片的平假名讀音（打字練習用），找不到回傳 null */
    fun readingOf(front: String, back: String): String? {
        fun kana(s: String): String? {
            val h = KanaData.toHiragana(s.trim())
            return h.takeIf { it.length >= 2 && it.all { c -> c in 'ぁ'..'ゖ' || c == 'ー' } }
        }
        val first = back.lineSequence().firstOrNull()?.split('／', '・', ' ', '　')?.firstOrNull().orEmpty()
        return kana(first) ?: kana(front)
    }
}
