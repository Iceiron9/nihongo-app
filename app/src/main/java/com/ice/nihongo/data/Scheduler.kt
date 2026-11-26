package com.ice.nihongo.data

/**
 * 自動複習排程（SM-2，Anki 早期使用的演算法）。
 * grade：0 = 重來、1 = 困難、2 = 良好、3 = 簡單
 */
object Scheduler {
    const val DAY = 86_400_000L
    const val RELEARN = 10 * 60_000L // 答錯 10 分鐘後再出現

    fun review(c: Card, grade: Int, now: Long = System.currentTimeMillis()): Card {
        require(grade in 0..3)
        val introduced = if (c.introduced == 0L) now else c.introduced
        if (grade == 0) {
            return c.copy(
                introduced = introduced,
                reps = 0, interval = 0,
                ease = maxOf(1.3, c.ease - 0.2),
                due = now + RELEARN
            )
        }
        val ease = maxOf(1.3, c.ease + listOf(-0.15, 0.0, 0.15)[grade - 1])
        val interval = when (c.reps) {
            0 -> if (grade == 3) 4 else 1
            1 -> if (grade == 1) 3 else 6
            else -> {
                val factor = when (grade) { 1 -> 1.2; 2 -> ease; else -> ease * 1.3 }
                maxOf(c.interval + 1, (c.interval * factor).toInt())
            }
        }
        return c.copy(ease = ease, interval = interval, reps = c.reps + 1, due = now + interval * DAY,
            introduced = introduced)
    }

    /** 按鈕上顯示「多久後再複習」 */
    fun preview(c: Card, grade: Int): String {
        val next = review(c, grade, 0L)
        return if (grade == 0) "10分" else "${next.interval}天"
    }
}
