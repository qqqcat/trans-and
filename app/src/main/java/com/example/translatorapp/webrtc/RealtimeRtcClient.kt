
package com.example.translatorapp.webrtc

import com.example.translatorapp.network.IceServerDto

import android.util.Log
import org.webrtc.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RealtimeRtcClient(
    private val peerConnectionFactory: PeerConnectionFactory,
    private val eglBase: EglBase
) {
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var localAudioTrack: AudioTrack? = null

    fun createPeerConnection(iceServers: List<IceServerDto>, observer: PeerConnection.Observer): PeerConnection {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers.map {
            PeerConnection.IceServer.builder(it.urls)
                .setUsername(it.username ?: "")
                .setPassword(it.credential ?: "")
                .createIceServer()
        })
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
        return peerConnection!!
    }

    fun createDataChannel(label: String = "events"): DataChannel? {
        val init = DataChannel.Init()
        dataChannel = peerConnection?.createDataChannel(label, init)
        return dataChannel
    }

    fun addAudioTrack(): AudioTrack {
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio0", audioSource)
        localAudioTrack?.setEnabled(true)
        peerConnection?.addTrack(localAudioTrack)
        return localAudioTrack!!
    }

    suspend fun createOffer(): SessionDescription = suspendCancellableCoroutine { cont ->
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) { cont.resume(desc) }
            override fun onSetSuccess() {}
            override fun onCreateFailure(msg: String?) { cont.resumeWithException(Exception(msg)) }
            override fun onSetFailure(msg: String?) {}
        }, constraints)
    }

    fun setLocalDescription(desc: SessionDescription, observer: SdpObserver) {
        peerConnection?.setLocalDescription(observer, desc)
    }

    fun setRemoteDescription(desc: SessionDescription, observer: SdpObserver) {
        peerConnection?.setRemoteDescription(observer, desc)
    }

    fun close() {
        dataChannel?.close()
        peerConnection?.close()
        localAudioTrack?.dispose()
        dataChannel = null
        peerConnection = null
        localAudioTrack = null
    }
}
