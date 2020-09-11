package com.example.android_rc

import android.accessibilityservice.GestureDescription
import android.app.*
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.android_rc.api.*
import org.webrtc.*

const val NOTIFICATION_ID = "screen_capture_sender_service"

class ScreenCaptureService : Service() {
    private val TAG = ScreenCaptureService::class.java.simpleName

    companion object {
        const val EXTRA_WS_URL = "EXTRA_WS_URL"
        const val EXTRA_ROOM_ID = "EXTRA_ROOM_ID"
        const val EXTRA_CLIENT_ID = "EXTRA_CLIENT_ID"

        private const val CAPTURE_THREAD = "CaptureThread"
        private const val TRACK_ID_PREFIX = "ARDAMS"
        private const val VIDEO_TRACK_ID = TRACK_ID_PREFIX + "v0"
        private const val AUDIO_TRACK_ID = TRACK_ID_PREFIX + "a0"
        private const val SCREEN_RESOLUTION_SCALE = 2.0f
        private const val VIDEO_CODEC = "VP8"
        private const val STAT_CALLBACK_PERIOD = 1000

        var isActive: Boolean = false
        val errorDescription: MutableList<String> = mutableListOf()
    }

    private val handler: Handler = Handler()
    private val eglBase: EglBase = EglBase.create()
    private var isError = false
    private var connected: Boolean = false
    private var peerConnectionClient: PeerConnectionClient? = null
    private var webSocketClient: WebSocketClient? = null
    private var initiator: Boolean = false
    private var callStartedTimeMs: Long = 0


    override fun onBind(intent: Intent): IBinder {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        isActive = true
        errorDescription.clear()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        isActive = false
        stopScreenCapture()
    }

    private fun stopScreenCapture() {
        peerConnectionClient?.close()
        peerConnectionClient = null

        webSocketClient?.close()
        webSocketClient = null

        returnToActivity()
        if (isActive) stopSelf()
    }

    private fun returnToActivity() {
        val intent = Intent(applicationContext, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(1, notification)

        initializePeerConnection()
        initializeWebSocket(intent)
        callStartedTimeMs = System.currentTimeMillis()
        return START_STICKY
    }

    private fun createNotification(): Notification {
        createNotificationChannel()

        val openIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        val stopIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, ServiceStopperBroadcastReceiver::class.java).apply {
                action = "com.example.capture_sender.action.STOP_SERVICE"
            },
            0
        )
        return NotificationCompat.Builder(this, NOTIFICATION_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Screen Capture")
            .setContentText("Now Screen Capturing...")
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop Screen Capture", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val context = applicationContext
        val manager = NotificationManagerCompat.from(context)
        if (manager.getNotificationChannel(NOTIFICATION_ID) == null) {
            val mChannel = NotificationChannel(
                NOTIFICATION_ID,
                context.getString(R.string.app_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(mChannel)
        }
    }

    private fun initializeWebSocket(intent: Intent) {
        val wsUrl = getExtra(intent, EXTRA_WS_URL)
        val roomId = getExtra(intent, EXTRA_ROOM_ID)
        val clientId = getExtra(intent, EXTRA_CLIENT_ID)

        webSocketClient = WebSocketClient(
            ConnectOptions(wsUrl, roomId, clientId), webSocketEvents
        )
    }

    private fun getExtra(intent: Intent, key: String): String {
        return intent.getStringExtra(key)
            ?: throw IllegalArgumentException("The Intent Extra Key ($key) is required.")
    }

    private fun initializePeerConnection() {
        MainActivity.mediaProjectionPermissionResultData
            ?: throw IllegalStateException("MediaProjectionPermissionResult is null")

        val metrics = MainActivity.metrics
        val width = (metrics.widthPixels / SCREEN_RESOLUTION_SCALE).toInt()
        val height = (metrics.heightPixels / SCREEN_RESOLUTION_SCALE).toInt()
        val fps = 30
        val peerConnectionParameters = PeerConnectionClient.PeerConnectionParameters(
            true, width, height, SCREEN_RESOLUTION_SCALE, fps, 0, VIDEO_CODEC, true, true,
            0, null, true, false, false, false, false
        )

        peerConnectionClient = PeerConnectionClient(
            applicationContext,
            eglBase,
            peerConnectionParameters,
            peerConnectionEvents,
            clientDataEvents
        )
        peerConnectionClient!!.createPeerConnectionFactory(PeerConnectionFactory.Options())
    }

    private val webSocketEvents = object : WebSocketEvents {
        override fun onConnected(params: SignalingParameters) {
            runOnMainThread { onConnectedInternal(params) }
        }

        override fun onRemoteDescription(sdp: SessionDescription) {
            runOnMainThread { onRemoteDescriptionInternal(sdp) }
        }

        override fun onRemoteIceCandidate(candidate: IceCandidate) {
            runOnMainThread { onRemoteIceCandidateInternal(candidate) }
        }

        override fun onChannelClose() {
            runOnMainThread { onChannelCloseInternal() }
        }

        override fun onChannelError(description: String) {
            reportError(description)
        }
    }

    fun onConnectedInternal(params: SignalingParameters) {
        val delta = System.currentTimeMillis() - callStartedTimeMs

        Log.i(TAG, "Creating peer connection, delay=" + delta + "ms")
        val videoCapturer: VideoCapturer = ScreenCapturerAndroid(
            MainActivity.mediaProjectionPermissionResultData,
            object : MediaProjection.Callback() {
                override fun onStop() {
                    reportError("User revoked permission to capture the screen.")
                }
            }
        )
        peerConnectionClient!!.createPeerConnection(null, null, videoCapturer)

        initiator = params.initiator
        if (initiator) {
            Log.i(TAG, "Creating OFFER...")
            peerConnectionClient!!.createOffer()
        } else {
            if (params.offerSdp != null) {
                peerConnectionClient!!.setRemoteDescription(params.offerSdp)
                Log.i(TAG, "Creating ANSWER...")
                peerConnectionClient!!.createAnswer()
            }
        }
    }

    fun onRemoteDescriptionInternal(sdp: SessionDescription) {
        val delta = System.currentTimeMillis() - callStartedTimeMs
        if (peerConnectionClient == null) {
            Log.e(TAG, "Received remote SDP for non-initialized peer connection.")
            return
        }
        Log.i(TAG, "Received remote " + sdp.type + ", delay=" + delta + "ms")
        peerConnectionClient!!.setRemoteDescription(sdp)
        if (!initiator) {
            Log.i(TAG, "Creating ANSWER...")
            peerConnectionClient!!.createAnswer()
        }
    }

    fun onRemoteIceCandidateInternal(candidate: IceCandidate) {
        if (peerConnectionClient == null) {
            Log.e(TAG, "Received ICE candidate for a non-initialized peer connection.")
            return
        }
        peerConnectionClient!!.addRemoteIceCandidate(candidate)
    }

    fun onChannelCloseInternal() {
        Log.i(TAG, "Remote end hung up; dropping PeerConnection")
        stopScreenCapture()
    }


    private val peerConnectionEvents = object : PeerConnectionClient.PeerConnectionEvents {
        override fun onLocalDescription(sdp: SessionDescription) {
            runOnMainThread {
                onLocalDescriptionInternal(sdp)
            }
        }

        override fun onIceCandidate(candidate: IceCandidate) {
            runOnMainThread {
                onIceCandidateInternal(candidate)
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {
            runOnMainThread {
                onIceCandidatesRemovedInternal(candidates)
            }
        }

        override fun onIceConnected() {
            runOnMainThread {
                onIceConnectedInternal()
            }
        }

        override fun onIceDisconnected() {
            runOnMainThread {
                onIceDisconnectedInternal()
            }
        }

        override fun onConnected() {
            runOnMainThread {
                onConnectedInternal()
            }
        }

        override fun onDisconnected() {
            runOnMainThread {
                onDisconnectedInternal()
            }
        }

        override fun onPeerConnectionClosed() {
            runOnMainThread {
                onPeerConnectionClosedInternal()
            }
        }

        override fun onPeerConnectionStatsReady(reports: Array<StatsReport>) {
            runOnMainThread {
                onPeerConnectionStatsReadyInternal(reports)
            }
        }

        override fun onPeerConnectionError(description: String) {
            reportError(description)
        }
    }

    fun onLocalDescriptionInternal(sdp: SessionDescription) {
        val delta = System.currentTimeMillis() - callStartedTimeMs
        if (webSocketClient != null) {
            Log.i(TAG, "Sending " + sdp.type + ", delay=" + delta + "ms")
            if (initiator) {
                webSocketClient!!.sendOffer(sdp)
            } else {
                webSocketClient!!.sendAnswer(sdp)
            }
        }
    }

    fun onIceCandidateInternal(candidate: IceCandidate) {
        webSocketClient?.sendCandidate(candidate)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onIceCandidatesRemovedInternal(candidates: Array<IceCandidate>) {
        Log.w(TAG, "onIceCandidatesRemovedInternal event is not supported.")
//        webSocketClient?.sendLocalIceCandidateRemovals(candidates)
    }

    fun onIceConnectedInternal() {
        val delta = System.currentTimeMillis() - callStartedTimeMs
        Log.i(TAG, "ICE connected, delay=${delta}ms")
    }

    fun onIceDisconnectedInternal() {
        Log.i(TAG, "ICE disconnected")
    }

    fun onConnectedInternal() {
        val delta = System.currentTimeMillis() - callStartedTimeMs
        Log.i(TAG, "DTLS connected, delay=${delta}ms")
        connected = true

        if (peerConnectionClient == null || isError) {
            Log.w(TAG, "Call is connected in closed or error state")
            return
        }
        // Enable statistics callback.
        peerConnectionClient!!.enableStatsEvents(true, STAT_CALLBACK_PERIOD)
    }

    fun onDisconnectedInternal() {
        Log.i(TAG, "DTLS disconnected")
        connected = false
        stopScreenCapture()
    }

    fun onPeerConnectionClosedInternal() {
    }

    fun onPeerConnectionStatsReadyInternal(reports: Array<StatsReport>) {
        if (!isError && connected) {
            Log.i(TAG, "OnPeerConnectionStats: $reports")
//            hudFragment.updateEncoderStatistics(reports)
        }
    }

    private val clientDataEvents = object : PeerConnectionClient.ClientDataEvents {
        override fun onTouchEvent(stroke: GestureDescription.StrokeDescription) {
            runOnMainThread {
                onTouchEventInternal(stroke)
            }
        }
    }

    fun onTouchEventInternal(stroke: GestureDescription.StrokeDescription) {
        TouchEmulationAccessibilityService.instance?.doTouch(stroke)
    }

    private fun runOnMainThread(task: () -> Unit) {
        handler.post(task)
    }

    private fun reportError(description: String) {
        runOnMainThread {
            Log.e(TAG, "Error: $description")
            errorDescription.add(description)
            if (!isError) {
                isError = true
                stopScreenCapture()
            }
        }
    }
}
