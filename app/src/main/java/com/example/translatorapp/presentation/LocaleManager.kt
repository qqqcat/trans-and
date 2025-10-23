package com.example.translatorapp.presentation

import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import com.example.translatorapp.R

object LocaleManager {
    private const val TAG = "LocaleManager"

    fun applyLocale(languageTag: String?, isUserChange: Boolean = false) {
        Log.d(TAG, "applyLocale called with languageTag: $languageTag")
        val tag = languageTag
            ?.takeIf { it.isNotBlank() }
            ?.let { normalizeTag(it) }
        Log.d(TAG, "Normalized tag: $tag")
        val locales = if (tag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }

        // Android 13+ (API 33): use system LocaleManager#setApplicationLocales
        val apiLevel = android.os.Build.VERSION.SDK_INT
        Log.d(TAG, "API level: $apiLevel")
        if (apiLevel >= 33) {
            Log.d(TAG, "Attempting API 33+ LocaleManager")
            try {
                val context = getApplicationContext()
                val localeManagerClass = Class.forName("android.os.LocaleManager")
                val localeListClass = Class.forName("android.os.LocaleList")
                val localeManager = context.getSystemService(localeManagerClass)
                val tags = locales.toLanguageTags()
                Log.d(TAG, "Language tags: $tags")
                val localeList = localeListClass.getMethod("forLanguageTags", String::class.java).invoke(null, tags)
                localeManager?.javaClass?.getMethod("setApplicationLocales", localeListClass)?.invoke(localeManager, localeList)
                Log.d(TAG, "Successfully set application locales via reflection")
                // For Compose apps, we need to restart the process for locale changes to take effect
                if (isUserChange) {
                    Log.d(TAG, "Scheduling app restart for locale change")
                    restartApp(context)
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "API 33+ LocaleManager not available or failed, falling back to AppCompatDelegate", e)
                // fallback to AppCompatDelegate
            }
        }

        // Fallback: AppCompatDelegate (Android 12-)
        Log.d(TAG, "Using AppCompatDelegate fallback")
        val current = AppCompatDelegate.getApplicationLocales()
        Log.d(TAG, "Current locales: ${current.toLanguageTags()}")
        if (current.toLanguageTags() == locales.toLanguageTags()) {
            Log.d(TAG, "Locales already set, skipping")
            return
        }
        if (locales.size() > 0) {
            locales[0]?.let { Locale.setDefault(it) }
            Log.d(TAG, "Set JVM default locale to ${locales[0]}")
        }
        AppCompatDelegate.setApplicationLocales(locales)
        Log.d(TAG, "Set application locales via AppCompatDelegate")
        
        // Force configuration update for devices where AppCompatDelegate doesn't work properly
        try {
            val context = getApplicationContext()
            val config = android.content.res.Configuration(context.resources.configuration)
            val localeList = android.os.LocaleList.forLanguageTags(locales.toLanguageTags())
            config.setLocales(localeList)
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            Log.d(TAG, "Forced configuration update")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to force configuration update", e)
        }
        
        // For Compose apps, we need to restart the process for locale changes to take effect
        if (isUserChange) {
            Log.d(TAG, "Scheduling app restart for locale change")
            restartApp(getApplicationContext())
        }
    }

    // 获取全局 ApplicationContext
    private fun getApplicationContext(): android.content.Context {
        // 尝试多种方式获取Application实例
        try {
            // 方法1: 通过反射获取TranslatorApplication的instance
            val clazz = Class.forName("com.example.translatorapp.TranslatorApplication")
            val field = clazz.getDeclaredField("instance")
            field.isAccessible = true
            val app = field.get(null) as? android.app.Application
            if (app != null) {
                return app
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Application via reflection field", e)
        }

        try {
            // 方法2: 通过反射调用getInstance方法
            val clazz = Class.forName("com.example.translatorapp.TranslatorApplication")
            val method = clazz.getDeclaredMethod("getInstance")
            method.isAccessible = true
            return method.invoke(null) as android.content.Context
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Application via reflection method", e)
        }

        // 方法3: 如果以上都失败，抛出异常
        throw IllegalStateException("Unable to get ApplicationContext for LocaleManager")
    }

    private fun restartApp(context: android.content.Context) {
        try {
            // Show a toast to inform user about restart
            android.widget.Toast.makeText(context, context.getString(R.string.locale_restart_message), android.widget.Toast.LENGTH_SHORT).show()

            // Use AlarmManager to schedule restart
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

            val pendingIntent = android.app.PendingIntent.getActivity(context, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)

            val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.setExact(android.app.AlarmManager.RTC, System.currentTimeMillis() + 1000, pendingIntent)

            // Kill current process after scheduling
            android.os.Process.killProcess(android.os.Process.myPid())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart app with AlarmManager", e)
            // Fallback: just recreate current activity
            try {
                (context as? android.app.Activity)?.recreate()
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to recreate activity", e2)
            }
        }
    }

    private fun normalizeTag(tag: String): String {
        return when (tag.lowercase()) {
            "en", "en_us", "en-us" -> "en-US"
            "zh", "zh_cn", "zh-cn" -> "zh-CN"
            "zh_tw", "zh-tw" -> "zh-TW"
            "es", "es_es", "es-es" -> "es-ES"
            "fr", "fr_fr", "fr-fr" -> "fr-FR"
            "ja", "ja_jp", "ja-jp" -> "ja-JP"
            "ko", "ko_kr", "ko-kr" -> "ko-KR"
            "ar", "ar_sa", "ar-sa" -> "ar-SA"
            "ru", "ru_ru", "ru-ru" -> "ru-RU"
            else -> tag
        }
    }
}
