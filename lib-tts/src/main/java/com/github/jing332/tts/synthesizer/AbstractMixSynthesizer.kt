package com.github.jing332.tts.synthesizer

import androidx.annotation.MainThread
import android.os.SystemClock
import com.drake.net.utils.withMain
import com.github.jing332.common.utils.StringUtils
import com.github.jing332.common.utils.toByteArray
import com.github.jing332.database.entities.systts.source.PluginTtsSource
import com.github.jing332.tts.CachedEngineManager
import com.github.jing332.tts.SynthesizerContext
import com.github.jing332.tts.speech.EngineState
import com.github.jing332.tts.error.RequesterError
import com.github.jing332.tts.error.SynthesisError
import com.github.jing332.tts.error.TextProcessorError
import com.github.jing332.tts.speech.EmptyInputStream
import com.github.jing332.tts.synthesizer.event.ErrorEvent
import com.github.jing332.tts.synthesizer.event.Event
import com.github.jing332.tts.synthesizer.event.NormalEvent
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream

abstract class AbstractMixSynthesizer() : Synthesizer {
    companion object {
        // 32→128：缓冲按块计，128块×2048B≈256KB，24kHz下约5.5秒音频，
        // 高倍速听书（1-3x）时盖插件网络等待，消除段间接缝；停止时通道直接丢弃，不影响停止响应
        const val PROCUDE_CAPACITY: Int = 128

        /**
         * 生成静音 PCM 音频数据（不含 WAV 头）
         *
         * 注意：TTS 回调已通过 onSynthesizeStart(sampleRate) 声明了音频格式，
         * 因此 onSynthesizeAvailable 应发送裸 PCM 数据，不能带 WAV 头，
         * 否则 44 字节的 RIFF 头会被当成音频数据播放产生噪音。
         *
         * @param sampleRate 采样率，必须与 onSynthesizeStart 声明的一致
         * @param durationMs 持续时间，默认 100ms
         * @return PCM 格式的字节数组（16bit 单声道）
         */
        fun createSilentPcmAudio(sampleRate: Int = 16000, durationMs: Int = 100): ByteArray {
            val numChannels = 1
            val bitsPerSample = 16
            val numSamples = (sampleRate * durationMs) / 1000
            val dataSize = numSamples * numChannels * (bitsPerSample / 8)
            return ByteArray(dataSize) // 全 0 即静音
        }
    }

    private val logger: KLogger
        get() = context.logger

    /**
     * 产物收集器：并发管线里 fetcher 的成品先进这里，由按序回填协程统一交给播放通道。
     */
    private class PayloadCollector : PayloadSink {
        val payloads = mutableListOf<ChannelPayload>()
        override suspend fun send(payload: ChannelPayload) {
            payloads.add(payload)
        }

        override fun trySendBlocking(payload: ChannelPayload) {
            payloads.add(payload)
        }
    }


    abstract val context: SynthesizerContext

    abstract val textProcessor: ITextProcessor
    abstract val ttsRequester: ITtsRequester
    abstract val streamProcessor: IResultProcessor
    abstract val repo: ITtsRepository
    abstract val bgmPlayer: IBgmPlayer

    var isInitialized: Boolean = false
        private set

    private var maxSampleRate: Int = 16000

    // All enabled configs
    private var mConfigs: Map<Long, TtsConfiguration> = mapOf()
        set(value) {
            field = value
            maxSampleRate =
                mConfigs.values.maxByOrNull { it.audioFormat.sampleRate }?.audioFormat?.sampleRate
                    ?: 16000
        }

    /**
     * 派发一条事件。
     *
     * 11-16（段序保序）：并发合成时段完成顺序 ≠ 段序，而以前每个 worker 在自己完成
     * 那一刻就直接派发 —— 日志文件的行序于是变成「谁先合成完谁先写」。真机实证
     * 01:26 五笔请求行序 15、15、13、14、12（正文顺序 12、13、14、15）。
     *
     * 带 [request] 的事件走该请求自带的闸门（段序缓冲，等按序回填时再放行）；
     * 无请求的全局事件（仓库错、BGM 等）直发。闸门与段号**随载荷传递**，
     * 绝不用共享成员变量——那正是 11-16 上午刚修掉的串值形态。
     */
    private fun event(event: Event, request: RequestPayload? = null) {
        val g = request?.eventGate
        if (g != null) g.push(request.segmentKey, event)
        else context.event?.dispatch(event)
    }

    /**
     * @return null means presetConfigId is not found from database
     */
    private suspend fun textProcess(
        params: SystemParams,
        presetConfigId: Long?,
        // 11-14：段落位置透传（U阅 Bundle 带入，见 ITextProcessor.process 注释）
        paraIndex: Int = -1,
        paraTotal: Int = 0,
        // 11-15（任务书-U阅批量提交多段）：一次请求内带多段。
        // 每段仍**独立**调用一次 textProcessor.process——这是刻意的：
        // 朗读规则引擎的分析缓存、跨章预标注、段落口径都以「段」为单位，
        // 拼成一大段会让缓存键变化、角色识别上下文错乱。
        // 这里只负责「把 N 段的分析结果汇成一个列表」，并行发生在下游 produceSegments。
        batchSegments: List<BatchSegment>? = null,
    ): Result<List<TextSegment>, SynthesisError> {
        val presetConfig: TtsConfiguration? = presetConfigId?.run { repo.getTts(this) }
        if (presetConfigId != null && presetConfig == null) {
            return Err(SynthesisError.PresetMissing(presetConfigId))
        }

        // 批量路径：逐段跑文本处理，按段序拼接结果（顺序即播放顺序，不可乱）
        if (batchSegments != null) {
            if (batchSegments.isEmpty()) return Ok(emptyList())
            val merged = ArrayList<TextSegment>(batchSegments.size)
            for (segment in batchSegments) {
                if (segment.text.isBlank()) continue
                val segmentIndex = if (segment.paraIndex >= 0) segment.paraIndex else paraIndex
                var failure: SynthesisError? = null
                textProcessor
                    .process(segment.text, presetConfig, segmentIndex, paraTotal)
                    .onSuccess { list ->
                        // 与单段路径同口径：过滤纯静音段后再并入
                        merged.addAll(list.filterNot { StringUtils.isSilent(it.text) })
                    }
                    .onFailure { err: TextProcessorError ->
                        event(ErrorEvent.TextProcessor(err))
                        failure = SynthesisError.TextHandle(err)
                    }
                // 单段文本处理失败即整批失败：与单段请求的失败语义一致，
                // 绝不「跳过这一段继续」，否则会静默漏读一句
                failure?.let { return Err(it) }
            }
            logger.info {
                "batch textProcess: segments=${batchSegments.size}, unitCount=${merged.size}"
            }
            return Ok(merged)
        }

        textProcessor
            .process(params.text, presetConfig, paraIndex, paraTotal)
            .onSuccess { list -> return Ok(list.filterNot { StringUtils.isSilent(it.text) }) }
            .onFailure { err: TextProcessorError ->
                event(ErrorEvent.TextProcessor(err))
                return Err(SynthesisError.TextHandle(err))
            }

        return Ok(emptyList())
    }

    /**
     * 缓存键（2026-11-15）：段文本 + 发音源 + 音色 + **最终生效参数** + 输出采样率。
     *
     * 最终参数取 `config.audioParams` 而非 [SystemParams]——三层乘积
     * （全局 × 配置 × 插件）在 resolveTtsPlayback 里已算进 audioParams，
     * 而 U阅 传进来的 SystemParams 里 speed/volume/pitch 恒为 1.0
     * （日志实测：`SystemParams(... speed=1.0, volume=1.0, pitch=1.0)`）。
     * 用错这一层会让「语速改了却命中旧音频」。
     */
    private fun cacheKeyOf(text: String, cfg: TtsConfiguration): String =
        SynthesizedAudioCache.keyOf(
            text = text,
            sourceKey = cfg.source.getKey() + "|" + cfg.source.javaClass.simpleName,
            voice = cfg.source.voice,
            locale = cfg.source.locale,
            speed = cfg.audioParams.speed,
            volume = cfg.audioParams.volume,
            pitch = cfg.audioParams.pitch,
            sampleRate = maxSampleRate,
        )

    private fun cacheKeyOf(segment: TextSegment, params: SystemParams): String =
        cacheKeyOf(segment.text, segment.tts)

    /** 单段预热：已有缓存直接算成功；否则合成并写缓存。 */
    private suspend fun preloadOne(segment: TextSegment, params: SystemParams): Boolean {
        val key = cacheKeyOf(segment, params)
        if (SynthesizedAudioCache.get(key) != null) return true

        val sink = CollectSink()
        try {
            requestAndProcess(
                sink, params.copy(text = segment.text), segment.tts,
                roleName = segment.roleName,
                bookName = segment.bookName,
                chapterTitle = segment.chapterTitle,
                paraIndex = segment.paraIndex,
                paraTotal = segment.paraTotal,
                // 预热不出日志卡片（见 requestAndProcess 的 emitHitLog）
                emitHitLog = false,
                // 11-16：关键是这一个 —— 只关 emitHitLog 拦不住「未命中」那条路，
                // 它照样发 Request/ReadAllFromStream，于是预热段在日志页冒充播放卡
                //（真机 02:12:10–21 实证：5 张预热卡挤在用户正听的卡前面）。
                isPreload = true,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "preload failed: ${segment.text.take(20)}" }
            return false
        }

        val pcm = sink.toByteArrayOrNull() ?: return false
        SynthesizedAudioCache.put(key, pcm)
        return true
    }

    /**
     * 批量预热（2026-11-15，任务书-U阅批量提交多段 · 路 C）：并发合成 N 段并写入缓存，
     * **不输出音频**。返回成功预热（含本就命中缓存）的段数。
     *
     * 并发度复用 `prefetchConcurrency`，走和播放同一条 requestAndProcess 管线
     * （重试/备用/引擎池全部继承）。失败只记日志、不算致命——预热是优化，
     * 预热失败的段后续仍会由正常播放请求现场合成。
     */
    private suspend fun preloadSegments(
        segments: List<TextSegment>,
        params: SystemParams,
    ): Int {
        if (segments.isEmpty()) return 0
        val concurrency = context.cfg.prefetchConcurrency().coerceIn(1, 8)
        logger.info {
            "preloadSegments: concurrency=$concurrency, segments=${segments.size}"
        }
        val gate = Semaphore(concurrency)
        return coroutineScope {
            segments
                .map { seg ->
                    async(Dispatchers.IO) {
                        gate.withPermit { preloadOne(seg, params) }
                    }
                }
                .map { it.await() }
                .count { it }
        }
    }

    /**
     * @return null means request failed, [EmptyInputStream] means direct play
     *
     */
    private suspend fun requestInternal(
        request: RequestPayload,
        playCallback: suspend (ITtsRequester.ISyncPlayCallback) -> Unit,
    ): InputStream? {
        val result = try {
            withTimeout(context.cfg.requestTimeout()) {
                ttsRequester.request(request.params, request.config)
            }
        } catch (e: TimeoutCancellationException) {
            event(ErrorEvent.RequestTimeout(request), request)
            return null
        } catch (e: CancellationException) {
            throw e
        }

        result.onSuccess { resp ->
            resp.onCallback {
                playCallback(it)
                return EmptyInputStream
            }.onStream { ins ->
                return ins
            }
        }.onFailure {
            when (it) {
                is RequesterError.RequestError -> {
                    event(ErrorEvent.Request(request, it.error), request)
                }

                is RequesterError.StateError -> {
                    event(ErrorEvent.Request(request, IllegalStateException(it.message)), request)
                }
            }

            return null
        }

        return null
    }


    /** 分句阶段致命错误的内部载体：穿过 produceSegments 抛给 executeSynthesis 转为合成错误。 */
    private class PayloadErrorException(val err: SynthesisError) : Exception(err.toString())

    /**
     * 产物出口的最小接口：把 [requestAndProcess] 从 SendChannel 解耦——
     * 基线路径直接对接播放通道；并发管线对接 PayloadCollector（先收集后按序回填）。
     */
    private interface PayloadSink {
        suspend fun send(payload: ChannelPayload)
        fun trySendBlocking(payload: ChannelPayload)
    }

    /**
     * 预热用收集器（2026-11-15）：把合成产物聚成一个 ByteArray，不写播放通道。
     * 只收 Bytes；DirectPlayCallback（本机引擎同步播放型插件）与预热语义冲突——
     * 这种插件本来就没有「提前合成好放一边」的产物，预热直接算作失败跳过。
     */
    private class CollectSink : PayloadSink {
        private val out = java.io.ByteArrayOutputStream()
        private var unsupported = false

        override suspend fun send(payload: ChannelPayload) {
            when (payload) {
                is ChannelPayload.Bytes -> out.write(payload.data)
                // 同步播放型：预热无法预先生成音频，标记后由调用方丢弃
                is ChannelPayload.DirectPlayCallback -> unsupported = true
                is ChannelPayload.Error -> throw PayloadErrorException(payload.err)
            }
        }

        override fun trySendBlocking(payload: ChannelPayload) {
            when (payload) {
                is ChannelPayload.Bytes -> out.write(payload.data)
                is ChannelPayload.DirectPlayCallback -> unsupported = true
                is ChannelPayload.Error -> throw PayloadErrorException(payload.err)
            }
        }

        fun toByteArrayOrNull(): ByteArray? =
            if (unsupported) null else out.toByteArray().takeIf { it.isNotEmpty() }
    }

    /** 播放通道适配器：行为与旧「requestAndProcess 直写 channel」完全一致。 */
    private class ChannelSink(private val channel: SendChannel<ChannelPayload>) : PayloadSink {
        override suspend fun send(payload: ChannelPayload) = channel.send(payload)
        override fun trySendBlocking(payload: ChannelPayload) {
            channel.trySendBlocking(payload)
        }
    }

    private suspend fun requestAndProcess(
        sink: PayloadSink,
        params: SystemParams,
        config: TtsConfiguration,
        retries: Int = 0,
        maxRetries: Int = context.cfg.maxRetryTimes(),
        prefetchedStream: InputStream? = null,
        prefetchedCostMs: Long = 0,
        roleName: String = "",
        failoverFromTag: String? = null,
        // v1.7 书/章：随片段一路带到 RequestPayload，最终进日志行。
        // 重试/切备用的递归调用必须原样透传，否则失败链上的那条日志会丢掉书章
        bookName: String = "",
        chapterTitle: String = "",
        // 11-13：请求实例 id（空串=自己生成）。重试/备用递归必须原样透传同一 id，
        // 否则同一条失败链在日志里会拆成两张卡。
        reqId: String = "",
        // 11-14：段落位置（用户令「每个请求音频日志都标 1/5」）。随片段透传，
        // 重试/备用递归原样带——失败链上的日志段号不能丢。
        paraIndex: Int = -1,
        paraTotal: Int = 0,
        // 11-15 晚：预热路径传 false——预热只是备货，不该在日志页生成卡片。
        emitHitLog: Boolean = true,
        // 11-16：本笔属于后台预热批次。见 RequestPayload.isPreload 的注释——
        // 单靠 emitHitLog 拦不住**未命中**那条路（它照样发 Request/ReadAllFromStream），
        // 于是预热段在日志页冒充播放卡。这里显式标到载荷上，由服务层统一丢弃。
        isPreload: Boolean = false,
        // 11-16（段序保序）：并发批次的段序闸门 + 本笔段下标，随载荷透传。
        // 重试/备用递归原样带（同一笔仍属同一段，事件必须仍排在同一位置）。
        eventGate: SegmentEventGate? = null,
        segmentKey: Int = -1,
    ) {
        val request = RequestPayload(
            params, config, roleName, failoverFromTag, bookName, chapterTitle,
            reqId = reqId.ifEmpty { "r" + System.identityHashCode(params).toString(36) + System.nanoTime().toString(36).takeLast(6) },
            paraIndex = paraIndex,
            paraTotal = paraTotal,
            isPreload = isPreload,
            eventGate = eventGate,
            segmentKey = segmentKey,
        )

        // 11-15（路 C）：合成结果缓存命中即直接产出，不再走插件请求。
        // 只在本笔的**首次**尝试查（retries==0）：重试时不查，否则缓存里的旧音频
        // 会盖住真实的失败，让重试链永远「成功」。
        // 播放与预热共用同一条管线，所以这里一处接入，两条路径都生效。
        if (retries == 0) {
            val cachedPcm = SynthesizedAudioCache.get(cacheKeyOf(params.text, config))
            if (cachedPcm != null) {
                logger.info { "cache hit: ${params.text.take(20)}" }
                if (emitHitLog) {
                    // 11-15 晚补：命中也要出「请求音频 + 获取成功」两张行——
                    // 否则命中段在日志页上完全不存在，用户看到的第一张卡是下一个现场合成的段，
                    // 误判成「起点跳到页尾」（实测 21:23 段 24–27 全命中、首卡是段 28）。
                    // fromCache=true 让服务端多落一行「命中合成缓存」标明来路；耗时记 0.0s。
                    val hitRequest = request.copy(fromCache = true)
                    event(NormalEvent.Request(hitRequest, 0), hitRequest)
                    event(NormalEvent.ReadAllFromStream(hitRequest, cachedPcm.size, 0L), hitRequest)
                }
                sink.trySendBlocking(ChannelPayload.Bytes(cachedPcm))
                return
            }
        }
        suspend fun retry() {
            CachedEngineManager.removeEngine(config.source)
            delay(context.cfg.retryDelay())
            val toggleTryValue = context.cfg.toggleTry()
            logger.debug {
                "retry: retries=$retries, toggleTry=$toggleTryValue, maxRetries=$maxRetries, hasStandby=${config.standbyConfig != null}"
            }
            return if (config.standbyConfig != null && toggleTryValue <= retries + 1) {
                val fromTag = config.speechInfo.tag
                val toTag = config.standbyConfig?.speechInfo?.tag ?: ""
                logger.info { "standby triggered: toggleTry=$toggleTryValue, retries=$retries, fromTag=$fromTag, toTag=$toTag" }
                val standbyRequest = request.copy(config = config.standbyConfig)
                event(NormalEvent.StandbyTts(
                    standbyRequest,
                    fromTag = fromTag,
                    toTag = toTag,
                    reason = "retry",
                    isFallback = config.standbyIsFallback,
                ), standbyRequest)
                // 带上「从哪个标签转来」：切换后的请求已被上面一条日志说明了来路，
                // 服务层不能再把它当成「规则分析失败降级」重复报一次。
                requestAndProcess(
                    sink, params, config.standbyConfig, 0, maxRetries,
                    roleName = roleName, failoverFromTag = fromTag,
                    bookName = bookName, chapterTitle = chapterTitle,
                    reqId = request.reqId,
                    paraIndex = paraIndex, paraTotal = paraTotal,
                    // 11-16：预热的失败链也不能在日志里冒成播放卡
                    isPreload = isPreload,
                )
            } else {
                val next = retries + 1
                // 重试时在原文末尾追加可配置的字符（次数 = 重试次数），
                // 用于绕过部分插件对相同文本的缓存/去重逻辑，提高重试成功率。
                // 仅对插件合成生效，避免影响其它引擎的输出内容。
                val append = context.cfg.retryAppendText()
                val retryParams = if (config.source is PluginTtsSource && append.isNotEmpty()) {
                    params.copy(text = params.text + append.repeat(next))
                } else {
                    params
                }
                requestAndProcess(
                    sink, retryParams, config, next, maxRetries,
                    roleName = roleName, failoverFromTag = failoverFromTag,
                    bookName = bookName, chapterTitle = chapterTitle,
                    reqId = request.reqId,
                    paraIndex = paraIndex, paraTotal = paraTotal,
                    isPreload = isPreload,
                )
            }
        }

        if (retries > maxRetries) {
            event(NormalEvent.RequestCountEnded)
            // 兜底发音人(duihua/duihuaA/duihuaB)或音效(localSound)失败时记录错误日志
            // 括号1-4、narration 不在此列；记录日志后仍按用户配置的 restartOnMaxRetryMode 处理
            val curTag = config.speechInfo.tag
            val isFallbackOrSound = curTag in listOf("duihua", "duihuaA", "duihuaB")
                || curTag.startsWith("localSound")
            if (isFallbackOrSound) {
                logger.error { "兜底/音效标签失败: tag=$curTag, text=${params.text.take(20)}" }
                event(ErrorEvent.Request(request, IllegalStateException("功能标签($curTag)兜底发音人重试失败")), request)
            }
            when (context.cfg.restartOnMaxRetryMode()) {
                1 -> { // 不生成空音频直接重启
                    logger.warn { "max retries exceeded, restarting app directly..." }
                    Runtime.getRuntime().exit(0)
                }
                2 -> { // 生成空音频后重启
                    val silentAudio = createSilentPcmAudio(maxSampleRate, durationMs = 100)
                    sink.trySendBlocking(ChannelPayload.Bytes(silentAudio))
                    logger.warn { "max retries exceeded, restarting app after empty audio..." }
                    Runtime.getRuntime().exit(0)
                }
                else -> { // 0 = 关闭，生成空音频但不重启
                    val silentAudio = createSilentPcmAudio(maxSampleRate, durationMs = 100)
                    sink.trySendBlocking(ChannelPayload.Bytes(silentAudio))
                }
            }
            return
        }

        logger.debug { "start request: $retries, ${params}, ${config}" }
        event(NormalEvent.Request(request, retries), request)

        // 优先使用预取的流，避免重复请求；重试时 prefetchedStream=null 走正常请求路径
        // 请求段耗时单独计量：ByteArray型插件(豆包/千问)的下载发生在 requestInternal 内部，
        // 流式插件的下载发生在后续读流阶段，两段相加才是完整的真实耗时(获取成功日志)
        val reqStart = SystemClock.elapsedRealtime()
        val stream = prefetchedStream ?: requestInternal(request, playCallback = {
                logger.debug { "send direct play callback..." }
                sink.send(ChannelPayload.DirectPlayCallback(request, it))
            })
        val requestCostMs = if (prefetchedStream != null)
            prefetchedCostMs
        else
            SystemClock.elapsedRealtime() - reqStart

        if (stream == null) return retry() // request failed
        else if (stream is EmptyInputStream) return // direct play

        // 流处理（解码/读取）不在 requestInternal 的 withTimeout 范围内，
        // 网络流的 read() 可能无限阻塞导致既不成功也不失败、卡住无法重试。
        // 这里用超时保护，超时后关闭流打断阻塞 IO 并触发重试。
        val streamTimeout = maxOf(context.cfg.requestTimeout() * 3, 30_000L)
        val processResult = try {
            withTimeout(streamTimeout) {
                streamProcessor.processStream(
                    ins = stream,
                    request = request,
                    targetSampleRate = maxSampleRate,
                    callback = { pcm -> sink.trySendBlocking(ChannelPayload.Bytes(pcm.toByteArray())) },
                    requestCostMs = requestCostMs,
                )
            }
        } catch (e: TimeoutCancellationException) {
            runCatching { stream.close() }
            event(ErrorEvent.RequestTimeout(request), request)
            return retry()
        } catch (e: CancellationException) {
            throw e
        }

        processResult.onFailure { e ->
            event(ErrorEvent.ResultProcessor(request, e), request)
            return retry()
        }
    }

    /**
     * 预取并发数（2026-11-13，任务书-UTTS预取并发数）：
     * 请求内分句后，N 个 fetcher 协程「领号」并发请求/解码，产物按序回填到播放通道。
     * 保序铁律：payload 只在下面的按序回填循环里进播放通道，fetcher 之间互不越位；
     * 前一句未回填完，后一句的成品也排队等着，绝不跳（a2866a5 教训）。
     *
     * 并发安全模型（无需按插件设门，源码核实 2026-11-13）：
     * - 同插件请求在 TtsPluginEngineV2.getAudio 的引擎级 mMutex 串行执行 JS——
     *   Rhino 单脚本引擎 + 每次调用独立桥接流/连接，不存在跨请求共享传输状态，不会串音；
     *   整包型插件（豆包/千问 ByteArray）因此无并行收益（但安全）；
     * - HTTP 流式插件（Fish 等 V1 getAudio 返回 BufferedInputStream）：JS 调用与
     *   连接建立在锁内完成，下载消费在锁外 —— 并发窗口真实存在，收益目标即此类。
     * - 不同插件（不同引擎实例/不同 CachedEngineManager key）天然互不相干。
     */
    private suspend fun produceSegments(
        channel: SendChannel<ChannelPayload>,
        params: SystemParams,
        list: List<TextSegment>,
    ) {
        val concurrency = context.cfg.prefetchConcurrency().coerceIn(1, 8)
        // 11-13：并发数落日志——用户报障时从 system_tts.log 直接确认本次配置，
        // 不用再靠「请求时间戳重叠」反推（S4 实测：三路配 2 路时肉眼根本看不出来）
        logger.info { "produceSegments: concurrency=$concurrency, segments=${list.size}" }
        // N=1：保持与旧「串行 for」完全一致的基线路径（验收第2条），直写播放通道
        if (concurrency <= 1 || list.size <= 1) {
            val sink = ChannelSink(channel)
            for ((index, segment) in list.withIndex()) {
                requestAndProcess(
                    sink, params.copy(text = segment.text), segment.tts,
                    roleName = segment.roleName,
                    bookName = segment.bookName,
                    chapterTitle = segment.chapterTitle,
                    paraIndex = segment.paraIndex,
                    paraTotal = segment.paraTotal,
                )
                insertSegmentPause(channel, index, list.size)
            }
            return
        }

        // 领号通道：按序发 0,1,2…；fetcher 领到号才能处理对应段 → 并发上限=并发数
        val indexCh = Channel<Int>(capacity = concurrency)
        // 同插件并发门（11-13）：WS/攒发型插件（豆包、猫箱等）的桥接流在 invoke 返回后
        // 仍由 JS 异步填充，同插件的下一笔请求会重置其 WS/忙标志 → 「请等待当前请求完成」
        // 「Broken pipe」。规则：同一插件在本批内按段序串行——第 i 段须等它之前最近一个
        // 同插件段「消费完」才能发请求；不同插件仍并行（Fish 等 V1 流式收益不受影响）。
        //
        // 11-15 修正（引擎实例池）：本门按 pluginId 一律生效，会让池化完全失效——
        // 同插件第二段仍要等第一段「消费完」才发请求，多开的引擎实例根本拿不到并行请求。
        // 现在只对**不可池化**插件保留本门（它们才是 1da91f21 要防的对象）；
        // 插件标记 allowParallel=true 的（每请求自建连接、无共享状态）直接跳过，
        // 让 N 个引擎实例真正并行。判据与白名单见 DatabaseManager.MIGRATION_35_36。
        val samePluginPrev = arrayOfNulls<Int>(list.size)
        run {
            val lastByPlugin = HashMap<String, Int>()
            list.forEachIndexed { i, seg ->
                val pluginSource = seg.tts.source as? PluginTtsSource ?: return@forEachIndexed
                val pid = pluginSource.pluginId
                // 池化插件不分实例号串行：它们的并行是安全的（判定依据见上）
                val parallelSafe = pluginSource.plugin?.allowParallel == true
                if (parallelSafe) return@forEachIndexed
                lastByPlugin[pid]?.let { samePluginPrev[i] = it }
                lastByPlugin[pid] = i
            }
        }
        val consumed = List(list.size) { CompletableDeferred<Unit>() }
        // 每段独立产物信箱：fetcher 的成品只进 box[i]，谁先完成都不影响顺序
        val boxes = List(list.size) { Channel<ChannelPayload?>(capacity = Channel.UNLIMITED) }
        // 11-16（段序保序）：日志事件也走「按段下标缓冲、按序回填放行」——
        // 与产物信箱同一套保序思路。以前产物有序、日志无序，用户看到的就是
        // 「声音顺序对，日志顺序全乱」。这里让两者共用同一个顺序事实：段下标。
        val eventGate = SegmentEventGate()
        eventGate.dispatch = { ev -> context.event?.dispatch(ev) }
        // 独立 scope：fetcher 失败不传染回填循环（SupervisorJob），随 finally 一并取消
        val fetcherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val fetchers = List(concurrency) { worker ->
            fetcherScope.launch(CoroutineName("SynthPrefetch #$worker")) {
                for (index in indexCh) { // 领号
                    val segment = list[index]
                    val box = boxes[index]
                    // 同插件串行门：等前一个同插件段消费完再发请求
                    samePluginPrev[index]?.let { prev ->
                        runCatching { consumed[prev].await() }
                    }
                    val collector = PayloadCollector()
                    try {
                        requestAndProcess(
                            collector, params.copy(text = segment.text), segment.tts,
                            roleName = segment.roleName,
                            bookName = segment.bookName,
                            chapterTitle = segment.chapterTitle,
                            paraIndex = segment.paraIndex,
                            paraTotal = segment.paraTotal,
                            // 11-16：事件进闸门，按段下标缓冲，等回填到本段再放行
                            eventGate = eventGate,
                            segmentKey = index,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // requestAndProcess 内部已含重试/兜底/静音占位收尾，这里只挡异常逃逸
                        logger.error(e) { "prefetch worker #$worker segment #$index failed" }
                    }
                    if (collector.payloads.isEmpty()) {
                        // 无产物（该段直接播放/被破窗清掉）：信箱保持明确终态，按序回填不空等
                        box.close()
                    } else {
                        collector.payloads.forEach { box.trySend(it) }
                        box.close()
                    }
                }
            }
        }
        // 发号
        val feeder = fetcherScope.launch(CoroutineName("SynthPrefetch #feeder")) {
            try {
                for (index in list.indices) indexCh.send(index)
                indexCh.close()
            } catch (e: Exception) {
                indexCh.close(e)
            }
        }

        // 按序回填：逐段消费信箱，段间停顿在此插入（保持批尾气口语义）
        try {
            for ((index, box) in boxes.withIndex()) {
                for (payload in box) {
                    when (payload) {
                        is ChannelPayload.Error -> {
                            // 文本处理等致命错误：终止一切并交由 executeSynthesis 返回 Err
                            throw PayloadErrorException(payload.err)
                        }
                        else -> channel.send(requireNotNull(payload))
                    }
                }
                // 11-16（段序保序）：本段产物已按序发出，现在把它的日志事件也放行。
                //
                // 时机刻意选在「box 消费完」之后：此刻该段的请求行、成功行、
                // 重试链全部已进闸门（worker 收尾才 close box），一次按真实发生顺序
                // 放出去，段内顺序完整、段间严格递增。于是日志行序 ≡ 段序 ≡ 播放顺序。
                eventGate.flush(index)
                // 该段直接播放（EmptyInputStream 无 Bytes 产物）时信箱为空，停顿照插
                insertSegmentPause(channel, index, list.size)
                // 通知同插件门：本段已消费完，可放行下一个同插件段
                consumed[index].complete(Unit)
            }
            fetchers.forEach { it.join() }
            feeder.join()
        } finally {
            // ⚠️ 顺序：先掐 worker（停止继续产生事件），再 flushAll 兜底。
            // 反过来的话 worker 可能在 flushAll 之后才把事件推进闸门，
            // 那段日志就永远滞留在缓冲里没人放行（丢行）。
            // 无论正常收尾、致命错误还是外部取消（onStop 破窗），都掐掉 fetcher 与发号。
            indexCh.close()
            fetcherScope.cancel()
            // 11-16：兜底——把还没放行的事件按段序全部吐出。
            // 正常路径下回填循环已把每段 flush 过一遍，这里只剩取消/异常时的残尾。
            eventGate.flushAll()
        }
    }

    private suspend fun insertSegmentPause(
        channel: SendChannel<ChannelPayload>, index: Int, total: Int,
    ) {
        // 段间停顿：每段音频之后插入固定时长静音PCM(含批次最后一段)
        // 直写通道，不经过Sonic变速/静音跳过管线，停顿=设置的固定墙钟时长
        // 末段也插：阅读App常按句投喂、纯旁白段整段一个角色，批内往往只有
        // 1段，仅在批内多段间插入会永远不触发；段尾停顿充当批间气口，
        // 下一批到达时本批被强制收尾(onStop破窗)，停顿自然截断不叠加
        val pauseMs = context.cfg.segmentPauseMs()
        if (pauseMs > 0) {
            logger.info { "segment pause: insert ${pauseMs}ms after segment ${index + 1}/$total" }
            channel.trySendBlocking(
                ChannelPayload.Bytes(
                    createSilentPcmAudio(maxSampleRate, durationMs = pauseMs)
                )
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun executeSynthesis(
        params: SystemParams, callback: SynthesisCallback, presetConfigId: Long?,
        // 11-14：段落位置透传（见 synthesize 注释）
        paraIndex: Int = -1,
        paraTotal: Int = 0,
        // 11-15：批量段列表（见 textProcess 注释），null=单段路径
        batchSegments: List<BatchSegment>? = null,
    ): Result<Unit, SynthesisError> = coroutineScope {
        if (mConfigs.isEmpty()) return@coroutineScope Err(SynthesisError.ConfigEmpty)

        logger.debug { "onSynthesizeStart: sampleRate=${maxSampleRate}" }

        val channel =
            produce<ChannelPayload>(CoroutineName("Synthesis producer"), PROCUDE_CAPACITY) {
                textProcess(params, presetConfigId, paraIndex, paraTotal, batchSegments)
                    .onSuccess { list ->
                        try {
                            // 11-15：批量请求 = 预热请求。合成结果写进文本缓存，
                            // **不回传真实音频**（调用方拿到的是一段极短静音，
                            // 它只要知道「这批备好了」即可；真实音频留给后续单段请求命中）。
                            // 为什么不能回传真实音频：系统 TTS 一次只产出一个文件，
                            // N 段音频拼成连续流后调用方无法按段切分落盘。
                            if (batchSegments != null) {
                                val ok = preloadSegments(list, params)
                                logger.info {
                                    "preload done: ok=$ok/${list.size}, cache=${SynthesizedAudioCache.stats()}"
                                }
                                channel.trySendBlocking(
                                    ChannelPayload.Bytes(
                                        createSilentPcmAudio(maxSampleRate, durationMs = 50)
                                    )
                                )
                            } else {
                                produceSegments(channel, params, list)
                            }
                        } catch (e: PayloadErrorException) {
                            // 分句阶段致命错误（如文本处理器报错）：作为合成错误交给消费端
                            channel.send(ChannelPayload.Error(e.err))
                        }
                        // 其它异常照常向外抛：produce 协程失败会被消费循环重抛
                    }
                    .onFailure {
                        channel.send(ChannelPayload.Error(it))
                    }
            }

        try {
            var isFirst = true
            for (payload in channel) {
                if (isFirst && (payload is ChannelPayload.Bytes || payload is ChannelPayload.DirectPlayCallback)) {
                    callback.onSynthesizeStart(maxSampleRate)
                    isFirst = false
                }

                when (payload) {
                    is ChannelPayload.Bytes -> callback.onSynthesizeAvailable(payload.data)

                    is ChannelPayload.DirectPlayCallback -> try {
                        event(NormalEvent.DirectPlay(payload.request))
                        payload.callback.play()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        event(ErrorEvent.DirectPlay(payload.request, e))
                    } finally {
                        logger.debug { "direct play done" }
                    }

                    is ChannelPayload.Error -> {
                        return@coroutineScope Err(payload.err)
                    }

                    else -> logger.error { "unknown data: $payload" }
                }
            }
        } finally {
            logger.debug { "channel closed" }
        }

        Ok(Unit)
    }

    private val mutex = Mutex()

    override val isSynthesizing: Boolean
        get() = mutex.isLocked


    private var initError: SynthesisError? = null

    override suspend fun synthesize(
        params: SystemParams, forceConfigId: Long?, callback: SynthesisCallback,
        // 11-14：段落位置（用户令「每个请求音频日志都标 1/5」）。U阅 每段一次
        // synthesize 调用，段号随入口传入，一路透传到 TextSegment → RequestPayload
        // → 日志行。-1/0 = 调用方没带（第三方/旧版），全链不标。
        // ⚠️ override 不能带默认值（Kotlin 规则，CI 实锤 588:26），默认值由接口声明。
        paraIndex: Int,
        paraTotal: Int,
        // 11-15（任务书-U阅批量提交多段）：一次请求内多段并行。
        // null = 原单段路径，行为不变。
        batchSegments: List<BatchSegment>?,
    ): Result<Unit, SynthesisError> = mutex.withLock {
        logger.atTrace {
            message = "synthesize"
            payload = mapOf(
                "forceConfigId" to forceConfigId,
                "params" to params,
                "batchSize" to (batchSegments?.size ?: 0),
            )
        }

        initError?.let {
            return@withLock Err(it)
        }

        withMain { bgmPlayer.play() }
        try {
            executeSynthesis(params, callback, forceConfigId, paraIndex, paraTotal, batchSegments)
        } finally {
            logger.debug { "synthesize done" }
            withContext(NonCancellable) {
                withMain { bgmPlayer.stop() }
            }
        }
    }

    override suspend fun init() = mutex.withLock {
        try {
            repo.init()
        } catch (e: Exception) {
            event(ErrorEvent.Repository(e))
            return@withLock
        }

        mConfigs = repo.getAllTts()
        if (mConfigs.isEmpty()) {
            event(ErrorEvent.ConfigEmpty)
            return@withLock
        }

        // 合成结果缓存（11-15 路 C）：预热写、播放读。用 cacheDir，
        // 系统清缓存时一起清掉正合适——它是可再生成的加速数据。
        SynthesizedAudioCache.init(context.androidContext)

        // 引擎实例池大小跟随预取并发数（2026-11-15，用户定稿：不新增设置项）。
        // 用 provider 而非取值：并发数原本就是 ValueProvider，用户改设置后无需重建合成器。
        CachedEngineManager.poolSizeProvider = { context.cfg.prefetchConcurrency() }

        // 引擎预热：提前初始化所有引擎，避免首请求时同步初始化延迟
        mConfigs.values.map { it.source }.distinctBy { it.getKey() }.forEach { source ->
            try {
                CachedEngineManager.getEngine(context.androidContext, source)?.let { engine ->
                    if (engine.state != EngineState.Initialized) {
                        engine.onInit()
                    }
                }
            } catch (e: Exception) {
                logger.warn { "引擎预热失败: ${source.getKey()}, ${e.message}" }
            }
        }

        textProcessor.init(context.androidContext, mConfigs).onFailure {
            event(ErrorEvent.TextProcessor(it))
            return@withLock
        }

        streamProcessor.init(context.androidContext)

        val bgmList = mutableListOf<BgmSource>()
        try {
            repo.getAllBgm().forEach { bgm ->
                bgm.musicList.forEach {
                    bgmList.add(BgmSource(path = it, volume = bgm.volume))
                }
            }

            withMain {
                bgmPlayer.init()
                bgmPlayer.setPlayList(list = bgmList)
            }
        } catch (e: Exception) {
            event(ErrorEvent.BgmLoading(e))
            return@withLock
        }

        isInitialized = true
    }


    @MainThread
    override suspend fun destroy() = mutex.withLock {
        isInitialized = false
        repo.destroy()
        ttsRequester.destroy()
        streamProcessor.destroy()
        bgmPlayer.destroy()
    }

    sealed interface ChannelPayload {
        data class Bytes(val data: ByteArray) : ChannelPayload
        data class DirectPlayCallback(
            val request: RequestPayload,
            val callback: ITtsRequester.ISyncPlayCallback,
        ) : ChannelPayload

        data class Error(val err: SynthesisError) : ChannelPayload
    }
}