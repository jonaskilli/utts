package com.github.jing332.tts

import android.content.Context
import com.github.jing332.database.dbm
import com.github.jing332.database.entities.systts.source.PluginTtsSource
import com.github.jing332.database.entities.systts.source.TextToSpeechSource
import com.github.jing332.tts.speech.TextToSpeechProvider
import com.github.jing332.tts.speech.plugin.PluginTtsProvider
import com.github.jing332.tts.util.AbstractCachedManager
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.Executors

object CachedEngineManager :
    AbstractCachedManager<String, TextToSpeechProvider<TextToSpeechSource>>(
        timeout = 1000L * 60L * 10L, // 10 min
        delay = 1000L * 60 // 1 min
    ) {
    private val logger = KotlinLogging.logger("CachedEngineManager")

    // 专用线程池用于异步销毁引擎，防止 onDestroy/onStop 中的同步脚本调用
    // （如 Rhino invokeMethod）阻塞重试流程
    private val destroyExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "EngineDestroy").apply { isDaemon = true }
    }

    override fun onCacheRemove(key: String, value: TextToSpeechProvider<TextToSpeechSource>): Boolean {
        logger.atDebug { message = "Engine timeout destroy: $key" }
        value.onDestroy()

        return super.onCacheRemove(key, value)
    }

    fun getEngine(context: Context, source: TextToSpeechSource): TextToSpeechProvider<TextToSpeechSource>? {
        val baseKey = baseKeyOf(source)

        // 引擎实例池（2026-11-15，任务书-UTTS引擎实例池）：
        // 只有插件显式标记 allowParallel 才分池，其余（含全部未判定插件）
        // 走原来的单 key 路径，行为与引入池化前逐字节一致。
        val poolSize = if (isParallelSafe(source)) parallelPoolSize() else 1
        if (poolSize <= 1) {
            return getOrCreate(baseKey, context, source, 0)
        }

        // 轮转分配：并发 N 路 fetcher 各自取下一个实例，天然均匀。
        // 不做「借用/归还」计数——合成是短任务，计数器在多路取消/破窗时会漏减，
        // 漏减即永久少一路可用实例（比分配不均更糟）。轮转无状态、不会泄漏。
        // floorMod 而非 and：计数器溢成负数时取值仍连续（and 会跳变）。
        //
        // 不按音色分池：同一批内 tts 配置是 configs.values.random() 逐段挑的，
        // 按音色分池会让实例数随配置数膨胀（N 配置 × 8 路），且池化收益就没了。
        // 音色串音由引擎侧的临界区解决（source 挪进 mMutex 锁内赋值，见
        // TtsPluginEngineV2.getAudio）——同实例上「设 source → 调 JS」不可被插队。
        val slot = Math.floorMod(parallelCursor.getAndIncrement(), poolSize)
        return getOrCreate("$baseKey#$slot", context, source, slot)
    }

    /**
     * 固定取池内 0 号实例：试听/预览路径专用。
     *
     * 试听是「即点即播」的单笔请求，若参与轮转会随机占掉一路朗读实例
     * （用户听书时点试听，旁白就少一路并行）。固定 0 号后，试听与朗读
     * 抢用同一实例的关系**与池化前完全一致**（池化前也只有这一个），
     * 即验收第 5 条「试听路径不受影响」。
     */
    fun getEngineForPreview(
        context: Context,
        source: TextToSpeechSource,
    ): TextToSpeechProvider<TextToSpeechSource>? {
        val baseKey = baseKeyOf(source)
        val poolSize = if (isParallelSafe(source)) parallelPoolSize() else 1
        return if (poolSize <= 1) getOrCreate(baseKey, context, source, 0)
        else getOrCreate("$baseKey#0", context, source, 0)
    }

    private fun getOrCreate(
        key: String,
        context: Context,
        source: TextToSpeechSource,
        slot: Int,
    ): TextToSpeechProvider<TextToSpeechSource>? {
        val cachedEngine = cache[key]
        return if (cachedEngine == null) {
            val engine = SpeechServiceFactory.createEngine(context, source, slot) ?: return null
            cache.put(key, engine)
            engine
        } else {
            cachedEngine
        }
    }

    /** 不含实例号的缓存 key：池化时 N 个实例共享这个前缀。 */
    private fun baseKeyOf(source: TextToSpeechSource): String =
        source.getKey() + ";" + source.javaClass.simpleName

    /**
     * 该源是否允许同时存在多个引擎实例。
     * 只看插件表标记；规则来源与判定依据见 DatabaseManager.MIGRATION_35_36。
     * 非插件源（本机引擎等）一律单实例。
     */
    private fun isParallelSafe(source: TextToSpeechSource): Boolean {
        val pluginSource = source as? PluginTtsSource ?: return false
        val plugin = pluginSource.plugin ?: runCatching {
            dbm.pluginDao.getMetaByPluginId(pluginSource.pluginId)
        }.getOrNull()
        return plugin?.allowParallel == true
    }

    /**
     * 实例数跟随「预取并发数」（用户 11-15 定稿：不再多一个设置项）。
     * 由 MixSynthesizer.init 用 context.cfg.prefetchConcurrency 装配；
     * 未装配时按 1 处理，即不分池（与引入池化前一致）。
     */
    @Volatile
    var poolSizeProvider: () -> Int = { 1 }

    private fun parallelPoolSize(): Int =
        runCatching { poolSizeProvider() }.getOrDefault(1).coerceIn(1, MAX_POOL_SIZE)

    private const val MAX_POOL_SIZE = 8
    private val parallelCursor = java.util.concurrent.atomic.AtomicInteger(0)

    fun removeEngine(source: TextToSpeechSource) {
        // 池化插件要把整个池一起清掉：只 remove 一个槽位的话，
        // 下一次轮转会命中其余旧实例（脏状态仍在），等于没修。
        val baseKey = baseKeyOf(source)
        val keys = cache.keySet().filter { it == baseKey || it.startsWith("$baseKey#") }
        for (key in keys) {
            val engine = cache[key] ?: continue
            // 先从缓存移除，确保下次 getEngine 创建新实例
            cache.remove(key)
            // 异步销毁：onDestroy 内部的 onStop 会同步调用脚本（Rhino invokeMethod），
            // 可能因脚本卡死或后台线程占用而永久阻塞，同步等待会导致重试无法触发
            destroyExecutor.submit {
                runCatching { engine.onDestroy() }
                    .onFailure { logger.warn(it) { "async engine destroy failed: $key" } }
            }
        }
    }

    /**
     * 按插件失效全部合成引擎缓存：插件变量(userVars)/代码更新后调用。
     * 缓存是「访问即续期」的 TimedCache，频繁试听的旧引擎永不过期，
     * 不主动清掉的话，改完变量主界面试听仍会提示“请先填写变量”。
     */
    fun removeByPluginId(pluginId: String) {
        // hutool 的缓存值/条目迭代器只读(CacheValuesIterator.remove 直接抛
        // UnsupportedOperationException)，removeAll{}/迭代中 remove 会崩溃；
        // keySet() 是底层 ConcurrentHashMap 键集,先快照再逐键 remove
        val keys = cache.keySet().toList()
        for (key in keys) {
            val value = cache[key] ?: continue
            if (value is PluginTtsProvider && value.plugin.pluginId == pluginId) {
                destroyExecutor.submit {
                    runCatching { value.onDestroy() }
                        .onFailure { logger.warn(it) { "async engine destroy failed: $pluginId" } }
                }
                cache.remove(key)
            }
        }
    }

    fun expireAll() {
        logger.atDebug { message = "Expire all cached engine" }
        val keys = cache.keySet().toList()
        for (key in keys) {
            val value = cache[key]
            if (value != null) {
                destroyExecutor.submit {
                    runCatching { value.onDestroy() }
                        .onFailure { logger.warn(it) { "async engine destroy failed: $key" } }
                }
            }
            cache.remove(key)
        }
    }
}