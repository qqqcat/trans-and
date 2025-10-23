package com.example.translatorapp.data.realtime

import com.example.translatorapp.network.RealtimeSessionService
import com.example.translatorapp.network.StartSessionRequest
import com.example.translatorapp.webrtc.RealtimeRtcClient
import com.example.translatorapp.webrtc.RtcEventChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import javax.inject.Inject

class RealtimeSessionManager @Inject constructor(
    private val sessionService: RealtimeSessionService,
    private val rtcClient: RealtimeRtcClient,
    private val realtimeApi: com.example.translatorapp.network.RealtimeApi
) {
    var eventChannel: RtcEventChannel? = null
    var peerConnection: PeerConnection? = null
    var sessionId: String? = null
    var clientSecret: String? = null

    suspend fun startSession(model: String): Boolean = withContext(Dispatchers.IO) {
        // 1. 获取临时密钥和 ICE 配置
        val sessionResp = sessionService.startSession(deployment = model, body = StartSessionRequest(model))
        sessionId = sessionResp.id
        clientSecret = sessionResp.client_secret.value
        // 2. 创建 PeerConnection
        peerConnection = rtcClient.createPeerConnection(
            sessionResp.webrtc?.ice_servers ?: emptyList(),
            object : PeerConnection.Observer {
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidate(p0: org.webrtc.IceCandidate?) {}
                override fun onIceCandidatesRemoved(p0: Array<out org.webrtc.IceCandidate>?) {}
                override fun onAddStream(p0: org.webrtc.MediaStream?) {}
                override fun onRemoveStream(p0: org.webrtc.MediaStream?) {}
                override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(p0: org.webrtc.RtpReceiver?, p1: Array<out org.webrtc.MediaStream>?) {}
            }
        )
        // 3. 创建 DataChannel
        val dataChannel = rtcClient.createDataChannel()
        eventChannel = dataChannel?.let { RtcEventChannel(it) }
        // 4. 创建音频轨道
        rtcClient.addAudioTrack()

        // 5. SDP offer 协商
        val offer = rtcClient.createOffer()
        rtcClient.setLocalDescription(offer, object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        })

        // 6. HTTP POST offer.sdp 到 Azure WebRTC 端点，带 Bearer client_secret
        // 通过 RealtimeApi 实现 POST 协商，获取 answer 并设置 remoteDescription
        try {
            val answerSdp = realtimeApi.sendOfferAndGetAnswer(sessionId!!, offer.description, clientSecret!!)
            if (answerSdp != null) {
                val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                rtcClient.setRemoteDescription(answer, object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                })
            }
        } catch (e: Exception) {
            // 日志输出
            android.util.Log.e("RealtimeSessionManager", "WebRTC offer/answer 协商失败: ${e.message}", e)
            return@withContext false
        }
        return@withContext true
    }

    fun close() {
        eventChannel = null
        rtcClient.close()
        peerConnection = null
        sessionId = null
        clientSecret = null
    }
}
