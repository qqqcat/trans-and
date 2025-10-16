# TransAnd Android 客户端实现摘要

本文档总结了依据《PRDdesign.md》构建的原生 Android 翻译 App 的主要设计与实现要点。

## 架构概览

- **UI 层**：使用 Jetpack Compose 构建，按照首页、设置、历史三大路由拆分，并提供复用组件（麦克风按钮、状态卡片等）。
- **领域层**：定义语言方向、模型配置、会话状态等核心模型，以及针对仓库的用例集合，确保业务逻辑可测试、可复用。
- **数据层**：
  - `RealtimeSessionManager` 负责与 WebRTC、音频采集及 API Relay 协同，维护实时会话状态。
  - `TranslationRepositoryImpl` 将偏好、实时流和历史记录整合为统一接口。
  - 使用 Room (`HistoryDatabase`) 存储历史记录，DataStore (`UserPreferencesDataSource`) 保存用户偏好。
- **系统能力**：封装音频采集/播放（`AudioSessionController`）、WebRTC (`WebRtcClient`)、网络监听和权限检测，满足低延时与隐私要求。
- **依赖注入**：通过 Hilt 配置 Retrofit、Room、PeerConnectionFactory、仓库等单例资源，方便未来扩展与测试。

## 测试策略

- 领域层单元测试覆盖关键用例（会话启动、麦克风切换、离线兜底开关等），确保业务逻辑正确。
- Gradle 项目配置为 `compileSdk` 34、`minSdk` 26，启用 Compose、Hilt、Room、Retrofit、WebRTC 等依赖，便于进行集成测试与打包。

## 下一步建议

1. 对接真实的 API Relay 与 OpenAI Realtime SDP 交换逻辑，实现音频 buffer 的真正上传与流式回调。
2. 在 `RealtimeSessionManager` 中接入 Whisper 本地模型推理，并在弱网时自动切换。
3. 增加性能指标收集与上传的自动化测试，以及 UI 仪表盘展示。
4. 扩展 E2E 测试，验证权限申请、弱网提示、前台服务保活等系统行为。
