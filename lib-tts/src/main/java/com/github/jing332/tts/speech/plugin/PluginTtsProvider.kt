package com.github.jing332.tts.speech.plugin

import android.content.Context
import com.github.jing332.database.entities.plugin.Plugin
import com.github.jing332.database.entities.systts.source.PluginTtsSource
import com.github.jing332.tts.CachedEngineManager
import com.github.jing332.tts.speech.EngineState
import com.github.jing332.tts.speech.TextToSpeechProvider
import com.github.jing332.tts.speech.plugin.engine.TtsPluginEngineV2
import com.github.jing332.tts.synthesizer.SystemParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.InputStream

open class PluginTtsProvider(
    val context: Context,
    val plugin: Plugin,
) : TextToSpeechProvider<PluginTtsSource>() {

    private var mEngine: TtsPluginEngineV2? = null

    var engine: TtsPluginEngineV2?
        get() = mEngine
        set(value) {
            mEngine = value
        }

    override var state: EngineState = EngineState.Uninitialized()

    override suspend fun getStream(params: SystemParams, source: PluginTtsSource): InputStream {
        val route = parameterRoute(
            pluginId = plugin.pluginId,
            legacySpeed = plugin.pluginHandlesSpeed,
            legacyVolume = plugin.pluginHandlesVolume,
            legacyPitch = plugin.pluginHandlesPitch,
        )
        // audioParams 是唯一用户调节层；仅把插件声明负责的维度发给插件，
        // 本机负责的维度发送中性值，避免插件与本机重复处理。
        val speed = if (route.pluginSpeed) params.speed else 1f
        val volume = if (route.pluginVolume) params.volume else 1f
        val pitch = if (route.pluginPitch) params.pitch else 1f

        // source.data mapping to ttsrv.tts.data for javascript
        mEngine?.source = source

        return try {
            mEngine?.getAudio(
                text = params.text,
                locale = source.locale,
                voice = source.voice,
                rate = speed,
                volume = volume,
                pitch = pitch,
                timeoutMs = params.requestTimeout
            ) ?: throw IllegalStateException("Engine not initialized: ${plugin.pluginId}")
        } catch (e: CancellationException) {
            // ===== 11-13 晚：取消后「作废引擎 or 保留引擎」折中（用户选定）=====
            //
            // 背景：11-13 白天实装「取消即作废引擎」，治好了「请等待当前请求完成 /
            // Object PluginJS not found / WS Broken pipe」那套重试风暴，代价是
            // **每次用户跳段/翻页都要重建引擎**。真机日志实测重建开销可观：
            // 用户 7 分钟里 15 次 Engine timeout destroy，其中 8 次紧跟 -3（取消）之后，
            // 每次重建 1~4 秒，期间新请求只能排队 —— 用户体感就是"跳段后要等一下才出声"。
            //
            // 折中：按取消的**来路**分档，只对"脏风险高"的那类作废引擎。
            //   · 超时（TimeoutCancellationException，withTimeout 抛出，它是
            //     CancellationException 的子类）→ 插件本身处理慢/卡住，引擎内部
            //     状态高度可疑：**照旧作废重建**，与旧行为完全一致。
            //   · 其它取消 = 用户主动停止/翻页/跳段、U阅 stop()、破窗重发 →
            //     这是我们自己的调度掐断的，插件通常停在干净位置：**只重置 state，
            //     保留引擎**，下一笔直接用，省掉 1~4 秒重建。
            //
            // 保留档的兜底自愈：万一下一笔真撞上脏状态，合成会失败 → 正常重试链
            // 递增 retries → 重试前也会 removeEngine 重建（见 AbstractMixSynthesizer
            // 的 retry()），等价于旧行为、只多花一次重试；不会比"每次都重建"更差地卡死。
            //
            // 想恢复旧行为（一律作废）：删掉下面的 isTimeout 判断，回到无条件 removeEngine。
            val isTimeout = e is TimeoutCancellationException
            state = EngineState.Uninitialized()
            if (isTimeout) {
                runCatching { CachedEngineManager.removeEngine(source) }
            }
            throw e
        } catch (e: Exception) {
            // 发生网络或其他异常时，重置引擎状态并强制销毁
            // 确保下次请求重新执行 onInit()，实现网络恢复后的自愈
            state = EngineState.Uninitialized()
            onDestroy()
            throw e
        }
    }

    override suspend fun onInit() {
        state = EngineState.Initializing
        if (mEngine == null)
            mEngine = TtsPluginEngineManager.get(context, plugin)

        state = EngineState.Initialized
    }

    override fun onStop() {
        super.onStop()
        mEngine?.onStop()
    }

    override fun onDestroy() {
        state = EngineState.Uninitialized()
        mEngine?.onStop()
        TtsPluginEngineManager.remove(plugin.pluginId)
        mEngine = null
    }
}
