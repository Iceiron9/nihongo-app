package com.ice.nihongo.vocab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card as UiCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ice.nihongo.data.AppDb
import com.ice.nihongo.data.Card
import com.ice.nihongo.data.Pack
import com.ice.nihongo.data.Packs
import com.ice.nihongo.data.Plan
import com.ice.nihongo.data.StudySettings
import com.ice.nihongo.data.ReviewScreen
import com.ice.nihongo.data.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 單字分頁：複習／清單／單字包 */
@Composable
fun VocabScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf("複習", "清單", "單字包").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        when (tab) {
            0 -> ReviewScreen("vocab")
            1 -> WordList()
            else -> PackList { tab = 1 }
        }
    }
}

/** 「3 天後」這種文字 */
fun dueText(c: Card, now: Long = System.currentTimeMillis()): String {
    val days = ((c.due - now) / Scheduler.DAY).toInt()
    return when {
        c.isNew -> "新卡・還沒學"
        c.due <= now -> "今天要複習"
        days == 0 -> "今天稍後"
        else -> "$days 天後複習"
    }
}

@Composable
private fun WordList() {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).cards() }
    val scope = rememberCoroutineScope()
    var q by rememberSaveable { mutableStateOf("") }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var deleting by remember { mutableStateOf<Card?>(null) }

    LaunchedEffect(q) { cards = dao.search("vocab", q.trim()) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            q, { q = it },
            label = { Text("搜尋（日文、讀音或中文）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Text("共 ${cards.size} 個", style = MaterialTheme.typography.bodySmall, color = Color.Gray,
            modifier = Modifier.padding(vertical = 6.dp))
        if (cards.isEmpty()) {
            Text("還沒有單字。到「單字包」加入 JLPT 單字，或在「影音」分頁點歌詞裡的字。",
                modifier = Modifier.padding(top = 24.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(cards, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.front, fontSize = 22.sp)
                        Text(c.back.replace('\n', '　'), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            listOfNotNull(dueText(c), c.source).joinToString("・"),
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray
                        )
                    }
                    TextButton(onClick = { deleting = c }) { Text("刪除", color = Color(0xFFD32F2F)) }
                }
                HorizontalDivider()
            }
        }
    }

    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("刪除「${c.front}」？") },
            text = { Text("這張卡片和它的複習紀錄會一起刪掉。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        dao.delete(c)
                        cards = cards - c
                        deleting = null
                    }
                }) { Text("刪除", color = Color(0xFFD32F2F)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }
}

private data class PackEntry(val file: String, val pack: Pack)

/** 單字包排序：N5 → N1，其他（歌曲）排在後面 */
fun packOrder(file: String): Int =
    listOf("jlpt_n5", "jlpt_n4", "jlpt_n3", "jlpt_n2", "jlpt_n1").indexOf(file.removeSuffix(".tsv")).let { if (it < 0) 99 else it }

@Composable
private fun PackList(onAdded: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).cards() }
    val settings = remember { StudySettings(ctx) }
    val scope = rememberCoroutineScope()
    var packs by remember { mutableStateOf<List<PackEntry>>(emptyList()) }
    val added = remember { mutableStateMapOf<String, Int>() }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var remaining by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        packs = withContext(Dispatchers.IO) {
            (ctx.assets.list("packs") ?: emptyArray()).filter { it.endsWith(".tsv") }
                .sortedWith(compareBy({ packOrder(it) }, { it })).map { f ->
                PackEntry(f, Packs.parse(ctx.assets.open("packs/$f").use { it.readBytes() }.toString(Charsets.UTF_8)))
            }
        }
        val have = dao.fronts("vocab").toSet()
        packs.forEach { e -> added[e.file] = e.pack.words.count { it.front in have } }
        remaining = dao.newRemaining("vocab")
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(6.dp)); PlanCard(settings, remaining) }
        if (message.isNotEmpty()) item { Text(message, color = Color(0xFF1976D2)) }
        items(packs, key = { it.file }) { e ->
            val total = e.pack.words.size
            val have = added[e.file] ?: 0
            UiCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(e.pack.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$total 個單字・例如：" + e.pack.words.take(4).joinToString("、") { it.front },
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))
                    if (have >= total) {
                        OutlinedButton(onClick = onAdded) { Text("已全部加入・看清單") }
                    } else {
                        Button(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                val existing = dao.fronts("vocab").toSet()
                                val cards = Packs.toCards(e.pack, existing)
                                dao.insertAll(cards)
                                added[e.file] = total
                                remaining = dao.newRemaining("vocab")
                                val skipped = total - cards.size
                                message = "已加入 ${cards.size} 個單字" +
                                    (if (skipped > 0) "（$skipped 個本來就有，跳過）" else "") +
                                    "，會照上面設定的每日新字數依序出現"
                                busy = false
                            }
                        }) { Text(if (have > 0) "加入剩下的 ${total - have} 個" else "加入") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private val dateFmt = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.TAIWAN)

/** 每日新字設定：固定數量，或設定考試日期自動計算 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanCard(settings: StudySettings, remaining: Int) {
    var fixed by remember { mutableIntStateOf(settings.fixedPerDay) }
    var exam by remember { mutableLongStateOf(settings.examDate) }
    var picking by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val useExam = exam > now
    val perDay = if (useExam) Plan.autoPerDay(remaining, now, exam) else fixed
    val days = Plan.daysToFinish(remaining, perDay)

    UiCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("每日進度", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("考試日期：", Modifier.weight(1f))
                TextButton(onClick = { picking = true }) { Text(if (useExam) dateFmt.format(java.util.Date(exam)) else "未設定") }
                if (useExam) TextButton(onClick = { exam = 0L; settings.examDate = 0L }) { Text("清除") }
            }
            if (useExam) {
                val left = Plan.daysUntil(now, exam)
                Text(
                    "距離考試 $left 天。新字在考前 ${if (left > Plan.BUFFER_DAYS + 14) Plan.BUFFER_DAYS else 0} 天學完，" +
                        "所以每天要學 $perDay 個",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (perDay >= 60) Text(
                    "⚠ 每天 $perDay 個新字負擔很重，複習量會到每天 ${Plan.reviewsPerDay(perDay)} 張左右",
                    color = Color(0xFFD32F2F), style = MaterialTheme.typography.bodySmall
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("每天新字：", Modifier.weight(1f))
                    OutlinedButton(onClick = { fixed = (fixed - 5).coerceAtLeast(Plan.MIN_PER_DAY); settings.fixedPerDay = fixed }) { Text("−") }
                    Text("$fixed 個", Modifier.padding(horizontal = 12.dp), fontSize = 18.sp)
                    OutlinedButton(onClick = { fixed = (fixed + 5).coerceAtMost(Plan.MAX_PER_DAY); settings.fixedPerDay = fixed }) { Text("＋") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (remaining == 0) "目前沒有未學的單字，先在下面加入單字包。"
                else "未學 $remaining 個・照這個速度 ${dateFmt.format(java.util.Date(now + days * Scheduler.DAY))} 學完" +
                    "・每天約 ${Plan.minutesPerDay(perDay)} 分鐘（含複習）",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray
            )
        }
    }

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = if (exam > now) exam else now + 180 * Scheduler.DAY)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { exam = it; settings.examDate = it }
                    picking = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("取消") } }
        ) { DatePicker(state) }
    }
}
