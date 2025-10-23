# 离线语音识别功能状态

## 开发决定

**离线语音识别功能已取消开发**

基于项目优先级和资源分配的考虑，TransAnd 项目决定不再开发离线语音识别功能。项目将专注于云端实时翻译服务的完善和优化。

## 历史集成状态

项目早期曾集成 whisper.cpp 第三方库作为离线语音识别的基础：

- ✅ **第三方库集成**：whisper.cpp 已作为第三方库集成到 `third_party/whisper.cpp`
- ✅ **基础 JNI 支持**：包含了 `LibWhisper` 和 `WhisperCpuConfig` 类的基本封装
- ✅ **模型资源**：Tiny 模型已包含在应用 assets 中
- ✅ **UI 集成**：设置页面包含了离线模型下载和选择的 UI（目前未使用）

## 后续处理

- **代码保留**：现有的 whisper.cpp 集成代码将保留在项目中，以备将来可能的重新启用
- **UI 清理**：设置页面中的离线相关选项可能在后续版本中移除或隐藏
- **资源管理**：third_party 目录下的 whisper.cpp 库将保持现状，不再进行功能开发

## 替代方案

项目将专注于以下方向来提升翻译体验：

1. **云端服务优化**：完善实时翻译服务的稳定性和性能
2. **网络优化**：提升弱网络环境下的翻译体验
3. **缓存策略**：实现智能的翻译结果缓存
4. **离线翻译**：支持已翻译内容的离线查看（而非实时语音识别）

## 总结

离线语音识别功能的取消使项目能够更专注于核心的实时翻译服务，提升产品的稳定性和用户体验。 whisper.cpp 的集成代码保留为技术储备，为将来可能的扩展留有余地。

### 已完成部分
- **第三方库集成**：whisper.cpp 已作为第三方库集成到项目中
- **基础 JNI 支持**：包含了 `LibWhisper` 和 `WhisperCpuConfig` 类的基础封装
- **模型资源**：Tiny 模型已包含在应用 assets 中
- **UI 集成**：设置页面包含了离线模型下载和选择的 UI
- **错误处理**：实现了内存不足时的模型降级逻辑

### 待实现部分
- **完整的 JNI 集成**：whisper.cpp 的 JNI 封装和原生库调用
- **OfflineVoiceController**：离线语音处理控制器的完整实现
- **WhisperRuntime**：Whisper 推理运行时的实现
- **OfflineModelManager**：离线模型管理器的完整功能
- **LocalTtsSynthesizer**：本地 TTS 合成器的实现
- **音频处理管道**：从音频采集到文本输出的完整处理链

## 目标架构
- `AudioSessionController` 保持对 16 kHz 单声道 PCM 采集的控制权
- `OfflineVoiceController` 协调采集、缓冲、语音活动检测、whisper 推理、翻译和合成
- `WhisperRuntime` 加载 whisper.cpp 原生库并在后台调度器上执行转录或直接翻译
- `OfflineModelManager` 跟踪已安装的模型，处理校验和验证，并公开选择策略
- `LocalTtsSynthesizer` 使用协程友好的 API 封装 Android `TextToSpeech` 并实现音频文件缓存
- `TranslationRepository` 根据会话配置、连接性和已安装资源在实时（网络）和离线之间做出决策

### 数据流
1. UI 请求 `TranslationRepository.startRealtimeSession` 并指定 `TranslationModelProfile.Offline`
2. Repository 解析设置，检查 `OfflineModelManager`
3. 如果准备就绪，repository 调用 `OfflineVoiceController.start`，否则回退到远程或提示模型下载
4. `AudioSessionController` 将 PCM 帧流式传输到 `OfflineVoiceController`
5. `OfflineVoiceController` 使用 VAD 对音频进行分段，将工作项排队到 `WhisperRuntime`
6. `WhisperRuntime` 返回转录文本，根据模型能力可选地返回翻译文本
7. `LocalTranslator` 在 whisper 模型无法直接翻译时对转录文本进行后处理
8. `LocalTtsSynthesizer` 当目标语言需要语音输出时生成播放缓冲区
9. Repository 向流发出 `TranslationContent`，镜像实时路径的行为

## 模型打包策略
- **基准模型 (tiny.en)**：通过资源包或 Play Feature Delivery "按需"模块随应用发布，避免 APK 膨胀（>75 MB 压缩）。仅提供英语转录；翻译由软件处理（英语 -> 目标语言）
- **可选模型 (turbo)**：可下载包（~1.5 GB）。通过设置页面公开，带有进度 UI。支持多语言识别和翻译
- 将模型存储在 `Context.filesDir / whisper / models / {modelName}` 下，使用 JSON 清单描述校验和、版本、大小、语言能力
- 使用 `WorkManager` 进行后台下载，支持恢复，通过清单验证 SHA256
- 提供用户控制：Wi-Fi 自动下载、手动删除、存储使用情况显示

## JNI 和原生集成
- 将 whisper.cpp 作为 git 子模块包含或在 CI 期间下载；通过 CMake 编译为共享库 `libwhisper_release.so`
- 在 `app/build.gradle.kts` 中添加 NDK 工具链配置，使用 `externalNativeBuild { cmake }`
- 公开薄 JNI 封装：
  - `nativeInit(modelPath, threads, enableTranslation)` 返回句柄
  - `nativeProcess(handle, pcmBuffer, sampleRate, language, task)` 返回包含时间和文本的 JSON 字符串
  - `nativeRelease(handle)`
- 通过 `WhisperRuntime` 使用 `SharedFlow` 管理生命周期，报告进度、加载状态和错误
- 使用 `Dispatchers.Default.limitedParallelism(1)` 进行推理队列，避免 CPU 争用

## TTS 集成
- 使用 Android `TextToSpeech` 实现 `LocalTtsSynthesizer`
- 预加载匹配 `UserSettings.direction.targetLanguage` 的语音
- 将合成的音频作为 PCM 提供，供 `AudioSessionController.playAudio` 重用
- 在 `filesDir/tts-cache` 中缓存合成结果（`hash(transcript,targetLanguage)`），避免重新合成
- 当设备缺少 TTS 语音时，通过提示用户安装语音包来提供回退

## 实现阶段
1. **脚手架搭建（当前阶段）**
   - ✅ 定义 Kotlin 接口 (`OfflineVoiceController`, `WhisperRuntime`, `OfflineModelManager`, `LocalTtsSynthesizer`)
   - ✅ 连接 Hilt 模块、配置标志和 repository 决策逻辑
   - ✅ 提供模型清单架构和下载占位符实现
   - � 集成 whisper.cpp JNI 封装与现有 LibWhisper 类
   - � 实现 OfflineVoiceController 以协调采集、缓冲和推理

2. **原生启动**
   - � 集成 whisper.cpp 子模块，添加 CMake 工具链，实现 JNI 封装
   - � 使用单元测试和短 PCM 样本验证转录管道

3. **模型交付**
   - ✅ 实现资源包/下载管理器、UI 消息传递、错误处理
   - ✅ 添加后台工作者和存储配额管理

4. **TTS 集成**
   - � 使用 Android `TextToSpeech` 实现 `LocalTtsSynthesizer`
   - � 预加载匹配 `UserSettings.direction.targetLanguage` 的语音
   - � 将合成的音频作为 PCM 提供，供 `AudioSessionController.playAudio` 重用

5. **完善和 QA**
   - � 优化延迟（线程调优、VAD 窗口）
   - � 添加仪器测试、遥测钩子（离线安全）
   - � 更新文档、发布说明和回退启发式

## 风险和缓解措施
- **大型模型尺寸**：使用可选下载，显示存储使用情况，支持删除
- **中端设备 CPU 压力**：允许线程数配置，公开"平衡" vs "质量"设置
- **TTS 语音可用性**：检测已安装的语音，指导用户下载
- **UX 复杂性**：在设置 > 离线语音下整合控制，重用现有 UI 模式
