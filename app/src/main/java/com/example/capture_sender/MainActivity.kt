package com.example.capture_sender

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    lateinit var vm: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        vm = MainViewModel(this)
        vm.initialize()
        observeViewModel()
        fab_connect.setOnClickListener {
            vm.onClickConnect()
        }
        fab_disconnect.setOnClickListener {
            vm.onClickDisconnect()
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

    fun openSettingsActivity() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    fun reportMessage(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }
}