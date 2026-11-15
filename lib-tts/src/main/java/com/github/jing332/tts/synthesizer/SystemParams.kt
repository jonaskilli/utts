package com.github.jing332.tts.synthesizer

data class SystemParams(
    val text: String = "",
    val speed: Float = 1f,
    val volume: Float = 1f,
    val pitch: Float = 1f,
    val requestTimeout: Long = 5000L
)

/**
 * 批量合成里的一段（2026-11-15，任务书-U阅批量提交多段）。
 *
 * ## 为什么需要它
 *
 * 现状是「U阅 一段一次请求」：`SystemTtsService.onSynthesizeText` 收一段文本 →
 * `synthesize()` → 内部切句 → 串行合成。而 `onSynthesizeText` 开头有**强力破窗**
 * （新请求取消旧请求），所以「U阅 并发提交多笔」走不通，第 2 笔必掐第 1 笔。
 *
 * 要并行只能一笔请求内带多段：调用方用 `PARAM_BATCH_JSON` 传段列表，
 * 合成器把各段分别过一遍文本处理器拿到逐段音色，再交给已有的
 * `produceSegments()`（内含 `prefetchConcurrency` + 引擎实例池）并行合成。
 *
 * ## 为什么不带音色
 *
 * 音色由 UTTS 自己的朗读规则引擎按角色分配（`TextProcessor` 的 isMultiVoice 分支），
 * 调用方（U阅）从不指定音色——实测 `forceConfigId` 96/96 全为 null。
 * 所以这里只带文本与段号，音色在合成器内部由规则引擎决定。
 *
 * @property text 该段正文（已含调用方的规则替换结果）
 * @property paraIndex 段号（0-based，与单段请求的 `para_index` 同口径），-1=调用方没带
 */
data class BatchSegment(
    val text: String,
    val paraIndex: Int = -1,
)
