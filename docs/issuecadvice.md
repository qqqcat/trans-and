看你这段日志，其实把“为什么它老复读”这事儿已经基本说清了。我把关键症状→根因→对策梳成一套可直接改的检查清单：

你日志里的关键信号（和它们的含义）

input_audio_buffer.speech_started / speech_stopped / committed：服务端 VAD 侦测到你“开始/结束说话”，并把音频缓冲提交为一条用户消息。
Microsoft Learn

紧接着的 conversation.item.created 里 content: [{ "type":"input_audio", "transcript": null }]：此条用户消息没有文本转写（你会话配置里 transcriptionModel: null）。模型仍然能吃“音频 token”，但没有明确文本时，更容易回到模板化寒暄。建议打开转写。
Microsoft Learn

播放阶段你能看到：output_audio_buffer.started → ... → audio_transcript.delta... → audio_transcript.done，说明助手在连续说话输出。

每次它说话时你端里会打印：Muted local microphone during assistant playback（播放时静麦），但紧接着常出现：Restored local microphone state after assistant playback (delayed)，而后马上又来一条 input_audio_buffer.speech_started，再之后才看到 output_audio_buffer.cleared / conversation.item.truncated。这意味着你先解除了静麦→助手声音还在尾声→被麦克风重新拾音→VAD把“助手的尾音/回声”当成你在说话→再次触发一轮回答，于是开始“礼貌复读”的循环。

官方文档里也强调：如果用户要“打断”（barge-in），客户端应在侦测到用户说话时先发 response.cancel 或对上一条助手音频做 conversation.item.truncate，由服务器返 conversation.item.truncated 来与本地播放状态对齐，否则就会产生状态不同步和回声触发的连锁反应。
Microsoft Learn

另外你还收到一个明确错误：Unknown parameter: 'session.max_output_tokens'。这是因为实时会话的设置里没有这个字段名；需要通过 session.update 设置支持的会话参数，或在 response.create 时设置响应级别的 max_output_tokens。
Microsoft Learn

造成“重复一句话”的主要根因

回声/尾音触发的误唤醒（最常见）：你解静麦早于 output_audio_buffer.cleared，回放尾声被当成新一轮用户语音，VAD自动再次生成答案，内容又缺少上下文，自然是“能不能具体说说”的模板句。
Microsoft Learn

无文本转写（transcript: null）：未开启会话内 STT 时，模型只靠音频语义去判断，很容易保守地回问。开启 input_audio_transcription（如 whisper-1）能显著改善稳定性和针对性。
Microsoft Learn

VAD 过于敏感：server_vad 阈值较低时（0.5），环境噪声/回声容易被当成“speech_started”。官方允许调 threshold / prefix_padding_ms / silence_duration_ms 以抑制误触。
Microsoft Learn

浏览器/端侧没开 AEC/NS/AGC：回声消除、降噪、自动增益没开，移动端尤其容易拾到外放声音。应在 getUserMedia 打开这些约束。
Runebook

直接可改的修复清单（按优先级）

把“解静麦”的时机改到 output_audio_buffer.cleared 之后

现在你是在“播放结束的回调（延迟）”就解静麦，但从日志看常常先出现 speech_started，后才到 cleared/truncated。

正确顺序：收到 output_audio_buffer.stopped → 等 output_audio_buffer.cleared（或你主动发 conversation.item.truncate 并等 conversation.item.truncated）→ 再解静麦。
Microsoft Learn

实现标准“打断（barge-in）流程”

一旦捕获到用户真的在说话（而不是回声），立刻发送：

response.cancel（取消还在进行的那条 response），以及/或者

conversation.item.truncate（把上一条助手音频在服务端也截断），等待服务器回 conversation.item.truncated 后再开始采集/提交新一轮用户音频。
Microsoft Learn

打开会话内语音转写（强烈建议）

在你的首个 session.update 里加上：

{
  "type": "session.update",
  "session": {
    "input_audio_transcription": { "model": "whisper-1" }
  }
}


这会让服务端在提交完音频后发 conversation.item.audio_transcription.completed，从而给到明确的 transcript，回答就不再是模板寒暄了。
Microsoft Learn

调稳 VAD（先保守，后微调）

先把 threshold 提到 0.75，并把 silence_duration_ms 拉到 600~800ms，prefix_padding_ms 设 300~400ms，能显著减少回声/尾音触发。

配置示例：

{
  "type": "session.update",
  "session": {
    "turn_detection": {
      "type": "server_vad",
      "threshold": 0.75,
      "prefix_padding_ms": 300,
      "silence_duration_ms": 700,
      "create_response": true
    }
  }
}


如果想更“懂语义再停”，可以试 semantic_vad（更少打断，更不容易切成短句）。
Microsoft Learn

端侧一定要启用 AEC/NS/AGC（尤其是 Android）

采集媒体时加：echoCancellation: true, noiseSuppression: true, autoGainControl: true。这些是标准 WebRTC 约束，Flutter WebRTC 也支持等价参数。
Runebook

同时尽量用耳机/听筒而不是外放，进一步降低回声路径。

修正参数名：删掉 session.max_output_tokens

该字段名无效（你日志里有 “Unknown parameter: 'session.max_output_tokens'”）。

会话范围的设置请通过 session.update 里支持的字段（文档列出），回复级别的 token 上限放到 response.create.response.max_output_tokens。
Microsoft Learn

加一个“空内容防抖”

如果一轮 VAD 提交后既没有 audio_transcription.completed，且上一轮助手播放刚结束不久（比如 < 500ms），把这次输入视为可能的回声，直接 response.cancel + 丢弃，不触发应答。

你日志里的两处“指纹”对应到上面哪几条

Restored local microphone state ... 立刻跟着 input_audio_buffer.speech_started → 第 1、2、5 条（解静麦时机 + barge-in + AEC）。

conversation.item.truncated 出现在你说话之后，而不是你先截停 → 第 2 条（应由客户端先 response.cancel / conversation.item.truncate 再让用户说）。
Microsoft Learn

transcript: null、但 audio_tokens 很高 → 第 3、7 条（打开 STT 并做空内容防抖）。
Microsoft Learn

参考（官方文档）

Azure 官方《How to use the GPT Realtime API》——会话配置、VAD（server_vad / semantic_vad）、session.update、response.cancel、conversation.item.truncated 等完整流程说明。
Microsoft Learn

Azure 官方《Audio events reference》——各类音频事件（input_audio_buffer.*、会话/会话项事件、响应事件）定义与时序。

MDN：getUserMedia 里的 echoCancellation / noiseSuppression / autoGainControl 约束说明。
Runebook