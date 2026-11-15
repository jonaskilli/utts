package com.github.jing332.tts

import android.content.Context
import com.github.jing332.database.dbm
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
                    source.plugin ?: dbm.pluginDao.getEnabled(source.pluginId) ?: return null,
                    engineSlot = engineSlot,
                )
            }

            else -> null
        } as TextToSpeechProvider<TextToSpeechSource>?
    }
}