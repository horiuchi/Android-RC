package com.example.capture_sender

import android.app.Application
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.capture_sender.api.ConnectOptions
import com.example.capture_sender.api.Models
import com.example.capture_sender.api.PeerConnection
import kotlinx.coroutines.launch
import org.webrtc.PeerConnectionFactory
import org.webrtc.VideoCapturer

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private lateinit var preferences: Preferences
    private lateinit var capturer: VideoCapturer
    private lateinit var factory: PeerConnectionFactory
    private var conn: PeerConnection? = null
    private var clientId: String? = null

    val reportMessage = MutableLiveData<String>()
    val fabConnectVisibility = MutableLiveData<Int>()
    val fabDisconnectVisibility = MutableLiveData<Int>()

    fun initialize() {
        preferences = Preferences(getApplication())
        preferences.initializeRoomId()

        fabConnectVisibility.value = toVisibility(false)
        fabDisconnectVisibility.value = toVisibility(false)
    }

    fun setUp(vc: VideoCapturer) {
        capturer = vc
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(getApplication()).createInitializationOptions())
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()

        fabConnectVisibility.value = toVisibility(true)
    }

    override fun onCleared() {
        super.onCleared()

        factory?.dispose()
        conn?.close()
    }

    fun onClickConnect() {
        conn?.close()
        conn = null

        val option = buildConnectionOption()
        if (option != null) {
            viewModelScope.launch {
                conn = PeerConnection(option)
                startConnection()
            }
        }
    }

    fun onClickDisconnect() {
        conn?.close()
        conn = null

        viewModelScope.launch {
            disconnected()
        }
    }

    private fun buildConnectionOption(): ConnectOptions? {
        val wsUrl = preferences.wsUrl
        val roomId = preferences.roomId
        if (wsUrl.isBlank() || roomId.isBlank()) {
            reportMessage.value = "Required the `wsUrl` and `roomId` configurations."
            return null
        }

        val id = Utils.randomString(6)
        clientId = id
        return ConnectOptions(
            wsUrl,
            roomId,
            id,
            { getLocalSdp() },
            { onOpened() },
            { onSetRemoteSdp(it) },
            { onReceiveIceData(it) },
            { onClosed() }
        )
    }

    private fun getLocalSdp(): String {
        return ""
    }

    private fun onOpened() {
        viewModelScope.launch {
            connected()
        }
    }

    private fun onSetRemoteSdp(sdp: String) {
    }

    private fun onReceiveIceData(ice: Models.IceData) {
    }

    private fun onClosed() {
        viewModelScope.launch {
            disconnected()
        }
    }

    fun startConnection() {
        fabConnectVisibility.value = toVisibility(false)
    }

    fun connected() {
        fabDisconnectVisibility.value = toVisibility(true)
    }

    fun disconnected() {
        fabConnectVisibility.value = toVisibility(true)
        fabDisconnectVisibility.value = toVisibility(false)
    }

    private fun toVisibility(b: Boolean): Int {
        return if (b) View.VISIBLE else View.INVISIBLE
    }
}
