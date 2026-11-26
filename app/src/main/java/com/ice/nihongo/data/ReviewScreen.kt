package com.ice.nihongo.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** 像 Anki 一樣的複習畫面，五十音和單字共用 */
@Composable
fun ReviewScreen(deck: String) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).cards() }
    val scope = rememberCoroutineScope()
    var queue by remember(deck) { mutableStateOf<List<Card>?>(null) }
    var showBack by remember(deck) { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var total by remember(deck) { mutableStateOf(0) }
    var remaining by remember(deck) { mutableStateOf(0) }
    val settings = remember { StudySettings(ctx) }

    suspend fun reload() {
        if (deck == "kana") AppDb.seedKana(dao)
        val now = System.currentTimeMillis()
        remaining = dao.newRemaining(deck)
        // 單字：每天限額；五十音：一次最多 50 個新的
        val limit = if (deck == "vocab")
            Plan.newLeftToday(settings.perDay(remaining, now), dao.introducedSince(deck, Plan.startOfDay(now)))
        else 50
        val news = dao.newCards(deck, limit)
        queue = Plan.interleave(dao.dueReviews(deck, now), news)
        total = dao.count(deck)
        showBack = false
    }
    LaunchedEffect(deck) { reload() }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    (if (deck == "kana") "五十音" else "單字") + "　今天還有 ${queue?.size ?: 0} 張",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "新字 ${queue?.count { it.isNew } ?: 0}・複習 ${queue?.count { !it.isNew } ?: 0}・未學 $remaining／共 $total",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray
                )
            }
            if (deck == "vocab") OutlinedButton(onClick = { adding = !adding }) { Text(if (adding) "取消" else "＋新增") }
        }

        if (adding) {
            AddCard { front, back ->
                scope.launch {
                    if (front.isNotBlank() && !dao.exists(deck, front.trim())) {
                        dao.insert(Card(deck = deck, front = front.trim(), back = back.trim()))
                    }
                    adding = false
                    reload()
                }
            }
            return@Column
        }

        val q = queue
        val card = q?.firstOrNull()
        if (q == null) return@Column
        if (card == null) {
            Spacer(Modifier.height(80.dp))
            Text("今天的複習完成了！🎉", style = MaterialTheme.typography.headlineSmall)
            if (deck == "vocab" && remaining > 0) {
                Spacer(Modifier.height(12.dp))
                Text("明天會再出新字。想調整每天的數量，到「單字包」設定。", textAlign = TextAlign.Center)
            }
            if (deck == "vocab" && total == 0) {
                Spacer(Modifier.height(12.dp))
                Text("到「影音」分頁匯入歌曲或字幕，點單字就會加進來。", textAlign = TextAlign.Center)
            }
            return@Column
        }

        Spacer(Modifier.height(60.dp))
        Text(card.front, fontSize = if (card.front.length <= 2) 96.sp else 48.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        if (showBack) {
            Text(card.back, fontSize = 28.sp, textAlign = TextAlign.Center)
            card.source?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val labels = listOf("重來", "困難", "良好", "簡單")
                val colors = listOf(Color(0xFFD32F2F), Color(0xFFF57C00), Color(0xFF388E3C), Color(0xFF1976D2))
                labels.forEachIndexed { g, label ->
                    Button(
                        onClick = {
                            scope.launch {
                                val next = Scheduler.review(card, g)
                                dao.update(next)
                                // 答錯的卡片排到這次的最後面再出一次
                                queue = q.drop(1) + if (g == 0) listOf(next) else emptyList()
                                showBack = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors[g]),
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label)
                            Text(Scheduler.preview(card, g), fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
            Button(onClick = { showBack = true }, Modifier.fillMaxWidth().height(56.dp)) { Text("顯示答案") }
        }
    }
}

@Composable
private fun AddCard(onSave: (String, String) -> Unit) {
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        OutlinedTextField(front, { front = it }, label = { Text("單字（例如：桜）") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(back, { back = it }, label = { Text("讀音／意思（例如：さくら　櫻花）") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onSave(front, back) }, Modifier.fillMaxWidth()) { Text("儲存") }
    }
}
