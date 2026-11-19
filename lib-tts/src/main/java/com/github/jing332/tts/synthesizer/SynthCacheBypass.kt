package com.github.jing332.tts.synthesizer

/**
 * 一次性「跳过合成缓存」放行名单（11-20，日志栏喇叭「重新合成」）。
 *
 * ## 为什么需要它
 * 日志栏喇叭的「重新合成」要让用户听到**新**音频。但 `requestAndProcess` 开头
 * 会先查 `SynthesizedAudioCache`，命中就直接回旧 PCM —— 那样「重新合成」等于
 * 什么都没做，只是把旧音频再播一遍。
 *
 * 删缓存文件也能达到目的，但删除需要**算出缓存键**：键含 sourceKey / voice /
 * locale / speed / volume / pitch / sampleRate 七项，日志页只有文本，算不出来。
 * 所以改成「按文本放行」：日志页点重合成时把该段正文登记进来，下一次请求
 * 文本与之相关即跳过缓存读取。
 *
 * ## 为什么不是「整段精确等值」
 * 关键现实：**开「按句合成」时，送合成器的文本是拆句后的单句，不是整段原文**。
 * 整段等值匹配会大面积失效（长段必拆）。日志页拿到的是整段（请求行 `<b>…</b>`），
 * 而实际请求的是句子，两者只能是「包含」关系。
 *
 * 因此匹配规则：请求文本**被登记的整段包含**即命中（子串判定）。这样子句、
 * 拆句后的每一句都能放行。风险是一直放行整段期间的所有子句——但这恰是用户要的
 * 语义：点「重新合成」= 「这一段的所有音频都重新做一遍」。
 *
 * ## 单次消费与清理
 * 每命中一次就消耗掉对应登记（避免长期放行：若一直放行，用户之后正常收听
 * 该段会反复付合成成本）。登记带时间戳，超过 [TTL_MS] 自动失效——U阅 可能因
 * 切换、缓冲等原因没在合理时间内重发该段请求，过期即放弃，不会挂住内存。
 *
 * ## 并发安全
 * 日志页（主线程）登记、合成器（IO 协程）消费。用 CopyOnWriteArrayList +
 * 每条目自己的 consumed 标志：并发请求同一段时只放行一次（第一笔重合成、
 * 后续照常走缓存），避免同一段被并发重复合成。
 */
object SynthCacheBypass {

    /** 放行有效期：一次「重新合成」点击的合理生命周期 */
    private const val TTL_MS = 3 * 60 * 1000L

    private class Entry(
        val segment: String,
        val at: Long = System.currentTimeMillis(),
        @Volatile var consumed: Boolean = false,
    ) {
        fun expired(now: Long): Boolean = now - at > TTL_MS
    }

    private val entries = java.util.concurrent.CopyOnWriteArrayList<Entry>()

    /** 放行**一段**：该段正文（整段）下属的所有子句下一次合成都不读缓存。 */
    fun arm(segmentText: String) {
        val t = segmentText.trim()
        if (t.isEmpty()) return
        prune()
        // 同一整段重复 arm：覆盖旧的（用户连点两次 = 以最后一次为准，不叠加）
        entries.removeAll { it.segment == t }
        entries.add(Entry(t))
    }

    /**
     * 本次请求是否应跳过缓存读取。
     *
     * @param requestText 本笔请求**实际送合成**的文本（可能是拆句后的单句）
     * @return true = 跳过缓存读取并消费掉该登记
     */
    fun consumeIfMatch(requestText: String): Boolean {
        if (entries.isEmpty()) return false
        val r = requestText.trim()
        if (r.isEmpty()) return false
        prune()
        for (e in entries) {
            if (e.consumed) continue
            // 子句包含在整段里即命中（拆句场景）；整段等值也覆盖
            if (e.segment.contains(r) || r.contains(e.segment)) {
                e.consumed = true
                entries.remove(e)
                return true
            }
        }
        return false
    }

    fun clear() {
        entries.clear()
    }

    private fun prune() {
        val now = System.currentTimeMillis()
        entries.removeAll { it.consumed || it.expired(now) }
    }
}
