package com.ice.nihongo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.ice.nihongo.data.ReviewScreen
import com.ice.nihongo.media.MediaScreen
import com.ice.nihongo.typing.TypingScreen
import com.ice.nihongo.vocab.VocabScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { AppRoot() } }
    }
}

private data class Tab(val label: String, val icon: String)

@Composable
fun AppRoot() {
    val tabs = listOf(Tab("影音", "🎵"), Tab("五十音", "あ"), Tab("單字", "📖"), Tab("打字", "⌨"))
    var sel by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(bottomBar = {
        NavigationBar {
            tabs.forEachIndexed { i, t ->
                NavigationBarItem(
                    selected = sel == i,
                    onClick = { sel = i },
                    icon = { Text(t.icon, fontSize = 20.sp) },
                    label = { Text(t.label) }
                )
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad)) {
            when (sel) {
                0 -> MediaScreen()
                1 -> ReviewScreen("kana")
                2 -> VocabScreen()
                else -> TypingScreen()
            }
        }
    }
}
