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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private fun event(event: Event) {
        context.event?.dispatch(event)
    }

    /**
     * @return null means presetConfigId is not found from database
     */
    private suspend fun textProcess(
        params: SystemParams,
        presetConfigId: Long?,
    ): Result<List<TextSegment>, SynthesisError> {
        val presetConfig: TtsConfiguration? = presetConfigId?.run { repo.getTts(this) }
        if (presetConfigId != null && presetConfig == null) {
            return Err(SynthesisError.PresetMissing(presetConfigId))
        }
        textProcessor
            .process(params.text, presetConfig)
            .onSuccess { list -> return Ok(list.filterNot { StringUtils.isSilent(it.text) }) }
            .onFailure { err: TextProcessorError ->
                event(ErrorEvent.TextProcessor(err))
                return Err(SynthesisError.TextHandle(err))
            }

        return Ok(emptyList())
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
            event(ErrorEvent.RequestTimeout(request))
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
                    event(ErrorEvent.Request(request, it.error))
                }

                is RequesterError.StateError -> {
                    event(ErrorEvent.Request(request, IllegalStateException(it.message)))
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
    ) {
        val request = RequestPayload(params, config, roleName, failoverFromTag, bookName, chapterTitle)
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
                event(NormalEvent.StandbyTts(
                    request.copy(config = config.standbyConfig),
                    fromTag = fromTag,
                    toTag = toTag,
                    reason = "retry",
                    isFallback = config.standbyIsFallback,
                ))
                // 带上「从哪个标签转来」：切换后的请求已被上面一条日志说明了来路，
                // 服务层不能再把它当成「规则分析失败降级」重复报一次。
                requestAndProcess(
                    sink, params, config.standbyConfig, 0, maxRetries,
                    roleName = roleName, failoverFromTag = fromTag,
                    bookName = bookName, chapterTitle = chapterTitle,
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
                event(ErrorEvent.Request(request, IllegalStateException("功能标签($curTag)兜底发音人重试失败")))
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
        event(NormalEvent.Request(request, retries))

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
            event(ErrorEvent.RequestTimeout(request))
            return retry()
        } catch (e: CancellationException) {
            throw e
        }

        processResult.onFailure { e ->
            event(ErrorEvent.ResultProcessor(request, e))
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
        // N=1：保持与旧「串行 for」完全一致的基线路径（验收第2条），直写播放通道
        if (concurrency <= 1 || list.size <= 1) {
            val sink = ChannelSink(channel)
            for ((index, segment) in list.withIndex()) {
                requestAndProcess(
                    sink, params.copy(text = segment.text), segment.tts,
                    roleName = segment.roleName,
                    bookName = segment.bookName,
                    chapterTitle = segment.chapterTitle,
                )
                insertSegmentPause(channel, index, list.size)
            }
            return
        }

        // 领号通道：按序发 0,1,2…；fetcher 领到号才能处理对应段 → 并发上限=并发数
        val indexCh = Channel<Int>(capacity = concurrency)
        // 每段独立产物信箱：fetcher 的成品只进 box[i]，谁先完成都不影响顺序
        val boxes = List(list.size) { Channel<ChannelPayload?>(capacity = Channel.UNLIMITED) }
        // 独立 scope：fetcher 失败不传染回填循环（SupervisorJob），随 finally 一并取消
        val fetcherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val fetchers = List(concurrency) { worker ->
            fetcherScope.launch(CoroutineName("SynthPrefetch #$worker")) {
                for (index in indexCh) { // 领号
                    val segment = list[index]
                    val box = boxes[index]
                    val collector = PayloadCollector()
                    try {
                        requestAndProcess(
                            collector, params.copy(text = segment.text), segment.tts,
                            roleName = segment.roleName,
                            bookName = segment.bookName,
                            chapterTitle = segment.chapterTitle,
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
                // 该段直接播放（EmptyInputStream 无 Bytes 产物）时信箱为空，停顿照插
                insertSegmentPause(channel, index, list.size)
            }
            fetchers.forEach { it.join() }
            feeder.join()
        } finally {
            // 无论正常收尾、致命错误还是外部取消（onStop 破窗），都掐掉 fetcher 与发号
            indexCh.close()
            fetcherScope.cancel()
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
    ): Result<Unit, SynthesisError> = coroutineScope {
        if (mConfigs.isEmpty()) return@coroutineScope Err(SynthesisError.ConfigEmpty)

        logger.debug { "onSynthesizeStart: sampleRate=${maxSampleRate}" }

        val channel =
            produce<ChannelPayload>(CoroutineName("Synthesis producer"), PROCUDE_CAPACITY) {
                textProcess(params, presetConfigId)
                    .onSuccess { list ->
                        try {
                            produceSegments(channel, params, list)
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
    ): Result<Unit, SynthesisError> = mutex.withLock {
        logger.atTrace {
            message = "synthesize"
            payload = mapOf(
                "forceConfigId" to forceConfigId,
                "params" to params,
            )
        }

        initError?.let {
            return@withLock Err(it)
        }

        withMain { bgmPlayer.play() }
        try {
            executeSynthesis(params, callback, forceConfigId)
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