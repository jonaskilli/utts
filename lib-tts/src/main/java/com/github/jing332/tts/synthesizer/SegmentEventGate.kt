package com.github.jing332.tts.synthesizer

import com.github.jing332.tts.synthesizer.event.Event

/**
 * 段序事件闸门（2026-11-16，用户令「日志顺序必须跟正文一模一样，调用的顺序也不能错」）。
 *
 * ## 要解决的问题
 *
 * `produceSegments` 并发合成时，每个 worker 协程在**自己完成的那一刻**直接派发日志事件
 * （Request / ReadAllFromStream / Error…）。并发度 2 时段的完成顺序不等于段序，
 * 于是日志文件里的行序天然是「谁先合成完谁先写」。
 *
 * 真机实证（2026-11-16 01:26）：5 笔请求的行序是 15、15、13、14、12，而正文顺序是
 * 12、13、14、15。缓存命中把这点放大得更明显——命中是毫秒级、未命中要 3~8 秒，
 * 重听时命中段全部冲到未命中段前面，日志看上去就是「顺序全乱」。
 *
 * **只靠排序层救不了**：渲染时按 paraIndex 排只能掩盖症状，日志文件本身的顺序、
 * 以及「先请求谁后请求谁」的调用次序仍然是错的。用户要的是源头保序。
 *
 * ## 做法：按段序放行
 *
 * 并发 worker 产生的事件先按**批内段下标**缓冲，不立刻派发；等「按序回填」循环
 * 推进到该段时（那里本来就严格按 0,1,2… 消费产物），再一次性把该段的事件
 * 按真实发生顺序放出去。
 *
 * 于是：并发度任意 → 日志行序恒等于段序；未开并发/单段路径不建闸，行为与改动前完全一致；
 * 段内仍保持真实发生顺序（请求行必在成功行之前，重试链完整）。
 *
 * ## 为什么不按时间戳重排
 *
 * 文件时间戳是「落盘那一刻」的墙钟，不是「这件事该排第几」。改写时间戳会让日志
 * 失去真实发生时间，排查时序问题反而更糟（例如定位 9.6 秒空窗就靠真实时间）。
 * 所以本闸只调整**派发时机**，不动时间戳。
 *
 * ## 线程安全
 *
 * worker 与回填循环可能在不同线程。用 synchronized 保护缓冲（事件很小，
 * 只在 push/flush 时短暂持锁，不覆盖合成过程本身）；派发在锁外做，
 * 因为派发会走到服务层写文件，不该持锁。
 */
class SegmentEventGate {

    /** 段下标 → 已缓冲、尚未放行的事件（段内保持真实发生顺序）。 */
    private val buffers = LinkedHashMap<Int, MutableList<Event>>()

    /** 已放行到的段下标。回填循环保证严格递增，故只需记一个水位。 */
    private var releasedUpTo = -1

    /**
     * 缓冲一条事件，等 [flush] 放行。
     *
     * 非挂起，因为调用点（`AbstractMixSynthesizer.event` 与 DefaultResultProcessor 的派发）
     * 遍布在非挂起上下文里，改成 suspend 会波及整条调用链。
     *
     * @param key 该事件所属的批内段下标（即回填循环里的 index）
     */
    fun push(key: Int, event: Event) {
        val immediate = synchronized(this) {
            // 该段已放行（回填已经过它了）→ 直接放出去，不能憋着，
            // 否则事件会永久滞留（例如回填之后才到的收尾事件）
            if (key <= releasedUpTo) true
            else {
                buffers.getOrPut(key) { ArrayList(4) }.add(event)
                false
            }
        }
        if (immediate) dispatch(event)
    }

    /**
     * 放行段 [key] 的全部事件，并把水位推到 [key]。
     *
     * 由按序回填循环在「该段产物已消费完」时调用——那一刻正是这段在正文里的位置。
     */
    fun flush(key: Int) {
        val pending: List<Event>
        synchronized(this) {
            if (key <= releasedUpTo) return
            releasedUpTo = key
            // 顺带放行所有 <= key 的段：防止某段事件全空、回填跳过它导致水位卡住
            val out = ArrayList<Event>(8)
            val iter = buffers.keys.iterator()
            while (iter.hasNext()) {
                val k = iter.next()
                if (k <= key) {
                    buffers[k]?.let { out.addAll(it) }
                    iter.remove()
                }
            }
            pending = out
        }
        pending.forEach { dispatch(it) }
    }

    /**
     * 收尾：把还没放行的事件按段序全部放出。
     *
     * 正常路径下回填循环会把每段 flush 一遍；这里兜底异常/取消路径，
     * 保证不会因为某段没走到 flush 就把日志吞掉（宁可顺序差一点，不能丢行）。
     */
    fun flushAll() {
        val pending: List<Event>
        synchronized(this) {
            val out = ArrayList<Event>(8)
            buffers.values.forEach { out.addAll(it) }
            buffers.clear()
            pending = out
        }
        pending.forEach { dispatch(it) }
    }

    /** 当前还在缓冲的段数（诊断用）。 */
    fun pendingSegments(): Int = synchronized(this) { buffers.size }

    /**
     * 实际派发出口。由 [AbstractMixSynthesizer.produceSegments] 在创建闸门时注入
     * `context.event?.dispatch`。
     *
     * 用可变属性而不是构造参数：闸门要在合成器内部创建、出口又要指向合成器的
     * context，两边互相依赖，构造参数会形成循环。
     */
    private var dispatch: (Event) -> Unit = {}

    fun setDispatcher(fn: (Event) -> Unit) {
        dispatch = fn
    }
}
