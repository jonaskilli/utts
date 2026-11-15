package com.github.jing332.database.entities.plugin

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.github.jing332.database.entities.MapConverters
import com.github.jing332.database.entities.systts.AudioParams
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Parcelize
@Entity
@TypeConverters(MapConverters::class)
data class Plugin(
    @Transient
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    var isEnabled: Boolean = false,

    @ColumnInfo(defaultValue = "0")
    var version: Int = 0,

    var name: String = "",
    var pluginId: String = "",
    var author: String = "",

    @ColumnInfo(defaultValue = "")
    var iconUrl: String = "",

    var code: String = "",

    // authKey: { label: "验证KEY", hint: "填入用于验证身份的KEY"}
    @ColumnInfo(name = "defVars", defaultValue = "{}")
    var defVars: Map<String, Map<String, String>> = mutableMapOf(),


    @ColumnInfo(defaultValue = "{}")
    var userVars: Map<String, String> = mutableMapOf(),

    // 索引 排序用
    @ColumnInfo(name = "order", defaultValue = "0")
    var order: Int = 0,

    // 插件级音频参数（全局调节该插件下所有发音人）
    @ColumnInfo(defaultValue = "{}")
    var audioParams: AudioParams = AudioParams(),

    // 插件JS已自行处理该参数（如把语速发给服务端变速），
    // 朗读时本机 Sonic 不再叠加该项，避免双重生效
    @ColumnInfo(defaultValue = "0")
    var pluginHandlesSpeed: Boolean = false,

    @ColumnInfo(defaultValue = "0")
    var pluginHandlesVolume: Boolean = false,

    @ColumnInfo(defaultValue = "0")
    var pluginHandlesPitch: Boolean = false,

    /**
     * 该插件允许同时存在多个引擎实例（2026-11-15，任务书-UTTS引擎实例池）。
     *
     * 判定口径：插件 JS 在每次请求里自建连接/局部状态，模块级无共享 WS/忙标志，
     * 两个请求同时打过去互不干扰 → true。有模块级 `var ws`/`isWsReady`/全局 callback
     * 的 WS 型插件（豆包/猫箱/千问型）第二笔请求会重置第一笔的连接（1da91f21 实锤
     * 「请等待当前请求完成/Broken pipe」）→ 必须 false。
     *
     * 默认 false = 安全优先：未判定的新插件一律走现行单实例路径，
     * 行为与引入本列之前逐字节一致。11-15 实读 23 项插件 JS 判定的白名单在
     * MIGRATION_35_36 里灌入。
     */
    @ColumnInfo(defaultValue = "0")
    var allowParallel: Boolean = false,
) : Parcelable {
    val mutableUserVars: MutableMap<String, String>
        get() = userVars as MutableMap<String, String>

    override fun toString(): String {
        return "name: $name, pluginId: $pluginId, author: $author, version: $version, isEnabled: $isEnabled"
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other) && other is Plugin && other.userVars == userVars
    }
}