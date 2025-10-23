package com.example.translatorapp.data.datasource

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.translatorapp.data.model.TranslationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM translation_history ORDER BY created_at DESC")
    fun observeHistory(): Flow<List<TranslationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TranslationHistoryEntity)

    @Query("DELETE FROM translation_history")
    suspend fun clear()

    @Query("UPDATE translation_history SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE translation_history SET tags = :tags WHERE id = :id")
    suspend fun updateTags(id: Long, tags: String)
}
