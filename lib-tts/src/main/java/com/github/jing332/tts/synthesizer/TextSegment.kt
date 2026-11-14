package com.github.jing332.tts.synthesizer

// roleName: 朗读规则实时分析出的角色名（用户 09-08 定稿：日志只认实时名），
// 非多角色片段/旧规则为空串
// bookName / chapterTitle: 朗读规则 v1.7 起透传的书名与章标题（用户 11-10 令「日志分小说、分章节」）。
// 旧规则不返回这两个字段时为空串，日志按「无书章」处理，行为与以前完全一致。
data class TextSegment(
    val text: String,
    val tts: TtsConfiguration,
    val roleName: String = "",
    val bookName: String = "",
    val chapterTitle: String = "",
    // 11-14：段落位置（用户令「每个请求音频日志都标 1/5 这种段落标记」）。
    // 由调用方（U阅）随请求传入，本类只透传；-1/0=没带，日志不标。
    val paraIndex: Int = -1,
    val paraTotal: Int = 0,
) {}
