package com.example.translatorapp.localmodel

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperLocalEngine @Inject constructor() {
    suspend fun transcribe(audio: ByteArray): String {
        delay(50)
        return "离线识别结果"
    }
}
