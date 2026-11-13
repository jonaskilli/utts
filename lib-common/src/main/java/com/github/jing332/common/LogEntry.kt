package com.github.jing332.common

import android.graphics.Color
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LogEntry(
    val level: Int,
    val message: String,
    val time: String = "",
    val wrapLine: Boolean = true,
    val isPluginLog: Boolean = false,
    val isSpeechRuleLog: Boolean = false,
    // 渲染层级：0=主行；>0=从属于上一主行的子行(缩进显示)。用于“请求→获取结果”这类成对日志的分组表达
    val indent: Int = 0,
    // 请求发生时的配置项 id：日志快捷面板凭它定位配置项（0=无关联，如插件日志/系统日志）
    val configId: Long = 0,
    // 朗读规则实时分析出的角色名（用户 09-08）：仅多角色对话请求有值，供快捷面板精确改绑该角色
    val roleName: String = "",
    // 相同原因合并计数（10-11 用户令「相同原因的失败视为相同，日志可以×2、×3」）：
    // 相邻且原因指纹相同的失败/警告合并为一条，本字段记出现次数（1=未合并，不显示 ×N）。
    // 只作用于 ERROR/WARN；成功行、正文行不折叠
    val repeatCount: Int = 1,
    // 合并组的末条时间（与 time 同格式）：行头显示「首条–末条」完整时间用
    //（10-11 用户令：两头都要完整，不能只写秒钟）。repeatCount<=1 时为空串
    val endTime: String = "",
    // 书名 / 章标题（11-10 用户令「日志分小说，然后还要分章节」）：
    // 由朗读规则 v1.7 随片段透传，请求行经 MDC 落盘。空串=旧规则/单语音/定位失败，
    // 日志页按「无书章」处理（不画书条、不画章节签），与旧行为完全一致
    val bookName: String = "",
    val chapterTitle: String = "",
    // 11-13：请求实例 id（一次「请求音频」= 一个 id，其成功/失败/取消子行同 id）。
    // 落盘在该行的 message 前缀「⟨req:xxx⟩」里（见 SystemTtsService.logS/normalEvent）。
    // 用途：并发两笔请求交错完成时，日志页按 id 把子行挂回自己那张卡——
    // 此前只按出现顺序就近挂，实测「爷爷，」卡上挂着「他说，」的 0.9s/50.7KB，
    // 自己的成功行反而掉成孤立裸行。空串=旧日志行/无关联行，退回顺序配对。
    val reqId: String = "",
) :
    Parcelable {
    fun getLevelChar(): String = level.toLogLevelChar()
}