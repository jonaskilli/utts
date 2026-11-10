package com.github.jing332.database.entities.systts

import com.github.jing332.database.dbm

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
