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

/**
 * 一笔系统 TTS 请求的**不可变入参快照**（2026-11-16，并发串值修复）。
 *
 * ## 为什么需要它
 *
 * 段号与批量段列表原先存在 `SystemTtsService` 的 `@Volatile` 成员变量里：
 * `onSynthesizeText` 写入 → `mScope.launch` 起的合成协程异步读出。
 * 预热请求与播放请求几乎同时进入时，后一笔直接覆盖前一筆，协程读到别人的值。
 *
 * 真机实证（2026-11-16 01:26:47.494，两笔请求同一毫秒落地）：
 * 两笔的 `paraTotal` 一个是 105、一个是空，随后同一章的命中请求又都变成 647。
 *
 * 最坏后果（按代码推演，未等真机复现）：
 *  - 播放请求读到预热的 `batchSegments` → 走预热分支，只回 50ms 静音、**不输出音频**（漏读）；
 *  - 预热请求读到 null → 退化成单段合成（白烧一次 API 且缓存没备上）。
 *
 * 所以改成「进入 onSynthesizeText 时就地构造快照，随调用栈一路传下去」，
 * 每个协程只认自己那份，与谁并发都互不影响。
 *
 * @property paraIndex 段号（0-based），-1 = 调用方没带
 * @property paraTotal 本章总段数，0 = 调用方没带
 * @property batchSegments 批量段列表，null = 单段路径
 */
data class RequestSnapshot(
    val paraIndex: Int = -1,
    val paraTotal: Int = 0,
    val batchSegments: List<BatchSegment>? = null,
    /**
     * 11-20：本笔是「后台预加载」，但要走**正常单段合成**（出真实音频）。
     *
     * 为什么需要（用户 11-20 口径）：预加载的语义是「和正常播放一样地把后面几章
     * 合成好」——音频要落进调用方（U阅）自己的缓存，播放时像普通命中一样秒回、
     * 段前标记要亮。而 batch_json 预热只回 50ms 静音、只写 UTTS 的 synth_cache，
     * 调用方拿不到音频 ⇒ 看着「像没加载过」。
     *
     * 区别只在「出不出音频」：
     *   · batchSegments != null → 批量预热，**不出音频**（回静音）；
     *   · preloadOnly = true   → 单段预加载，**正常出音频**。
     * 两者在服务层的待遇完全一致：不破窗、不占播放槽位、不落日志页、可被播放掐。
     */
    val preloadOnly: Boolean = false,
) {
    val isPreload: Boolean get() = batchSegments != null || preloadOnly
}
