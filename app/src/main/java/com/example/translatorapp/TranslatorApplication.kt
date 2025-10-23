package com.example.translatorapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TranslatorApplication : Application() {
	companion object {
		@Volatile
		private var instance: TranslatorApplication? = null

		fun getInstance(): TranslatorApplication {
			return instance ?: throw IllegalStateException("Application not initialized")
		}
	}

	override fun onCreate() {
		super.onCreate()
		instance = this
	}
}
