package com.github.jing332.tts

import android.content.Context
import com.github.jing332.database.dbm
import com.github.jing332.database.entities.plugin.Plugin
import com.github.jing332.database.entities.systts.source.TextToSpeechSource
import com.github.jing332.database.entities.systts.source.LocalTtsSource
import com.github.jing332.database.entities.systts.source.PluginTtsSource
import com.github.jing332.tts.speech.TextToSpeechProvider
import com.github.jing332.tts.speech.local.LocalTtsProvider
import com.github.jing332.tts.speech.plugin.PluginTtsProvider

@Suppress("UNCHECKED_CAST")
object SpeechServiceFactory {
    /**
     * @param engineSlot 引擎实例池槽位（2026-11-15）。0=不分池；>0=该 provider 独占一个
     *   Rhino 引擎实例，用于同插件多实例并行合成。只有插件标记 allowParallel 才会 >0。
     */
    fun createEngine(
        context: Context,
        source: TextToSpeechSource,
        engineSlot: Int = 0,
    ): TextToSpeechProvider<TextToSpeechSource>? {
        return when (source) {
            is LocalTtsSource -> LocalTtsProvider(context, source.engine)
            is PluginTtsSource -> {
                PluginTtsProvider(
                    context,
                    loadPlugin(source) ?: return null,
                    engineSlot = engineSlot,
                )
            }

            else -> null
        } as TextToSpeechProvider<TextToSpeechSource>?
    }

    /**
     * 取「带 JS 源码」的插件对象交给引擎。
     *
     * 2026-11-15 回归（引擎实例池 `4c7a65ea` 引入，本函数即其修复）：
     * 该提交为省一次查库，在 `resolveTtsPlayback` 里把**元数据副本**回填进了
     * `source.plugin`——而元数据查询的 `code` 是空串（`getMetaByPluginId` /
     * `getAllMeta`，为躲 09-07 CursorWindow OOM 故意不取 code）。
     * 本函数原先写 `source.plugin ?: getEnabled(...)`，于是拿到空 code 的插件：
     * Rhino 执行空脚本 → 全局里没有 `PluginJS` → 每段合成都报
     * 「Object `PluginJS` not found」，重试两次、兜底发音人也同样报错，
     * 现象就是**点击朗读完全没声音、日志满屏同一个错**（启动预热也会逐个报）。
     *
     * 判据用 code 是否为空，而不是「plugin 是否为 null」：
     * 有真 code 的插件对象照旧直接用（编辑器/试听路径就是这么传的），
     * 只有元数据副本才回查一次全量（SELECT *，含 code）。
     * 引擎本身有缓存，这条回查只在建引擎时发生，不会回到逐段查库的 N+1。
     */
    private fun loadPlugin(source: PluginTtsSource): Plugin? {
        val cached = source.plugin
        if (cached != null && cached.code.isNotBlank()) return cached
        return dbm.pluginDao.getEnabled(source.pluginId)
    }
}