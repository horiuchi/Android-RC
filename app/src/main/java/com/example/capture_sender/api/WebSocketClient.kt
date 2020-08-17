package com.example.capture_sender.api

import android.util.Log
import okhttp3.*
import java.time.Duration


class WebSocketClient(private val options: ConnectOptions, private val events: WebSocketEvents) {
    private val TAG = WebSocketClient::class.java.simpleName

    companion object {
        val Timeout: Duration = Duration.ofSeconds(10)
    }

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
            closeSocket(t)
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

    private fun send(action: Models) {
        Log.i(TAG, "On Send Action: $action")
        socket?.send(toJson(action))
    }

    private fun register() {
        state.registering()
        send(Models.RegisterAction(roomId = options.roomId, clientId = options.clientId))
        events.onOpened()
    }

    private fun offerOrWait(accept: Models.AcceptEvent) {
        if (accept.isExistClient) {
            state.connecting()
            send(Models.OfferAction(sdp = options.getSdp()))
        } else {
            state.waitPartner()
        }
    }

    private fun answer(offer: Models.OfferAction) {
        state.connected()
        send(Models.AnswerEvent(sdp = options.getSdp()))
        events.onConnected(offer.sdp)
    }

    private fun connected(answer: Models.AnswerEvent) {
        state.connected()
        events.onConnected(answer.sdp)
    }

    private fun candidate(data: Models.CandidateData) {
        events.onCandidate(data.ice)
    }

    private fun pong() {
        send(Models.PongEvent())
    }

    fun sendCandidate(data: Models.IceData) {
        send(Models.CandidateData(ice = data))
    }

    fun close(code: Int = 1000, reason: String? = null) {
        socket?.close(code, reason)
        closeSocket()
    }

    private fun closeSocket(t: Throwable? = null) {
        socket = null
        state.closed()
        if (t != null) {
            events.onFailure(t)
        } else {
            events.onClosed()
        }
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
    val getSdp: () -> String
)

interface WebSocketEvents {
    fun onOpened()
    fun onConnected(sdp: String)
    fun onCandidate(action: Models.IceData)
    fun onClosed()
    fun onFailure(t: Throwable)
}

class ConnectionState {
    private val TAG = ConnectionState::class.java.simpleName

    private enum class State {
        OPEN,
        REGISTERING,
        WAIT_PARTNER,
        CONNECTING,
        CONNECTED,
        CLOSED,
    }

    private var state = State.CLOSED

    fun open() {
        if (state != State.CLOSED) {
            throw IllegalStateException("Can not change state to `open`. Now state is not `closed`.")
        }
        changeState(State.OPEN)
    }

    fun registering() {
        if (state != State.OPEN) {
            throw IllegalStateException("Can not change state to `registering`. Now state is not `open`.")
        }
        changeState(State.REGISTERING)
    }

    fun waitPartner() {
        if (state != State.REGISTERING) {
            throw IllegalStateException("Can not change state to `waitPartner`. Now state is not `registering`.")
        }
        changeState(State.WAIT_PARTNER)
    }

    fun connecting() {
        if (state != State.REGISTERING) {
            throw IllegalStateException("Can not change state to `connecting`. Now state is not `registering`.")
        }
        changeState(State.CONNECTING)
    }

    fun connected() {
        if (state != State.CONNECTING || state != State.WAIT_PARTNER) {
            throw IllegalStateException("Can not change state to `connected`. Now state is not `waitPartner` nor `connecting`.")
        }
        changeState(State.CONNECTED)
    }

    fun closed() {
        changeState(State.CLOSED)
    }

    private fun changeState(newState: State) {
        Log.i(TAG, "Change State: $state -> $newState")
        state = newState
    }
}

