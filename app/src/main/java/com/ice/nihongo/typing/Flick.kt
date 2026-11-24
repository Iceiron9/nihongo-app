package com.ice.nihongo.typing

import kotlin.math.abs

/**
 * 九宮格（12 鍵）日文輸入的規則。
 * 每個鍵的字串依序是：點一下、往左、往上、往右、往下。
 */
object Flick {
    const val BACKSPACE = "⌫"
    const val DAKUTEN = "゛゜"   // 濁音／半濁音／小字 切換鍵
    const val SPACE = "␣"

    val keys: List<List<String>> = listOf(
        listOf("あいうえお", "かきくけこ", "さしすせそ"),
        listOf("たちつてと", "なにぬねの", "はひふへほ"),
        listOf("まみむめも", "や「ゆ」よ", "らりるれろ"),
        listOf(DAKUTEN, "わをんー", BACKSPACE)
    )

    /** 滑動方向 → 第幾個字（0 = 點一下） */
    fun index(dx: Float, dy: Float, threshold: Float): Int = when {
        abs(dx) < threshold && abs(dy) < threshold -> 0
        abs(dx) > abs(dy) -> if (dx < 0) 1 else 3
        else -> if (dy < 0) 2 else 4
    }

    /** 這個鍵在這個方向會輸出什麼（空字串 = 沒有字） */
    fun output(key: String, dir: Int): String {
        if (key == BACKSPACE || key == DAKUTEN) return key
        return key.getOrNull(dir)?.toString() ?: ""
    }

    // 「゛゜」鍵循環：か→が→か、は→ば→ぱ→は、つ→っ→づ→つ、や→ゃ→や
    private val cycles = listOf(
        "かが", "きぎ", "くぐ", "けげ", "こご", "さざ", "しじ", "すず", "せぜ", "そぞ",
        "ただ", "ちぢ", "つっづ", "てで", "とど", "はばぱ", "ひびぴ", "ふぶぷ", "へべぺ", "ほぼぽ",
        "あぁ", "いぃ", "うぅゔ", "えぇ", "おぉ", "やゃ", "ゆゅ", "よょ", "わゎ"
    )

    fun cycle(c: Char): Char {
        for (s in cycles) {
            val i = s.indexOf(c)
            if (i >= 0) return s[(i + 1) % s.length]
        }
        return c
    }

    /** 按下一個鍵之後，輸入框文字的變化 */
    fun apply(current: String, out: String): String = when (out) {
        "" -> current
        BACKSPACE -> current.dropLast(1)
        DAKUTEN -> if (current.isEmpty()) current else current.dropLast(1) + cycle(current.last())
        else -> current + out
    }
}
