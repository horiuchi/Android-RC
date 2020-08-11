package com.example.capture_sender

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

const val NOTIFICATION_ID = "screen_capture_sender_service"

class ScreenCaptureService : Service() {
    private val TAG = ScreenCaptureService::class.java.simpleName

    companion object {
        var isActive: Boolean = false
    }

    override fun onBind(intent: Intent): IBinder {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val openIntent =
            PendingIntent.getActivity(this, 0, Intent(this, ServiceControlActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, ServiceStopperBroadcastReceiver::class.java).apply {
                action = "com.example.capture_sender.action.STOP_SERVICE"
            },
            0
        )
        val notification = NotificationCompat.Builder(this, NOTIFICATION_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Screen Capture")
            .setContentText("Now Screen Capturing...")
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop Screen Capture", stopIntent)
            .build()

        startForeground(1, notification)
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        isActive = true
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        isActive = false
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
}
