# WebRTC 协商缺失问题修复说明

## 背景
- 应用在启动实时翻译会话后很快提示 “Missing WebRTC negotiation payload”。
- 检查日志发现客户端仅等待后端返回 SDP offer 并直接发送数据通道消息，没有按照 OpenAI Realtime API 要求主动发起 SDP 协商。

## 修复概述
1. **会话启动**：调用 `RealtimeApi.startSession` 获取 `sessionId`、`token` 与新的 `clientSecret`。
2. **本地 SDP 生成**：`WebRtcClient.createOffer()` 会在创建的 `PeerConnection` 上生成本地 offer 并设置 LocalDescription。
3. **实时接口协商**：通过 `RealtimeApi.negotiateRealtimeRtc()` 携带 `clientSecret` 向 `.../realtimertc?model=<model>` 发送 offer (`Content-Type: application/sdp`)；接口返回的 `answer.sdp` 设置为远端描述。
4. **音频链路建立**：握手完成后再启动麦克风采集与下行音频播放，同时维持原有的指标上报、方向/模型切换能力。
5. **模型映射**：为 `TranslationModelProfile` 新增实时模型映射，当前默认 `Balanced`/`Offline` 对应 `gpt-realtime-mini`，`Accuracy` 对应 `gpt-4.1-realtime-preview`。

## 日志排查建议
- 在 `RealtimeApi.negotiateRealtimeRtc` 处增加临时日志可验证请求 URL、返回状态码及 SDP 长度。
- 关注 PeerConnection 的 `PeerConnectionState`，确认从 `NEW` -> `CONNECTED` 的状态转换。
- 如仍出现协商失败，可进一步输出 `clientSecret` 是否为空、HTTP 响应码与 body 内容。

## 相关代码入口
- `RealtimeSessionManager.start()`：串联会话启动、SDP 生成、协商与音频初始化。
- `WebRtcClient`：封装 offer/answer 创建、数据通道收发。
- `RealtimeApi`：新增 `negotiateRealtimeRtc` 方法处理 SDP POST 调用。

以上改动确保客户端按规范完成 WebRTC SDP 握手，从根源上消除缺失协商负载导致的错误提示。
