package com.github.jing332.tts.speech.plugin

import android.content.Context
import com.github.jing332.database.entities.plugin.Plugin
import com.github.jing332.tts.speech.plugin.engine.TtsPluginUiEngineV2
import com.github.jing332.tts.util.AbstractCachedManager
import io.github.oshai.kotlinlogging.KotlinLogging

object TtsPluginEngineManager : AbstractCachedManager<String, TtsPluginUiEngineV2>(
    timeout = 1000L * 60L * 10L, // 10 min
    delay = 1000L * 60L * 1L, // 1 min
) {
    private val logger = KotlinLogging.logger("TtsPluginEngineManager")
    /**
     * 取插件引擎（2026-11-15 加 slot，引擎实例池）。
     *
     * slot 的含义：同一个插件允许同时存在多个 Rhino 引擎实例时，用 slot 区分。
     * 池化必须连引擎一起分——PluginTtsProvider 虽有 N 个，但它们都从这里取引擎；
     * 若这里仍按 pluginId 返回单例，N 个 provider 会共用同一个 Rhino 引擎和同一把
     * TtsPluginEngineV2.mMutex，合成本质上仍全程串行，池化彻底无效。
     *
     * slot = 0 或不分池时，key 与旧版完全一致（"pluginId"），
     * 保证未标记 allowParallel 的插件走原路径、行为不变、且能命中旧缓存。
     */
    fun get(context: Context, plugin: Plugin, slot: Int = 0): TtsPluginUiEngineV2 {
        val key = if (slot <= 0) plugin.pluginId else "${plugin.pluginId}#$slot"

        // 检查缓存的引擎是否使用了相同的代码
        val cachedEngine = cache.get(key)
        // 11-23（真机 02:37:22 / 18:26:17 两场实锤）：**缓存里的引擎可能是死的**——
        // destroy() 只把 Rhino scope 置空，对象还躺在缓存里；只比 code 会把它原样交出去，
        // 下一笔请求就在 5~8ms 内抛 `Object PluginJS not found`（无网络往返）。
        // 所以先验活：不可用等同于「代码不匹配」，一律重建。
        val usable = cachedEngine != null && cachedEngine.isUsable()
        if (cachedEngine != null && usable && cachedEngine.plugin.code == plugin.code) {
            return cachedEngine
        }
        if (cachedEngine != null) {
            // 留痕：这是「引擎被从脚下撤走」家族的最后一块拼图，下次复现能直接看到
            // 是「缓存里躺了死实例」还是「插件代码变了」，不用再靠猜。
            logger.warn {
                (if (!usable) "cached engine unusable (destroyed/scope lost), rebuilding: $key"
                else "plugin code changed, rebuilding: $key")
            }
        }

        // 代码已更改或实例已失效，销毁旧引擎并创建新引擎
        cachedEngine?.destroy()

        val engine = TtsPluginUiEngineV2(context, plugin)
        engine.eval()
        cache.put(key, engine)
        return engine
    }

    /**
     * 清掉该插件的**全部**引擎实例（含池化槽位）。
     * 插件代码/变量更新、禁用、删除时调用——只清 0 号槽位会留下脏实例。
     */
    fun remove(pluginId: String) {
        val keys = cache.keySet().toList()
        for (key in keys) {
            if (key == pluginId || key.startsWith("$pluginId#")) {
                cache.remove(key)
            }
        }
    }

    /**
     * 只清某一个槽位的引擎实例（2026-11-15）。
     * 单路 provider 销毁时用：池化下每条路有自己的引擎，销毁一路不许波及其它路。
     */
    fun removeOne(pluginId: String, slot: Int) {
        cache.remove(if (slot <= 0) pluginId else "$pluginId#$slot")
    }
}
