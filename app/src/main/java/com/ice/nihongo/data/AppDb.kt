package com.ice.nihongo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ice.nihongo.kana.KanaData

@Database(entities = [Card::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun cards(): CardDao

    companion object {
        @Volatile private var inst: AppDb? = null

        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "nihongo.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { inst = it }
        }

        /**
         * 第 2 版：新卡改成「每天限額」，不再預先排好日期。
         * 加上 introduced 欄位，已經學過的卡標記起來，還沒學的新卡日期歸零。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Card ADD COLUMN introduced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE Card SET introduced = 1 WHERE reps > 0 OR ease != 2.5")
                db.execSQL("UPDATE Card SET due = 0 WHERE introduced = 0")
            }
        }

        /** 第一次打開時建立五十音卡片 */
        suspend fun seedKana(dao: CardDao) {
            if (dao.count("kana") > 0) return
            dao.insertAll(KanaData.all().map { Card(deck = "kana", front = it.first, back = it.second) })
        }
    }
}
