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
        android.util.Log.d("RealtimeSessionManager", "[startSession] 请求会话和 ICE 配置: model=$model")
        val sessionResp = sessionService.startSession(deployment = model, body = StartSessionRequest(model))
        sessionId = sessionResp.id
        clientSecret = sessionResp.client_secret.value
        android.util.Log.d("RealtimeSessionManager", "[startSession] 获取到 sessionId=$sessionId, clientSecret=${clientSecret?.take(6)}... ")

        // 2. 创建 PeerConnection
        val iceServers = sessionResp.webrtc?.ice_servers ?: emptyList()
        android.util.Log.d("RealtimeSessionManager", "[startSession] ICE servers: $iceServers")
        peerConnection = rtcClient.createPeerConnection(
            iceServers,
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
        android.util.Log.d("RealtimeSessionManager", "[startSession] DataChannel created: $eventChannel")

        // 4. 创建音频轨道
        rtcClient.addAudioTrack()
        android.util.Log.d("RealtimeSessionManager", "[startSession] Audio track added")

        // 5. SDP offer 协商
        val offer = rtcClient.createOffer()
        android.util.Log.d("RealtimeSessionManager", "[startSession] Created offer SDP: ${offer.description.take(80)}...")
        rtcClient.setLocalDescription(offer, object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
                android.util.Log.d("RealtimeSessionManager", "[setLocalDescription] onCreateSuccess: $p0")
            }
            override fun onSetSuccess() {
                android.util.Log.d("RealtimeSessionManager", "[setLocalDescription] onSetSuccess")
            }
            override fun onCreateFailure(p0: String?) {
                android.util.Log.e("RealtimeSessionManager", "[setLocalDescription] onCreateFailure: $p0")
            }
            override fun onSetFailure(p0: String?) {
                android.util.Log.e("RealtimeSessionManager", "[setLocalDescription] onSetFailure: $p0")
            }
        })

        // 6. HTTP POST offer.sdp 到 Azure WebRTC 端点，带 Bearer client_secret
        // 通过 RealtimeApi 实现 POST 协商，获取 answer 并设置 remoteDescription
        try {
            android.util.Log.d("RealtimeSessionManager", "[startSession] 发送 offer.sdp 到 Azure WebRTC 端点，开始协商...")
            val answerSdp = realtimeApi.sendOfferAndGetAnswer(sessionId!!, offer.description, clientSecret!!)
            if (answerSdp != null) {
                android.util.Log.d("RealtimeSessionManager", "[startSession] 收到 answer.sdp，设置 remoteDescription")
                val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                rtcClient.setRemoteDescription(answer, object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {
                        android.util.Log.d("RealtimeSessionManager", "[setRemoteDescription] onCreateSuccess: $p0")
                    }
                    override fun onSetSuccess() {
                        android.util.Log.d("RealtimeSessionManager", "[setRemoteDescription] onSetSuccess")
                    }
                    override fun onCreateFailure(p0: String?) {
                        android.util.Log.e("RealtimeSessionManager", "[setRemoteDescription] onCreateFailure: $p0")
                    }
                    override fun onSetFailure(p0: String?) {
                        android.util.Log.e("RealtimeSessionManager", "[setRemoteDescription] onSetFailure: $p0")
                    }
                })
            } else {
                android.util.Log.e("RealtimeSessionManager", "[startSession] 未收到 answer.sdp，协商失败")
                return@withContext false
            }
        } catch (e: Exception) {
            android.util.Log.e("RealtimeSessionManager", "[startSession] WebRTC offer/answer 协商失败: ${e.message}", e)
            return@withContext false
        }
        android.util.Log.d("RealtimeSessionManager", "[startSession] WebRTC 协商成功")
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
