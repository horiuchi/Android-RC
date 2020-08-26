package com.example.android_rc

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager

private enum class Key {
    WsUrl,
    RoomId,
}

class Preferences(private val context: Context) {
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    val wsUrl: String
        get() = getValue(Key.WsUrl.name)

    val roomId: String
        get() = getValue(Key.RoomId.name)

    fun initializeRoomId() {
        val value = roomId
        if (value.isBlank()) {
            sharedPreferences.edit {
                this.putString(Key.RoomId.name, Utils.randomString(8))
            }
        }
    }

    private fun getValue(key: String): String {
        val value = sharedPreferences.getString(key, "")
        return value ?: ""
    }
}