package com.github.jing332.database.entities.systts

import com.github.jing332.database.dbm
import com.github.jing332.database.entities.systts.v1.GroupWithV1TTS

/**
 * `tagRuleId` 空值 → 当前启用规则（用户令 11-11：配置列表与 z阅读 通用，不在 JSON 里写这一项）。
 *
 * ## 为什么空值可以等价于「当前启用的那条」
 * 朗读规则**全局只允许启用一条**——`SpeechRuleManagerScreen` 在启用某条时会先把其它已启用规则
 * 全部停用（`dbm.speechRuleDao.allEnabled.filter { it.id != item.id }`）。因此「配置没写规则 id」
 * 与「用当前那条规则」在任何时刻都是同一个答案，不存在歧义。
 *
 * ## 为什么必须全 app 统一走这里
 * 历史问题是「半通不通」：朗读期若能读到配置、点标签/换声却各自 `getByRuleId("")` 拿到 null，
 * 于是同一份配置出现「能读，但标签点不动、提示未绑定朗读规则」。所有需要按配置规则定位的地方
 * （朗读 `TextProcessor`、标签切换 `TagSwitchDialog`、换声 `VoicePickerDialog`、列表快捷切换、
 * tagName 计算）都必须走这一个口径。
 *
 * ⚠️ 会查库：调用方须在 IO 线程（Room 虽开了 allowMainThreadQueries，但列表/朗读链路上
 * 主线程查询会掉帧）。
 */
object TagRuleResolver {
    /**
     * @param ruleId 配置里存的 tagRuleId，可为空串
     * @return 实际生效的规则 id；库里没有任何启用规则时返回空串（调用方保留原有「无规则」处理）
     */
    fun resolve(ruleId: String): String =
        ruleId.ifBlank { dbm.speechRuleDao.allEnabled.firstOrNull()?.ruleId.orEmpty() }
}

/**
 * 导出用：把配置里的 `tagRuleId` 抹成空串（用户令 11-11「不管是什么样，都不应该有这一项」）。
 *
 * ## 抹成空串 ≠ 丢信息
 * - **序列化**：`AppConst.jsonBuilder` 未开 `encodeDefaults`，空串等于字段默认值 → 该键整行消失。
 *   于是导出文件里不会出现 `tagRuleId`，和 z阅读 的配置格式完全一致，两边可互导。
 * - **回导**：`TagRuleResolver` 保证空值等价于「当前启用规则」（规则全局只启用一条），
 *   导入侧 `migrateTagNamesIfNeed` / `insertJReadItems` 也会自动回填，所以抹掉不影响使用。
 *
 * ## 为什么只抹值、不删字段
 * `tagRuleId` 在库里还承担「角色数据目录 / 密钥池 / 插件回调身份」三件事，从实体删掉要升
 * Room 版本 + 写迁移（24 个文件、400+ 处引用），风险与收益不成比例。抹值只在导出这一层发生，
 * 数据库里仍然是完整的。
 *
 * ## 用在哪些导出上
 * 配置列表导出、单条导出、旧数据迁移导出、整机备份/分享包——**所有**产出配置 JSON 的地方都要走，
 * 否则用户会在某个入口又看见这一项。反过来，运行时/落库路径**绝不能**调用它。
 */
fun List<GroupWithSystemTts>.stripTagRuleId(): List<GroupWithSystemTts> =
    map { group ->
        group.copy(
            list = group.list.map { tts ->
                val cfg = tts.config as? TtsConfigurationDTO
                if (cfg == null || cfg.speechRule.tagRuleId.isEmpty()) tts
                else tts.copy(config = cfg.copy(speechRule = cfg.speechRule.copy(tagRuleId = "")))
            }
        )
    }


/**
 * 导出用：v1 旧结构（`SystemTtsDao.getAllGroupWithTts`）同样抹掉 `tagRuleId`。
 * 与 [stripTagRuleId] 同口径，只是承载类型不同——旧版迁移导出走的就是这条。
 */
fun List<GroupWithV1TTS>.stripTagRuleIdV1(): List<GroupWithV1TTS> =
    map { group ->
        group.copy(
            list = group.list.map { tts ->
                if (tts.speechRule.tagRuleId.isEmpty()) tts
                else tts.copy(speechRule = tts.speechRule.copy(tagRuleId = ""))
            }
        )
    }
