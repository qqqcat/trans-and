package com.example.translatorapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationHistoryItem
import com.example.translatorapp.domain.model.TranslationInputMode
import kotlinx.datetime.toInstant

@Entity(tableName = "translation_history")
data class TranslationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "direction") val direction: String,
    @ColumnInfo(name = "source_text") val sourceText: String,
    @ColumnInfo(name = "translated_text") val translatedText: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "source_language") val sourceLanguage: String? = null,
    @ColumnInfo(name = "target_language") val targetLanguage: String? = null,
    @ColumnInfo(name = "detected_language") val detectedLanguage: String? = null,
    @ColumnInfo(name = "input_mode") val inputMode: String = TranslationInputMode.Voice.name,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "tags") val tags: String = "",
) {
    fun toDomain(): TranslationHistoryItem = TranslationHistoryItem(
        id = id,
        direction = buildDirection(),
        sourceText = sourceText,
        translatedText = translatedText,
        createdAt = createdAt.toInstant(),
        inputMode = runCatching { TranslationInputMode.valueOf(inputMode) }.getOrElse { TranslationInputMode.Voice },
        detectedSourceLanguage = detectedLanguage?.let { SupportedLanguage.fromCode(it) },
        isFavorite = isFavorite,
        tags = tags.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet(),
    )

    companion object {
        fun fromDomain(item: TranslationHistoryItem): TranslationHistoryEntity = TranslationHistoryEntity(
            id = item.id,
            direction = item.direction.encode(),
            sourceText = item.sourceText,
            translatedText = item.translatedText,
            createdAt = item.createdAt.toString(),
            sourceLanguage = item.direction.sourceLanguage?.code,
            targetLanguage = item.direction.targetLanguage.code,
            detectedLanguage = item.detectedSourceLanguage?.code,
            inputMode = item.inputMode.name,
            isFavorite = item.isFavorite,
            tags = item.tags.joinToString(separator = ",")
        )
    }

    private fun buildDirection(): LanguageDirection {
        if (!sourceLanguage.isNullOrBlank() || !targetLanguage.isNullOrBlank()) {
            val source = sourceLanguage?.let { SupportedLanguage.fromCode(it) }
            val target = SupportedLanguage.fromCode(targetLanguage)
            if (target != null) {
                return LanguageDirection(source, target)
            }
        }
        return LanguageDirection.decode(direction)
    }
}

fun TranslationHistoryItem.toEntity(): TranslationHistoryEntity = TranslationHistoryEntity.fromDomain(this)

fun TranslationContent.toHistoryEntity(direction: LanguageDirection): TranslationHistoryEntity {
    val actualDirection = LanguageDirection(
        sourceLanguage = detectedSourceLanguage ?: direction.sourceLanguage,
        targetLanguage = targetLanguage ?: direction.targetLanguage
    )
    val item = TranslationHistoryItem(
        id = 0,
        direction = actualDirection,
        sourceText = transcript,
        translatedText = translation,
        createdAt = timestamp,
        inputMode = inputMode,
        detectedSourceLanguage = detectedSourceLanguage,
        isFavorite = false,
        tags = emptySet()
    )
    return TranslationHistoryEntity.fromDomain(item)
}
