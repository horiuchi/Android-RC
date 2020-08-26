package com.example.android_rc

import org.webrtc.SessionDescription

object Utils {
    private val charPool = ('0'..'9')

    fun randomString(length: Int): String {
        return (1..length)
            .map { charPool.random() }
            .joinToString("");
    }

    fun createSDP(type: SessionDescription.Type, description: String): SessionDescription {
        return SessionDescription(type, "${description.trim()}\r\n")
    }
}