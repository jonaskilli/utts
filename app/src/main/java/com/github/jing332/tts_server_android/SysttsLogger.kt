package com.github.jing332.tts_server_android

import com.github.jing332.common.LogEntry
import com.github.jing332.common.LogLevel
import com.github.jing332.tts_server_android.constant.AppConst
import java.io.File
import java.io.FileWriter
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

object SysttsLogger {
    private val listeners = CopyOnWriteArraySet<LogListener>()

    // 降级兜底「继承日志」用（用户 09-14 定）：兜底原因不反查接口，直接继承上方已打出的
    // 原因日志——在此维护最近 W/E 主行环形缓冲；朗读规则/插件 console 的 W/E 经
    // Console.extraListeners 一并汇入（hookConsole 幂等挂钩，不抢 UI 的单值全局监听）
    private const val REASON_CAP = 32

    // 原因只认「新鲜」的：朗读请求排队出队，兜底请求落后失败日志几秒属正常，30 秒内才可能是
    // 本次兜底的原因。此前原因永久残留——一次「无效数据」能跨书挂半小时黄字（10-06 用户实锤：
    // 换书后章节标题的合法括号4路由还拿十几分钟前的旧原因报「密钥疑似失效」）
    private const val REASON_MAX_AGE_MS = 30_000L
    private class ReasonEntry(val entry: LogEntry, val at: Long)
    private val recentReasons = ArrayDeque<ReasonEntry>(REASON_CAP)
    private val consoleHooked = AtomicBoolean(false)

    // ===== 当前书章上下文（11-11 新增）=====
    // 用户实报「每本书我选择朗读规则和插件也不显示相关日志」：
    // 插件 / 朗读规则的 console 日志**不带书章字段**（Console.write 只填 level/message），
    // 于是日志页按书过滤 `filter { it.bookName == b }` 时全被丢弃——选单本书时一条都看不到。
    //
    // 为什么不从 MDC 取：console 日志产生在 JS 引擎线程，且规则分析阶段**早于**请求合成，
    // 那时 normalEvent 还没把书章写进 MDC，取到的必然是空。
    //
    // 解法：这里记住"最近一次见过的书章"（任何带书章的日志经过都会更新它），
    // console 日志到达时用它补上。空串表示还没读到过任何书章，保持旧行为。
    @Volatile
    private var currentBook: String = ""
    @Volatile
    private var currentChapter: String = ""

    /** 供 console 日志补书章用：返回 (book, chapter)，未读到过时两项均为空串 */
    fun currentBookChapter(): Pair<String, String> = currentBook to currentChapter

    fun log(entry: LogEntry) {
        // 更新书章上下文：任何带书名的条目都会刷新（主行与子行都算）
        if (entry.bookName.isNotBlank()) {
            currentBook = entry.bookName
            currentChapter = entry.chapterTitle
        }
        // 超时看门狗监测点：所有进入日志界面的日志都经过这里，
        // 在此通知看门狗（无锁、非阻塞），用于检测"超时后卡死"并自动重启。
        // 仅 WARN 级别（黄色）且含"超时"二字才触发计时，精确匹配真正的超时日志。
        TtsTimeoutWatchdog.onLog(entry.level, entry.message)
        recordReason(entry)
        listeners.forEach { it.log(entry) }
    }

    // ===== 插件 / 朗读规则日志落盘（11-10 用户令「我希望能够保存下来，而不是临时」）=====
    // 背景：Console.write 只把日志交给内存监听器（TtsLogViewModel 的 500 条缓冲），
    // **从来没写过文件**——进程一死就全丢。这里在 hookConsole 的同一个幂等钩子上补落盘。
    //
    // 为什么不写进 system_tts.log：那是 logback 的 RollingFileAppender 管的文件
    //（10MB 滚动、SysttsFilter 只放行 SystemTtsService 的 logger），
    // 我们再往里追加会绕开它的滚动与文件锁，正是问题 3「一片红」的同类风险。
    // 故独立两个文件，各自限大小、超限从尾巴截，绝不动 logback 的文件。
    private val consoleLogLock = Any()
    private const val CONSOLE_LOG_MAX_BYTES = 4L * 1024 * 1024
    private val consoleTimeFmt by lazy {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    }

    private fun consoleLogFile(entry: LogEntry): File? = runCatching {
        val dir = File(AppConst.externalFilesDir.parentFile, "cache/log")
        if (!dir.exists()) dir.mkdirs()
        File(dir, if (entry.isPluginLog) "plugin.log" else "speech_rule.log")
    }.getOrNull()

    /** 落盘一条 console 日志：只追加、失败静默（日志不该因写盘失败反过来打断朗读） */
    private fun persistConsole(entry: LogEntry) {
        if (!entry.isPluginLog && !entry.isSpeechRuleLog) return
        val f = consoleLogFile(entry) ?: return
        val level = when (entry.level) {
            LogLevel.ERROR -> "ERROR"
            LogLevel.WARN -> "WARN"
            LogLevel.INFO -> "INFO"
            LogLevel.DEBUG -> "DEBUG"
            else -> "TRACE"
        }
        synchronized(consoleLogLock) {
            runCatching {
                // 超限截半：读尾 1MB 重写，避免长期累积吃满存储（用户手机容量有限）
                if (f.exists() && f.length() > CONSOLE_LOG_MAX_BYTES) {
                    val tail = f.readBytes().let { it.copyOfRange(it.size / 2, it.size) }
                    f.writeBytes(tail)
                }
                // console 通道的 LogEntry 不带 time（到达时由 VM 事后补），落盘必须自带真实时间，
                // 否则重启回读后这些行没有时间戳、也进不了按日期/章节的归组
                val time = entry.time.ifEmpty {
                    java.time.LocalDateTime.now().format(consoleTimeFmt)
                }
                // ⚠️ 11-11：落盘也补书章。此前只写 `time | LEVEL | message`，
                // 重启回读后这些行没有书章 ⇒ 按书过滤时被丢弃（用户实报"不显示"）。
                // 格式与主日志统一：time | LEVEL | configId | roleName | §book | chapter | message
                // configId/roleName 留空占位，避免解析器把 § 当正文字符。
                val (bk, ch) = currentBookChapter()
                // ⚠️ 分隔符防护：书名/章名若含 " | " 或 "§"，写盘后会串位、回读解析错乱。
                // 文件名本身能包含这些字符（用户的书名是任意文本），故写入前替换掉。
                // 双竖线用全角｜替代（显示上几乎无差别），§ 同理。message 是最后一段、
                // 可含 " | "（解析时全量重组），保持原样。
                val safeBook = bk.replace("§", "Ｓ").replace("|", "｜")
                val safeChapter = ch.replace("§", "Ｓ").replace("|", "｜")
                FileWriter(f, true).use { w ->
                    w.append("$time | $level |  |  | §$safeBook | $safeChapter | ${entry.message}\n")
                }
            }.onFailure { android.util.Log.w("SysttsLogger", "persistConsole: ", it) }
        }
    }

    /** 服务启动时挂一次（幂等）：朗读规则/插件 console 日志也进原因缓冲 + 落盘 */
    fun hookConsole() {
        if (consoleHooked.compareAndSet(false, true)) {
            com.github.jing332.script.runtime.console.Console.extraListeners.add {
                recordReason(it)
                persistConsole(it)
            }
        }
    }

    private fun recordReason(entry: LogEntry) {
        if (entry.level != LogLevel.WARN && entry.level != LogLevel.ERROR) return
        if (entry.indent != 0) return // 子行从属于主行，不作为独立原因
        synchronized(recentReasons) {
            if (recentReasons.size >= REASON_CAP) recentReasons.removeFirst()
            recentReasons.addLast(ReasonEntry(entry, System.currentTimeMillis()))
        }
    }

    /** 时间窗内最近一条 W/E 主行（朗读规则 console 优先，其次插件，再次主流程），无则 null；
     *  返回纯文本（剥离 HTML 标签、压空白、截 40 字），供兜底子行拼接 */
    fun lastWarnReason(): String? {
        val now = System.currentTimeMillis()
        fun ReasonEntry.fresh() = now - at <= REASON_MAX_AGE_MS
        val e = synchronized(recentReasons) {
            recentReasons.lastOrNull { it.entry.isSpeechRuleLog && it.fresh() }?.entry
                ?: recentReasons.lastOrNull { it.entry.isPluginLog && it.fresh() }?.entry
                ?: recentReasons.lastOrNull { it.fresh() }?.entry
        } ?: return null
        val plain = e.message
            .removePrefix("[SpeechRule] ").removePrefix("[Plugin] ")
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\s+"), " ").trim()
        if (plain.isEmpty()) return null
        return if (plain.length > 40) plain.take(40) + "…" else plain
    }

    fun register(listener: LogListener) {
        listeners.add(listener)
    }

    fun unregister(listener: LogListener) {
        listeners.remove(listener)
    }

    fun interface LogListener {
        fun log(entry: LogEntry)
    }
}
