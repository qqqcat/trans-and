package com.example.translatorapp.webrtc

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WebRtcModule {
    @Provides
    @Singleton
    fun provideEglBase(): EglBase = EglBase.create()

    @Provides
    @Singleton
    fun providePeerConnectionFactory(context: Context, eglBase: EglBase): PeerConnectionFactory {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(null)
            .setVideoDecoderFactory(null)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }
}
