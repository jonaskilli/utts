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
    // 11-15 晚：本笔是**合成缓存命中**（没走插件）。日志页据此标一行「命中合成缓存」，
    // 否则命中段只有「获取成功」没有来路，看着和现场合成一模一样（用户实测被这点骗过：
    // 段 24–27 全部命中、都不出卡片，日志页第一张卡是段 28，误以为起点跳到倒数第二段）。
    val fromCache: Boolean = false,
    // 11-16：本笔属于**后台预热批次**（路 C 的备货请求），不是用户正在听的那一段。
    //
    // 为什么要在载荷上标：预热走的是同一条 requestAndProcess → 同一条事件派发，
    // 服务层没办法从别的字段把两者分开（paraIndex 两边都有、fromCache 只标命中）。
    // 真机实证 02:12:10–21 那 12 秒：日志页先后冒出 5 张「请求音频」卡，全部来自预热，
    // 用户正听的段 13 的卡反而排在它们后面 —— 看起来就是「日志顺序不对」。
    // 服务层见到本字段为 true 就不再落播放卡片（只在 DEBUG 日志留一行），
    // 预热进度改由「preload done: ok=x/M」那一行汇总。
    val isPreload: Boolean = false,
) {
    val text: String
        get() = params.text
}
