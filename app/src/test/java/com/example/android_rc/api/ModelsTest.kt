package com.example.android_rc.api

import org.junit.Test

import org.junit.Assert.*
import com.example.android_rc.api.Models.*

class ModelsTest {
    @Test
    fun fromJson_register() {
        val json = """{"type":"register","roomId":"123456","clientId":"abcdefgh"}"""
        val actual = fromJson(json)

        val expected = RegisterAction(roomId = "123456", clientId = "abcdefgh")
        assertEquals(expected, actual)
    }
    @Test
    fun toJson_register() {
        val model = RegisterAction(roomId = "123456", clientId = "abcdefgh")
        val actual = toJson(model)

        val expected = """{"type":"register","roomId":"123456","clientId":"abcdefgh"}"""
        assertEquals(expected, actual)
    }

    @Test
    fun fromJson_accept() {
        val json = """{"type":"accept","iceServers":[],"isExistUser":false,"isExistClient":true}"""
        val actual = fromJson(json)

        val expected = AcceptEvent(iceServers = listOf(), isExistUser = false, isExistClient = true)
        assertEquals(expected, actual)
    }
    @Test
    fun toJson_accept() {
        val model = AcceptEvent(iceServers = listOf(), isExistUser = false, isExistClient = true)
        val actual = toJson(model)

        val expected = """{"type":"accept","iceServers":[],"isExistUser":false,"isExistClient":true}"""
        assertEquals(expected, actual)
    }



    @Test
    fun fromJson_candidate() {
        val json = """{"type":"candidate","ice":{"candidate":"candidate text data","sdpMLineIndex":1}}"""
        val actual = fromJson(json)

        val expected = CandidateData(ice= IceData(candidate = "candidate text data", sdpMLineIndex = 1, sdpMid = null))
        assertEquals(expected, actual)
    }
    @Test
    fun toJson_candidate() {
        val model = CandidateData(ice= IceData(candidate = "candidate text data", sdpMLineIndex = 1, sdpMid = null))
        val actual = toJson(model)

        val expected = """{"type":"candidate","ice":{"candidate":"candidate text data","sdpMLineIndex":1}}"""
        assertEquals(expected, actual)
    }


    @Test
    fun fromJson_bye() {
        val json = """{"type":"bye"}"""
        val actual = fromJson(json)

        val expected = ByeEvent()
        assertEquals(expected, actual)
    }
    @Test
    fun toJson_bye() {
        val model = ByeEvent()
        val actual = toJson(model)

        val expected = """{"type":"bye"}"""
        assertEquals(expected, actual)
    }
}