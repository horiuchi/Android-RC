package com.example.capture_sender.api

import android.util.Log
import okhttp3.*
import java.time.Duration

val Timeout: Duration = Duration.ofSeconds(10)

class PeerConnection(private val options: ConnectOptions) {
    private val TAG = PeerConnection::class.java.simpleName

    private val state = ConnectionState()
    private var socket: WebSocket? = null


    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "On Open Event: $response")
            register()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "On Closing Event: $code, $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "On Closed Event: $code, $reason")
            closeSocket()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "On Failure Event: $response, $t")
            closeSocket()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.i(TAG, "On Message Event: $text")
            val model = fromJson(text)
            if (model == null) {
                Log.e(TAG, "Error parsing response data.")
                return
            }

            when (model.type) {
                ModelsType.accept -> {
                    val action = model as Models.AcceptEvent
                    offerOrWait(action)
                }
                ModelsType.reject -> {
                    close()
                }
                ModelsType.offer -> {
                    val action = model as Models.OfferAction
                    answer(action)
                }
                ModelsType.answer -> {
                    val action = model as Models.AnswerEvent
                    connected(action)
                }
                ModelsType.candidate -> {
                    val action = model as Models.CandidateData
                    candidate(action)
                }
                ModelsType.ping -> {
                    pong()
                }
                ModelsType.bye -> {
                    closeSocket()
                }
                else -> {
                    Log.w(TAG, "Receive an illegal type message.")
                }
            }
        }
    }

    private fun register() {
        state.registering()
        val action = Models.RegisterAction(roomId = options.roomId, clientId = options.clientId)
        socket?.send(toJson(action))
    }

    private fun offerOrWait(accept: Models.AcceptEvent) {
        if (accept.isExistClient) {
            state.connecting()
            val action = Models.OfferAction(sdp = options.getSdp())
            socket?.send(toJson(action))
        } else {
            state.waitPartner()
        }
    }

    private fun answer(offer: Models.OfferAction) {
        state.connected()
        val action = Models.AnswerEvent(sdp = options.getSdp())
        socket?.send(toJson(action))
        options.onConnected(offer.sdp)
    }

    private fun connected(answer: Models.AnswerEvent) {
        state.connected()
        options.onConnected(answer.sdp)
    }

    private fun candidate(data: Models.CandidateData) {
        options.onCandidate(data.ice)
    }

    private fun pong() {
        val action = Models.PongEvent()
        socket?.send(toJson(action))
    }

    fun sendCandidate(data: Models.IceData) {
        val action = Models.CandidateData(ice = data)
        socket?.send(toJson(action))
    }

    fun close(code: Int = 1000, reason: String? = null) {
        socket?.close(code, reason)
        closeSocket()
    }

    private fun closeSocket() {
        socket = null
        state.closed()
        options.onClosed()
    }

    init {
        socket = OkHttpClient.Builder()
            .connectTimeout(Timeout)
            .readTimeout(Timeout)
            .writeTimeout(Timeout)
            .build()
            .newWebSocket(
                Request.Builder()
                    .url(options.wsUrl)
                    .build(),
                listener
            )
        state.open()
    }
}

data class ConnectOptions(
    val wsUrl: String,
    val roomId: String,
    val clientId: String,
    val getSdp: () -> String,

    val onConnected: (sdp: String) -> Unit,
    val onCandidate: (action: Models.IceData) -> Unit,
    val onClosed: () -> Unit
)

class ConnectionState {
    private val TAG = ConnectionState::class.java.simpleName

    private enum class State {
        open,
        registering,
        waitPartner,
        connecting,
        connected,
        closed,
    }

    private var state = State.closed

    fun open() {
        if (state != State.closed) {
            throw IllegalStateException("Can not change state to `open`. Now state is not `closed`.")
        }
        changeState(State.open)
    }

    fun registering() {
        if (state != State.open) {
            throw IllegalStateException("Can not change state to `registering`. Now state is not `open`.")
        }
        changeState(State.registering)
    }

    fun waitPartner() {
        if (state != State.registering) {
            throw IllegalStateException("Can not change state to `waitPartner`. Now state is not `registering`.")
        }
        changeState(State.waitPartner)
    }

    fun connecting() {
        if (state != State.registering) {
            throw IllegalStateException("Can not change state to `connecting`. Now state is not `registering`.")
        }
        changeState(State.connecting)
    }

    fun connected() {
        if (state != State.connecting || state != State.waitPartner) {
            throw IllegalStateException("Can not change state to `connected`. Now state is not `waitPartner` nor `connecting`.")
        }
        changeState(State.connected)
    }

    fun closed() {
        changeState(State.closed)
    }

    private fun changeState(newState: State) {
        Log.i(TAG, "Change State: $state -> $newState")
    }
}

