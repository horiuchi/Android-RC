package com.example.capture_sender

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_service_control.*
import org.webrtc.ScreenCapturerAndroid

class ServiceControlActivity : AppCompatActivity() {
    private val TAG = ServiceControlActivity::class.java.simpleName

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1001
        private const val REQUEST_MULTI_PERMISSION = 1002
        private const val STREAM_NAME_PREFIX = "android_device_stream"
        private val MANDATORY_PERMISSIONS = arrayOf(
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.INTERNET,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.RECORD_AUDIO
        )

        var mediaProjectionPermissionResultData: Intent? = null
        val metrics = DisplayMetrics()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_control)

        checkPermissions()

        toggleButton.isChecked = !ScreenCaptureService.isActive
        toggleButton.setOnClickListener {
            val captureService = Intent(application, ScreenCaptureService::class.java)
            if (toggleButton.isChecked) {
                stopService(captureService)
            } else {
                startForegroundService(captureService)
            }
        }
        toggleButton.isEnabled = false

        requestScreenCapturePermission()
    }

    private fun checkPermissions() {
        val notGranted = arrayListOf<String>()
        for (permission in MANDATORY_PERMISSIONS) {
            if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                notGranted.add(permission)
            }
        }
        requestPermissions(notGranted.toTypedArray(), REQUEST_MULTI_PERMISSION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_MULTI_PERMISSION) {
            for (result in grantResults.withIndex()) {
                if (result.value != PackageManager.PERMISSION_GRANTED) {
                    val permission = permissions[result.index]
                    Log.e(TAG, "Not granted the permission: $permission")
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                    return
                }
            }
        }
    }

    private fun requestScreenCapturePermission() {
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager?
        if (mediaProjectionManager != null) {
            startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Toast.makeText(this, "User cancelled", Toast.LENGTH_LONG).show();
                return
            }

            mediaProjectionPermissionResultData = data
            windowManager.defaultDisplay.getRealMetrics(metrics)

            toggleButton.isEnabled = true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_options, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.app_bar_settings -> {
                openSettingsActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openSettingsActivity() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}