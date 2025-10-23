# TransAnd Android 实时翻译应用

TransAnd 是一款基于《PRDdesign.md》设计的原生 Android 实时语音翻译应用 Demo，专注于实现中英法语之间的实时翻译。项目采用 Jetpack Compose 构建 UI，配合 Hilt、Room、DataStore 与 WebRTC 实现模块化架构。

## 核心功能

### ✅ 已实现功能
- **完整的用户界面**：使用 Jetpack Compose 构建现代化 UI，包括首页、设置页、历史记录页
- **多语言支持**：支持10种语言（中文简体/繁体、英语、法语、西班牙语、德语、日语、韩语、葡萄牙语、俄语、阿拉伯语）
- **国际化 (i18n)**：完整的多语言界面支持，所有UI文案均通过 `stringResource()` 实现
- **实时翻译架构**：基于 WebRTC + OpenAI Realtime API 的实时翻译框架
- **数据持久化**：使用 Room 数据库存储翻译历史，DataStore 保存用户偏好
- **音频处理**：集成音频采集和播放功能，支持实时语音输入
- **网络通信**：基于 Retrofit + OkHttp 的 API 通信，支持实时事件流处理

### 🚧 开发中功能
- **实时翻译服务**：核心翻译功能需要配合后端 API Relay 服务使用

## 技术架构

| 层级 | 说明 |
| ---- | ---- |
| UI 层 | 使用 Jetpack Compose 与 Navigation 构建 `HomeRoute`、`SettingsRoute`、`HistoryRoute`，并封装复用组件。支持完整的国际化架构。 |
| 领域层 | 定义 `LanguageDirection`、`TranslationModelProfile` 等核心模型及用例集合，确保业务逻辑独立可测。 |
| 数据层 | `TranslationRepositoryImpl` 聚合实时会话管理、历史记录与用户偏好，提供统一的数据访问接口。 |
| 系统服务 | `AudioSessionController` 与 `WebRtcClient` 分别封装录音/播放和 WebRTC 管理，`NetworkMonitor` 提供网络状态检测。 |
| 依赖注入 | `AppModule` 通过 Hilt 提供 Retrofit、Room、PeerConnectionFactory 等依赖。 |

## 目录结构

```
.
├── app/                      # Android 模块
│   ├── build.gradle.kts      # 模块配置、依赖管理
│   └── src/main/java/com/example/translatorapp/
│       ├── presentation/     # Compose UI 与导航
│       │   ├── components/   # 复用组件
│       │   ├── home/         # 首页路由
│       │   ├── settings/     # 设置页路由
│       │   ├── history/      # 历史记录页路由
│       │   └── theme/        # 主题配置
│       ├── domain/           # 领域模型与用例
│       │   ├── model/        # 核心数据模型
│       │   ├── repository/   # 仓库接口定义
│       │   └── usecase/      # 业务用例
│       ├── data/             # 数据层实现
│       │   ├── repository/   # 仓库实现
│       │   ├── datasource/   # 数据源
│       │   └── model/        # 数据模型
│       ├── audio/            # 音频处理
│       ├── webrtc/           # WebRTC 通信
│       ├── network/          # 网络层
│       ├── util/             # 工具类
│       └── di/               # 依赖注入配置
├── third_party/              # 第三方库
│   └── whisper.cpp/          # 第三方语音处理库（已集成但未使用）
├── docs/                     # 项目文档
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
3. 运行 `app` 模块即可体验 Demo；首次启动请在 **Settings → 网络与服务** 中配置可用的 API Host（默认地址为 `https://api.realtime-proxy.example/`），并确认麦克风权限。

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
- 领域层单元测试：基础的单元测试覆盖核心业务逻辑
- 数据持久化：Room 数据库与 DataStore 确保用户偏好、翻译历史可靠存储
- 国际化支持：完整的多语言字符串资源覆盖
- 架构文档：`docs/implementation-notes.md` 汇总模块边界、扩展计划，可结合 PRD 进行验收

## 后续扩展建议
- 对接真实的 API Relay 服务，实现完整的 WebRTC 音频上传与流式文本下行
- 增加性能指标可视化与智能切换策略
- 扩展 UI/E2E 自动化测试，覆盖权限申请、弱网提示与前台服务保活场景
- 完善国际化支持，增加更多语言包、RTL布局适配以及本地化日期时间格式

