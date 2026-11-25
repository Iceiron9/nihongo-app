package com.ice.nihongo.typing

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ice.nihongo.data.AppDb
import com.ice.nihongo.data.Packs

/** 一個九宮格按鍵：點一下或往四個方向滑 */
@Composable
private fun FlickKey(key: String, onInput: (String) -> Unit) {
    var dir by remember { mutableStateOf(-1) } // 正在滑的方向，用來顯示提示
    val threshold = with(LocalDensity.current) { 24.dp.toPx() }
    Box(
        Modifier.size(100.dp, 62.dp).padding(3.dp)
            .background(
                if (dir >= 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(10.dp)
            )
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var total = Offset.Zero
                    dir = 0
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        total = ch.position - down.position
                        dir = Flick.index(total.x, total.y, threshold)
                        ch.consume()
                        if (!ch.pressed) break
                    }
                    onInput(Flick.output(key, Flick.index(total.x, total.y, threshold)))
                    dir = -1
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val label = when {
            key == Flick.BACKSPACE || key == Flick.DAKUTEN -> key
            dir > 0 -> Flick.output(key, dir).ifEmpty { key[0].toString() }
            else -> key[0].toString()
        }
        Text(label, fontSize = 26.sp)
    }
}

@Composable
fun TypingScreen() {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).cards() }
    var extra by remember { mutableStateOf(listOf<String>()) }
    var target by remember { mutableStateOf(TypingPool.next()) }
    var typed by remember { mutableStateOf("") }
    var start by remember { mutableLongStateOf(0L) }
    var result by remember { mutableStateOf("") }
    var count by remember { mutableStateOf(0) }

    // 單字卡的讀音也拿來當題目
    LaunchedEffect(Unit) {
        extra = dao.recent("vocab", 300).mapNotNull { Packs.readingOf(it.front, it.back) }.distinct()
        if (TypingPool.size() == 0 && extra.isNotEmpty()) target = TypingPool.next(extra)
    }

    fun input(out: String) {
        if (start == 0L && out.isNotEmpty()) start = System.currentTimeMillis()
        typed = Flick.apply(typed, out)
        if (typed == target) {
            val sec = (System.currentTimeMillis() - start) / 1000.0
            count++
            result = "第 $count 題完成！%.1f 秒，每分鐘 %.0f 字".format(sec, target.length / maxOf(sec, 0.1) * 60)
            target = TypingPool.next(extra); typed = ""; start = 0L
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("九宮格打字練習", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Text(target, fontSize = 30.sp)
        // 打對的字綠色，打錯的紅色
        Text(buildAnnotatedString {
            typed.forEachIndexed { i, c ->
                withStyle(SpanStyle(color = if (target.getOrNull(i) == c) Color(0xFF2E7D32) else Color.Red)) { append(c) }
            }
            append("▏")
        }, fontSize = 30.sp, modifier = Modifier.padding(12.dp))
        Text(result, color = Color(0xFF1976D2))
        TextButton(onClick = { target = TypingPool.next(extra); typed = ""; start = 0L }) { Text("換一題") }
        Spacer(Modifier.weight(1f))
        Text("點一下輸入あ段；往左、上、右、下滑是い、う、え、お段", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Flick.keys.forEach { row -> Row { row.forEach { k -> FlickKey(k, ::input) } } }
        }
    }
}
