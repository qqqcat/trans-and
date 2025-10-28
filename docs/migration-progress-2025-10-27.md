# 2025-10-27 迁移进展速记

- [x] AppConfig 支持 `dart-define`、OS 环境变量与 `local.properties` 三路兜底，解决 Flutter Web 无法读取系统环境变量的问题。
- [x] Settings 存储复用标准化后的 endpoint，避免多余的首尾 `/` 导致 Azure 路径拼接失败。
- [x] pubspec 升级 `drift`/`drift_flutter` 并补充 `path` 依赖，为历史记录 Drift 持久层做准备。
- [ ] Realtime WebRTC 互通、Drift schema、CI pipeline 仍待实现。
