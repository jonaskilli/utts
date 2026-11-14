package com.github.jing332.tts.synthesizer

// roleName: 本片段的实时角色名（朗读规则分析，用户 09-08 定稿日志只认它），空串=无
// failoverFromTag: 本请求是「重试失败切换」从哪个标签转过来的，null=正常请求
// bookName / chapterTitle: 朗读规则 v1.7 透传的书名与章标题（用户 11-10「日志分小说、分章节」），
// 空串=旧规则或定位失败，日志不画书条/章节签
data class RequestPayload(
    val params: SystemParams,
    val config: TtsConfiguration,
    val roleName: String = "",
    val failoverFromTag: String? = null,
    val bookName: String = "",
    val chapterTitle: String = "",
    // 11-13：请求实例 id。一次「请求音频」生成一个，其成功/失败/取消子行沿用同一个值，
    // 日志页据此把子行挂回自己的卡（并发交错时不再按就近顺序错配）。
    // 重试/切备用的递归调用必须原样透传，否则同一条失败链会拆成两张卡。
    val reqId: String = "",
    // 11-14：段落位置（用户令「每个请求音频日志都标 1/5 这种段落标记」）。
    // 来源=U阅 synthesizeToFile 的 Bundle（paraIndex 0-based / paraTotal 本章总段数），
    // 经 SystemTtsService 读出随 MDC 落盘。两者皆 -1/0 = 旧版 U阅没带，日志不标段号。
    val paraIndex: Int = -1,
    val paraTotal: Int = 0,
) {
    val text: String
        get() = params.text
}
