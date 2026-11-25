package com.ice.nihongo.data

import androidx.room.Dao
import androidx.room.Delete
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

    @Query(
        "SELECT * FROM Card WHERE deck = :deck AND (front LIKE '%' || :q || '%' OR back LIKE '%' || :q || '%') " +
            "ORDER BY id DESC LIMIT 2000"
    )
    suspend fun search(deck: String, q: String): List<Card>

    @Query("SELECT front FROM Card WHERE deck = :deck")
    suspend fun fronts(deck: String): List<String>

    @Query("SELECT MAX(due) FROM Card WHERE deck = :deck AND reps = 0")
    suspend fun lastNewDue(deck: String): Long?

    @Delete
    suspend fun delete(card: Card)

    @Insert
    suspend fun insert(card: Card): Long

    @Insert
    suspend fun insertAll(cards: List<Card>)

    @Update
    suspend fun update(card: Card)
}
