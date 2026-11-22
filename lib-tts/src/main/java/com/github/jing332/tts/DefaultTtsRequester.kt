package com.github.jing332.tts

import com.github.jing332.tts.error.RequesterError
import com.github.jing332.tts.synthesizer.ITtsRequester
import com.github.jing332.tts.synthesizer.SystemParams
import com.github.jing332.tts.synthesizer.TtsConfiguration
import com.github.jing332.tts.speech.EngineState
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException

class DefaultTtsRequester(
    var context: SynthesizerContext,
) : ITtsRequester {
    private val logger = KotlinLogging.logger("DefaultTtsRequester")

    /**
     * 11-22 11:0x：把失败的真实异常原文打进 debug.log。
     *
     * ## 为什么必须加
     * 用户 10:1x 装机实测出了两笔失败，**日志页显示对了**（网络类→「获取失败：连接失败」、
     * 引擎类→「源错误：朗读引擎失效」），但翻遍 debug.log **找不到任何异常原文**：
     * 既没有 `PluginJS not found`、也没有 `Engine not initialized`、连 `IllegalState` 都没有。
     *
     * 结果就是：只能看到「引擎失效」这个**结论**，看不到它是怎么失效的。判断根因时
     * 只能靠代码推测——而推测已经错过一次（把「失败后的清理销毁」误读成「销毁导致失败」）。
     *
     * 归因链本身是通的（SystemTtsService.isSourceError 只看 cause 链），但那条链只在
     * 内存里跑一次，没人把顶层异常与 cause 链落盘。这里补上，只打**失败路径**，
     * 成功路径一行不加（不污染日志、不影响性能）。
     *
     * 输出格式刻意紧凑（单行、cause 用 ` <- ` 串起来、每个只取 simpleName + message
     * 前 120 字），因为 reason 列会被日志页折叠显示，太长反而看不清。
     */
    private fun dumpFailure(stage: String, source: Any?, e: Throwable) {
        val chain = buildString {
            var cur: Throwable? = e
            var depth = 0
            while (cur != null && depth < 8) {
                if (depth > 0) append(" <- ")
                append(cur.javaClass.simpleName)
                val m = cur.message
                if (!m.isNullOrBlank()) {
                    append("(").append(m.take(120).replace('\n', ' ')).append(")")
                }
                cur = cur.cause
                depth++
            }
        }
        logger.warn { "TTS失败原文 [$stage] source=${source ?: "-"} | $chain" }
    }

    override suspend fun request(
        params: SystemParams, tts: TtsConfiguration,
    ): Result<ITtsRequester.Response, RequesterError> {
        // 11-22 01:1x（用户令「不要遗留，直接修」）：改用 acquire/release 引用计数取引擎。
        //
        // 为什么必须配对：CachedEngineManager 的销毁只看缓存状态，不看「有没有请求正握着
        // 这个引擎用」。真机实证：一笔跑了 12 秒的长请求期间，缓存时间戳不续期，
        // 定时清理完全可能把引擎从它脚下撤走 ⇒ 紧接着就是
        // 「IllegalStateException: Object `PluginJS` not found」（21:42:13 / 21:51:39 两例）。
        //
        // 配对面（三条 return 路径都要释放）：
        //   · onInit 失败：立即释放；
        //   · 同步播放型（isSyncPlay）：引擎的生命周期覆盖到 syncPlay 回调**执行完**，
        //     故在回调内部 finally 释放——不能在这里直接放，那样引擎可能在回调前被销毁；
        //   · 流式型：本次 request 返回流就结束了「持有」，由调用方接管读流——
        //     读流期间引擎仍要被保护，故把释放挂到流关闭上（LatchedInputStream）。
        val engine =
            CachedEngineManager.acquireEngine(context.androidContext, tts.source) ?: return Err(
                RequesterError.StateError("engine ${tts.source} not found")
            )

        if (engine.state != EngineState.Initialized) {
            try {
                engine.onInit()
            } catch (e: Exception) {
                CachedEngineManager.releaseEngine(engine)
                return Err(RequesterError.RequestError(e))
            }
        }

        // 合并 TtsConfiguration.audioParams 到 SystemParams
        // 确保 TtsRepository 中计算的最终音频参数被使用
        val mergedParams = params.copy(
            speed = tts.audioParams.speed,
            volume = tts.audioParams.volume,
            pitch = tts.audioParams.pitch
        )

        return if (engine.isSyncPlay(tts.source)) {
            Ok(
                ITtsRequester.Response(
                    callback = ITtsRequester.ISyncPlayCallback {
                        try {
                            engine.syncPlay(mergedParams, tts.source)
                        } finally {
                            // 同步播放型：音频已经进了播放器，持有到此为止
                            CachedEngineManager.releaseEngine(engine)
                        }
                    }
                )
            )
        } else {
            try {
                Ok(
                    // 流式型：把释放挂到「流被关闭」上——读流期间引擎必须保持存活，
                    // 读完了才允许它过期/被销毁。用包装流而不是 try/finally，
                    // 是因为本函数返回后控制权就交给调用方了。
                    ITtsRequester.Response(
                        stream = LatchedInputStream(
                            engine.getStream(mergedParams, tts.source)
                        ) { CachedEngineManager.releaseEngine(engine) }
                    )
                )
            } catch (e: CancellationException) {
                dumpFailure("cancelled", tts.source, e)
                CachedEngineManager.releaseEngine(engine)
                throw e
            } catch (e: Exception) {
                dumpFailure("getStream", tts.source, e)
                // ===== 11-22 11:0x：**网络故障不销毁引擎**（用户实报「引擎在在飞期被销毁」）=====
                //
                // 真机日志（10:09:00 纳米AI36）：
                //   10:09:00.525  jsPost ✕ api.so.n.cn NetConnectException（网络断了）
                //   10:09:00.528  Engine remove deferred, marked stale (in-flight>0)
                //   10:09:00.528  Engine timeout destroy
                //   10:09:01.538  开始第 1 次重试 → 2.2s 后成功
                //
                // 也就是说：一次**纯网络抖动**，把本来健康的引擎销毁了，下一笔必须重建
                // （真机重建 1~4 秒）。重试能成功恰恰证明引擎没坏——坏的是网络。
                //
                // 这与「源错误 / 获取失败」的二分口径完全一致（用户 09:3x 拍板）：
                //   · 源错误 = 朗读源自己坏了，重试不自愈 → **才需要销毁重建**；
                //   · 获取失败 = 网络/超时/返回格式，重试多半自愈 → 引擎无需动。
                // 判据与 SystemTtsService.isSourceError 同源（都是「这类错重试会不会自愈」），
                // 这里只是把那个已经定好的口径用到「要不要销毁引擎」这个动作上。
                //
                // 为什么以前一刀切销毁：注释历史写的是「发生网络或其他异常时重置引擎状态
                // 并强制销毁，确保下次 onInit 重新执行，实现网络恢复后的自愈」。但网络恢复
                // 根本不需要重建引擎——引擎内部状态与网络无关，重建纯属浪费 1~4 秒。
                //
                // 回归风险兜底：万一是「引擎真坏了但表现为网络异常」这种边缘情况，
                // 下一笔仍会失败 → 走重试链 → 重试时会再次进这里，此时若判为引擎类则销毁。
                // 最坏多花一次重试，不会卡死。
                val engineBroken = isEngineFailure(e)
                if (engineBroken) {
                    // 引擎实例级失效（PluginJS 对象没了 / Engine not initialized）：
                    // 必须重建，否则重试拿到同一个脏实例仍会失败。
                    logger.warn {
                        "engine failure detected, destroying engine: ${tts.source.getKey()} | ${e.message?.take(100)}"
                    }
                    CachedEngineManager.removeEngine(tts.source)
                } else {
                    logger.info {
                        "transient failure (network/timeout), engine kept alive: ${tts.source.getKey()} | ${e.javaClass.simpleName}"
                    }
                }
                CachedEngineManager.releaseEngine(engine)
                Err(RequesterError.RequestError(e))
            }
        }
    }

    /**
     * 判断这笔失败是否属于「引擎实例自己坏了」（需要销毁重建）。
     *
     * 判据必须与 `SystemTtsService.isSourceError` **完全同源**，否则会出现
     * 「日志页说源错误、但引擎没重建」或者反过来的错位。
     *
     * 分两步，顺序不能反：
     *   ① **先排除网络类**——网络故障即使跨了 Rhino 边界也归「获取失败」，
     *      IOException/UnknownHost/Connect/Timeout/SSL + drake 的 Net* 全部排除；
     *   ② 再认引擎失效两条固定文案（二者都是 IllegalStateException，
     *      只能靠 message 区分：类名相同）。
     *
     * 返回 true = 引擎坏了要重建；false = 网络/超时等可自愈故障，引擎保留。
     */
    private fun isEngineFailure(t: Throwable): Boolean {
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth++ < 16) {
            // ① 网络层优先排除（与 SystemTtsService 同一份清单）
            when (cur) {
                is java.net.UnknownHostException,
                is java.net.ConnectException,
                is java.net.SocketTimeoutException,
                is java.net.SocketException,
                is javax.net.ssl.SSLException,
                is java.io.InterruptedIOException,
                -> return false
            }
            when (cur.javaClass.simpleName) {
                "NetUnknownHostException",
                "NetConnectException",
                "NetSocketTimeoutException",
                "NetworkingException",
                "HttpFailureException",
                -> return false
            }
            // ② 引擎失效固定文案（与 SystemTtsService.isSourceError 逐字一致）
            if (cur is IllegalStateException) {
                val m = cur.message.orEmpty()
                if (m.contains("PluginJS` not found") || m.contains("Engine not initialized")) {
                    return true
                }
            }
            cur = cur.cause
        }
        // 不在上面两类里的未知异常：保守起见**不销毁**（保引擎，靠重试链自愈）。
        // 理由：销毁的代价是重建 1~4 秒，而误不销毁的代价只是多一次重试；
        // 且真·引擎失效下次仍会命中 ② 被销毁。
        return false
    }

    override fun destroy() {
    }
}

/**
 * 11-22 01:1x（用户令「不要遗留，直接修」）：给流式引擎加「读流期间保活」的包装。
 *
 * ## 为什么需要它
 * [DefaultTtsRequester.request] 拿到流就返回了，此时「谁还握着引擎」这件事
 * 传给了调用方（AbstractMixSynthesizer 要读完整个流才结束）。如果不做保护，
 * 这段时间里 CachedEngineManager 的定时清理可能把引擎销毁——
 * 表现就是读到一半/紧接着下一笔撞 `Object PluginJS not found`。
 *
 * ## 实现
 * 包装底层流，**只在本流被 close（或读完自动 close）时**回调一次释放，
 * 用 AtomicBoolean 保证幂等：正常读完 close、异常 close、双层 close 都只释放一次，
 * 否则计数被多减一次会让「在飞=0」提前成立，保护失效。
 *
 * 不重写 read 逻辑：所有读操作原样委托，包装层不引入缓冲/状态，行为与直连一致。
 */
private class LatchedInputStream(
    private val delegate: java.io.InputStream,
    private val onRelease: () -> Unit,
) : java.io.InputStream() {
    private val released = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun releaseOnce() {
        if (released.compareAndSet(false, true)) {
            runCatching { onRelease() }
        }
    }

    override fun read(): Int = delegate.read()

    override fun read(b: ByteArray): Int = delegate.read(b)

    override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)

    override fun skip(n: Long): Long = delegate.skip(n)

    override fun available(): Int = delegate.available()

    override fun close() {
        try {
            delegate.close()
        } finally {
            releaseOnce()
        }
    }

    override fun markSupported(): Boolean = delegate.markSupported()

    override fun mark(readlimit: Int) = delegate.mark(readlimit)

    override fun reset() = delegate.reset()
}
