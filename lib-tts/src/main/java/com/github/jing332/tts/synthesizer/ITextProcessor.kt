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
        // 11-17（竞速/慢钥改造）：本笔是不是后台预热批次。
        // 规则据此决定 AI 调用要不要「加赛」（同 prompt 两把钥并发、先成者胜）：
        // 预热是备货，慢就慢，不该把后台请求翻倍；只有播放请求值得多花一把。
        // 默认 false = 调用方没带（旧路径/第三方），与改造前逐字节一致。
        isPreload: Boolean = false,
    ): Result<List<TextSegment>, com.github.jing332.tts.error.TextProcessorError>
}