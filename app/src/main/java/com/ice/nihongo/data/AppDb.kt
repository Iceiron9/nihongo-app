package com.ice.nihongo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ice.nihongo.kana.KanaData

@Database(entities = [Card::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun cards(): CardDao

    companion object {
        @Volatile private var inst: AppDb? = null

        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "nihongo.db")
                .build().also { inst = it }
        }

        /** 第一次打開時建立五十音卡片 */
        suspend fun seedKana(dao: CardDao) {
            if (dao.count("kana") > 0) return
            dao.insertAll(KanaData.all().map { Card(deck = "kana", front = it.first, back = it.second) })
        }
    }
}
