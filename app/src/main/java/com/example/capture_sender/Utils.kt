package com.example.capture_sender

object Utils {
    private val charPool = ('0'..'9')

    fun randomString(length: Int): String {
        return (1..length)
            .map { charPool.random() }
            .joinToString("");
    }
}