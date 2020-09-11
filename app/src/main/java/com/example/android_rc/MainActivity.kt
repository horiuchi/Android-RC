package com.example.android_rc

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private val TAG = MainActivity::class.java.simpleName

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1001
        private const val REQUEST_MULTI_PERMISSION = 1002
        private const val STREAM_NAME_PREFIX = "android_device_stream"
        private val MANDATORY_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.INTERNET,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.RECORD_AUDIO
        )

        var mediaProjectionPermissionResultData: Intent? = null
        val metrics = DisplayMetrics()
    }

    private lateinit var preferences: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        preferences = Preferences(application)
        preferences.initializeRoomId()

        touchEmulationToggleButton.isEnabled = TouchEmulationAccessibilityService.instance == null
        touchEmulationToggleButton.setOnClickListener {
            val openSettings = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            openSettings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
            startActivity(openSettings)

            showMessage("Please enable the `android-rc` service.")
        }

        screenCaptureToggleButton.isChecked = !ScreenCaptureService.isActive
        screenCaptureToggleButton.setOnClickListener {
            if (screenCaptureToggleButton.isChecked) {
                val captureService = Intent(application, ScreenCaptureService::class.java)
                stopService(captureService)
            } else {
                if (RuntimePermissionUtils.hasSelfPermissions(this, *MANDATORY_PERMISSIONS)) {
                    requestScreenCapturePermission()
                } else {
                    requestPermissions(MANDATORY_PERMISSIONS, REQUEST_MULTI_PERMISSION)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        touchEmulationToggleButton.isEnabled = TouchEmulationAccessibilityService.instance == null
        screenCaptureToggleButton.isChecked = !ScreenCaptureService.isActive
        errorMultiLineText.text = ScreenCaptureService.errorDescription.joinToString("\n")
    }

    private fun startCaptureService(intent: Intent) {
        val wsUrl = preferences.wsUrl
        val roomId = preferences.roomId
        if (wsUrl.isBlank() || roomId.isBlank()) {
            showMessage("Required the `wsUrl` and `roomId` configurations.")
            screenCaptureToggleButton.isChecked = false
            return
        }
        val clientId = Utils.randomString(6)

        intent.putExtra(ScreenCaptureService.EXTRA_WS_URL, wsUrl)
        intent.putExtra(ScreenCaptureService.EXTRA_ROOM_ID, roomId)
        intent.putExtra(ScreenCaptureService.EXTRA_CLIENT_ID, clientId)
        startForegroundService(intent)

        backToHome()
    }

    private fun backToHome() {
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)
    }

    override fun onRequestPermissionsResult( requestCode: Int, permissions: Array<out String>, grantResults: IntArray ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_MULTI_PERMISSION && grantResults.isNotEmpty()) {
            if (!RuntimePermissionUtils.checkGrantResults(*grantResults)) {
                showMessage("Not granted permissions.")
            } else {
                requestScreenCapturePermission()
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
                showMessage("User cancelled")
                return
            }

            mediaProjectionPermissionResultData = data
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val captureService = Intent(application, ScreenCaptureService::class.java)
            startCaptureService(captureService)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        Log.i(TAG, "onCreateOptionsMenu Called")
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
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openSettingsActivity() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun showMessage(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}