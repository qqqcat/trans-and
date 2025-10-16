package com.example.translatorapp.webrtc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRtcClient @Inject constructor(
    private val peerConnectionFactory: PeerConnectionFactory
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _remoteAudio = MutableSharedFlow<ByteArray>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val remoteAudio = _remoteAudio.asSharedFlow()

    private val _connectionState = MutableStateFlow(PeerConnection.PeerConnectionState.NEW)
    val connectionState = _connectionState.asStateFlow()

    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null

    fun createPeerConnection(iceServers: List<PeerConnection.IceServer>) {
        if (peerConnection != null) return
        val pc = peerConnectionFactory.createPeerConnection(
            PeerConnection.RTCConfiguration(iceServers),
            object : PeerConnection.Observer {
                override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}

                override fun onIceConnectionReceivingChange(receiving: Boolean) {}

                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}

                override fun onIceCandidate(candidate: org.webrtc.IceCandidate?) {}

                override fun onIceCandidatesRemoved(candidates: Array<out org.webrtc.IceCandidate>?) {}

                override fun onAddStream(stream: MediaStream?) {}

                override fun onRemoveStream(stream: MediaStream?) {}

                override fun onDataChannel(dataChannel: org.webrtc.DataChannel?) {}

                override fun onRenegotiationNeeded() {}

                override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    newState?.let { _connectionState.value = it }
                }
            }
        )
        peerConnection = pc
        audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        audioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)
        val mediaStream = peerConnectionFactory.createLocalMediaStream("ARDAMS")
        mediaStream.addTrack(audioTrack)
        pc?.addStream(mediaStream)
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), sdp)
    }

    fun setLocalDescription(sdp: SessionDescription) {
        peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
    }

    fun handleRemoteAudioFrame(bytes: ByteArray) {
        coroutineScope.launch {
            _remoteAudio.emit(bytes)
        }
    }

    fun close() {
        peerConnection?.dispose()
        peerConnection = null
        audioTrack?.dispose()
        audioSource?.dispose()
        audioTrack = null
        audioSource = null
    }
}

class SimpleSdpObserver : PeerConnection.SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
