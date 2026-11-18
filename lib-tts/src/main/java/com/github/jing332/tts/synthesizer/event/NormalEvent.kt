package com.github.jing332.tts.synthesizer.event

import com.github.jing332.tts.synthesizer.BgmSource
import com.github.jing332.tts.synthesizer.RequestPayload

sealed interface NormalEvent : Event {
    data class Request(
        val request: RequestPayload,
        val retries: Int,
    ) : NormalEvent

    data class ReadAllFromStream(
        val request: RequestPayload,
        val size: Int,
        val costTime: Long,
    ) : NormalEvent

    data class HandleStream(
        val request: RequestPayload,
    ) : NormalEvent

    data class DirectPlay(val request: RequestPayload) : NormalEvent
    data class StandbyTts(
        val request: RequestPayload,
        val fromTag: String = "",
        val toTag: String = "",
        val reason: String = "retry",

        /** 切换到的是性别/中性兜底（而非用户显式备用），日志用词不同 */
        val isFallback: Boolean = false,
    ) : NormalEvent
    // 11-18：带 isPreload——预热与播放的重试上限文案分叉（用户令）：
    // 预热=「由实时合成兜底」，播放=「跳过」照旧。
    data class RequestCountEnded(val isPreload: Boolean = false) : NormalEvent

    data class BgmCurrentPlaying(val source: BgmSource) : NormalEvent

    /**
     * 11-18 用户令：预热批里被朗读规则**整段过滤**的段（纯符号段）。
     *
     * 为什么要单独一条事件：这种段既没有请求也没有音频，日志页默认视图里整段凭空消失，
     * 段序直接从 16/115 跳到 17/115（真机 14:29 实锤）。用户要「每一段正文都能对上」，
     * 且这条不能只留在「朗读规则」混入视图里——默认日志就要能看见。
     * 带 [paraIndex]（0-based，与日志页段号口径一致：显示时 +1）才能确认它属于哪一段：
     * 预热是提前备货，事件时间必然早于该段播放，只按时间看不出归属。
     */
    data class SegmentFiltered(
        val paraIndex: Int,
        val text: String,
    ) : NormalEvent
}