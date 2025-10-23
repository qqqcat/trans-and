package com.example.translatorapp.webrtc

import java.nio.ByteBuffer
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import org.webrtc.SdpObserver
import kotlin.coroutines.resume

@Singleton
class WebRtcClient @Inject constructor(
    private val peerConnectionFactory: PeerConnectionFactory
) {
    private val logTag = "WebRtcClient"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _remoteAudio = MutableSharedFlow<ByteArray>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val remoteAudio = _remoteAudio.asSharedFlow()

    private val _connectionState = MutableStateFlow(PeerConnection.PeerConnectionState.NEW)
    val connectionState = _connectionState.asStateFlow()

    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var upstreamChannel: DataChannel? = null
    private var downstreamChannel: DataChannel? = null

    private var onIceCandidateCallback: ((IceCandidate) -> Unit)? = null

    fun setOnIceCandidateListener(listener: (IceCandidate) -> Unit) {
    onIceCandidateCallback = listener
    Log.d(logTag, "Set ICE candidate listener: $listener")
    }

    fun createPeerConnection(
        iceServers: List<PeerConnection.IceServer>
    ) {
        if (peerConnection != null) return
        val configuration = PeerConnection.RTCConfiguration(iceServers)
        Log.d(logTag, "Creating PeerConnection with iceServers: $iceServers")
        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState) {
                Log.d(logTag, "Signaling state changed: $newState")
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                Log.d(logTag, "ICE connection state changed: $newState")
            }

            override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState) {}

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                Log.d(logTag, "PeerConnection state changed: $newState")
                _connectionState.value = newState
            }

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
                Log.d(logTag, "ICE gathering state changed: $newState")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(logTag, "Local ICE candidate generated: $candidate")
                onIceCandidateCallback?.invoke(candidate)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}

            override fun onAddStream(stream: MediaStream) {}

            override fun onRemoveStream(stream: MediaStream) {}

            override fun onDataChannel(channel: DataChannel) {
                Log.d(logTag, "DataChannel created: ${channel.label()}")
                if (channel.label() == AUDIO_DOWNSTREAM_LABEL) {
                    attachDownstreamChannel(channel)
                }
            }

            override fun onRenegotiationNeeded() {
                Log.d(logTag, "Renegotiation needed")
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}

            override fun onTrack(transceiver: RtpTransceiver?) {}
        }
        val peer = peerConnectionFactory.createPeerConnection(configuration, observer)
        peerConnection = peer
        audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        audioTrack = peerConnectionFactory.createAudioTrack(LOCAL_AUDIO_TRACK_ID, audioSource)
        audioTrack?.setEnabled(true)
        val mediaStream = peerConnectionFactory.createLocalMediaStream(LOCAL_MEDIA_STREAM_ID)
        mediaStream.addTrack(audioTrack)
        peer?.addStream(mediaStream)
        upstreamChannel = peer?.createDataChannel(AUDIO_UPSTREAM_LABEL, DataChannel.Init().apply {
            ordered = true
        })
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        Log.d(logTag, "Setting remote description: $sdp")
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d(logTag, "Remote description set successfully")
            }
            override fun onSetFailure(error: String?) {
                Log.e(logTag, "Set remote description failed: $error")
            }
        }, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
    Log.d(logTag, "Adding remote ICE candidate: $candidate")
    peerConnection?.addIceCandidate(candidate)
    }

    suspend fun createAnswer(): SessionDescription? = suspendCancellableCoroutine { continuation ->
        val peer = peerConnection ?: run {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        peer.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                Log.d(logTag, "SDP answer created: $sdp")
                if (sdp == null) {
                    continuation.resume(null)
                    return
                }
                peer.setLocalDescription(SimpleSdpObserver(), sdp)
                continuation.resume(sdp)
            }

            override fun onCreateFailure(error: String?) {
                Log.e(logTag, "SDP answer creation failed: $error")
                continuation.resume(null)
            }
        }, MediaConstraints())
    }

    fun sendAudioFrame(bytes: ByteArray): Boolean {
        val channel = upstreamChannel ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), true)
        return channel.send(buffer)
    }

    private fun attachDownstreamChannel(channel: DataChannel) {
        downstreamChannel?.dispose()
        downstreamChannel = channel.apply {
            registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) {}

                override fun onStateChange() {}

                override fun onMessage(buffer: DataChannel.Buffer) {
                    if (!buffer.binary) return
                    val data = ByteArray(buffer.data.remaining())
                    buffer.data.get(data)
                    handleRemoteAudioFrame(data)
                }
            })
        }
    }

    private fun handleRemoteAudioFrame(bytes: ByteArray) {
        coroutineScope.launch {
            _remoteAudio.emit(bytes)
        }
    }

    fun close() {
        upstreamChannel?.dispose()
        downstreamChannel?.dispose()
        upstreamChannel = null
        downstreamChannel = null
        peerConnection?.dispose()
        peerConnection = null
        audioTrack?.dispose()
        audioSource?.dispose()
        audioTrack = null
        audioSource = null
        _connectionState.value = PeerConnection.PeerConnectionState.CLOSED
    }

    companion object {
        private const val LOCAL_MEDIA_STREAM_ID = "ARDAMS"
        private const val LOCAL_AUDIO_TRACK_ID = "ARDAMSa0"
        private const val AUDIO_UPSTREAM_LABEL = "audio-upstream"
        private const val AUDIO_DOWNSTREAM_LABEL = "audio-downstream"
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
