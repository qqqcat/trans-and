package com.example.transand_flutter

import android.media.AudioManager
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.example.transand_flutter/audio_session"
    private lateinit var audioSessionManager: AudioSessionManager

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        audioSessionManager = AudioSessionManager.getInstance()

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "configureSystemAEC" -> {
                    val sessionId = call.argument<Int>("sessionId")
                    if (sessionId != null) {
                        val success = audioSessionManager.configureSystemAEC(sessionId)
                        result.success(success)
                    } else {
                        result.error("INVALID_ARGUMENT", "sessionId is required", null)
                    }
                }
                "configureAllAudioEffects" -> {
                    val sessionId = call.argument<Int>("sessionId")
                    val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                    if (sessionId != null) {
                        val success = audioSessionManager.configureAllAudioEffects(sessionId, audioManager)
                        result.success(success)
                    } else {
                        result.error("INVALID_ARGUMENT", "sessionId is required", null)
                    }
                }
                "configureCommunicationDevice" -> {
                    val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                    val success = audioSessionManager.configureCommunicationDevice(audioManager)
                    result.success(success)
                }
                "releaseAudioEffects" -> {
                    audioSessionManager.release()
                    result.success(null)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioSessionManager.release()
    }
}
