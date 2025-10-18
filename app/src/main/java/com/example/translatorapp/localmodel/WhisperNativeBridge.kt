package com.example.translatorapp.localmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhisperNativeBridge {

    init {
        System.loadLibrary("whisper-bridge")
    }

    suspend fun init(modelPath: String, preferredThreads: Int): Long =
        withContext(Dispatchers.Default) {
            nativeInit(modelPath, preferredThreads)
        }

    suspend fun release(handle: Long) {
        withContext(Dispatchers.Default) {
            nativeRelease(handle)
        }
    }

    fun process(
        handle: Long,
        audio: ByteArray,
        sampleRate: Int,
        sourceLanguage: String?,
        targetLanguage: String,
        enableTranslation: Boolean,
        preferredThreads: Int
    ): String = nativeProcess(
        handle,
        audio,
        sampleRate,
        sourceLanguage,
        targetLanguage,
        enableTranslation,
        preferredThreads
    )

    private external fun nativeInit(modelPath: String, preferredThreads: Int): Long
    private external fun nativeRelease(handle: Long)
    private external fun nativeProcess(
        handle: Long,
        audio: ByteArray,
        sampleRate: Int,
        sourceLanguage: String?,
        targetLanguage: String,
        enableTranslation: Boolean,
        preferredThreads: Int
    ): String
}
