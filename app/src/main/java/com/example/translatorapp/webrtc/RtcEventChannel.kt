package com.example.translatorapp.webrtc

import org.webrtc.DataChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class RtcEventChannel(
    private val dataChannel: DataChannel,
    private val json: Json = Json
) {
    interface Listener {
        fun onEvent(event: JsonObject)
        fun onError(error: Exception)
    }

    private var listener: Listener? = null

    fun setListener(l: Listener) {
        listener = l
        dataChannel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer) {
                try {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    val text = String(bytes, Charsets.UTF_8)
                    val event = json.decodeFromString<JsonObject>(text)
                    listener?.onEvent(event)
                } catch (e: Exception) {
                    listener?.onError(e)
                }
            }
        })
    }

    fun sendEvent(event: JsonObject) {
        val text = json.encodeToString(event)
        val buffer = DataChannel.Buffer(
            java.nio.ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8)), false
        )
        dataChannel.send(buffer)
    }
}
