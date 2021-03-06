package com.example.android_rc.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

sealed class Models(val type: ModelsType) {

    companion object {
        private val moshi: Moshi = Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Models::class.java, "type")
                    .withSubtype(RegisterAction::class.java, ModelsType.register.name)
                    .withSubtype(AcceptEvent::class.java, ModelsType.accept.name)
                    .withSubtype(RejectEvent::class.java, ModelsType.reject.name)
                    .withSubtype(OfferAction::class.java, ModelsType.offer.name)
                    .withSubtype(AnswerEvent::class.java, ModelsType.answer.name)
                    .withSubtype(CandidateData::class.java, ModelsType.candidate.name)
                    .withSubtype(PingAction::class.java, ModelsType.ping.name)
                    .withSubtype(PongEvent::class.java, ModelsType.pong.name)
                    .withSubtype(ByeEvent::class.java, ModelsType.bye.name)
            )
            .add(KotlinJsonAdapterFactory())
            .build()

        private val adapter: JsonAdapter<Models> = moshi.adapter(Models::class.java)

        fun fromJson(json: String): Models? {
            return adapter.fromJson(json)
        }

        fun toJson(model: Models): String {
            return adapter.toJson(model)
        }
    }

    @JsonClass(generateAdapter = true)
    data class RegisterAction(
        @Json(name = "roomId") val roomId: String,
        @Json(name = "clientId") val clientId: String
    ) : Models(ModelsType.register)

    @JsonClass(generateAdapter = true)
    data class AcceptEvent(
        @Json(name = "iceServers") val iceServers: List<String>?,
        @Json(name = "isExistUser") val isExistUser: Boolean?,
        @Json(name = "isExistClient") val isExistClient: Boolean
    ) : Models(ModelsType.accept)

    @JsonClass(generateAdapter = true)
    data class RejectEvent(
        @Json(name = "reason") val reason: String
    ) : Models(ModelsType.reject)

    @JsonClass(generateAdapter = true)
    data class OfferAction(
        @Json(name = "sdp") val sdp: String
    ) : Models(ModelsType.offer)

    @JsonClass(generateAdapter = true)
    data class AnswerEvent(
        @Json(name = "sdp") val sdp: String
    ) : Models(ModelsType.answer)

    @JsonClass(generateAdapter = true)
    data class CandidateData(
        @Json(name = "ice") val ice: IceData
    ) : Models(ModelsType.candidate)
    data class IceData(
        @Json(name = "candidate") val candidate: String,
        @Json(name = "sdpMLineIndex") val sdpMLineIndex: Int,
        @Json(name = "sdpMid") val sdpMid: String?
    )

    @JsonClass(generateAdapter = true)
    data class PingAction(
        @Transient val _dummy: String? = null
    ) : Models(ModelsType.ping)

    @JsonClass(generateAdapter = true)
    data class PongEvent(
        @Transient val _dummy: String? = null
    ) : Models(ModelsType.pong)

    @JsonClass(generateAdapter = true)
    data class ByeEvent(
        @Transient val _dummy: String? = null
    ) : Models(ModelsType.bye)

}

enum class ModelsType {
    @Json(name = "register")
    register,
    @Json(name = "accept")
    accept,
    @Json(name = "reject")
    reject,
    @Json(name = "offer")
    offer,
    @Json(name = "answer")
    answer,
    @Json(name = "candidate")
    candidate,
    @Json(name = "ping")
    ping,
    @Json(name = "pong")
    pong,
    @Json(name = "bye")
    bye,
}
