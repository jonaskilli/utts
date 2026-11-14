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
    ): Result<Unit, SynthesisError>
}

interface SynthesisCallback {
    fun onSynthesizeStart(sampleRate: Int)
    fun onSynthesizeAvailable(audio: ByteArray)
}
