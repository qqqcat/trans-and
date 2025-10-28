# TransAnd (Flutter Rebuild)

> ⚠️ 原 Kotlin/Jetpack Compose Android 工程已进入维护模式。本仓库主线转向 **Flutter** 跨平台实现，Android 原生模块仅保留存量代码，后续迭代以 `flutter_app/` 为准。

## 目录结构速览

```
flutter_app/                # 新的 Flutter 跨平台工程
  lib/
    app/                    # MaterialApp / 路由配置
    core/                   # 日志、主题、服务定位
    data/                   # 仓储 (Session/History/Settings)
    domain/                 # 实体与用例
    presentation/           # UI (Home / Settings / History)
    services/               # WebRTC、Realtime API、音频、设置存储
  pubspec.yaml
  analysis_options.yaml
docs/
  realtime-issues-2025-10-27.md
  implementation-notes.md
  project-evaluation.md
  ...
PRDdesign.md                # Flutter 重构 PRD（v2.0）
app/                        # 旧 Kotlin Android 工程（冻结，仅备查）
```

## 技术选型
- **Flutter 3.24+ / Dart 3.4+**（请安装最新 stable 版；参考 `flutter --version`）
- 状态管理：`hooks_riverpod`
- 网络：`dio` + `web_socket_channel`
- 实时能力：`flutter_webrtc`（音频采集与双向传输）
- 本地存储：`drift` + `sqlite3_flutter_libs`（历史记录）、`shared_preferences`（配置）
- 音频：`just_audio` + `audio_session`
- 路由：`go_router`

> 所有依赖版本已在 [`flutter_app/pubspec.yaml`](flutter_app/pubspec.yaml) 中声明，并可在官方包仓库查询到 2025 年 10 月稳定版本。

## 快速开始

1. 安装 Flutter stable，并确保 `flutter doctor` 通过。
2. 在仓库根目录执行：
   ```bash
   cd flutter_app
   flutter pub get
   flutter run   # Android / iOS 均可
   ```
3. 若需要保留原生工程，可分别在不同 IDE 打开 `app/` 与 `flutter_app/`。

### 常用命令
```bash
flutter analyze
flutter test
flutter run -d chrome           # Web 端快速联调（实验性）
```

## 当前状态

- ✅ Flutter 工程脚手架（路由、主题、依赖注入）
- ✅ 会话 / 历史 / 设置状态管理骨架
- ✅ WebRTC / Realtime API / 音频服务占位实现
- ✅ 文档更新（重构 PRD、事故复盘、迁移规划）
- ⏳ 实时语音链路、数据库落地、完整 UI 待后续迭代补全（见 `PRDdesign.md`）

## 迁移提示

- 原生 Kotlin 代码暂存 `app/`，必要 bugfix 可在独立分支处理。
- 请避免直接在 `main` 上执行破坏性命令，使用分支 + PR 并保证提交粒度。
- 每次迭代务必同步更新 `/docs` 资料，遵循“文档即代码”规范。

## 贡献流程
1. Fork & clone
2. 切分 feature 分支
3. 参考 `PRDdesign.md` 与 `docs/` 中的开发计划
4. 提交 PR，附带说明与文档更新

了解更多背景/路线图，请阅读：[PRDdesign.md](PRDdesign.md) 与 [docs/realtime-issues-2025-10-27.md](docs/realtime-issues-2025-10-27.md)。

祝开发顺利 🚀
