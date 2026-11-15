package com.github.jing332.tts.speech.plugin

import android.content.Context
import com.github.jing332.database.entities.plugin.Plugin
import com.github.jing332.tts.speech.plugin.engine.TtsPluginUiEngineV2
import com.github.jing332.tts.util.AbstractCachedManager

object TtsPluginEngineManager : AbstractCachedManager<String, TtsPluginUiEngineV2>(
    timeout = 1000L * 60L * 10L, // 10 min
    delay = 1000L * 60L * 1L, // 1 min
) {
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
        if (cachedEngine != null && cachedEngine.plugin.code == plugin.code) {
            return cachedEngine
        }

        // 代码已更改，销毁旧引擎并创建新引擎
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
