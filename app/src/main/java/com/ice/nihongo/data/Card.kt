package com.ice.nihongo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 一張卡片。五十音（deck = "kana"）和單字（deck = "vocab"）共用。 */
@Entity
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deck: String,
    val front: String,          // 例如 あ 或 桜
    val back: String,           // 讀音與意思
    val source: String? = null, // 出自哪首歌或影片、哪一句
    val ease: Double = 2.5,
    val interval: Int = 0,      // 天
    val reps: Int = 0,
    val due: Long = 0L          // 0 = 新卡，馬上可以複習
)
