package com.example.android_rc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ServiceStopperBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val targetIntent = Intent(context, ScreenCaptureService::class.java)
        context.stopService(targetIntent)
    }
}
