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
    private val rtcClient: RealtimeRtcClient
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
        // TODO: 实现 HTTP POST 发送 offer.sdp 并 setRemoteDescription(answer)
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
