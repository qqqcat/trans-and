# Offline Whisper And TTS Roadmap

## Goals
- Deliver reliable offline speech to text and text to speech when network quality is poor or privacy sensitive.
- Ship whisper.cpp tiny as built-in baseline; offer whisper.cpp turbo as optional download for multilingual transcription and translation.
- Keep application size reasonable by using split delivery or on-demand downloads.
- Reuse existing audio capture, repository, and UI flows with minimal disruption.

## Target Architecture
- `AudioSessionController` keeps ownership of PCM capture at 16 kHz mono.
- `OfflineVoiceController` coordinates capture, buffering, voice activity detection, whisper inference, translation, and synthesis.
- `WhisperRuntime` loads whisper.cpp native library and executes transcription or direct translation on background dispatcher.
- `OfflineModelManager` tracks installed models, handles checksum verification, and exposes selection policies.
- `LocalTtsSynthesizer` wraps Android `TextToSpeech` with coroutine friendly APIs and audio file caching.
- `TranslationRepository` decides between realtime (network) and offline based on session profile, connectivity, and installed assets.

### Data Flow
1. UI requests `TranslationRepository.startRealtimeSession` with `TranslationModelProfile.Offline`.
2. Repository resolves settings, checks `OfflineModelManager`.
3. If ready, repository invokes `OfflineVoiceController.start`, otherwise falls back to remote or prompts model download.
4. `AudioSessionController` streams PCM frames into `OfflineVoiceController`.
5. `OfflineVoiceController` segments audio with VAD, enqueues work items to `WhisperRuntime`.
6. `WhisperRuntime` returns transcript plus optional translated text depending on model capability.
7. `LocalTranslator` post-processes transcript when whisper model cannot translate directly.
8. `LocalTtsSynthesizer` generates playback buffer when target language requires spoken output.
9. Repository emits `TranslationContent` to flows, mirrors behaviour of realtime path.

## Model Packaging Strategy
- **Baseline (tiny.en)**: ship with app through asset pack or Play Feature Delivery "on demand" module to avoid APK bloat (>75 MB compressed). Provides English transcription only; translation handled in software (English -> target).
- **Optional (turbo)**: downloadable pack (~1.5 GB). Exposed via settings screen with progress UI. Supports multilingual recognition and translation.
- Store models under `Context.filesDir / whisper / models / {modelName}` with manifest JSON describing checksum, version, size, language capabilities.
- Use `WorkManager` for background downloads with resume support, verifying SHA256 via manifest.
- Provide user controls: auto-download on Wi-Fi, manual delete, storage usage display.

## JNI And Native Integration
- Include whisper.cpp as a git submodule or download during CI; compile via CMake into shared library `libwhisper_release.so`.
- Add NDK toolchain configuration in `app/build.gradle.kts` with `externalNativeBuild { cmake }`.
- Expose thin JNI wrapper:
  - `nativeInit(modelPath, threads, enableTranslation)` returning handle.
  - `nativeProcess(handle, pcmBuffer, sampleRate, language, task)` returning JSON string with timing and text.
  - `nativeRelease(handle)`.
- Manage lifecycle via `WhisperRuntime` using `SharedFlow` to report progress, load state, and errors.
- Use `Dispatchers.Default.limitedParallelism(1)` for inference queue to avoid CPU contention.

## TTS Integration
- Implement `LocalTtsSynthesizer` with Android `TextToSpeech`.
- Pre-load voices matching `UserSettings.direction.targetLanguage`.
- Provide synthesized audio as PCM for reuse by `AudioSessionController.playAudio`.
- Cache synthesized results (`hash(transcript,targetLanguage)`) in `filesDir/tts-cache` to avoid re-synthesis.
- Offer fallback where device lacks TTS voice by prompting user to install voice pack.

## Implementation Phases
1. **Scaffolding (current task)**  
   - Define Kotlin interfaces (`OfflineVoiceController`, `WhisperRuntime`, `OfflineModelManager`, `LocalTtsSynthesizer`).  
   - Wire Hilt modules, configuration flags, and repository decision logic.  
   - Provide model manifest schema and download placeholder implementation.
2. **Native Bring-up**  
   - Integrate whisper.cpp submodule, add CMake toolchain, implement JNI wrapper.  
   - Validate transcription pipeline with unit tests using short PCM fixtures.
3. **Model Delivery**  
   - Implement asset packs / download manager, UI messaging, error handling.  
   - Add background workers and storage quota management.
4. **Polish And QA**  
   - Optimize latency (thread tuning, VAD window).  
   - Add instrumentation tests, telemetry hooks (offline safe).  
   - Update documentation, release notes, and fallback heuristics.

## Risks And Mitigations
- **Large model sizes**: use optional downloads, show storage usage, support deletion.  
- **CPU pressure on mid-tier devices**: allow thread count configuration, expose "balanced" vs "quality" settings.  
- **TTS voice availability**: detect installed voices, guide user to download.  
- **UX complexity**: consolidate controls under Settings > Offline speech, reuse existing UI patterns.
