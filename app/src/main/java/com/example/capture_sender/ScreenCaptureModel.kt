package com.example.capture_sender

import android.app.Application
import android.content.Context
import org.webrtc.*

const val CAPTURE_THREAD = "CaptureThread"
const val TRACK_ID_PREFIX = "ARDAMS"
const val VIDEO_TRACK_ID = TRACK_ID_PREFIX + "v0"
const val AUDIO_TRACK_ID = TRACK_ID_PREFIX + "a0"

class ScreenCaptureModel constructor(
    private val videoCapturer: VideoCapturer,
    private val params: MainViewModel.Parameters
) {

    private lateinit var factory: PeerConnectionFactory
    private lateinit var localMediaStream: MediaStream
    private lateinit var videoSource: VideoSource

    fun initialize(app: Application) {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions())
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()

        initScreenCaptureStream(app.applicationContext)
    }

    fun dispose() {
        factory.dispose()
        videoSource.dispose()
    }

    private fun initScreenCaptureStream(appContext: Context) {
        localMediaStream = factory.createLocalMediaStream(TRACK_ID_PREFIX)

        val videoConstraints = MediaConstraints()
        videoConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "maxHeight",
                params.height.toString()
            )
        )
        videoConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "maxWidth",
                params.width.toString()
            )
        )
        videoConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "maxFrameRate",
                params.fps.toString()
            )
        )
        videoConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "minFrameRate",
                params.fps.toString()
            )
        )

        // TODO
        val eglBase = EglBase.create()
        videoSource = factory.createVideoSource(videoCapturer.isScreencast);
        val surfaceTextureHelper = SurfaceTextureHelper.create(CAPTURE_THREAD, eglBase.eglBaseContext);
        videoCapturer.initialize(surfaceTextureHelper, appContext, videoSource.capturerObserver);

        videoCapturer.startCapture(params.width, params.height, params.fps)
        val localVideoTrack: VideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        localVideoTrack.setEnabled(true)
        localMediaStream.addTrack(factory.createVideoTrack(VIDEO_TRACK_ID, videoSource))
        val audioSource: AudioSource = factory.createAudioSource(MediaConstraints())
        localMediaStream.addTrack(factory.createAudioTrack(AUDIO_TRACK_ID, audioSource))
//        mListener.onStatusChanged("STREAMING")
    }
}
