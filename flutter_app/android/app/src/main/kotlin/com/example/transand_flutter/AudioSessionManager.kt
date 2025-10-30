package com.example.transand_flutter

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log

class AudioSessionManager {
    private val TAG = "AudioSessionManager"

    companion object {
        private var instance: AudioSessionManager? = null

        fun getInstance(): AudioSessionManager {
            return instance ?: AudioSessionManager().also { instance = it }
        }
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    /**
     * Configure system-level Acoustic Echo Cancellation for the given AudioRecord session
     */
    fun configureSystemAEC(audioRecordSessionId: Int): Boolean {
        try {
            // Check if AcousticEchoCanceler is available
            if (!AcousticEchoCanceler.isAvailable()) {
                Log.w(TAG, "AcousticEchoCanceler is not available on this device")
                return false
            }

            // Release existing AEC if any
            acousticEchoCanceler?.release()
            acousticEchoCanceler = null

            // Create AcousticEchoCanceler for the session
            acousticEchoCanceler = AcousticEchoCanceler.create(audioRecordSessionId)
            if (acousticEchoCanceler == null) {
                Log.w(TAG, "Failed to create AcousticEchoCanceler for session $audioRecordSessionId")
                return false
            }

            // Enable AEC
            val enabled = acousticEchoCanceler!!.enabled
            if (!enabled) {
                acousticEchoCanceler!!.enabled = true
                Log.i(TAG, "AcousticEchoCanceler enabled for session $audioRecordSessionId")
            } else {
                Log.i(TAG, "AcousticEchoCanceler was already enabled for session $audioRecordSessionId")
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring AcousticEchoCanceler", e)
            return false
        }
    }

    /**
     * Configure Automatic Gain Control for the given AudioRecord session
     */
    fun configureAGC(audioRecordSessionId: Int): Boolean {
        try {
            if (!AutomaticGainControl.isAvailable()) {
                Log.w(TAG, "AutomaticGainControl is not available on this device")
                return false
            }

            // Release existing AGC if any
            automaticGainControl?.release()
            automaticGainControl = null

            automaticGainControl = AutomaticGainControl.create(audioRecordSessionId)
            if (automaticGainControl == null) {
                Log.w(TAG, "Failed to create AutomaticGainControl for session $audioRecordSessionId")
                return false
            }

            val enabled = automaticGainControl!!.enabled
            if (!enabled) {
                automaticGainControl!!.enabled = true
                Log.i(TAG, "AutomaticGainControl enabled for session $audioRecordSessionId")
            } else {
                Log.i(TAG, "AutomaticGainControl was already enabled for session $audioRecordSessionId")
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring AutomaticGainControl", e)
            return false
        }
    }

    /**
     * Configure Noise Suppression for the given AudioRecord session
     */
    fun configureNoiseSuppression(audioRecordSessionId: Int): Boolean {
        try {
            if (!NoiseSuppressor.isAvailable()) {
                Log.w(TAG, "NoiseSuppressor is not available on this device")
                return false
            }

            // Release existing NoiseSuppressor if any
            noiseSuppressor?.release()
            noiseSuppressor = null

            noiseSuppressor = NoiseSuppressor.create(audioRecordSessionId)
            if (noiseSuppressor == null) {
                Log.w(TAG, "Failed to create NoiseSuppressor for session $audioRecordSessionId")
                return false
            }

            val enabled = noiseSuppressor!!.enabled
            if (!enabled) {
                noiseSuppressor!!.enabled = true
                Log.i(TAG, "NoiseSuppressor enabled for session $audioRecordSessionId")
            } else {
                Log.i(TAG, "NoiseSuppressor was already enabled for session $audioRecordSessionId")
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring NoiseSuppressor", e)
            return false
        }
    }

    /**
     * Configure audio communication device using modern Android APIs
     * This prevents audio loopback issues and ensures proper AEC
     */
    fun configureCommunicationDevice(audioManager: AudioManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.w(TAG, "setCommunicationDevice requires API 31+ (Android 12+)")
            return false
        }

        try {
            // Get available communication devices
            val devices = audioManager.availableCommunicationDevices
            if (devices.isEmpty()) {
                Log.w(TAG, "No communication devices available")
                return false
            }

            // Prefer Bluetooth SCO devices, then wired headsets, then built-in earpiece
            val preferredDevice = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO } ?:
                                 devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET } ?:
                                 devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE } ?:
                                 devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

            if (preferredDevice != null) {
                audioManager.setCommunicationDevice(preferredDevice)
                Log.i(TAG, "Set communication device to: ${preferredDevice.productName} (type: ${preferredDevice.type})")
                return true
            } else {
                Log.w(TAG, "No suitable communication device found")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure communication device", e)
            return false
        }
    }

    /**
     * Create a temporary AudioRecord to obtain a valid session ID for audio effects
     * This is needed when we don't have access to the WebRTC AudioRecord session
     */
    private fun createTemporaryAudioRecordSession(): Int {
        try {
            // Use minimal audio format for temporary recording
            val sampleRate = 16000
            val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
            val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            if (minBufferSize <= 0) {
                Log.e(TAG, "Invalid buffer size for temporary AudioRecord")
                return 0
            }

            // Create temporary AudioRecord
            val tempAudioRecord = AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize
            )

            if (tempAudioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize temporary AudioRecord")
                tempAudioRecord.release()
                return 0
            }

            // Get the session ID
            val sessionId = tempAudioRecord.audioSessionId
            Log.i(TAG, "Created temporary AudioRecord with session ID: $sessionId")

            // Release the temporary AudioRecord immediately
            tempAudioRecord.release()

            return sessionId
        } catch (e: Exception) {
            Log.e(TAG, "Error creating temporary AudioRecord session", e)
            return 0
        }
    }
    fun configureAllAudioEffects(audioRecordSessionId: Int, audioManager: AudioManager): Boolean {
        var success = true
        var actualSessionId = audioRecordSessionId

        // If sessionId is 0 or invalid, create a temporary AudioRecord to get a valid session ID
        if (actualSessionId == 0) {
            actualSessionId = createTemporaryAudioRecordSession()
            if (actualSessionId == 0) {
                Log.w(TAG, "Failed to create temporary AudioRecord session for audio effects")
                return false
            }
            Log.i(TAG, "Using temporary AudioRecord session ID: $actualSessionId")
        }

        // Configure system-level audio effects
        success = success and configureSystemAEC(actualSessionId)
        success = success and configureAGC(actualSessionId)
        success = success and configureNoiseSuppression(actualSessionId)

        // Configure communication device routing
        success = success and configureCommunicationDevice(audioManager)

        return success
    }

    /**
     * Release all audio effects and resources
     */
    fun release() {
        try {
            acousticEchoCanceler?.release()
            acousticEchoCanceler = null

            automaticGainControl?.release()
            automaticGainControl = null

            noiseSuppressor?.release()
            noiseSuppressor = null

            Log.i(TAG, "Audio effects released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio effects", e)
        }
    }
}