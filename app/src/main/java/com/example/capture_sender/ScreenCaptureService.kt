package com.example.capture_sender

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.capture_sender.api.PeerConnectionClient
import org.webrtc.*

const val NOTIFICATION_ID = "screen_capture_sender_service"

class ScreenCaptureService : Service() {
    private val TAG = ScreenCaptureService::class.java.simpleName

    companion object {
        private const val CAPTURE_THREAD = "CaptureThread"
        private const val TRACK_ID_PREFIX = "ARDAMS"
        private const val VIDEO_TRACK_ID = TRACK_ID_PREFIX + "v0"
        private const val AUDIO_TRACK_ID = TRACK_ID_PREFIX + "a0"
        private const val SCREEN_RESOLUTION_SCALE = 2
        private const val VIDEO_CODEC = "VP8"

        var isActive: Boolean = false
    }

    val eglBase: EglBase = EglBase.create()
    var peerConnectionClient: PeerConnectionClient? = null

    override fun onBind(intent: Intent): IBinder {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        isActive = true
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        isActive = false
        stopScreenCapture()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(1, notification)

        startScreenCapture()
        return START_STICKY
    }

    private fun createNotification(): Notification {
        createNotificationChannel()

        val openIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, ServiceControlActivity::class.java),
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

    private fun startScreenCapture() {
        val data = ServiceControlActivity.mediaProjectionPermissionResultData
            ?: throw IllegalStateException("MediaProjectionPermissionResult is null")

        val metrics = ServiceControlActivity.metrics
        val width = metrics.widthPixels / SCREEN_RESOLUTION_SCALE
        val height = metrics.heightPixels / SCREEN_RESOLUTION_SCALE
        val fps = 30
        val dataChannelParameters = PeerConnectionClient.DataChannelParameters(
            false, 0, 0, "", false, 0
        )
        val peerConnectionParameters = PeerConnectionClient.PeerConnectionParameters(
            true, width, height, fps, 0, VIDEO_CODEC, true, true,
            0, null, true, false, false, false, false, dataChannelParameters
        )

        peerConnectionClient = PeerConnectionClient(
            applicationContext, eglBase, peerConnectionParameters, peerConnectionEvents
        )
        val options = PeerConnectionFactory.Options()
        peerConnectionClient!!.createPeerConnectionFactory(options)

    }

    private fun stopScreenCapture() {
        peerConnectionClient?.close()
        peerConnectionClient = null
    }

    private val peerConnectionEvents = object : PeerConnectionClient.PeerConnectionEvents {
        override fun onLocalDescription(sdp: SessionDescription) {
        }

        override fun onIceCandidate(candidate: IceCandidate) {
        }

        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {
        }

        override fun onIceConnected() {
        }

        override fun onIceDisconnected() {
        }

        override fun onConnected() {
        }

        override fun onDisconnected() {
        }

        override fun onPeerConnectionClosed() {
        }

        override fun onPeerConnectionStatsReady(reports: Array<StatsReport>) {
        }

        override fun onPeerConnectionError(description: String) {
        }
    }

}
