package com.example.translatorapp.data.datasource

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.translatorapp.data.model.TranslationHistoryEntity

@Database(
    entities = [TranslationHistoryEntity::class],
    version = 2,
    exportSchema = true
)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE translation_history ADD COLUMN source_language TEXT")
                db.execSQL("ALTER TABLE translation_history ADD COLUMN target_language TEXT")
                db.execSQL("ALTER TABLE translation_history ADD COLUMN detected_language TEXT")
                db.execSQL("ALTER TABLE translation_history ADD COLUMN input_mode TEXT NOT NULL DEFAULT 'Voice'")
                db.execSQL("ALTER TABLE translation_history ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE translation_history ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE translation_history SET direction = CASE direction " +
                        "WHEN 'ChineseToFrench' THEN 'zh-CN|fr-FR' " +
                        "WHEN 'FrenchToChinese' THEN 'fr-FR|zh-CN' " +
                        "ELSE direction END"
                )
            }
        }
    }
}
