package com.ice.nihongo.media

/**
 * 備案：匯入字幕或歌詞。支援 SRT、VTT、LRC（有時間軸的歌詞），
 * 以及沒有時間軸的純文字歌詞（一行一句）。
 */
object SubtitleImport {
    private val TIME = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")
    private val LRC = Regex("""^\s*((?:\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?])+)(.*)$""")
    private val LRC_TAG = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val TAGS = Regex("""<[^>]+>|\{[^}]+}""") // 去掉 <i> 或 {\an8} 之類的格式碼

    fun parse(raw: String): List<Segment> {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        return when {
            "-->" in text -> parseSrt(text)
            text.lines().any { LRC.matches(it) } -> parseLrc(text)
            else -> text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                .mapIndexed { i, l -> Segment(i * 1000L, i * 1000L, l) }
        }
    }

    private fun ms(s: String): Long {
        val m = TIME.find(s) ?: return 0
        val (h, mi, sec, frac) = m.destructured
        return (h.ifEmpty { "0" }.toLong() * 3_600_000) + mi.toLong() * 60_000 + sec.toLong() * 1000 +
            frac.padEnd(3, '0').take(3).toLong()
    }

    private fun parseSrt(text: String): List<Segment> =
        text.trim().split(Regex("""\n\s*\n""")).mapNotNull { block ->
            val lines = block.lines()
            val ti = lines.indexOfFirst { "-->" in it }
            if (ti < 0) return@mapNotNull null
            val (a, b) = lines[ti].split("-->", limit = 2)
            val body = lines.drop(ti + 1).joinToString(" ") { it.trim() }.replace(TAGS, "").trim()
            if (body.isEmpty()) null else Segment(ms(a), ms(b), body)
        }

    private fun parseLrc(text: String): List<Segment> {
        val items = mutableListOf<Pair<Long, String>>()
        for (line in text.lines()) {
            val m = LRC.matchEntire(line) ?: continue
            val body = m.groupValues[2].replace(TAGS, "").trim()
            if (body.isEmpty()) continue
            for (t in LRC_TAG.findAll(m.groupValues[1])) {
                val (mi, sec, frac) = t.destructured
                val start = mi.toLong() * 60_000 + sec.toLong() * 1000 + frac.padEnd(3, '0').take(3).ifEmpty { "0" }.toLong()
                items += start to body
            }
        }
        items.sortBy { it.first }
        return items.mapIndexed { i, (start, body) ->
            val end = items.getOrNull(i + 1)?.first ?: (start + 5000)
            Segment(start, end, body)
        }
    }
}
