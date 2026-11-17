package com.github.jing332.database.entities.systts

/**
 * 当前生效朗读规则的 id 与数据目录。
 *
 * ## 沿革
 *
 * 11-11 用户令：`SpeechRuleInfo.tagRuleId` 字段整个删掉，「本机只认一套规则」，
 * 各处一律引用写死的 `mingwuyan`。
 *
 * 11-17 用户令（支线规则）：**新建一条独立朗读规则（Fish 按书配音），不去改现役规则**。
 * 于是这里从「常量」改为「跟随启用规则」：
 * - 规则表仍全局只允许启用一条（启用时自动停用其它，见 SpeechRuleManagerScreen）；
 * - [refresh] 在「App 启动 / 规则启停 / 导入 / 每次合成初始化」时被调用，
 *   写入启用规则的 `ruleId`；
 * - 所有原来引用 `SpeechRuleIds.RULE_ID` / `DATA_DIR` 的地方**无需改动**，
 *   自动跟着走：换规则 = 换角色记录目录（`/sdcard/Download/chajian/<ruleId>/`），
 *   两条支线互不污染。
 *
 * 未刷新过 / 库里没有任何启用规则时回退 [DEFAULT_RULE_ID]（= 现役 mingwuyan），
 * 行为与改造前逐字节一致。
 */
object SpeechRuleIds {
    /** 回退规则 id，等同现役规则的 `SpeechRuleJS.id`。 */
    const val DEFAULT_RULE_ID = "mingwuyan"

    @Volatile
    private var activeRuleId: String = DEFAULT_RULE_ID

    /** 当前生效规则的 id（= 启用规则的 `SpeechRuleJS.id`）。 */
    val RULE_ID: String get() = activeRuleId

    /** 当前规则的角色数据目录：`/sdcard/Download/chajian/<当前规则 id>/`。 */
    val DATA_DIR: String get() = "/sdcard/Download/chajian/$RULE_ID"

    /** 刷新生效规则；传 null/空串 = 回退默认规则（停用全部规则时）。 */
    fun refresh(ruleId: String?) {
        val id = ruleId?.trim().orEmpty()
        activeRuleId = id.ifEmpty { DEFAULT_RULE_ID }
    }
}