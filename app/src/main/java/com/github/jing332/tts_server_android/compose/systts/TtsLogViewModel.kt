package com.github.jing332.tts_server_android.compose.systts

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drake.net.utils.withMain
import com.github.jing332.common.LogEntry
import com.github.jing332.common.LogLevel
import com.github.jing332.common.toLogLevel
import com.github.jing332.common.utils.runOnUI
import com.github.jing332.script.runtime.console.Console
import com.github.jing332.database.dbm
import com.github.jing332.tts_server_android.SysttsLogger
import com.github.jing332.tts_server_android.service.systts.help.ChapterOrderFile
import com.github.jing332.tts_server_android.constant.AppConst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter

class TtsLogViewModel : ViewModel() {
    companion object {
        const val TAG = "TtsLogViewModel"

        /** 完整时间戳（logback 主日志 / console 文本日志）：`2026-11-19 00:38:56.648` */
        private val TS_FULL = Regex("^\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.?\\d*")

        /** 短时间戳（U阅 appLog 同款）：`11-19 00:38:56` */
        private val TS_SHORT = Regex("^\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d")

        // 内存日志滑动窗口上限：旧值50万条，插件日志每条可达数KB，长会话直接吃满256MB堆
        // （装机实测 OOM：画列表项时连80字节都分配失败）。完整历史仍由磁盘
        // cache/log/system_tts.log 承载，内存只保最近段。超限后按批裁最旧，不清空全部
        const val MAX_LOGS = 20000

        // 裁剪批量：让 size 长到 MAX+PRUNE_BATCH 再一次裁回 MAX，均摊掉逐条删头部的数组搬移开销
        const val PRUNE_BATCH = 1000

        // 插件/朗读规则日志独立缓冲上限（用户 09-08：不再混入主列表刷屏/挤占窗口）。
        // 条数 500 + 字符总量熔断 30 万（双保险：单条可长达数KB~十几KB，纯条数上限挡不住堆积）
        const val AUX_MAX = 500

        // 独立缓冲裁剪批量
        const val AUX_PRUNE = 100

        // 独立缓冲字符总量熔断线（message 字符数合计；超限按最旧裁，UTF-16 下约 0.6MB/缓冲）
        const val AUX_CHAR_BUDGET = 300_000L

        // 主流程日志监听器是否已全局注册（见 RouteSink 注释：防重复注册导致同一条日志多份）
        private val registered = java.util.concurrent.atomic.AtomicBoolean(false)

        // 支持的日志级别
        val LOG_LEVELS = listOf(
            LogLevel.ERROR,
            LogLevel.WARN,
            LogLevel.INFO,
            LogLevel.SUCCESS,
            LogLevel.DEBUG,
            LogLevel.TRACE
        )

        // 11-11 用户令：日志目录搬到 Download/chajian/log（文件管理器直接可见）；11-23 归拢到 chajian/uyue-utts/logs/utts。
        // 统一取 AppConst.logDir——它内部带可写探测，权限没给时自动退回旧私有目录。
        // ⚠️ 别在这里再写死 "cache/log"：那样会和 logback、子行、console 四处分叉。
        val file = File(AppConst.logDir, "system_tts.log")
    }

    val logs = mutableStateListOf<LogEntry>()

    // 插件/朗读规则日志独立缓冲（防刷屏/防OOM，09-08 保留）：始终记录、不挤占主列表窗口；
    // 勾选"插件日志/朗读规则日志"= 在主时间流中追加显示该缓冲（用户 09-09：恢复 09-08 改前的
    // 混排行为——看过程必须与"请求音频/获取成功"等结果流对照，"切换显示"把两者切断了）
    val pluginLogs = mutableStateListOf<LogEntry>()
    val speechRuleLogs = mutableStateListOf<LogEntry>()

    // 日志级别筛选（存储选中的日志级别 Int 值）
    val selectedLevels = mutableStateListOf<Int>()

    // @Deprecated 11-11：筛选弹窗整体删除，弹窗状态已无消费者。保留字段是为了避免
    // 其它分支/并行改动引用它时编译报错，下批确认无引用后连同 LogFilterDialog.kt 一起清。
    @Deprecated("筛选已挪进日志页吸顶区（11-11），此状态不再使用")
    val showFilterDialog = mutableStateOf(false)

    // 调试模式开关 - 在主时间流中追加/隐藏插件日志（默认隐藏，用户手动开启）
    val showPluginLogs = mutableStateOf(false)

    // 调试模式开关 - 在主时间流中追加/隐藏朗读规则日志（默认隐藏，用户手动开启）
    val showSpeechRuleLogs = mutableStateOf(false)

    // ===== 书 / 章范围（11-10 用户令「日志分小说、分章节」）=====
    // 语义：空值=不限；scopeBook 非空=只看该书；scopeChapter 非空=只看该章。
    // ⚠️ 这里的「切书」只切**日志查看范围**，绝不写 cunfang.txt、不动角色管理的当前书
    //（用户 11-10 明确：「切书就在日志里生效」）——所以状态只活在内存里、不持久化：
    // 重进日志页回到「全部」，避免上次的筛选把人困在一本书里看不到新日志。
    val scopeBook = mutableStateOf<String?>(null)
    val scopeChapter = mutableStateOf<String?>(null)

    /** 日志里出现过的书（按首次出现顺序，供切换弹窗列表用） */
    val knownBooks: List<String>
        get() = logs.asSequence()
            .mapNotNull { it.bookName.takeIf { b -> b.isNotBlank() } }
            .distinct()
            .toList()

    /**
     * 某本书下出现过的章，带每章条数。
     *
     * ⚠️ 排序口径（11-11 用户问「章节顺序不会错吧」后修正）：
     * 原来按「首次出现在日志流里的顺序」——顺序朗读时正确，**跳章就乱**
     * （先听第 15 章再回第 3 章 → 列表变成 15、3、4…）。
     * 现在改为按 `chajian/<ruleId>/data.json` 的书内真实章序排（与朗读规则判断章节同源），
     * 日志里没见过但书里有的章不列；书内序里找不到的章（旧日志/改过名）保序追在末尾。
     * 数据文件读不到时自动退回「首次出现顺序」，不会因此让列表变空。
     */
    fun chaptersOf(book: String): List<Pair<String, Int>> {
        val counts = logs.asSequence()
            .filter { it.bookName == book }
            .mapNotNull { it.chapterTitle.takeIf { c -> c.isNotBlank() } }
            .toList()
            .groupingBy { it }
            .eachCount()
        if (counts.isEmpty()) return emptyList()
        val seenOrder = LinkedHashSet<String>()
        logs.forEach { if (it.bookName == book && it.chapterTitle.isNotBlank())
            seenOrder.add(it.chapterTitle) }

        // 真实章序：读 data.json（内部按 mtime/size 缓存，切书前只读一次）
        val ruleId = runCatching {
            dbm.speechRuleDao.getAllEnabledWithoutCode().firstOrNull()?.ruleId
        }.getOrNull().orEmpty()
        val bookOrder = if (ruleId.isBlank()) emptyList()
        else runCatching {
            ChapterOrderFile.readChapterOrder(ruleId)
        }.getOrDefault(emptyList())

        val ordered = ChapterOrderFile.sortByBookOrder(bookOrder, seenOrder.toList())
        return ordered.mapNotNull { name ->
            counts[name]?.let { name to it }
        }
    }

    /**
     * 「按正文顺序排」（11-21 14:2x 用户拍板）用的章序数据：书名 → 章名列表（书内真实序）。
     * 与 chaptersOf 同源（ChapterOrderFile/data.json），供 LogGroups.build 的排序键用。
     * 只在朗读规则可用时返回单书映射；规则/文件不可用返回 null → build 退回时间原序。
     */
    fun chapterOrderMap(): Map<String, List<String>>? {
        val ruleId = runCatching {
            dbm.speechRuleDao.getAllEnabledWithoutCode().firstOrNull()?.ruleId
        }.getOrNull().orEmpty()
        if (ruleId.isBlank()) return null
        val order = runCatching { ChapterOrderFile.readChapterOrder(ruleId) }
            .getOrDefault(emptyList())
        if (order.isEmpty()) return null
        val book = runCatching { ChapterOrderFile.readBookName(ruleId) }.getOrDefault("")
        return if (book.isBlank()) null else mapOf(book to order)
    }

    /** 清掉书章范围（筛选弹窗「全部书籍」/ 切书后回到不限） */
    fun clearScope() {
        scopeBook.value = null
        scopeChapter.value = null
    }

    // DEBUG(D) 级开关（用户 09-10 定稿）：**默认开启**——插件开发靠 console.debug 调试；
    // 嫌吵的用户在筛选里关掉后 D 级完全不进列表。持久化到配置，避免每次启动重关。
    val showDebugLogs = com.github.jing332.tts_server_android.conf.SystemTtsConfig.isDebugLogEnabled

    // 与系统日志同款时间戳格式：合并排序按字符串比较即可保持时序（等宽、字典序=时间序）
    private val auxTimeFormatter =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)

    val filteredLogs: List<LogEntry>
        get() {
            // 勾选=全量混排（用户 09-09）：主列表与勾选的辅助缓冲按时间戳合并。
            // 不勾选=不混入（用户 10-03 拍板，推翻 09-09 的 ERROR/WARN 旁路放行）：
            // 插件/规则的黄警红错常年混在主时间流里搅乱主日志。唯一例外=朗读规则的
            // ERROR 红字仍放行（10-03 二次拍板）——「无可用API密钥/请求失败/降级兜底」
            // 这类规则侧报障是排障主线索；WARN 黄字与插件日志（含 ERROR）一律不混。
            // app 侧失败文案（获取失败/源错误/超时）本就在主日志，报障链不中断
            val showPlugin = showPluginLogs.value
            val showRule = showSpeechRuleLogs.value
            val levels = selectedLevels
            // DEBUG 开关关闭时：主列表的 D 级一并隐藏（插件/规则缓冲只在勾选时参与）
            val showDebug = showDebugLogs.value
            fun keep(e: LogEntry) =
                // 11-18 二改（用户令，推翻同日正交化）：「错误」=收窄**全部行**——
                // 主日志、朗读规则、插件一视同仁，勾错误后只留 ERROR/WARN。
                // 此前「混入行豁免级别过滤」会让规则 INFO 在错误视图里继续刷屏，与用户
                // 「点击错误应该只显示错误的筛选」的预期相反。混入开关=来源开关（勾了
                // 才把规则/插件缓冲**并进**列表），级别筛选=对合并后的列表整体收窄。
                // DEBUG 开关独立生效（关掉就看不到 D 级，混入行也一样）。
                (levels.isEmpty() || e.level in levels) &&
                    (showDebug || e.level != LogLevel.DEBUG)

            val pluginPart = if (showPlugin) pluginLogs else emptyList<LogEntry>()
            val rulePart =
                if (showRule) speechRuleLogs
                else speechRuleLogs.filter { it.level == LogLevel.ERROR }

            // 都不勾（或勾了但缓冲为空）：纯主列表直通，不建列表不排序
            if (pluginPart.isEmpty() && rulePart.isEmpty())
                return logs.filter { keep(it) }

            val source = ArrayList<LogEntry>(logs.size + pluginPart.size + rulePart.size)
            source.addAll(logs)
            source.addAll(pluginPart)
            source.addAll(rulePart)
            // 稳定排序：同毫秒内保持各缓冲内的到达顺序
            val merged = source.sortedBy { it.time }
            return merged.filter { keep(it) }
            // 注：搜索词不做过滤——搜索是定位(跳转+高亮)，由 TtsLogScreen/LogScreen 处理，
            // 保留完整列表便于查看匹配项的前后文
        }

    // 按类型路由日志到对应缓冲（主列表 / 插件缓冲 / 规则缓冲）。
    // 双保险裁剪：条数上限 + 字符总量熔断（超长条目堆积时按最旧裁，与条数无关）
    private val pluginChars = java.util.concurrent.atomic.AtomicLong()
    private val ruleChars = java.util.concurrent.atomic.AtomicLong()

    // Console 通道（插件 JS / 朗读规则 JS）的 LogEntry 不带 time，到达时补打时间戳，
    // 供勾选后与主列表按时间混排（logback 通道的日志自带时间戳，走 else 分支不覆盖）
    /**
     * 给 console 日志（插件/朗读规则）补书章（11-11 新增）。
     *
     * 这两类日志由 Console.write 产生，只有 level/message，没有书章；
     * 日志页按书过滤时 `filter { it.bookName == b }` 会把它们全部丢掉。
     * 这里用 SysttsLogger 记录的"当前书章"补上（规则分析阶段 MDC 里还没有书章，
     * 所以不能从 MDC 取）。
     *
     * 已经是带书章的条目不覆盖（幂等）；上下文为空时保持原样，退化为旧行为。
     */
    private fun stampBookChapter(entry: LogEntry): LogEntry {
        if (entry.bookName.isNotBlank()) return entry
        // 11-22 02:4x（用户实报「书章就绪那行位置不对」）：自描述行用自己的书章。
        // 「【书章就绪】书 · 章」自带权威书章（规则刚从 data.json 解析出来），
        // 比上下文猜的准——跨章第一笔播放请求还没落日志时，上下文仍是上一章，
        // 会把这条章首宣告排到上一章末尾。段号置 -1=章首，排在它宣告那一章正文之前。
        // 与 persistConsole（落盘路径）同一口径，两路同源。详见 SysttsLogger.parseChapterReadyLine。
        SysttsLogger.parseChapterReadyLine(entry.message)?.let { (book, chapter) ->
            return entry.copy(bookName = book, chapterTitle = chapter, paraIndex = -1)
        }
        val (book, chapter) = SysttsLogger.currentBookChapter()
        if (book.isBlank()) return entry
        // 11-21 14:5x（用户终裁「全按正文顺序 100% 不串」）：console 日志（插件/
        // 朗读规则）补上当前段号——排序键需要 paraIndex 才能把它插进正确段的位置，
        // 否则只能堆章尾。段号随书章上下文同生命周期（主流程请求行推进）。
        val para = SysttsLogger.currentParaIndex()
        return entry.copy(bookName = book, chapterTitle = chapter, paraIndex = para)
    }

    @Synchronized
    private fun stampTime(entry: LogEntry): LogEntry =
        if (entry.time.isEmpty())
            entry.copy(time = auxTimeFormatter.format(System.currentTimeMillis()))
        else entry

    // 「相邻」判据必须看**到达序**，不是各缓冲自己的末条（10-12 用户实拍实锤）：
    // 插件/朗读规则日志走独立缓冲，缓冲内相邻 ≠ 屏幕上相邻——千问插件持续 404 时，
    // 34 个**不同句子**各失败一次，在插件缓冲里是连续 34 条同指纹 ⇒ 被折成「失败 ×34」
    //（截图 11:48:50–11:49:47），而屏幕上这 34 条之间夹着 源错误/重试/切备用/成功。
    // 这里记住到达序上最后一次落库的条目与它属于哪条缓冲：插件/规则只在「上一条就是本缓冲末条」
    // 时才允许折叠；主流程任何一条进来都把相邻打断。
    private var lastArrival: LogEntry? = null
    private var lastArrivalKind: Int = -1 // 0=主流程 1=插件 2=朗读规则

    private fun routeEntry(entry: LogEntry) {
        val isPlugin = entry.isPluginLog
        val isRule = entry.isSpeechRuleLog
        when {
            isPlugin || isRule -> {
                val stamped = stampTime(entry)
                val target = if (isPlugin) pluginLogs else speechRuleLogs
                val chars = if (isPlugin) pluginChars else ruleChars
                val kind = if (isPlugin) 1 else 2
                // 相邻同因合并（10-11 用户令「相同原因的失败视为相同，日志可以×2、×3」）：
                // 与末条同指纹则并入计数，不再新增条目。10-05 曾因「×2 让人困惑」撤掉折叠，
                // 但那时的真根因是 SysttsLogger 监听器重复注册（同一条日志被投递两遍，
                // 已在 1bf6a74 修为 RouteSink 单例）——现在折叠反映的是真实重复次数。
                // ⚠️ 只有「屏幕上相邻」才折（10-12）：见上方 lastArrival 注释
                val contiguous = lastArrivalKind == kind && lastArrival === target.lastOrNull()
                val merged = if (contiguous) mergeInto(target, stamped) else null
                if (merged == null) {
                    target.add(stamped)
                    chars.addAndGet(stamped.message.length.toLong())
                } else {
                    // 并入末条：先扣旧串长度、再加新串长度（message 保留首条原文，
                    // 只更新计数与末条时间，避免长度记账漂移）。
                    // ⚠️ chars 是 AtomicLong，addAndGet 只收 Long——两个 Int 相减必须显式 toLong()
                    //（CI 38138740502 实锤：Argument type mismatch: actual Int, but Long was expected）
                    chars.addAndGet((merged.newLen - merged.oldLen).toLong())
                }
                lastArrival = target.lastOrNull()
                lastArrivalKind = kind
                // 条数裁剪
                val overflow = target.size - AUX_MAX
                if (overflow >= AUX_PRUNE) {
                    repeat(overflow) {
                        val removed = target.removeAt(0)
                        chars.addAndGet(-removed.message.length.toLong())
                    }
                }
                // 字符总量熔断（防超长条目堆积）
                while (chars.get() > AUX_CHAR_BUDGET && target.size > 1) {
                    val removed = target.removeAt(0)
                    chars.addAndGet(-removed.message.length.toLong())
                }
            }
            else -> {
                // 主流程日志同样折叠（10-11 用户令「不管是插件还是朗读规则还是正常的日志」）：
                // 应用层失败链重试风暴同因刷屏也收成一条 ×N。
                // 主流程沿用「重试行不打断相邻」的老口径（同一次请求内的重试链要折出 ×2）
                val merged = mergeInto(logs, entry)
                if (merged == null) {
                    logs.add(entry)
                }
                lastArrival = logs.lastOrNull()
                lastArrivalKind = 0
                val overflow = logs.size - MAX_LOGS
                if (overflow >= PRUNE_BATCH) {
                    repeat(overflow) { logs.removeAt(0) }
                }
            }
        }
    }

    /** 合并结果：新长度与旧长度之差（供字符记账） */
    private class MergeResult(val oldLen: Int, val newLen: Int)

    /** 重试行判据（「开始第 N 次重试」）：折叠时它不算「打断相邻」的日志 */
    private fun isRetryRow(e: LogEntry): Boolean =
        e.level == LogLevel.WARN && e.message.contains("次重试")

    /**
     * 相邻同因合并（10-11 用户令）：
     * 与本条**同级别**、且**原因指纹相同**的最近一条失败/警告，把本条并入它——
     * 计数 +1、记下末条时间（行头显示「首末完整时间」），message 保留首条原文。
     * 返回 null 表示未合并（调用方照常 add）。
     *
     * 「相邻」的口径（关键）：**重试行不打断相邻**。真实失败链是
     *   请求音频 → 获取失败 → 「开始第 1 次重试」 → 获取失败 → 到达上限 → 切兜底 → 成功
     * 两次「获取失败」中间夹着重试行；若按字面严格相邻，用户要的 ×2 永远折不出来
     *（10-11 模拟实锤：严格口径下两条获取失败各 ×1）。重试行属于同一条失败链的
     * 过程噪声，故向后越过它去找上一条失败；合并时把被越过的重试行一并吸收
     *（次数已由 ×N 表达，留着重试行反而是同一件事说两遍）。
     * 非重试行的其他日志（切备用/成功/请求音频）照旧打断相邻——这正是用户要的
     *「相邻」的本意：不跨事件合并。
     *
     * 指纹口径（`failureFingerprint`）：去 HTML 标签 → 去首尾空白 → 去掉插件内部
     * 重试序号 `[0]`/`[1]`（序号不是失败原因，不去掉则同一次失败的各次重试会被
     * 当成不同原因而折不起来）。数字本身保留：HTTP-404 与 HTTP-401 是不同原因。
     */
    private fun mergeInto(target: MutableList<LogEntry>, entry: LogEntry): MergeResult? {
        if (entry.level != LogLevel.ERROR && entry.level != LogLevel.WARN) return null
        val fp = failureFingerprint(entry.message) ?: return null
        // 向后越过重试行，找最近一条非重试日志
        var i = target.lastIndex
        while (i >= 0 && isRetryRow(target[i])) i--
        if (i < 0) return null
        val last = target[i]
        if (last.level != entry.level) return null
        if (fp != failureFingerprint(last.message)) return null
        val oldLen = last.message.length
        val merged = last.copy(
            repeatCount = last.repeatCount + 1,
            // 末条时间：首末范围用。末条本身也可能是合并来的，它的 endTime 已是更新的那个
            endTime = entry.time.ifEmpty { last.endTime },
        )
        target[i] = merged
        // 吸收被越过的重试行（i 之后的都只可能是重试行——上面循环保证了这一点）
        if (i < target.lastIndex) {
            repeat(target.size - 1 - i) { target.removeAt(target.lastIndex) }
        }
        return MergeResult(oldLen, merged.message.length)
    }

    /** 失败原因指纹（同因判据）；空正文返回 null=不参与合并 */
    private fun failureFingerprint(message: String): String? {
        val s = message
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\[\\d+]"), "") // 插件内部重试序号 [0]/[1]
            .trim()
        return s.ifEmpty { null }
    }
    
    // 11-15：`toggleLevel()` 随分级筛选条一起删除——它的唯一调用点就在那条筛选条自己的
    // 胶囊里（自指死链），级别筛选从未有过真正的入口。`selectedLevels` 保留为
    // 「只看错误」三态开关的存储底座（showAllLevels / showErrorsOnly / isErrorsOnly）。
    
    fun clearFilter() {
        selectedLevels.clear()
    }

    /** 显示范围=全部（用户 09-08 简化：级别键退役，只留三态） */
    fun showAllLevels() {
        selectedLevels.clear()
    }

    /** 显示范围=只看错误+警告（排障态） */
    fun showErrorsOnly() {
        selectedLevels.clear()
        selectedLevels.add(LogLevel.ERROR)
        selectedLevels.add(LogLevel.WARN)
    }

    /** 当前是否为"只看错误"态 */
    fun isErrorsOnly(): Boolean =
        selectedLevels.toList() == listOf(LogLevel.ERROR, LogLevel.WARN)

    /**
     * 清空日志。
     *
     * ⚠️ 11-10 修「清空后继续朗读，日志出现一片红，重启 TTS 才正常」：
     * 旧实现在这里 `f.delete()` 掉了 `system_tts_*.log` 等滚动备份——那是 **logback 的
     * RollingFileAppender 正在持有的文件**。删掉后它的句柄失效，之后每次写日志都抛异常；
     * 异常又被当成一条 ERROR 日志写回日志界面，于是自激成一片红，直到服务重启、
     * logback 重新打开文件才恢复——与用户「重启 TTS 就好了」的观察完全吻合。
     *
     * 现在的口径：
     * ① 主文件只用 **截断**（FileWriter(false) 保留 inode，logback 句柄继续有效），**绝不 delete**；
     * ② 滚动备份与 debug/crash 文件一律不动，交给各自的滚动策略老化
     *   （system_tts 10MB/1天、debug 10MB/2份、crash 2MB/3份，本来就会自己清理）；
     * ③ 清空本身失败不再回灌进日志列表（那会制造新的红字），只写 Logcat。
     *
     * ⚠️ 11-11 二次修（用户实报「我都已经全都删除了，还冒出莫名其妙的获取成功日志」）：
     * ①只截断了主文件，但页面回读还有 **三个** 文件：
     *   `system_tts_child.log`（11-11 合并回主日志前的旧成功子行文件；升级用户盘上可能还有）、
     *   `plugin.log` / `speech_rule.log`（pullConsoleFile 读）。
     * 内存清了、这几份没清 ⇒ 下次进日志页（或 VM 重建、切书重载）pull() 又把旧行读回来，
     * 表现就是"删掉的成功日志又自己冒出来"，而且**只有成功子行、没有对应的请求行**
     *（请求行在主文件里，已被截断）——正是用户看到的那片孤立「获取成功」。
     * ②这三份都不是 logback 持有的句柄（子行是每次 FileWriter(append) 现开现关，
     *   console 是 persistConsole 里同款写法），因此同样用**截断**而不是 delete，
     *   既清干净也不会让写入端句柄失效。
     */
    fun clear() {
        logs.clear()
        pluginLogs.clear()
        speechRuleLogs.clear()
        // 主日志 + 三份回读来源一起截断：清空必须是"清空所有会被读回来的东西"，
        // 只清主文件等于给下次进页面留了一份"复活数据"。
        clearAuxFile(file)
        // 旧版遗留文件：已合并回主日志后不再产生新内容，但升级用户盘上可能还有，
        // 顺手清掉（留着只会让人以为"还有个文件没用上"）
        clearAuxFile(File(file.parentFile, "system_tts_child.log"))
        clearAuxFile(consoleFile(isPlugin = true))
        clearAuxFile(consoleFile(isPlugin = false))
        // ⚠️ 11-11 复盘补漏：弹窗里还列着「调试日志」「崩溃日志」和一堆轮转旧文件，
        // 但清空**从来没碰过它们**——用户在弹窗里看得见、点全部清空却清不掉，
        // 正是「清空只清一半」这个毛病的又一次。这里把目录里其余文件也一并处理。
        clearOtherLogFiles(
            liveNames = listOf(
                file.name, "system_tts_child.log", "plugin.log", "speech_rule.log"
            )
        )
    }

    /**
     * 清掉日志目录里除 [liveNames] 以外的文件（11-11 复盘补漏）。
     *
     * 分两种：logback 正持句柄的（debug.log / crash.log）**截断**；其余（轮转出来的
     * *_日期.log 备份）**直接删**——它们已经是"另一个文件"，logback 早切换过去了，
     * 留着只是占地方。
     */
    private fun clearOtherLogFiles(liveNames: List<String>) {
        val dir = file.parentFile ?: return
        val live = liveNames.toSet()
        val heldByLogback = setOf("debug.log", "crash.log")
        runCatching {
            dir.listFiles()?.forEach { f ->
                if (!f.isFile || f.name in live) return@forEach
                runCatching {
                    if (f.name in heldByLogback) FileWriter(f, false).use { it.write(CharArray(0)) }
                    else f.delete()
                }
            }
        }.onFailure { Log.e(TAG, "clearOtherLogFiles: ", it) }
    }

    /**
     * 按书清理日志（11-15 晚用户令：吸顶区书框右端加红叉，点了就清这本）。
     *
     * 三处一起改，少一处就"清不干净、下次进页面又冒出来"：
     *  1. 内存：logs/pluginLogs/speechRuleLogs 里匹配的条目移除，页面立刻少掉；
     *  2. 磁盘 plugin.log / speech_rule.log：现开现关写的，直接读→过滤→重写；
     *  3. 磁盘 system_tts.log：logback 持着句柄。先把它从 root logger 摘下来
     *     （appender.stop() 会关掉文件），过滤重写完成后再 start() + addAppender 挂回。
     *     摘挂之间只有几十毫秒，这期间的日志会走 logcat/debug.log、不进 system_tts.log
     *     —— 丢的是"按下红叉这一下"的几行，换来的是能安全重写（不停写就只能整份清空）。
     */
    fun clearByBook(book: String) = clearMatchingLogs(book, null)

    /** 按章清理日志（章框右端红叉）。 */
    fun clearByChapter(book: String, chapter: String) = clearMatchingLogs(book, chapter)

    private fun clearMatchingLogs(book: String, chapter: String?) {
        val hit: (String, String) -> Boolean = { b, c ->
            b == book && (chapter == null || c == chapter)
        }
        viewModelScope.launch(Dispatchers.IO) {
            withMain {
                logs.removeAll { hit(it.bookName, it.chapterTitle) }
                pluginLogs.removeAll { hit(it.bookName, it.chapterTitle) }
                speechRuleLogs.removeAll { hit(it.bookName, it.chapterTitle) }
            }
            listOfNotNull(consoleFile(isPlugin = true), consoleFile(isPlugin = false))
                .forEach { rewriteFiltered(it, hit) }
            rewriteMainFiltered(hit)
        }
    }

    /** 主日志「按书/章过滤重写」：摘 appender → 重写 → 挂回（见 clearByBook 注释）。 */
    private fun rewriteMainFiltered(hit: (String, String) -> Boolean) {
        runCatching {
            val ctx = org.slf4j.LoggerFactory.getILoggerFactory()
                as? ch.qos.logback.classic.LoggerContext
            val root = ctx?.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
            val app = root?.getAppender("SystemTTS")
            if (root == null || app == null) {
                // 拿不到 logback 句柄（配置被改/换了实现）：退回直接重写，宁可可能丢后续写入
                rewriteFiltered(file, hit)
                return
            }
            root.detachAppender(app)
            runCatching { app.stop() }
            try {
                rewriteFiltered(file, hit)
            } finally {
                runCatching {
                    app.start()
                    root.addAppender(app)
                }.onFailure { Log.e(TAG, "重新挂回 SystemTTS appender 失败: ", it) }
            }
        }.onFailure { Log.e(TAG, "rewriteMainFiltered: ", it) }
    }

    /**
     * 文本日志过滤重写（plugin/speech_rule/主日志通用）。
     * 行格式：`时间 | 级别 | configId | 角色 | §书名 | 章名 | 正文`；正文里可能有 " | "，
     * 所以 split 必须 limit=7，把正文整段留给最后一段。
     */
    private fun rewriteFiltered(f: File, hit: (String, String) -> Boolean) {
        runCatching {
            if (!f.exists() || f.length() == 0L) return
            val kept = f.readLines().filterNot { line ->
                val p = line.split(" | ", limit = 7)
                if (p.size < 7) return@filterNot false
                hit(p[4].trim().removePrefix("§"), p[5].trim())
            }
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = "\n"))
            if (!tmp.exists()) return
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }.onFailure { Log.e(TAG, "rewriteFiltered(${f.name}): ", it) }
    }

    /**
     * 单删一个日志文件（11-11 用户令「每一项可以单独删除」）。
     *
     * ## 为什么分两种处理
     * **logback 正持着句柄的文件**（system_tts.log / debug.log / crash.log）只能截断：
     * 一旦 delete，RollingFileAppender 的句柄失效，之后每次写都抛异常，异常又被当 ERROR
     * 回灌日志界面 → 自激成一片红，直到重启（11-10 踩过，代价是用户以为坏了）。
     * 其余文件（子行、console、轮转出来的备份）都是「每次现开现关」的写法，直接删掉干净利落，
     * 不会留下 0 字节的空壳占着列表。
     *
     * ## 删完要重读
     * 内存里三个缓冲是磁盘的投影，单删一个文件后立刻清空+重新 pull，
     * 保证界面显示的就是磁盘上真实剩下的东西（而不是删了还挂在列表里）。
     */
    fun clearFile(f: File) {
        if (!f.exists()) return
        val heldByLogback = f.name == "system_tts.log" ||
            f.name == "debug.log" ||
            f.name == "crash.log"
        runCatching {
            if (heldByLogback) FileWriter(f, false).use { it.write(CharArray(0)) }
            else f.delete()
        }.onFailure { Log.e(TAG, "clearFile: ", it) }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                withMain {
                    logs.clear()
                    pluginLogs.clear()
                    speechRuleLogs.clear()
                }
                pull()
            }.onFailure { Log.e(TAG, "clearFile reload: ", it) }
        }
    }

    /** 截断一个日志文件（保留 inode，不用 delete——写入端句柄继续有效） */
    private fun clearAuxFile(f: File?) {
        if (f == null) return
        runCatching {
            if (f.exists()) FileWriter(f, false).use { it.write(CharArray(0)) }
        }.onFailure {
            // 只进 Logcat：这里若 logs.add(ERROR)，用户会看到「清空失败」的假红字，
            // 而真正的原因（文件被占用等）他既看不懂也没法处理
            Log.e(TAG, "clear: ", it)
        }
    }


    private fun toLogEntry(line: String): LogEntry {
        // 11-13 修「获取成功」孤行（用户实报：上一行请求音频已有成功标志，下一行又
        // 单独一条获取成功）：logS 落盘时 indent **没写进行格式**（见
        // SystemTtsService.logS 的 FileWriter 段），回读解析不出子行标记 →
        // LogScreen 的 indent>0 分支进不去 → 配置项也是空（configId 留空占位）→
        // 既不进组也不开卡，落成独立裸行。live 内存路径带 indent=1 显示正常，
        // 只有历史行/重启后/清空重读可见此问题。
        // 修法（最小侵入）：「获取成功：」前缀只由 logS(SUCCESS, indent=1) 产生，
        // 回读时据此还原子行身份；若日后新增其他子行前缀，走落盘格式加字段的路。
        //
        // 11-16 用户实报（截图）：前几段明明已经播完，卡片上却一直挂着「请求中」。
        // 根因＝**前缀集合没跟着 11-15 的新前缀更新**：
        //   11-15 晚「缓存命中也出日志卡」把命中行的前缀改成了
        //   `获取成功（缓存）：`（SystemTtsService 1342 注释），而这里只认
        //   `获取成功：`（后面紧跟全角冒号）。命中行于是**不带 indent** →
        //   `groupHasSuccessSub`（要求 indent>0）判不出成功子行 →
        //   LogScreen 的 markPending 认定「本组至今没有结局」→ 挂「请求中」。
        // 真机实锤（04:17 那批）：被判「请求中」的卡 = 段 0/1/2/2/3，
        // 与 `获取成功（缓存）` 的 5 行**逐一对应**；现场合成的 16 行无一误判。
        // 修法：前缀判据覆盖两种写法（命中行同样由 logS(SUCCESS, indent=1) 产生，
        // 只是文案不同），仍然只认"获取成功"这一族，不扩大到其它 SUCCESS。
        // 11-16 终稿：前缀定「（预合成）」；历史前缀「（缓存）」「（本地缓存）」
        // 「（UTTS缓存）」一并认——旧日志文件里的成功行也要能还原成子行
        //（否则「请求中」误挂复发）
        val isAcquiredSuffix = line.contains("获取成功：") ||
            line.contains("获取成功（预合成）：") ||
            line.contains("获取成功（UTTS缓存）：") ||
            line.contains("获取成功（本地缓存）：") || line.contains("获取成功（缓存）：")
        // 11-13：带请求实例 id 的新格式（本次改造）——
        //   time | LEVEL | configId | roleName | §book | chapter | reqId | message
        // reqId 列可为空（旧行/无关联行）。判别：§ 段之后的第一个 " | " 分出的第 3 段
        // 是一个短 token（r+字母数字，或无字符）。为免误判正文里的 " | "，只认
        // 「以 r 开头且长度 3~24 的字母数字」或空串——不匹配就落回下面的老格式。
        // 11-14：再补 paraIndex/paraTotal 两段（用户令「每个请求音频日志都标 1/5」）。
        // 两段可为空（旧格式行/子行占位）——reqId 后允许接「 |  |  | 正文」或
        // 「 | 数字 | 数字 | 正文」；为兼容 reqId 老格式（reqId 后直接是正文），
        // para 两段整体可选：先试八段新式，不中再落回六段老式。
        Regex(
            "^(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3}) \\|\\s*([A-Z]+)\\s*\\|\\s*(\\d*)\\s*\\|\\s*(.*?)\\s*\\|\\s*§(.*?)\\s*\\|\\s*(.*?)\\s*\\|\\s*(r[0-9a-z]{2,23}|)\\s*\\|\\s*(\\d*)\\s*\\|\\s*(\\d*)\\s*\\|\\s*(true|)\\s*\\|\\s*(.*)$"
        ).find(line)?.let { m ->
            return LogEntry(
                level = m.groupValues[2].toLogLevel(),
                time = m.groupValues[1],
                message = m.groupValues[11],
                configId = m.groupValues[3].toLongOrNull() ?: 0L,
                roleName = m.groupValues[4],
                bookName = m.groupValues[5],
                chapterTitle = m.groupValues[6],
                reqId = m.groupValues[7],
                paraIndex = m.groupValues[8].toIntOrNull() ?: -1,
                paraTotal = m.groupValues[9].toIntOrNull() ?: 0,
                // 11-21：预加载标记（第 10 段）。九段式（paraTotal 止）的旧日志行
                // 不会命中本正则（段数不足），继续走下方老格式兜底，历史文件不丢。
                isPreload = m.groupValues[10] == "true",
                indent = if (isAcquiredSuffix) 1 else 0,
            )
        }
        // 带书章的新格式（11-10，v1.7 规则）：
        //   time | LEVEL | configId | roleName | §bookName | chapterTitle | message
        // 判别靠 bookName 前的固定标记 §：旧日志行里没有这一节，不会误判；正文里的 " | "
        // 也不会被当成字段分隔（message 永远是最后一段，非贪婪匹配 book/chapter 即可）。
        // ⚠️ 必须排在最前：这行的前半段与 09-08 老格式长得一样，先跑老正则会把它啃掉。
        Regex(
            "^(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3}) \\|\\s*([A-Z]+)\\s*\\|\\s*(\\d*)\\s*\\|\\s*(.*?)\\s*\\|\\s*§(.*?)\\s*\\|\\s*(.*?)\\s*\\|\\s*(.*)$"
        ).find(line)?.let { m ->
            return LogEntry(
                level = m.groupValues[2].toLogLevel(),
                time = m.groupValues[1],
                message = m.groupValues[7],
                configId = m.groupValues[3].toLongOrNull() ?: 0L,
                roleName = m.groupValues[4],
                bookName = m.groupValues[5],
                chapterTitle = m.groupValues[6],
                indent = if (isAcquiredSuffix) 1 else 0,
            )
        }
        // 新格式（09-08）：time | LEVEL | configId | roleName | message —— configId/roleName
        // 随 MDC 落盘，重启后旧日志行仍可点开快捷面板；configId 恒为数字作判别，兼容旧三段格式
        Regex(
            "^(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3}) \\|\\s*([A-Z]+)\\s*\\|\\s*(\\d+)\\s*\\|\\s*(.*?)\\s*\\|\\s*(.*)$"
        ).find(line)?.let { m ->
            return LogEntry(
                level = m.groupValues[2].toLogLevel(),
                time = m.groupValues[1],
                message = m.groupValues[5],
                configId = m.groupValues[3].toLongOrNull() ?: 0L,
                roleName = m.groupValues[4],
                indent = if (isAcquiredSuffix) 1 else 0,
            )
        }
        // MDC 全空的变体（无 configId 的系统级日志）：configId/roleName 落盘为空段，
        // 形如 time | LEVEL |  |  | message。上面的数字判别匹配不上，若掉进旧三段解析
        // 会把空段拼进 message（用户 09-09：屏幕上出现「D | | xxx」竖杠）；单独吃掉空段
        Regex(
            "^(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3}) \\|\\s*([A-Z]+)\\s*\\|\\s*\\|\\s*\\|\\s*(.*)$"
        ).find(line)?.let { m ->
            return LogEntry(
                level = m.groupValues[2].toLogLevel(),
                time = m.groupValues[1],
                message = m.groupValues[3],
                indent = if (isAcquiredSuffix) 1 else 0,
            )
        }
        // 旧格式：time | LEVEL | message（message 可含 " | "，需全量重组）
        return line.split(" | ").let {
            val time = it[0]
            val level = it[1]
            val message = it.drop(2).joinToString(" | ")
            LogEntry(
                level = level.toLogLevel(), time = time, message = message,
                indent = if (isAcquiredSuffix) 1 else 0,
            )
        }
    }

    // 日志目录路径。注意：成员 file 是日志文件本身（<日志目录>/system_tts.log），
    // 上游此处直接返回 file.absolutePath，导致调用方 File(logDir()).listFiles() 恒为 null
    // （listFiles 对非目录返回 null）→ 日志文件列表永远显示"日志目录为空"（用户 09-13 报）
    //
    // 11-11 用户令：目录改由 AppConst.logDir 决定（现为 Download/chajian/uyue-utts/logs/utts，权限不足自动回退），
    // 这里不再从 file 反推——反推在"目录被判定不可写"时会指到兜底目录之外。
    fun logDir(): String = AppConst.logDir.absolutePath

    // 主流程日志的分发落点。⚠️ 必须是「固定单例回调 + 可变目标」，不能每次 init 直接
    // SysttsLogger.register { routeEntry(...) }：
    // SysttsLogger 内部是 CopyOnWriteArraySet<LogListener>，而每次 register 传入的都是新的
    // lambda 实例（equals 不成立）⇒ **重复注册不会被去重**，监听器只增不减；且旧 lambda
    // 捕获的是旧 VM ⇒ 同一条日志被投递 N 次，表现成「日志里同一行出现两遍」（用户 10-05
    // 看到的 ×2 即此；×N 折叠只是把症状合了起来，根因在此）。
    // 现在：监听器**全局只注册一次**（见 registered 标志），回调只做一件事——把日志交给
    // 当前活着的那份 VM。
    private object RouteSink {
        @Volatile
        var target: TtsLogViewModel? = null

        fun emit(entry: LogEntry) {
            val vm = target ?: return
            runOnUI { vm.routeEntry(entry) }
        }
    }

    init {
        // 无论本 VM 是第几次创建，都把自己登记为当前分发目标（旧 VM 由 onCleared 摘除）
        RouteSink.target = this
        try {
            viewModelScope.launch(Dispatchers.IO) {
                pull()

                // 主流程日志监听器：全局幂等注册（这是修掉"同一条日志出现两遍"的关键）
                if (registered.compareAndSet(false, true)) {
                    SysttsLogger.register { log -> RouteSink.emit(log) }
                }

                // 插件/朗读规则日志监听器：Console 侧是**单值静态赋值**（覆盖语义），
                // 每次重新指向当前 VM 即可，不存在累加问题。
                //
                // ⚠️ 11-11 修（用户实报「每本书选择朗读规则和插件也不显示相关日志」）：
                // console 日志不带书章字段，按书过滤时会被整体丢弃。这里用
                // SysttsLogger 记的"当前书章上下文"补上，使其与主日志同属一本书。
                Console.globalPluginLogListener = { logEntry ->
                    val stamped = stampBookChapter(logEntry)
                    runOnUI { routeEntry(stamped) }
                }
                Console.globalSpeechRuleLogListener = { logEntry ->
                    val stamped = stampBookChapter(logEntry)
                    runOnUI { routeEntry(stamped) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "init: ", e)
        }
    }

    /** 本 VM 退出时摘掉分发目标，避免已销毁的 VM 继续接收（并让下一个 VM 接手） */
    override fun onCleared() {
        if (RouteSink.target === this) RouteSink.target = null
        super.onCleared()
    }

    fun add(line: String) {
        try {
            val logEntry = toLogEntry(line)
            logs.add(logEntry)
        } catch (e: Exception) {
            Log.e(TAG, "add: ", e)
        }
    }

    @Suppress("DEPRECATION")
    suspend fun pull() {
        runCatching {
            if (file.exists()) {
                // 最多读取最近 1500 行，解析后按类型路由批量添加，避免逐条触发多次重组
                // 11-19：先合并多行消息再逐条解析（堆栈/多行异常不再被拆成散条目）
                val entries = mergeContinuationLines(readTailLines(file, 1500)).mapNotNull { line ->
                    runCatching { toLogEntry(line) }.getOrNull()
                }
                withMain {
                    entries.forEach { routeEntry(it) }
                }
            }
        }.onFailure {
            logs.add(LogEntry(level = LogLevel.ERROR, message = it.stackTraceToString()))
            Log.e(TAG, "pull: ", it)
        }

        // 旧版成功子行文件的**一次性迁移**（11-11 用户定案合并回主日志后）。
        //
        // 历史：11-10~11-11 之间，logS 把「获取成功」写进独立的 system_tts_child.log，
        // 页面靠这里回读。现在写入端已改回主日志，这个文件不再产生新内容；
        // 但**升级上来的用户磁盘上还留着它**，直接不理会让"最近的历史成功行"凭空消失。
        // 所以这里读一次、把里面的行并进主日志，然后清空该文件（不是删——见下方注释），
        // 下次启动文件已是空的，不会重复并入。
        // 11-11 用户定案：成功子行已合并回主日志，不再单独回读 child 文件。
        // 若旧版本留下过 system_tts_child.log，pullOneLegacyChildFile 只读一次并迁进主日志，
        // 之后自然老化（不再写入），不会长期留着一份重复数据。
        pullOneLegacyChildFile()
        // 插件 / 朗读规则日志回读（11-10 用户令「希望能够保存下来」）：
        // 这两个文件由 SysttsLogger.persistConsole 追加，格式 time | LEVEL | message
        //（无 configId/roleName，也不带 § 书章段）。单独解析成本很低，不必并进上面的主解析。
        consoleFile(isPlugin = true)?.let { pullConsoleFile(it, isPlugin = true) }
        consoleFile(isPlugin = false)?.let { pullConsoleFile(it, isPlugin = false) }
    }

    /**
     * 把旧版遗留的 system_tts_child.log 并入主日志，并把该文件清空（11-11）。
     *
     * 只需跑一次：并入后立刻清空文件，下次启动 `f.length() == 0` 直接跳过。
     * **不清空而是删除**会有两个问题：① 写入端若还有残留调用会重新建文件；
     * ② 用户在文件列表里点它会"文件不存在"。
     *
     * 并入顺序仍按时间排序（旧子行时间戳夹在主日志行之间，直接 append 会堆到末尾）。
     */
    private suspend fun pullOneLegacyChildFile() {
        val f = runCatching { File(file.parentFile, "system_tts_child.log") }.getOrNull() ?: return
        if (!f.exists() || f.length() == 0L) return
        runCatching {
            val childEntries = mergeContinuationLines(readTailLines(f, 800)).mapNotNull { line ->
                runCatching { toLogEntry(line) }.getOrNull()
            }
            if (childEntries.isEmpty()) return@runCatching
            withMain {
                // 与已有条目按时间合并：稳定排序（Kotlin sortedBy 是稳定的），
                // 同毫秒时保持原有先后，不会把主行/子行的相对次序打乱。
                //
                // ⚠️ logs 是 SnapshotStateList，直接 clear()+addAll() 会触发两次重组、
                // 列表闪一下。这里**原地补位**：按合并后的顺序逐个比对，
                // 已有元素就地覆盖（不触发变更通知）、缺的位置再插入。
                val merged = (logs.toList() + childEntries).sortedBy { it.time }
                merged.forEachIndexed { i, entry ->
                    if (i < logs.size) {
                        if (logs[i] != entry) logs[i] = entry
                    } else {
                        logs.add(entry)
                    }
                }
            }
            // 并入完成立刻清空源文件：保证"只迁一次"。
            // 用截断不用 delete（写入端可能还持有引用；删了下次又建个空的，反而多一次判断）。
            runCatching { FileWriter(f, false).use { it.write(CharArray(0)) } }
        }.onFailure {
            Log.e(TAG, "pullOneLegacyChildFile: ", it)
        }
    }

    /** 插件 / 朗读规则日志文件路径（与 SysttsLogger.persistConsole 同址） */
    private fun consoleFile(isPlugin: Boolean): File? = runCatching {
        File(file.parentFile, if (isPlugin) "plugin.log" else "speech_rule.log")
    }.getOrNull()

    private suspend fun pullConsoleFile(f: File, isPlugin: Boolean) {
        if (!f.exists()) return
        runCatching {
            // 11-19：先合并多行消息（规则打的 AI 原始 JSON 是多行，散开后会变成一堆残条目）
            val entries = mergeContinuationLines(readTailLines(f, 1500)).mapNotNull { line ->
                runCatching { toConsoleEntry(line, isPlugin) }.getOrNull()
            }
            withMain { entries.forEach { routeEntry(it) } }
        }.onFailure {
            Log.e(TAG, "pullConsoleFile: ", it)
        }
    }

    /**
     * 解析 console 日志行。两种格式并存：
     *
     * **旧**（11-10 起）：`time | LEVEL | message`
     * **新**（11-11 起）：`time | LEVEL | configId | roleName | §book | chapter | message`
     *
     * 为什么要加新格式：旧格式不带书章，重启回读后这些行 bookName 为空，
     * 日志页按书过滤时被整体丢弃（用户实报"选择朗读规则和插件也不显示相关日志"）。
     *
     * 判别靠第 5 段是否以 `§` 开头——旧格式的 message 是任意文本，但
     * `§`(U+00A7) 极少出现在日志正文开头，且新格式一定带这个固定标记，判据可靠。
     * 两种格式都必须支持：升级后磁盘上还留着大量旧行。
     */
    private fun toConsoleEntry(line: String, isPlugin: Boolean): LogEntry {
        val parts = line.split(" | ")
        val level = parts.getOrNull(1)?.toLogLevel() ?: LogLevel.INFO
        val time = parts.getOrNull(0) ?: ""
        // 新格式：第 4 段（0-based）以 § 开头
        val bookMarkIdx = 4
        val hasBook = parts.size > bookMarkIdx && parts[bookMarkIdx].startsWith("§")
        return if (hasBook) {
            // 11-21 22:5x：第 7 段（0-based 6）可选为段号列——persistConsole 落盘
            // 「…§book | chapter | para | message」。判别：纯数字即段号，message 从
            // 第 8 段起重组；非数字（旧格式 message 直接在此）按旧口径从第 6 段起。
            // 历史行无段号 → para=-1，渲染层按「继承前行段号」兜底，老文件照样归位。
            val paraSeg = parts.getOrNull(6) ?: ""
            val para = paraSeg.toIntOrNull()
            LogEntry(
                level = level,
                time = time,
                bookName = parts[bookMarkIdx].removePrefix("§"),
                chapterTitle = parts.getOrNull(5) ?: "",
                paraIndex = para ?: -1,
                // message 从段号列之后起，可含 " | "，全量重组
                message = if (para != null) parts.drop(7).joinToString(" | ")
                          else parts.drop(6).joinToString(" | "),
                isPluginLog = isPlugin,
                isSpeechRuleLog = !isPlugin,
            )
        } else {
            LogEntry(
                level = level,
                time = time,
                message = parts.drop(2).joinToString(" | "),
                isPluginLog = isPlugin,
                isSpeechRuleLog = !isPlugin,
            )
        }
    }

    /**
     * 把「物理行」还原成「逻辑消息」：一条日志可能跨多行（规则打的 AI 原始 JSON、
     * 崩溃堆栈、插件异常都是多行的），落盘时是一条消息里含 '\n'，读回来却成了多个物理行。
     *
     * ⚠️ 不合并的后果（11-19 用户实拍）：一条 `【别名校验-日志】序号01格式错误，AI返回内容：`
     * 被拆成 `[`、`"isAlias": false,`、`"mainName": null,`…… 多个**独立条目**散在列表里，
     * 既看不出它们属于同一条消息，`toConsoleEntry` 还会把残行走「旧格式」兜底分支，
     * 连级别/书章都丢了。
     *
     * 判据：**行首是否带时间戳**（`yyyy-MM-dd HH:mm:ss.SSS` 或 `MM-dd HH:mm:ss`）。
     * 带时间戳 = 新消息开头；不带 = 上一条消息的续行，用 '\n' 接回去。
     * 只在「已经有一条消息」时合并——文件头部被截断产生的孤立续行直接丢弃（那半条
     * 本来就缺开头，拼不出完整消息）。
     */
    private fun mergeContinuationLines(lines: List<String>): List<String> {
        if (lines.isEmpty()) return lines
        val out = ArrayList<String>(lines.size)
        val sb = StringBuilder()
        for (line in lines) {
            val isNewMessage = line.length >= 19 &&
                (TS_FULL.containsMatchIn(line) || TS_SHORT.containsMatchIn(line))
            if (isNewMessage) {
                if (sb.isNotEmpty()) out.add(sb.toString())
                sb.setLength(0)
                sb.append(line)
            } else if (sb.isNotEmpty()) {
                // 续行：接回上一条消息（保留原始缩进与换行）
                sb.append('\n').append(line)
            }
            // else：文件头部被截断的孤立续行，丢弃
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    /**
     * 只读文件尾部 N 行。
     * 旧实现 file.readLines() 把整个文件一次性载入内存再 takeLast——日志文件按天滚动
     * 无大小上限，单日可达数十 MB，瞬时分配与文件大小成正比，是启动后首次进日志栏
     * 的 OOM 爆点。尾部 1MB 按平均 ~700B/行覆盖 1500 行绰绰有余。
     */
    private fun readTailLines(file: File, maxLines: Int): List<String> {
        val fileLen = file.length()
        val tailLen = minOf(fileLen, 1_000_000L).toInt()
        val buf = ByteArray(tailLen)
        java.io.RandomAccessFile(file, "r").use { raf ->
            raf.seek(fileLen - tailLen)
            raf.readFully(buf)
        }
        val lines = String(buf, Charsets.UTF_8).split('\n')
        // 起点不在文件头时，首行大概率是被截断的半行，丢弃
        val start = if (tailLen < fileLen) 1 else 0
        return lines.drop(start).filter { it.isNotBlank() }.takeLast(maxLines)
    }
}
