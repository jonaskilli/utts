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

    // ===== 11-22 01:1x（用户令「不要遗留，直接修」）：在飞请求引用计数 =====
    //
    // ## 现场（11-21 真机实锤两例）
    //   21:42:06.968  Engine timeout destroy: 讯飞配音引擎
    //   21:42:08.273  retry: retries=0 … 开始第 1 次重试
    //   21:42:13.645  获取失败：IllegalStateException：Object `PluginJS` not found
    //   21:51:33.352  Engine timeout destroy: 讯飞配音引擎
    //   21:51:34.664  开始第 1 次重试
    //   21:51:39.821  预合成失败（第 9 段）：IllegalStateException：Object `PluginJS` not found
    // 两次都是「引擎刚被销毁 → 挂着它的请求去用 PluginJS → 必然炸」。
    //
    // ## 根因
    // 销毁引擎的三条路（定时过期 / removeEngine / expireAll）**都只看缓存状态，
    // 不看这个引擎此刻是否正被某笔请求握着用**：
    //   · TimedCache 的 schedulePrune 每 60 秒扫一遍，超过 10 分钟未访问就回收；
    //     但「访问」只在 getEngine 那一刻登记——**一笔跑了 12 秒的长请求期间，
    //     缓存时间戳不会续期**，所以完全可能「请求还在用、引擎正好到期被销毁」；
    //   · removeEngine 的触发面更宽（任何一笔 getStream 抛异常，DefaultTtsRequester
    //     就整池 removeEngine），池化插件下会连带销毁**其它 7 路正在跑**的实例。
    // 注释里写的「Engine timeout destroy」看起来像单纯的空闲回收，实际却能把在飞请求
    // 的引擎从脚下撤走——这就是 PluginJS not found 的真正来源。
    //
    // ## 修法（引用计数，最小侵入）
    // 给每个 key 记「当前有几笔请求正握着这个引擎」：
    //   · acquireEngine 时 +1，releaseEngine 时 -1；
    //   · 销毁前检查计数 > 0 ⇒ **本次不销毁，保留缓存并续期**，
    //     等最后一笔释放时再走正常流程（release 里主动判定一次）。
    // 为什么不是「等所有请求跑完才销毁」的完全版：那是把销毁延迟到不确定时刻，
    // 反而让 10 分钟超时语义失效。这里取的是「在飞期间绝不销毁，空闲后立即按原逻辑」。
    private val inFlight = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()

    /** 每个 key 首次被 acquire 的时刻（用于识别计数泄漏，见 onCacheRemove 兜底段）。 */
    private val firstAcquireAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * 在飞计数泄漏兜底窗口：超过这个时长仍「在飞>0」，判定调用方从未 release。
     * 取 180 秒——真长请求最长也就十几秒（真机最长「获取成功 · 12.5s」），
     * 180 秒远超任何正常请求寿命，够宽松到不会误杀。
     */
    private val LEAK_GUARD_MS = 1000L * 180L

    /** 取引擎并在飞计数 +1。调用方必须 releaseEngine(同实例)。 */
    fun acquireEngine(
        context: Context,
        source: TextToSpeechSource,
    ): TextToSpeechProvider<TextToSpeechSource>? {
        val engine = getEngine(context, source) ?: return null
        // 计数挂在**这个实例对应的实际 key** 上。池化时 getEngine 内部按槽位选 key，
        // 这里无法直接得知是哪个槽——故用实例反查（见 keyOfEngine）。
        val key = keyOfEngine(engine) ?: return engine
        inFlight.getOrPut(key) { java.util.concurrent.atomic.AtomicInteger(0) }.incrementAndGet()
        firstAcquireAt.putIfAbsent(key, System.currentTimeMillis())
        return engine
    }

    /** 与 acquireEngine 配对：在飞计数 -1；归零后处理「用完后作废」的实例。 */
    fun releaseEngine(engine: TextToSpeechProvider<TextToSpeechSource>?) {
        if (engine == null) return
        val key = keyOfEngine(engine) ?: run {
            // 引擎已不在缓存里（被 removeEngine 直接销毁了）：若还被标记 stale，清掉标记即可
            return
        }
        val counter = inFlight[key] ?: return
        val left = counter.decrementAndGet()
        if (left > 0) return // 还有别的请求在用，先不动
        inFlight.remove(key)
        // 归零即清时间戳：否则下一次 acquire 会因 putIfAbsent 命中旧值，
        // 让泄漏兜底把一个刚起的新请求误判成「老了 180 秒」。
        firstAcquireAt.remove(key)
        // 11-22 01:1x：归零后补做「removeEngine 时因在飞而推迟的作废」——
        // 否则这个脏实例会一直留在池里继续服务（那等于 removeEngine 失效）。
        if (staleAfterUse.remove(key)) {
            if (cache.remove(key) != null) {
                destroyExecutor.submit {
                    runCatching { engine.onDestroy() }
                        .onFailure { logger.warn(it) { "async engine destroy failed: $key" } }
                }
                logger.atDebug { message = "Engine stale destroy after last release: $key" }
            }
        }
    }

    /** 反查：这个引擎实例当前挂在哪个缓存 key 下（池化时 key 带 #槽号后缀）。 */
    private fun keyOfEngine(engine: TextToSpeechProvider<TextToSpeechSource>): String? {
        // 用 keySet 快照遍历再逐个取值：hutool 的 TimedCache.entries() 返回类型
        // 在 Kotlin 侧有重载歧义（编译实锤 component1/component2/iterator 均 ambiguous），
        // keySet 是 ConcurrentHashMap 键集，语义等价且无歧义。
        for (k in cache.keySet().toList()) {
            if (cache[k] === engine) return k
        }
        return null
    }

    /** 该 key 是否还有请求在飞（销毁前的唯一否决条件）。 */
    private fun hasInFlight(key: String): Boolean =
        (inFlight[key]?.get() ?: 0) > 0

    override fun onCacheRemove(key: String, value: TextToSpeechProvider<TextToSpeechSource>): Boolean {
        // 11-22 01:1x：**在飞请求的引擎绝不销毁**——见上方引用计数注释。
        // 返回 true = 让 TimedCache 把它放回缓存（续期），语义上等价于「这次过期不成立」。
        // 这是修复 PluginJS not found 的关键一行：引擎被从脚下撤走，请求必然炸。
        //
        // ⚠️ 计数泄漏兜底：万一调用方拿到 Response 却从未消费（下游丢弃/提前 return），
        // releaseEngine 永远不会被调到，这个 key 会永久处于「在飞>0」而永不销毁。
        // 兜底策略：放回缓存时把**距上次 acquire 的时间**一并判断——超过一个过期周期
        // 还在飞，说明是泄漏而不是真在跑，直接放行销毁（宁可重建一次，也不能永久占着）。
        val counter = inFlight[key]
        if (counter != null && counter.get() > 0) {
            val since = firstAcquireAt[key] ?: 0L
            val ageMs = System.currentTimeMillis() - since
            if (ageMs < LEAK_GUARD_MS) {
                logger.atDebug {
                    message = "Engine destroy deferred (in-flight=${counter.get()}): $key"
                }
                return true // 放回缓存 = 续期，等最后一笔释放后再正常过期
            }
            // 超过兜底窗口：判为计数泄漏，清掉计数与时间戳，按正常流程销毁。
            // ⚠️ 写法与 atDebug 不同：warn{} 的 lambda 是参数形式（带 Throwable 重载），
            // 不能写 `message =`（那是 atDebug 的 builder 风格）。
            logger.warn {
                "Engine in-flight counter suspected leak (age=${ageMs}ms), forcing destroy: $key"
            }
            inFlight.remove(key)
            firstAcquireAt.remove(key)
        }
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
            // 11-22 01:1x（用户令「不要遗留，直接修」）：**别家正在跑的实例不能陪葬**。
            //
            // removeEngine 的调用面比想象中宽——最典型的是 DefaultTtsRequester：
            // 任何一笔 getStream 抛异常就整池 removeEngine。池化插件（allowParallel）
            // 下有 N 个实例并发服务，其中一路失败不代表另外 N-1 路也有问题；
            // 旧代码会把它们一并销毁，正在跑的请求立刻撞「Object PluginJS not found」。
            //
            // 这里的口径与 onCacheRemove 略有不同：removeEngine 是**主动作废**
            // （超时/换配置/插件更新），该重建的还是要重建；但只销毁**当前没在飞**的实例，
            // 在飞的那几个保留——等它们的 releaseEngine 归零后，下一次 getEngine
            // 命中缓存仍会拿到旧实例（脏状态可能残留），故这里同时打标记，
            // 让它们在释放时被清掉（见 releaseEngine 的 staleAfterUse）。
            if (hasInFlight(key)) {
                staleAfterUse.add(key)
                logger.atDebug {
                    message = "Engine remove deferred, marked stale (in-flight>0): $key"
                }
                continue
            }
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
     * 被 removeEngine 标记为「用完后作废」的 key（11-22 01:1x）。
     * 这些实例在飞时不销毁，等最后一笔释放时再清掉——既不让在飞请求撞 PluginJS，
     * 也不让脏实例长期留在池里继续服务。
     */
    private val staleAfterUse = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

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