package com.github.jing332.tts_server_android.service.systts.help

import android.content.Context
import com.github.jing332.common.utils.StringUtils
import com.github.jing332.database.constants.ReplaceExecution
import com.github.jing332.database.dbm
import com.github.jing332.database.entities.systts.SpeechRuleInfo
import com.github.jing332.database.entities.systts.SystemTtsV2
import com.github.jing332.tts.ConfigType
import com.github.jing332.tts.error.TextProcessorError
import com.github.jing332.tts.synthesizer.ITextProcessor
import com.github.jing332.tts.synthesizer.TextSegment
import com.github.jing332.tts.synthesizer.TtsConfiguration
import com.github.jing332.script.runtime.console.Console
import com.github.jing332.tts_server_android.conf.SystemTtsConfig
import com.github.jing332.tts_server_android.model.rhino.speech_rule.SpeechRuleEngine
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.random.Random
import com.github.jing332.database.entities.systts.SpeechRuleIds

class TextProcessor : ITextProcessor {
    companion object {
        private val logger = KotlinLogging.logger { this::class.java.name }
    }

    private var isMultiVoice: Boolean = false

    private val isSplitSentence: Boolean
        get() = SystemTtsConfig.isSplitEnabled.value

    private val isReplaceEnabled: Boolean
        get() = SystemTtsConfig.isReplaceEnabled.value

    private lateinit var engine: SpeechRuleEngine
    private val textReplacer = TextReplacer()

    private var configs: List<TtsConfiguration> = emptyList()
    private var speechRules: List<SpeechRuleInfo> = emptyList()
    private val random by lazy { Random(System.currentTimeMillis()) }

    override fun init(
        context: Context,
        configs: Map<Long, TtsConfiguration>,
    ): Result<Unit, TextProcessorError> {
        isMultiVoice = SystemTtsConfig.isMultiVoiceEnabled.value
        logger.debug { "isMultiVoice=$isMultiVoice, configs.size=${configs.size}" }
        if (isMultiVoice) {
            // tagRuleId 空值兜底（用户令 11-11）：配置列表导出与 z阅读 通用后，JSON 里不再带
            // tagRuleId，手写/外部导入的项也常缺这一项。朗读规则全局只允许启用一条
            // （SpeechRuleManagerScreen 启用即停用其它），所以「没写规则」等价于「用当前启用的那条」，
            // 不存在歧义。原先这里直接 getByRuleId("") → null → MissingRule，整篇不读并弹警告。
            //
            // 取哪条配置的 tagRuleId 作依据：优先第一个**非空**的（原实现只取 component1()，
            // 首项恰好为空时即使后面都绑定了也白搭）。全都为空时才回退到启用中的规则。
            // 规则归属是常量（tagRuleId 字段已删）：本机只有一套规则
            val ruleId = SpeechRuleIds.RULE_ID
            if (boundRuleId == null && ruleId.isNotBlank()) {
                logger.debug { "tagRuleId 缺省，回退到当前启用规则: $ruleId" }
            }
            val speechRule =
                dbm.speechRuleDao.getByRuleId(ruleId)
                    ?: return Err(TextProcessorError.MissingRule(ruleId))
            logger.debug { "speechRule loaded: ruleId=${speechRule.ruleId}, name=${speechRule.name}" }
            engine = SpeechRuleEngine(context, speechRule)
            // 必须在 eval() 之前设置 console，否则 JavaScript 绑定的是默认 Console
            logger.debug { "Before setting console: source=${engine.console.source}" }
            engine.console = Console(Console.LogSource.SPEECH_RULE)
            logger.debug { "After setting console: source=${engine.console.source}" }
            engine.eval()
            this.configs =
                configs.entries.map { entry ->
                    val cfg = entry.value
                    // 方案B：从 SystemTtsV2(即 cfg.tag) 取 displayName，从 source 取底层 voice
                    // 填入 SpeechRuleInfo，供 handleText 传给 JS 做发音人同步与校验
                    val systts = cfg.tag as? SystemTtsV2
                    cfg.copy(
                        speechInfo = cfg.speechInfo.copy(
                            configId = entry.key,
                            voice = cfg.source.voice,
                            displayName = systts?.displayName ?: ""
                        )
                    )
                }
            speechRules = this.configs.map { it.speechInfo }
        } else {
            this.configs = configs.values.toList()
            if (this.configs.isEmpty())
                return Err(TextProcessorError.MissingConfig(ConfigType.SINGLE_VOICE))
        }

        loadReplacer()
        return Ok(Unit)
    }

    fun loadReplacer() {
        textReplacer.load()
    }

    private fun splitText(text: String): List<String> {
        return if (!isSplitSentence) listOf(text)
        else if (isMultiVoice) {
            try {
                engine.splitText(text).map { it.toString() }
            } catch (_: NoSuchMethodException) {
                StringUtils.splitSentences(text)
            }
        } else {
            StringUtils.splitSentences(text)
        }
    }

    private fun replace(text: String, @ReplaceExecution execution: Int): String {
        return if (isReplaceEnabled)
            textReplacer.replace(text, execution)
        else
            text
    }

    override fun process(
        text: String,
        presetConfig: TtsConfiguration?,
    ): Result<List<TextSegment>, TextProcessorError> {
        val resultList = mutableListOf<TextSegment>()
        val replacedText = replace(text, ReplaceExecution.BEFORE)

        fun add(vararg fragments: TextSegment) {
            fragments.forEach { f ->
                // 书/章（v1.7）必须原样带过去：这里重建 TextSegment 时漏一个字段，
                // 日志里就整条链都拿不到书章（add 是唯一出口，所有片段都经它）
                resultList.add(
                    TextSegment(
                        text = replace(f.text, ReplaceExecution.AFTER),
                        tts = f.tts,
                        roleName = f.roleName,
                        bookName = f.bookName,
                        chapterTitle = f.chapterTitle,
                    )
                )
            }
        }

        fun splitAndAdd(
            text: String,
            config: TtsConfiguration,
            roleName: String = "",
            bookName: String = "",
            chapterTitle: String = "",
        ) {
            splitText(text).forEach {
                add(
                    TextSegment(
                        text = it,
                        tts = config,
                        roleName = roleName,
                        bookName = bookName,
                        chapterTitle = chapterTitle,
                    )
                )
            }
        }

        try {

            if (presetConfig != null) {
                splitAndAdd(replacedText, presetConfig)
            } else if (isMultiVoice) {
                val fragments = engine.handleText(replacedText, speechRules)

                fragments.forEach { txtWithTag ->
                    if (txtWithTag.text.isNotBlank()) {
                        val effectiveTag = txtWithTag.tag

                        val sameTagList = configs.filter {
                            !it.speechInfo.isStandby && it.speechInfo.tag == effectiveTag
                        }
                        val configFromId =
                            sameTagList.find { it.speechInfo.configId == txtWithTag.id }

                        // 匹配链：精确 ID > 同 tag > 同 voice。**到此为止，不再兜底**。
                        //
                        // ## 11-10 用户令：删掉「全库随机」这一层
                        //
                        // 原第四层是 `?: configs.randomOrNull(random)` ——从**所有已启用配置**
                        // 里随机挑一个。它在 forEach 内部执行，于是同一篇文章里
                        // **每一句旁白都各自随机一次、每句换一个声**，整库轮着读，
                        // 用户听感是「其余所有声音挨个读一遍，非常乱」。
                        //
                        // 用户定稿：「不给它兜底了，旁白没启用就直接停、报个错就行，
                        // 其他 tag 也一样」——宁可整段不读并显式报错，也不要跨类乱借声音。
                        // 报错经 MissingConfig 走到 SystemTtsService.handleTextProcessorError，
                        // 有 Toast + 日志，用户看得到是哪个 tag 缺配置，而不是默默念错。
                        //
                        // ⚠️ 角色管理里的「同类别自动分配」(CharacterRecordsFile.pickCategoryVoice)
                        // 是**改绑时**的动作，与朗读期匹配无关，保持原样不动。
                        val config = configFromId
                            ?: sameTagList.randomOrNull(random)
                            ?: configs.filter {
                                !it.speechInfo.isStandby && it.speechInfo.voice == effectiveTag
                            }.randomOrNull(random)
                            ?: return Err(
                                TextProcessorError.MissingConfig(
                                    ConfigType.TAG,
                                    "tag=${effectiveTag}, id=${txtWithTag.id}"
                                )
                            )
                        // 书/章取规则 v1.7 返回的字段（旧规则为空串，后续按无书章处理）
                        splitAndAdd(
                            txtWithTag.text, config, txtWithTag.roleName,
                            txtWithTag.bookName, txtWithTag.chapterTitle,
                        )
                    }
                }
            } else {
                val singleVoice = configs.randomOrNull(random) ?: return Err(
                    TextProcessorError.MissingConfig(
                        ConfigType.SINGLE_VOICE, "single voice"
                    )
                )
                splitAndAdd(replacedText, singleVoice)
            }
        } catch (e: UninitializedPropertyAccessException) {
            return Err(TextProcessorError.Initialization)
        } catch (e: Exception) {
            return Err(TextProcessorError.HandleText(e))
        }

        return Ok(resultList)
    }
}