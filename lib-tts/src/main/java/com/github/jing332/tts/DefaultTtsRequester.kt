package com.github.jing332.tts

import com.github.jing332.tts.error.RequesterError
import com.github.jing332.tts.synthesizer.ITtsRequester
import com.github.jing332.tts.synthesizer.SystemParams
import com.github.jing332.tts.synthesizer.TtsConfiguration
import com.github.jing332.tts.speech.EngineState
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CancellationException

class DefaultTtsRequester(
    var context: SynthesizerContext,
) : ITtsRequester {
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
                CachedEngineManager.releaseEngine(engine)
                throw e
            } catch (e: Exception) {
                CachedEngineManager.releaseEngine(engine)
                // removeEngine 内部会异步销毁引擎，防止 onDestroy/onStop 同步阻塞卡住
                CachedEngineManager.removeEngine(tts.source)
                Err(RequesterError.RequestError(e))
            }
        }
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
