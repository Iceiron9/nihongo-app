package com.ice.nihongo

import org.junit.Assert.assertEquals
import org.junit.Test
import com.ice.nihongo.data.*
import com.ice.nihongo.kana.KanaData
import com.ice.nihongo.media.*
import com.ice.nihongo.typing.*
import com.ice.nihongo.data.Pack
import com.ice.nihongo.data.PackWord
import com.ice.nihongo.data.Packs

private var fails = 0
private fun expect(name: String, ok: Boolean, info: Any? = "") { if (ok) println("✓ $name") else { fails++; println("✗ $name  $info") } }

// 假的分詞器：用空白切詞，結尾有 ? 的當作辭典裡沒有的詞
private val fake = Analyzer { text ->
    text.split(" ").filter { it.isNotEmpty() }.map { w ->
        val unknown = w.endsWith("?")
        val s = w.removeSuffix("?")
        val (surface, reading) = if ("/" in s) s.split("/", limit = 2).let { it[0] to it[1] } else s to ""
        Tok(surface, surface, reading, if (surface.length == 1 && surface[0] in 'ぁ'..'ゖ') "助詞" else "名詞", !unknown)
    }
}

private fun runAll(): Int {
    fails = 0
    // ---------- 複習排程 ----------
    val c0 = Card(deck = "kana", front = "あ", back = "a")
    val now = 1_000_000L
    val good1 = Scheduler.review(c0, 2, now)
    expect("新卡答良好 → 1 天後", good1.interval == 1 && good1.due == now + Scheduler.DAY, good1)
    val good2 = Scheduler.review(good1, 2, now)
    expect("第二次良好 → 6 天後", good2.interval == 6, good2)
    val good3 = Scheduler.review(good2, 2, now)
    expect("第三次良好 → 約 15 天", good3.interval == 15, good3)
    val again = Scheduler.review(good3, 0, now)
    expect("答錯 → 10 分鐘後、重新開始", again.reps == 0 && again.due == now + Scheduler.RELEARN && again.ease < good3.ease, again)
    val easy = Scheduler.review(c0, 3, now)
    expect("新卡答簡單 → 4 天", easy.interval == 4, easy)
    var c = c0; repeat(30) { c = Scheduler.review(c, 1, now) }
    expect("一直按困難，ease 不低於 1.3 且間隔仍會成長", c.ease >= 1.3 && c.interval > 30, c)
    expect("按鈕預覽文字", Scheduler.preview(c0, 0) == "10分" && Scheduler.preview(c0, 2) == "1天")

    // ---------- 五十音 ----------
    val h = KanaData.hiragana()
    expect("平假名 71 + 拗音 33 = 104", h.size == 104, h.size)
    expect("あ=a、し=shi、ん=n", h.first() == ("あ" to "a") && ("し" to "shi") in h && ("ん" to "n") in h)
    expect("拗音 きゃ=kya、しゃ=sha、ちょ=cho、じゅ=ju", listOf("きゃ" to "kya", "しゃ" to "sha", "ちょ" to "cho", "じゅ" to "ju").all { it in h })
    val all = KanaData.all()
    expect("片假名 ア=a、キャ=kya", ("ア" to "a") in all && ("キャ" to "kya") in all && all.size == 208)
    expect("片假名轉平假名", KanaData.toHiragana("サクラ") == "さくら")
    expect("卡片正面不重複", all.map { it.first }.toSet().size == all.size)

    // ---------- 字幕匯入 ----------
    val srt = "﻿1\r\n00:00:01,500 --> 00:00:03,000\r\n<i>桜が咲いた</i>\r\n\r\n2\r\n00:00:04,000 --> 00:00:06,250\r\n春の風\r\n二行目\r\n"
    val s1 = SubtitleImport.parse(srt)
    expect("SRT 兩句、時間正確、去掉格式碼", s1.size == 2 && s1[0] == Segment(1500, 3000, "桜が咲いた") && s1[1].text == "春の風 二行目" && s1[1].endMs == 6250L, s1)
    val vtt = "WEBVTT\n\n00:01.000 --> 00:02.500\nこんにちは\n"
    val s2 = SubtitleImport.parse(vtt)
    expect("VTT（沒有小時）", s2.size == 1 && s2[0].startMs == 1000L && s2[0].endMs == 2500L, s2)
    val lrc = "[ti:テスト]\n[00:12.30]夜空に\n[00:15.00][01:20.50]星が光る\n[00:18.00]\n"
    val s3 = SubtitleImport.parse(lrc)
    expect("LRC 歌詞：重複時間標籤展開、依時間排序", s3.map { it.startMs to it.text } == listOf(12300L to "夜空に", 15000L to "星が光る", 80500L to "星が光る"), s3)
    expect("LRC 結束時間 = 下一句開始", s3[0].endMs == 15000L, s3[0])
    val plain = SubtitleImport.parse("一行目\n\n  二行目  \n")
    expect("純文字歌詞一行一句", plain.map { it.text } == listOf("一行目", "二行目"), plain)

    // ---------- 自動校正 ----------
    val cor = Corrector(fake)
    val cleaned = cor.clean(listOf(
        Segment(0, 1000, "♪ 桜 が 咲く ♪", 0.9f),
        Segment(1000, 2000, "ご視聴ありがとうございました", 0.3f),
        Segment(2000, 3000, "ラララ", 0.9f), Segment(3000, 4000, "ラララ", 0.9f), Segment(4000, 5000, "ラララ", 0.9f),
        Segment(5000, 6000, "(音楽)", 0.9f)
    ))
    expect("清掉音符、幻聽句、第 3 次重複、純音樂標記", cleaned.map { it.text } == listOf("桜 が 咲く", "ラララ", "ラララ"), cleaned)
    expect("縮短重複迴圈", cor.collapseRepeats("あいあいあいあいあい") == "あいあい" && cor.collapseRepeats("さくら") == "さくら", cor.collapseRepeats("あいあいあいあいあい"))
    expect("未知詞多的句子分數較高（較不通順）", cor.score("桜 が 咲く") < cor.score("さ?く? ら? が 咲く"))
    expect("低信心句子需要重跑", cor.needsRetry(Segment(0, 1, "桜 が 咲く", 0.4f)) && !cor.needsRetry(Segment(0, 1, "桜 が 咲く", 0.9f)))
    val ctxWords = cor.contextWords(listOf("君 の 声", "君 と 夢", "空"))
    expect("上下文詞彙：出現 2 次以上", ctxWords == setOf("君"), ctxWords)
    val a = Segment(0, 1, "黄味? が 好き", 0.5f)
    val b = Segment(0, 1, "君 が 好き", 0.45f)
    expect("兩個版本挑通順、符合上下文的", cor.choose(a, b, setOf("君")) == b)
    expect("分數相同時挑信心高的", cor.choose(Segment(0, 1, "空", 0.6f), Segment(0, 1, "空", 0.8f), emptySet()).confidence == 0.8f)
    val ws = cor.words("桜/サクラ が 咲く/サク する 不明?")
    expect("挑單字：去助詞、去基本詞、去未知詞，讀音轉平假名", ws == listOf("桜" to "さくら", "咲く" to "さく"), ws)
    expect("整句讀音", cor.reading("桜/サクラ が") == "さくらが", cor.reading("桜/サクラ が"))

    // ---------- 辨識流程（用假的 Whisper） ----------
    val calls = mutableListOf<Triple<Int, Int, String>>()
    val rec = object : Recognizer {
        override fun recognize(audio: FloatArray, beam: Int, prompt: String): List<Segment> {
            calls += Triple(audio.size, beam, prompt)
            return if (beam == 1) listOf(
                Segment(0, 2000, "君 の 声", 0.9f),
                Segment(2000, 4000, "黄味? が 好き", 0.3f),
                Segment(4000, 6000, "君 と 夢", 0.9f)
            ) else listOf(Segment(0, 2600, "君 が 好き", 0.6f))
        }
    }
    val audio = FloatArray(16000 * 6)
    val out = Transcriber(rec, cor).run(audio, "テスト曲")
    expect("低信心那句用上下文重新辨識後被修正", out.map { it.text } == listOf("君 の 声", "君 が 好き", "君 と 夢"), out)
    expect("只重跑一句、用 beam 5、提示含前文和歌名", calls.size == 2 && calls[1].second == 5 && calls[1].third.contains("君 の 声") && calls[1].third.contains("テスト曲"), calls)
    expect("重跑時只切那一段音訊（±0.3 秒）", calls[1].first == (2.6 * 16000).toInt(), calls[1].first)
    expect("修正後時間軸不變", out[1].startMs == 2000L && out[1].endMs == 4000L)
    expect("音訊切片不越界", Transcriber.slice(audio, -500, 999_999).size == audio.size && Transcriber.slice(audio, 7000, 8000).isEmpty())

    // ---------- 降取樣 ----------
    val rs = FloatBuffer16k().apply { rate = 44100 }
    repeat(44100) { rs.add(kotlin.math.sin(it * 2 * Math.PI * 440 / 44100).toFloat()) }
    val r16 = rs.toArray()
    expect("44.1kHz 1 秒 → 16000 個樣本（±2）", kotlin.math.abs(r16.size - 16000) <= 2, r16.size)
    expect("降取樣後振幅正常", r16.maxOrNull()!! in 0.95f..1.0f, r16.maxOrNull())
    val rs48 = FloatBuffer16k().apply { rate = 48000 }; repeat(48000 * 3) { rs48.add(0.1f) }
    expect("48kHz 3 秒 → 48000 個樣本", kotlin.math.abs(rs48.toArray().size - 48000) <= 2, rs48.toArray().size)

    // ---------- 九宮格 ----------
    expect("點一下 = あ段", Flick.output("かきくけこ", Flick.index(2f, -3f, 30f)) == "か")
    expect("左滑 = い段", Flick.output("かきくけこ", Flick.index(-50f, 5f, 30f)) == "き")
    expect("上滑 = う段", Flick.output("かきくけこ", Flick.index(5f, -50f, 30f)) == "く")
    expect("右滑 = え段", Flick.output("かきくけこ", Flick.index(50f, 5f, 30f)) == "け")
    expect("下滑 = お段", Flick.output("かきくけこ", Flick.index(5f, 50f, 30f)) == "こ")
    expect("や 鍵：左滑「、右滑」", Flick.output("や「ゆ」よ", 1) == "「" && Flick.output("や「ゆ」よ", 3) == "」")
    expect("わ 鍵沒有下滑的字", Flick.output("わをんー", 4) == "")
    var t = ""
    for (o in listOf("は", Flick.DAKUTEN)) t = Flick.apply(t, o)
    expect("は + ゛ → ば", t == "ば", t)
    t = Flick.apply(t, Flick.DAKUTEN); expect("再按 → ぱ", t == "ぱ", t)
    t = Flick.apply(t, Flick.DAKUTEN); expect("再按 → 回到は", t == "は", t)
    expect("つ → っ → づ", Flick.cycle('つ') == 'っ' && Flick.cycle('っ') == 'づ')
    expect("や → ゃ", Flick.cycle('や') == 'ゃ')
    expect("刪除鍵", Flick.apply("さくら", Flick.BACKSPACE) == "さく" && Flick.apply("", Flick.BACKSPACE) == "")
    expect("空白輸入時按゛不會當", Flick.apply("", Flick.DAKUTEN) == "")
    // 每個平假名都打得出來
    val reachable = mutableSetOf<Char>()
    for (row in Flick.keys) for (k in row) for (d in 0..4) {
        val o = Flick.output(k, d); if (o.length == 1) { var ch = o[0]; repeat(3) { reachable += ch; ch = Flick.cycle(ch) } }
    }
    val missing = KanaData.hiragana().flatMap { it.first.toList() }.filter { it !in reachable }.toSet()
    expect("所有五十音都打得出來", missing.isEmpty(), missing)

    // ---------- 打字題庫 ----------
    expect("沒匯入時出內建例句", TypingPool.next().isNotEmpty())
    TypingPool.add("さくらがさいた"); TypingPool.add("ab"); TypingPool.add("よぞらにほしが★ひかる")
    expect("匯入句子只留假名、太短的不收", TypingPool.size() == 2, TypingPool.size())
    val picks = (1..20).map { TypingPool.next() }.toSet()
    expect("有匯入後優先出匯入的題目", picks.all { it == "さくらがさいた" || it == "よぞらにほしがひかる" }, picks)
    expect("不會連續出同一題", (1..20).map { TypingPool.next() }.zipWithNext().none { it.first == it.second })

    // ---------- 匯入：判斷檔案類型、檔名 ----------
    val mp4Head = byteArrayOf(0, 0, 0, 0x20) + "ftypisomiso2avc1mp41".toByteArray() + ByteArray(200) { (it * 7).toByte() }
    expect("影片（就算叫 .txt）不會被當成字幕", TextSniffer.decode(mp4Head) == null)
    expect("SRT 字幕認得是文字", TextSniffer.decode("1\n00:00:01,000 --> 00:00:02,000\n桜\n".toByteArray()) != null)
    expect("UTF-8 BOM 的歌詞認得是文字", TextSniffer.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "夜空に\n星が光る".toByteArray()) != null)
    expect("空檔案不是字幕", TextSniffer.decode(ByteArray(0)) == null)
    expect("歌名.mp4.txt → 歌名", TextSniffer.title("wowaka - Unknown Mother-Goose ft. Hatsune Miku 360p_h264.mp4.txt") == "wowaka - Unknown Mother-Goose ft. Hatsune Miku 360p_h264", TextSniffer.title("wowaka - Unknown Mother-Goose ft. Hatsune Miku 360p_h264.mp4.txt"))
    expect("一般檔名只去一層", TextSniffer.title("夜に駆ける.mp3") == "夜に駆ける" && TextSniffer.title("lyrics.txt") == "lyrics" && TextSniffer.title("noext") == "noext")

    // ---------- 單字包 ----------
    val pk = Packs.parse("\uFEFF# 測試包\n桜\tさくら\t櫻花\nああ\tああ\t啊\n壞行\n\n桜\tさくら\t重複\n")
    expect("單字包解析：標題、略過壞行", pk.title == "測試包" && pk.words.size == 3, pk)
    expect("背面：讀音換行中文；純假名只放中文", Packs.back(pk.words[0]) == "さくら\n櫻花" && Packs.back(pk.words[1]) == "啊")
    val day = Scheduler.DAY
    val big = Pack("大包", (1..45).map { PackWord("字$it", "よみ", "意思") })
    val cs = Packs.toCards(big, setOf("字1", "字2"), 1000L)
    expect("跳過已經有的字", cs.size == 43 && cs.none { it.front == "字1" })
    expect("每天 20 個新字分散排程", cs[0].due == 1000L && cs[19].due == 1000L && cs[20].due == 1000L + day && cs[42].due == 1000L + 2 * day, cs.map { it.due }.distinct())
    expect("卡片記錄出處、都是單字卡", cs.all { it.source == "大包" && it.deck == "vocab" && it.reps == 0 })
    expect("同一包重複的字只加一次", Packs.toCards(pk, emptySet(), 0L).size == 2)
    expect("第二包接在第一包後面", Packs.startAt(5 * day, day) == 6 * day && Packs.startAt(null, day) == day && Packs.startAt(0L, day) == day)
    expect("背完天數", Packs.days(43) == 3 && Packs.days(40) == 2 && Packs.days(0) == 0)
    expect("打字讀音：從背面第一行取", Packs.readingOf("桜", "さくら\n櫻花") == "さくら")
    expect("打字讀音：多種讀法取第一個", Packs.readingOf("私", "わたし／わたくし\n我") == "わたし")
    expect("打字讀音：純假名的字用正面、片假名轉平假名", Packs.readingOf("ああ", "啊") == "ああ" && Packs.readingOf("テレビ", "電視") == "てれび")
    expect("打字讀音：歌曲挑的字（背面只有讀音）", Packs.readingOf("咲く", "さく") == "さく")
    expect("打字讀音：找不到就不用", Packs.readingOf("～円", "～えん\n圓") == null)
    expect("清單日期文字", com.ice.nihongo.vocab.dueText(Card(deck = "vocab", front = "a", back = "b", due = 0L), 100L) == "新卡・今天" &&
        com.ice.nihongo.vocab.dueText(Card(deck = "vocab", front = "a", back = "b", due = 100L + 3 * day + 5), 100L) == "新卡・3 天後開始" &&
        com.ice.nihongo.vocab.dueText(Card(deck = "vocab", front = "a", back = "b", reps = 2, due = 100L + 6 * day + 5), 100L) == "6 天後複習")

    // 內建的單字包檔案都要能正確讀取
    val dir = listOf("src/main/assets/packs", "app/src/main/assets/packs").map { java.io.File(it) }.firstOrNull { it.isDirectory }
    expect("找得到單字包資料夾", dir != null)
    val files = dir?.listFiles { f -> f.name.endsWith(".tsv") }.orEmpty().sortedBy { it.name }
    expect("內建 3 個單字包", files.size == 3, files.map { it.name })
    for (f in files) {
        val text = f.readText()
        val p = Packs.parse(text)
        val dataLines = text.lines().count { it.isNotBlank() && !it.startsWith("#") }
        expect("${f.name}：每一行都有讀音和中文（${p.words.size} 字）", p.words.size == dataLines && p.words.all { it.reading.isNotBlank() && it.meaning.isNotBlank() }, dataLines)
        expect("${f.name}：沒有重複的字", p.words.map { it.front }.toSet().size == p.words.size)
    }
    expect("N5 約 700 字、N4 約 660 字", files.map { Packs.parse(it.readText()).words.size }.let { it.contains(710) && it.contains(666) && it.contains(74) }, files.map { Packs.parse(it.readText()).words.size })

    println(if (fails == 0) "\n全部通過" else "\n失敗 $fails 項")
    return fails
}

class LogicTest {
    @Test fun allLogic() = assertEquals("有測試失敗，請看上方輸出", 0L, runAll().toLong())
}
