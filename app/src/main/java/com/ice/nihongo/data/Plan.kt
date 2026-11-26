package com.ice.nihongo.data

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.max

/**
 * 每天學幾個新字的計算。
 * 兩種模式：固定每天 N 個，或設定考試日期，自動算出每天要學幾個，
 * 而且在考試前 [BUFFER_DAYS] 天學完新字，最後一個月專心複習。
 */
object Plan {
    const val BUFFER_DAYS = 30
    const val MIN_PER_DAY = 5
    const val MAX_PER_DAY = 150

    /** 從 now 到 target 還有幾天（不足一天算一天，已過期回傳 0） */
    fun daysUntil(now: Long, target: Long): Int =
        if (target <= now) 0 else ceil((target - now).toDouble() / Scheduler.DAY).toInt()

    /** 可以用來學新字的天數：留最後一個月複習；時間太短就不留 */
    fun learnDays(now: Long, target: Long, buffer: Int = BUFFER_DAYS): Int {
        val days = daysUntil(now, target)
        return if (days > buffer + 14) days - buffer else max(1, days)
    }

    /** 依考試日期，每天需要學幾個新字 */
    fun autoPerDay(remaining: Int, now: Long, target: Long, buffer: Int = BUFFER_DAYS): Int {
        if (remaining <= 0) return 0
        val need = ceil(remaining.toDouble() / learnDays(now, target, buffer)).toInt()
        return need.coerceIn(1, MAX_PER_DAY)
    }

    /** 照每天 perDay 個，還要幾天學完 */
    fun daysToFinish(remaining: Int, perDay: Int): Int =
        if (remaining <= 0 || perDay <= 0) 0 else ceil(remaining.toDouble() / perDay).toInt()

    /**
     * 每天大概要花幾分鐘（穩定之後）。
     * 經驗值：每天的複習量約為新字的 8 倍；新字每張約 20 秒、複習每張約 8 秒。
     */
    fun minutesPerDay(perDay: Int): Int = ceil(perDay * (20 + 8 * 8) / 60.0).toInt()

    /** 每天大約要複習幾張（穩定之後） */
    fun reviewsPerDay(perDay: Int): Int = perDay * 8

    /** 今天 0 點（手機時區） */
    fun startOfDay(now: Long, tz: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(tz).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** 今天還能學幾個新字 */
    fun newLeftToday(perDay: Int, learnedToday: Int): Int = max(0, perDay - learnedToday)

    /** 複習和新字交錯排：每 4 張複習夾 1 個新字，新字不會全擠在最後 */
    fun <T> interleave(reviews: List<T>, news: List<T>, every: Int = 4): List<T> {
        val out = ArrayList<T>(reviews.size + news.size)
        var n = 0
        reviews.forEachIndexed { i, r ->
            out += r
            if ((i + 1) % every == 0 && n < news.size) out += news[n++]
        }
        while (n < news.size) out += news[n++]
        return out
    }
}
