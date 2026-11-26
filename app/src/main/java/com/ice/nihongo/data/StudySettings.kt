package com.ice.nihongo.data

import android.content.Context

/** 每日新字設定，存在手機裡 */
class StudySettings(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("study", Context.MODE_PRIVATE)

    /** 固定模式：每天幾個新字 */
    var fixedPerDay: Int
        get() = prefs.getInt("fixedPerDay", 20)
        set(v) = prefs.edit().putInt("fixedPerDay", v.coerceIn(Plan.MIN_PER_DAY, Plan.MAX_PER_DAY)).apply()

    /** 考試日期（0 = 沒設定，用固定模式） */
    var examDate: Long
        get() = prefs.getLong("examDate", 0L)
        set(v) = prefs.edit().putLong("examDate", v).apply()

    val useExam get() = examDate > System.currentTimeMillis()

    /** 今天實際的每日新字數 */
    fun perDay(remaining: Int, now: Long = System.currentTimeMillis()): Int =
        if (examDate > now) Plan.autoPerDay(remaining, now, examDate) else fixedPerDay
}
