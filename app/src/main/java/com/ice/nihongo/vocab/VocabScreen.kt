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
        c.due <= now -> if (c.reps == 0) "新卡・今天" else "今天要複習"
        c.reps == 0 -> "新卡・$days 天後開始"
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
    val scope = rememberCoroutineScope()
    var packs by remember { mutableStateOf<List<PackEntry>>(emptyList()) }
    val added = remember { mutableStateMapOf<String, Int>() }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        packs = withContext(Dispatchers.IO) {
            (ctx.assets.list("packs") ?: emptyArray()).filter { it.endsWith(".tsv") }
                .sortedWith(compareBy({ packOrder(it) }, { it })).map { f ->
                PackEntry(f, Packs.parse(ctx.assets.open("packs/$f").use { it.readBytes() }.toString(Charsets.UTF_8)))
            }
        }
        val have = dao.fronts("vocab").toSet()
        packs.forEach { e -> added[e.file] = e.pack.words.count { it.front in have } }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "加入後每天最多出現 ${Packs.NEW_PER_DAY} 個新單字，跟著「複習」分頁每天背就好。",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray
        )
        if (message.isNotEmpty()) {
            Text(message, color = Color(0xFF1976D2), modifier = Modifier.padding(vertical = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    val now = System.currentTimeMillis()
                                    val start = Packs.startAt(dao.lastNewDue("vocab"), now)
                                    val cards = Packs.toCards(e.pack, existing, start)
                                    dao.insertAll(cards)
                                    added[e.file] = total
                                    val skipped = total - cards.size
                                    message = "已加入 ${cards.size} 個單字" +
                                        (if (skipped > 0) "（$skipped 個本來就有，跳過）" else "") +
                                        "，大約 ${Packs.days(cards.size)} 天背完新字"
                                    busy = false
                                }
                            }) { Text(if (have > 0) "加入剩下的 ${total - have} 個" else "加入") }
                        }
                    }
                }
            }
        }
    }
}
