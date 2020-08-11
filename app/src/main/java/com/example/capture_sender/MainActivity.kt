package com.example.capture_sender

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjection.Callback
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.activity_main.*
import org.webrtc.ScreenCapturerAndroid


class MainActivity : AppCompatActivity() {
    private val TAG = MainActivity::class.java.simpleName

    private val SCREEN_RESOLUTION_SCALE = 2
    private val REQUEST_MEDIA_PROJECTION = 1001
    private val REQUEST_MULTI_PERMISSION = 1002
    private val STREAM_NAME_PREFIX = "android_device_stream"
    private val MANDATORY_PERMISSIONS = arrayOf(
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.INTERNET,
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.RECORD_AUDIO
    )

    private val vm by lazy {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            .create(MainViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        checkPermissions()

        // initialize ViewModel
        vm.initialize()
        observeViewModel()
        fab_connect.setOnClickListener {
            var captureService = Intent(application, ScreenCaptureService::class.java)
            startForegroundService(captureService)

            vm.onClickConnect()
        }
        fab_disconnect.setOnClickListener {
            var captureService = Intent(application, ScreenCaptureService::class.java)
            stopService(captureService)

            vm.onClickDisconnect()
        }

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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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

            val capturer = ScreenCapturerAndroid(data, object : Callback() {
                override fun onStop() {
                    reportMessage("User revoked permission to capture the screen.");
                }
            })

            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            vm.setUp(
                capturer, MainViewModel.Parameters(
                    width = metrics.widthPixels / SCREEN_RESOLUTION_SCALE,
                    height = metrics.heightPixels / SCREEN_RESOLUTION_SCALE,
                    fps = 0
                )
            )
        }
    }

    private fun observeViewModel() {
        vm.reportMessage.observe(this, Observer {
            if (it.isNotBlank()) {
                reportMessage(it)
            }
        })
        vm.fabConnectVisibility.observe(this, Observer {
            fab_connect.visibility = it
        })
        vm.fabDisconnectVisibility.observe(this, Observer {
            fab_disconnect.visibility = it
        })
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

    private fun reportMessage(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }
}