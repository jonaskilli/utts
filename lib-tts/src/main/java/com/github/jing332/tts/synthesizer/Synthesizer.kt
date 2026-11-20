package com.github.jing332.tts.synthesizer

import com.github.jing332.tts.error.SynthesisError
import com.github.michaelbull.result.Result


interface Synthesizer {
    val isSynthesizing: Boolean
    suspend fun init()
    suspend fun destroy()


    suspend fun synthesize(
        params: SystemParams,
        forceConfigId: Long? = null,
        callback: SynthesisCallback,
        // 11-14：段落位置（用户令「每个请求音频日志都标 1/5」）。U阅 每段一次请求
        // 时随入口传入，一路透传到日志行；-1/0 = 调用方没带，全链不标。
        paraIndex: Int = -1,
        paraTotal: Int = 0,
        // 11-15（任务书-U阅批量提交多段）：一次请求内带多段并并行合成。
        // 为空 = 走原有的「单段 params.text」路径，行为完全不变。
        // 为何必须是「一笔多段」而不是「多笔并发」：onSynthesizeText 开头的破窗
        // 会让新请求取消旧请求，多笔并发必互相掐断（详见 BatchSegment 注释）。
        batchSegments: List<BatchSegment>? = null,
        // 11-20：本笔是「后台预加载」——不落日志页、不占播放槽位，但仍正常出音频。
        // 与 batchSegments（批量预热，只回静音）的区别见 RequestSnapshot.preloadOnly。
        emitHitLog: Boolean = true,
        isPreload: Boolean = false,
    ): Result<Unit, SynthesisError>
}

interface SynthesisCallback {
    fun onSynthesizeStart(sampleRate: Int)
    fun onSynthesizeAvailable(audio: ByteArray)
}
