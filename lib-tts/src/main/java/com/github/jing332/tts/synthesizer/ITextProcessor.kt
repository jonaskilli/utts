package com.github.jing332.tts.synthesizer

import android.content.Context
import com.github.michaelbull.result.Result

interface ITextProcessor {
    fun init(
        context: Context,
        configs: Map<Long, TtsConfiguration>,
    ): Result<Unit, com.github.jing332.tts.error.TextProcessorError>

    fun process(
        text: String,
        forceConfig: TtsConfiguration? = null,
        // 11-14：本笔请求的段落位置（U阅 Bundle 带 paraIndex/paraTotal，见 SystemParams
        // 之外的独立参数）。产出的每个 TextSegment 原样带上，最终随「请求音频」日志落盘，
        // 日志页据此给**每张请求卡**标「N/M」。默认 -1/0 = 调用方没带，不标。
        paraIndex: Int = -1,
        paraTotal: Int = 0,
    ): Result<List<TextSegment>, com.github.jing332.tts.error.TextProcessorError>
}