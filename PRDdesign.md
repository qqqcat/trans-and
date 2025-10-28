# TransAnd 跨平台重构 PRD（v2.0）

> 更新时间：2025-10-27  
> 作者：产品/技术联合小组  
> 适用阶段：原生 Android 版本暂停迭代，转向跨平台交付

---

## 1. 背景与目标

### 1.1 现状
- 现有工程为 Kotlin + Jetpack Compose 原生 Android 应用，依赖 WebRTC 与 Azure/OpenAI Realtime API 实现语音实时翻译。
- 团队连续四天的大量迭代在一次 `git reset --hard` 后丢失，暴露出开发流程与技术栈维护成本过高的问题。
- iOS 端尚未启动，跨平台需求逐渐显性；同时期望降低原生平台碎片化带来的测试与维护负担。

### 1.2 重构目标
1. **统一技术栈**：用一套代码覆盖 Android+iOS，并考虑桌面端预研。
2. **保留核心能力**：实时语音翻译、WebRTC 双向音频、历史记录、模型切换/配置。
3. **提升交付效率**：引入更易管理的状态管理和测试流程；降低开发者学习/维护成本。
4. **改善文档与流程**：建立跨平台 PRD 与工程结构的统一规范，避免再度丢失迭代成果。

---

## 2. 技术评估：Flutter vs. React Native

| 评估维度 | Flutter | React Native |
| --- | --- | --- |
| **渲染与性能** | 自研 Skia 引擎，绘制一致、性能稳定；对动画/语音可视化更友好。 | 依赖原生控件，存在平台差异 & Bridge 开销；复杂动画性能受限。 |
| **WebRTC 支持** | `flutter_webrtc` 官方维护，支持音视频、DataChannel、统一 API；与当前原生 WebRTC API 一致性高。 | `react-native-webrtc` 功能齐全但维护社区化，iOS/Android 差异较多，需要手动链接原生模块。 |
| **音频链路** | 可用 `flutter_webrtc` + `just_audio`/`audio_session` 构建录制、播放、音量控制。 | 需依赖多套原生模块，处理音频焦点/后台任务较繁琐。 |
| **状态管理** | 官方推荐 BLoC、Riverpod 等成熟方案，易于保持业务层整洁。 | Redux/MobX/Zustand 等任选，但需要更多脚手架与桥接。 |
| **开发效率** | Dart 语言一致性好，Hot Reload 稳定；Android Studio/VS Code 支持完善。 | 依赖 Node/npm 环境，调试时需处理 Metro bundler、Gradle、CocoaPods 多套工具链。 |
| **团队学习曲线** | 原团队已有 Compose 经验，思路类似（Widget 树），迁移成本低。 | 需跨入 JavaScript/TypeScript 生态，团队需要重新建立规范。 |
| **后续扩展** | FFI 支持完善，可封装原生 WebRTC/音频模块。 | 复杂原生模块需维护自定义 bridge，后续升级成本更高。 |

**结论**：采用 **Flutter** 作为跨平台技术栈，可以在保证实时音视频性能的同时，最大程度复用现有原生经验，降低迁移复杂度。

---

## 3. 现有能力梳理

| 模块 | 当前状态 | 迁移策略 |
| --- | --- | --- |
| 会话管理（RealtimeSessionManager） | 原生协程 + WebRTC | 抽象业务流程 → Flutter BLoC/service 层 |
| WebRTC/音频 | `org.webrtc` + `AudioRecord/AudioTrack` | 使用 `flutter_webrtc`，必要时封装自定义插件 |
| 历史记录（Room） | SQLite/Room | 改为 `drift` 或 `sqlite` + `moor`，保持本地存储能力 |
| 用户偏好（DataStore） | Proto DataStore | 改为 `shared_preferences` + 自定义 JSON/密钥存储 |
| UI（Compose） | 多语言/深色主题 | Flutter `Material 3` + `intl` |
| 网络（Retrofit/OkHttp） | REST + WebSocket | 使用 `dio` + `web_socket_channel`，抽象 API 客户端 |

---

## 4. 新版 Flutter 架构设计

### 4.1 分层结构
```
lib/
 ├─ app/                    # 入口、路由、主题
 ├─ presentation/           # Widget + 状态（BLoC / Riverpod）
 │   ├─ home/
 │   ├─ settings/
 │   └─ history/
 ├─ domain/                 # 实体、用例（UseCase）
 ├─ data/
 │   ├─ repositories/
 │   ├─ datasources/
 │   │   ├─ realtime_api/
 │   │   └─ local/
 ├─ core/                   # 错误处理、日志、配置
 ├─ services/
 │   ├─ webrtc_service.dart
 │   └─ audio_session_service.dart
 └─ bootstrap/              # 环境、依赖注入（GetIt/riverpod）
```

### 4.2 核心模块
1. **WebRTCService**
   - 基于 `flutter_webrtc`，封装 PeerConnection、DataChannel、音频 track 管理。
   - 统一事件流：连接状态、远端音频 buffer、ICE 候选。
2. **RealtimeApiClient**
   - `dio` + `web_socket_channel`；负责鉴权、会话创建、事件订阅。
   - 支持自动重连、心跳保活、日志分级。
3. **SessionBloc**
   - 负责对话生命周期（开始/结束/错误恢复）、模型切换、延迟统计。
   - 与 UI 解耦，便于单元测试。
4. **HistoryRepository**
   - 使用 `drift`（基于 sqlite）存储历史记录。
   - 提供搜索、标签、收藏等接口。
5. **SettingsController**
   - 管理模型配置、端点、语言偏好；使用 `shared_preferences` 或 `hive`。

### 4.3 关键交互流程
1. **启动会话**
   - UI 触发 `SessionBloc.startSession()`  
   - 校验端点/模型 → 调用 `RealtimeApiClient.createSession` → 返回 token  
   - `WebRTCService` 建立连接，推送状态更新 → UI 展示  
2. **实时翻译**
   - 本地音频帧通过 `WebRTCService.sendAudio` 上传  
   - 远端事件流 → `SessionBloc` → 更新 UI 及历史记录  
3. **结束会话**
   - 调用 `SessionBloc.stop()` → 资源释放 → 历史入库  
   - UI 显示结果摘要，提示评分/反馈

---

## 5. 迁移路线图

| 阶段 | 时长 | 目标 | 交付 |
| --- | --- | --- | --- |
| P0：基础搭建 | 2 周 | Flutter 工程模板、依赖注入、主题、路由 | 基础框架、样式指南 |
| P1：实时会话 MVP | 4 周 | 完成 WebRTC + Realtime API 接入、基础 UI | 语音翻译 MVP，可在 Android 调试 |
| P2：数据持久化 & 配置 | 3 周 | 历史记录、设置页、模型管理、语言国际化 | history 列表、设置完整、i18n |
| P3：质量强化 | 3 周 | 单元/集成测试、性能调优、错误处理、日志体系 | CI 流程、延迟监控面板 |
| P4：iOS 发布准备 | 2 周 | 适配 iOS 权限、音频策略，TestFlight 内测 | iOS 构建脚本、文档 |
| P5：Android 替换版本 | 1 周 | 替换旧原生应用，迁移历史数据 | 新版 APK、数据迁移脚本 |

---

## 6. 风险与对策

| 风险 | 影响 | 缓解措施 |
| --- | --- | --- |
| flutter_webrtc 插件兼容性 | 音频/视频异常 | 建立最小复现项目；必要时编译自维护分支 |
| 实时音频延迟 | 体验下降 | 引入端到端延迟埋点，动态调节缓冲区 |
| 数据迁移 | 历史记录丢失 | 在发布前提供原生 → Flutter 的导出/导入工具 |
| 团队熟悉度 | 开发效率降低 | 安排 Dart/Flutter 培训，制定代码规范 |
| 同步发布 | 双平台差异 | 使用 GitHub Actions/fastlane 实现自动构建 |

---

## 7. 文档与流程更新

1. `README.md`：更新为 Flutter 项目说明，保留原生版本历史链接。
2. `/docs/`：新增
   - `architecture-flutter.md`：架构与模块说明
   - `migration-plan.md`：原生 → Flutter 迁移清单
   - `webrtc-design-flutter.md`：音频管线与事件流
3. 引入 **文档即代码** 流程：每次功能迭代必须附带 PRD/流程更新，避免知识丢失。

---

## 8. 结论与立即行动

- **采纳 Flutter 为跨平台核心栈**，淘汰原生专用实现。
- 立即冻结现有 Kotlin 模块，只做必要的线上 bugfix。
- 本周内完成 Flutter 工程 scaffold、CI 初始化、基础 README 更新。
- 下周启动 P1（实时会话 MVP），确保语音链路跑通。
- 所有历史问题（延迟显示、历史记录、模型切换等）在 Flutter 版本中重新实现并验证；旧文档迁移到 `/docs/archive-native/`。

> 任何新的产品需求和技术决策，请以本 PRD 为准。如需变更，应同步更新文档并在团队例会上确认。
