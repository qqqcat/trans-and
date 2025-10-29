# TransAnd Copilot Instructions

## Architecture Overview
TransAnd is a real-time voice translation Android app using Clean Architecture with domain/data/presentation layers. It provides low-latency (<600ms) translation between Chinese/French using WebRTC + OpenAI Realtime API.

**Layer Structure:**
- **Domain**: Pure business logic with `TranslationRepository` interface, use cases, and immutable models
- **Data**: Repository implementations, Room DAOs, DataStore, WebRTC clients, and API services
- **Presentation**: Jetpack Compose UI with ViewModels managing reactive state flows

## Key Patterns & Conventions

### Language Direction Handling


### Reactive State Management
All UI state flows from domain layer through repository interfaces. ViewModels observe flows and transform to UI state:
```kotlin
class HomeViewModel @Inject constructor(
    observeSessionState: ObserveSessionStateUseCase
) : ViewModel() {
    init {
        viewModelScope.launch {
            observeSessionState().collect { session ->
                // Transform domain state to UI state
            }
        }
    }
}
```

### Dependency Injection
Use constructor injection with Hilt. All use cases and repositories are injected:
```kotlin
class StartRealtimeSessionUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(settings: UserSettings) = repository.startRealtimeSession(settings)
}
```

### Testing Pattern
Domain layer tests use fake repositories with `MutableStateFlow` for verification:
```kotlin
private class FakeRepository : TranslationRepository {
    val startCalls = MutableStateFlow<UserSettings?>(null)
    // ... implement interface with test observables
}
```

## Critical Workflows

### Building & Testing
```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run with custom metrics collection
./gradlew collectTestMetrics

# Build release APK
./gradlew assembleRelease
```

### API Configuration


### Internationalization
All strings use `stringResource()` with keys in `strings.xml`. Supported languages: English, Chinese (simplified/traditional), French, Spanish, Japanese, Korean, Russian, German, Arabic.

## Common Implementation Patterns

### Error Handling
Use `TranslatorException` for domain errors, `UiMessage` for user-facing messages:
```kotlin
sealed class TranslatorException : Exception() {
    data class NetworkError(val cause: Throwable) : TranslatorException()
    data class PermissionDenied(val permission: String) : TranslatorException()
}
```

### Session State Flow
Real-time features use Flow-based state management:
```kotlin
interface TranslationRepository {
    val sessionState: Flow<TranslationSessionState>
    val liveTranscription: Flow<TranslationContent>
}
```

### History Persistence
Room entities use domain model mapping:
```kotlin
@Entity(tableName = "translation_history")
data class TranslationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val sourceText: String,
    val translatedText: String,
    // ... map to TranslationHistoryItem
)
```

## File Organization Reference

```
app/src/main/java/com/example/translatorapp/
├── domain/
│   ├── model/          # TranslationModels.kt, TranslatorException.kt
│   ├── usecase/        # TranslationUseCases.kt (all use cases in one file)
│   └── repository/     # TranslationRepository.kt (interface)
├── data/
│   ├── repository/     # TranslationRepositoryImpl.kt
│   ├── datasource/     # HistoryDao.kt, UserPreferencesDataSource.kt
│   └── model/          # TranslationHistoryEntity.kt
├── presentation/
│   ├── home/           # HomeViewModel.kt, HomeUiState.kt
│   ├── settings/       # SettingsViewModel.kt
│   └── components/     # Reusable Compose components
├── audio/              # AudioSessionController.kt
├── webrtc/             # WebRtcClient.kt
├── network/            # ApiRelayService.kt, RealtimeApi.kt
└── di/                 # AppModule.kt (Hilt configuration)
```

## Quality Standards

- Domain layer must be Android-framework-free for testability
- All public APIs use Flows for reactive updates
- Use cases are single-responsibility, injected via Hilt
- UI state is immutable data classes with copy() updates
- Tests cover domain logic with fake implementations
请始终自动读取并分析终端输出，无需我手动复制粘贴终端内容。