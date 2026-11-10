package com.github.jing332.database.entities.systts

/**
 * 朗读规则的固定身份（用户令 11-11：`tagRuleId` 字段整个删除，本机只认这一套规则）。
 *
 * ## 背景
 * 配置项原先自带 `tagRuleId` 字段，用来表示「这条配置归哪份朗读规则管」，并据此拼出
 * 角色卡 / 发音人池 / 密钥 / 标记的目录：`/sdcard/Download/chajian/<tagRuleId>/`。
 *
 * 但本机的朗读规则**只有一套**（`SpeechRuleJS.id = "mingwuyan"`），且 UTTS 全局只允许
 * 启用一条规则（启用新规则会自动停用其它）。也就是说这个字段在任一时刻都是常量，
 * 存在配置里纯属冗余——还导致导出的 JSON 与 z阅读 的配置列表对不上。
 *
 * 用户定稿（11-11）：「数据库里真的不存，字段整个删掉」「固定写死 mingwuyan」。
 *
 * ## 因此
 * - `SpeechRuleInfo` 不再有 `tagRuleId`，数据库 JSON 与所有导出文件里都不会出现该键；
 * - 所有需要规则 id 的地方一律引用这里的 [RULE_ID]；
 * - 各处文件读写方法仍保留 `tagRuleId: String` 形参（调用方传本常量），这样将来若要
 *   恢复多规则支持，只需改这个常量与调用点，不必再全仓改签名。
 */
object SpeechRuleIds {
    /** 本机唯一朗读规则的 id，等同 `SpeechRuleJS.id`。 */
    const val RULE_ID = "mingwuyan"

    /** 角色数据目录：`/sdcard/Download/chajian/mingwuyan/`。 */
    const val DATA_DIR = "/sdcard/Download/chajian/$RULE_ID"
}
