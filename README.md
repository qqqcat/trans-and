# TransAnd Android 实时翻译应用

TransAnd 是一款基于《PRDdesign.md》设计的原生 Android 实时语音翻译应用 Demo，结合云端实时推理与本地兜底能力，帮助用户在中文与法语场景下进行低延迟交流。项目采用 Jetpack Compose 构建 UI，配合 Hilt、Room、DataStore 与 WebRTC 实现模块化、可扩展的体系结构。

## 核心功能
- **实时同声传译**：通过 `RealtimeSessionManager` 协调音频采集、API Relay 会话与 WebRTC 下行音频播放，展示实时字幕与译文。 
- **会话控制**：首页支持一键开关麦克风、结束会话，并实时显示延迟指标、错误提示等状态信息。 
- **多语言方向**：设置页提供「中文 ↔ 法语」方向切换以及模型档位（GPT-4.1、GPT-4o mini、Whisper v3 离线）选择。 
- **离线兜底与遥测开关**：用户可独立配置离线兜底、匿名性能上报开关，符合企业级合规要求。 
- **历史记录管理**：历史页读取 Room 持久化的会话记录，支持时间戳展示与一键清空。

## 技术架构
| 层级 | 说明 |
| ---- | ---- |
| UI 层 | 使用 Jetpack Compose 与 Navigation 构建 `HomeRoute`、`SettingsRoute`、`HistoryRoute`，并封装麦克风按钮、状态指示器等可复用组件。 |
| 领域层 | 定义 `LanguageDirection`、`TranslationModelProfile` 等核心模型及一组用例（如 `StartRealtimeSessionUseCase`、`ToggleMicrophoneUseCase`），确保业务逻辑独立可测。 |
| 数据层 | `TranslationRepositoryImpl` 聚合 `RealtimeSessionManager`、`UserPreferencesDataSource`、`HistoryDao`，统一暴露会话状态、实时字幕与历史列表。 |
| 系统服务 | `AudioSessionController` 与 `WebRtcClient` 分别封装录音/播放和 RTCPeerConnection 管理，`NetworkMonitor`、`PermissionManager` 提供系统状态辅助。 |
| 依赖注入 | `AppModule` 通过 Hilt 提供 Retrofit(`ApiRelayService`)、Room、PeerConnectionFactory、协程调度器等依赖。 |

## 目录结构
```text
.
├── app/                      # Android 模块
│   ├── build.gradle.kts      # 模块配置、依赖管理
│   └── src/main/java/com/example/translatorapp/
│       ├── presentation/     # Compose UI 与导航
│       ├── domain/           # 领域模型与用例
│       ├── data/             # 数据源与仓库实现
│       ├── audio/, webrtc/   # 系统能力封装
│       └── network/, di/     # 网络与依赖注入配置
├── docs/implementation-notes.md  # 架构补充说明
└── PRDdesign.md              # 产品需求文档
```

## 环境要求
- Android Studio Ladybug 或更新版本（建议使用最新稳定版）。
- Android Gradle Plugin 8.4+，JDK 17。
- Android SDK：`compileSdk` 34，`minSdk` 26。
- 可访问 WebRTC 与依赖仓库的网络环境（离线环境需提前同步 Maven 依赖）。

## 快速开始
1. 使用 Android Studio 打开仓库根目录，等待 Gradle 同步完成。
2. 连接具备麦克风的 Android 设备（或启动 API 26+ 模拟器）。
3. 运行 `app` 模块即可体验 Demo；真实部署前请在 `AppModule` 中替换 `https://api.realtime-proxy.example/` 为企业 API Relay 地址，并实现 WebRTC 音频通道上传逻辑。

### 命令行构建
```bash
./gradlew assembleDebug        # 编译 Debug 包
./gradlew assembleRelease      # 编译 Release 包
```

### 测试
```bash
./gradlew test --no-daemon --console=plain
```
> 注：在无外网的环境下首次执行可能因无法下载 Android Gradle Plugin 而失败，可预先配置镜像或离线仓库。

## 质量保障
- 领域层单元测试：`app/src/test/.../StartRealtimeSessionUseCaseTest.kt` 覆盖会话启动、麦克风切换、离线兜底配置等关键流程。
- 数据持久化：Room 数据库与 DataStore 确保用户偏好、翻译历史可靠存储。
- 架构文档：`docs/implementation-notes.md` 汇总模块边界、扩展计划，可结合 PRD 进行验收。

## 后续扩展建议
- 对接真实的 API Relay，实现 WebRTC 音频上传与流式文本下行。
- 增加性能指标可视化与离线模型智能切换策略。
- 扩展 UI/E2E 自动化测试，覆盖权限申请、弱网提示与前台服务保活场景。

