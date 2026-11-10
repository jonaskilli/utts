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
) {
    val text: String
        get() = params.text
}
