package com.example.capture_sender

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class TouchEmulationAccessibilityService : AccessibilityService() {
    private val TAG = TouchEmulationAccessibilityService::class.java.simpleName

    companion object {
        var instance: TouchEmulationAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }


    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        Log.i(TAG, "onAccessibilityEvent: $event")
    }

    override fun onInterrupt() {
        Log.i(TAG, "onInterrupt")
    }


    fun doTouch(x: Float, y: Float) {
        Log.i(TAG, "Called doTouch: ($x, $y)")
        val gestureBuilder = GestureDescription.Builder()
        val path = Path()
        path.moveTo(x, y)
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0L, 10L))
        val gestureDescription = gestureBuilder.build()

        val result = this.dispatchGesture(gestureDescription, object: GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.i(TAG, "Called Completed: ($x, $y) $gestureDescription")
                super.onCompleted(gestureDescription)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.i(TAG, "Called Cancelled: ($x, $y) $gestureDescription")
                super.onCancelled(gestureDescription)
            }
        }, null)
        Log.i(TAG, "Called Finished: ($x, $y) $result")
    }
}
