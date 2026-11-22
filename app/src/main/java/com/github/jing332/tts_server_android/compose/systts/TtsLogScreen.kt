package com.github.jing332.tts_server_android.compose.systts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import com.github.jing332.tts_server_android.compose.nav.NavTopAppBar
import com.github.jing332.tts_server_android.compose.theme.softStructureColor
import com.github.jing332.tts_server_android.compose.theme.SegNumberFont
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.github.jing332.tts_server_android.compose.theme.AppShapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.CompositionLocalProvider
import com.github.jing332.common.LogEntry
import com.github.jing332.common.LogLevel
import com.github.jing332.common.utils.toast
import com.github.jing332.tts_server_android.R
import com.github.jing332.compose.widgets.DenseTextField
import com.github.jing332.tts_server_android.compose.systts.role.FlatTextAction
import com.github.jing332.tts_server_android.compose.systts.role.softFillBarColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.withFrameNanos
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun TtsLogScreen(vm: TtsLogViewModel = viewModel()) {
    val context = LocalContext.current
    // 剪贴板：多选「复制(N)」用（1002）
    val clipboard = LocalClipboardManager.current
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    // ===== 11-16 晚：滚动三态（用户定稿状态机，见 LogScreen scrollMode 注释）=====
    var scrollMode by rememberSaveable { mutableStateOf(ScrollMode.FOLLOW_PLAYING) }
    // 日志文件列表弹窗（用户 09-08：文件夹点开自由选择文件，不再直接扔给外部查看器）
    var showLogFilesDialog by remember { mutableStateOf(false) }
    // 11-15 晚：书/章框小红叉的待确认目标（null=无弹窗）。确认后才真删——红叉紧挨着
    // 书名/章名，误触代价是"这本书的日志没了"，必须过一次确认。
    var pendingClear by remember { mutableStateOf<ClearScope?>(null) }
    // 待删文件（11-11 用户令「每一项可以单独删除」）：
    // 不弹二次确认框（弹窗套弹窗太吵），改用**点两下**——
    // 第一次点把该行标红并提示「再点一次删除」，2 秒内不点就自动复原。
    // 记的是文件名而不是 File：弹窗关闭重开后 File 实例会变，按名字比更稳。
    var pendingDeleteName by remember { mutableStateOf<String?>(null) }
    // 长按顶栏图标触发的「全部清空」确认框（11-11 用户令）：
    // 长按是快捷路径，但清空不可撤销，所以先用一个确认框挡住，确认后才真清。
    var showClearAllConfirm by remember { mutableStateOf(false) }
    // 应用内预览（11-11 用户令「这些日志文件能点开查看吗」→ 选应用内预览）：
    // null = 没在看；非 null = 正在看这个文件。
    var previewFile by remember { mutableStateOf<File?>(null) }
    var previewLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewLoading by remember { mutableStateOf(false) }
    // 预览已加载的行数（11-11 用户要「加载更多」）：每次 +300，直到顶到文件能读的范围
    var previewWantLines by remember { mutableStateOf(PREVIEW_PAGE) }
    // 是否还有更多可加载：读取结果少于请求数就说明已经到文件开头了
    var previewHasMore by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // 视野书章（11-11）：吸顶区下方那行小字，跟着列表滚动变化。
    // 「全部日志我划到哪就显示哪本书的信息」——由 LogScreen 通过 onViewChanged 回传。
    //
    // ⚠️ 吸顶区渲染在列表**之前**，首帧回调还没到（为空），会闪一下空白。
    // 故初值取「全量日志最后一条带书章的条目」，与"当前正在读"一致；
    // 之后只要用户滚动，回调就会把它覆盖成真正的视野值。
    // 「切过去」触发计数（11-11）：点一次 +1，传给 LogScreen 强制重启自动滚底。
    var jumpToLiveTick by remember { mutableIntStateOf(0) }
    // 11-15：「是否停在列表最底部」。原「最新日志」胶囊删掉后，吸顶书章的
    // 「跟随最新 vs 跟随视线」映射需要一个替代判据（见 PinnedLogHeader 里
    // shownBook 那段注释）。初值 true：进页面默认就在底部，顶部显示最新那条书章，
    // 与旧行为（autoFollow 默认 false 但 book 兜底取最新）观感一致。
    var atListBottom by remember { mutableStateOf(true) }
    // 视野书章（吸顶区「你看的是哪本哪章」）：**可写状态**——LogScreen 随滚动回传覆盖
    //（onViewChanged，见下方参数处），初值取「全量日志最后一条带书章的条目」兜底首帧。
    // 11-21 切栏卡顿修（②）：原初值计算挂在 remember(vm.logs) 上——key 是 2 万条的
    // SnapshotStateList，每条日志到达整表失效、data class 逐条 equals。初值只在
    // 「logs 从空变非空」时需要更新，改 derivedStateOf + size 门槛：只在列表首次
    // 有内容（或清空后重新有内容）时从尾部扫一次，平时读缓存零成本。
    var viewBookChapter by remember {
        mutableStateOf("" to "")
    }
    LaunchedEffect(vm.logs.size) {
        if (vm.logs.isNotEmpty() && viewBookChapter.first.isEmpty()) {
            val e = vm.logs.asReversed().firstOrNull { it.bookName.isNotBlank() }
            if (e != null) viewBookChapter = e.bookName to e.chapterTitle
        }
    }
    // 正在朗读的书章（只读）：「筛着 A 书却在读 B」跳转提示条用（吸顶书章 20:2x
    // 终裁永远跟视线，不再消费本字段）。
    // 来源=播放快照 ReadProgressReader.read()（playing=true 且 15s 内有心跳）。
    // 20:0x 曾短暂用于吸顶「停底部=朗读章」，同日终裁撤回；保留快照来源——
    // 日志尾含预加载笔，会被备货探头带成下一章（20:08 实拍），快照才是
    // 「正在朗读」的可靠口径。停读时快照 null → 空串，提示条自然隐藏。
    val readingBookChapter by remember {
        derivedStateOf {
            val snap = com.github.jing332.tts_server_android.service.systts.help.ReadProgressReader.read()
            (snap?.book.orEmpty()) to (snap?.chapterTitle.orEmpty())
        }
    }
    val readingBook = readingBookChapter.first
    val readingChapter = readingBookChapter.second
    // 书籍切换弹窗（11-10 方案 A）：点书籍条右侧「切换书」打开，只改日志查看范围。
    // 非空=弹窗打开，值为点进来的那本书（用于高亮当前书）
    var bookPickerFor by remember { mutableStateOf<String?>(null) }
    // 章节切换弹窗（11-11）：点章条右侧「切换章」打开。值为 (书, 当前章)，
    // 用于高亮当前章——用户 11-11 指出「切换书有键、切换章没有」，两边要对称
    var chapterPickerFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    // 「只看匹配」退役（10-08 log-ui-1008 ②拍板）：搜索即筛选——输入立刻只剩
    // 匹配条目，不再两段式开关绕弯；跳转目标直接在过滤后的列表里
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 11-18 三改（用户实拍：搜「喜欢」出 81 处，正文命中只有 2 条，全被章名噪音淹了）：
    // 命中拆成两种口径：
    //   matchesBody  = 可搜文本（已剔角色名）——计数主数、标记底色都用它
    //   matchesQuery = 正文/时间（列表过滤用它；时间另算，不虚增正文数）
    //   ⚠️ 11-19 起书章名**完全不参与搜索**：曾经改由「书章命中」栏单独呈现，
    //      但实测那栏价值低（搜正文时不出现、搜书名时点它反而把列表从 3 条扩到 401 条），
    //      已按用户令整栏删除。搜书名/章名现在只靠正文是否含该词命中。
    //      ⚠️ 别再把它加回 matchesQuery：那正是「整本书的行灌满列表」的老 bug。
    // 两种都同时比对「原文」与「剥掉 HTML 标签后的文本」：原文形如
    // `请求音频：<b>正文</b><br><font …>`，跨标签边界的词在原文里搜不到但上屏看得见
    //（「明明有却搜不到」就是这么来的）。
    fun LogEntry.plainMessage(): String = message.replace(Regex("<[^>]*>"), "")

    /**
     * 11-19（用户定稿「只把角色名排除吧」）：**只剔角色名，其余全部保留可搜**。
     *
     * 演进（别走回头路）：
     *   ① 最初整条 message 都搜 → 搜「太太」命中角色名「丁太太」11 条（用户实拍报障）；
     *   ② 我一度收窄成「只搜 <b>正文</b>」→ 用户否掉：「日期、底部插件名、声音名也有价值」，
     *      查「某个插件昨晚是不是老失败」「某个音色在哪几段」全都搜不到了，等于功能退化；
     *   ③ **终稿（本条）**：只剔除**角色名**这一段，其余（正文 / 声音名 / 语速音量 /
     *      插件名 / 时间 / 规则日志 / 子行）全部照常可搜。
     *
     * 为什么能精确定位角色名：服务端拼这条消息时给角色名单独套了
     * `<font color="#0F0F0F">角色名</font>`（ROLE_CHIP_COLOR，专属哨兵色，别处不用），
     * 所以按颜色精确剔除即可，不靠猜文字位置。
     */
    fun LogEntry.searchableText(): String {
        // 先剥 HTML 标签，再按行剥掉「角色名」那段——剥离在剥标签之前做不了，
        // 因为要认的是 <font color="#0F0F0F">…</font> 这层包裹。
        return message
            // 剔角色名：连带吃掉它后面紧跟的那个分隔逗号（否则搜「太太」仍会因
            // 「丁太太，」这半截的逗号残留而边界不清——不影响命中，但保持文本干净）
            .replace(Regex("<font color=\"#0F0F0F\">.*?</font>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]*>"), "")
    }

    // 主命中=可搜文本（已剔角色名）。时间也算——搜「18:47」时用户就是想找那一刻。
    fun LogEntry.matchesBody(q: String): Boolean =
        searchableText().contains(q, ignoreCase = true)
    // ===== 11-19（用户实拍）：书章名命中不再参与「按行列结果」 =====
    //
    // 症状：搜「呼啸」→「共 482 处 · 正文 10 处」，列表里 472 条全是**这本书的每一行**；
    // 搜「第3章」→「共 25 处 · 正文 4 处」，同样被 21 条「属于这一章」的行灌满。
    // 旧判据把 `bookName.contains(q) || chapterTitle.contains(q)` 也算命中，于是
    // 只要搜的是书/章名，整本书/整章的行就都被捞进列表——用户原话：
    // 「搜索的时候没有把书名和章节名单独列出来，而是显示了一堆无关紧要的」。
    //
    // 现行口径（11-20 定稿）：**列表只列正文/时间命中**（matchesQuery = matchesBody + 时间）。
    // 书/章名**完全不参与搜索**——曾经的「书章命中」栏已按用户令删除（11-20），
    // 因为它搜正文时不出现、搜书名时点它反而把列表从 3 条扩到 401 条（扩大而非收窄）。
    // 想按章找日志，走吸顶的书/章选择器（那里本来就能切）。
    fun LogEntry.matchesQuery(q: String): Boolean =
        matchesBody(q) || time.contains(q, ignoreCase = true)

    // 基础列表 = 等级/插件筛选结果 → 书/章收窄（11-10）→ 搜索词实时过滤。
    // 书/章收窄放在搜索之前：搜索是在「选定范围内」找词，符合直觉
    val displayLogs by remember(vm) {
        derivedStateOf {
            val q = searchQuery.trim()
            val base = vm.filteredLogs
                .let { list ->
                    // 「只看本书 / 只看本章」：由 VM 持有，供筛选弹窗与本处共用
                    val b = vm.scopeBook.value
                    val c = vm.scopeChapter.value
                    when {
                        b.isNullOrBlank() && c.isNullOrBlank() -> list
                        b.isNullOrBlank() -> list.filter { it.chapterTitle == c }
                        c.isNullOrBlank() -> list.filter { it.bookName == b }
                        else -> list.filter { it.bookName == b && it.chapterTitle == c }
                    }
                }
            if (q.isNotEmpty()) base.filter { it.matchesQuery(q) } else base
        }
    }

    // 匹配统计(控制行展示用)
    val matchCount by remember(vm) {
        derivedStateOf {
            val q = searchQuery.trim()
            if (q.isEmpty()) 0
            else displayLogs.count { it.matchesQuery(q) }
        }
    }

    // 正文命中数：控制行主数就用它（不含时间命中）。
    // 搜到 0 处 + 列表空 = 这次搜索确实没命中（书章名已不参与搜索，不再有别处兜着显示）。
    val bodyMatchCount by remember(vm) {
        derivedStateOf {
            val q = searchQuery.trim()
            if (q.isEmpty()) 0
            else displayLogs.count { it.matchesBody(q) }
        }
    }

    // 「当前在看哪本书」：日志里最近一条带书名的条目所属书。
    // 筛选弹窗「只看本书」用它取默认值——比让人从下拉里再挑一次少一步
    fun currentBookInView(): String? =
        vm.logs.lastOrNull { it.bookName.isNotBlank() }?.bookName

    // 吸顶区回落值（11-11 修「切换章节时有时无」）：
    // 取**全量日志**（不受按书/按章过滤影响）里最近一条带书章的条目。
    // displayLogs 是过滤后的视图，选单本书/切书时主行可能被滤掉 ⇒ 吸顶区会空；
    // 用全量日志兜底，保证只要读过这本书，顶部始终显示当前书章。
    val pinnedFallback = remember(vm.logs) {
        vm.logs.lastOrNull { it.bookName.isNotBlank() }
    }

    // ===== 11-14「正在朗读」播放快照 + 合成分子 =====
    //
    // playingSnap：轮询 readProgress.json（U阅 段切换时写）。快照带 mtime/size 缓存，
    // 文件没变时直接命中缓存不读盘 —— 但**必须放在 IO 线程**，理由见下方 11-15 晚修：
    // 那个文件在 /storage/emulated/0（外部存储，走 FUSE/MediaProvider），
    // 一次 File.exists() 平时是微秒级，碰上存储侧卡顿却能阻塞几秒到几分钟。
    // 早期写在 produceState 里（默认跑在主线程）→ 主线程被 stat 卡死，
    // MtWatchdog 记到「主线程卡死 626684ms」，整台朗读链跟着停摆（见 stall.log）。
    //
    val playingSnap by produceState<com.github.jing332.tts_server_android.service.systts.help.ReadProgressReader.Snapshot?>(
        initialValue = null,
        producer = {
            while (true) {
                // 11-15 晚修（stall.log 实锤）：外部存储的 stat 绝不留在主线程。
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.github.jing332.tts_server_android.service.systts.help.ReadProgressReader.read()
                }
                // 11-16 晚：1s 死等 → 事件驱动。FileObserver 盯 readProgress.json，
                // U阅 段切换一写就立刻重读（「正在朗读」高亮/跟随不再迟 1~2s）；
                // 1s 超时兜底，个别机型 FUSE 不派事件时轮询功能不失效。
                kotlinx.coroutines.withTimeoutOrNull(1000) {
                    com.github.jing332.tts_server_android.service.systts.help.ReadProgressReader.changes.first()
                }
            }
        }
    )
    // ===== 11-14 合成侧三值（进度行数据源）=====
    // 数据来自快照的合成前沿字段（U阅 合成循环每笔提交时刷新，见 ReadProgressFile.
    // reportSynthFront）；「多合几段」= 前沿段号 - 播放段号，仅同章时可减（预取跨章时
    // 前沿在下一章，"多合几段"跨章无意义，此时只报章名与正在合第几段）。
    // 11-15：进度行单独一条「原样快照」通道——用户令「停止朗读时合成进度行照样显示」，
    // 而 playingSnap 在 playing=false 时恒为 null（见 ReadProgressReader.read），
    // 拿它渲染进度行会导致整行（含合成段）随停止朗读一起消失。
    // 高亮/跟随仍用 playingSnap，两者互不影响。
    val progressSnap by produceState<com.github.jing332.tts_server_android.service.systts.help.ReadProgressReader.Snapshot?>(
        initialValue = null,
        producer = {
            while (true) {
                // 同 playingSnap：外部存储 stat 一律走 IO 线程（11-15 晚修）。
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.github.jing332.tts_server_android.service.systts.help.ReadProgressReader.readRaw()
                }
                // 同上：事件驱动 + 1s 兜底（合成前沿/进度行也即时刷新）
                kotlinx.coroutines.withTimeoutOrNull(1000) {
                    com.github.jing332.tts_server_android.service.systts.help.ReadProgressReader.changes.first()
                }
            }
        }
    )
    // 11-22 02:2x 用户令「还是别要这个底部的悬浮窗了」：底部固定缓存提示整撤
    //（`bottomCacheParts` 与 LogScreen 的四个 bottom* 参数一并删除）。
    // 读数仍在吸顶进度行，不另算一份。
    val synthesizedChapter = playingSnap?.synthChapterTitle.orEmpty()
    val synthesizedAhead = playingSnap?.let { snap ->
        if (snap.synthChapterIndex == snap.chapterIndex &&
            snap.synthSegIndex >= snap.segIndex
        ) snap.synthSegIndex - snap.segIndex else -1
    } ?: -1
    val synthesizedSegIdx = playingSnap?.synthSegIndex ?: -1

    // 归组结果（10-06 全链卡）：与 LogScreen 共用同一实例——搜索跳转要把条目下标
    // 换算成列表项下标（一张卡=一个列表项），必须用同一份归组才不会错位
    // 11-15：列表是否被「收窄」（决定要不要给无结局的卡标「请求中」）。
    // 必须用**全部**收窄来源：搜索词、级别筛选、只看本书/本章——
    // 少算一项就会把「结果行被筛掉的正常卡」误标成请求中。
    // （11-15：selectedLevels 现在只由「只看错误」置位，分级筛选条已撤。）
    val logNarrowed by remember(vm) {
        derivedStateOf {
            searchQuery.trim().isNotEmpty() ||
                vm.selectedLevels.isNotEmpty() ||
                !vm.scopeBook.value.isNullOrBlank() ||
                !vm.scopeChapter.value.isNullOrBlank()
        }
    }
    val logGroups = remember(displayLogs, pinnedFallback, logNarrowed) {
        LogGroups.build(
            displayLogs,
            fallbackBook = pinnedFallback?.bookName.orEmpty(),
            fallbackChapter = pinnedFallback?.chapterTitle.orEmpty(),
            markPending = !logNarrowed,
            // 11-21 14:2x（用户拍板「卡片按正文的顺序出现」）：日志流按
            // 「书→章→段」排序，预合成卡插到正文的正确位置，不再锯齿交错。
            // 排序键随 displayLogs 变化在 build 内重算，这里只取一次章序数据。
            chapterOrder = vm.chapterOrderMap(),
        )
    }

    // ——「原文」定位（用户 10-08 午后拍板 甲方案）——
    // 筛选/搜索态下列表是收窄视图；点卡上的「⟲ 原文」= 清筛选回到完整流，
    // 跳到该条目所在卡并高亮。完整流归组单独建（与 displayLogs 归组不同实例）。
    val isFiltered by remember(vm) {
        derivedStateOf {
            // 只认真正"收窄"视图的两项：搜索词、「只看错误」（10-10 用户令）。
            // （11-15 分级筛选条整条撤除后，selectedLevels 已只剩"只看错误"一种非空取值）
            // 「插件」「朗读规则」两个调试开关是**追加型**——勾上往列表里加日志，
            // 一点都不收窄，与"回完整流找原位置"语义相反；此前把它们也算进 isFiltered，
            // 导致只要勾了调试开关每张卡右上角都冒原文键（用户实机反馈）。
            searchQuery.trim().isNotEmpty() || vm.selectedLevels.isNotEmpty()
        }
    }
    // 11-21 切栏卡顿修（①）：fullGroups 整段删除——死代码。
    // 「回原位」11-16 起改在 locateOriginal 内**就地重建**全量组（见下方 freshGroups，
    // 不再用闭包捕获的组）；此后 fullGroups 没有任何消费者。但它挂在 remember(vm.filteredLogs)
    // 上：filteredLogs 是 getter（每次访问都 filter/merge 新建列表），每条新日志到达都
    // 触发一次「全量过滤 + LogGroups.build + 2 万条 data class 逐条 equals」——纯白烧，
    // 切回日志栏那一帧把朗读期间积压的失效一次性补算，正是卡顿来源之一。
    // 删除零功能损失；历史根因与修法见 locateOriginal 内 11-16 注释。
    var locateHighlight by remember { mutableStateOf<LogEntry?>(null) }

    fun locateOriginal(entry: LogEntry) {
        // 1) 清**收窄类**筛选（搜索词/级别勾选）——与 isFiltered 的判据保持一致。
        // 11-18 二改（用户令）：按条目来源**自动勾上对应混入开关**——在错误视图里点
        // 某条规则错误的「回原位」，上下文必须是全量规则日志（同组 INFO 找得回）；
        // 只清级别筛选而混入没勾的话，规则缓冲根本不参与列表，那条 ERROR 都不在。
        // 插件日志同理。两个开关只开不关（用户手动关掉的开关不替他重开之外的逻辑：
        // 这里是「回原位要看上下文」，开了才看得到，符合语义）。
        searchQuery = ""
        vm.clearFilter()
        if (entry.isSpeechRuleLog && !vm.showSpeechRuleLogs.value) vm.showSpeechRuleLogs.value = true
        if (entry.isPluginLog && !vm.showPluginLogs.value) vm.showPluginLogs.value = true
        // 2) 在完整流里找到该条目的卡头，滚动过去并高亮闪现
        val fullList = vm.filteredLogs
        val idx = fullList.indexOfFirst { it == entry }
        if (idx < 0) {
            // 11-16：静默 return 改为有提示——用户点「回原位」没反应会当成 bug 报障
            //（真机复现：筛选错误日志态点回原位，页面纹丝不动）
            context.toast("该条目已不在当前日志流中")
            return
        }
        locateHighlight = entry
        scope.launch {
            // ===== 11-16：不用闭包捕获的 fullGroups，就地重建全量组 =====
            //
            // 根因（真机实锤「筛选错误日志后点回原位，定不到位置」）：
            // fullGroups = remember(vm.filteredLogs) —— 在**筛选/搜索态**下它基于
            // 收窄列表建组；而上面 fullList 是清筛选后的**全量**索引。两套索引
            // 空间错位：listItemFor(idx) 把全量第 N 行映射到收窄组的第 N 个渲染项
            // ⇒ animateScrollToItem 滚到错误位置。等一帧也救不了——lambda 捕获的
            // 是旧引用，重组后的新组它读不到。
            //
            // 修法：这里用 fullList（全量）就地建组，索引空间与 idx 天然一致，
            // 不依赖重组时序、不依赖 remember 的 key 比较。
            val freshGroups = LogGroups.build(
                fullList,
                fallbackBook = pinnedFallback?.bookName.orEmpty(),
                fallbackChapter = pinnedFallback?.chapterTitle.orEmpty(),
            )
            // 状态更新后重组出完整列表再滚：等一帧让 displayLogs 生效
            withFrameNanos { }
            val target = freshGroups.listItemFor(idx)
            listState.animateScrollToItem(target)
            // 11-18 校正（用户实测「回原位只到附近，前后翻翻才找到」）：LazyColumn 对
            // 远距离目标的落点是**按估算偏移**滚的——日志行高差好几倍（一行正文 vs 一张卡），
            // 隔几千行滚过去能偏好几行，且高亮只有 2 秒，偏了就等于没定位到。
            // 修法：首滚后等两帧让目标邻域完成实际测量，再查一次可视区；
            // 不在就用 scrollToItem 补滚——第二次从邻域出发、偏移已实测，落点精确；
            // 补滚用 snap 不用动画，避免二段动画漂移。
            repeat(2) {
                withFrameNanos { }
                if (listState.layoutInfo.visibleItemsInfo.none { it.index == target }) {
                    listState.scrollToItem(target)
                }
            }
            // 高亮停留约 2s 后撤掉（LogEntryBody 按引用比对）
            kotlinx.coroutines.delay(2000)
            if (locateHighlight == entry) locateHighlight = null
        }
    }

    // 11-14：跳到某章首条（从章节弹窗抽出，供章下拉菜单直接调用——
    // 用户嫌弹窗离触发点远且复杂，点章名 ▾ 的菜单选完直接滚，不再二次弹窗）。
    // 只滚动不改筛选（原弹窗口径保留）。
    fun jumpToChapter(book: String, chapter: String) {
        val idx = displayLogs.indexOfFirst {
            it.bookName == book && it.chapterTitle == chapter
        }
        if (idx >= 0) {
            val item = logGroups.listItemFor(idx)
            scope.launch {
                runCatching { listState.animateScrollToItem(item) }
            }
        } else {
            context.toast("该章还没有日志")
        }
    }

    // 搜索词变化 → 跳到最近一条匹配(高亮由 LogScreen 渲染)；列表本身已实时过滤，
    // 跳转目标按归组映射换算：命中卡内任一行即落到整张卡
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.isNotEmpty()) {
            // 11-18：优先跳「正文命中」的最后一处；没有正文命中才退回全部命中
            val idx = displayLogs.indexOfLast { it.matchesBody(q) }
                .let { if (it >= 0) it else displayLogs.indexOfLast { it.matchesQuery(q) } }
            if (idx >= 0) listState.animateScrollToItem(logGroups.listItemFor(idx))
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // ———— 页面级多选（1003 终态：长按条目即进，1002 的顶栏 ☑ 入口已删）：跨条勾选 → 底栏「复制(N)」————
    // 跨条拖选是 compose 1.7 selection 的崩溃源（滚动回收条目后选区悬挂 selectableId），
    // 跨条复制一律走本模式；勾选以条目对象为键，筛选/重排不错位。退多选=返回键/复制后自动退。
    // 两个状态都用 remember（非 saveable）：Set<LogEntry> 不落 Bundle，旋转屏一并重置、
    // 保持「模式开着但勾选丢了」的不一致不会出现
    var selectionMode by remember { mutableStateOf(false) }
    var checkedEntries by remember { mutableStateOf<Set<LogEntry>>(emptySet()) }
    fun exitSelection() {
        selectionMode = false
        checkedEntries = emptySet()
    }
    fun toggleCheckAll() {
        val all = displayLogs.toSet()
        checkedEntries = if (checkedEntries.containsAll(all)) emptySet() else all
    }
    // 返回键先退多选（页面级；钥匙=与密钥页同一交互口径）
    androidx.activity.compose.BackHandler(enabled = selectionMode) { exitSelection() }
    // ===== 11-20 六改 / 七改：复制口径从「条目」改成「卡片」，并带上卡头信息 =====
    // 用户实报：「卡片是合二为一的，为啥复制之后变两条了？不能做成一条吗？
    // 等于是卡片变成我想要的样子，但底层还是以前吗？」——**是**：卡片只是渲染层
    // 的合并（LogGroups），勾选/复制一直按**条目**走。勾一张卡 = 组头 + 成功/重试/
    // 取消等成员行，复制出去就成两条（过程行还额外占段）。
    // 现在按卡片（=列表项）口径输出：一张卡一段文本，顺序沿用列表顺序。
    // 七改（用户追问「成功卡只复制前半段，耗时大小也不体现了」）：卡头信息不能丢——
    // 由 LogScreen.cardCopyText 统一派生「状态＋耗时大小」，与卡上第一行同口径：
    //   18:46:11 | 成功 3.0s，62.2 KB | 请求音频：正文⏎旁白，夏舒，……（本悦读）
    // 裸行没有卡头，保持原来的「时间 | 级别字 | 正文」。
    fun checkedCopyBlocks(): List<String> {
        val g = logGroups
        val out = ArrayList<String>()
        g.items.forEach { item ->
            val idxs = g.entriesOf(item)
            val hit = idxs.any { i ->
                g.entries.getOrNull(i)?.let { it in checkedEntries } == true
            }
            if (!hit) return@forEach
            when (item) {
                is LogGroups.Item.Group -> out.add(cardCopyText(g, item))
                else -> {
                    val e = idxs.firstOrNull()?.let { g.entries.getOrNull(it) }
                        ?: return@forEach
                    // 折叠次数写进文本（10-12 用户令）：×N 原来只体现在丸上，复制出去就丢了次数
                    val times = if (e.repeatCount > 1) "（×${e.repeatCount}）" else ""
                    out.add("${e.time} | ${e.getLevelChar()} | ${e.message.toPlainText()}$times")
                }
            }
        }
        return out
    }
    // 底栏「复制(N)」的 N 也按**卡片数**算（一张卡=一条，与复制结果条数一致）；
    // 否则勾一张合并卡会显示「复制(2)」而实际只输出一段。
    val checkedCardCount = remember(checkedEntries, logGroups) {
        val g = logGroups
        g.items.count { item ->
            g.entriesOf(item).any { i ->
                g.entries.getOrNull(i)?.let { it in checkedEntries } == true
            }
        }
    }
    // 复制：一张卡一段，段与段之间空行分隔
    // ⚠️ 富文本必须**转纯文本**再复制（10-12 用户实报）：message 是渲染用的富文本
    //（<font color="#00FFFF"> 哨兵色 / <b> / <br>），直接复制出去粘到别处全是标签，
    // 既不好读也没法直接发给别人。转换口径见 toPlainText。
    fun copyChecked() {
        if (checkedEntries.isEmpty()) {
            context.toast(R.string.log_select_none)
            return
        }
        val text = checkedCopyBlocks().joinToString(separator = "\n\n")
        clipboard.setText(AnnotatedString(text))
        exitSelection()
        context.toast(R.string.copied)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // 10-10 深夜白条真根因：本页躺在 MainPager 的 pager 里，外层已 pad bottom=bottomPad
        // （含底栏+手势条）；内层 Scaffold 默认 contentWindowInsets=systemBars 会把手势条
        // **再算进** paddingValues.calculateBottomPadding()，LogScreen 又按它垫一次
        // → 手势条高度双重避让=列表末尾与底栏之间的白条（装机截图复现，8dp Spacer 修复没治到根）。
        // 内容避让全由外层负责，本 Scaffold 的 insets 归零；多选底栏自行贴合即可
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // 多选底栏（照密钥页）：全选 |（右）复制(N)。放 Scaffold bottomBar：列表自动让位。
            // 不加 navigationBarsPadding——本页在 MainPager 的 pager 里，外层已让出底栏+手势条高度
            if (selectionMode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FlatTextAction(
                            stringResource(R.string.select_all),
                            MaterialTheme.colorScheme.onSurfaceVariant
                        ) { toggleCheckAll() }
                        Spacer(Modifier.weight(1f))
                        // 「复制(N)」改填充主色胶囊（10-04 用户：原灰/主色文字键不突出）。
                        // 全选=次要文字键、复制=主操作填充键，主次一眼可分（填色=动作的全局语汇）
                        Button(
                            onClick = { copyChecked() },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(percent = 50)
                        ) {
                            Text(
                                stringResource(R.string.log_copy_n, checkedCardCount),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        },
        topBar = {
            Column {
                // M3 TopAppBar → 全站自绘 NavTopAppBar（56dp、动作键热区贴边）：顶栏 ⋮ 与列表行 ⋮ 同列
                NavTopAppBar(
                    title = {
                        AnimatedContent(
                            targetState = isSearchActive,
                            transitionSpec = {
                                fadeIn() + slideInHorizontally { it } togetherWith
                                        fadeOut() + slideOutHorizontally { it }
                            },
                            label = "TitleAnimation"
                        ) { isSearch ->
                            if (!isSearch) {
                                // 双击标题栏空白区=回日志顶部（10-04 用户定：替代原浮动 ↑ 键）。
                                // Box 撑满标题槽（NavTopAppBar 里是 weight(1f) 的 Box）捕获整个标题区。
                                // 左对齐（10-05 用户：标题居中是四页独一份，「都跑中间了」——
                                // 撤掉居中，走 NavTopAppBar 默认 CenterStart，与主页/密钥页同构）
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(Unit) {
                                            detectTapGestures(onDoubleTap = {
                                                scope.launch {
                                                    runCatching { listState.scrollToItem(0) }
                                                }
                                            })
                                        },                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(text = stringResource(id = R.string.log))
                                }
                            } else {
                                // 搜索框（10-13 深夜用户令「参照主页把高度显示全」）：
                                // 原走 DockedSearchBar 壳（10-10「沉浸式样式」遗留），但它永远
                                // expanded=false、不需要下拉建议，壳只贡献一圈内边距——内外都压 48dp
                                // 时内层胶囊被挤成约 40dp，装机看着比主页矮一截（用户实报）。
                                // 改主页同款：Surface(48dp 胶囊) + DenseTextField(透明底)，
                                // 无壳、高度实打实 48dp；文字/占位符/清除键口径不变。
                                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .clip(CircleShape),
                                        color = softStructureColor(),
                                        shape = CircleShape
                                    ) {
                                        DenseTextField(
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            singleLine = true,
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent
                                            ),
                                            placeholder = {
                                                Text(stringResource(R.string.search_logs), maxLines = 1)
                                            },
                                            trailingIcon = {
                                                if (searchQuery.isNotEmpty()) {
                                                    IconButton(onClick = { searchQuery = "" }) {
                                                        Icon(Icons.Default.Clear, stringResource(R.string.clear))
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        // 搜索按钮
                        IconButton(onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        }) {
                            Icon(
                                if (isSearchActive) Icons.AutoMirrored.Default.ArrowBack else Icons.Default.Search,
                                if (isSearchActive) stringResource(R.string.nav_back) else stringResource(R.string.search)
                            )
                        }

                        // 漏斗/文件夹/清空三键（10-10 恢复 3779dba 甲案：四键常驻——
                        // 搜索态不再隐藏，级别筛选/开日志目录/清空全程可达。
                        // dda939c 教训：撤 if 门控时条件与大括号必须一起动，勿留孤儿 lambda）
                        // 11-11 用户令：漏斗键删除——筛选开关已全部挪进吸顶区
                        //（实时/错误/朗读规则/插件），弹窗不再需要。

                        // ===== 日志管理入口（11-11 用户令：图标要带名称 + 右侧单独一个删除键）=====
                        //
                        // ## 为什么改名 + 加文字
                        // 用户原话：「图标加个名称，要不要加个什么日志管理什么之类的？」
                        // 光一个文档图标，不点开根本不知道是日志文件列表；加三个字成本很低。
                        // 「日志管理」而不是「日志文件」：它管的是**看 + 删**，不只是"文件列表"。
                        //
                        // ## 为什么把垃圾桶加回来
                        // 上一版把「全部清空」藏进了长按（用户令）。用户实报：
                        // 「我觉得长按全清空大家不会知道」——他说得对，长按是**不可发现**的交互，
                        // 没有视觉线索、也没有人能猜到。功能被藏起来等于不存在（同一个坑在
                        // 11-11 的"横滑才有 DEBUG"上已经踩过一次）。
                        // 所以：清空回到顶栏右侧的常驻图标，与「日志管理」并排、各自可发现。
                        // 长按这一层撤掉——同一个动作不再留两个隐藏/显式入口。
                        //
                        // ## 两个按钮各管什么（一眼分得清）
                        //   📄 日志管理 → 文件列表：点行看内容、行尾删单个
                        //   🗑         → 全部清空（带确认框，不可逆动作不能一键就没）
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 图标 + 文字做成一个点击单元（只点文字也算点中，热区连成一片）
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { showLogFilesDialog = true }
                                    .padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 11-13：去掉 onSurfaceVariant 灰——同排顶栏动作键
                                //（搜索/文件夹 IconButton）都是默认 onSurface，
                                // 一个入口键不该比邻居浅一档（用户比对截图指出）。
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "日志管理",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                )
                            }
                            IconButton(onClick = { showClearAllConfirm = true }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "全部清空日志",
                                    // 11-13 用户令：「这个删除图标感觉不太对，还是改为黑色默认垃圾桶」。
                                    // 顶栏这一枚改回默认 onSurface（中性色）；危险级别由点按后的
                                    // 确认弹窗承担，不再靠红色预警。其余入口（GroupItem 删除、
                                    // 日志管理行尾删单个）维持原 error 色，用户未要求改动。
                                )
                            }
                        }
                    }
                )

                // 11-13 用户令「上面下面的分隔线都有必要留吗」：删掉标题/搜索行与筛选区之间这条。
                // 筛选区属于同一头部区块，兄弟之间不该有分割线；真正需要的「头部↔滚动列表」
                // 分隔线在筛选区之后（见下方 if (vm.logs.isNotEmpty()) 的那条）。

                // 搜索控制行：匹配数 + 上一处/下一处跳转。
                // ⚠️ 漏斗键本行撤（10-10）：恢复「四键常驻」后顶栏已有级别筛选键，
                // 搜索时本行再出一个是重复入口；原「漏斗住进本行」的前提
                //（顶栏三键搜索态隐藏）已被四键常驻推翻，见顶栏 actions 注释。
                AnimatedVisibility(
                    visible = isSearchActive && searchQuery.isNotBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // ===== 11-19 用户令：撤掉「上一处/下一处」箭头 =====
                        //
                        // 用户原话：「这个搜索顶部的上一个，下一个是不是没什么用啊？
                        // 他每次都出来一大片，我点击上一个下一个没什么作用吧？」
                        // 核实结论：**箭头没坏，是它在这个形态下必然多余**——
                        // 搜索一开，displayLogs 已被 matchesQuery 过滤成「只剩命中行」，
                        // 相邻两条命中之间没有无关行可跳，点一下只是从第 1 条挪到第 2 条，
                        // 手指一滑就做到。若要箭头有意义，得改成「保留全部行 + 高亮命中」
                        // 的定位式列表；用户选择维持过滤式，故箭头连同 jumpToMatch 一并删除。
                        //
                        // 计数文案保留（用户要靠它判断「到底有没有搜到」）：
                        // 列表已只列命中行，故主数报正文；时间命中另计。
                        Text(
                            text = buildString {
                                append("正文 ${bodyMatchCount} 处")
                                if (matchCount != bodyMatchCount) {
                                    append(" · 共 ${matchCount} 处(含时间)")
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                        )
                    }
                }

                // 11-15 用户令「要撤呀，本来就没有做过级别筛选这个」——整条分级筛选条删除。
                // 撤除依据（读码确认是死 UI，不是「暂时用不到」）：
                //   `vm.toggleLevel()` 全仓**只有一个调用点**，就在这条筛选条自己的胶囊里；
                //   也就是说它只能被自己打开——`selectedLevels` 实际只有两种取值：
                //   空（全部）或 {ERROR, WARN}（「只看错误」三态开关塞进去的）。
                //   于是这条一旦渲染出来就永远无法再次进入，属于自指死链。
                // 保留：`selectedLevels` 这套存储与 showAllLevels/showErrorsOnly/isErrorsOnly
                //   仍是「只看错误」的实现底座，未动；clearFilter() 仍被 locateOriginal 使用。

                // ===================== 吸顶区（11-11 用户令）=====================
                // 三行：①筛选开关 ②书名（点=切书）+章名（点=跳章）+进度行。
                // 只在有书章时显示书章两行；筛选行始终显示（它是日常操作）。
                // ⚠️ 放在 topBar 里而不是列表 stickyHeader：topBar 天然不随列表滚动，
                // 且与搜索行/分隔线同属一个固定区，不会出现「sticky 与 topBar 抢层级」。
                // 11-13 用户令：「日志空白页不应该显示这些，得有能筛选的东西才显示」。
                // 没有任何日志时不渲染筛选区（含下方分隔线），空页只剩标题与搜索。
                if (vm.logs.isNotEmpty())
                PinnedLogHeader(
                    errorOnly = vm.isErrorsOnly(),
                    // ===== 吸顶四颗（11-18 正交化，用户令）=====
                    // 「筛错误」= 系统主日志的级别过滤；「+朗读规则/+插件日志」= 混入来源开关
                    // （勾了即全量混入，混入行豁免级别过滤，见 filteredLogs.keep）。三者**并选**
                    // ——11-16 的「筛错误熄灭混入」口径作废：那次熄灭 + 回原位不恢复，
                    // 导致用户勾过的规则日志全量视图丢失、同组 1、3 条找不回（真机实锤）。
                    // 「最新」保持与筛错误互斥（跟随最新滚错误列表的底语义确实拧）。
                    onToggleErrorOnly = {
                        if (vm.isErrorsOnly()) {
                            vm.showAllLevels()
                        } else {
                            vm.showErrorsOnly()
                            if (scrollMode == ScrollMode.FOLLOW_LATEST) {
                                scrollMode = ScrollMode.FOLLOW_PLAYING
                            }
                        }
                    },
                    showRule = vm.showSpeechRuleLogs.value,
                    onToggleRule = {
                        vm.showSpeechRuleLogs.value = !vm.showSpeechRuleLogs.value
                        // 正交化后不再退筛错误——三键独立
                    },
                    showPlugin = vm.showPluginLogs.value,
                    onTogglePlugin = {
                        vm.showPluginLogs.value = !vm.showPluginLogs.value
                        // 正交化后不再退筛错误——三键独立
                    },
                    latestMode = scrollMode == ScrollMode.FOLLOW_LATEST,
                    onToggleLatest = {
                        scrollMode =
                            if (scrollMode == ScrollMode.FOLLOW_LATEST) ScrollMode.FOLLOW_PLAYING
                            else ScrollMode.FOLLOW_LATEST
                        // 11-16 晚互斥：进入跟随最新时退出「筛错误」独占态（混入两颗保留）
                        if (scrollMode == ScrollMode.FOLLOW_LATEST) {
                            if (vm.isErrorsOnly()) vm.showAllLevels()
                            // 11-17 放宽后点「最新」的一次性跳底改走 pendingJumpToBottom：
                            // 旧版靠「松手立马回底」分支顺手滚到底，松手弹回已按用户要求
                            // 撤掉（参照最早「实时最新」宽松语义），入口跳底不能跟着丢。
                            jumpToLiveTick++
                        }
                    },
                    book = logGroups.pinnedBook,
                    bookCount = logGroups.pinnedBookCount,
                    bookTotal = logGroups.bookTotal,
                    chapter = logGroups.pinnedChapter,
                    chapterCount = logGroups.pinnedChapterCount,
                    // 11-14 用户定稿：下拉菜单点选后直接生效，不再二次弹窗——
                    // 书=切筛选范围（原书籍弹窗职责）；章=滚到该章首条（原章节弹窗职责）。
                    // 弹窗本体保留（章节弹窗还有"该章还没有日志"等兜底文案依赖，
                    // 若后续确认无入口再撤）。
                    // 11-15 晚：书/章框右端小红叉 → 弹确认 → 按范围清日志
                    onClearBook = { b -> pendingClear = ClearScope(b, null) },
                    onClearChapter = { b, c -> pendingClear = ClearScope(b, c) },
                    onSwitchBook = { b ->
                        vm.scopeBook.value = b
                        vm.scopeChapter.value = null
                    },
                    onSwitchChapter = { b, c -> jumpToChapter(b, c) },
                    knownBooks = vm.knownBooks,
                    chaptersOf = { b -> vm.chaptersOf(b) },
                    // 筛选条件（与"视野"分开）：null=不限
                    scopeBook = vm.scopeBook.value,
                    scopeChapter = vm.scopeChapter.value,
                    // 视野：跟着滚动显示"你看的是哪本哪章"（用户：「我划到哪就显示哪本书」）
                    viewBook = viewBookChapter.first,
                    viewChapter = viewBookChapter.second,
                    atBottom = atListBottom,
                    onClearScope = { vm.clearScope() },
                    // 切过去（用户定案 A）：回到全部日志 + 实时最新。
                    // 滚到底由 LogScreen 内部 autoScrollToBottom 的 LaunchedEffect 完成
                    // ——打开实时最新后 list.size 变化就会自动 animateScrollToItem(lastIndex)。
                    onJumpToLive = {
                        // ⚠️ 只清筛选 + 请求一次滚底，**绝不碰 autoScrollToBottom**。
                        // 那个开关是用户勾选的「持续跟随」，点一次跳转不该替用户打开它
                        //（11-11 用户纠正：「只有我确定勾选了才启用，一般都是正常状态」）。
                        vm.clearScope()
                        jumpToLiveTick++
                    },
                    // 正在朗读的书（与筛选无关）：用于"筛着 A 在读 B"的跳转提示条
                    readingBook = readingBook,
                    readingChapter = readingChapter,
                    // 11-14 后台合成进度行 + 快照（内部自算书章行后缀）
                    playingSnap = progressSnap,
                    // 绿点 15s 活跃门：外层 playingSnap=ReadProgressReader.read()
                    // （playing=true 且 15s 内有心跳）；不认 30min 宽限的 readRaw
                    dotActive = playingSnap != null,
                )
                // 11-16 用户令：「如果没有后台合成那一行文字，吸顶区下方那条分隔线也不要，
                // 书名框和章节框的描边就能代替」。
                // 所以这条线的存在性=**合成进度行是否会渲染**（同一判据，抽成 hasSynthLine，
                // 免得两处各写一份又走样）。有进度行时它把"控件带"和"日志流"分开；
                // 没进度行时，书框/章框那圈描边本身就是边界，再多一条线是重复。
                if (vm.logs.isNotEmpty() && hasSynthLine(progressSnap))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    ) { paddingValues ->
        LogScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .padding(bottom = paddingValues.calculateBottomPadding()),
            // 11-14「正在朗读」：快照（卡片描边/复位浮钮/段号丸）；进度行在 PinnedLogHeader
            playingSnapshot = playingSnap,
            // 11-22 02:2x 用户令「还是别要这个底部的悬浮窗了」：底部固定缓存提示已撤，
            // bottom* 四个参数同步删除（读数仍见吸顶进度行）。
            list = displayLogs,
            listState = listState,
            // 11-15：「实时跟随」整条链路随胶囊一起撤（见 PinnedLogHeader 里「最新日志」
            // 胶囊的注释）。列表不再自己滚底，回最新统一走右下角 ⇊ 浮钮。
            // 搜索定位/跳章那些**一次性**滚动仍由各自的回调驱动，不受影响。
            searchQuery = searchQuery,
            selectionMode = selectionMode,
            checkedEntries = checkedEntries,
            onToggleCheck = { e -> checkedEntries = if (e in checkedEntries) checkedEntries - e else checkedEntries + e },
            // 长按拖动多选（1003）：长按即进多选并勾上该条，拖动连选、拖回缩小；
            // 整集替换（非逐条 toggle），底栏「复制(N)」计数拖动中实时跟着走
            dragSelectEnabled = true,
            onEnterSelection = { selectionMode = true },
            onCheckedChange = { checkedEntries = it },
            // 共享归组（10-06 全链卡）：与上面搜索跳转用同一实例，条目↔列表项不错位
            groups = logGroups,
            // 「原文」定位（用户 10-08 午后 甲方案）：筛选/搜索态显示，点击回调清筛选+跳原位
            showLocateKey = isFiltered,
            onLocateOriginal = { entry -> locateOriginal(entry) },
            scrollMode = scrollMode,
            onScrollModeChange = { scrollMode = it },
            locateHighlight = locateHighlight,
            // 书籍条右侧「切换书」：开书单弹窗（只切日志查看范围，用户 11-10 明确）
            onSwitchBook = { bookPickerFor = it },
            // 章条右侧「切换章」：弹章节列表（用户 11-11 令，与「切换书」对称）
            onSwitchChapter = { book, chapter -> chapterPickerFor = book to chapter },
            // 视野书章回传（11-11）：吸顶区那行"当前：《书名》第N章"跟着滚动变。
            // 只在首项所属书章变化时触发（LogScreen 内部已按 derivedStateOf 收敛）。
            onViewChanged = { b, c -> viewBookChapter = b to c },
            // 11-15：吸顶书章的"跟随最新/跟随视线"判据（替代已删的「最新日志」胶囊）
            onAtBottomChanged = { atListBottom = it },
            // 切过去时强制滚底（见 LogScreen 里 autoScrollToBottom 的注释）
            jumpToLiveTick = jumpToLiveTick,
            // 11-20「日志栏喇叭」：卡右上喇叭 → 两个动作。
            // resynthesize=true：先让 U阅 删该段缓存、再读该段原文送合成
            //   （UTTS 侧跳过合成缓存，SynthCacheBypass）。这样做而不是在本页直接
            //   发合成请求：本页拿不到「该段最终的合成文本」（规则跑完后会加音效标记、
            //   拆句、换角色音色），只有 U阅 重新走一遍整链才与正常朗读同源。
            // resynthesize=false：只写命令文件，由 U阅 从该段起连续朗读。
            onSpeakerAction = { entry, resynthesize ->
                // 重合成先登记 UTTS 侧合成缓存旁路（按「该段最终合成文本」放行一次）。
                // 文本取请求行 <b>…</b> 内的正文——那正是送合成器的最终文本，
                // 与 RequestPayload.text() 同源，故能命中。不登记的话 U阅 重新请求
                // 会直接命中 synth_cache 秒回旧音频，等于没重新合成。
                val segmentText = com.github.jing332.tts_server_android.compose.systts
                    .boldBodyOf(entry)
                if (resynthesize && segmentText.isNotBlank()) {
                    com.github.jing332.tts.synthesizer.SynthCacheBypass.arm(segmentText)
                }
                com.github.jing332.tts_server_android.service.systts.help.ReadCommandFile
                    .writePlayFrom(
                        book = entry.bookName,
                        chapterTitle = entry.chapterTitle,
                        chapterIndex = -1, // 日志行不带章序，由 U阅 按章标题自行解析
                        segIndex = entry.paraIndex,
                        singleParagraph = resynthesize,
                        purgeCache = resynthesize,
                    ).let { ok ->
                        context.toast(
                            if (ok) {
                                if (resynthesize) "已请求重新合成该段"
                                else "已通知从该段开始朗读"
                            } else {
                                "操作失败：无法写入朗读命令"
                            }
                        )
                    }
            },
            // 11-20「试听这一段」：喇叭单击（照阅读端段落喇叭）。
            // 走 TaggedTtsPreviewPlayer（独立播放器：不触发 BGM、不进朗读队列、
            // 不落服务日志），preferCache=true 让它优先播合成缓存里的成品——
            // 有缓存时听到的就是 U阅 刚才那一份，没缓存时现场合成。
            // 与「重新合成」的关键区别：**不打断** U阅 正在进行的朗读。
            onAuditionSegment = { entry ->
                val entity = entry.configId
                    .takeIf { it > 0L }
                    ?.let { com.github.jing332.database.dbm.systemTtsV2.get(it) }
                val text = com.github.jing332.tts_server_android.compose.systts.boldBodyOf(entry)
                if (entity == null || text.isBlank()) {
                    context.toast("无法试听：这条日志找不到对应配置项或正文")
                } else {
                    com.github.jing332.tts.TaggedTtsPreviewPlayer.play(
                        context, entity, text, preferCache = true,
                    )
                }
            },
            // 点章节签只负责滚动到本章首条（滚动已在 LogScreen 内完成，这里不再改筛选——
            // 一改 scopeChapter 列表就重排，刚滚到的位置会立刻错位）
        )
    }

    // 书籍切换弹窗（11-10 方案 A）：只切「日志看哪本书」，不写 cunfang.txt、
    // 不动角色管理的当前书（用户 11-10 明确「切书就在日志里生效」）。
    // 版式照角色管理的书籍列表：当前书=主色竖条 + 主色书名，其余=彩色圆点。
    bookPickerFor?.let { currentBook ->
        val books = vm.knownBooks
        AlertDialog(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = { bookPickerFor = null },
            title = { Text("书籍列表") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (books.isEmpty()) {
                        Text(
                            "日志里还没有书章信息（需朗读规则 v1.7 起）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 「全部书籍」置顶：清掉书章范围，回到完整时间流
                    BookPickRow(
                        name = "全部书籍",
                        count = vm.logs.size,
                        isCurrent = vm.scopeBook.value == null,
                        isAll = true,
                    ) { vm.clearScope(); bookPickerFor = null }
                    books.forEachIndexed { idx, book ->
                        val isCurrent = vm.scopeBook.value == book
                        BookPickRow(
                            name = book,
                            count = vm.logs.count { it.bookName == book },
                            isCurrent = isCurrent,
                            dotColor = BOOK_PICK_DOTS[idx % BOOK_PICK_DOTS.size],
                        ) {
                            // 切书=重设范围；章节归属变了，顺手清掉章范围，免得锁在旧书的章上
                            vm.scopeBook.value = book
                            vm.scopeChapter.value = null
                            bookPickerFor = null
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { bookPickerFor = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // 章节切换弹窗（11-11 用户令）：点章条右侧「切换章」打开，点一条跳到该章第一条日志。
    // ⚠️ 列表用**完整章名原文**：用户书里的章名有「第 14 章」（带空格）、「第二百六十二章」
    // （中文数字）、「第1章(5)」「第1章(8)」（同号重复）等不规则写法，任何"解析成数字再排"
    // 的方案都会出错（两个第1章分不清、第1章排到第10章后面）。这里原样显示、按出现顺序排，
    // 左边序号只是列表位置（不是章号），用户照着原文挑即可。
    chapterPickerFor?.let { (pickBook, pickChapter) ->
        val chapters = vm.chaptersOf(pickBook)   // 按首次出现顺序，带每章条数
        AlertDialog(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = { chapterPickerFor = null },
            title = { Text("章节") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "《$pickBook》",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    if (chapters.isEmpty()) {
                        Text(
                            "该书的日志里还没有章节信息（需朗读规则 v1.7 起）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    chapters.forEachIndexed { i, (name, cnt) ->
                        val isCur = name == pickChapter
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(AppShapes.sm)
                                .then(
                                    if (isCur) Modifier.background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    ) else Modifier
                                )
                                .clickable {
                                    chapterPickerFor = null
                                    // 跳章=滚到该章第一条，**不改筛选范围**。
                                    // 曾想过「点章=只看本章」（写 scopeChapter），但那会让列表立刻
                                    // 重排、刚滚到的位置当场错位；而且用户要的是「跳过去看」，
                                    // 不是「把它从全流里摘出来」。故只滚动。
                                    val idx = displayLogs.indexOfFirst {
                                        it.bookName == pickBook && it.chapterTitle == name
                                    }
                                    if (idx >= 0) {
                                        // 归组后条目下标 ≠ 列表项下标，必须经 logGroups 换算
                                        val item = logGroups.listItemFor(idx)
                                        scope.launch {
                                            runCatching {
                                                listState.animateScrollToItem(item)
                                            }
                                        }
                                    } else {
                                        context.toast("该章还没有日志")
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                // 左边序号=列表位置，不是书里的章号（两个「第1章」也能区分）
                                text = "${i + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(24.dp),
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCur) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isCur) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (isCur) "$cnt 条 · 当前" else "$cnt 条",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { chapterPickerFor = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // 日志文件弹窗（11-11 用户令：把顶栏的「文件夹 + 垃圾桶」合成一个入口）
    //
    // 原来顶栏是两个键：📁 只管打开列表，🗑 只能一键全删，想删某个文件做不到。
    // 现在只剩 📁 一个入口，能力全搬进弹窗：
    //   · 点行 = 打开看（原样）；
    //   · 行尾 🗑 = 删这一个文件（点两下确认，见 pendingDeleteName）；
    //   · 标题行右上角「全部清空」= 原顶栏垃圾桶的功能（清四份回读来源，逻辑仍在 vm.clear()）；
    //   · 底部「打开目录」保留（打开文件管理器看这个文件夹）。
    pendingClear?.let { scope ->
        val isChapter = scope.chapter != null
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text(if (isChapter) "清理这一章的日志？" else "清理这本书的日志？") },
            text = {
                Text(
                    buildString {
                        append(scope.book)
                        scope.chapter?.let { append("\n").append(it) }
                        append("\n\n只删这一范围的行，其它书/章保留；删除后不可恢复。")
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val c = scope.chapter
                    if (c == null) vm.clearByBook(scope.book) else vm.clearByChapter(scope.book, c)
                    // 11-21 复核补漏：按书/章清空后，若视野书章正好是被清的范围，
                    // 吸顶不该再挂着它——兜底逻辑会因残留非空值跳过，这里主动复位。
                    // （复位后 LaunchedEffect(vm.logs.size) 若列表还有其他书会重新兜底。）
                    if (viewBookChapter.first == scope.book &&
                        (scope.chapter == null || viewBookChapter.second == scope.chapter)
                    ) viewBookChapter = "" to ""
                    pendingClear = null
                }) {
                    Text("清理", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = null }) { Text("取消") }
            },
        )
    }

    if (showLogFilesDialog) {
        // ⚠️ 用 fileCount 当 remember 的 key：删完一个文件后必须重新 listFiles，
        // 否则列表里还挂着已删的那行（原来只 remember 一次、无 key，等于永远不刷新）。
        var fileCount by remember { mutableStateOf(-1) }
        val logFiles = remember(showLogFilesDialog, fileCount) {
            File(vm.logDir()).listFiles()
                ?.filter { it.isFile }
                // 11-11 用户令：**固定顺序**，不再按修改时间排。
                // 原来按 lastModified 倒序：谁刚写过谁跳最上面，行序每次打开都不一样，
                // 想删某个文件得先找一遍。日志文件本来就那么几个，固定位置更好用。
                // 用户指定：朗读规则日志排在插件日志之前。
                // 未列出的文件（轮转出来的 *_旧.log 等）统一排在后面，再按时间倒序。
                ?.sortedWith(
                    compareBy<File>(
                        {
                            when (it.name) {
                                "system_tts.log" -> 0        // 朗读日志
                                "speech_rule.log" -> 2       // 朗读规则日志
                                "plugin.log" -> 3            // 插件日志
                                "debug.log" -> 4             // 调试日志
                                "crash.log" -> 5             // 崩溃日志
                                else -> 9                    // 旧文件/未知文件垫底
                            }
                        },
                        { -it.lastModified() },
                    )
                ) ?: emptyList()
        }
        val timeFmt = remember {
            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        }
        AlertDialog(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = {
                showLogFilesDialog = false
                pendingDeleteName = null
            },
            title = {
                // 11-11 用户令「全部清空，应该放右上角」：
                // 标题行做成一整行——左边标题、右边「全部清空」。
                // 为什么挪上去：清空是这一页的**主操作**，放底部按钮区会和「取消」
                // 抢同一条视线；右上角是全站放"整页级动作"的固定位置。
                // 另有快捷路径：长按顶栏文档图标也能清空（带确认框）。
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "日志文件",
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "全部清空",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                vm.clear()
                                // 11-21 复核补漏：清空后视野书章同步复位——旧实现挂在
                                // remember(vm.logs) 上随清空自动重算回空，改成手动兜底后
                                // 非空残留（滚动回写过）不会被清，吸顶会挂着已清空的书章。
                                viewBookChapter = "" to ""
                                fileCount = fileCount + 1
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 11-11 用户令「提示一下当前所在的位置，就写一个 log 文件夹在的位置就行」：
                    // 日志目录现在在公共存储下（Download/chajian/log），用户可能想自己去
                    // 文件管理器翻，或想知道"到底存哪了"。这里直接把绝对路径写出来，
                    // 长按可复制——路径很长，点名拷比手抄靠谱。
                    // ⚠️ 用 vm.logDir() 而不是写死字符串：权限不足时它会自动回退到私有目录，
                    // 写死的话显示的位置和真实位置会对不上。
                    val dirPath = vm.logDir()
                    Text(
                        text = dirPath,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable {
                                // 点一下就整条复制，方便粘到文件管理器或发给我
                                clipboard.setText(AnnotatedString(dirPath))
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    if (logFiles.isEmpty()) {
                        Text(
                            "日志目录为空",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    logFiles.forEach { f ->
                        val armed = pendingDeleteName == f.name
                        // 点两下确认：第一次点只是「上膛」（标红 + 文案变），2 秒后自动复原。
                        // 不用二次确认弹窗——在文件列表里再套一层对话框会让人以为流程很重。
                        // key 用 f.name：这个列表是 forEach 铺开的，若只按 armed 做 key，
                        // 删除后重排会让计时状态错挂到别的行上
                        LaunchedEffect(f.name, armed) {
                            if (armed) {
                                kotlinx.coroutines.delay(2000)
                                if (pendingDeleteName == f.name) pendingDeleteName = null
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(AppShapes.sm)
                                .then(
                                    if (armed) Modifier.background(
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    ) else Modifier
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clip(AppShapes.sm)
                                    .clickable {
                                        // 11-11 用户令：点开改成**应用内预览**，不跳外部查看器。
                                        // 外部查看器不一定装、大文件还会卡在系统加载上，
                                        // 而且它一点开就把本页弹窗关掉，看完回来要重新点图标。
                                        showLogFilesDialog = false
                                        pendingDeleteName = null
                                        previewFile = f
                                        previewLines = emptyList()
                                        previewWantLines = PREVIEW_PAGE
                                        previewLoading = true
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            // 先给尾部 PREVIEW_PAGE 行；不够再点「加载更多」往回要。
                                            // 单次读取上限 1MB（见 readTailLinesForPreview），
                                            // 所以无论点多少次都不会一次性把大文件整个读进内存。
                                            val lines = runCatching {
                                                readTailLinesForPreview(f, PREVIEW_PAGE)
                                            }.getOrDefault(emptyList())
                                            previewLines = lines
                                            previewHasMore = lines.size >= PREVIEW_PAGE
                                            previewLoading = false
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    // 主标题用中文名（11-11 用户令）；磁盘文件名作为副信息保留——
                                    // 排障时两边要对得上，只说"朗读日志"他不知道是哪个文件。
                                    val cn = logFileDisplayName(f.name)
                                    Text(
                                        text = cn ?: f.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        color = if (armed) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (armed) "再点一次删除"
                                        else buildString {
                                            append("${f.length() / 1024} KB · ")
                                            append(timeFmt.format(f.lastModified()))
                                            if (cn != null) append(" · ${f.name}")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (armed) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                // 删除键：30dp 视觉、48dp 触摸区（与全站最小可点口径一致）
                                IconButton(
                                    onClick = {
                                        if (armed) {
                                            pendingDeleteName = null
                                            vm.clearFile(f)
                                            fileCount = fileCount + 1   // 触发重新 listFiles
                                        } else {
                                            pendingDeleteName = f.name
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "删除 ${f.name}",
                                        tint = if (armed) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            // ⚠️ confirmButton 是 M3 AlertDialog 的**必填参数**（无默认值）。
            // 删「打开整个目录」时我把它整个删了 → 编译报 "None of the following candidates
            // is applicable"，连带后面一串 @Composable 报错（CI run 43031934592）。
            // 现在放一个「关闭」：主按钮位不空着，取消键也还在（两处都能关）。
            confirmButton = {
                TextButton(onClick = {
                    showLogFilesDialog = false
                    pendingDeleteName = null
                }) {
                    Text("关闭")
                }
            },
            // 「打开整个目录」已删（11-11 用户令「这个选项感觉没啥用」）：
            // 日志目录现在就在 Download/chajian/log，文件管理器里能直接进，
            // 再给一个"用外部应用打开目录"的键属于重复入口，且很多文件管理器并不支持。
            dismissButton = {
                TextButton(onClick = {
                    showLogFilesDialog = false
                    pendingDeleteName = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 长按顶栏图标 → 全部清空的确认框（11-11 用户令）
    if (showClearAllConfirm) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("清空全部日志？") },
            text = {
                Text(
                    "会清空日志目录里的全部文件：系统TTS、调试（debug）、崩溃（crash）、" +
                        "插件、朗读规则五类，以及各轮转备份。" +
                        "清空后不可恢复。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.clear()
                    // 11-21 复核补漏：同上，清空后视野书章复位（见长按顶栏清空处注释）
                    viewBookChapter = "" to ""
                    showClearAllConfirm = false
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 应用内日志预览（11-11 用户令：点开文件直接在 App 里看）
    //
    // 为什么不交给外部查看器：手机不一定装了文本查看器；大文件在系统查看器里会卡加载；
    // 而且它会把本页挤到后台，看完回来还得重新点图标。
    // 这里只读**尾部 300 行**——日志是按时间追加的，出问题看最近的才有意义。
    previewFile?.let { pf ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = {
                previewFile = null
                previewWantLines = PREVIEW_PAGE
                previewHasMore = false
            },
            title = {
                Column {
                    Text(logFileDisplayName(pf.name) ?: pf.name)
                    // 副标题：原文件名 + 大小，排障时对得上磁盘上的文件
                    Text(
                        "${pf.name} · ${pf.length() / 1024} KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            text = {
                Column(Modifier.heightIn(max = 420.dp)) {
                    if (previewLoading) {
                        Text(
                            "读取中…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (previewLines.isEmpty()) {
                        Text(
                            "这个文件是空的",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // 头部：行数 + 「加载更多」（11-11 用户令）
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "最近 ${previewLines.size} 行",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (previewHasMore) {
                                Text(
                                    if (previewLoading) "读取中…" else "加载更多",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (previewLoading) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable(enabled = !previewLoading) {
                                            // 每次往回多要一页；读到文件开头后 hasMore 转 false，按钮消失
                                            val want = previewWantLines + PREVIEW_PAGE
                                            previewWantLines = want
                                            previewLoading = true
                                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                val more = runCatching {
                                                    readTailLinesForPreview(pf, want)
                                                }.getOrDefault(emptyList())
                                                previewLines = more
                                                previewHasMore = more.size >= want
                                                previewLoading = false
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Column(
                            Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // 等宽字体：日志按列对齐，比例字体读起来会串位
                            Text(
                                text = previewLines.joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                // 复制全文（尾部 300 行）——发给我排查时比截图方便
                TextButton(
                    enabled = previewLines.isNotEmpty(),
                    onClick = {
                        clipboard.setText(AnnotatedString(previewLines.joinToString("\n")))
                    }
                ) {
                    Text("复制")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    previewFile = null
                    // 重置分页：下次点开的文件要重新从一页开始，
                    // 否则会带着上次"加载了 1500 行"的状态直接读一大坨
                    previewWantLines = PREVIEW_PAGE
                    previewHasMore = false
                }) {
                    Text("关闭")
                }
            }
        )
    }

    // 11-11 用户令：筛选弹窗整体删除——9 个控件全部挪进吸顶区（筛选开关行）
    // 与书章切换（书条/章条的「切换书/切换章」）。弹窗里的 scopeBook/scopeChapter
    // 两个「只看本书/只看本章」也一并作废：切书后列表本来就只显示那本书。

}

/**
 * 日志文件的中文名（11-11 用户令「这些日志能不能用中文名」）。
 *
 * 只做**显示层**映射：磁盘上的文件名一个字都不改——logback.xml、子行写入、
 * console 落盘、清空逻辑全都按英文名找文件，改名等于把整条链路拆了。
 *
 * 不认识的文件（如轮转出来的 system_tts_2026-11-11.0.log）不进这张表，
 * 调用方回退显示原文件名，不会因为新增文件就显示空白。
 */
/** 预览每次加载的行数（11-11 用户定：先给 300 行，不够点「加载更多」往回要） */
private const val PREVIEW_PAGE = 300

private fun logFileDisplayName(fileName: String): String? = when {
    // 11-11 用户定名：朗读主日志 → 朗读日志（去掉"主"，它不跟任何东西对仗，反而像"最重要"）
    fileName == "system_tts.log" -> "朗读日志"
    fileName == "plugin.log" -> "插件日志"
    fileName == "speech_rule.log" -> "朗读规则日志"
    fileName == "debug.log" -> "调试日志"
    fileName == "crash.log" -> "崩溃日志"
    // 轮转备份：system_tts_2026-11-11.0.log / debug_2026-11-11.0.log 之类
    fileName.startsWith("system_tts_") -> "朗读日志（旧）"
    fileName.startsWith("debug_") -> "调试日志（旧）"
    fileName.startsWith("crash_") -> "崩溃日志（旧）"
    else -> null
}

// 富文本 → 纯文本（复制用，10-12 用户实报「复制出来全是标签」）：
// ① <br> / <br/> → 换行；② 其余标签（<font …>、<b>、</…>）整个删掉；
// ③ 顺手还原常见实体（&lt; &gt; &amp; &nbsp;）——粘到聊天框里能直接读。
// 只用正则做，不引 HTML 解析器：日志正文的标签集是固定的这几种（见 SystemTtsService 拼装处）。
internal fun String.toPlainText(): String = this
    .replace(Regex("(?i)<br\\s*/?>"), "\n")
    .replace(Regex("(?s)<[^>]*>"), "")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")
    .replace("&nbsp;", " ")

// 用外部查看器打开文件/目录（text/plain 优先，通用类型兜底）
// 目录必须走 */*（text/plain 会把目录 URI 丢给文本编辑器，打不开）
/**
 * 读日志文件尾部 N 行，供应用内预览（11-11）。
 *
 * 与 TtsLogViewModel.readTailLines 同思路但**不复用**：那是 private 且带日志页的解析口径，
 * 这里只要原始文本行。实现同样只读文件尾部 1MB —— 日志单文件可达 10MB，
 * 整个读进内存是 OOM 的经典爆点（10-11 就因 readLines 全量读崩过一次）。
 */
private fun readTailLinesForPreview(file: java.io.File, maxLines: Int): List<String> {
    val len = file.length()
    if (len <= 0) return emptyList()
    // 读多少字节：想要的行数越多读得越多，但**单次永远不超过 2MB**——
    // 就算用户反复点「加载更多」，内存占用也是有界的（日志平均一行 ~200 字节，
    // 2MB 约一万行，足够覆盖任何一次实际排查）。
    val wantBytes = (maxLines.toLong() * 220L).coerceAtMost(2L * 1024 * 1024)
    val tailLen = minOf(len, wantBytes).toInt()
    val buf = ByteArray(tailLen)
    java.io.RandomAccessFile(file, "r").use { raf ->
        raf.seek(len - tailLen)
        raf.readFully(buf)
    }
    val lines = String(buf, Charsets.UTF_8).split('\n')
    // 起点不在文件头时首行多半是被截断的半行，丢掉
    val start = if (tailLen < len) 1 else 0
    return lines.drop(start).filter { it.isNotBlank() }.takeLast(maxLines)
}


// ===================== 日志「切换书」弹窗的行（11-10 方案 A）=====================
// 与角色管理 BookRow 同语汇：当前书=主色竖条 + 主色书名，其余=彩色圆点 + 常规色。
// 额外带「N 条」计数——日志页选书是为看历史，条数能让人知道这本书有多少料。
private val BOOK_PICK_DOTS = listOf(
    Color(0xFF7E57C2), Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFF8D6E63),
    Color(0xFF66BB6A), Color(0xFFEC407A), Color(0xFFFF7043), Color(0xFF42A5F5),
)

@Composable
private fun BookPickRow(
    name: String,
    count: Int,
    isCurrent: Boolean,
    isAll: Boolean = false,
    dotColor: Color = Color(0xFF7E57C2),
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 行首标记位固定 16dp：当前书=主色竖条 / 其余=5dp 彩色圆点 → 书名左缘始终对齐
        Box(Modifier.width(16.dp), contentAlignment = Alignment.CenterStart) {
            if (isCurrent) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .background(MaterialTheme.colorScheme.primary, AppShapes.xs)
                )
            } else if (!isAll) {
                Box(Modifier.size(5.dp).background(dotColor, CircleShape))
            }
        }
        Text(
            text = name,
            // 15sp：与角色管理书籍行同档（bodyLarge 16sp 在弹窗里偏大，降到 15 仍高于次要信息）
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = if (isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count 条",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 按范围清日志的目标：chapter=null 表示清整本书（11-15 晚红叉用）。 */
internal data class ClearScope(val book: String, val chapter: String?)

/** 吸顶区③-b「合成进度行」是否会出现——吸顶区分隔线的存在性跟它走（11-16 用户令）。 */
private fun hasSynthLine(
    snap: com.github.jing332.tts_server_android.service.systts.help.ReadProgressReader.Snapshot?
): Boolean {
    val s = snap ?: return false
    if (s.synthTotalSegs > 0) return true
    if (shortChapterLabel(s.synthChapterTitle).isNotBlank()) return true
    return s.synthChapterIndex >= 0
}

// ===================== 日志吸顶区（11-11 用户令「书名和章节名一直置顶」）=====================
// 三行结构，从上到下（11-16 用户令「把筛选调到书章下面」后的行序）：
//   ① 书/章行（书名点=切书弹窗，章名点=跳章弹窗）
//   ② 筛选开关：实时 | 错误 ‖ 朗读规则 | 插件
//      （11-11 用户令删掉 DEBUG：它只在插件用 console.debug() 打点时有用，默认本就关闭，
//       他的规则脚本与插件里 console.debug 是 0 处，整排留一颗永远不亮的键只占宽度）
//      （11-11 用户令改短：「实时最新」→「实时」、「只看错误」→「错误」；
//       原设计这里是竖线分档，但那道竖线早已撤掉，只剩左端标签旁的一道）
//   ③ 合成进度行（沉底，紧贴日志列表）
//
// 书章行只在有数据时显示（老规则/单语音模式下整块隐藏，不出现空条）。
// 字号按主次（11-16 换位后）：书章 13sp 主角 > 筛选 12sp = 胶囊 12sp = 合成 12sp。
// 字色：书章 100% onSurface（黑色，你令提黑但不加粗）；筛选/胶囊/合成 78% 灰。
// 合成进度行另有一枚前置绿点做「这行是活的」的提示（11-16 用户令），颜色仍按筛选行走：
// 播放中呼吸（绿），停止变灰点静止——见下方进度行 if/else 注释。
@Composable
private fun PinnedLogHeader(
    errorOnly: Boolean,
    onToggleErrorOnly: () -> Unit,
    showRule: Boolean,
    onToggleRule: () -> Unit,
    showPlugin: Boolean,
    onTogglePlugin: () -> Unit,
    // 11-16 晚：滚动三态「跟随最新」入口胶囊（放本排最右，用户定稿位置）。
    // 选中=强制固定日志底部（新日志到达自动滚底，拖拽不退出）；取消=回跟随朗读。
    // 与「实时」（跳最新一次）、复位浮钮（回朗读位置）分工见 LogScreen scrollMode 注释。
    latestMode: Boolean = false,
    onToggleLatest: () -> Unit = {},
    book: String,
    bookCount: Int,
    /** 不同书数：<2 时「筛选书」没有可筛对象，不显示（用户 11-13 口径）。 */
    bookTotal: Int = 0,
    chapter: String,
    chapterCount: Int,
    onSwitchBook: (String) -> Unit,
    onSwitchChapter: (String, String) -> Unit,
    // 11-14 书章下拉数据源：书列表 / 某书的章列表（含条数）——由调用方从 vm 取，
    // 子组件不直接摸 vm（此前 CI 红：PinnedLogHeader 内引用主作用域 vm）。
    knownBooks: List<String> = emptyList(),
    chaptersOf: (String) -> List<Pair<String, Int>> = { emptyList() },
    // 11-11 用户定案「全部日志 + 其下方筛选」：
    //   · scopeBook / scopeChapter 是**筛选条件**（null=不限）；
    //   · viewBook / viewChapter 是**跟随滚动的视野信息**（当前看到哪本书哪一章）。
    // 两者必须分开：筛选决定列表范围，视野只用于"你看的是哪本"这行小字。
    scopeBook: String? = null,
    scopeChapter: String? = null,
    viewBook: String = "",
    viewChapter: String = "",
    // 11-15：列表是否停在最底部（替代已删的「最新日志」胶囊当判据）。
    // 在底部=你在看最新，吸顶显示最新那条的书章；往回翻=跟随视线。
    atBottom: Boolean = true,
    onClearScope: () -> Unit = {},
    // 11-15 晚：书框/章框右端小红叉的落地动作（清了要确认，所以这里只上报范围）
    onClearBook: (String) -> Unit = { _ -> },
    onClearChapter: (String, String) -> Unit = { _, _ -> },
    // 「切过去」＝回到全部日志 + 实时最新 + 滚到底（用户 11-11 定案 A）。
    // ⚠️ 原来这里错用了 onSwitchBook(readingBook)，那只是**打开书单弹窗**，
    // 既没清筛选也没滚到底，用户实报「你说的切过去是切到全部日志的实时最新吧」——
    // 他说的是对的，那是正确行为；原实现是 bug。
    onJumpToLive: () -> Unit = {},
    // 正在朗读的书（与筛选不同）：非空且与 scopeBook 不一致时给「切过去」提示条
    readingBook: String = "",
    readingChapter: String = "",
    // 11-14「正在朗读」播放快照（ReadProgressReader.read()）：非空时
    // ①书章行下渲染进度行 ②传给 LogScreen 做卡片描边与复位浮钮。null=没在朗读。
    playingSnap: com.github.jing332.tts_server_android.service.systts.help.ReadProgressReader.Snapshot? = null,
    // 11-16 晚：绿点的 **15s 活跃门**（用户拍板「做绿点15秒吧」）——呼吸只认「15s 内有心跳」
    // 的播放侧快照（= ReadProgressReader.read() 判据，调用方传 playingSnap != null）。
    // 为什么不直接用 snap.playing：snap 走 readRaw 的 30min 宽限（11-15「停止后进度行
    // 照显示」口径），U阅 进程被杀来不及写停止标记时 playing 会冻结为 true，
    // 绿点就假呼吸最长 30 分钟。呼吸/灰点以本参数为准；进度行**文字**仍按 snap.playing
    // 走（停止后照样显示合成进度的口径不动）。
    dotActive: Boolean = false,

) {
    Column(
        Modifier
            .fillMaxWidth()
            // 11-11 用户令「不应该有填充色，后面应该是等待选择的胶囊，选中才填色」：
            // 原来整条吸顶横带铺了一层淡绿（lerp(secondaryContainer, Low, 0.55) ≈ #E9F5ED），
            // 于是填充色跑到胶囊外面去了——那排看着像"每颗都已选中"。
            // 现在横带退回页底色（不设背景），颜色只留在被选中的那颗胶囊内部。
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ===== ① 书/章行（11-16 用户令「把筛选调到书章下面」：本排升为吸顶第一排）=====
        //
        // 结构定案（11-11 用户逐条确认，仍成立）：
        //   · 「全部日志」不再是"某一本书"，而是常驻标题；书名/章名降级成它下面的**筛选条件**；
        //   · 书筛选常驻（「书：全部」/「书：《书名》」）；**章筛选只在选了书之后出现**
        //     ——章脱离书没有可筛范围，单独摆出来只会让人困惑；
        //   · 另有一行小字显示**视野**（跟着滚动变：你看的是哪本书哪一章），它不是按钮；
        //   · 筛着 A 书却在读 B 时，追加一条提示条，可一键切过去（不劫持筛选）。
        //
        // ⚠️ 与旧版的区别：旧版把书条/章条当"切换"用（点按钮弹列表，语义是导航）；
        // 用户指出「你现在是筛选，不是切换」——所以胶囊文案与语义都改成筛选。

        // ① 书/章行（11-16 换位后为吸顶第一排）——**这一排不横滚**（11-11 用户实报后改）。
        //
        // ⚠️ 根因（别再套回 horizontalScroll）：
        // 原实现给这排套了 horizontalScroll，书名又用 weight(1f, fill=false)。
        // 横滚行给子项的宽度上界是**无限**，weight 分到的剩余空间也是无限，
        // 于是 fill=false 的文本按**原始宽度**铺开、永远不省略——书名有多长，这排就多宽，
        // 行尾的「筛选书」「切换章」被一路推出屏幕，用户只能横滑到最右才看得见
        //（原话：「筛选书出现的位置不太对，我需要在筛选里面滑到最右才显示」）。
        //
        // 现在用普通 Row（宽度受父约束）+ 左侧信息槽 weight(1f)：
        //   · 槽先让出右侧按钮的位置，按钮永远贴在行尾，不需要横滑；
        //   · 槽内书名超长时省略号，**章名固定宽度不让位**（「第N章」是用户要的信息，
        //     不能被书名挤掉）；筛选态胶囊同理，「取消」始终在胶囊右端可见；
        //   · 这排不再滚动，右缘渐隐随之撤掉（只有第一排还需要）。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 11-14 用户终令「章节那一排应该跟筛选左右边距一样」→ 8/8。
                // 11-16 用户令「把筛选调到书章下面」：这排升为吸顶**第一排**。
                // 左右仍 8/8（吸顶控件带口径不变）；bottom 0 保留——与后接进度行
                // 贴成一组（间距账见筛选行注释）。
                // ⚠️ 11-14 的「筛选行在上、书章行在下」布局已被本次换位取代，
                // 下方"同属一条带"的口径仍成立，但行序看 11-16 这次为准。
                .padding(start = 8.dp, end = 8.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            // 11-14 晚（用户令「书名和章节名中间有空隙咋处理」）：
            // 空隙的真因不是这里，而是**书名的 Box 挂了 weight(1f)**——它把整行剩余宽度
            // 全吃下来，章名框被顶到行尾，中间就露出一大段空白。已改 weight(1f, fill=false)
            // （只让"够用即可"、不吃满），两框因此**相邻**。这里的间距再收到 2dp，
            // 让两个框看成一整条带上的两颗，而不是被拉开的两截。
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // ===== 书/章：左侧信息 + 右侧按钮（11-11 用户选 B 方案定稿）=====
            // ## 两种状态必须一眼分得清（用户反复强调的点）
            //   · **未筛选**：普通灰字《书名》第N章，**无底、无绿、不可点**——纯信息；
            //   · **已筛选**：同一位置换成**淡绿胶囊**，右端写「**取消**」两个字（不是 ×）。
            //
            // ## 为什么是胶囊+「取消」而不是纯文字+×（用户 11-11 拍板 B）
            // × 太像"删除/关闭"，读者会误解它删的是这本书、或只是关掉某个提示；
            // 「取消」两个字无歧义。而胶囊底色正好承担"这条筛选正在生效"的状态表达，
            // 与未筛选时的灰字形成天然反差——不需要额外加"已筛选"三个字。
            // 取消仍只在这颗胶囊里，不给同一件事开第二个入口。
            val filtered = !scopeBook.isNullOrBlank()
            // ===== 11-13 晚（用户令「顶部章节名，两个都要：又要最新又要滚动」）=====
            //
            // 两个口径各有用途，用户明确要**都保留**：
            //   · 在看最新（列表在底部）= 顶部显示**最新那条**的书章
            //     （list.lastOrNull{带书章} → book/chapter）；
            //   · 在往回翻旧日志 = 顶部跟随**视线所在位置**
            //     （viewBook/viewChapter，由 LogScreen 回传）。
            //
            // 11-15 判据换了但语义不变：原来靠「最新日志」胶囊的勾选状态当开关
            //（autoFollow），胶囊已按用户令删除（"跳转动作不该占筛选位"），
            // 现在改看**列表是不是停在最底部**——在底部本来就等于"在看最新"，
            // 这个判据免费、不需要新控件，而且比手动勾选更贴合直觉
            //（你滑到最新它自然就跟最新，不需要额外记得开开关）。
            //
            // 回落顺序都保留：主值取空时（无书章条目/筛选清空）退到另一个，
            // 避免吸顶整块空掉。
            //
            // ===== 11-21 20:2x 用户终裁：吸顶**永远跟视线** =====
            //
            // 沿革：11-13「底部=最新条、翻旧=视线」→ 11-15 判据换「列表停底」
            // → 20:0x 改三层（朗读中停底=朗读章）→ 20:2x 终裁推翻三层、回到纯视线。
            //
            // 终裁理由（用户选「吸顶=永远跟视线」）：分工明确、行为可预期——
            //   · 吸顶书章 = 「我在看哪章」（划到哪显示哪，绝不自己跳）；
            //   · 第二行进度行 = 「朗读/备货到哪」（当前：247章 62/146｜缓存：248章 3/81）。
            // 20:08 实拍的「播放 247、备货探头 248、顶部跳 248」这类被动跳变根除：
            // 顶部只有滚动才变。20:0x 那版「停底=朗读章」的问题同样是被动跳变
            //（我在底部没动，播放切章时顶部自己换了）。
            //
            // 视线空（刚进页首帧、日志清空、筛选清空）→ 兜底「最后一条日志的书章」
            // → 再空（列表无书章条目）→ 空串（吸顶书章块整体隐藏）。
            // 筛选态仍显示筛选范围（scopeBook/scopeChapter），不变。
            val shownBook = when {
                filtered -> scopeBook.orEmpty()
                else -> viewBook.ifBlank { book }
            }
            val shownChapter = when {
                filtered -> scopeChapter.orEmpty()
                else -> viewChapter.ifBlank { chapter }
            }
            val chapLabel = shortChapterLabel(shownChapter).let { if (it.isBlank()) "" else " $it" }
            // 11-14 用户第二次改口：章名后的「（1/25）」**挪到第二行的进度行**去
            //（「我想把章节名后的（1/25）换到第二行」）。
            // 理由很实在：书章行本来就挤（长书名 + 章名 + 两个下拉箭头），再挂一个
            // 「（21/104）」会把章名和箭头挤到换行；而第二行本来就是讲进度的，
            // 两个数字放一起（后台合成 10/20 ｜ 当前朗读 5/10）语义也更连贯。
            // 故这里保留变量但恒为空串——保留名字是为了两处调用点不用改结构，
            // 将来若要恢复只需改这一处。
            val playingProgressSuffix = ""

            // ===== 11-14 晚（用户令「我感觉现在还是不够灵活，这个关于章节名和书名的长度」）=====
            //
            // 上一版给章名一个**固定**上限 maxWidth/3，好看但死板，实测立刻暴露矛盾：
            //   书名「三体」只需 ~65dp，章名「第9章 他把她锁进了柴房」需要 ~158dp，
            //   整行明明放得下，章名却被 1/3（≈115dp）硬切成了「第9章 他把她…」，
            //   而书名框那边空着 160dp 没法用。
            //
            // 病根：1/3 是**行宽的比例**，跟"书名实际要多少"无关，所以它既可能过宽、
            // 也可能过窄。真正该由书名决定章名能拿多少。现改为：
            //   ① 先量书名文字宽 wBook（用 TextMeasurer，不需要 composition）；
            //   ② 还剩下的宽度 = 行宽 − 2dp 间距 − wBook；
            //   ③ 章名上限 = (2) 与「行宽/3」取**较大**者 —— 即「书名用不完的部分都给章名」，
            //     1/3 退化成**保底**（防止书名极长时把章名压成省略号）；
            //   ④ 若两框都放得下（wBook + wChap ≤ 行宽 − 2dp），章名**不受任何上限**，
            //      完整显示，剩余空白**全归书名框**（用户 11-14 选定的分法）。
            //
            // 于是 1/3 只在「书名长 + 章名也长」时作为兜底出现，不再误伤短书名场景。
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            // 11-15：菜单宽度上限用屏宽（不是行宽）。已核对 material3 1.4.0-alpha09：
            // 横向定位前两档（对齐锚点）不做钳制，宽度只要 ≤ 屏宽−16（行内衬 8+8）
            // 就一定落得进窗，卡片边缘不会被 MD3 挪动。
            val screenW = LocalConfiguration.current.screenWidthDp.dp
            val chapTextStyle = TextStyle(fontSize = 11.5.sp)
            // 两框各自的"框内开销"（不是文字宽），用于把文字宽换算成框宽：
            //   书名框 = 左衬 6 + 图标 14 + 图标字距 3 + 箭头 14 + 右衬 3 = 40dp
            //   章名框 = 左衬 6 + 箭头 14 + 右衬 3                    = 23dp
            val bookChrome = with(density) { 40.dp.toPx() }
            val chapChrome = with(density) { 23.dp.toPx() }
            val gapPx = with(density) { 2.dp.toPx() }
            val wBookText = if (shownBook.isBlank()) 0f
            else textMeasurer.measure(shownBook, chapTextStyle).size.width.toFloat()
            val wChapText = if (chapLabel.isBlank()) 0f
            else textMeasurer.measure(chapLabel.trim(), chapTextStyle).size.width.toFloat()
            val bookNeeded = wBookText + bookChrome
            val chapNeeded = wChapText + chapChrome

            // 外层 BoxWithConstraints 只为拿**这一行真实可用宽**（已扣左右 8dp 内衬），
            // 供下面的章名上限做动态计算；它不改布局语义。
            // ⚠️ weight 必须挂在 BoxWithConstraints 上（它是外层 Row 的直接子项，有 RowScope）。
            // 挂到它内部的 Box 上会 CI 报「cannot be called in this context with an implicit receiver」
            // ——那一层是 BoxWithConstraintsScope，没有 RowScope.weight。
            BoxWithConstraints(Modifier.weight(1f)) {
            val rowW = constraints.maxWidth.toFloat()
            // 两框能否同时完整放下（各自内容宽 + 2dp 间距）
            val bothFit = chapLabel.isBlank() || (bookNeeded + chapNeeded + gapPx <= rowW)
            // 章名框上限（动态，不是写死 1/3）：
            //   · 放得下 → 不限，章名完整显示（剩余空白全归书名框，用户 11-14 选定）；
            //   · 放不下 → 取「行宽 − 间距 − 书名需要宽」与「行宽/3」中的**较大**者。
            //     含义：书名用不完的宽度优先给章名；1/3 退化成一个保底下限，
            //     只在书名很长、把章名挤到不足 1/3 时才生效。
            // 这样 1/3 不再是"天花板"，不会再出现「书名只要 65dp、章名却被切在 115dp」。
            val chapCapPx = when {
                chapLabel.isBlank() -> 0f
                bothFit -> rowW
                else -> maxOf(rowW - gapPx - bookNeeded, rowW / 3f)
            }
            val chapMaxWidth = with(density) { chapCapPx.toDp() }

            // 左侧信息槽：占满行内剩余宽度，右侧两个按钮先量、先占位。
            // 槽内书名可省略，章名不让位——长书名不能把「第N章」或右侧按钮挤没。
            Box(Modifier.fillMaxWidth()) {
                if (shownBook.isNotBlank()) {
                    if (filtered) {
                        // ---- 已筛选：淡绿胶囊 + 取消 ----
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                // 11-11 统一：改用 softFillBarColor()（= secondaryContainer 往白里混 42%，
                                // C 档 #DAF2E2），与角色列表标签框、换声面板分段选中态同一档。
                                // 原来用裸 secondaryContainer，比那两处浓一档——同一主题下两种绿。
                                // 它仍是从主题色派生的（lerp secondaryContainer），换主题会跟着变。
                                .background(softFillBarColor())
                                .padding(start = 9.dp, end = 3.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 11-14 用户令「做成下拉框的形式」：书名/章名各挂一个
                            // DropdownMenu——书多项/章多项时点击展开选择，只有一项
                            // 时无可选项=点了也不弹（视觉上就是普通文字，自然禁用）。
                            // 职责承接原「筛选书」（书下拉）与「跳到第x章」（章下拉）。
                            var bookMenu by remember { mutableStateOf(false) }
                            var chapMenu by remember { mutableStateOf(false) }
                            // 11-14：可点线索（与未筛选分支同口径）——挂展开箭头，
                            // 双项都套 Row 以让箭头紧跟文字、点击区域包含箭头。
                            //
                            // 11-14 用户实报「点击出来的卡片位置尤其不对，得居右吧，
                            // 还有这些点击出来的卡片跟标题很割裂」：
                            // 根因=DropdownMenu 的位置以**父布局节点**为锚点，而此前
                            // 它是挂在占满剩余宽度的信息槽 Box 里 ⇒ 菜单永远从行首弹出、
                            // 与触发文字隔着半个屏（真机截图实锤）。
                            // ⚠️ MD3 DropdownMenu **没有 alignment 参数**（CI 实锤
                            // 「No parameter with name 'alignment' found」），offset 也
                            // 只按锚点左上角算。唯一可靠做法：把菜单塞进**只包住触发文字
                            // 的小 Box**，锚点随文字走——菜单就贴在文字正下方展开，
                            // 与触发项不再割裂；超出屏边由 MD3 自带边缘钳制处理。
                            // ⚠️ weight 必须挂在 Box 上：Box 处在父 Row 的 RowScope 里，
                            // 而 Box 内部是 BoxScope（没有 weight）——挂在内层 Row 上
                            // CI 报「cannot be called in this context with an implicit receiver」。
                            // weight(1f) + fillMaxWidth（11-14 晚用户令「书名和章节名的框要把
                            // 整行撑满，而不是右侧留空」）：书名框吃掉行内剩余宽度，
                            // 与章名框合起来正好撑满整行。短书名时框照样宽、文字靠左——
                            // "撑满"是**框**的规矩，不是文字的宽。
                            // 章名不挂 weight ⇒ 它按自身宽度先排、永不被书名挤压（非 weight 子项先测）。
                            // 11-15：量书框实宽——弹窗宽度要与它一致（用户令）。
                            var bookTriggerW by remember { mutableStateOf(0.dp) }
                            Box(
                                Modifier.weight(1f).onGloballyPositioned {
                                    bookTriggerW = with(density) { it.size.width.toDp() }
                                }
                            ) {
                                // 11-14：触发框改 PickerTrigger（参照 GitHub 的 main 按钮：
                                // 圆角描边 + 内衬 + ▾，让"能点"一眼可辨）。
                                PickerTrigger(
                                    // 11-14 用户令：「把书名号删掉，前面改为书图标」。
                                    // 书名号占了 2 个字宽（《》各约 11.5dp，合计≈23dp），
                                    // 而"这是一本书"靠图标表达更快——省宽又更直观。
                                    text = shownBook,
                                    leadingIcon = Icons.Default.Book,
                                    stretchText = true,
                                    onClick = { bookMenu = true },
                                    // 11-14：筛选态也改 onSurface（与未筛选态同色）——
                                    // 两态切换只该变底/描边，文字色跳变会显得控件"换了个东西"
                                    textColor = MaterialTheme.colorScheme.onSurface,
                                    opened = bookMenu,
                                    // 撑满 weight 槽（同未筛选态，见那边注释）
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                // 紧凑菜单（用户令「把这个框小一点……得和谐呀」）：
                                // MD3 DropdownMenu 默认项 48dp 高/16sp 字，相对吸顶 11sp 小字巨大。
                                // 自定义项：11.5sp、36dp 高、内衬 12×6，与吸顶区同视觉密度；
                                // 触发字本身不动（仍是 11sp 原样式，不加箭头/不加框）。
                                // 书名菜单**居左**（用户定稿）：锚点=书名文字小 Box，
                                // offset 归零即从文字左缘向右展开，与行首自然对齐。
                                // 11-14：改用 BookChapterPickerMenu（参照 GitHub 分支切换器：
                                // 搜索框 + 当前项打勾 + 标题行）。旧版是一长串无搜索的列表。
                                val bookMenuW = minOf(maxOf(bookTriggerW, 220.dp), screenW - 16.dp)
                                BookChapterPickerMenu(
                                    expanded = bookMenu,
                                    onDismissRequest = { bookMenu = false },
                                    // 11-15 定案：书菜单宽度=书框实宽（≥220dp 保底、≤屏宽−24），
                                    // 卡片左缘=书框左缘（offsetX 默认 0）——上下合成一体。
                                    menuWidth = bookMenuW,
                                    items = knownBooks.map { b ->
                                        Triple(
                                            b,
                                            null,
                                            b == shownBook,
                                        )
                                    },
                                    onPick = { b ->
                                        bookMenu = false
                                        onSwitchBook(b)
                                    },
                                    onClearItem = { b ->
                                        bookMenu = false
                                        onClearBook(b)
                                    },
                                )
                            }
                            if (chapLabel.isNotBlank()) {
                                // 章名下拉同理：锚点只贴触发框，菜单从它右缘展开
                                // 章名宽度上限 1/3（与未筛选分支同口径，见那边注释）：
                                // 两态切换时章名框不会忽然宽窄跳一格
                                var chapTriggerW by remember { mutableStateOf(0.dp) }
                                Box {
                                    PickerTrigger(
                                        text = chapLabel.trim() + playingProgressSuffix,
                                        onClick = { chapMenu = true },
                                        // 同上：筛选态与未筛选态同色
                                        textColor = MaterialTheme.colorScheme.onSurface,
                                        opened = chapMenu,
                                        // 11-15：量章框实宽（widthIn 上限生效后的宽），
                                        // 供菜单右缘对齐用。
                                        modifier = Modifier.widthIn(max = chapMaxWidth)
                                            .onGloballyPositioned {
                                                chapTriggerW = with(density) { it.size.width.toDp() }
                                            },
                                    )
                                    // 章名菜单**居右**（用户定稿）：章名在书名之后、靠行右侧，
                                    // 若仍从左缘展开，220dp 宽会顶出屏幕右沿（用户原话
                                    // 「如果居下，位置应该也不够吧」）。故把锚点左缘向左推
                                    // 一个菜单宽度减去触发区宽度的距离，让菜单**右缘贴着触发文字右缘**。
                                    // 用 offset 的 x 负值实现（MD3 DropdownMenu 没有 alignment 参数）。
                                    // 11-14：改用 BookChapterPickerMenu（与书名菜单同款：
                                    // 标题行 + 当前项打勾；不放假搜索框——用户明确说不需要）
                                    val chapMenuW = minOf(maxOf(chapTriggerW, 220.dp), screenW - 16.dp)
                                    BookChapterPickerMenu(
                                        expanded = chapMenu,
                                        onDismissRequest = { chapMenu = false },
                                        // 11-15 定案：章菜单右缘=章框右缘（卡片左缘左推
                                        // anchorW−menuW）；宽度仍 220dp 起（章框本身窄，
                                        // 只有长章名时才跟着变宽）。
                                        menuWidth = chapMenuW,
                                        offsetX = chapTriggerW - chapMenuW,
                                        items = chaptersOf(shownBook).map { (name, count) ->
                                            Triple(name, count, name == shownChapter)
                                        },
                                        onPick = { name ->
                                            chapMenu = false
                                            onSwitchChapter(shownBook, name)
                                        },
                                        onClearItem = { name ->
                                            chapMenu = false
                                            onClearChapter(shownBook, name)
                                        },
                                    )
                                }
                            }
                            // 「取消」与书名之间一道浅竖线分隔（视觉上表明这是两段，不是书名的一部分）
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .height(10.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "取消",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(onClick = onClearScope)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    } else {
                        // ---- 未筛选：灰字 + 下拉箭头（11-14 用户实报「书籍名和章节名的样式
                        // 看不出来能点击选择」）----
                        //
                        // 根因：这两处是纯 Text + clickable，**零可点线索**——颜色与其它
                        // 说明文字一模一样，没有任何箭头/下划线/底色，肉眼看就是死文字。
                        // 现在给每个可点项挂一枚 12dp 的 KeyboardArrowDown（与筛选胶囊同灰档），
                        // 不加重底色（11-11 定稿「未筛选=纯信息，无底无绿」仍然成立），
                        // 只加"能展开"的通用符号，一眼可辨且不喧宾夺主。
                        //
                        // 菜单位置（用户两轮反馈定稿）：
                        //   第一轮「卡片位置尤其不对，得居右吧，跟标题很割裂」→ 曾想用
                        //   alignment=TopEnd，但 MD3 DropdownMenu **没有这个参数**（CI 实锤）。
                        //   第二轮「书名的弹窗应该居左，章节名弹窗应该居右」→ 定了方向：
                        //   · 书名在行首 ⇒ 菜单从**左缘**展开（锚点就是书名文字，天然居左）；
                        //   · 章名在书名之后、靠行尾 ⇒ 菜单要让开书名，从**右缘**展开；
                        //   · 用户还提醒「如果居下，位置应该也不够吧」——菜单是向下弹的，
                        //     章名在行的右侧，若仍从左缘起，220dp 宽会超出屏幕右沿，
                        //     故章名菜单必须右对齐（offset 负数把左缘往左推）。
                        // 做法：锚点小 Box + AppDropdownMenu 的 offset（唯一可用手段）。
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var bookMenu2 by remember { mutableStateOf(false) }
                            var chapMenu2 by remember { mutableStateOf(false) }
                            // 锚点=只包住触发文字的小 Box（见上方长注释：
                            // MD3 DropdownMenu 无 alignment 参数，位置只跟父布局节点走）。
                            // ⚠️ weight 挂 Box（同上方注释）：Box 在内层是 BoxScope 没有 weight。
                            // weight(1f) + fillMaxWidth（11-14 晚用户令「书名和章节名的框要把
                            // 整行撑满，而不是右侧留空」）：书名框吃掉行内剩余宽度，
                            // 与章名框合起来正好撑满整行。短书名时框照样宽、文字靠左——
                            // "撑满"是**框**的规矩，不是文字的宽。
                            // 11-15：量书框实宽（与筛选态同口径），弹窗宽度跟它一致。
                            var bookTriggerW2 by remember { mutableStateOf(0.dp) }
                            Box(
                                Modifier.weight(1f).onGloballyPositioned {
                                    bookTriggerW2 = with(density) { it.size.width.toDp() }
                                }
                            ) {
                            // 11-14：触发框改 PickerTrigger（带描边，见组件注释）
                            PickerTrigger(
                                // 同上：书名号 → 书图标
                                text = shownBook,
                                leadingIcon = Icons.Default.Book,
                                stretchText = true,
                                onClick = { bookMenu2 = true },
                                opened = bookMenu2,
                                // 撑满 weight 槽：PickerTrigger 的 Row 是 wrap content，
                                // 不显式 fillMaxWidth 时短书名会让框缩回文字宽度、右边留白
                                modifier = Modifier.fillMaxWidth(),
                            )
                            // 书名菜单居左（锚点小 Box + offset 归零，从左缘展开）
                            val bookMenuW2 = minOf(maxOf(bookTriggerW2, 220.dp), screenW - 16.dp)
                            BookChapterPickerMenu(
                                expanded = bookMenu2,
                                onDismissRequest = { bookMenu2 = false },
                                // 同筛选态：宽度=书框实宽，卡片左缘=书框左缘。
                                menuWidth = bookMenuW2,
                                items = knownBooks.map { b ->
                                    Triple(b, null, b == shownBook)
                                },
                                onPick = { b ->
                                    bookMenu2 = false
                                    onSwitchBook(b)
                                },
                                onClearItem = { b ->
                                    bookMenu2 = false
                                    onClearBook(b)
                                },
                            )
                            }
                            if (chapLabel.isNotBlank()) {
                                // 11-14 晚定稿（两条要求合并，用户「以章节的完整度为先，因为它字数少，
                                // 剩下的就都是书名的位置」+「结合那个 1/3 的限制，各种情况都要灵活变动」）：
                                //   ① 章名框 = **自身宽度**，一、俩字也不拉伸成固定宽——"第9章"
                                //      就只占四个字，书名因此能多拿宽度；
                                //   ② 但要有**上限**：行可用宽的 1/3。章名一旦是长标题
                                //      （"第9章 他把她锁进了柴房"）就会无限吃宽、把书名挤到只剩图标，
                                //      这时才截断省略。上限是兜底，不是常态宽度；
                                //   ③ 书名框 weight(1f, fill=false) 吃剩余宽度，装不下才省略；
                                //   ④ 两框由父 Row 的 2dp 间距连成一条，宽度随内容灵活变，但不分离。
                                var chapTriggerW2 by remember { mutableStateOf(0.dp) }
                                Box {
                                // 11-14：章名触发框同款（带描边）
                                PickerTrigger(
                                    text = chapLabel.trim() + playingProgressSuffix,
                                    onClick = { chapMenu2 = true },
                                    opened = chapMenu2,
                                    // 上限只作用在**框**：正常章名够不到它，长章名才在这里省略
                                    // 11-15：同时量实宽，供菜单右缘对齐。
                                    modifier = Modifier.widthIn(max = chapMaxWidth)
                                        .onGloballyPositioned {
                                            chapTriggerW2 = with(density) { it.size.width.toDp() }
                                        },
                                )
                                // 章名菜单居右（同筛选态：offset 左推让右缘贴触发文字）
                                val chapMenuW2 = minOf(maxOf(chapTriggerW2, 220.dp), screenW - 16.dp)
                                BookChapterPickerMenu(
                                    expanded = chapMenu2,
                                    onDismissRequest = { chapMenu2 = false },
                                    // 同筛选态：卡片右缘=章框右缘。
                                    menuWidth = chapMenuW2,
                                    offsetX = chapTriggerW2 - chapMenuW2,
                                    items = chaptersOf(shownBook).map { (name, count) ->
                                        Triple(name, count, name == shownChapter)
                                    },
                                    onPick = { name ->
                                        chapMenu2 = false
                                        onSwitchChapter(shownBook, name)
                                    },
                                    onClearItem = { name ->
                                        chapMenu2 = false
                                        onClearChapter(shownBook, name)
                                    },
                                )
                                }
                            }
                        }
                    }
                }
            }
            }

            // 11-14 用户令「别加筛选书和跳章节了，书名和章节名做成能跳转的形式」：
            // 原「筛选书」「切换章」两颗 PinnedChip 整撤——开弹窗的职责移到
            // 书名/章名文字的点击上（见上方两分支）。弹窗本体、行为、筛选逻辑零改动。
            //
            // 「取消筛选」仍由已筛选态书名旁的「取消」承担（11-11 选 B 方案：一个动作一个入口）。
            // 注：「取消筛选」由左侧胶囊里的「取消」承担，不再单独放按钮
            //（用户 11-11 选 B 方案：一个动作一个入口）。
        }

        // ② 筛选开关行（11-16 换位后为吸顶第二排）
        //
        // ⚠️ 11-11 用户令「我点开之后就要正式的界面，第一行第二行一共两行筛选，
        // 而不是左滑之后才会出现」——这一排**不许再横滑**，五颗必须在 360dp 内全部可见。
        //
        // 原实现是 horizontalScroll + 每颗 widthIn(min = 62.dp)：
        // 62×5 + 5×5 间距 + 标签 44 + 左右内衬 24 ≈ 412dp，屏只有 360dp，
        // 于是最后一颗（当时是「DEBUG」）永远被切在外面，用户得横滑才看得见（他实报的那一幕）。
        // 右缘渐隐只是"提示还能滑"，治不了"按钮不在屏幕上"。
        //
        // 现在：等分宽度。整排（标签 + 5 颗 + 间距）按剩余宽度平均分给 5 颗，
        // 每颗在 360dp 屏上约 55.8dp，四字标签 44dp 文字 + 10dp 内衬 = 54dp，刚好装得下。
        // 短标签（插件）随等分变宽，整排左右对齐、不再参差，也不再需要最小宽度撑齐。
        // 屏更窄（如 320dp）时靠 weight 继续等比压缩，仍然全部可见，不会溢出。
        // ⚠️ 11-11 用户令「能放下就放一屏，挤挤就是了」：
        // 上一版按 11dp/字估宽度**估错了**——真机截图实测 11sp 汉字是 12.28dp
        // （172px / 3.5 / 4 字），四字标签要 49.14dp，加 5dp 内衬 = 59.14dp，
        // 而等分只剩 55.2dp ⇒「朗读规则」照旧会被省略号吃掉。
        // 现在把边距收紧（外衬 24→16、间距 3→2、内衬 5→4），每颗能分到 57.8dp，
        // 比需要的 57.14dp 多 0.66dp，五颗才真的全部完整显示。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 外衬 24→16：左右各收 4dp（删掉 DEBUG 后每颗已很宽裕，这两处保留不动）
                // 11-14 用户令（看关系而非只统一数值）：「筛选这个按键左边距顶格排是不是更
                // 合适？」——是。这排是 fillMaxWidth 的**等分工具条**，整条本身就是页面的
                // 工具栏，不参与下方内容的竖线；硬拉到 16dp 内容线反而像"被缩进一截的
                // 按钮条"，左侧多出 6dp 空白显得多余。故取 8dp「接近顶格」：
                // 比内容线更靠外（读作工具条），又不像 0 那样贴死屏幕边。
                // 左右同为 8dp，四颗胶囊等分后每颗约 69dp（四字仅需 56dp，余量充足）。
                // bottom 5→9：与「书章/进度」拉开分组（间距账见书章行注释）。
                // 11-16 用户令「筛选上、书章下换过来」：这排现在是吸顶**第二排**。
                // 间距账（换位后重排）：
                //   书章行 bottom0 + 本排 top9 = 9dp（书章与筛选的组间隔）；
                //   本排 bottom6 + 合成行 top1 = 7dp（筛选与合成成一组）。
                // 左右仍是 8dp，与书章框左缘同线（11-14 定的吸顶控件带口径不变）。
                .padding(start = 8.dp, end = 8.dp, top = 9.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            // 11-17 用户令「均匀分布」：删掉混入组/筛错误/最新之间的两枚 10dp 组间距
            // Spacer——四颗全是 weight(1f) 等宽，唯一不匀的就是组间距把空隙撕成
            // 3/13/13dp。现在整排统一 3dp，视觉均匀。
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // 11-11 用户令：这排到底是什么，得有个名分。
            // 左侧「筛选」标签自带图标 + 一道竖线分隔，把「这是筛选器」讲清楚；
            // 竖线只留这一道——胶囊之间不再插分隔线（用户：「后边就不要有分隔线，
            // 不然就有点儿区分不开」：一道线是「标签｜选项」的分界，两道以上就成了装饰噪音）。
            // ===== 11-14 用户实报「胶囊以前是合理间距、宽度也一致，你咋改成这样了」=====
            //
            // 根因（考古确证）：0ebaccca 给这排加第 5 颗（「最新日志」）时，
            // 同时把**等分 weight 方案整排换成 horizontalScroll**（理由=横滑与 weight 互斥），
            // 于是四颗胶囊从「等分一屏、左右对齐」退化成「固有宽度、挤在左边、宽窄参差」。
            // 用户要的是原来的等分观感，不是能左滑。
            //
            // 现改回等分 + 去掉横滑：4 颗全靠 weight 平分剩余宽度
            //（360dp 屏约 69dp/颗，四字「朗读规则」13sp 实测 56dp + 内衬 12dp = 68dp，
            // 刚好装下；插件/错误/最新两字更宽松）。窄屏（320dp）等比压缩，
            // 靠 Ellipsis 兜底不溢出。
            // ⚠️ 别再套回 horizontalScroll——它会解除 weight，胶囊立刻又变参差。
            // 11-16 晚语义分工保留：「错误」=筛出（收窄），「朗读规则/插件」=混入
            // （往正文日志里加）。
            // 11-17 用户令：胶囊改名（+规则日志→朗读规则、+插件日志→插件、
            // 筛错误→错误），去「+」前缀——混入/筛出的区分由互斥逻辑承担，
            // 不再靠文案前缀。
            PinnedChip(
                label = "朗读规则",
                selected = showRule,
                onClick = onToggleRule,
                modifier = Modifier.weight(1f),
                horizontalPadding = 6.dp,
            )
            PinnedChip(
                label = "插件",
                selected = showPlugin,
                onClick = onTogglePlugin,
                modifier = Modifier.weight(1f),
                horizontalPadding = 6.dp,
            )
            PinnedChip(
                label = "错误",
                selected = errorOnly,
                onClick = onToggleErrorOnly,
                error = true,
                modifier = Modifier.weight(1f),
                horizontalPadding = 6.dp,
            )
            // 11-16 晚：滚动三态「跟随最新」模式胶囊（用户定稿：放本排**最右侧**）。
            // 选中=强制固定日志底部（新日志到达自动滚底，拖拽不退出）；取消=回跟随朗读。
            // 与 ↓ 浮钮（跳最新一次的正交链）区分。
            PinnedChip(
                label = "最新",
                selected = latestMode,
                onClick = onToggleLatest,
                modifier = Modifier.weight(1f),
                horizontalPadding = 6.dp,
            )
            // 11-15 用户令：删掉「最新日志」胶囊。
            //
            // 理由（用户原话）：「如果说是跳转的话，放到胶囊这个地方就不合适，
            // 而且日志栏下方不是有那个向下的箭头吗？点击一下就到最新了」。
            // 判断成立，且比修开关更干净：
            //   · 语义错位——这排是**筛选开关**（选中的各行才显示），"跳到最新"是
            //     一次性**导航**动作，不该占筛选位；
            //   · 功能本来就半废——勾上它滚到底，可「正在朗读」滚动跟随（11-15 加）
            //     会在朗读段一变时把视口拉回耳朵位置，两个效应抢视口，永远跟不住；
            //   · 替代入口本来就有——右下角 ⇊「跳到底部」浮钮（本批同时修好它
            //     在朗读中也出现，见 LogScreen 里 visible 的注释）。
            // 连带撤掉的还有：本组件 autoFollow/onToggleAutoFollow 两个参数、
            // LogScreen 的 autoScrollToBottom 参数、ViewModel 的状态位、LogFilterDialog
            // 里那颗「实时显示最新日志」开关——全链已无引用点。
        }
        // ===== ③ 合成进度行（11-16 换位后为吸顶第三排，紧贴日志列表）=====
        //
        // 三个数字，各答一个问题：
        //   《第六章》          ← 正在合的是哪一章（预取可能已跨章，播放章≠合成章）
        //   多合 5 段           ← 合成前沿领先播放位置多少段（你最关心的那个数：
        //                          它越大，你接下来随便跳都不用等）
        //   正在合第 3 段       ← 该章内合成进行到的段号（体感"快好了没有"）
        //
        // 数据：章名与段号来自快照（U阅 写的合成侧信息——注：快照是播放侧，
        // 合成侧章/段由 UTTS 自己从 in-flight 请求行提取，见 synthesizedAhead 计算）。
        // 没在朗读整行隐藏。
        playingSnap?.let { snap ->
            // 用户定稿格式（融合旧 1/32 与章名双信息）：
            //   跨章预取（合成章≠播放章）：「后台合成 第6章 1/35（当前：第5章3/25）」
            //   同章（常态）：                「后台合成 第6章 3/25」
            // 括号段=播放位置（用户耳朵所在），括号外=合成前沿——一眼看出领先了多少。
            // 分子=该章已完成段数（按正文前缀匹配数过），分母=该章总段数。
            val synthIdx = snap.synthChapterIndex
            val synthName = shortChapterLabel(snap.synthChapterTitle)
            val playName = shortChapterLabel(snap.chapterTitle)
            // 合成章已完成数：以「正在朗读丸+成功」的近似口径计——直接数该章内
            // 正文前缀已出现过请求行的段数上限不可靠，改用前沿段号+1（前沿之前的
            // 都提交过了；跨章预取按章内顺序合，段号即进度）。
            val synthDone = (snap.synthSegIndex + 1).coerceAtLeast(0)
            val synthTotal = snap.synthTotalSegs
            val playDone = (snap.segIndex + 1).coerceAtLeast(0)
            val playTotal = snap.totalSegs
            // 用户定稿两分支：
            //   同章：后台合成 3/25        ← 书章行已显示当前章，第二行不重复
            //   跨章：后台合成 第6章 1/35  ← 括号不再带播放进度（播放进度已在书章行的（3/25）里）
            // 11-14 用户令（② 定稿）：「那就不要当前朗读了，只留合成进度，把后台合成
            // 改为这个，不管是否跨章，都要加章节名」。
            //
            // 定稿格式（单段，不再并列播放位置）：
            //   合成进度：第六章 10/20
            // 要点三条：
            //   ① 「后台合成」→「合成进度」（用户 11-14 纠正：「②文字是合成进度啊」：
            //      这一行报的是合成这件事，前缀就该点名"合成进度"，不是只写"合成"）；
            //   ② **恒带章名**（不再分同章/跨章两套）：用户明确「不管是否跨章都要加」。
            //      同章时章名与书章行重复，但用户要的是"一眼看出合成到哪一章"，
            //      重复可接受；
            //   ③ 播放位置彻底移除（用户：「不要当前朗读了」）——播放进度看段号
            //      与书章行的读数即可，第二行只负责"合成到哪了"。
            //
            // 11-14 修「第 0 章」（保留）：synthChapterIndex 未上报时是 -1，
            // 旧代码 `第${-1+1}章` 会印出「第 0 章」。现在索引无效就不写章号，
            // 只写进度——宁缺勿错。
            val synthIdxValid = synthIdx >= 0
            val synthNameOrNum = when {
                synthName.isNotBlank() -> synthName
                synthIdxValid -> "第${synthIdx + 1}章"
                else -> ""
            }
            // ===== 11-16 02:2x 用户定稿格式 =====
            //   同章：当前：62/362｜缓存：68/362        （同章**不写章名**）
            //   跨章：当前：第二章 62/362｜缓存：第三章 5/40（两侧都写，否则分不清归属）
            //   停止：缓存：第二章 68/362               （「当前」整段消失、**靠右**、
            //                                            并无条件带章名——见下方 synthHead 注释）
            // 与旧版的差别（用户逐条确认）：
            //   ① 标签从「正在播放/合成进度」压成「当前/缓存」——两个字，省一半宽；
            //   ② 同章不重复章名（书章行已经写着当前章），跨章才两边都补，
            //      避免「62/362 到底是哪一章的」这种歧义；
            //   ③ 停止朗读时只留后半段，后半段**居右**（TextAlign.End 而不是留一大片空白在左）。
            //      11-16 用户令「合成日志都居右放」：朗读中也一律居右，两态统一。
            //      不再按 playingNow 切换对齐（见下方 progTextAlign）
            val playingNow = snap.playing
            val playSegText = if (playTotal > 0) {
                playDone.coerceAtMost(playTotal).toString() + "/" + playTotal
            } else ""
            val playNameOrNum = when {
                playName.isNotBlank() -> playName
                snap.chapterIndex >= 0 -> "第${snap.chapterIndex + 1}章"
                else -> ""
            }
            // 跨章判定：章号都有效且不同（章号缺失时按"同章"处理，宁可不写重复章名）
            val crossChapter = snap.synthChapterIndex >= 0 &&
                snap.chapterIndex >= 0 && snap.synthChapterIndex != snap.chapterIndex
            val playHead = buildString {
                if (playNameOrNum.isNotBlank() && crossChapter) {
                    append(playNameOrNum).append(' ')
                }
                append(playSegText)
            }
            val synthHead = buildString {
                // 11-16 02:3x 用户二次澄清：「停止朗读了，要显示第x章，不分是否跨章」。
                // 所以「缓存」侧的章名条件是 **crossChapter || !playingNow**：
                //   · 在朗读且同章 → 省略（书章行已写着当前章）；
                //   · 跨章 → 写（否则分不清数字归属）；
                //   · 已停止 → 一律写（此时没有「当前」可比，不写就不知道缓存停在哪一章）。
                if (synthNameOrNum.isNotBlank() && (crossChapter || !playingNow)) {
                    append(synthNameOrNum).append(' ')
                }
                if (synthTotal > 0) {
                    append(synthDone.coerceAtMost(synthTotal)).append('/').append(synthTotal)
                }
            }
            // 竖线两侧**不留空格**（用户给的样例就是 1/25｜缓存）；全角竖线本身自带间距。
            // 11-18 段号突出：曾有「分子半粗、分母常规」的字重分层方案；11-19 用户令
            // 整段统一（含分母同字重），字重分层取消。11-20 定档 500 Medium（见 boldSeg）。
            // 当前侧与缓存侧同口径（用户令两侧统一）。
            // 用 AnnotatedString 局部设置，不拆多 Text（保持单行居右 + Ellipsis 整体行为）。
            // 11-19 晚用户令「段数换字体，顶部播放和缓存那一行也要改」→ 同一个
            // SegNumberFont（IBM Plex Mono）。**只作用于 N/M 这一段**：
            // 参数名虽叫 numerator，实际调用方传的是整个「133/389」（含分母）——
            // 与日志行右侧段号同口径（11-19 起整段同字重）。
            // 11-20 装机实拍 600「太重」→ 用户选定降一档 **500 Medium**，
            // 与 LogScreen.kt 段号同步改；SpanStyle 必须与 Type.kt 里 Font()
            // 的登记（Medium）对上，否则叠合成伪粗。
            // 中文「当前：」「｜缓存：」「第12章」不在这个 span 内，仍走系统字体；
            // 即便误入也不会变豆腐块——Plex 子集没有的字形会自动回落。
            fun boldSeg(numerator: String) = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontFamily = SegNumberFont,
                        fontWeight = FontWeight.Medium,
                    )
                ) { append(numerator) }
            }
            // ===== 11-22 00:3x 用户令「把缓存进度弄到明显一点」=====
            //
            // 旧口径：缓存进度与播放进度挤在同一行（「当前：95/110｜缓存：107/110」），
            // 两者字号、颜色、字重完全同档，只靠一个「｜」分隔。用户反馈"不明显"——
            // 确实如此：这行里真正回答"我接下来能不能秒播"的是**缓存**那一半，
            // 播放进度只是"我听到哪了"，而旧版把二者做成同权重。
            //
            // 新口径（只加重、不换行、不改数据源）：
            //   ① 缓存那一半整体**提一档字重**（Medium）并换成主题绿 primary——
            //      绿色在这一页的语义就是「已完成/可用」（成功丸、胶囊选中同色系），
            //      "已缓存"正是这个语义，用它做区分既醒目又不引入新颜色；
            //   ② 缓存侧数字用 SegNumberFont + 加粗，与段号同口径（用户 11-19 定的
            //      「段数换字体」在两步之内的延续）；
            //   ③ 播放侧（「当前」）保持原来的灰 + 常规字重——它不需要抢眼。
            // 不改行高、不改对齐、不改 Ellipsis 行为：单行 AnnotatedString 局部加样式。
            val cacheColor = MaterialTheme.colorScheme.primary
            fun cacheSeg(numerator: String) = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontFamily = SegNumberFont,
                        fontWeight = FontWeight.SemiBold,
                        color = cacheColor,
                    )
                ) { append(numerator) }
            }
            val text = if (playingNow && playHead.isNotBlank()) {
                buildAnnotatedString {
                    append("当前：")
                    // playHead 可能带跨章章名前缀（「第2章 33/89」）：章名常规，分子才半粗
                    val sp = playHead.indexOf(' ')
                    if (sp > 0) {
                        append(playHead.substring(0, sp + 1))
                        append(boldSeg(playHead.substring(sp + 1)))
                    } else {
                        append(boldSeg(playHead))
                    }
                    append("｜")
                    // 11-22：「缓存」标签本身也染绿加粗，让右半边整块跳出来
                    withStyle(
                        SpanStyle(fontWeight = FontWeight.Medium, color = cacheColor)
                    ) { append("缓存：") }
                    val spS = synthHead.indexOf(' ')
                    if (spS > 0) {
                        append(synthHead.substring(0, spS + 1))
                        append(cacheSeg(synthHead.substring(spS + 1)))
                    } else {
                        append(cacheSeg(synthHead))
                    }
                }
            } else {
                buildAnnotatedString {
                    // 不在朗读时整行只剩缓存——此时它是唯一信息，同样染绿加粗
                    withStyle(
                        SpanStyle(fontWeight = FontWeight.Medium, color = cacheColor)
                    ) { append("缓存：") }
                    val spS = synthHead.indexOf(' ')
                    if (spS > 0) {
                        append(synthHead.substring(0, spS + 1))
                        append(cacheSeg(synthHead.substring(spS + 1)))
                    } else {
                        append(cacheSeg(synthHead))
                    }
                }
            }
            // 11-16 用户令「合成日志都居右放」：**一律居右**，不再分朗读中/停止两态
            // （原先朗读中左对齐、停止才居右）。文字内容不变，「当前：…｜缓存：…」
            // 朗读中照旧整段显示，停止时只剩「缓存：…」——都靠右。
            val progTextAlign = TextAlign.End
            // 合成侧完全没数据（没开播/未上报）时这一行**整行不渲染**——否则只剩
            // 「合成进度：」四个字挂在那里，没有信息量还占 24dp。
            // ⚠️ 必须真用上这个门控（11-14 自查发现：上一版只声明没使用）。
            // 顺带影响：书章行 bottom 已改成 0（为与进度行贴成一组），
            // 若此时仍渲染空进度行不会有问题；但若整行隐藏，书章行会直接贴在
            // 列表首行上——故隐藏时把书章行的呼吸补回来，见下方 else 分支。
            // 11-15：整行存在性只看**合成侧**有没有数据——用户令「停止朗读时，合成进度行
            // 照样显示」，所以不能再因为 playing=false 就整行收起。
            val showProgLine = synthTotal > 0 || synthNameOrNum.isNotBlank()
            if (showProgLine) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 11-15 用户令：「合成进度以及下方的日志都要左右边距改为 8dp，
                    // 就是跟顶部一样」→ 16/16 改 **8/8**，与书章框左缘同线。
                    // 原注释（11-14 的「归到内容线 16」）随之失效：内容线本身已整体
                    // 收到 8（见 LogScreen 裸行/组卡/日期签注释），进度行不再当孤例。
                    // 11-16 换位后这行沉到**第三排**、紧贴日志列表：top 与上方筛选行
                    // 成一组（筛选 bottom6 + 本行 top1 = 7dp），bottom 留呼吸与列表分开。
                    .padding(start = 8.dp, end = 8.dp, top = 1.dp, bottom = 7.dp),
                // 11-16：整行靠右 = 点 + 读数作为一个整体贴在右缘（原先 Text 独占
                // weight 会把点甩到最左边、和读数中间空半屏）。改成
                // Arrangement.End，点跟着文字走，读起来就一句「● 当前：…｜缓存：…」。
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 11-16 用户令（第三轮定稿）：「在**前面加一个绿色点**表示提示，
                // 字体颜色就按照筛选行字体颜色」。
                //
                // 为什么加点是正解、而前两轮「提黑/深绿」都不对：
                //   · 提黑（A）= 跟书章抢同一档；书章是「身份」，进度是「状态」，
                //     同一黑会把吸顶三行的层级压平。
                //   · 深绿（C）= 整行染绿，但这页已满页绿（成功丸、胶囊选中、
                //     目录键），再来一行绿会被读成「又一个状态」。
                //   · **点**只占 5dp、只表达「这行是活的」，不改变字的灰度——
                //     字色归队筛选行（78% 灰），身份靠一枚点单独表达，两件事分开。
                //
                // 点的规格（与全站圆点同口径：见本页 1462 的时间轴圆点、LogScreen 的
                // 成功丸前置点，都是 5dp + CircleShape）。动态口径见下方 if/else 注释
                // （播放=呼吸绿，停止=灰点静止）。
                // 位置：Row 内居中对齐（verticalAlignment=CenterVertically），
                // 与文字同一视觉中线；右侧留 5dp（点 5 + 间隙 5 ≈ 一个字的半宽，
                // 不会跟「当前：」贴死，也不至于看起来像两个独立元素）。
                // 11-16 晚用户令（推翻我按静态 A 案的落定）：「正在合成和缓存那一行
                // 绿点不是动态点吗？你预览都出了，怎么现在是静态点？」——落回预览 B 案：
                //   · 播放中：绿点呼吸（rememberInfiniteTransition，700ms 去回 ≈1.4s 一个
                //     来回，透明度 1→0.3→1）。动画只挂在播放态的 Composable 上，切到停止
                //     分支即离开组合，动画随之销毁，不耗帧。
                //   · 停止：灰点静止（onSurface@38%）。不选「停止无点」的 C 案：留一颗
                //     灰点行高不变、左右不跳位，且保留「流水线还在、只是没在播」的暗示；
                //     要换 C 案删 else 分支即可。
                //   · ⚠️ 呼吸用 alpha 而不是改尺寸/颜色：改尺寸会牵动行内基线，改颜色
                //     要插值两个 Color；alpha 只动透明度，GPU 合成最便宜。
                //   · 呼吸条件=dotActive（15s 活跃门，11-16 晚用户拍板），不是 playingNow：
                //     后者来自 readRaw 的 30min 宽限，U阅 被杀后会假呼吸；前者 15s 内没
                //     心跳就变灰。进度行文字仍按 playingNow（停止后照显示口径不动）。
                if (dotActive) {
                    val dotTransition = rememberInfiniteTransition(label = "progDot")
                    val dotAlpha by dotTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 700, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "progDotAlpha",
                    )
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha), CircleShape)
                    )
                } else {
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), CircleShape)
                    )
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    // 11-16 用户令「字号跟筛选行统一」：12.5 → **12sp**，
                    // 与筛选标签、胶囊同档（理由：这行是第二排的读数，不该比
                    // 它自己那一排的标题大半档；旧 12.5 是 11-15「介于 12/13」的残留）。
                    fontSize = 12.sp,
                    // 11-16 用户令「字体颜色就按照筛选行」：改回 78% 灰
                    // （原 onSurfaceVariant #414941 与 78% ≈#4B4E4A 几乎同灰，
                    // 改后与筛选标签、胶囊、书章的灰阶彻底同源）。
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = progTextAlign,
                    // 交给 Arrangement.End 贴右；不再 weight 抢宽——抢宽会把
                    // 前面的绿点顶到行首，点与读数分家。
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            } else {
                // 没有合成数据：进度行整行隐藏，补一笔呼吸高度，
                // 否则筛选行会直接贴在日志列表首行的分隔线上。
                Spacer(Modifier.height(7.dp))
            }
        }

        // ===== ④ 跳转提示条：筛着 A 书、却在读 B =====
        // 用户：「如果正在朗读其他的再来一个跳转」。只在"有筛选 且 正在读的不是筛选的那本"时出现。
        val readingMismatch = !scopeBook.isNullOrBlank() &&
            readingBook.isNotBlank() && readingBook != scopeBook
        if (readingMismatch) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 11-15：12→8，与内容线同批对齐（见进度行注释）
                    .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
                    .clip(AppShapes.sm)
                    .background(Color(0xFFFFF7E3))
                    .clickable {
                        // 定案 A（用户 11-11）：清筛选 → 回到全部日志 → 开启实时最新 → 滚到底。
                        // 三条缺一不可：只清筛选不滚底，你看不到刚读的那几条；只滚底不清筛选，
                        // 列表里根本没有正在读的那本（被筛掉了）。
                        onJumpToLive()
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "正在朗读《$readingBook》${shortChapterLabel(readingChapter).let { if (it.isBlank()) "" else " $it" }}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.5.sp,
                    color = Color(0xFF7A5C00),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    // 11-11 用户指出：这个动作也是**跳转**（清筛选+回实时最新+滚过去），
                    // 不会开始朗读，所以不能叫"去听"。统一跳转语义：跳到这本。
                    text = "跳到这本 →",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.5.sp,
                    color = Color(0xFFA06A00),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * 章节标题 → 只留章号（11-11 用户令「章节可以显示，章节名就不用显示了吧」）。
 *
 * 例：「第6章 高山流水遇知音」→「第6章」；「第 12 章 某标题」→「第 12 章」。
 * 抠不到「第N章」时（如「序章」「番外·少年时」），取前 6 个字兜底——
 * 用户原话是章名不要显示，但完全不显示又丢了"在哪一章"的信息，故截短而非留空。
 * 空串进空串出。
 */
/** 标准章号：「第 85 章」「第500章」「第三章」「第 1 回」——只取到「章/回/节/卷/话/集/篇/部」为止。 */
private val CHAPTER_NO_RE = Regex("^第\\s*[0-9０-９一二三四五六七八九十百千零两]+\\s*[章回节卷话集篇部]")

/**
 * 裸数字章号：「0040 在变态这方面…」这类**没有「第X章」外壳**的编号章名。
 * 只认「数字 + 分隔符/行尾」，因此「1984年的夏天」这种以数字开头的真标题不会被误吃。
 */
private val BARE_NO_RE = Regex("^[0-9０-９]{1,5}(?=[\\s.、,，:：_\\-—·]|$)")

/**
 * 章名缩写（日志页吸顶区各处共用）：**只留章号，标题部分一律丢掉**。
 *
 * 用户 11-15 令：「章节框如果标准的话，有一个章节名加上后面的章节名称，比如第一章xxx，
 * 那这个框里只显示这个第一章，不要显示后面的内容。同样遇到这种同类的情况，也是只显示前半部分」。
 *
 * 三种来源（实机样本）：
 *  - `第 85 章 进入敌区` / `第500章 暗涌` / `第三章 自控：…` → `第 85 章` / `第500章` / `第三章`
 *  - `0040 在变态这方面，你不如我`（无「第X章」外壳的编号章名）→ `0040`
 *  - `序章` / `楔子` / `番外 一`（无编号）→ 原样保留
 *
 * 旧实现的问题：标准格式以外一律 `take(6)`，于是 `0040 在变态这方面…` 会截成
 * `0040 在`——章号后面仍拖着半个标题字，既不是章号也不是标题。现在按「编号」整体取，
 * 不再按字数硬切。
 *
 * ⚠️ 只用于**触发框/提示条上的缩写显示**；章节下拉菜单里必须仍用完整章名，
 * 否则几十章全叫「第85章」「第86章」，用户没法在列表里挑（见 BookChapterPickerMenu 调用处）。
 */
private fun shortChapterLabel(chapter: String): String {
    if (chapter.isBlank()) return ""
    val c = chapter.trim()
    CHAPTER_NO_RE.find(c)?.let { return it.value }
    BARE_NO_RE.find(c)?.let { return it.value }
    return c
}


/**
 * 书单 / 章节单下拉菜单（11-14 用户指定参照 GitHub 分支切换器改造）。
 *
 * 用户原话：「书籍和章节筛选和弹窗可以参照这个，我感觉他做的挺好的」，
 * 参照对象是 GitHub「Switch branches/tags」弹层。它好用的三个点正好是旧版缺的：
 *  1. **顶部搜索框**——旧版只有一长串可滚列表，这书 104 章、书架 37 本，
 *     找某章只能一路翻；现在打字即过滤（contains，忽略大小写）。
 *  2. **当前项打勾**——旧版看不出"我现在在哪本书/哪一章"，只能靠记忆比对；
 *     现在当前项前面 ✓、字色主题主色，一眼定位。
 *  3. **标题行 + 紧凑密度**——对应参照图的「Switch branches/tags」标题，
 *     条目仍 36dp/11.5sp（用户令「框小一点」的口径不变）。
 * 四处（筛选/未筛选 × 书名/章名）共用本组件，改一次全生效。
 */
@Composable
private fun BookChapterPickerMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<Triple<String, Int?, Boolean>>,
    onPick: (String) -> Unit,
    // 11-15 用户两条令（合并口径）：
    //   ① 书框弹窗「宽度跟书框宽度要和谐一点，宽度一致会更好吗」→ 宽度=书框实宽；
    //   ② 「弹窗现在你右移之后内部对勾要跟上方对齐」→ 卡片左缘=书框左缘，核对内衬。
    // 内衬为什么是 5dp（不是 8/14）——上方书框结构（见 PickerTrigger）：
    //   左内衬 6 → 书图标 14 → 3 间距 → 书名文字
    //   勾左缘 = 卡片左缘 + 5  ≈ 书图标左缘（6dp，差 1dp，同一竖列）
    //   条目文字左缘 = 5 + 勾位 18 = 23dp = 书名文字左缘（6+14+3），完全同线。
    // 章菜单仍居右：调用方传 offsetX = 章框实宽 − menuWidth，让卡片右缘=章框右缘。
    menuWidth: Dp = 220.dp,
    offsetX: Dp = 0.dp,
    // 11-16 用户定稿：红叉挂在**弹窗里每一行的最右侧**（每本书/每一章各一个），
    // 不是挂在吸顶的书框/章框上（那里一屏只有两个框，且更容易误触）。
    onClearItem: ((String) -> Unit)? = null,
) {
    if (!expanded) return
    androidx.compose.material3.DropdownMenu(
        expanded = true,
        onDismissRequest = onDismissRequest,
        // 宽度由调用方算好传入（11-15）：书菜单=书框实宽；章菜单=max(章框实宽,220dp)。
        // 用显式 width 而非 widthIn：菜单宽度必须与锚点宽度严格对上，否则右缘对齐
        // 全靠猜；220dp 只是二者共同的**下限**（用户「框小一点」的口径不变）。
        modifier = Modifier.width(menuWidth),
        // ===== 位置账（11-15 版，替代 11-14 的 −8dp 推导）=====
        // 定位基准是**卡片边线**，不是条目文字：用户 11-15 明确「应该跟书框左缘
        // 和章节框右缘对齐」。
        //   · 书菜单：卡片左缘 = 锚点左缘 ⇒ offsetX = 0；锚点就是包住书框的那个
        //     weight 槽，所以卡片左缘 = 书框左缘。勾/文字的落点见 menuWidth 注释。
        //   · 章菜单：卡片右缘 = 锚点右缘 ⇒ offsetX = 锚点宽 − 菜单宽（负值，向左推）。
        // y 仍归 0：弹窗顶边 = 书框底边（11-14 定案）。MD3 的天生起点就是
        // anchorBounds.bottom，**不要**再减 8dp——减了会把白卡上沿压进框里。
        // ⚠️ 别再沿用「offset=−8 让勾对齐框左缘」那套：那是拿**勾**当基准，
        // 卡片本身反而凸出框外 8dp，于是用户看到的两个弹窗都不贴框。
        // ⚠️ MD3 DropdownMenu 无 alignment 参数，offset 只按锚点左上角算。
        // 已核对 material3 1.4.0-alpha09 的 DropdownMenuPositionProvider：横向候选
        // =「start 对 anchor start → end 对 anchor end → 贴窗边」，前两档**不做
        // 边距钳制**——宽度不超窗时 offset 给多少卡片就落在哪，不会被挪动；
        // 调用方仍把宽度封在屏宽−16（行自身内衬 8+8），保证永远走前两档。
        offset = androidx.compose.ui.unit.DpOffset(offsetX, 0.dp),
        // 纯白卡片（surface）+ 12dp 圆角 + 轻投影：照 GitHub 那个弹窗的形态。
        // 不用 AppDropdownMenu——它写死了 surfaceContainerLow 灰白底，质感差一档。
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    ) {
        // ⚠️ 不加标题行（用户 11-14 质疑「有必要加选择书籍和选择章节这个标题吗」，
        // 结论=没必要，已删）。判断依据：
        //   · GitHub 那个弹窗（Switch branches/tags）需要标题，是因为它的触发器是
        //     **一个分支图标**，图标本身不带语义，得靠标题说明"点开是什么"；
        //   · 我们是**书名/章名文字本身**当触发器——点书名显然在选书，点章名显然
        //     在选章节，条目也全是同类内容，标题等于把刚说过的再说一遍；
        //   · 220dp 小弹窗里那行白占高度（标题 14dp+分隔线），把列表挤短 1~2 条，
        //     与用户「框小一点、别臃肿」的一贯口径冲突。
        // 同理保留「不放搜索框」（用户明说不需要）。
        Column(
            Modifier
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            items.forEach { (name, count, isCurrent) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.sm)
                        .clickable { onPick(name) }
                        // 左内衬 5dp（11-15）：勾左缘=卡片左缘+5 ≈ 书图标左缘 6dp，
                        // 条目文字=+23dp = 书名文字左缘（算式见 BookChapterPickerMenu 注释）。
                        // 右内衬同步 5dp，保证条目选中底色与卡片边距左右对称。
                        .padding(horizontal = 5.dp, vertical = 9.dp),
                ) {
                    // 打勾位（参照 GitHub 当前分支前的 ✓）：固定占宽 18dp，
                    // 非当前项也留白——不留白的话勾会顶得各条文字左缘参差。
                    Box(Modifier.width(18.dp)) {
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Text(
                        text = name,
                        fontSize = 12.sp,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // 11-16 用户令「章节弹窗右侧不要加那个多少条的提示」：
                    // 那一格改放红叉，条数不再显示（列表本就按它排，数字只是噪音）。
                    onClearItem?.let { clear ->
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清理这个范围的日志",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(15.dp)
                                .clickable(
                                    // 子级 clickable 先消费：点叉只清日志，不会连带选中该行
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { clear(name) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 书章行的「下拉触发框」（11-14 用户指定参照 GitHub 的 main 分支按钮）。
 *
 * 用户原话：「不需要搜索，我只说说 main 框和点开这个出来的弹窗框值得借鉴」。
 * 参照的 main 按钮有什么、这里就有什么：
 *  · **一圈圆角描边**——让"这是个能点的控件"一眼可辨。旧版是裸文字 + ▾，
 *    在 11sp 小字里和旁边的信息文字混成一片，用户实报「看不出来能点击选择」；
 *  · **内衬**——左右 6dp、上下 2dp，文字不贴边，形成可见的按钮体积；
 *  · **右侧 ▾**——与原实现一致，展开语义。
 * 描边取 outlineVariant（与筛选胶囊同档灰），不抢吸顶区的主次。
 *
 * 已筛选态仍用胶囊底（11-11 定稿 B 方案：胶囊=筛选生效的状态表达），
 * 此处只服务"未筛选/筛选态里各自的可点触发"，由调用方决定配色入参。
 */
@Composable
private fun PickerTrigger(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // 11-14 用户实报「书章现在是灰色，不太合适，改为黑色，跟请求音频的颜色一样」。
    // 采纳理由（不是照做）：
    //   · onSurfaceVariant（灰）在本页的语义是**辅助信息**（耗时/体积/发音人），
    //     而书名/章名是**导航**（点它要跳转），错用了低一级的颜色；
    //   · 它俩现在还是唯二带描边的可点控件——描边在说"能点"、灰字在说"别注意我"，
    //     两个信号打架；改 onSurface（正文同档）后自洽。
    // 11-14 曾落到「onSurface 78%」（当时筛选行还在书章上方，怕书章压过筛选）。
    // 11-16 用户令把筛选行调到书章下方之后，书章成为吸顶第一排的"主角"，
    // 且筛选行已单独保持灰档——层级条件成立，书章文字提到 100% onSurface
    // （你令「提黑」，不加字重，理由见调用处）。深色主题下 onSurface 自动变近白。
    // 注：用户说的「跟请求音频一样」指的是它**后面的正文黑**（#onSurface），
    // 不是「请求音频：」这个前缀——前缀是 10-08 用户拍板改的中性灰 #79747E。
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    bordered: Boolean = true,
    // 11-14 用户令（第三次澄清后定稿）：「1、2 说的都是一个意思，就是点击 main
    // 分支框这个框的变化，颜色可以看看，但不需要一样，因为咱们有自己的颜色」。
    //
    // 参照 GitHub 那个 main 按钮的行为（真机截图核对）：
    //   · 正常态：淡底 + 淡描边，安静待着；
    //   · 点开后：框本身给出反馈——底色加深、描边变清晰，像"活起来"了；
    //   · **形状、尺寸、位置全都不变**，只有颜色到位——所以点开不跳、不位移。
    // 颜色不照抄 GitHub 的冷灰蓝，改用本项目主题：展开时底色走 primary 的极淡混合、
    // 描边走 primary 半透明——与「正在朗读」左轨/筛选选中同源，不引入新色相。
    opened: Boolean = false,
    // 11-14 用户令：「把书名号删掉，前面改为书图标，章节不用加图标吧？」
    // 书名用图标（书=这是什么），章名不加（"第9章"文字本身已自解释，加图标是噪音）。
    // 图标尺寸跟箭头统一 14dp（用户同批要求「连箭头都统一」）。
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    // 11-15 用户令「书籍框下拉箭头没有在最右」：
    //   · 书框本身 fillMaxWidth，文字若 fill=false（按内容宽）箭头就紧贴文字，
    //     右边空一截——书框传 stretchText=true，文字吃满剩余宽度，箭头顶到框右缘；
    //   · 章框是自适应宽（widthIn 上限封顶），**必须保持 fill=false**：
    //     章框的 Row 没有固定宽，weight fill=true 会把整框撑到上限宽，
    //     "第9章"四个字也占满 1/3 行宽，与「章名框=自身宽度」的定稿冲突。
    stretchText: Boolean = false,
) {
    val shape = RoundedCornerShape(6.dp)
    // 11-14 晚（用户令「书籍和章节的填充底色应该跟顶部胶囊一样，点击时才变色」）：
    // 底色**归零**，常态只剩描边——与顶部未选中胶囊同款（透明 + outlineVariant 1dp）。
    // 旧版常态铺 surfaceVariant 45% 的灰底，等于"每颗胶囊都按下了"，
    // 与吸顶区"未选中=空心"的既定口径打架，也把书章框读成了灰控件。
    // 展开时才上底色＝**现在的这个灰**（surfaceVariant 45%，用户原话
    // 「点击时才变为现在的这个灰色」）；常态一颗透明框，与未选中胶囊同款。
    val bgColor = if (opened) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    } else {
        Color.Transparent
    }
    val borderColor = if (opened) {
        // 展开态：描边随底色一起变实（outlineVariant 满档），表达"这个框正连着弹窗"。
        // 不上主色：用户要的开态是那个灰，颜色不引入新的绿相。
        MaterialTheme.colorScheme.outlineVariant
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .then(if (bordered) Modifier.background(bgColor) else Modifier)
            .then(
                if (bordered) Modifier.border(
                    // 描边略微加粗（0.8→1.2dp）参与"展开了"的反馈，但幅度克制，
                    // 不做发光/阴影——吸顶区是信息区，不该有装饰性特效。
                    width = if (opened) 1.2.dp else 0.8.dp,
                    color = borderColor,
                    shape = shape,
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 3.dp, top = 2.dp, bottom = 2.dp),
    ) {
        // 前置图标（书名=书本图标）：放在文字之前，与文字同色系、略淡一档，
        // 避免图标比字还抢眼。尺寸 14dp 与右侧箭头一致。
        leadingIcon?.let { v ->
            Icon(
                imageVector = v,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(14.dp)
                    .padding(end = 0.dp),
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = text,
            // 11-15 随筛选行一起放大：11.5→**13sp**（= 日志裸行档）。
            // 书名/章号是每次都要用的选择器，11.5 太像说明文字。
            fontSize = 13.sp,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (stretchText) Modifier.weight(1f) else Modifier.weight(1f, fill = false),
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            // 展开时箭头也跟着主色，三处（底/描边/箭头）同源，整体是一件事
            tint = if (opened) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            else textColor.copy(alpha = 0.7f),
            // 11-14 用户令「14，连箭头都统一」：图标尺寸全站统一到 14dp。
            // 原 13dp 与筛选图标的 12dp 各差 1dp，肉眼看得出"两个图标不一样大"。
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * 吸顶筛选开关（11-11 用户令定稿）。
 *
 * **未选中 = 纯文字、无填充**：原实现未选中给 surfaceContainerLow 灰白底 + 半透明描边，
 * 五枚全都有底，装机上像「五个按钮全按下了」，读不出哪几个是开着的
 * （用户：「它不应该有填充色，我选中哪个…这个里面才便有填充」）。
 * 现在底色只在选中时才出现：正常档 = secondaryContainer 浅绿 + outlineVariant 描边、
 * 错误档 = errorContainer 淡红（保留排障语义）；未选中透明，靠文字色与选中态区分。
 */
@Composable
private fun PinnedChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    // 错误档：保留淡红填充 + 红字（11-11 用户令「只看错误填充色是红色，字号也是红色也行吧，
    // 要不就不改了」）。曾经尝试把它改成和其它筛选同款的浅绿选中态，用户否掉，
    // 排障语义（"红色=错误"）保留。
    error: Boolean = false,
    // 11-11 用户令「点开就要正式的界面，不要左滑才出现」：
    // 第一排改为等分宽度，由调用方传 Modifier.weight(1f) 分剩余宽度，
    // 传进来的那个 Modifier **不带** widthIn(min=62dp)——62dp 正是把这排撑到
    // 412dp、逼出横滑的原因（62×5 就 310dp，加标签与间距超过 360dp 屏宽）。
    // 第二排的「筛选书/切换章」不是等分的，仍靠 minWidth 撑成四字宽。
    modifier: Modifier = Modifier,
    minWidth: Dp = 0.dp,
    horizontalPadding: Dp = 9.dp,
) {
    val cs = MaterialTheme.colorScheme
    val bg = when {
        selected && error -> cs.errorContainer
        // 与筛选胶囊同档（softFillBarColor），保证吸顶区两排的颜色一致
        selected -> softFillBarColor()
        else -> Color.Transparent
    }
    val fg = when {
        selected && error -> cs.onErrorContainer
        // 选中 = 主色：这是**状态色**（选中了），必须与书章那个**身份色**区分开，
        // 否则整排分不出哪颗亮着。用户 11-14 明确「选中态保持主色不变」。
        selected -> cs.primary
        // 未选中：与书章联动到同一档（onSurface 78% ≈45% 亮度）——
        // 两排同属吸顶区，一个 45% 一个 28% 站在一起会"一亮一闷"。
        // 原 28%（onSurfaceVariant）在 11sp 小字上偏弱，四字标签读着吃力。
        else -> cs.onSurface.copy(alpha = 0.78f)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        // 11-15 晚二次定档（用户「筛选跟后面胶囊字号不一致，互换一下怎么样」）：
        // **胶囊 13→12sp、筛选 12→13sp**，两者交换。
        // 理由：这一排的层级是「标签 > 选项」——筛选是这个成排的标题，
        // 胶囊是可选项；原来标签比选项小一档，读起来像脚注挂在胶囊上。
        // 交换后标签 13 立住，胶囊 12 与日志时间戳(12)同级。
        // 余量（按现在的三颗胶囊算，别照 11-15 那版四颗的旧账）：
        // 「实时最新」已于 11-15 撤掉（分级筛选条整排下线），现在是
        // 朗读规则(4字)/插件(2字)/错误(2字) 三颗；360dp 屏每颗 ≈93dp，
        // 四字「朗读规则」12sp ≈48dp + 内衬 12dp = 60dp，仍有 30dp 余量；
        // 字号放大 1.12 倍时 ≈66dp，也不会触发省略。
        fontSize = 12.sp,
        // 11-11 用户令「选中后文字太过了」：SemiBold(600) → Medium(500)。
        // 选中态原本同时变「底色 + 字色 + 字重」三个维度，字重那一维最扎眼；
        // 降到 500 后仍有轻微加粗感，但不会"跳出来"。未选中仍是 Normal(400)，差一档够用。
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        color = fg,
        maxLines = 1,
        // 极窄屏（320dp）等分后只剩 ~47dp，四字标签放不下；
        // 用省略号而不是硬切，至少不出现半个字。360dp 及以上不会触发。
        overflow = TextOverflow.Ellipsis,
        // 等分后每颗宽度由 weight 决定（360dp 屏约 55dp），文字盒内居中。
        // 短标签（如「插件」）跟着变宽——整排左右边缘对齐、宽窄一致，不再参差。
        // 原先用 widthIn(min = 62.dp) 撑齐，那会把整排撑出屏外，已撤。
        textAlign = TextAlign.Center,
        modifier = modifier
            .then(if (minWidth > 0.dp) Modifier.widthIn(min = minWidth) else Modifier)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(
                // 11-11 用户令「后面应该是等待选择的胶囊，选中才填色」：
                // 未选中 = 空心胶囊 + 1dp 描边（看得出"这里可以点"）；选中才填色。
                // 描边色用满不透明的 outlineVariant（原先未选中乘了 0.55 透明，屏幕上几乎看不见）。
                // error 档选中是实色淡红底，不再叠描边。
                if (!(selected && error)) Modifier.border(1.dp, cs.outlineVariant, RoundedCornerShape(50))
                else Modifier
            )
            .clickable(onClick = onClick)
            // 内衬收到 5dp：11-15 那版是四颗胶囊、最长的「实时最新」等分后只剩 ~2dp
            // 余量，9dp 内衬会把它挤成省略/换行。现在「实时最新」已随分级筛选条撤掉，
            // 三颗里最长的是「朗读规则」，余量宽松（见 PinnedChip 上的字号注释），
            // 5dp 沿用不改——它只是让内衬别再吃掉宽度，改回 9dp 没有收益。
            .padding(horizontal = horizontalPadding, vertical = 3.5.dp),
    )
}

/**
 * 筛选行左端的「筛选」标签（11-11 用户令：「这一行左侧也应该有个筛选的提示」）。
 * 图标 + 文字 + 一道右侧竖线：既说明这排是什么，又和右边选项拉开层次。
 * ⚠️ 全排只有这一道竖线，胶囊之间不许再加（用户：「后边就不要有分隔线，不然区分不开」）。
 */
@Composable
private fun PinnedFilterLabel() {
    val cs = MaterialTheme.colorScheme
    // 11-11：内衬从 3+7 收到 2+4，右缘 7 收到 4——第一排改等分后总宽吃紧，
    // 标签少占 7dp，五颗胶囊每颗就多分到 1.4dp（四字标签只差 2dp 余量，很关键）。
    Row(
        modifier = Modifier.padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 11-14：图标与文字同步提到 onSurface 78%（与右侧四颗胶囊、下方书章同一档）。
        // 不这么做的话：标签停在 28%、胶囊 45%、书章 45%——同一行里
        // "筛选"最暗，看着像掉出去的一截，整排不成立。
        Icon(
            Icons.Default.FilterList,
            contentDescription = null,
            tint = cs.onSurface.copy(alpha = 0.78f),
            // 11-14 用户实报「筛选图标是不是太小了」→ 12dp 确实偏小：
            // Material 图标在 12dp 下 FilterList 三条横线快糊成一块，且它是整排的
            // 标识符（"这排是什么"全靠它 + 两个字），比例上应接近文字高度。
            // 定 14dp（用户拍板）：与标签文字视觉高度协调；16dp 会顶到胶囊上下、
            // 18dp 会与胶囊等高变成"图标主导"，比例失衡。
            // 11-15 晚标签 12→13sp 后仍留 14dp：13sp 墨迹高 ≈12dp，14dp 图标刚好略高于字，
            // 跟到 15/16 就会比「筛选」两个字还高、抢掉标签的份量。
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = "筛选",
            style = MaterialTheme.typography.labelSmall,
            // 11-16 用户令「筛选跟同行字号统一」：13→**12sp**，与胶囊同为 12。
            // 原先刻意比胶囊大一档（11-15 定「标签>选项」），但换位后筛选行是
            // 第二排工具行、书章才是主角，标签不该再压胶囊；两排各自成体系：
            // 第一排书章 13sp 主角，第二排筛选+胶囊统一 12sp。
            fontSize = 12.sp,
            // 同图标：与胶囊/书章统一到 78% 档（理由见上方 Icon 注释）
            color = cs.onSurface.copy(alpha = 0.78f),
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .width(1.dp)
                .height(14.dp)
                .background(cs.outlineVariant)
        )
    }
}

/** 吸顶区右端的小键（切换书 / 切换章），与书条章的旧样式同口径 */
@Composable
private fun PinnedSwitchKey(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.5.sp,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}
