package com.github.jing332.tts_server_android.model.rhino.speech_rule

import android.content.Context
import com.github.jing332.database.entities.SpeechRule
import com.github.jing332.database.entities.TagsDataMap
import com.github.jing332.database.entities.systts.SpeechRuleInfo

import com.github.jing332.script.runtime.console.Console
import com.github.jing332.script.simple.SimpleScriptEngine
import com.github.jing332.script.source.toScriptSource
import com.github.jing332.tts_server_android.R
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptRuntime

class SpeechRuleEngine(
    val context: Context,
    private val rule: SpeechRule
) {
    companion object {
        const val OBJ_JS = "SpeechRuleJS"

        const val FUNC_GET_TAG_NAME = "getTagName"
        const val FUNC_HANDLE_TEXT = "handleText"
        const val FUNC_SPLIT_TEXT = "splitText"
        const val FUNC_GET_CATEGORY_TAG = "getCategoryTag"

        /**
         * 11-18（v1.7.32 约定）：配置变更后让规则重读角色记录 / 清发音人池。
         * 引擎复用（TextProcessor 按签名复用）时必须靠它保证「改角色/换发音人实时生效」，
         * 否则规则内存里的旧角色表会把 App 刚写进 characterRecords.json 的改动覆盖回去。
         */
        const val FUNC_RELOAD_CHARACTER_RECORDS = "reloadCharacterRecords"

        fun getTagName(context: Context, speechRule: SpeechRule, info: SpeechRuleInfo): String {
            // 规则外标签（jread 未映射的性格/群杂等）：规则 JS 对表外标签一律兜底返回「旁白」，
            // 会把未映射项显示名误标成旁白——直接原样返回，不进 JS
            if (speechRule.tags[info.tag] == null) return info.tag

            val engine = SpeechRuleEngine(context, speechRule)
            engine.eval()

            val tagName = try {
                engine.getTagName(info.tag, info.tagData)
            } catch (_: NoSuchMethodException) {
                speechRule.tags[info.tag] ?: ""
            }

            return tagName
        }
    }

    // 构造即标 SPEECH_RULE：本类就是朗读规则引擎，而 Console 的默认来源是 PLUGIN。
    // 此前只有朗读链（TextProcessor）与规则编辑器显式改过标记，其余构造点
    //（角色管理页重算 / TagNameUtils / 分类导入器 / 导入解析 / companion 反查）全用默认值，
    // 产出的规则日志被打成 [Plugin] 前缀进插件缓冲——「朗读规则」开关勾了也永远看不到它们。
    // 在这里给默认值，全部构造点一处修好；调用方若要换 console 仍可覆盖 setter。
    val engine = SimpleScriptEngine(context, rule.ruleId).apply {
        runtime.console = Console(Console.LogSource.SPEECH_RULE)
    }
    var console: Console
        get() = engine.runtime.console
        set(value) {
            engine.runtime.console = value
        }


    private val objJS
        get() = engine.get(OBJ_JS) as? NativeObject
            ?: throw NullPointerException("朗读规则未定义 SpeechRuleJS 对象，请检查JS代码顶部是否有 var SpeechRuleJS = {...}")

    fun eval() {
        engine.execute(rule.code.toScriptSource())
    }

    @Suppress("UNCHECKED_CAST")
    fun evalInfo() {
        eval()
        objJS.apply {
            rule.name = get("name").toString()
            rule.ruleId = get("id").toString()
            rule.author = get("author").toString()

            rule.tags = get("tags") as Map<String, String>

            rule.tagsData =
                getOrDefault(
                    "tagsData",
                    emptyMap<String, Map<String, Map<String, String>>>()
                ) as TagsDataMap

            runCatching {
                rule.version = ScriptRuntime.toNumber(get("version")).toInt()
            }.onFailure {
                throw NumberFormatException(context.getString(R.string.plugin_bad_format))
            }
        }
    }

    fun getTagName(tag: String, tagMap: Map<String, String>): String {
        return engine.invokeMethod(objJS, FUNC_GET_TAG_NAME, tag, tagMap).toString()
    }

    /**
     * 可选的 JS 钩子：试听分类保存时，由朗读规则自定义「分类下第 [seq] 个声音」应使用的 tag。
     * 每套规则可实现自己的逻辑（如映射到已有角色 tag、拼接不同格式的序号等）。
     * JS 未实现该方法时返回 null，调用方回退默认「分类名+两位序号」。
     */
    fun getCategoryTag(category: String, seq: Int): String? {
        return runCatching {
            engine.invokeMethod(objJS, FUNC_GET_CATEGORY_TAG, category, seq)?.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /**
     * 11-18：请规则重载角色记录（引擎复用的前置条件）。
     *
     * @return true=规则已重载，可安全复用引擎；false=规则未实现该方法（v1.7.31 及更早）
     *   或执行失败——调用方必须回退「重建引擎」，保持旧规则原有行为。
     */
    fun reloadCharacterRecords(): Boolean {
        return runCatching {
            val ret = engine.invokeMethod(objJS, FUNC_RELOAD_CHARACTER_RECORDS)
            ret == null || ret.toString() != "false"
        }.getOrDefault(false)
    }

    data class TagData(val id: String, val value: String)

    /**
     * @param isPreload 11-17（竞速/慢钥改造）：本笔是否后台预热批次。
     *   作为 handleText 的**第三个参数**（Boolean）传给规则 JS——旧规则
     *   （只声明两个形参）忽略它，行为逐字节不变；v1.7.21 起规则用它决定 AI 调用走
     *   「加赛（两把并发先成者胜）」还是「单发」：预热是备货，不该把请求翻倍。
     */
    fun handleText(
        text: String,
        list: List<SpeechRuleInfo> = emptyList(),
        isPreload: Boolean = false,
    ): List<TextWithTag> {
        val tagsDataMap: MutableMap<String, MutableMap<String, MutableList<Map<String, String>>>> =
            mutableMapOf()
        list.forEach { info ->
            if (tagsDataMap[info.tag] == null)
                tagsDataMap[info.tag] = mutableMapOf()

            info.tagData.forEach {
                if (tagsDataMap[info.tag]!![it.key] == null)
                    tagsDataMap[info.tag]!![it.key] = mutableListOf()

                tagsDataMap[info.tag]!![it.key]!!.add(
                    mapOf(
                        "id" to info.configId.toString(),
                        "value" to it.value
                    )
                )
            }

            // 方案B：把底层 voice 和 displayName 也带入 tagsData
            // key 加下划线前缀避免和正常 tagData 冲突，JS 侧通过 tagsData[tag]._voice 取值
            if (info.voice.isNotEmpty()) {
                val voiceKey = "_voice"
                if (tagsDataMap[info.tag]!![voiceKey] == null)
                    tagsDataMap[info.tag]!![voiceKey] = mutableListOf()
                tagsDataMap[info.tag]!![voiceKey]!!.add(
                    mapOf(
                        "id" to info.configId.toString(),
                        "value" to info.voice
                    )
                )
            }
            if (info.displayName.isNotEmpty()) {
                val nameKey = "_displayName"
                if (tagsDataMap[info.tag]!![nameKey] == null)
                    tagsDataMap[info.tag]!![nameKey] = mutableListOf()
                tagsDataMap[info.tag]!![nameKey]!!.add(
                    mapOf(
                        "id" to info.configId.toString(),
                        "value" to info.displayName
                    )
                )
            }
        }
        return handleText(text, tagsDataMap, isPreload)
    }

    /** ['dialogue']['role']= List<TagData>
     *@param tagsDataSet 例： key: dialogue, value: map(key: role, value: [{tagDataId:111, 张三, 李四])
     */
    fun handleText(
        text: String,
        tagsDataMap: Map<String, Map<String, List<Map<String, String>>>>,
        isPreload: Boolean = false,
    ): List<TextWithTag> {
        val resultList: MutableList<TextWithTag> = mutableListOf()
        // 第三参直接给 Boolean：Rhino 对 Java Map 的属性访问不可靠（现有的 tagsData
        // 就得 entrySet 逐层转，见规则 handleText 头），布尔值转换则无歧义。
        engine.invokeMethod(objJS, FUNC_HANDLE_TEXT, text, tagsDataMap, isPreload)
            ?.run { this as List<*> }
            ?.let { list ->
                list.forEach {
                    if (it is Map<*, *>) {
                        resultList.add(
                            TextWithTag(
                                it["text"].toString(),
                                it["tag"].toString(),
                                it.getOrDefault("id", 0).toString().toLong(),
                                // 实时角色名（用户 09-08）：规则 JS 返回条目可带 name 字段，
                                // 旧规则没有此字段时为空串，向后兼容
                                roleName = it["name"]?.toString() ?: "",
                                // 书/章（v1.7 起）：规则返回 book / chapter 两个字段供日志分组。
                                // 旧规则没有这两个键 ⇒ 空串，日志照旧不画书条/章节签
                                bookName = it["book"]?.toString() ?: "",
                                chapterTitle = it["chapter"]?.toString() ?: "",
                            )
                        )
                    }
                }

            }
        return resultList
    }

    @Suppress("UNCHECKED_CAST")
    fun splitText(text: String): List<CharSequence> {
        return engine.invokeMethod(
            objJS,
            FUNC_SPLIT_TEXT,
            text
        ) as List<CharSequence>
    }

    data class TextWithTag(
        val text: String,
        val tag: String,
        val id: Long,
        val roleName: String = "",
        val bookName: String = "",
        val chapterTitle: String = "",
    )
}