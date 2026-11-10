package com.github.jing332.database.entities.systts

import android.os.Parcelable
import androidx.room.ColumnInfo
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class AudioParams(
    var speed: Float = 1f,

    var volume: Float = 1f,

    var pitch: Float = 1f,

    /**
     * 混响开关。11-10 用户令混响功能整体下线（UI 与处理链均已删除），但**字段保留**：
     * SystemTtsGroup 的 audioParams_ 前缀嵌入列已在表里，Room 打开库时会校验 schema，
     * 删字段必须重建整表并升版本，风险远大于收益。现值恒为 false，读写路径已无消费方。
     */
    @ColumnInfo(defaultValue = "0")
    var reverbEnabled: Boolean = false,
) : Parcelable {
    companion object {
        const val FOLLOW = 0f
    }

    val isDefaultValue: Boolean
        get() = speed == 1f && volume == 1f && pitch == 1f

    fun copyIfFollow(followSpeed: Float, followVolume: Float, followPitch: Float): AudioParams {
        return AudioParams(
            if (speed == FOLLOW) followSpeed else speed,
            if (volume == FOLLOW) followVolume else volume,
            if (pitch == FOLLOW) followPitch else pitch
        )
    }

    fun copyIfFollow(params: AudioParams): AudioParams {
        return AudioParams(
            if (speed == FOLLOW) params.speed else speed,
            if (volume == FOLLOW) params.volume else volume,
            if (pitch == FOLLOW) params.pitch else pitch
        )
    }

    fun reset(v: Float) {
        speed = v
        volume = v
        pitch = v
    }

}
