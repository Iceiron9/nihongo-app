@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ice.nihongo.media

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ice.nihongo.data.AppDb
import com.ice.nihongo.data.Card
import com.ice.nihongo.typing.TypingPool
import com.ice.nihongo.whisper.WhisperEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 辨識結果在畫面之間保留（切換分頁不會消失） */
private object MediaState {
    val lines = mutableStateListOf<Segment>()
    val words = mutableStateListOf<List<Pair<String, String>>>()
    var source by mutableStateOf("")
    var status by mutableStateOf("匯入歌曲、影片，或字幕／歌詞檔")
    var busy by mutableStateOf(false)
    val added = mutableStateListOf<String>()
}

@Composable
fun MediaScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDb.get(ctx).cards() }
    val engine = remember { WhisperEngine(ctx.applicationContext) }
    val s = MediaState

    fun nameOf(uri: Uri): String =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }?.substringBeforeLast('.') ?: "未命名"

    suspend fun show(segs: List<Segment>) {
        val corrector = Corrector(KuromojiAnalyzer.shared)
        val w = withContext(Dispatchers.Default) {
            segs.map { seg -> corrector.words(seg.text) }.also {
                segs.forEach { seg -> TypingPool.add(corrector.reading(seg.text)) }
            }
        }
        s.lines.clear(); s.lines.addAll(segs)
        s.words.clear(); s.words.addAll(w)
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || s.busy) return@rememberLauncherForActivityResult
        s.source = nameOf(uri)
        s.busy = true
        scope.launch {
            try {
                s.status = "解碼音訊…"
                val audio = withContext(Dispatchers.IO) {
                    AudioDecoder.decode(ctx, uri) { pct -> s.status = "解碼音訊… $pct%" }
                }
                s.status = "載入 Whisper 模型（第一次比較久）…"
                withContext(Dispatchers.IO) { engine.load() }
                val segs = withContext(Dispatchers.Default) {
                    Transcriber(engine, Corrector(KuromojiAnalyzer.shared))
                        .run(audio, s.source) { msg -> s.status = msg }
                }
                show(segs)
                s.status = if (segs.isEmpty()) "沒有辨識到日文，可以改用「匯入字幕／歌詞」"
                else "完成！共 ${segs.size} 句，點單字加入單字卡"
            } catch (e: Throwable) {
                s.status = "發生錯誤：${e.message}"
            } finally {
                s.busy = false
            }
        }
    }

    val pickSub = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || s.busy) return@rememberLauncherForActivityResult
        s.source = nameOf(uri)
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }.toString(Charsets.UTF_8)
                }
                val segs = SubtitleImport.parse(text)
                show(segs)
                s.status = "已匯入 ${segs.size} 句"
            } catch (e: Throwable) {
                s.status = "無法讀取：${e.message}"
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pickMedia.launch(arrayOf("audio/*", "video/*")) }, enabled = !s.busy) { Text("匯入影音") }
            OutlinedButton(onClick = { pickSub.launch(arrayOf("*/*")) }, enabled = !s.busy) { Text("匯入字幕／歌詞") }
        }
        Spacer(Modifier.width(8.dp))
        Text(s.status, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
        if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (s.source.isNotEmpty() && s.lines.isNotEmpty()) Text("📄 ${s.source}", style = MaterialTheme.typography.titleSmall)

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(s.lines) { i, seg ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        seg.text,
                        style = MaterialTheme.typography.titleMedium,
                        // 信心低的句子用橘色，提醒可能聽錯
                        color = if (seg.confidence < 0.55f) Color(0xFFE65100) else Color.Unspecified
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.words.getOrNull(i)?.forEach { (word, reading) ->
                            val done = word in s.added
                            AssistChip(
                                onClick = {
                                    if (done) return@AssistChip
                                    scope.launch {
                                        if (!dao.exists("vocab", word)) {
                                            dao.insert(Card(deck = "vocab", front = word, back = reading,
                                                source = "${s.source}：${seg.text}"))
                                        }
                                        s.added += word
                                    }
                                },
                                label = { Text(if (done) "✓ $word" else if (reading.isEmpty() || reading == word) word else "$word（$reading）") }
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
