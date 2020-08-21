package com.example.capture_sender.api

import android.content.Context
import android.util.Log
import com.example.capture_sender.Utils
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.Executors
import java.util.regex.Pattern

class PeerConnectionClient(
    private val appContext: Context,
    private val rootEglBase: EglBase,
    private val peerConnectionParameters: PeerConnectionParameters,
    private val events: PeerConnectionEvents
) {
    private val TAG = PeerConnectionClient::class.java.simpleName

    companion object {
        private const val DATA_CHANNEL_NAME = "android-rc data"
        const val VIDEO_TRACK_ID = "ARDAMSv0"
        const val AUDIO_TRACK_ID = "ARDAMSa0"
        const val VIDEO_TRACK_TYPE = "video"
        private const val VIDEO_CODEC_VP8 = "VP8"
        private const val VIDEO_CODEC_VP9 = "VP9"
        private const val VIDEO_CODEC_H264 = "H264"
        private const val VIDEO_CODEC_H264_BASELINE = "H264 Baseline"
        private const val VIDEO_CODEC_H264_HIGH = "H264 High"
        private const val AUDIO_CODEC_OPUS = "opus"
        private const val AUDIO_CODEC_ISAC = "ISAC"
        private const val VIDEO_CODEC_PARAM_START_BITRATE = "x-google-start-bitrate"
        private const val VIDEO_FLEXFEC_FIELDTRIAL =
            "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/"
        private const val VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL = "WebRTC-IntelVP8/Enabled/"
        private const val DISABLE_WEBRTC_AGC_FIELDTRIAL =
            "WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/"
        private const val AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate"
        private const val AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation"
        private const val AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl"
        private const val AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter"
        private const val AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression"
        private const val DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement"
        private const val HD_VIDEO_WIDTH = 1280
        private const val HD_VIDEO_HEIGHT = 720
        private const val BPS_IN_KBPS = 1000

        // Executor thread is started once in private ctor and is used for all
        // peer connection API calls to ensure new peer connection factory is
        // created on the same thread as previously destroyed factory.
        private val executor = Executors.newSingleThreadExecutor()
    }

    private val statsTimer = Timer()
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var preferIsac = false
    private var videoCapturerStopped = false
    private var isError = false
    private var localRender: VideoSink? = null
    private var remoteSinks: List<VideoSink>? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private var videoFps = 0
    private var audioConstraints: MediaConstraints? = null
    private var sdpMediaConstraints: MediaConstraints? = null

    private var queuedRemoteCandidates: MutableList<IceCandidate>? = null
    private var isInitiator = false
    private var localSdp: SessionDescription? = null
    private var videoCapturer: VideoCapturer? = null

    private var renderVideo = true
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var localVideoSender: RtpSender? = null

    private var enableAudio = true
    private var localAudioTrack: AudioTrack? = null
    private var dataChannel: DataChannel? = null
    private val dataChannelEnabled = true

    class DataChannelParameters(
        val ordered: Boolean, val maxRetransmitTimeMs: Int, val maxRetransmits: Int,
        val protocol: String, val negotiated: Boolean, val id: Int
    )

    class PeerConnectionParameters(
        val videoCallEnabled: Boolean,
//        val loopback: Boolean,
//        val tracing: Boolean,
        val videoWidth: Int,
        val videoHeight: Int,
        val videoFps: Int,
        val videoMaxBitrate: Int,
        val videoCodec: String,
        val videoCodecHwAcceleration: Boolean,
        val videoFlexfecEnabled: Boolean,
        val audioStartBitrate: Int,
        val audioCodec: String?,
        val noAudioProcessing: Boolean,
//        val aecDump: Boolean,
//        val saveInputAudioToFile: Boolean,
//        val useOpenSLES: Boolean,
        val disableBuiltInAEC: Boolean,
        val disableBuiltInAGC: Boolean,
        val disableBuiltInNS: Boolean,
        val disableWebRtcAGCAndHPF: Boolean,
//        val enableRtcEventLog: Boolean,
        val dataChannelParameters: DataChannelParameters?
    )

    interface PeerConnectionEvents {
        /**
         * Callback fired once local SDP is created and set.
         */
        fun onLocalDescription(sdp: SessionDescription)

        /**
         * Callback fired once local Ice candidate is generated.
         */
        fun onIceCandidate(candidate: IceCandidate)

        /**
         * Callback fired once local ICE candidates are removed.
         */
        fun onIceCandidatesRemoved(candidates: Array<IceCandidate>)

        /**
         * Callback fired once connection is established (IceConnectionState is
         * CONNECTED).
         */
        fun onIceConnected()

        /**
         * Callback fired once connection is disconnected (IceConnectionState is
         * DISCONNECTED).
         */
        fun onIceDisconnected()

        /**
         * Callback fired once DTLS connection is established (PeerConnectionState
         * is CONNECTED).
         */
        fun onConnected()

        /**
         * Callback fired once DTLS connection is disconnected (PeerConnectionState
         * is DISCONNECTED).
         */
        fun onDisconnected()

        /**
         * Callback fired once peer connection is closed.
         */
        fun onPeerConnectionClosed()

        /**
         * Callback fired once peer connection statistics is ready.
         */
        fun onPeerConnectionStatsReady(reports: Array<StatsReport>)

        /**
         * Callback fired once peer connection error happened.
         */
        fun onPeerConnectionError(description: String)
    }

    /**
     * This function should only be called once.
     */
    fun createPeerConnectionFactory(options: PeerConnectionFactory.Options?) {
        check(factory == null) { "PeerConnectionFactory has already been constructed" }
        executor.execute {
            createPeerConnectionFactoryInternal(options)
        }
    }

    fun createPeerConnection(
        localRender: VideoSink?,
        remoteSink: VideoSink?,
        videoCapturer: VideoCapturer?
    ) {
        if (peerConnectionParameters.videoCallEnabled && videoCapturer == null) {
            Log.w(TAG, "Video call enabled but no video capturer provided.")
        }
        createPeerConnection(
            localRender,
            if (remoteSink == null) emptyList() else listOf(remoteSink),
            videoCapturer
        )
    }

    fun createPeerConnection(
        localRender: VideoSink?,
        remoteSinks: List<VideoSink>,
        videoCapturer: VideoCapturer?
    ) {
        this.localRender = localRender
        this.remoteSinks = remoteSinks
        this.videoCapturer = videoCapturer
        executor.execute {
            try {
                createMediaConstraintsInternal()
                createPeerConnectionInternal()
            } catch (e: Exception) {
                reportError("Failed to create peer connection: " + e.message)
                throw e
            }
        }
    }

    fun close() {
        executor.execute { closeInternal() }
    }

    private val isVideoCallEnabled: Boolean
        get() = peerConnectionParameters.videoCallEnabled && videoCapturer != null

    private fun createPeerConnectionFactoryInternal(options: PeerConnectionFactory.Options?) {
        isError = false

        // Check if ISAC is used by default.
        preferIsac =
            (peerConnectionParameters.audioCodec != null && peerConnectionParameters.audioCodec == AUDIO_CODEC_ISAC)

        val adm = createJavaAudioDevice()
        // Create peer connection factory.
        if (options != null) {
            Log.d(TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask)
        }
        val enableH264HighProfile = VIDEO_CODEC_H264_HIGH == peerConnectionParameters.videoCodec
        val encoderFactory: VideoEncoderFactory
        val decoderFactory: VideoDecoderFactory
        if (peerConnectionParameters.videoCodecHwAcceleration) {
            encoderFactory = DefaultVideoEncoderFactory(
                rootEglBase.eglBaseContext,
                true /* enableIntelVp8Encoder */,
                enableH264HighProfile
            )
            decoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)
        } else {
            encoderFactory = SoftwareVideoEncoderFactory()
            decoderFactory = SoftwareVideoDecoderFactory()
        }
        factory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
        Log.d(TAG, "Peer connection factory created.")
        adm.release()
    }

    fun createJavaAudioDevice(): AudioDeviceModule {
        // Set audio record error callbacks.
        val audioRecordErrorCallback: JavaAudioDeviceModule.AudioRecordErrorCallback =
            object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(errorMessage: String) {
                    Log.e(TAG, "onWebRtcAudioRecordInitError: $errorMessage")
                    reportError(errorMessage)
                }

                override fun onWebRtcAudioRecordStartError(
                    errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode,
                    errorMessage: String
                ) {
                    Log.e(TAG, "onWebRtcAudioRecordStartError: $errorCode. $errorMessage")
                    reportError(errorMessage)
                }

                override fun onWebRtcAudioRecordError(errorMessage: String) {
                    Log.e(TAG, "onWebRtcAudioRecordError: $errorMessage")
                    reportError(errorMessage)
                }
            }
        val audioTrackErrorCallback: JavaAudioDeviceModule.AudioTrackErrorCallback =
            object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                override fun onWebRtcAudioTrackInitError(errorMessage: String) {
                    Log.e(TAG, "onWebRtcAudioTrackInitError: $errorMessage")
                    reportError(errorMessage)
                }

                override fun onWebRtcAudioTrackStartError(
                    errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode,
                    errorMessage: String
                ) {
                    Log.e(TAG, "onWebRtcAudioTrackStartError: $errorCode. $errorMessage")
                    reportError(errorMessage)
                }

                override fun onWebRtcAudioTrackError(errorMessage: String) {
                    Log.e(TAG, "onWebRtcAudioTrackError: $errorMessage")
                    reportError(errorMessage)
                }
            }

        // Set audio record state callbacks.
        val audioRecordStateCallback: JavaAudioDeviceModule.AudioRecordStateCallback =
            object : JavaAudioDeviceModule.AudioRecordStateCallback {
                override fun onWebRtcAudioRecordStart() {
                    Log.i(TAG, "Audio recording starts")
                }

                override fun onWebRtcAudioRecordStop() {
                    Log.i(TAG, "Audio recording stops")
                }
            }

        // Set audio track state callbacks.
        val audioTrackStateCallback: JavaAudioDeviceModule.AudioTrackStateCallback =
            object : JavaAudioDeviceModule.AudioTrackStateCallback {
                override fun onWebRtcAudioTrackStart() {
                    Log.i(TAG, "Audio playout starts")
                }

                override fun onWebRtcAudioTrackStop() {
                    Log.i(TAG, "Audio playout stops")
                }
            }
        return JavaAudioDeviceModule.builder(appContext)
//            .setSamplesReadyCallback(saveRecordedAudioToFile)
            .setUseHardwareAcousticEchoCanceler(!peerConnectionParameters.disableBuiltInAEC)
            .setUseHardwareNoiseSuppressor(!peerConnectionParameters.disableBuiltInNS)
            .setAudioRecordErrorCallback(audioRecordErrorCallback)
            .setAudioTrackErrorCallback(audioTrackErrorCallback)
            .setAudioRecordStateCallback(audioRecordStateCallback)
            .setAudioTrackStateCallback(audioTrackStateCallback)
            .createAudioDeviceModule()
    }

    private fun createMediaConstraintsInternal() {
        // Create video constraints if video call is enabled.
        if (isVideoCallEnabled) {
            videoWidth = peerConnectionParameters.videoWidth
            videoHeight = peerConnectionParameters.videoHeight
            videoFps = peerConnectionParameters.videoFps

            // If video resolution is not specified, default to HD.
            if (videoWidth == 0 || videoHeight == 0) {
                videoWidth = HD_VIDEO_WIDTH
                videoHeight = HD_VIDEO_HEIGHT
            }

            // If fps is not specified, default to 30.
            if (videoFps == 0) {
                videoFps = 30
            }
            Logging.d(TAG, "Capturing format: " + videoWidth + "x" + videoHeight + "@" + videoFps)
        }

        // Create audio constraints.
        audioConstraints = MediaConstraints()
        // added for audio performance measurements
        if (peerConnectionParameters.noAudioProcessing) {
            Log.d(TAG, "Disabling audio processing")
            audioConstraints!!.mandatory.add(
                MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "false")
            )
            audioConstraints!!.mandatory.add(
                MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false")
            )
            audioConstraints!!.mandatory.add(
                MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false")
            )
            audioConstraints!!.mandatory.add(
                MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "false")
            )
        }
        // Create SDP constraints.
        sdpMediaConstraints = MediaConstraints()
        sdpMediaConstraints!!.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        )
        sdpMediaConstraints!!.mandatory.add(
            MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo",
                java.lang.Boolean.toString(isVideoCallEnabled)
            )
        )
        // TODO: this key should remove on production.
        sdpMediaConstraints!!.mandatory.add(
            MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true")
        )
    }

    private fun createPeerConnectionInternal() {
        if (factory == null || isError) {
            Log.e(TAG, "Peerconnection factory is not created")
            return
        }
        Log.d(TAG, "Create peer connection.")
        queuedRemoteCandidates = ArrayList()
        val iceServers = emptyList<PeerConnection.IceServer>()
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy =
            PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA
        // Enable DTLS for normal calls and disable for loopback calls.
//        rtcConfig.enableDtlsSrtp = false // TODO
        rtcConfig.enableDtlsSrtp = true // TODO
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        peerConnection = factory!!.createPeerConnection(rtcConfig, pcObserver)
        if (dataChannelEnabled) {
            val init = DataChannel.Init()
            init.ordered = peerConnectionParameters.dataChannelParameters!!.ordered
            init.negotiated = peerConnectionParameters.dataChannelParameters!!.negotiated
            init.maxRetransmits = peerConnectionParameters.dataChannelParameters!!.maxRetransmits
            init.maxRetransmitTimeMs =
                peerConnectionParameters.dataChannelParameters!!.maxRetransmitTimeMs
            init.id = peerConnectionParameters.dataChannelParameters!!.id
            init.protocol = peerConnectionParameters.dataChannelParameters!!.protocol
            dataChannel = peerConnection!!.createDataChannel(DATA_CHANNEL_NAME, init)
        }
        isInitiator = false

        // Set INFO libjingle logging.
        // NOTE: this _must_ happen while |factory| is alive!
        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO)
        val mediaStreamLabels = listOf("ARDAMS")
        if (isVideoCallEnabled) {
            peerConnection!!.addTrack(createVideoTrack(videoCapturer), mediaStreamLabels)
            // We can add the renderers right away because we don't need to wait for an
            // answer to get the remote track.
            remoteVideoTrack = getRemoteVideoTrack()
            remoteVideoTrack!!.setEnabled(renderVideo)
            for (remoteSink in remoteSinks!!) {
                remoteVideoTrack!!.addSink(remoteSink)
            }
        }
        peerConnection!!.addTrack(createAudioTrack(), mediaStreamLabels)
        if (isVideoCallEnabled) {
            findVideoSender()
        }
        Log.d(TAG, "Peer connection created.")
    }

    private fun closeInternal() {
        Log.d(TAG, "Closing peer connection.")
        statsTimer.cancel()
        if (dataChannel != null) {
            dataChannel!!.dispose()
            dataChannel = null
        }
        if (peerConnection != null) {
            peerConnection!!.dispose()
            peerConnection = null
        }
        Log.d(TAG, "Closing audio source.")
        if (audioSource != null) {
            audioSource!!.dispose()
            audioSource = null
        }
        Log.d(TAG, "Stopping capture.")
        if (videoCapturer != null) {
            try {
                videoCapturer!!.stopCapture()
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
            videoCapturerStopped = true
            videoCapturer!!.dispose()
            videoCapturer = null
        }
        Log.d(TAG, "Closing video source.")
        if (videoSource != null) {
            videoSource!!.dispose()
            videoSource = null
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper!!.dispose()
            surfaceTextureHelper = null
        }
        localRender = null
        remoteSinks = null
        Log.d(TAG, "Closing peer connection factory.")
        if (factory != null) {
            factory!!.dispose()
            factory = null
        }
        rootEglBase.release()
        Log.d(TAG, "Closing peer connection done.")
        events.onPeerConnectionClosed()
        PeerConnectionFactory.stopInternalTracingCapture()
        PeerConnectionFactory.shutdownInternalTracer()
    }

    val isHDVideo: Boolean
        get() = isVideoCallEnabled && videoWidth * videoHeight >= 1280 * 720

    // TODO(sakal): getStats is deprecated.
    private val stats: Unit
        get() {
            if (peerConnection == null || isError) {
                return
            }
            val success = peerConnection!!.getStats(
                { reports -> events.onPeerConnectionStatsReady(reports) },
                null
            )
            if (!success) {
                Log.e(TAG, "getStats() returns false!")
            }
        }

    fun enableStatsEvents(enable: Boolean, periodMs: Int) {
        if (enable) {
            try {
                statsTimer.schedule(object : TimerTask() {
                    override fun run() {
                        executor.execute { stats }
                    }
                }, 0, periodMs.toLong())
            } catch (e: Exception) {
                Log.e(TAG, "Can not schedule statistics timer", e)
            }
        } else {
            statsTimer.cancel()
        }
    }

    fun setAudioEnabled(enable: Boolean) {
        executor.execute {
            enableAudio = enable
            if (localAudioTrack != null) {
                localAudioTrack!!.setEnabled(enableAudio)
            }
        }
    }

    fun setVideoEnabled(enable: Boolean) {
        executor.execute {
            renderVideo = enable
            if (localVideoTrack != null) {
                localVideoTrack!!.setEnabled(renderVideo)
            }
            if (remoteVideoTrack != null) {
                remoteVideoTrack!!.setEnabled(renderVideo)
            }
        }
    }

    fun createOffer() {
        executor.execute {
            if (peerConnection != null && !isError) {
                Log.d(TAG, "PC Create OFFER")
                isInitiator = true
                peerConnection!!.createOffer(sdpObserver, sdpMediaConstraints)
            }
        }
    }

    fun createAnswer() {
        executor.execute {
            if (peerConnection != null && !isError) {
                Log.d(TAG, "PC create ANSWER")
                isInitiator = false
                peerConnection!!.createAnswer(sdpObserver, sdpMediaConstraints)
            }
        }
    }

    fun addRemoteIceCandidate(candidate: IceCandidate) {
        executor.execute {
            if (peerConnection != null && !isError) {
                if (queuedRemoteCandidates != null) {
                    queuedRemoteCandidates!!.add(candidate)
                } else {
                    peerConnection!!.addIceCandidate(candidate)
                }
            }
        }
    }

    fun removeRemoteIceCandidates(candidates: Array<IceCandidate?>?) {
        executor.execute {
            if (peerConnection == null || isError) {
                return@execute
            }
            // Drain the queued remote candidates if there is any so that
            // they are processed in the proper order.
            drainCandidates()
            peerConnection!!.removeIceCandidates(candidates)
        }
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        executor.execute {
            if (peerConnection == null || isError) {
                return@execute
            }
            var sdpDescription = sdp.description
            if (preferIsac) {
                sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true)
            }
            if (isVideoCallEnabled) {
                sdpDescription = preferCodec(
                    sdpDescription,
                    getSdpVideoCodecName(peerConnectionParameters),
                    false
                )
            }
            if (peerConnectionParameters.audioStartBitrate > 0) {
                sdpDescription = setStartBitrate(
                    AUDIO_CODEC_OPUS,
                    false,
                    sdpDescription,
                    peerConnectionParameters.audioStartBitrate
                )
            }
            Log.d(TAG, "Set remote SDP: $sdpDescription")
            val sdpRemote = Utils.createSDP(sdp.type, sdpDescription)
            peerConnection!!.setRemoteDescription(sdpObserver, sdpRemote)
        }
    }

    fun stopVideoSource() {
        executor.execute {
            if (videoCapturer != null && !videoCapturerStopped) {
                Log.d(TAG, "Stop video source.")
                try {
                    videoCapturer!!.stopCapture()
                } catch (e: InterruptedException) {
                }
                videoCapturerStopped = true
            }
        }
    }

    fun startVideoSource() {
        executor.execute {
            if (videoCapturer != null && videoCapturerStopped) {
                Log.d(TAG, "Restart video source.")
                videoCapturer!!.startCapture(videoWidth, videoHeight, videoFps)
                videoCapturerStopped = false
            }
        }
    }

    fun setVideoMaxBitrate(maxBitrateKbps: Int?) {
        executor.execute {
            if (peerConnection == null || localVideoSender == null || isError) {
                return@execute
            }
            Log.d(TAG, "Requested max video bitrate: $maxBitrateKbps")
            if (localVideoSender == null) {
                Log.w(TAG, "Sender is not ready.")
                return@execute
            }
            val parameters = localVideoSender!!.parameters
            if (parameters.encodings.size == 0) {
                Log.w(TAG, "RtpParameters are not ready.")
                return@execute
            }
            for (encoding in parameters.encodings) {
                // Null value means no limit.
                encoding.maxBitrateBps =
                    if (maxBitrateKbps == null) null else maxBitrateKbps * BPS_IN_KBPS
            }
            if (!localVideoSender!!.setParameters(parameters)) {
                Log.e(TAG, "RtpSender.setParameters failed.")
            }
            Log.d(TAG, "Configured max video bitrate to: $maxBitrateKbps")
        }
    }

    private fun reportError(errorMessage: String) {
        Log.e(TAG, "Peerconnection error: $errorMessage")
        executor.execute {
            if (!isError) {
                events.onPeerConnectionError(errorMessage)
                isError = true
            }
        }
    }

    private fun createAudioTrack(): AudioTrack? {
        audioSource = factory!!.createAudioSource(audioConstraints)
        localAudioTrack = factory!!.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        localAudioTrack!!.setEnabled(enableAudio)
        return localAudioTrack
    }

    private fun createVideoTrack(capturer: VideoCapturer?): VideoTrack? {
        surfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
        videoSource = factory!!.createVideoSource(capturer!!.isScreencast)
        capturer.initialize(surfaceTextureHelper, appContext, videoSource!!.capturerObserver)
        capturer.startCapture(videoWidth, videoHeight, videoFps)
        localVideoTrack = factory!!.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        localVideoTrack!!.setEnabled(renderVideo)
//        localVideoTrack!!.addSink(localRender)
        return localVideoTrack
    }

    private fun findVideoSender() {
        for (sender in peerConnection!!.senders) {
            if (sender.track() != null) {
                val trackType = sender.track()!!.kind()
                if (trackType == VIDEO_TRACK_TYPE) {
                    Log.d(TAG, "Found video sender.")
                    localVideoSender = sender
                }
            }
        }
    }

    private fun getRemoteVideoTrack(): VideoTrack? {
        for (transceiver in peerConnection!!.transceivers) {
            val track = transceiver.receiver.track()
            if (track is VideoTrack) {
                return track
            }
        }
        return null
    }

    private fun drainCandidates() {
        if (queuedRemoteCandidates != null) {
            Log.d(TAG, "Add " + queuedRemoteCandidates!!.size + " remote candidates")
            for (candidate in queuedRemoteCandidates!!) {
                peerConnection!!.addIceCandidate(candidate)
            }
            queuedRemoteCandidates = null
        }
    }

    private fun switchCameraInternal() {
        if (videoCapturer is CameraVideoCapturer) {
            if (!isVideoCallEnabled || isError) {
                Log.e(TAG, "Failed to switch camera. Video: $isVideoCallEnabled. Error : $isError")
                return  // No video is sent or only one camera is available or error happened.
            }
            Log.d(TAG, "Switch camera")
            val cameraVideoCapturer = videoCapturer as CameraVideoCapturer
            cameraVideoCapturer.switchCamera(null)
        } else {
            Log.d(TAG, "Will not switch camera, video caputurer is not a camera")
        }
    }

    fun switchCamera() {
        executor.execute { switchCameraInternal() }
    }

    fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        executor.execute {
            changeCaptureFormatInternal(width, height, framerate)
        }
    }

    private fun changeCaptureFormatInternal(width: Int, height: Int, framerate: Int) {
        if (!isVideoCallEnabled || isError || videoCapturer == null) {
            Log.e(TAG, "Failed to change capture format. Video: $isVideoCallEnabled. Error : $isError")
            return
        }
        Log.d(TAG, "changeCaptureFormat: " + width + "x" + height + "@" + framerate)
        videoSource!!.adaptOutputFormat(width, height, framerate)
    }

    private val pcObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            executor.execute {
                events.onIceCandidate(candidate)
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {
            executor.execute {
                events.onIceCandidatesRemoved(candidates)
            }
        }

        override fun onSignalingChange(newState: PeerConnection.SignalingState) {
            Log.d(TAG, "SignalingState: $newState")
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            executor.execute {
                Log.d(TAG, "IceConnectionState: $newState")
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        events.onIceConnected()
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        events.onIceDisconnected()
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        reportError("ICE connection failed.")
                    }
                }
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            executor.execute {
                Log.d(TAG, "PeerConnectionState: $newState")
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        events.onConnected()
                    }
                    PeerConnection.PeerConnectionState.DISCONNECTED -> {
                        events.onDisconnected()
                    }
                    PeerConnection.PeerConnectionState.FAILED -> {
                        reportError("DTLS connection failed.")
                    }
                }
            }
        }

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
            Log.d(TAG, "IceGatheringState: $newState")
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            Log.d(TAG, "IceConnectionReceiving changed to $receiving")
        }

        override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent) {
            Log.d(TAG, "Selected candidate pair changed because: $event")
        }

        override fun onAddStream(stream: MediaStream) {}
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(dc: DataChannel) {
            Log.d(TAG, "New Data channel " + dc.label())
            if (!dataChannelEnabled) return
            dc.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) {
                    Log.d(TAG, "Data channel buffered amount changed: ${dc.label()}: ${dc.state()}")
                }

                override fun onStateChange() {
                    Log.d(TAG, "Data channel state changed: ${dc.label()}: ${dc.state()}")
                }

                override fun onMessage(buffer: DataChannel.Buffer) {
                    if (buffer.binary) {
                        Log.d(TAG, "Received binary msg over $dc")
                        return
                    }
                    val data = buffer.data
                    val bytes = ByteArray(data.capacity())
                    data.get(bytes)
                    val strData = String(bytes, Charset.forName("UTF-8"))
                    Log.d(TAG, "Got msg: $strData over $dc")

                    // TODO
                }
            })
        }

        override fun onRenegotiationNeeded() {
            // No need to do anything; AppRTC follows a pre-agreed-upon
            // signaling/negotiation protocol.
        }

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
        }
    }

    private fun setLocalSdp(sdp: SessionDescription) {
        if (peerConnection != null && !isError) {
            Log.d(TAG, "Set local SDP from " + sdp.type)
            peerConnection!!.setLocalDescription(sdpObserver, sdp)
        }
    }

    private val sdpObserver = object : SdpObserver {
        override fun onCreateSuccess(origSdp: SessionDescription) {
            if (localSdp != null) {
                reportError("Multiple SDP create.")
                return
            }
            var sdpDescription = origSdp.description
            if (preferIsac) {
                sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true)
            }
            if (isVideoCallEnabled) {
                sdpDescription = preferCodec(
                    sdpDescription,
                    getSdpVideoCodecName(peerConnectionParameters),
                    false
                )
            }
            val sdp = Utils.createSDP(origSdp.type, sdpDescription)
            localSdp = sdp
            executor.execute {
                setLocalSdp(sdp)
            }
        }

        override fun onSetSuccess() {
            executor.execute {
                if (peerConnection == null || isError) {
                    return@execute
                }
                if (isInitiator) {
                    // For offering peer connection we first create offer and set
                    // local SDP, then after receiving answer set remote SDP.
                    if (peerConnection!!.remoteDescription == null) {
                        // We've just set our local SDP so time to send it.
                        Log.d(TAG, "Local SDP set succesfully")
                        events.onLocalDescription(localSdp!!)
                    } else {
                        // We've just set remote description, so drain remote
                        // and send local ICE candidates.
                        Log.d(TAG, "Remote SDP set succesfully")
                        drainCandidates()
                    }
                } else {
                    // For answering peer connection we set remote SDP and then
                    // create answer and set local SDP.
                    if (peerConnection!!.localDescription != null) {
                        // We've just set our local SDP so time to send it, drain
                        // remote and send local ICE candidates.
                        Log.d(TAG, "Local SDP set succesfully")
                        events.onLocalDescription(localSdp!!)
                        drainCandidates()
                    } else {
                        // We've just set remote SDP - do nothing for now -
                        // answer will be created soon.
                        Log.d(TAG, "Remote SDP set succesfully")
                    }
                }
            }
        }

        override fun onCreateFailure(error: String) {
            reportError("createSDP error: $error")
        }

        override fun onSetFailure(error: String) {
            reportError("setSDP error: $error")
        }
    }

    private fun getSdpVideoCodecName(parameters: PeerConnectionParameters): String {
        return when (parameters.videoCodec) {
            VIDEO_CODEC_VP8 -> VIDEO_CODEC_VP8
            VIDEO_CODEC_VP9 -> VIDEO_CODEC_VP9
            VIDEO_CODEC_H264_HIGH, VIDEO_CODEC_H264_BASELINE -> VIDEO_CODEC_H264
            else -> VIDEO_CODEC_VP8
        }
    }

    private fun getFieldTrials(peerConnectionParameters: PeerConnectionParameters): String {
        var fieldTrials = ""
        if (peerConnectionParameters.videoFlexfecEnabled) {
            fieldTrials += VIDEO_FLEXFEC_FIELDTRIAL
            Log.d(TAG, "Enable FlexFEC field trial.")
        }
        fieldTrials += VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL
        if (peerConnectionParameters.disableWebRtcAGCAndHPF) {
            fieldTrials += DISABLE_WEBRTC_AGC_FIELDTRIAL
            Log.d(TAG, "Disable WebRTC AGC field trial.")
        }
        return fieldTrials
    }

    private fun setStartBitrate(
        codec: String,
        isVideoCodec: Boolean,
        sdpDescription: String,
        bitrateKbps: Int
    ): String {
        val lines = sdpDescription.split("\r\n".toRegex()).toTypedArray()
        var rtpmapLineIndex = -1
        var sdpFormatUpdated = false
        var codecRtpMap: String? = null
        // Search for codec rtpmap in format
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        var regex = "^a=rtpmap:(\\d+) $codec(/\\d+)+[\r]?$"
        var codecPattern = Pattern.compile(regex)
        for (i in lines.indices) {
            val codecMatcher = codecPattern.matcher(lines[i])
            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1)
                rtpmapLineIndex = i
                break
            }
        }
        if (codecRtpMap == null) {
            Log.w(TAG, "No rtpmap for $codec codec")
            return sdpDescription
        }
        Log.d(TAG, "Found " + codec + " rtpmap " + codecRtpMap + " at " + lines[rtpmapLineIndex])

        // Check if a=fmtp string already exist in remote SDP for this codec and
        // update it with new bitrate parameter.
        regex = "^a=fmtp:$codecRtpMap \\w+=\\d+.*[\r]?$"
        codecPattern = Pattern.compile(regex)
        for (i in lines.indices) {
            val codecMatcher = codecPattern.matcher(lines[i])
            if (codecMatcher.matches()) {
                Log.d(TAG, "Found " + codec + " " + lines[i])
                if (isVideoCodec) {
                    lines[i] += "; $VIDEO_CODEC_PARAM_START_BITRATE=$bitrateKbps"
                } else {
                    lines[i] += "; " + AUDIO_CODEC_PARAM_BITRATE + "=" + bitrateKbps * 1000
                }
                Log.d(TAG, "Update remote SDP line: " + lines[i])
                sdpFormatUpdated = true
                break
            }
        }
        val newSdpDescription = StringBuilder()
        for (i in lines.indices) {
            newSdpDescription.append(lines[i]).append("\r\n")
            // Append new a=fmtp line if no such line exist for a codec.
            if (!sdpFormatUpdated && i == rtpmapLineIndex) {
                val bitrateSet = if (isVideoCodec) {
                    "a=fmtp:$codecRtpMap $VIDEO_CODEC_PARAM_START_BITRATE=$bitrateKbps"
                } else {
                    "a=fmtp:$codecRtpMap $AUDIO_CODEC_PARAM_BITRATE=${bitrateKbps * 1000}"
                }
                Log.d(TAG, "Add remote SDP line: $bitrateSet")
                newSdpDescription.append(bitrateSet).append("\r\n")
            }
        }
        return newSdpDescription.toString()
    }

    /** Returns the line number containing "m=audio|video", or -1 if no such line exists.  */
    private fun findMediaDescriptionLine(isAudio: Boolean, sdpLines: Array<String>): Int {
        val mediaDescription = if (isAudio) "m=audio " else "m=video "
        for (i in sdpLines.indices) {
            if (sdpLines[i].startsWith(mediaDescription)) {
                return i
            }
        }
        return -1
    }

    private fun joinString(
        s: Iterable<CharSequence?>,
        delimiter: String,
        delimiterAtEnd: Boolean
    ): String {
        val iter = s.iterator()
        if (!iter.hasNext()) {
            return ""
        }
        val buffer = StringBuilder(iter.next()!!)
        while (iter.hasNext()) {
            buffer.append(delimiter).append(iter.next())
        }
        if (delimiterAtEnd) {
            buffer.append(delimiter)
        }
        return buffer.toString()
    }

    private fun movePayloadTypesToFront(
        preferredPayloadTypes: List<String>,
        mLine: String
    ): String? {
        // The format of the media description line should be: m=<media> <port> <proto> <fmt> ...
        val origLineParts = Arrays.asList(*mLine.split(" ".toRegex()).toTypedArray())
        if (origLineParts.size <= 3) {
            Log.e(TAG, "Wrong SDP media description format: $mLine")
            return null
        }
        val header: List<String> = origLineParts.subList(0, 3)
        val unpreferredPayloadTypes: MutableList<String> =
            ArrayList(origLineParts.subList(3, origLineParts.size))
        unpreferredPayloadTypes.removeAll(preferredPayloadTypes)
        // Reconstruct the line with |preferredPayloadTypes| moved to the beginning of the payload
        // types.
        val newLineParts: MutableList<String?> = ArrayList()
        newLineParts.addAll(header)
        newLineParts.addAll(preferredPayloadTypes)
        newLineParts.addAll(unpreferredPayloadTypes)
        return joinString(newLineParts, " ", false /* delimiterAtEnd */)
    }

    private fun preferCodec(sdpDescription: String, codec: String, isAudio: Boolean): String {
        val lines = sdpDescription.split("\r\n".toRegex()).toTypedArray()
        val mLineIndex = findMediaDescriptionLine(isAudio, lines)
        if (mLineIndex == -1) {
            Log.w(TAG, "No mediaDescription line, so can't prefer $codec")
            return sdpDescription
        }
        // A list with all the payload types with name |codec|. The payload types are integers in the
        // range 96-127, but they are stored as strings here.
        val codecPayloadTypes: MutableList<String> = ArrayList()
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        val codecPattern = Pattern.compile("^a=rtpmap:(\\d+) $codec(/\\d+)+[\r]?$")
        for (line in lines) {
            val codecMatcher = codecPattern.matcher(line)
            if (codecMatcher.matches()) {
                codecPayloadTypes.add(codecMatcher.group(1)!!)
            }
        }
        if (codecPayloadTypes.isEmpty()) {
            Log.w(TAG, "No payload types with name $codec")
            return sdpDescription
        }
        val newMLine =
            movePayloadTypesToFront(codecPayloadTypes, lines[mLineIndex]) ?: return sdpDescription
        Log.d(TAG, "Change media description from: " + lines[mLineIndex] + " to " + newMLine)
        lines[mLineIndex] = newMLine
        return joinString(Arrays.asList(*lines), "\r\n", true /* delimiterAtEnd */)
    }


    /**
     * Create a PeerConnectionClient with the specified parameters. PeerConnectionClient takes
     * ownership of |eglBase|.
     */
    init {
        Log.d(TAG, "Preferred video codec: " + getSdpVideoCodecName(peerConnectionParameters))
        val fieldTrials = getFieldTrials(peerConnectionParameters)
        executor.execute {
            Log.d(TAG, "Initialize WebRTC. Field trials: $fieldTrials")
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                    .setFieldTrials(fieldTrials)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
            )
        }
    }
}
