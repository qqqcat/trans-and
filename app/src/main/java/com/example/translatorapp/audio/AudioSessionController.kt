package com.example.translatorapp.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.example.translatorapp.util.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 采集/播放 16kHz 单声道 PCM16 音频，适配 Azure WebRTC 端点。
 * 用于 WebRTC 音频轨道采集与播放链路。
 */
@Singleton
class AudioSessionController @Inject constructor(
    private val permissionManager: PermissionManager
) {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: Flow<Boolean> = _isRecording.asStateFlow()

    fun isRecordingNow(): Boolean = _isRecording.value

    private var record: AudioRecord? = null
    private var play: AudioTrack? = null
    private var playbackSampleRate = SAMPLE_RATE
    private var audioJob: Job? = null
    private val ioScope = CoroutineScope(Dispatchers.IO)

    /**
     * 启动音频采集，采集参数：16kHz、单声道、PCM16。
     * onBuffer 回调可直接用于 WebRTC 音频轨道推流。
     */
    fun startCapture(onBuffer: suspend (ByteArray) -> Unit) {
        if (_isRecording.value) return
        // 检查权限
        if (!permissionManager.hasRecordAudioPermission()) {
            throw SecurityException("RECORD_AUDIO permission is required to start audio capture")
        }
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE) * 2
        try {
            record = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .build()
        } catch (e: SecurityException) {
            throw SecurityException("RECORD_AUDIO permission denied or revoked", e)
        }
        record?.startRecording()
        _isRecording.value = true
        audioJob = ioScope.launch {
            val buffer = ByteArray(minBufferSize)
            while (_isRecording.value) {
                val read = record?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    // 可直接推送到 WebRTC 音频轨道
                    onBuffer(buffer.copyOf(read))
                }
            }
        }
    }

    fun stopCapture() {
        _isRecording.value = false
        audioJob?.cancel()
        record?.stop()
        record?.release()
        record = null
    }

    /**
     * 播放音频流，参数需为 16kHz 单声道 PCM16。
     * 可用于 WebRTC 下行音频播放。
     */
    fun playAudio(stream: ByteArray, sampleRate: Int = SAMPLE_RATE) {
        val track = if (play == null || playbackSampleRate != sampleRate) {
            play?.stop()
            play?.release()
            createAudioTrack(sampleRate).also {
                play = it
                playbackSampleRate = sampleRate
            }
        } else {
            play!!
        }
        track.write(stream, 0, stream.size)
        track.play()
    }

    fun releasePlayback() {
        play?.stop()
        play?.release()
        play = null
        playbackSampleRate = SAMPLE_RATE
    }

    private fun createAudioTrack(sampleRate: Int): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(sampleRate)
            .build()

    companion object {
        /**
         * Azure WebRTC 端点要求采集/播放参数：16kHz、单声道、PCM16。
         */
        private const val SAMPLE_RATE = 16_000
    }
}
