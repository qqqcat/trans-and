package com.example.translatorapp.offline

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

enum class OfflineModelProfile(
    val defaultFileName: String,
    val supportsMultilingual: Boolean
) {
    Tiny("ggml-tiny.en.bin", supportsMultilingual = false),
    Turbo("ggml-turbo.bin", supportsMultilingual = true)
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
    val installingProfile: OfflineModelProfile? = null
)

@Singleton
class OfflineModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val modelsDir: File = File(context.filesDir, "whisper/models")

    private val _state = MutableStateFlow(OfflineModelState())
    val state: StateFlow<OfflineModelState> = _state.asStateFlow()

    suspend fun ensureModel(profile: OfflineModelProfile): OfflineModel = withContext(Dispatchers.IO) {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        val modelFile = File(modelsDir, profile.defaultFileName)
        if (!modelFile.exists()) {
            modelFile.writeBytes(byteArrayOf()) // placeholder marker until real download pipeline
        }
        _state.update { current ->
            current.copy(installedProfiles = current.installedProfiles + profile, installingProfile = null)
        }
        OfflineModel(profile = profile, file = modelFile)
    }

    fun markInstalling(profile: OfflineModelProfile) {
        _state.update { it.copy(installingProfile = profile) }
    }
}
