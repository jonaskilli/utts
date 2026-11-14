package com.github.jing332.tts_server_android

import androidx.annotation.Keep
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import cn.hutool.core.date.LocalDateTimeUtil
import com.github.jing332.common.LogEntry
import com.github.jing332.common.toLogLevel
import com.github.jing332.tts_server_android.service.systts.SystemTtsService
import java.time.format.DateTimeFormatter
import java.util.TimeZone

@Keep
class SysttsFilter : Filter<ILoggingEvent>() {
    companion object {
        const val TAG = "SysttsFilter"
        const val ACTION_ON_LOG = "SystemFilter.SYSTTS_ON_LOG"
        private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        
        // 插件相关的 logger 名称列表
        private val PLUGIN_LOGGER_NAMES = listOf(
            "TtsPluginUiEngineV2",
            "TtsPluginEngineV2",
            "PluginTtsProvider",
            "PluginPreviewActivity",
            "PluginEditViewModel",
            "PluginLoginActivity",
            "JsBridgeInputStream"
        )

        // 系统 TTS 内部模块的 logger 名称（非插件，归入系统日志，默认显示在日志栏）
        private val SYSTTS_INTERNAL_LOGGER_NAMES = listOf(
            "SpeakerLoudnessManager",
            "ListManagerViewModel"
        )
    }

    override fun decide(event: ILoggingEvent): FilterReply {
        val isPluginLog = PLUGIN_LOGGER_NAMES.any { event.loggerName.contains(it) }
        val isSysttsInternal = SYSTTS_INTERNAL_LOGGER_NAMES.any { event.loggerName.contains(it) }

        return if (event.loggerName == SystemTtsService.TAG || isPluginLog || isSysttsInternal) {
            SysttsLogger.log(
                LogEntry(
                    level = event.level.toString().toLogLevel(),
                    time = LocalDateTimeUtil.of(event.timeStamp, TimeZone.getDefault())
                        .format(dateFormatter),
                    message = event.message,
                    isPluginLog = isPluginLog,
                    // 请求行经 MDC 携带的配置项 id（SystemTtsService 写"请求音频"时放入），
                    // 供日志快捷面板定位配置项；无关联日志该键不存在，回退 0
                    configId = event.mdcPropertyMap["configId"]?.toLongOrNull() ?: 0L,
                    // 实时角色名同走 MDC（多角色对话请求才有值）
                    roleName = event.mdcPropertyMap["roleName"] ?: "",
                    // 书/章（v1.7 规则，11-10 用户令「日志分小说、分章节」）：
                    // 同样随 MDC 落盘，重启后旧日志行仍可按书/章归组；无值即空串
                    bookName = event.mdcPropertyMap["bookName"] ?: "",
                    chapterTitle = event.mdcPropertyMap["chapterTitle"] ?: "",
                    // 11-14 修「实时页成功行没挂在卡片上、还多一条裸行」（用户两次截图实报）：
                    // 请求实例 id 早已随 MDC 落盘（logback.xml 有 %X{reqId} 列），
                    // **落盘侧**回读时能带上，于是 LogScreen 的重排正确；
                    // 但实时这条路径当初只补了 configId/roleName/书章，独独漏了 reqId
                    // ⇒ 实时页里请求主行 reqId 恒空 ⇒ regroupByReqId 的游程判据对主行
                    // 完全透明（`if (e.reqId.isEmpty()) continue`）⇒ 两笔并发时后一张卡
                    // 把前一张顶掉，成功行挂到别人的卡上，另一条成功行落成裸行。
                    // 主行与子行（logS 直传 reqId）同源，补上这一行两路径才一致。
                    reqId = event.mdcPropertyMap["reqId"] ?: "",
                    // 11-14：段落位置同走 MDC（用户令「每个请求音频日志都标 1/5」）。
                    // 落盘侧回读能带上（logback.xml 已加两列），实时这条路径必须同样补——
                    // 否则实时页所有卡段号恒空、只有重启回读后才出现，两条路径不一致。
                    // 无值（旧版 U阅/子行）时 MDC 无该键 → 默认 -1/0，渲染不标。
                    paraIndex = event.mdcPropertyMap["paraIndex"]?.toIntOrNull() ?: -1,
                    paraTotal = event.mdcPropertyMap["paraTotal"]?.toIntOrNull() ?: 0,
                )
            )

            FilterReply.ACCEPT
        } else FilterReply.DENY
    }
}