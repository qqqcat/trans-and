# TransAnd Flutter
Cross-platform rebuild of the TransAnd realtime interpreter. The Flutter target keeps the original goals (low-latency duplex speech translation powered by Azure OpenAI Realtime) while consolidating the stack for Android, iOS, and Web.

## Prerequisites

- Flutter 3.24+ (any channel with web support enabled)
- Dart 3.4+
- Android/iOS toolchains as required for device builds
- Azure OpenAI deployment that exposes a Realtime model (for example `gpt-realtime-mini`)

Key packages in use:

- `flutter_webrtc` for microphone capture, playback, and SDP/ICE negotiation [[docs](https://github.com/flutter-webrtc/flutter-webrtc/blob/main/README.md)]
- `drift` + `drift_flutter` for cross-platform history persistence [[docs](https://github.com/simolus3/drift/blob/develop/drift_flutter/README.md)]

## Project Structure

```
lib/
  app/                // Router, theming, ProviderScope
  core/               // Bootstrap, config, logging, service locator
  services/           // Realtime API client, WebRTC glue, audio session
  presentation/       // Feature widgets (home, history, settings)
  data/               // Repositories wrapping local storage
  domain/             // UI-friendly models
tests/                // Smoke tests & future unit coverage
```

## Configuration

`AppConfig.load()` resolves Azure credentials at startup in the following order:

1. Dart defines `AZURE_OPENAI_ENDPOINT` / `AZURE_OPENAI_API_KEY`
2. Process environment variables (available on Android, iOS, desktop)
3. `local.properties` entries:
   ```
   azure.openai.endpoint=https://<your-endpoint>/
   azure.openai.apiKey=<your-secret>
   ```

For web builds you **must** supply Dart defines because browser builds cannot read OS environment variables:

```bash
flutter run -d chrome \
  --dart-define=AZURE_OPENAI_ENDPOINT=https://<your-endpoint>/ \
  --dart-define=AZURE_OPENAI_API_KEY=<your-secret>
```

> Keep secrets out of source control. The Flutter CLI supports `--dart-define-from-file` so you can point to an ignored `.env` file instead of typing keys into the command line.

On mobile the existing `local.properties` fallback continues to work as long as the file lives next to the Flutter workspace root (`../local.properties`).

### Optional realtime tuning

You can fine-tune the realtime session defaults through optional defines / environment variables (they fall back to the values baked into `AppConfig`):

| Key | Purpose |
| --- | --- |
| `AZURE_REALTIME_TRANSCRIPTION_MODEL` | Speech-to-text model used for automatic transcription (for example `gpt-4o-mini-transcribe` or `whisper-1`). |
| `AZURE_REALTIME_TURN_DETECTION` | Set to `server_vad` (default) to keep Azure's voice-activity detection or `none` to drive turns manually. |
| `AZURE_REALTIME_TURN_DETECTION_THRESHOLD` | Optional VAD score threshold (0-1) when using `server_vad`. |
| `AZURE_REALTIME_TURN_DETECTION_SILENCE_MS` | Optional trailing silence duration (milliseconds) before a turn is committed. |
| `AZURE_REALTIME_MUTE_MIC_DURING_PLAYBACK` | When `true` (default) the app temporarily mutes the local mic while the assistant is speaking to avoid barge-in feedback. |

## Setup & Tooling

```bash
cd flutter_app
flutter pub get
flutter analyze
```

- Add platform folders if missing: `flutter create .` (only needed once after cloning)
- Optional when code generation is introduced: `flutter pub run build_runner watch`

## Running

```bash
# Web (Chrome)
flutter run -d chrome --dart-define=AZURE_OPENAI_ENDPOINT=... --dart-define=AZURE_OPENAI_API_KEY=...

# Android
flutter run -d emulator-5554

# iOS (requires macOS)
flutter run -d ios
```

The Settings page lets you override the endpoint and deployment model stored in shared preferences. API keys are still provided via `AppConfig` so that secrets never persist in local storage.

## Realtime Session Flow

1. Request a short-lived client secret from Azure OpenAI `/realtimeapi/sessions` (preview API `2025-04-01-preview`) [[docs](https://github.com/azure-samples/aoai-realtime-audio-sdk/blob/main/samples/middle-tier/README.md)].
2. Negotiate WebRTC (SDP offer/answer and ICE) through `flutter_webrtc`.
3. Stream microphone PCM16 frames and receive assistant audio plus transcripts.
4. Persist utterances into Drift for the History view.

## Next Steps

- Flesh out the Drift schema and add DAO/unit tests.
- Finish the WebRTC integration (ICE servers, media tracks).
- Automate CI (`flutter analyze`, `flutter test`, `flutter build apk`).
- Localize UI strings (`intl` / `arb`) and replace placeholder latency metrics with live data.

Contributions should stay analyzer-clean, update relevant docs, and note any manual or automated testing performed.
