package com.github.jing332.tts_server_android.compose.systts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.github.jing332.tts_server_android.compose.theme.isAppInDarkTheme
import com.github.jing332.tts_server_android.compose.theme.AppShapes
import com.github.jing332.common.LogEntry
import com.github.jing332.common.LogLevel
import com.github.jing332.common.toLogLevelChar
import com.github.jing332.compose.ComposeExtensions.toAnnotatedString
import com.github.jing332.compose.widgets.ControlBottomBarVisibility
import com.github.jing332.tts_server_android.R
import com.github.jing332.tts_server_android.compose.systts.role.softContainerColor
import com.github.jing332.tts_server_android.compose.theme.logLevelColor
import com.github.jing332.tts_server_android.compose.LocalBottomBarBehavior
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

// SystemTtsService 拼接次级信息所用哨兵色，此处按主题重映射
// ===================== 日志字号阶梯 =====================
// 沿革：11-11 早先为「省一屏高度」整体收过一档（正文 14→13、辅助 12→11），
// 用户随后实报「日志字号太小了，除了吸顶区都恢复为以前的字号吧」——现恢复原值。
//
// 恢复时**不能只把两个常量改大**：原来的分组比现在细。
// 收档时把三种字号都并进了两个常量，其中：
//   · 裸行原 13sp  ┐ 收档时同归 LOG_BODY_SIZE(13)
//   · 正文/主行原 14sp ┘
// 若直接把 LOG_BODY_SIZE 改成 14，裸行也会跟着变大——那不是恢复，是又改了一次
//（10-11 用户对裸行有过明确口径：「12 还是小，提到 13，与成员行/主行前缀同档」）。
// 故按原样拆回三档：
private val LOG_HEAD_SIZE = 14.sp   // 正文 / 请求主行（原 14sp，收档时被压到 13）
private val LOG_BODY_SIZE = 13.sp   // 裸行 / 成员行 / 前缀 / 声音信息（原本就是 13sp，未变）
private val LOG_AUX_SIZE = 12.sp    // 时间 / 状态（原 12sp，收档时被压到 11）

// ⚠️ 恢复时踩过的坑，写下来免得后人重犯：
// 收档前 "13sp" 这一个数值同时被**前缀**、**声音信息**、**裸行**、**成员行**四处使用，
// 而 "12sp" 只被**时间**、**状态**使用。收档把两者并成了一个 LOG_AUX_SIZE(11)，
// 于是恢复时若直接把 LOG_AUX_SIZE 改回 13，会把"时间/状态"这档也一起改大（它们原本是 12）；
// 若只改回 12，前缀/声音信息又会变小（它们原本是 13）。
// 正确做法就是现在这样拆成三档：HEAD 14 / BODY 13 / AUX 12，各回各的原值。
private val LOG_RAW_TEXT_SIZE = 12.sp

private val MetaColorSentinel = Color(0xFFFF00FF)       // 获取成功前缀 → 石板灰
private val VoiceMetaSentinel = Color(0xFF00FFFF)       // 发音人信息 → 雾紫
private val RoleChipSentinel = Color(0xFF0F0F0F)        // 角色名小牌 → 靛蓝牌字色（10-14 用户令）
private val PluginChipSentinel = Color(0xFF0E0E0E)      // 插件名小牌 → 灰青牌字色

// 排版实验 1008（用户 10-08 拍板「撤吧，灰」）：请求行"请求音频："前缀撤级别绿——
// 每卡必有、信息量为零的词占最显眼色正好反了（9-13 去彩色化同方向），改中性灰，
// 与时间行同档；黄警/红错的对比度由此上来。字号分层保留（前缀 13sp）
private val RequestPrefixColorLight = Color(0xFF79747E)
private val RequestPrefixColorDark = Color(0xFF938F99)

// 把命中哨兵色的段落整体换成目标色，让"请求音频"正文(纯绿)与
// 获取成功前缀(石板灰)/发音人信息(雾紫)/角色牌(靛蓝)/插件牌(灰青)层次分明但不抢眼
private fun AnnotatedString.remapMetaColor(
    metaColor: Color,
    voiceColor: Color,
    roleChipColor: Color,
    pluginChipColor: Color,
): AnnotatedString {
    if (spanStyles.none {
            it.item.color == MetaColorSentinel || it.item.color == VoiceMetaSentinel ||
                    it.item.color == RoleChipSentinel || it.item.color == PluginChipSentinel
        }) return this
    return buildAnnotatedString {
        append(this@remapMetaColor.text)
        spanStyles.forEach { r ->
            val newColor = when (r.item.color) {
                MetaColorSentinel -> metaColor
                VoiceMetaSentinel -> voiceColor
                RoleChipSentinel -> roleChipColor
                PluginChipSentinel -> pluginChipColor
                else -> r.item.color
            }
            addStyle(
                r.item.copy(color = newColor),
                r.start,
                r.end
            )
            // 角色名（10-12 三改，用户令「声音行的角色名，干脆换颜色处理吧，不然它总是跟
            // 其他文字对齐不了」）：**撤掉圆角丸与底色 span**，回到纯字色。
            // 10-14 用户令「不能加粗」：Medium 字重与 HTML <b> 一并撤（服务端已同步去 <b>），
            // 角色名=普通字重 + 靛蓝→纯绿（见 roleChipColor），区分度全靠颜色。
            // 演进：10-11 做成直角底 span → 又改成真圆角丸（RoleNamePill）→ 10-12 用户否掉。
            // 根因是「丸」作为独立 Box/Text 与后随文字只能靠 alignByBaseline 硬凑，
            // 内衬一进一出就对不齐；纯字色是同一条 Text 里的 span，天然同基线。
            // ⚠️ 别再改回丸：用户已连续两版不满意，本条为终稿口径。
        }
    }
}

/**
 * 日志归组模型（10-06 全链卡改版，10-10 行流改版）。渲染单位从"逐条日志"升级为归组项：
 * - Group：一次请求管线。head="请求音频"主行（configId≠0，MDC 只在请求行携带）；
 *   members=结果子行(indent>0)/插件过程行/卡打开期间的其他主行(重试/备用)。
 *   收组条件：SUCCESS 结果、主行 ERROR（失败终点）、下一请求开组。
 *   朗读规则日志不进组（10-10 用户令）：一律落裸行——原「卡外悬置→下一张卡做前置区」
 *   在搜索/筛选的收窄列表里等不到开卡，是「勾了朗读规则搜不到」的根因。
 *   10-10 行流改版：渲染不再画卡（容器全撤），归组仅用于①头行药丸推导（重试/切备用/
 *   失败状态从 members 推）②搜索跳转落整组 ③多选整组勾选。
 * - Bare：与请求挨不上的条目——插件/规则散条、系统消息、头部被级别筛掉的孤儿子行。
 * - Header：日期签，每天第一条上方出现一次。
 *
 * 纯函数、确定性；TtsLogScreen 与 LogScreen 共用同一实例（搜索跳转要用条目→列表项
 * 映射）。转发器日志无 configId/indent/插件标记，全部落裸行、零影响。
 */
internal class LogGroups(
    val items: List<Item>,
    val entryToList: IntArray,
    // 以失败收场的组头行下标（10-10 行级丸配套）：请求行本身是 INFO，失败丸「失败」的
    // 依据是「该组以失败收场」——ERROR 子行/主行收卡时由 build 记入
    val failedHeads: Set<Int> = emptySet(),
    // 11-11 吸顶区用：本批日志里「最早出现的书」及其章（书条/章条不再进列表流，
    // 改由 TtsLogScreen 吸顶渲染——用户令「书名和章节名一直置顶」）。
    // 空串=该批日志没有书章（老规则/单语音），吸顶整块不显示。
    val pinnedBook: String = "",
    val pinnedChapter: String = "",
    val pinnedChapterCount: Int = 0,
    val pinnedBookCount: Int = 0,
    /** 本份日志里出现过的不同书数（筛选书只在 ≥2 本时有意义）。 */
    val bookTotal: Int = 0,
    // 11-11「吸顶区跟随滚动」：每个**列表项下标** → 该项所属的 (书, 章)。
    // 用户令「全部日志我划到哪就显示哪本书的信息」——吸顶区不再是"最新一条"，
    // 而是"当前视口首项所属"。
    //   · 建表时机：build() 里逐项扫一遍，遇到带书章的条目就更新「当前书章」并记下；
    //   · 无书章的项沿用上一个值（同一本书里连续都是同书同章，无需重复算）；
    //   · 表首（还没遇到过任何书章）为空串对。
    // 用 IntArray 存两次查表的代价太高，这里直接用两个 String 数组，
    // 尺寸 = items.size，与 entryToList 同级开销。
    private val itemBook: Array<String> = emptyArray(),
    private val itemChapter: Array<String> = emptyArray(),
) {
    /** 取某个列表项所属的书/章（11-11 吸顶跟随用）；越界或未建表时返回空串对 */
    fun bookChapterAt(itemIndex: Int): Pair<String, String> =
        if (itemIndex in itemBook.indices && itemIndex < itemChapter.size)
            itemBook[itemIndex] to itemChapter[itemIndex]
        else "" to ""
    sealed class Item {
        class Header(val date: String) : Item()
        class Bare(val index: Int) : Item()

        // 书籍条（11-10 用户令「日志分小说、然后还要分章节」 / 方案 A）：
        // 换书处插一条，列出书名与本书条目数；点击可切书（只切日志查看范围）
        class BookBar(val book: String, val count: Int) : Item()

        // 章节签：跨章处插一条，列出章名与本章条数；点击跳到本章首条。
        // anchorEntry = 本章第一条的**条目下标**（不是列表项下标）——跳转时经
        // LogGroups.listItemFor 换算成列表位置，不必在渲染层反查。
        class ChapterBar(
            val book: String,
            val chapter: String,
            val anchorEntry: Int,
            val count: Int,
        ) : Item()

        // 一次请求管线的归组（10-10 行流改版：渲染层不再画卡，只保留归组供
        // 搜索跳转/多选粒度/头行药丸推导用）。head=「请求音频」主行；
        // members=结果子行(indent>0)/插件过程行/其他主行(重试/备用/失败)。
        // 收组条件与旧卡一致：SUCCESS 结果、主行 ERROR（失败终点）、下一请求开组。
        // absorbError=被本组吸收的源错误条目下标（10-11 老版式定稿）：源错误条目含
        // 请求全文，失败时以它为主体渲染（胶囊源错误/英文红/正文红粗/发音人不红），
        // 请求主行正文不再重复显示——同一次失败一条说完
        class Group(
            val head: Int,
            val members: List<Int>,
            val absorbError: Int = -1,
            // 本组内发生过的重试次数（10-12 用户令「重试和失败是紧密联系的，别单独占一行」）：
            // 重试行不再落成独立行，改在组头行灯位后挂一枚「重试N」小丸；
            // 组已收卡时挂到刚收的那张组上（见 build 里 retry 分支）
            val retryCount: Int = 0,
        ) : Item()
    }

    // 条目下标 → 列表项下标（搜索跳转/滚动定位用）；越界或未入组时兜底直用条目下标
    fun listItemFor(entryIndex: Int): Int =
        if (entryIndex in entryToList.indices && entryToList[entryIndex] >= 0) entryToList[entryIndex]
        else entryIndex

    fun entriesOf(item: Item): List<Int> = when (item) {
        is Item.Header -> emptyList()
        // 书条/章节签是纯装饰性分隔头，不带可复制条目（多选/复制不把它们算进去）
        is Item.BookBar -> emptyList()
        is Item.ChapterBar -> emptyList()
        is Item.Bare -> listOf(item.index)
        // ⚠️ 吸收条必须算进本组（10-12 用户实报「有些信息无法复制了」）：absorbError 是
        // 失败全文条（真因 + 正文 + 发音人信息），10-11 起由组头代画、**不进 members**——
        // 于是组的勾选集合（原 head+members）拿不到它，勾上整组复制/搜索定位都少了真因那一行。
        is Item.Group -> listOf(item.head) + item.members +
            listOfNotNull(item.absorbError.takeIf { it >= 0 })
    }

    companion object {
        /**
         * 11-13 归属修复（用户实报「爷爷，卡上挂着别人 0.9s/50.7KB」）：
         * 并发时两笔请求的日志按时间交错落盘——REQ_A、REQ_B、SUCCESS_A、SUCCESS_B，
         * 分组按顺序跑，SUCCESS_A 就挂到 B 的卡上、SUCCESS_B 因卡已关掉成孤立裸行。
         *
         * 修法取最小侵入：按请求实例 id 做一次**稳定重排**——把同一 reqId 的所有行
         * 聚到该请求首次出现的位置，组内保持原始先后（请求行必是首条）。重排后
         * 分组逻辑照旧按顺序跑就能正确配对，不必重写整台状态机。
         * 无 reqId 的行（旧日志、插件行、规则行、系统消息）各自归位到原下标，
         * 相对顺序保持不变；已有顺序即为最优时该函数是恒等变换。
         */
        private fun regroupByReqId(list: List<LogEntry>): List<LogEntry> {
            if (list.isEmpty()) return list
            if (list.none { it.reqId.isNotEmpty() }) return list
            // 每个 reqId 的首条下标 + 其成员（保持原顺序）
            val firstAt = LinkedHashMap<String, Int>()
            val buckets = HashMap<String, MutableList<LogEntry>>()
            list.forEachIndexed { i, e ->
                if (e.reqId.isEmpty()) return@forEachIndexed
                firstAt.getOrPut(e.reqId) { i }
                buckets.getOrPut(e.reqId) { ArrayList() }.add(e)
            }
            // 已经是「首条紧随其后就是全部成员」的常见情形：跳过重排，避免无谓开销
            var needReorder = false
            for ((id, idx) in firstAt) {
                val n = buckets[id]?.size ?: 0
                if (n <= 1) continue
                var seen = 0
                for (j in idx until minOf(idx + n + 8, list.size)) {
                    if (list[j].reqId == id) seen++
                }
                if (seen < n) { needReorder = true; break }
            }
            if (!needReorder) return list

            val out = ArrayList<LogEntry>(list.size)
            val emitted = HashSet<String>()
            list.forEachIndexed { i, e ->
                if (e.reqId.isEmpty()) {
                    out.add(e)
                } else if (emitted.add(e.reqId)) {
                    buckets[e.reqId]?.forEach { out.add(it) }
                }
                // 已发过的 reqId 成员跳过（它们已随首条一起输出）
            }
            return out
        }

        /**
         * @param fallbackBook / [fallbackChapter] 列表里没有带书章的条目时的回落值
         *   （11-11：由调用方传"全量日志里最近一条带书章的条目"）。
         *   用户实报「切换章节时有时无」——因为吸顶区只看**过滤后**的列表，
         *   选单本书/切书时主行被滤掉，吸顶区就空掉。加回落可保证只要读过这本书就仍显示。
         */
        fun build(
            input: List<LogEntry>,
            fallbackBook: String = "",
            fallbackChapter: String = "",
        ): LogGroups {
            val list = regroupByReqId(input)
            val raw = ArrayList<Item>(list.size)
            var head = -1
            var members = ArrayList<Int>()
            // 失败组头行标记（10-10 行级丸配套）：请求行本身是 INFO，失败丸（失败）的依据
            // 是「该组以失败收场」——ERROR 子行/主行收卡时把 head 记进来，渲染侧查此表
            val failedHeads = HashSet<Int>()
            var groupFailed = false
            // 连续同文源错误去重（10-11 老版式定稿配套）：记住上一条 ERROR 子行去 HTML
            // 后的全文，重试风暴同文即丢；跨请求/文案变化重置
            var lastErrorStripped: String? = null
            // 老版式失败条（10-11 定稿）：本次开卡以来最近一条 ERROR 子行下标——
            // 源错误条目含请求全文，收卡时它是组的渲染主体（absorbError）
            var errMemberIdx = -1
            // 本组内重试次数（10-12）：重试行不单独占行，挂到组头做小丸
            var retryCount = 0

            fun closeCard() {
                if (head >= 0) {
                    if (groupFailed) {
                        failedHeads.add(head)
                        raw.add(Item.Group(head, members.toList(), absorbError = errMemberIdx, retryCount = retryCount))
                    } else {
                        raw.add(Item.Group(head, members.toList(), retryCount = retryCount))
                    }
                }
                head = -1
                members = ArrayList()
                groupFailed = false
                errMemberIdx = -1
                retryCount = 0
            }
            fun openCard(requestIdx: Int) {
                closeCard()
                head = requestIdx
            }

            list.forEachIndexed { i, e ->
                when {
                    // ⚠️ 插件日志判断必须在 indent 分支之前：插件行天然 indent=1（子行形态），
                    // 走 indent 分支会进组——与「一律独立裸行」的终令相悖
                    // 插件日志一律独立裸行（10-10 用户终令「插件日志要拆开，一条归一条，
                    // 不并入请求组」）：无论何时产生（请求中/失败链期间）都不进组、不进
                    // 悬置链——与其他日志同格式独立成行（时间头/12sp/分隔线/定位键全同款）。
                    e.isPluginLog -> raw.add(Item.Bare(i))
                    // ⚠️ ERROR 结果子行（源错误）=失败终点（10-11 用户定稿「拿不准就按老版本」）：
                    // 源错误条目本就含请求全文（服务端老版格式：错误+<br>+正文+发音人信息），
                    // 归组成员区隐藏主行只渲染本条——即老版观感「胶囊源错误/英文红/正文红粗/
                    // 发音人不红」一条说完。收卡=失败终点（groupFailed，渲染失败丸）。
                    // 连续同文源错误去重（真机实锤：重试风暴同一句错误+正文原样刷两轮）：
                    // 与上一条 ERROR 同文（去 HTML 后）则整条丢弃——重试必然同死法零新信息，
                    // 文案变了（超时→401）照常显示。
                    e.indent > 0 && e.level == LogLevel.ERROR -> {
                        val hadHead = head >= 0
                        if (hadHead) {
                            groupFailed = true
                            errMemberIdx = i
                        }
                        val stripped = e.message.replace(Regex("<[^>]*>"), "")
                        val isDup = !stripped.isNullOrBlank() && stripped == lastErrorStripped
                        lastErrorStripped = stripped
                        closeCard()
                        // 有卡=已被组吸收（absorbError 渲染），不再落裸行避免双显；
                        // 无卡=请求头被筛掉的孤儿，照旧裸行兜底
                        if (!hadHead && !isDup) raw.add(Item.Bare(i))
                    }
                    // 结果/子行：有卡归卡（SUCCESS 顺带收卡）；无卡=头部被筛掉的孤儿 → 裸行
                    e.indent > 0 -> {
                        if (head >= 0) {
                            members.add(i)
                            if (e.level == LogLevel.SUCCESS) closeCard()
                        } else raw.add(Item.Bare(i))
                    }
                    // 规则日志一律落裸行（10-10 用户令：不进卡）。原「卡外悬置→下一张卡
                    // 做前置区」的机制整拆：搜索/筛选时收窄列表里经常没有跟得上的请求行，
                    // 悬置行等不到开卡就渲染不出来（表现为「勾了朗读规则也搜不到」）；
                    // 卡内也不再吸收规则行，避免分析行挂在无关请求卡顶造成误导
                    e.isSpeechRuleLog -> raw.add(Item.Bare(i))
                    // "请求音频"主行：开新卡（旧卡先收）
                    e.configId != 0L -> openCard(i)
                    // 其他主行（重试/备用TTS/兜底发音人/系统消息）：有卡归卡，ERROR=失败
                    // 终点收卡（标记 groupFailed）；无卡落裸行——失败链不再悬置并组（10-10 终案）
                    else -> {
                        // ⚠️ 重试行先拦（10-12 用户令「重试没别的信息，单独占一行浪费空间，
                        // 它和失败是紧密联系的」）：不落行、只把次数挂到组头做「重试N」小丸。
                        // 两种落点：①本组还开着 → 记在本次开卡上且**不关组**（后面那次失败与
                        // 本次重试同属一条失败链）；②上一次失败已收卡 → 挂到刚收的那张组上。
                        // 前面既没开卡也没有组（头部被筛掉的孤儿）→ 退回裸行，不至于把日志吞掉
                        val isRetryRow = e.level == LogLevel.WARN && e.message.contains("次重试")
                        if (isRetryRow) {
                            if (head >= 0) {
                                retryCount++
                            } else {
                                // ⚠️ 挂到「上一张组」前要验明身份（10-12 自审）：重试必然跟在一次
                                // 失败之后，所以只认**失败组**（failedHeads 记过），且与它最后一条
                                // 相隔不超过 60 秒——否则请求行被筛选掉时，重试会挂到上一张不相干
                                // 的组上（跨句错挂）。两条都不满足就退回裸行，宁可多一行也别错挂
                                val last = raw.lastOrNull()
                                val lastIdx = (last as? Item.Group)?.let { g ->
                                    if (failedHeads.contains(g.head)) {
                                        val tailIdx = if (g.absorbError >= 0) g.absorbError
                                        else g.members.lastOrNull() ?: g.head
                                        list[tailIdx].time
                                    } else null
                                }
                                if (last is Item.Group && lastIdx != null && withinSeconds(lastIdx, e.time, 60)) {
                                    raw[raw.size - 1] = Item.Group(
                                        last.head, last.members, last.absorbError, last.retryCount + 1
                                    )
                                } else {
                                    raw.add(Item.Bare(i))
                                }
                            }
                        } else if (head >= 0) {
                            // 老版式失败全文条（10-11）：本条含请求全文（错误 + <br> +
                            // 正文 + 发音人信息）时，让它当本组渲染主体（absorbError）——
                            // 渲染侧改画它、不再画请求主行正文，避免同一段正文出现两遍。
                            // ⚠️ 此时**不进 members**：absorbError 与成员区都会画一遍
                            //（成员区只跳过 SUCCESS），进了就双重渲染。
                            // 用「含 <br>」判别：配置为空/系统消息这类无正文的 ERROR
                            // 不进吸收（保持原样：主行正文照旧显示）。
                            val isFullFail = e.level == LogLevel.ERROR && e.message.contains("<br>")
                            if (isFullFail) {
                                groupFailed = true
                                errMemberIdx = i
                            } else {
                                members.add(i)
                                if (e.level == LogLevel.ERROR) groupFailed = true
                            }
                            closeCard()
                        } else raw.add(Item.Bare(i))
                    }
                }
            }
            closeCard()

            // 插书条/章节签/日期签 + 建条目→列表项映射
            //
            // 层级（方案 A，11-10 用户拍板）：书 > 章 > 日期 > 行。
            // 三者都只在「变化处」出现：同书连续不重复画书条，同章连续不重复画章节签，
            // 同一天不重复画日期签——所以正常阅读时一屏最多各一条，不会顶掉正文。
            // ⚠️ 书/章取的是**锚点条目**（组头或裸行）自身的字段：请求行才带书章，
            // 结果子行/插件行没有，用它们判断会漏。
            val finalItems = ArrayList<Item>(raw.size + 8)
            val map = IntArray(list.size) { -1 }
            var lastDate: String? = null
            // 11-11：书条/章条改吸顶后不再需要这两个游标（原用于判断「换书/跨章处插签」）

            // 先统计每本书/每章各有多少条（书条/章节签要显示「N 条」键 + 首条下标）
            fun anchorOf(item: Item): Int = when (item) {
                is Item.Bare -> item.index
                is Item.Group -> item.head
                else -> -1
            }
            val bookCount = HashMap<String, Int>()
            val bookStart = HashMap<String, Int>()            // 书 -> 首条目下标
            val chapterCount = HashMap<Pair<String, String>, Int>()
            val chapterStart = HashMap<Pair<String, String>, Int>()  // (书,章) -> 首条目下标
            raw.forEach { item ->
                val ai = anchorOf(item)
                if (ai < 0) return@forEach
                val e = list[ai]
                if (e.bookName.isBlank()) return@forEach
                bookCount[e.bookName] = (bookCount[e.bookName] ?: 0) + 1
                if (!bookStart.containsKey(e.bookName)) bookStart[e.bookName] = ai
                if (e.chapterTitle.isNotBlank()) {
                    val k = e.bookName to e.chapterTitle
                    chapterCount[k] = (chapterCount[k] ?: 0) + 1
                    if (!chapterStart.containsKey(k)) chapterStart[k] = ai
                }
            }

            raw.forEach { item ->
                val anchorIdx = anchorOf(item)
                if (anchorIdx >= 0) {
                    val e = list[anchorIdx]
                    // 11-11：书条/章条不再插入列表流——改由上层吸顶渲染（用户令置顶）。
                    // 这里只负责日期签；书章信息在循环外统计成 pinnedBook/pinnedChapter。
                    val t = e.time
                    if (t.length >= 10) {
                        val d = t.substring(0, 10)
                        if (d != lastDate) {
                            finalItems.add(Item.Header(d))
                            lastDate = d
                        }
                    }
                }
                // finalItems.size 即本 item 即将占据的下标
                when (item) {
                    is Item.Bare -> map[item.index] = finalItems.size
                    is Item.Group -> (listOf(item.head) + item.members)
                        .forEach { map[it] = finalItems.size }
                    else -> {}
                }
                finalItems.add(item)
            }
            // 吸顶信息：取本批日志里**最后一条**带书章的条目。
            //
            // ⚠️⚠️ 11-11 关键修正（用户实报两件事：「都换新书读了，顶部没自动更新书」
            //      「如果选择全部书籍，顶部竟然还用某一本书，这也不合适」）：
            // 原实现取 `firstOrNull` —— 列表是**按时间正序**的（最早的在前、新日志追加在尾，
            // 自动滚动也是 scrollToItem(lastIndex) 滚到底），所以 firstOrNull 拿到的是
            // **列表里最旧**的那条书章。于是：
            //   · 换新书之后，顶部仍然显示最早读过的那本书 ⇒ 用户看到「没自动更新」；
            //   · 选"全部书籍"时，顶部也固定显示最旧那本 ⇒ 用户看到「竟然还是某一本书」。
            // 这两条其实是同一个 bug，不是两个。取**最后一条**才是"当前正在读的那本"。
            //
            // 另外保留回落（列表里完全没有带书章的条目时用最近一次朗读上下文），
            // 防止日志被清空/过滤光后顶部整个空掉。
            val latestWithBook = list.lastOrNull { it.bookName.isNotBlank() }
            // 语义要分清：列表里有书章条目 ⇒ 用它（哪怕章为空，那是"这本书确实没有章"，
            // 不能拿别的书的章来补）；列表里一条都没有 ⇒ 才回落到 fallback。
            val pBook = if (latestWithBook != null) latestWithBook.bookName else fallbackBook
            val pChapter = if (latestWithBook != null) latestWithBook.chapterTitle else fallbackChapter
            val pKey = pBook to pChapter

            // ===== 11-11 吸顶跟随：逐项扫出「每个列表项属于哪本书哪一章」=====
            // 用户令「全部日志我划到哪就显示哪本书的信息」。
            // 做法：顺扫 finalItems，遇到带书章的条目就把"当前书章"更新掉并记给这一项；
            // 没有书章的项沿用上一个（同一本书里连续日志同书同章，不必重复取）。
            // 成本 O(n)、只建两个数组，与 entryToList 同量级；只在 build 时算一次，
            // 不进滚动热路径——滚动时只做一次下标查表。
            val itemBookArr = Array(finalItems.size) { "" }
            val itemChapterArr = Array(finalItems.size) { "" }
            run {
                var cBook = ""
                var cChapter = ""
                finalItems.forEachIndexed { li, it ->
                    val ai = anchorOf(it)
                    if (ai >= 0 && ai < list.size) {
                        val e = list[ai]
                        if (e.bookName.isNotBlank()) {
                            cBook = e.bookName
                            cChapter = e.chapterTitle
                        }
                    }
                    itemBookArr[li] = cBook
                    itemChapterArr[li] = cChapter
                }
            }

            return LogGroups(
                items = finalItems,
                entryToList = map,
                failedHeads = failedHeads,
                pinnedBook = pBook,
                pinnedChapter = pChapter,
                pinnedChapterCount = if (pChapter.isBlank()) 0 else (chapterCount[pKey] ?: 0),
                pinnedBookCount = if (pBook.isBlank()) 0 else (bookCount[pBook] ?: 0),
                bookTotal = bookStart.size,
                itemBook = itemBookArr,
                itemChapter = itemChapterArr,
            )
        }
    }
}

@OptIn(
    ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class // FlowRow（头部药丸链换行）
)
@Composable
internal fun LogScreen(
    modifier: Modifier,
    list: List<LogEntry>,
    listState: LazyListState = rememberLazyListState(),
    autoScrollToBottom: Boolean = false,
    // 非空时命中项加背景高亮(定位用，不过滤列表)
    searchQuery: String = "",
    // 多选模式：长按条目进入（1003；顶栏 ☑ 已删，退多选=返回键/复制后自动退）。跨条复制
    // 走本模式而非 compose selection——条目滚出视口被回收后选区仍悬挂其 selectableId，
    // 拖柄重算查表即崩（上游至今未修，详见 1002 记录）
    selectionMode: Boolean = false,
    // 勾选的日志（以条目对象为键：对列表增删/筛选重排免疫；time 毫秒级，同值碰撞可忽略）
    checkedEntries: Set<LogEntry> = emptySet(),
    onToggleCheck: (LogEntry) -> Unit = {},
    // 长按拖动多选（1003）：长按任意条目＝立刻进多选并勾上它，按住拖动＝划过的条目连续
    // 勾选、拖回缩小范围。仅 dragSelectEnabled=true 的页面启用——转发器日志没有多选 UI
    dragSelectEnabled: Boolean = false,
    onEnterSelection: () -> Unit = {},
    onCheckedChange: (Set<LogEntry>) -> Unit = {},
    // 归组结果（10-06 全链卡）：由 TtsLogScreen 传入同一实例（搜索跳转要用映射）；
    // 缺省内部构建。转发器按位置传参、不传此参，走内部构建、全裸行零影响
    groups: LogGroups? = null,
    // 「原文」定位键（用户 10-08 午后 甲方案）：非空=true=当前处于筛选/搜索态，
    // 卡右上角显示小键；点击回调把该条目交回上层（清筛选+跳完整流原位）
    showLocateKey: Boolean = false,
    onLocateOriginal: (LogEntry) -> Unit = {},
    // 定位高亮：命中条目黄底闪现（完整流里看到前后文），上层 2s 后清除
    locateHighlight: LogEntry? = null,
    // 书籍条右侧「切换书」（11-10 方案 A）：null=不显示切换入口（如转发器日志页）
    onSwitchBook: ((String) -> Unit)? = null,
    // 点章节签跳章：上层可借此同步「当前章」用于筛选联动
    onJumpToChapter: ((book: String, chapter: String) -> Unit)? = null,
    // 章条右侧「切换章」键（11-11 用户指出「切换书有键、切换章没有」）：
    // 与「切换书」对称——书条右端弹书单，章条右端弹章节列表。
    // 始终传非 null（用户令「始终显示」）：只有一章的书也显示，点了能看见确实只有一章。
    onSwitchChapter: ((book: String, chapter: String) -> Unit)? = null,
    // 视野书章回传（11-11）：吸顶区要显示"你划到了哪本书哪一章"，
    // 而列表滚动状态只在本组件内，故用回调把结果抛给上层。
    // 只在**值变化**时回调一次（内部做了比较），不会逐帧触发。
    onViewChanged: ((book: String, chapter: String) -> Unit)? = null,
    // 「切过去」触发计数（11-11）：每次点提示条 +1，用来强制重启自动滚底的 LaunchedEffect。
    // 见下方 autoScrollToBottom 的 LaunchedEffect 注释：只靠 list.size/autoScrollToBottom
    // 两个 key 时，"本来就开着实时最新"的情况不会滚动。
    jumpToLiveTick: Int = 0,
) {
    ControlBottomBarVisibility(listState, LocalBottomBarBehavior.current)
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val context = LocalContext.current
    // 非空时显示日志快捷面板（点请求卡触发，锚定"请求音频"主行）
    var quickPanelEntry by remember { mutableStateOf<LogEntry?>(null) }
    quickPanelEntry?.let { entry ->
        com.github.jing332.tts_server_android.compose.systts.log.LogQuickPanel(
            onDismissRequest = { quickPanelEntry = null },
            entry = entry,
        )
    }
    // 长按拖动手势要用到的最新值（pointerInput(Unit) 不随重组重启，一律走 State 读）
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    val currentList by rememberUpdatedState(list)
    val currentChecked by rememberUpdatedState(checkedEntries)
    val currentOnEnterSelection by rememberUpdatedState(onEnterSelection)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)

    // 归组结果：外部传入优先，否则内部构建（缓存随列表重建）
    val builtGroups = remember(list, groups) { groups ?: LogGroups.build(list) }
    val currentGroups by rememberUpdatedState(builtGroups)

    // ===== 11-11 吸顶跟随滚动：当前视口首项所属的书/章 =====
    // 用户令「全部日志我划到哪就显示哪本书的信息」。
    // 实现要点（这是滚动热路径，不能每帧重算）：
    //   · 用 derivedStateOf 包装：Compose 只在其**结果变化**时才触发重组；
    //   · 读取的是 listState.firstVisibleItemIndex（不是 visibleItemsInfo 列表），
    //     滚动时该值变化次数远少于逐帧，且 firstVisibleItemIndex 的变更会被精确追踪；
    //   · 真正的查表（currentGroups.bookChapterAt）只在下标变了时才执行一次。
    // 由于 derivedStateOf 的 lambda 会被重用，这里用 remember(currentGroups) 保证
    // 换书/筛选后查的是新表。
    // 「跳到这本」的一次性滚底请求（11-11）：true=待执行，滚完即清。
    // 与 autoScrollToBottom（用户勾选的持续跟随）是两回事，别合并。
    var pendingJumpToBottom by remember { mutableStateOf(false) }
    val visibleBookChapter by remember(currentGroups) {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex
            currentGroups.bookChapterAt(idx)
        }
    }
    // 把视野书章抛给上层（吸顶区渲染在 LogScreen 外面）。
    // ⚠️ 必须用 LaunchedEffect 而不是在组合中直接调用回调——组合期间写状态会触发
    // "Writing to state during composition" 的重组风暴。
    // key 用 visibleBookChapter 本身：只有它变了才重跑，不会逐帧触发。
    val currentOnViewChanged by rememberUpdatedState(onViewChanged)
    LaunchedEffect(visibleBookChapter) {
        currentOnViewChanged?.invoke(visibleBookChapter.first, visibleBookChapter.second)
    }

    // 拖选笔画共享态：手势跑在 AwaitPointerEventScope（受限挂起作用域）里，编译器禁止
    // 在其中 launch 协程，边缘自动滚只能挂外部协程，靠这组状态桥接手势与滚动手
    val dragActive = remember { mutableStateOf(false) }
    val dragPointerY = remember { mutableStateOf(0f) }
    // 锚点/末位均为【列表项下标】（卡/裸行/日期签），不是条目下标
    val dragAnchor = remember { mutableStateOf(0) }
    val dragBase = remember { mutableStateOf<Set<LogEntry>>(emptySet()) }
    val dragLastIdx = remember { mutableStateOf(0) }

    // 指尖纵坐标 → 可视列表项下标；只认日志项（收尾 Spacer 不算），落在空档上就近取边界项
    fun logIndexAtY(y: Float): Int? {
        val items = listState.layoutInfo.visibleItemsInfo
            .filter { it.index < currentGroups.items.size }
        if (items.isEmpty()) return null
        val hit = items.firstOrNull { y >= it.offset && y < it.offset + it.size }
        return hit?.index
            ?: if (y < items.first().offset) items.first().index
            else items.last().index
    }

    // 以长按起点为锚的连续区间=追加勾选；拖回缩小只收回本次加的，之前勾的行不受影响。
    // 区间按列表项（卡/裸行）展开成条目集合：拖过一张卡=整组勾选
    fun emitDragSelection() {
        val g = currentGroups
        if (g.items.isEmpty()) return
        val lo = minOf(dragAnchor.value, dragLastIdx.value).coerceIn(0, g.items.lastIndex)
        val hi = maxOf(dragAnchor.value, dragLastIdx.value).coerceIn(0, g.items.lastIndex)
        var sel = dragBase.value
        for (k in lo..hi) {
            g.entriesOf(g.items[k]).forEach { idx ->
                if (idx in currentList.indices) sel = sel + currentList[idx]
            }
        }
        currentOnCheckedChange(sel)
    }

    fun updateDragSelection(y: Float) {
        logIndexAtY(y)?.let { idx ->
            if (idx != dragLastIdx.value) {
                dragLastIdx.value = idx
                emitDragSelection()
            }
        }
    }

    // 边缘自动滚：拖选中指尖压上下边缘时逐帧匀速滚，扫选屏外项；滚动后指尖下的项
    // 变了，选中范围跟着延伸/缩小。收笔（dragActive=false）即卸载
    val density = LocalDensity.current
    if (dragActive.value) {
        LaunchedEffect(density) {
            val edgeZone = with(density) { 48.dp.toPx() }
            val frameScroll = with(density) { 14.dp.toPx() }
            while (dragActive.value) {
                withFrameNanos { }
                val end = listState.layoutInfo.viewportEndOffset
                if (dragPointerY.value < edgeZone) listState.scrollBy(-frameScroll)
                else if (dragPointerY.value > end - edgeZone) listState.scrollBy(frameScroll)
                else continue
                updateDragSelection(dragPointerY.value)
            }
        }
    }
    Box(modifier) {
        // 回底键出现时机 A+B（10-10 装机反馈用户拍板）：
        // A=离底超过 15 条目（约一屏半）才显示——翻一两行找东西不再闪键，翻远了才给一键直达；
        // B=不在底部时有新日志到达（size 超过「上次在底部时」的基线）也显示——翻旧日志时
        //   新请求进来，箭头提示「下面有新的」，一按即回。
        // 旧逻辑=只要不在最底就常驻（出现太勤，用户反馈「时机该改改」）。
        // 基线 lastSeenSizeAtBottom 在 isAtBottom=true 时同步；按键回底后 isFarFromBottom
        // 转否且基线已同步，键自然消失。
        var lastSeenSizeAtBottom by remember { mutableStateOf(list.size) }
        val isFarFromBottom by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val visibleItemsInfo = layoutInfo.visibleItemsInfo
                if (layoutInfo.totalItemsCount <= 0 || visibleItemsInfo.isEmpty()) {
                    false
                } else {
                    val lastVisibleItem = visibleItemsInfo.last()
                    // 离底 15 条目以上=一屏开外（行流单条约 40~60dp，15 条≈一屏半）
                    layoutInfo.totalItemsCount - 1 - lastVisibleItem.index > 15
                }
            }
        }
        val isAtBottom by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val visibleItemsInfo = layoutInfo.visibleItemsInfo
                if (layoutInfo.totalItemsCount <= 0 || visibleItemsInfo.isEmpty()) true
                else visibleItemsInfo.last().index >= layoutInfo.totalItemsCount - 1
            }
        }
        // 滚回底部（或本来就在底）时同步基线：此后 size 增长才被视为「新到达」
        LaunchedEffect(isAtBottom) {
            if (isAtBottom) lastSeenSizeAtBottom = list.size
        }
        val hasNewBelow = list.size > lastSeenSizeAtBottom && !isAtBottom
        val showJumpToBottom = isFarFromBottom || hasNewBelow
        // ===== 自动滚底 =====
        // 两条来源必须分开，因为语义完全不同（11-11 用户纠正）：
        //
        // ① **实时最新勾选**（autoScrollToBottom）：用户主动开启的**持续跟随**，
        //    新日志一来就滚到底。未勾选时绝不自动滚——用户原话
        //    「本来那个实时最新也不是勾选状态呀，只有我确定勾选了才启用，一般都是正常状态」。
        //
        // ② **一次性的跳到底**（pendingJumpToBottom）：「跳到这本」按钮触发，
        //    只滚这一次，**不改勾选状态**。此前我错写成 `autoScrollToBottom = true`，
        //    等于点一次按钮就把持续跟随打开了，与用户预期相反。
        //
        // ⚠️ pendingJumpToBottom 这个中间标志不能省：
        // 「跳到这本」会先清筛选，列表内容**下一帧**才变。若直接在按钮处滚动，
        // 滚的还是旧（筛选过的）列表，滚了等于没滚。
        // 所以这里先置标志，等 list/currentGroups 变化后在有新内容的这一帧再滚，滚完清标志。
        LaunchedEffect(jumpToLiveTick) {
            if (jumpToLiveTick > 0) pendingJumpToBottom = true
        }
        LaunchedEffect(list.size, autoScrollToBottom, pendingJumpToBottom, currentGroups) {
            val wantScroll = autoScrollToBottom || pendingJumpToBottom
            if (wantScroll && currentGroups.items.isNotEmpty()) {
                runCatching { listState.animateScrollToItem(currentGroups.items.lastIndex) }
                if (pendingJumpToBottom) pendingJumpToBottom = false
            }
        }

        if (list.isEmpty())
            Box(Modifier.align(Alignment.Center)) {
                Text(
                    text = stringResource(R.string.empty_list),
                    style = MaterialTheme.typography.titleMedium
                )
            }

        // 排版实验 1008（用户 10-08 拍板①）：成员行 forceColor 压制撤除，kidBody 色系
        // 退役——行流恢复级别色/来源色（插件灰青/规则灰紫可见）
        val darkTheme = isAppInDarkTheme()
        // 10-10 行流改版：卡片容器全撤，cardBg 三档随卡同亡；报错信号改由
        // 失败红胶囊+红原因字承担（StatusPill，11-10 四改恢复胶囊）
        // 获取成功前缀：石板灰 Blue Grey 800/200
        // 发音人信息：棕褐 #7D6B5D / 深色主题 #A08B7A
        // 11-10 二改（用户实锤「声音信息当时有原本的颜色」）：这四色**是信息类别色**，
        // 不是装饰色——发音人信息、角色名、插件名各有一个专属色，一眼分得清"谁在说话/
        // 哪个是角色/哪个是插件"。11-10 一改曾把它们全换成主题槽，结果四类内容糊成同一色系
        // （声音信息从棕褐变成主题绿，跟正文撞），已回退。
        // ⚠️ 判据：**信息类别**（这一行是什么）用固定色；**结构/状态**（成功/失败/规则行）
        // 才跟主题。别再把这两类混为一谈。
        val metaColor = if (darkTheme) Color(0xFFB0BEC5) else Color(0xFF37474F)
        val voiceColor = if (darkTheme) Color(0xFFA08B7A) else Color(0xFF7D6B5D)
        // 角色名（11-10 三改，用户令「角色名改主题主色，成功保持绿」）：改取主题主色。
        //
        // ## 为什么角色名要跟主题、而发音人/插件名不跟
        //
        // 关键差别在**会不会和「成功」撞色**：
        // - 角色名原来是纯绿 #009908，与成功字色 #4CAF50 实测**色相只差 1.4 度**
        //   （142.8° vs 144.2°），人眼读作同一个绿；两者又同处一行（行头「成功」、
        //   下一行「角色名」），"状态绿"和"角色绿"抢同一个语义。
        // - 发音人（棕褐 #7D6B5D）与插件名（灰青 #5B6472）跟主色系天然分开，
        //   换主题也不会与任何状态色撞，所以保持固定（信息类别色）。
        //
        // 改主色后：绿色主题下是绿（明度 0.612，比成功绿 0.673 接近，需靠位置区分），
        // 紫色/蓝色/深灰下色相彻底不同，一眼可分。
        //
        // ## 沿革
        // 老日志绿（与成功丸撞）→ 靛蓝 #3949AB（10-11 装机嫌突兀）→ 纯绿 #009908
        // （10-14 调色台自选）→ **主题主色**（11-10，撤胶囊后与「成功」同屏更需区分）。
        //
        // 插件名小牌：灰青，保持固定（理由同上）。
        val roleChipColor = MaterialTheme.colorScheme.primary
        val pluginChipColor = if (darkTheme) Color(0xFF9AA0A8) else Color(0xFF5B6472)

        LazyColumn(
            Modifier
                .fillMaxSize()
                .then(
                    if (dragSelectEnabled) Modifier.pointerInput(Unit) {
                        // 手写 awaitEachGesture。等待长按阶段：本页躺在底栏横向翻页容器里，
                        // 长按期间手指轻微横漂会被外层 pager 抢走变成切页。
                        // 10-07 换栏修复：横移处理加时间闸——落指 150ms 内就越过 slop 的横移
                        // = 快速换栏意图，不消费直接放手让 pager 接管（10-06 版无条件消费，
                        // 把左右滑换栏整个废掉了）；150ms 后才慢慢漂过 slop = 按住时的轻微
                        // 横漂，就地消费保住长按。时间闸取长按超时（400ms）的前段：正常换栏
                        // 横滑几乎都在 150ms 内越过 slop，按住不动的手几乎不会。
                        // 纵向明确滚动立即放行（与 10-06 一致）。
                        // 超时未被取消 = 长按成立（withTimeoutOrNull null 返回值语义）
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val slop = viewConfiguration.touchSlop
                            val downAt = System.nanoTime()
                            val swipeGraceMs = 150L
                            val waited = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    if (change.changedToUpIgnoreConsumed()) break
                                    if (change.isConsumed) break
                                    if (change.positionChangedIgnoreConsumed()) {
                                        val dx = change.position.x - down.position.x
                                        val dy = change.position.y - down.position.y
                                        if (abs(dy) > slop && abs(dy) >= abs(dx)) break // 纵向滚动意图 → 放行
                                        if (abs(dx) > slop || abs(dy) > slop) {
                                            val elapsedMs = (System.nanoTime() - downAt) / 1_000_000
                                            if (abs(dx) > abs(dy) && elapsedMs < swipeGraceMs)
                                                break // 快速横滑 → 不消费，pager 接管换栏
                                            change.consume() // 长按等待期横漂：pager 抢不走
                                        }
                                    }
                                }
                                "done"
                            }
                            val anchor = if (waited == null) logIndexAtY(down.position.y) else null
                            if (anchor == null) return@awaitEachGesture
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnEnterSelection()
                            dragAnchor.value = anchor
                            dragBase.value = currentChecked
                            dragLastIdx.value = anchor
                            dragPointerY.value = down.position.y
                            emitDragSelection()
                            dragActive.value = true
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.first()
                                    // 抬手即收笔；UP 也消费掉，行点击（勾选/快捷面板）
                                    // 不会在松手瞬间再触发一次
                                    if (change.changedToUpIgnoreConsumed()) {
                                        change.consume()
                                        break
                                    }
                                    if (change.positionChangedIgnoreConsumed()) {
                                        change.consume()
                                        dragPointerY.value = change.position.y
                                        updateDragSelection(change.position.y)
                                    }
                                }
                            } finally {
                                dragActive.value = false
                            }
                        }
                    } else Modifier
                ),
            state = listState,
            // 左右基准线（10-10 用户拍板 B 案：统一 16）：容器仍 0，但**裸行/日期签文字/
            // 卡内正文三项同落 16dp 全站 ListGutter 内容线**。
            // 历史：10-05 恢复过「容器 0 + 行内 4」的旧版口径，当时卡片带 16dp 外边距、
            // 卡内文字在 24dp，裸行 4dp 属「贴边族」合理；10-10 卡片改底色通边（撤外边距、
            // 内衬 16）后卡内文字落到 16dp，裸行却没跟着动 ⇒ 裸行成了全页最靠左的一列
            //（比日期签还左），页面出现三条左线。此为那次改卡片漏掉的对齐。
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
                itemsIndexed(currentGroups.items, key = { index, _ -> index }) { _, item ->
                    when (item) {
                        // 11-11 用户令：书条/章条不再出现在日志流里，改为**吸顶**
                        //（在 TtsLogScreen 的 topBar 下方固定渲染）。此处保留分支只因
                        // Item 是 sealed class 需要穷举，实际不会再产出这两个 Item。
                        is LogGroups.Item.BookBar -> Unit
                        is LogGroups.Item.ChapterBar -> Unit
                        is LogGroups.Item.Header -> {
                            // 日期签：每天第一条上方出现一次（10-06：日期不再逐行重复）。
                            // 签盒 start 7 + 签内衬 9 = 签文字 16，与裸行/卡内正文同线
                            Text(
                                text = item.date,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 7.dp, top = 10.dp, bottom = 4.dp)
                                    .background(
                                        // 日期签底色（11-10 十二改，用户令「三处都恢复以前的绿」）。
                                        //
                                        // 直接取 surfaceContainerHighest 是错的：该槽在 04:39
                                        // 中性化后四套主题全被压成 C=0.002 的灰（绿 #E4E6E5 /
                                        // 紫 #E6E5E7 / 蓝 #E5E5E7 / 黑 #E5E5E5），以前是
                                        // 绿 #DAF1E1 / 紫 #F0DEFB，彩度差 13 倍。
                                        //
                                        // 但**不能改 Highest 槽本身**——它还同时供给
                                        // TtsLogScreen 的 UNKNOWN 级别色、VoicePickerDialog 的
                                        // 只读字段底，一改三处一起变。故日期签单独推导：
                                        // secondaryContainer（主题浅容器档）往卡底混 10%，
                                        // 绿 → #DAF2E2（L=0.939 / C=0.033，与旧值 #DAF1E1
                                        // L=0.937 / C=0.032 基本重合）。
                                        // 四套主题同一算式，各自带自己的色相（紫 #EFE7FE /
                                        // 蓝 #E1ECFE / 深灰 #EBECEF 仍近中性，符合深灰主题无彩定位）。
                                        lerp(
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            MaterialTheme.colorScheme.surfaceContainerLow,
                                            0.10f
                                        ),
                                        AppShapes.md
                                    )
                                    .padding(horizontal = 9.dp, vertical = 2.dp)
                            )
                        }

                        is LogGroups.Item.Bare -> {
                            val log = list[item.index]
                            val checked = log in checkedEntries
                            val isMatch = isMatchEntry(log, searchQuery)
                            // 裸行内容一份定义，滑删/非滑删两分支共用（避免复制整段 Column）
                            val bareContent: @Composable () -> Unit = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // 左滑删除底层透底修复（10-10 装机截图实锤）：裸行无底色时
                                    // SwipeToDismissBox 的 errorContainer 粉底+垃圾桶整行常驻透出。
                                    // 前景补页底色（background=页底，浅色纯白/深色锚同色）盖住
                                    // 底层，左滑时才从右侧露出删除提示
                                    .background(MaterialTheme.colorScheme.background)
                                    .then(
                                        if (selectionMode) Modifier.clickable { onToggleCheck(log) }
                                        else Modifier
                                    )
                                    .then(
                                        if (checked) Modifier
                                            .clip(AppShapes.sm)
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                            )
                                        // 搜索命中底色整撤（10-10 装机用户令）：图二搜索态命中项
                                        // 整行淡绿块太重，命中信息由「N 处匹配」计数与跳转承担，
                                        // 行本身保持素底
                                        else Modifier
                                    )
                                    // 10-10 B 案：裸行左缘 4→16，与卡内正文/日期签文字同落
                                    // 全站 ListGutter 16dp 线（原本 4dp 是卡片带外边距时代的
                                    // 「贴边族」口径，卡片改通边后失效）。纵向 3.5 未动。
                                    .padding(horizontal = 16.dp, vertical = 3.5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 行内元素套 Row(CenterVertically)：与组头行同因——FlowRow
                                    // 行内元素顶对齐，丸比时间文字高，中心错位（10-11 用户令）
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (selectionMode) {
                                            Icon(
                                                imageVector = if (checked) Icons.Default.CheckBox
                                                else Icons.Default.CheckBoxOutlineBlank,
                                                contentDescription = null,
                                                modifier = Modifier.padding(end = 4.dp).size(16.dp),
                                                tint = if (checked) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        // 时间去日期去毫秒（10-10 用户令，与请求组头行同口径）：
                                        // 日期签在列顶、毫秒无人看；裸行时间染次级灰与头行一致。
                                        // 合并组走 headTimeText 出「首末完整时间」（10-11 用户令）。
                                        Text(
                                            text = headTimeText(log),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = LOG_AUX_SIZE,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        // 行级丸（10-10 终案）：重试 W 主行=重试N、兜底/备用主行=
                                        // 切备用、失败主行=失败——失败链单条化后丸跟着各行走；
                                        // 插件/规则/系统消息/源错误子行裸行 pillOf 返回 null 不渲染
                                        pillOf(log, item.index)?.let {
                                            Box(Modifier.padding(start = 6.dp)) { StatusPill(it) }
                                        }
                                    }
                                    // ⚠️ 级别字母（I/W/E）整删（10-10 用户令「W,I 这种字母都删除」）：
                                    // 胶囊已承担状态表达,字母冗余
                                    // 「回原位」定位键（10-10 装机反馈用户令，10-11 用户令换显示）：
                                    // 筛选/搜索态裸行同样可跳回完整流原位（此前只有请求组头行有，
                                    // 插件/规则日志搜到后没键回不去）；弹簧顶最右，样式同组头行。
                                    // 10-11 换法：①撤 ⟲ 符号（"撤销/重来"语义，与"跳回原位"不搭）；
                                    // ②颜色沿用 primary 字 + 淡紫底（softContainerColor，与全站分类胶囊同款）——
                                    // 底色把"可点的钮"和正文隔开，比裸灰字醒目、又不与日志正文混读（10-11 用户定稿）
                                    if (showLocateKey) {
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            text = "回原位",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                                .clip(AppShapes.md)
                                                .background(softContainerColor())
                                                .clickable { onLocateOriginal(log) }
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                // 源错误失败条裸行（10-13 晚用户实锤「引用的正文字号不统一」）：
                                // 同一份「源错误：…+引用正文+发音人」内容，组头吸收路径走
                                // oldStyleFail 三段分层（错误串13/正文14粗/发音人13），裸行路径
                                // 却整体 13sp——同屏一大会一小。判定=失败终点（ERROR 裸行）
                                // 且消息带 <b> 与两段以上 <br> 结构（服务端源错误全文的标志；
                                // ⚠️ 原判据数 '\n' 字符是错的——entry.message 是原始 HTML，
                                // <br> 是标签不是换行符，永远数出 0，分层从未生效），
                                // 命中即走同一分层；行高同步提到 18.2（=14×1.3，与组头同规格）。
                                // 其余裸行（规则/插件消息）维持 13sp 不动。
                                val bareFailStyle = log.level == LogLevel.ERROR && log.indent == 0 &&
                                    log.message.contains("<b>", ignoreCase = true) &&
                                    Regex("<br", RegexOption.IGNORE_CASE).findAll(log.message).count() >= 2
                                LogEntryBody(
                                    entry = log,
                                    darkTheme = darkTheme,
                                    metaColor = metaColor,
                                    voiceColor = voiceColor,
                                    roleChipColor = roleChipColor,
                                    pluginChipColor = pluginChipColor,
                                    // 裸行=正文档（11-11 统一走 LOG_BODY_SIZE，与请求主行同大）
                                    fontSize = LOG_BODY_SIZE,
                                    lineHeight = if (bareFailStyle) 18.2.sp else 17.sp, // 13×1.3
                                    oldStyleFail = bareFailStyle,
                                    // 命中高亮已在整行背景，正文不再叠一层
                                    isMatch = false,
                                    highlight = log == locateHighlight,
                                    // 重试行不画正文（10-11 用户令修正）：服务端保留「开始第 N 次
                                    // 重试」原文只为让「重试N」丸解析得到，行头丸已表达同一信息，
                                    // 正文再画一遍是重复。此前把正文撤成空串导致丸也推不出来
                                    //（真机表现为「只有时间、下面空白」的一行），此处改为
                                    // 服务端留原文 + 渲染层不画正文，既不重复也不空行
                                    suppressBody = pillOf(log, item.index)?.kind == LogPillKind.RETRY,
                                )
                            }
                            }
                            // 左滑删除整撤（10-10 装机用户令「只是看日志而已，为啥要有删除」
                            // +「上次都让你改结果还是这样」——10-10 白天 M3 三件套批加的该功能
                            // 本身不该存在：日志是只读流，删除走顶栏垃圾桶清空+多选勾删两通道）。
                            // SwipeToDismissBox 包装层、透底修复（前景补 background）、
                            // onRemoveEntry 参数与 VM.removeEntry 已于 10-11 一并清干净。
                            bareContent()
                            // 条目间分隔线（10-10 用户令「分隔线需要加」）：行流撤卡后行间只剩
                            // 空白，加发丝线恢复条目边界；日期签前不画（签自带灰底已是边界）。
                            // 口径沿用顶栏分隔线 outlineVariant@50%
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }

                        is LogGroups.Item.Group -> {
                            val head = list[item.head]
                            val memberEntries = item.members.map { list[it] }
                            val groupLogs = currentGroups.entriesOf(item).map { list[it] }
                            val groupChecked = groupLogs.all { it in checkedEntries }
                            // 老版式失败条（10-11 用户定稿「拿不准就按老版本，毕竟是起源」）：
                            // absorbError≥0 = 本组以源错误收场，渲染主体换成源错误条目
                            //（服务端已拼回请求全文：错误+正文粗+发音人信息）——
                            // 胶囊「源错误」+英文错误红+正文红粗+发音人信息不红，
                            // 请求主行正文不再重复（同一次失败一条说完，老版观感）
                            val absorbed = if (item.absorbError >= 0) list[item.absorbError] else null
                            // 行级丸：成功=「成功」（从组内 SUCCESS 成员推导——成功子行隐藏
                            // 信息上头行，主行是 INFO 推不出，aad9867 修）；失败=「源错误」
                            //（有吸收条）/「失败」（其他死法：超时/获取失败等，文案跟错误类型走）。
                            // ⚠️ 未决不兜底（10-11 用户实报「请求音频刚发出就标失败，成功后翻绿」）：
                            // 请求行发出到结果子行到达有几秒空窗——组开着、SUCCESS 没到、
                            // failedHeads 也没记，旧兜底 `?: LogPill(FAIL,"失败")` 把空窗误画成红丸。
                            // 改为没有失败证据就不出丸（结果到了由 SUCCESS/failedHeads/源错误
                            // 接管），等待期行头只有时间，与「过程未知」的语义一致
                            val successMember = memberEntries.firstOrNull { it.level == LogLevel.SUCCESS }
                            val pill = remember(item.head, successMember, absorbed, currentGroups) {
                                when {
                                    successMember != null -> LogPill(LogPillKind.OK, "成功")
                                    // 丸只报状态（10-12 用户令）：原来写「源错误」——那是**原因**，
                                    // 不该占丸位。现在一律「失败」，原因交给正文首行（服务端
                                    // 已改成「源错误：…」开头，见 SystemTtsService.errorEvent）
                                    absorbed != null -> LogPill(LogPillKind.FAIL, "失败")
                                    else -> pillOf(head, item.head, currentGroups.failedHeads)
                                }
                            }
                            // 丸不带 ×N（10-12 用户令整体撤除，理由见 pillOf 上方注释）。
                            // 旧的 failCount/pillShown 后缀拼装一并删掉——同一信息在
                            // 「失败 ×2 / 重试×2 / 失败×3」三枚丸里重复出现，用户读成乱的
                            val pillShown = pill
                            // 老版式失败条无丸后灰字（错误全文在正文区），成功尾段照旧
                            val tailText = remember(item.head, successMember, absorbed) {
                                if (absorbed != null) null else tailTextOfRow(successMember ?: head)
                            }
                            // 组头时间（10-12 用户令「起始时间到结束时间都会有」）：成功组=请求→成功，
                            // 失败组=请求→失败（合并过的取末次失败时间）。起点统一是**请求行**，
                            // 不再是「吸收条自己的时间」——同一页所有成对行都读成「从发起到有结果」
                            val groupHeadTime = groupTimeText(head, successMember ?: absorbed)
                            // 行流定稿（10-10 用户令）：成功=行头绿丸+灰数字，成员区整撤
                            // （结果信息已上头）；失败/重试/切备用的过程行仍要露出——
                            // 有非成功成员行时显示成员区（成功行本身不上，数字已在丸后）
                            val showMembers = memberEntries.any { it.level != LogLevel.SUCCESS }
                            Column(
                                modifier = Modifier
                                    // 行流：容器全撤，左缘直接落全站 16dp ListGutter 线；
                                    // 行间用纵向间距分隔（首行顶 6dp，行与行 8dp 由外层间隔表达——
                                    // 这里用统一 vertical 6dp，视觉等同旧行距但无线）
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .then(
                                        if (selectionMode) Modifier.clickable {
                                            // 组=多选粒度：整组勾/整组取消
                                            if (groupChecked)
                                                onCheckedChange(checkedEntries - groupLogs.toSet())
                                            else
                                                onCheckedChange(checkedEntries + groupLogs.toSet())
                                        }
                                        // 非多选：点行弹快捷面板（换发音人），锚定请求主行
                                        else Modifier.clickable { quickPanelEntry = head }
                                    )
                            ) {
                                // 头部行（10-10 装机反馈两令）：①撤丸间「→」——成功一行
                                // 只有「时间 成功 大小·耗时」，箭头是多余符号（用户不认），
                                // 保留 FlowRow 换行（药丸链过长仍折行不截断）；②对齐——
                                // ⚠️ FlowRow 没有 verticalAlignment 参数（CI 37959867285 实锤，
                                // 静守卫盲区），主轴对齐走 verticalArrangement=Arrangement.Center；
                                // 丸与文字垂直同轴另由丸内衬对齐（labelSmall 自身高度小）
                                FlowRow(
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // 行内元素套 Row(CenterVertically)（10-11 用户令「药丸内的文字
                                    // 和时间不在一个水平线上」）：FlowRow 的 verticalArrangement
                                    // 只管**行与行**的纵向排布，行内元素是顶对齐的——丸（labelSmall
                                    // +2dp 内衬）比时间文字高，顶对齐后中心错位。套一层 Row 纵向居中即齐。
                                    // 「回原位」键留在 FlowRow 里用 weight 顶右（Row 内 weight 只管行内）。
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (selectionMode) {
                                            Icon(
                                                imageVector = if (groupChecked) Icons.Default.CheckBox
                                                else Icons.Default.CheckBoxOutlineBlank,
                                                contentDescription = null,
                                                modifier = Modifier.padding(end = 4.dp).size(16.dp),
                                                tint = if (groupChecked) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            // 时间字号 13→12sp（10-12 用户令「时间栏字号缩小一号」）：
                                            // 与正文 14sp 拉开两档，进一步让行内重心落在丸上
                                            text = groupHeadTime,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = LOG_AUX_SIZE,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        // 状态链（10-12 三改，用户令「这个乘几是不是错误的改法…干脆去掉吧；
                                        // 重试要不要另外起一行，你来定吧」；11-10 三改撤过胶囊、同日四改已恢复）：
                                        // ①所有丸一律不带 ×N（两套语义打架，理由见 pillOf 上方注释）；
                                        // ②重试字**不另起一行**，仍并排在状态胶囊后——用户 10-12 上午刚定过
                                        //   「重试和失败是紧密联系的，别单独占一行」，另起行会把一次失败
                                        //   撑成三行，听书时刷屏；并排时「失败 重试」已把过程说清。
                                        // ③「失败×N」第三枚累计丸整撤（它就是用户点名那个乘几）。
                                        // 元素次序维持「时间 → 胶囊 → 尾串」（10-12 用户令「其他不变」）：
                                        // 曾把成功字提到时间前，用户明确否掉，别再动。
                                        val retryPill = if (item.retryCount > 0)
                                            LogPill(LogPillKind.RETRY, "重试") else null
                                        pillShown?.let {
                                            Box(Modifier.padding(start = 6.dp)) { StatusPill(it) }
                                        }
                                        // 成功但重试过：过程同样要看得见 → 「成功 重试」
                                        retryPill?.let {
                                            Box(Modifier.padding(start = 6.dp)) { StatusPill(it) }
                                        }
                                        // 胶囊后的灰字（成功=「耗时，大小」/失败主行=截短原因），留 6dp 隙。
                                        // 11-10 四改恢复胶囊后，胶囊与尾串之间靠 6dp 间隙分隔（不再靠空格）；
                                        // 三改期间"只用空格"的口径随胶囊一起作废。
                                        // 10-12 用户令「后两项交换下位置，并用逗号隔开，其他不变」：
                                        // 重排已在 tailTextOfRow 里做完（1.2s，103.0 KB），此处只管画。
                                        // 10-13 深夜用户令「胶囊后边的文字大小跟时间大小不一致」：13sp→12sp，
                                        // 与同一行的时间栏（12sp）和胶囊内文字（12sp）统一。
                                        // 失败原因红=旧正宗红 #F44336（10-10 用户令「错误的红色要用
                                        // 以前正宗的红色」——colorScheme.error 在动态取色下偏暗紫红）
                                        if (tailText != null) {
                                            Text(
                                                text = tailText,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = LOG_AUX_SIZE,
                                                color = if (pillShown?.kind == LogPillKind.FAIL)
                                                    Color(if (darkTheme) 0xFFE57373 else 0xFFF44336)
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                    // 「回原位」定位键（筛选/搜索态）：弹簧顶到最右，动作留右手位。
                                    // 10-11 换显示：淡紫底胶囊（与裸行同口径）
                                    if (showLocateKey) {
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            text = "回原位",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                                .clip(AppShapes.md)
                                                .background(softContainerColor())
                                                .clickable { onLocateOriginal(head) }
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                // 老版式失败条（10-11 定稿）：渲染主体换成被吸收的源错误/
                                // 超时条目（服务端已拼请求全文），请求主行正文不再重复——
                                // 成功组不变（head 请求行照旧）。13sp 与成员行/裸行同档
                                if (absorbed != null) {
                                    LogEntryBody(
                                        entry = absorbed,
                                        darkTheme = darkTheme,
                                        metaColor = metaColor,
                                        voiceColor = voiceColor,
                                        roleChipColor = roleChipColor,
                                        pluginChipColor = pluginChipColor,
                                        // 11-11 恢复：卡内正文原为 14sp（与上面主行同档），收档时被压到 13。
                                        fontSize = LOG_HEAD_SIZE,
                                        lineHeight = 18.2.sp, // 14×1.3（还原收档前的原值）
                                        isMatch = isMatchEntry(absorbed, searchQuery),
                                        emphasizeError = true,
                                        oldStyleFail = true,
                                        highlight = absorbed == locateHighlight,
                                    )
                                } else {
                                LogEntryBody(
                                    entry = head,
                                    darkTheme = darkTheme,
                                    metaColor = metaColor,
                                    voiceColor = voiceColor,
                                    roleChipColor = roleChipColor,
                                    pluginChipColor = pluginChipColor,
                                    // 请求主行 14sp（10-10 字号令纠偏：「缩到与声音信息一样」
                                    // 只指插件/规则裸行；主行恢复排版实验 1008 定版 14sp，
                                    // 内三段分层回前缀13/正文14/声音12——见 LogEntryBody isRequestHead）
                                    // 11-11 恢复：收档时这里被压到 13，现用 LOG_HEAD_SIZE 回到 14。
                                    fontSize = LOG_HEAD_SIZE,
                                    lineHeight = 18.2.sp, // 14×1.3（还原收档前的原值；收档时曾压到 17）
                                    isMatch = isMatchEntry(head, searchQuery),
                                    // 排版实验 1008（C）：只染"请求音频："前缀，正文回默认色
                                    isRequestHead = true,
                                    highlight = head == locateHighlight,
                                )
                                }
                                // 过程成员行（10-10 用户令终版）：失败丸行（时间+丸，无文字）
                                // 之后，**失败的详细信息单独列一行**——成员区 ERROR 行（完整
                                // 「源错误： xxx」）必须显示；成功行不上（数字已进丸后）。
                                // WARN 重试/兜底等过程行不在成员区（失败链单条化后它们已
                                // 独立成裸行，见裸行分支）
                                if (showMembers) {
                                    item.members.forEach { mIdx ->
                                        val m = list[mIdx]
                                        if (m.level == LogLevel.SUCCESS) return@forEach
                                        Column(Modifier.padding(top = 2.dp)) {
                                            LogEntryBody(
                                                entry = m,
                                                darkTheme = darkTheme,
                                                metaColor = metaColor,
                                                voiceColor = voiceColor,
                                                roleChipColor = roleChipColor,
                                                pluginChipColor = pluginChipColor,
                                                fontSize = LOG_BODY_SIZE,
                                                lineHeight = 17.sp, // 13×1.3
                                                isMatch = isMatchEntry(m, searchQuery),
                                                emphasizeError = true,
                                                highlight = m == locateHighlight,
                                            )
                                        }
                                    }
                                }
                            }
                            // 条目间分隔线（10-10 用户令）：与裸行分支同款，组与组之间画线
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                item {
                    // 尾部 Spacer 曾用 navigationBarsPadding（手势条避让）——与本页上游
                    // MainPager bottomPad（含手势条）+ TtsLogScreen calculateBottomPadding
                    // 两层避让叠加，列表末尾与底栏之间多出一条空白（10-10 装机截图实锤）。
                    // 改固定 8dp 只做行尾呼吸，手势条高度由上游两层负责
                    Spacer(Modifier.height(8.dp))
                }
            }

        // 侧边浮动键（用户 1002）：右下浮动 ↓=回底部；回顶部走双击标题栏空白区
        // 排版实验 1008（①已拍板）：56dp 标准键 → 40dp 小键，原地缩小少盖正文
        //
        // 11-10 用户实报「点这个向下箭头总是误触日志换发声人弹窗」：
        // 键本体只有 40dp（低于 Material 标准的 48dp 最小可点），又正压在「最新日志」
        // 那一行上——拇指向下戳时稍偏几个像素就落到下面的请求行，而请求行是**整行可点**
        // 弹车声面板的。修法不是缩小它，而是把**可点区域**放大到 56dp（视觉仍是 40dp），
        // 外加一圈同色描边+投影让它从正文里「浮起来」，误触判定立刻分化。
        // 为什么不再改成长按：长按已被本页的「拖选多选」独占（见 dragSelectEnabled
        // 那套 awaitEachGesture），再挂一个长按会和进多选直接打架（用户 11-10 指出）。
        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 键缩小后 48dp 遗留外距把键顶离屏底太远（用户 10-08：太靠上）→20dp，
                // 拇指自然可及也不压底栏
                .padding(bottom = 20.dp),
            visible = showJumpToBottom,
            enter = fadeIn() + expandIn(expandFrom = Alignment.BottomCenter),
            exit = shrinkOut(shrinkTowards = Alignment.BottomCenter) + fadeOut(),
        ) {
            // 外层 Box 承担 56dp 触摸区（透明），内层 40dp 才是可见键——
            // Compose 里触摸区由最外层有尺寸的节点决定，所以不能只给 FAB 加 padding
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clickable(
                        // 去涟漪但保留可达性语义：这个键只干一件事（滚到底）
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        scope.launch {
                            kotlin.runCatching {
                                listState.scrollToItem(currentGroups.items.lastIndex)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                // 视觉键（40dp）：加投影让它在日志正文上明确"浮起"，
                // 点击仍走外层 56dp 区（这里不挂 onClick，避免双触发）
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.KeyboardDoubleArrowDown,
                            stringResource(id = R.string.move_to_bottom),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

// 搜索命中判定（条目级）：正文或时间含搜索词
private fun isMatchEntry(e: LogEntry, q: String): Boolean =
    q.isNotEmpty() && (e.message.contains(q, ignoreCase = true) || e.time.contains(q, ignoreCase = true))

// 行级药丸推导（10-10 终案「失败链按以前单条处理+丸保留」）：每条日志按自己的
// 级别/文案出一颗丸——重试 W 主行=重试N、兜底/备用 I 主行=切备用、成功请求行=成功、
// 失败请求行=失败（依据 failedHeads：请求行本身是 INFO，失败标记由归组记入）、
// ERROR 主行（无卡区失败终点）=失败。成员行（源错误等结果子行）已独立裸行=无丸。
// 丸字=汉字「成功/失败」（10-11 用户令：✔/✕ 符号加了不好看，换回文字）
//
// 丸上的「×N」后缀已于 10-12 整体撤除（用户令「我怀疑这个乘几是不是错误的改法，因为它总是
// 显示的有问题，要不干脆去掉吧」）。撤除根因：同一个 ×N 在两类位置上语义不同——
// 插件/规则裸行是「同因折叠了几条」（ViewModel 的 repeatCount），请求组头读起来却是
// 「本组重试了几次」，同页两种读法必然误读。次数对「听书时哪儿断了」也无用。
// 需要次数时看正文里的重试行（「开始第 N 次重试」原文照旧保留）。
private fun pillOf(e: LogEntry, entryIndex: Int, failedHeads: Set<Int> = emptySet()): LogPill? {
    val msg = e.message.replace(Regex("<[^>]*>"), "")
    return when {
        e.configId != 0L -> when {
            e.level == LogLevel.SUCCESS -> LogPill(LogPillKind.OK, "成功")
            entryIndex in failedHeads -> LogPill(LogPillKind.FAIL, "失败")
            else -> null
        }
        msg.contains("次重试") || msg.contains(" retry", true) ->
            // 重试丸只报「重试过」这一事实，不带次数（10-12 与撤 ×N 同口径）
            LogPill(LogPillKind.RETRY, "重试")
        msg.startsWith("使用备用") || msg.startsWith("使用兜底") ->
            LogPill(LogPillKind.STANDBY, "切备用")
        e.level == LogLevel.ERROR && e.indent == 0 -> LogPill(LogPillKind.FAIL, "失败")
        else -> null
    }
}

/**
 * 行头时间文案（10-11 用户令「首末完整」，10-12 用户令「所有日志时间统一 + 起始→结束」）：
 * 全站只有一种写法：`HH:mm:ss`（单点）或 `HH:mm:ss→HH:mm:ss`（区间，一律箭头）。
 * 历史遗留的 en dash（合并失败曾用 `–`）已撤——同一页不该出现两种连接符。
 * 时间源格式 `yyyy-MM-dd HH:mm:ss.SSS`，去日期去毫秒后长度恰为 8。
 */
private fun headTimeText(e: LogEntry): String {
    val start = e.time.drop(11).dropLast(4)
    val end = e.endTime.takeIf { it.length >= 19 }?.drop(11)?.dropLast(4)
    return if (e.repeatCount > 1 && !end.isNullOrBlank() && end != start) "$start→$end" else start
}

/**
 * 成对行的时间区间（10-12 用户令「起始时间到结束时间会有吗」）：起点=请求行时间，
 * 终点=结果行时间（成功子行 / 失败全文条；合并过的取它的末条时间 endTime）。
 * 只有终点确实晚于起点才出区间；取不到终点或同一秒 → 退回单时间。
 */
private fun groupTimeText(startEntry: LogEntry, endEntry: LogEntry?): String {
    val start = startEntry.time.drop(11).dropLast(4)
    if (start.length != 8) return headTimeText(startEntry)
    val endRaw = endEntry?.let { e ->
        e.endTime.takeIf { it.length >= 19 } ?: e.time.takeIf { it.length >= 19 }
    } ?: return start
    val end = endRaw.drop(11).dropLast(4)
    return if (end != start) "$start→$end" else start
}

/** 两个日志时间是否相隔不超过 seconds 秒（重试挂「上一张失败组」的身份校验用；格式 yyyy-MM-dd HH:mm:ss.SSS） */
private fun withinSeconds(a: String, b: String, seconds: Int): Boolean {
    if (a.length < 19 || b.length < 19) return false
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
    return runCatching {
        val ta = fmt.parse(a)?.time ?: return@runCatching false
        val tb = fmt.parse(b)?.time ?: return@runCatching false
        kotlin.math.abs(tb - ta) <= seconds * 1000L
    }.getOrDefault(false)
}

// 行级丸后灰字：只服务成功行=「耗时，大小」（10-12 用户令「1.2s，103KB 后两项交换下位置，
// 并用逗号隔开，其他不变」——原为「103.0 KB · 1.2s」：①两项交换，耗时在前；②「·」改中文逗号）。
// 失败丸后不跟 IOException 文案、失败丸单独一行——失败行的异常细节走成员区完整源错误行。
// 截短 40 全角字逻辑保留（成功行数字不长，此处主要防御）。
private fun tailTextOfRow(e: LogEntry): String? {
    val msg = e.message.replace(Regex("<[^>]*>"), "")
    val t = when {
        e.level == LogLevel.SUCCESS ->
            msg.substringAfter("获取成功：", "").trim().ifEmpty { null }
        else -> null // 失败行不放原因（用户令）
    } ?: return null
    // 服务端原文形如「103.0 KB · 1.2s」：按用户给的示例「1.2s，103KB」重排 ——
    // **耗时在前、大小在后**，中间用中文逗号（＝把后两项交换位置）。
    // 拆不开（格式变了）就原样返回，不让排序逻辑吃掉内容。
    val parts = t.split("·").map { it.trim() }.filter { it.isNotEmpty() }
    val reordered = if (parts.size == 2) "${parts[1]}，${parts[0]}" else t
    var w = 0f
    val out = StringBuilder()
    var clipped = false
    for (ch in reordered) {
        val cw = if (ch.code < 0x2E80) 0.55f else 1.0f
        if (w + cw > 40f) { clipped = true; break }
        w += cw
        out.append(ch)
    }
    return if (clipped) out.toString().trimEnd() + "…" else reordered
}

internal enum class LogPillKind { OK, FAIL, RETRY, STANDBY }
internal data class LogPill(val kind: LogPillKind, val text: String)

// 状态药丸（10-10 行流定稿；11-10 四改：成功改主题主色，其余三态不变）：丸只装状态字；
// 丸后灰字（耗时·大小/失败原因）由调用方另排。
// ⚠️ 不用 Pair 解构（when 分支混用 to 与 if 表达式时编译器推成 Any）——分两个 when
/**
 * 行头状态胶囊（11-10 深夜四改：用户令「只把胶囊加回去，成功的颜色改成主题色」）。
 *
 * ## 沿革（别再反复）
 *
 * 10-11 淡底重字定稿 → 11-10 三改撤胶囊改纯文字 → **11-10 四改恢复胶囊**。
 * 三改的撤除理由是「多数派不需要色块」，但用户实机看完一圈（含纯文字对比图）后
 * 明确要回胶囊形态——胶囊是这套日志的识别特征，撤掉后行头散、不好扫。已按用户令恢复。
 * ⚠️ 不要再自作主张撤掉。
 *
 * ## 成功 = 主题主色（本次唯一改色项）
 *
 * 原成功字 #4CAF50 是固定绿、不跟主题，在紫/蓝/黑主题下与界面割裂。改取
 * `colorScheme.primary`，换主题时整排「成功」跟着变。
 *
 * 代价如实记录：绿色主题下主色 #109D58 对淡底只有约 3.2:1（低于正文 4.5），
 * 这是**用户明确选择**的结果，不是疏漏，别再顺手"修正"回 #4CAF50。
 *
 * 胶囊底同理跟着主色走（页底混 8% 主色），否则蓝主题会出现"蓝字压淡绿底"的撞色。
 * 失败/重试/切备用三态**完全不动**：语义色（红=错）不随主题漂。
 */
@Composable
private fun StatusPill(pill: LogPill) {
    val dark = isAppInDarkTheme()
    val primary = MaterialTheme.colorScheme.primary
    val fg = when (pill.kind) {
        // 成功 = 主题主色（11-10 四改，用户令）。深浅由主题自己给（深色主题的 primary
        // 本身就是提亮档），不再另配深色值。
        LogPillKind.OK -> primary
        LogPillKind.FAIL -> Color(0xFFC62828)
        LogPillKind.RETRY -> if (dark) Color(0xFF9AA0A8) else Color(0xFF4A5568)
        LogPillKind.STANDBY -> if (dark) Color(0xFFC9A94E) else Color(0xFF9A7B14)
    }
    val bg = when (pill.kind) {
        // 成功底随主色：页底混 8% 主色，自动带当前主题色相（原固定淡绿 #F2F8F4 在
        // 蓝/紫主题下会与主色字撞色）
        LogPillKind.OK -> lerp(MaterialTheme.colorScheme.background, primary, 0.08f)
        LogPillKind.FAIL -> Color(0xFFFDEEED)
        LogPillKind.RETRY -> if (dark) Color(0xFF2A2D33) else Color(0xFFF3F5F8)
        LogPillKind.STANDBY -> if (dark) Color(0xFF3A3226) else Color(0xFFFDF7E7)
    }
    // Box 居中（10-12 用户令「字一定要居中对齐」）：Text 直接挂 padding 时字形上下留白
    // 随字重/基线浮动，视觉偏上；Box(contentAlignment=Center) + 内衬给 Box 才几何居中。
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = pill.text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = fg,
        )
    }
}

// 单条日志正文渲染：HTML → AnnotatedString + 哨兵色重映射，级别色或指定色
@Composable
private fun LogEntryBody(
    entry: LogEntry,
    darkTheme: Boolean,
    metaColor: Color,
    voiceColor: Color,
    // 角色名/插件名小牌字色（10-10 行流定稿；背景色 span 由 HTML font 标签来不了，
    // 牌感靠字色+前后空隙表达，字号 12sp 由声音信息段统一压）
    roleChipColor: Color,
    pluginChipColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    isMatch: Boolean,
    // 非空=成员行统一用次级色（石板灰/暗红），级别色让位
    forceColor: Color? = null,
    // 排版实验 1008（C）：请求主行——"请求音频："前缀染深绿、其余回默认色。
    // 请求行 HTML 结构固定：`请求音频：` + <b>正文</b>（+ 哨兵色次级段），
    // 据此把首段（正文之前的裸文本）与前缀分开着色
    isRequestHead: Boolean = false,
    // 排版实验 1008（E）：错误行加粗 + ⚠ 行首标，让错误在行流里突出于成功行
    emphasizeError: Boolean = false,
    // 老版式失败条分段染红（10-11 用户定稿「拿不准就按老版本」）：源错误/超时条目含
    // 全文（错误 + <br> + <b>正文</b> + <br> + 发音人信息），老版观感=错误行与正文
    // 正文红、发音人信息不红——条目级级别色做不到分段，这里按首个 <br> 分段实现：
    // 发音人信息段（第二个 <br> 起）保持原哨兵色，前两段染老红
    oldStyleFail: Boolean = false,
    // 「原文」定位命中（用户 10-08 甲方案）：黄底高亮闪现，由上层定时清除
    highlight: Boolean = false,
    // 不画正文（10-11 用户令）：重试行用——服务端保留「开始第 N 次重试」原文供
    // pillOf 解析出「重试N」丸，丸已表达同一信息，正文再画一遍是重复。
    // 行头（时间+丸）照常渲染，故不会出现「只有时间、下面空白」的空行
    suppressBody: Boolean = false,
) {
    if (suppressBody) return
    val spanned = remember(entry.message, darkTheme, metaColor, voiceColor, roleChipColor, pluginChipColor, isRequestHead, emphasizeError, oldStyleFail) {
        val base = HtmlCompat.fromHtml(entry.message, HtmlCompat.FROM_HTML_MODE_COMPACT)
            .toAnnotatedString()
            .remapMetaColor(metaColor, voiceColor, roleChipColor, pluginChipColor)
        var s = base
        if (isRequestHead) {
            // 字号分层回 1008 定版（10-10 用户令纠偏：「缩到与声音信息一样」只指插件/
            // 规则裸行；主行三段=前缀 13 / 正文 14（<b> 加粗承担字重）/ 声音信息 12）。
            // 段界：前缀=首个"："及之前；声音信息=哨兵色 span（remapMetaColor 后已是
            // 雾紫/石板灰目标色）；两者之间=正文。
            val prefixEnd = base.text.indexOf("：").let { if (it >= 0) it + 1 else 0 }
            // 声音信息段起点 = 首个哨兵色重映射 span 的 start（remapMetaColor 只对
            // MetaColorSentinel/VoiceMetaSentinel 换色，其余 span 不动，可靠定位）
            val metaStart = base.spanStyles
                .firstOrNull { it.item.color == voiceColor }?.start ?: base.text.length
            s = buildAnnotatedString {
                append(base.text)
                base.spanStyles.forEach { r ->
                    var item = r.item
                    if (r.start < prefixEnd) {
                        // 前缀：染深绿 + 13sp（原值 13，不是 12——见常量区注释）
                        item = item.copy(
                            color = if (darkTheme) RequestPrefixColorDark else RequestPrefixColorLight,
                            fontSize = LOG_BODY_SIZE,
                        )
                    } else if (r.start >= metaStart && metaStart < base.text.length) {
                        // 声音信息：哨兵色已换好目标色。字号 13sp（10-11 用户令
                        // 「时间和下方声音条的字号太小」）——与裸行/成员行同档，
                        // 与正文 14sp 仍保一档差
                        item = item.copy(fontSize = LOG_BODY_SIZE)
                    } else if (r.item.color == roleChipColor) {
                        // 角色名（10-13 拆段后的新分支）：它现在**不再**被声音信息那段 font 罩住
                        //（服务端已把外层 font 拆成三段，见 SystemTtsService.configInfo），
                        // 所以拿不到上面那支的 13sp。此处显式补回 13sp，保住与改前同一字号；
                        // 颜色由 remapMetaColor 的 RoleChipSentinel 分支给
                        item = item.copy(fontSize = LOG_BODY_SIZE)
                    }
                    addStyle(item, r.start, r.end)
                }
                // 前缀段可能无 span 覆盖（级别色是 Text 整体 color，不是 span）：
                // 显式补一个 13sp span
                if (prefixEnd > 0) addStyle(SpanStyle(fontSize = LOG_BODY_SIZE), 0, prefixEnd)
                // 声音信息段若无 span 覆盖（整段哨兵色必有 span，此处兜底）：补 13sp
                if (metaStart < base.text.length) {
                    addStyle(SpanStyle(fontSize = LOG_BODY_SIZE), metaStart, base.text.length)
                }
            }
        }
        // 报错三件套②收口（10-10 用户令「最后的源错误信息加粗了，不应该」）：行首 ✖/⚠ 撤 +
        // **W/E 行加粗也撤**——用户明确不加粗；级别色（红/黄）本身就是信号，不再叠加任何强调
        // 老版式失败条（10-11 定稿）：条目结构固定「错误串 + <br> + <b>正文</b> + <br> +
        // 发音人信息」——第二段（正文）染老红，第一段（错误串）由条目级级别色染红，
        // 第三段（发音人信息）保持哨兵重映射色不红。段界=换行符在文本中的下标
        //（fromHtml 后 <br> 变 '\n'）
        if (oldStyleFail) {
            val nl1 = s.text.indexOf('\n')
            val nl2 = if (nl1 >= 0) s.text.indexOf('\n', nl1 + 1) else -1
            if (nl1 >= 0 && nl2 > nl1) {
                val failRed = if (darkTheme) Color(0xFFE57373) else Color(0xFFF44336)
                s = buildAnnotatedString {
                    append(s.text)
                    s.spanStyles.forEach { r -> addStyle(r.item, r.start, r.end) }
                    // 正文段（两个换行之间）整体染老红，压过 <b> 的字重色。
                    // 14sp 显式写死（10-13 晚用户实锤「引用的正文字号不统一」）：
                    // 该段原先吃调用方基础字号——组头吸收路径基础 14sp、裸行基础 13sp，
                    // 同一份源错误同屏两种正文字号。三段规格自此全局唯一：
                    // 错误串 13 / 正文 14 粗 / 发音人 13，与调用方基础字号无关。
                    // 11-11 恢复：正文段回到 14（LOG_HEAD_SIZE），与注释里的三段规格一致。
                    addStyle(SpanStyle(color = failRed, fontWeight = FontWeight.Bold, fontSize = LOG_HEAD_SIZE), nl1 + 1, nl2)
                    // 字号分层（10-13 用户令「源错误这一句字号忽大忽小」根修）：
                    // 错误串段 0..nl1 与发音人段 nl2.. 全 13sp（与成员行/成功行同档）
                    addStyle(SpanStyle(fontSize = LOG_BODY_SIZE), 0, nl1)
                    addStyle(SpanStyle(fontSize = LOG_BODY_SIZE), nl2, s.text.length)
                }
            }
        }
        s
    }
    val bodyColor = forceColor ?: when {
        // ⚠️「成功回旧绿」纠偏（10-10 用户令）：只指头行**药丸里「成功」两字**（药丸
        // 自身绿未动过），SUCCESS 正文行维持原口径=石板灰 metaColor——上版把正文也
        // 染成 INFO 正绿是理解偏了，回退
        entry.level == LogLevel.SUCCESS -> metaColor
        // 排版实验 1008（C）：请求主行正文回默认色（前缀已单独染色），其余行照旧级别色
        isRequestHead -> MaterialTheme.colorScheme.onSurface
        // 11-10 主题化（范围已收紧到只有 INFO）：旧版走 lib-common 的 toArgb()，
        // INFO 恒为 #4CAF50（白底 2.78:1）且换什么主题都是绿的——只把这一级改成主题主色。
        // DEBUG/TRACE/WARN/ERROR/SUCCESS 保持各自原色，见 theme/LogColors.kt 顶部判据。
        else -> logLevelColor(entry.level)
    }
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = fontSize,
        lineHeight = lineHeight,
        // 字号真分层后撤主行整体半粗：正文 <b> 自带字重，先前 SemiBold 叠加
        // 是"主行三段看着没分层"的根源（前缀/声音信息被衬得过轻）
    )
    val hlModifier = if (highlight) Modifier
        .clip(AppShapes.xs)
        .background(Color(0xFFFFE082))
    // 搜索命中底色整撤（10-10 装机用户令）：与裸行整行底同批，命中不再染行
    else Modifier

    // 正文整体一条 Text（10-12 三改）：角色名不再是独立丸——它只是同一条
    // AnnotatedString 里的一个 span（字色 = roleChipColor，见 remapMetaColor），
    // 天然与后随文字同基线、同排版，不存在「对齐不了」。
    // 撤掉的旧路径：把角色名 subSequence 切出来单画 RoleNamePill 圆角丸 + Row(alignByBaseline)——
    // 丸自带内衬/行盒，与旁文只能靠基线硬凑，用户连续两版不满意（10-11 起、10-12 终否）。
    Text(
        text = spanned,
        color = bodyColor,
        style = bodyStyle,
        modifier = hlModifier,
    )
}

// RoleNamePill（角色名圆角丸）已于 10-12 随「角色名改纯字色」整体删除——
// 它只被正文渲染那一处调用，用户否掉丸形态后即成死代码。
// 若要复活「丸」形态，先读 remapMetaColor 与 LogEntryBody 里的终稿注释（为什么撤）。

// ===================== 书籍条 / 章节签（11-10 用户令「日志分小说、分章节」，方案 A）=====================
// 层级：书 > 章 > 日期 > 行。两者都是「分隔头」，不是数据行：
//   · 书条：底色 secondaryContainer 往卡底混 10%（与日期签同算式，但更深一档），
//     整行可点右侧「切换书」，只切日志查看范围、不动角色管理的当前书；
//   · 章节签：底色再淡一档（bookfill 0.06），左侧章节序号小牌 + 章名 + 条数，
//     整行点击跳到本章首条。
// 两者的左缘都落 16dp 内容线（与裸行/日期签文字/卡内正文同线），不各自另起一列。
@Composable
private fun BookBarRow(
    book: String,
    count: Int,
    onSwitch: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                lerp(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    0.10f
                )
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "《$book》",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (count > 0) "$count 条" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        if (onSwitch != null) {
            Text(
                text = "切换书",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(AppShapes.sm)
                    .clickable(onClick = onSwitch)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ChapterBarRow(
    book: String,
    chapter: String,
    count: Int,
    onSwitch: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                lerp(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    0.55f
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 章节序号小牌：只从章名里取「第N章」这一段，取不到就整串当章名
        val idx = Regex("^第\\s*[0-9一二三四五六七八九十百零]+\\s*章").find(chapter)?.value
        if (idx != null) {
            Text(
                text = idx,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(AppShapes.xs)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            // 已带序号就不再重复一遍序号前缀
            text = idx?.let { chapter.removePrefix(it).trim() }?.ifEmpty { chapter } ?: chapter,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (count > 0) "$count 条" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 右侧「切换章」（11-11 用户令）：与书条「切换书」同款胶囊、同位置、同字号——
        // 两者左右对齐才读得出「这是同级入口」。始终显示（只有一章也显示）。
        if (onSwitch != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "切换章",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(AppShapes.sm)
                    // 点击独立于整行：点键=换章，点行=跳本章首条，两个动作不互相吃掉
                    .clickable(onClick = onSwitch)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
