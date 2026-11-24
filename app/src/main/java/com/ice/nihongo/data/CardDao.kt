package com.ice.nihongo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface CardDao {
    @Query("SELECT * FROM Card WHERE deck = :deck AND due <= :now ORDER BY due LIMIT 100")
    suspend fun dueCards(deck: String, now: Long): List<Card>

    @Query("SELECT COUNT(*) FROM Card WHERE deck = :deck AND due <= :now")
    suspend fun dueCount(deck: String, now: Long): Int

    @Query("SELECT COUNT(*) FROM Card WHERE deck = :deck")
    suspend fun count(deck: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM Card WHERE deck = :deck AND front = :front)")
    suspend fun exists(deck: String, front: String): Boolean

    @Query("SELECT * FROM Card WHERE deck = :deck ORDER BY id DESC LIMIT :limit")
    suspend fun recent(deck: String, limit: Int): List<Card>

    @Insert
    suspend fun insert(card: Card): Long

    @Insert
    suspend fun insertAll(cards: List<Card>)

    @Update
    suspend fun update(card: Card)
}
