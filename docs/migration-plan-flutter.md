# Flutter 迁移实施计划（摘录）

> 最后更新：2025-10-27  
> 配合《PRDdesign.md》使用，用于跟踪原生 → Flutter 的执行细节。

## 里程碑

| 阶段 | 目标 | 负责人 | 预计完成 |
| --- | --- | --- | --- |
| P0 脚手架 | Flutter 工程初始化、CI、代码规范 | Tech Lead | 2025-11-03 |
| P1 实时会话 MVP | WebRTC 接入、Realtime API、基础 UI | 音视频小组 | 2025-11-24 |
| P2 数据与配置 | Drift 落地历史记录、设置页重建、国际化 | Data 小组 | 2025-12-08 |
| P3 质量强化 | 单元/集成测试、性能基线、Crash 监控 | QA | 2025-12-22 |
| P4 iOS 适配 | iOS 权限策略、TestFlight 内测 | iOS 小组 | 2026-01-05 |

## 待办清单（2025-10-27）

- [ ] 在 `flutter_app` 目录启用 `melos` 或脚本化工具，统一 lint/test。
- [ ] 抽象 Realtime API 调用（REST + WebSocket），接入真实 token。
- [ ] 使用 `flutter_webrtc` 打通本地音频 track → Realtime session。
- [ ] 建立 Drift 数据库 schema，并提供历史迁移脚本（原生 Room → Drift）。
- [ ] 国际化：迁移 `strings.xml` 文案至 Flutter `intl`（arb 文件）。
- [ ] 构建 GitHub Actions（Flutter analyze + test + build apk/ipa）。
- [ ] 文档：更新贡献指南、代码规范、事件复盘。

## 已知风险

1. **WebRTC 插件兼容性**：需关注 iOS 后台录音策略、Android 音频焦点冲突。
2. **实时延迟指标**：上线前需建立端到端埋点，必要时降级到纯文本模式。
3. **数据迁移**：提供从旧版导出/导入工具，避免用户历史记录丢失。
4. **团队熟悉度**：安排 Flutter/Dart 培训，制定编码规范和 review 清单。

> 备注：本文件会随着每次迭代更新，务必在 PR 中同步维护。




