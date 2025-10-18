package com.example.translatorapp.offline

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class OfflineModelProfile(
    val defaultFileName: String,
    val supportsMultilingual: Boolean
) {
    Tiny("ggml-tiny.en.bin", supportsMultilingual = false),
    Turbo("ggml-large-v3-turbo.bin", supportsMultilingual = true)
}

data class OfflineModel(
    val profile: OfflineModelProfile,
    val file: File
) {
    val supportsTranslation: Boolean
        get() = profile.supportsMultilingual
}

data class OfflineModelState(
    val installedProfiles: Set<OfflineModelProfile> = emptySet(),
    val installingProfile: OfflineModelProfile? = null,
    val downloadProgress: Map<OfflineModelProfile, Float> = emptyMap()
)

private data class ModelDescriptor(
    val profile: OfflineModelProfile,
    val url: String,
    val sha256: String,
    val sizeBytes: Long
)

@Singleton
class OfflineModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    private val descriptors = mapOf(
        OfflineModelProfile.Tiny to ModelDescriptor(
            profile = OfflineModelProfile.Tiny,
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin?download=1",
            sha256 = "921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f",
            sizeBytes = 77_704_715L
        ),
        OfflineModelProfile.Turbo to ModelDescriptor(
            profile = OfflineModelProfile.Turbo,
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo.bin?download=1",
            sha256 = "1fc70f774d38eb169993ac391eea357ef47c88757ef72ee5943879b7e8e2bc69",
            sizeBytes = 1_624_555_275L
        )
    )

    private val modelsDir: File = File(context.filesDir, "whisper/models")

    private val _state = MutableStateFlow(OfflineModelState())
    val state: StateFlow<OfflineModelState> = _state.asStateFlow()

    suspend fun ensureModel(profile: OfflineModelProfile): OfflineModel = withContext(Dispatchers.IO) {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        val descriptor = descriptors[profile] ?: error("No descriptor for $profile")
        val modelFile = File(modelsDir, profile.defaultFileName)
        if (!modelFile.exists() || !verifyChecksum(modelFile, descriptor.sha256)) {
            downloadModel(descriptor, modelFile)
        } else {
            _state.update { current ->
                current.copy(installedProfiles = current.installedProfiles + profile)
            }
        }
        OfflineModel(profile, modelFile)
    }

    private suspend fun downloadModel(descriptor: ModelDescriptor, target: File) {
        val tempFile = File(target.parentFile, "${target.name}.download")
        _state.update { state ->
            state.copy(
                installingProfile = descriptor.profile,
                downloadProgress = state.downloadProgress + (descriptor.profile to 0f)
            )
        }
        try {
            val request = Request.Builder().url(descriptor.url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Failed to download model: HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Empty response body for model download")
                val total = if (descriptor.sizeBytes > 0) descriptor.sizeBytes else body.contentLength()
                tempFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        val digest = MessageDigest.getInstance("SHA-256")
                        var read: Int
                        var downloaded = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val progress = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                _state.update { current ->
                                    val map = current.downloadProgress.toMutableMap()
                                    map[descriptor.profile] = progress
                                    current.copy(
                                        installingProfile = descriptor.profile,
                                        downloadProgress = map
                                    )
                                }
                            }
                        }
                        output.flush()
                        val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                        if (!actualSha.equals(descriptor.sha256, ignoreCase = true)) {
                            throw IOException("Checksum mismatch for ${descriptor.profile}: expected ${descriptor.sha256} got $actualSha")
                        }
                    }
                }
            }
            if (descriptor.sizeBytes > 0 && tempFile.length() != descriptor.sizeBytes) {
                throw IOException("Size mismatch for ${descriptor.profile}: expected ${descriptor.sizeBytes} got ${tempFile.length()}")
            }
            if (target.exists()) {
                target.delete()
            }
            if (!tempFile.renameTo(target)) {
                throw IOException("Unable to move downloaded model into place")
            }
            _state.update { state ->
                val progress = state.downloadProgress.toMutableMap()
                progress.remove(descriptor.profile)
                state.copy(
                    installingProfile = null,
                    installedProfiles = state.installedProfiles + descriptor.profile,
                    downloadProgress = progress
                )
            }
        } catch (error: Throwable) {
            tempFile.delete()
            _state.update { state ->
                val progress = state.downloadProgress.toMutableMap()
                progress.remove(descriptor.profile)
                state.copy(
                    installingProfile = null,
                    downloadProgress = progress
                )
            }
            throw error
        }
    }

    private fun verifyChecksum(file: File, expectedSha: String): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha, ignoreCase = true)
    }

    fun markInstalling(profile: OfflineModelProfile) {
        _state.update { it.copy(installingProfile = profile) }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}
