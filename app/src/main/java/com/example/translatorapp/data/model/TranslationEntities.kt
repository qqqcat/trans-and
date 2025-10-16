package com.example.translatorapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.TranslationHistoryItem
import kotlinx.datetime.toInstant

@Entity(tableName = "translation_history")
data class TranslationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "direction") val direction: String,
    @ColumnInfo(name = "source_text") val sourceText: String,
    @ColumnInfo(name = "translated_text") val translatedText: String,
    @ColumnInfo(name = "created_at") val createdAt: String
) {
    fun toDomain(): TranslationHistoryItem = TranslationHistoryItem(
        id = id,
        direction = LanguageDirection.valueOf(direction),
        sourceText = sourceText,
        translatedText = translatedText,
        createdAt = createdAt.toInstant()
    )

    companion object {
        fun fromDomain(item: TranslationHistoryItem): TranslationHistoryEntity = TranslationHistoryEntity(
            id = item.id,
            direction = item.direction.name,
            sourceText = item.sourceText,
            translatedText = item.translatedText,
            createdAt = item.createdAt.toString()
        )
    }
}

fun TranslationHistoryItem.toEntity(): TranslationHistoryEntity = TranslationHistoryEntity.fromDomain(this)
