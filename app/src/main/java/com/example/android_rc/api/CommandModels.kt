package com.example.android_rc.api

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

sealed class CommandModels(val type: CommandModelsType) {

    companion object {
        private val moshi: Moshi = Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(CommandModels::class.java, "type")
                    .withSubtype(ConfigData::class.java, CommandModelsType.config.name)
                    .withSubtype(TouchData::class.java, CommandModelsType.touch.name)
            )
            .add(KotlinJsonAdapterFactory())
            .build()

        private val adapter: JsonAdapter<CommandModels> = moshi.adapter(CommandModels::class.java)

        fun fromJson(json: String): CommandModels? {
            return adapter.fromJson(json)
        }

        fun toJson(model: CommandModels): String {
            return adapter.toJson(model)
        }
    }

    @JsonClass(generateAdapter = true)
    data class ConfigData(
        val data: ConfigEventData
    ) : CommandModels(CommandModelsType.config)

    @JsonClass(generateAdapter = true)
    data class TouchData(
        val data: List<TouchEventData>
    ) : CommandModels(CommandModelsType.touch)

}

enum class CommandModelsType {
    @Json(name = "config")
    config,

    @Json(name = "touch")
    touch,
}

data class ConfigEventData(
    val height: Float,
    val width: Float
)

data class TouchEventData(
    val x: Float,
    val y: Float,
    val t: Int
)

class PositionConverter(private val videoWidth: Int, private val videoHeight: Int, private val scale: Float) {
    private var widthRatio = 1.0F
    private var heightRatio = 1.0F

    fun setClientSize(width: Float, height: Float) {
        if (width == 0.0F || height == 0.0F) {
            clear()
            return
        }
        widthRatio = videoWidth * scale / width
        heightRatio = videoHeight * scale / height
        Log.i("PositionConverter", "onTouchData: [Rect] (${videoWidth * scale}, ${videoHeight * scale}) -> ($width, $height)")
    }

    fun calcPosition(x: Float, y: Float): Position {
        Log.i("PositionConverter", "onTouchData: [Pos] ($x, $y) -> (${x * widthRatio}, ${y * heightRatio})")
        return Position(x * widthRatio, y * heightRatio)
    }

    private fun clear() {
        widthRatio = 1.0F
        heightRatio = 1.0F
    }
}

data class Position(
    val x: Float,
    val y: Float
)

