package com.example.capture_sender

import android.app.Application
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.capture_sender.api.ConnectOptions
import com.example.capture_sender.api.Models
import com.example.capture_sender.api.WebSocketClient
import kotlinx.coroutines.launch
import org.webrtc.VideoCapturer

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private lateinit var preferences: Preferences
    private lateinit var model: ScreenCaptureModel
    private var conn: WebSocketClient? = null
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

    fun setUp(vc: VideoCapturer, params: Parameters) {
        model = ScreenCaptureModel(vc, params)
        model.initialize(getApplication())

        fabConnectVisibility.value = toVisibility(true)
    }

    override fun onCleared() {
        super.onCleared()

        model?.dispose()
        conn?.close()
    }

    fun onClickConnect() {
        conn?.close()
        conn = null

        val option = buildConnectionOption()
        if (option != null) {
            viewModelScope.launch {
//                conn = WebSocketClient(option)
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
            { getLocalSdp() }
//            { onOpened() },
//            { onSetRemoteSdp(it) },
//            { onReceiveIceData(it) },
//            { onClosed() }
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

    data class Parameters(
        val width: Int,
        val height: Int,
        val fps: Int
    )
}
