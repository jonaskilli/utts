package com.github.jing332.tts_server_android.compose.systts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun TtsLogScreen(vm: TtsLogViewModel = viewModel()) {
    val context = LocalContext.current
    // 剪贴板：多选「复制(N)」用（1002）
    val clipboard = LocalClipboardManager.current
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    // 日志文件列表弹窗（用户 09-08：文件夹点开自由选择文件，不再直接扔给外部查看器）
    var showLogFilesDialog by remember { mutableStateOf(false) }
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
    var viewBookChapter by remember(vm.logs) {
        val e = vm.logs.lastOrNull { it.bookName.isNotBlank() }
        mutableStateOf(e?.bookName.orEmpty() to e?.chapterTitle.orEmpty())
    }
    // 正在朗读的书章（与筛选无关）：用于"筛着 A 书却在读 B"时的跳转提示。
    // 取全量日志里**最后一条**带书章的条目——它就是"最近一次请求"所属的书。
    val readingBookChapter by remember(vm.logs) {
        derivedStateOf {
            val e = vm.logs.lastOrNull { it.bookName.isNotBlank() }
            (e?.bookName.orEmpty()) to (e?.chapterTitle.orEmpty())
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

    fun LogEntry.matchesQuery(q: String): Boolean =
        message.contains(q, ignoreCase = true) || time.contains(q, ignoreCase = true)

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

    // 归组结果（10-06 全链卡）：与 LogScreen 共用同一实例——搜索跳转要把条目下标
    // 换算成列表项下标（一张卡=一个列表项），必须用同一份归组才不会错位
    val logGroups = remember(displayLogs, pinnedFallback) {
        LogGroups.build(
            displayLogs,
            fallbackBook = pinnedFallback?.bookName.orEmpty(),
            fallbackChapter = pinnedFallback?.chapterTitle.orEmpty(),
        )
    }

    // ——「原文」定位（用户 10-08 午后拍板 甲方案）——
    // 筛选/搜索态下列表是收窄视图；点卡上的「⟲ 原文」= 清筛选回到完整流，
    // 跳到该条目所在卡并高亮。完整流归组单独建（与 displayLogs 归组不同实例）。
    val isFiltered by remember(vm) {
        derivedStateOf {
            // 只认真正"收窄"视图的两项：搜索词、级别筛选（10-10 用户令）。
            // 「插件」「朗读规则」两个调试开关是**追加型**——勾上往列表里加日志，
            // 一点都不收窄，与"回完整流找原位置"语义相反；此前把它们也算进 isFiltered，
            // 导致只要勾了调试开关每张卡右上角都冒原文键（用户实机反馈）。
            searchQuery.trim().isNotEmpty() || vm.selectedLevels.isNotEmpty()
        }
    }
    // 原文定位用的完整流：回落值同上（它本身就是全量视图，其实不会缺，保持口径一致即可）
    val fullGroups = remember(vm.filteredLogs) {
        LogGroups.build(
            vm.filteredLogs,
            fallbackBook = pinnedFallback?.bookName.orEmpty(),
            fallbackChapter = pinnedFallback?.chapterTitle.orEmpty(),
        )
    }
    var locateHighlight by remember { mutableStateOf<LogEntry?>(null) }

    fun locateOriginal(entry: LogEntry) {
        // 1) 清**收窄类**筛选（搜索词/级别勾选）——与 isFiltered 的判据保持一致。
        // 「插件」「朗读规则」两个调试开关**不动**：它们是用户主动开启的追加项，
        // 不是筛选条件；点一次「原文」就把它们关掉属意外副作用（此前会误关）。
        searchQuery = ""
        vm.clearFilter()
        // 2) 在完整流里找到该条目的卡头，滚动过去并高亮闪现
        val fullList = vm.filteredLogs
        val idx = fullList.indexOfFirst { it == entry }.let { found ->
            if (found >= 0) found else return
        }
        locateHighlight = entry
        scope.launch {
            // 状态更新后重组出完整列表再滚：等一帧让 displayLogs 生效
            withFrameNanos { }
            listState.animateScrollToItem(fullGroups.listItemFor(idx))
            // 高亮停留约 2s 后撤掉（LogEntryBody 按引用比对）
            kotlinx.coroutines.delay(2000)
            if (locateHighlight == entry) locateHighlight = null
        }
    }

    // 搜索词变化 → 跳到最近一条匹配(高亮由 LogScreen 渲染)；列表本身已实时过滤，
    // 跳转目标按归组映射换算：命中卡内任一行即落到整张卡
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.isNotEmpty()) {
            val idx = displayLogs.indexOfLast { it.matchesQuery(q) }
            if (idx >= 0) listState.animateScrollToItem(logGroups.listItemFor(idx))
        }
    }

    // 上一处/下一处：以当前可视位置为锚点跳转，到头自动绕回；条目下标经归组映射
    // 换算成列表项下标再滚动
    fun jumpToMatch(forward: Boolean) {
        val q = searchQuery.trim()
        if (q.isEmpty()) return
        val matches = displayLogs.indices.filter { displayLogs[it].matchesQuery(q) }
        if (matches.isEmpty()) return
        // 可视首项（可能是组/裸行/日期签）→ 取该列表项的首个条目下标做锚
        val firstVisible = listState.firstVisibleItemIndex
        val anchorEntry = when (val item = logGroups.items.getOrNull(firstVisible)) {
            is LogGroups.Item.Bare -> item.index
            is LogGroups.Item.Group -> item.head
            else -> firstVisible
        }
        val target = if (forward)
            matches.firstOrNull { it > anchorEntry } ?: matches.first()
        else
            matches.lastOrNull { it < anchorEntry } ?: matches.last()
        scope.launch { listState.animateScrollToItem(logGroups.listItemFor(target)) }
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
    // 复制：按列表顺序拼接「时间 | 级别 | 正文」，多条之间以空行分隔
    // ⚠️ 正文必须**转纯文本**再复制（10-12 用户实报）：message 是渲染用的富文本
    //（<font color="#00FFFF"> 哨兵色 / <b> / <br>），直接复制出去粘到别处全是标签，
    // 既不好读也没法直接发给别人。转换口径见 toPlainText。
    fun copyChecked() {
        if (checkedEntries.isEmpty()) {
            context.toast(R.string.log_select_none)
            return
        }
        val text = displayLogs.filter { it in checkedEntries }
            .joinToString(separator = "\n\n") {
                // 折叠次数写进文本（10-12 用户令）：×N 原来只体现在丸上，复制出去就丢了次数
                val times = if (it.repeatCount > 1) "（×${it.repeatCount}）" else ""
                "${it.time} | ${it.getLevelChar()} | ${it.message.toPlainText()}$times"
            }
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
                                stringResource(R.string.log_copy_n, checkedEntries.size),
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

                // 顶栏与列表之间的分隔线（10-06）：滚动的日志/卡片贴到顶栏时有了"底"，
                // 不再像 UI 被拦腰截断
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // 搜索控制行：匹配数 + 上一处/下一处跳转。
                // ⚠️ 漏斗键本行撤（10-10）：恢复「四键常驻」后顶栏已有级别筛选键，
                // 搜索时本行再出一个是重复入口；原「漏斗住进本行」的前提
                //（顶栏三键搜索态隐藏）已被四键常驻推翻，见顶栏 actions 注释。
                AnimatedVisibility(
                    visible = isSearchActive && searchQuery.isNotBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${matchCount} 处匹配",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { jumpToMatch(false) }) {
                            Icon(Icons.Default.KeyboardArrowUp, "上一处")
                        }
                        IconButton(onClick = { jumpToMatch(true) }) {
                            Icon(Icons.Default.KeyboardArrowDown, "下一处")
                        }
                    }
                }

                // 筛选标签显示
                // 11-11 用户令「只看错误的时候，不要出现上方的那个 WARN 和 ERROR」：
                // 「只看错误」的实现是往 selectedLevels 塞 ERROR+WARN，于是这条分级筛选条
                // 会跟着冒出来——用户看到的是一条本不该存在的筛选栏。
                // 它是「只看错误」的副作用而非独立筛选意图，故该态下不显示。
                AnimatedVisibility(
                    visible = vm.selectedLevels.isNotEmpty() && !vm.isErrorsOnly(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.filter),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // chips 流式换行：一行放不下自动折第二行（用户 09-08：不想左右滑）
                            FlowRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                vm.selectedLevels.forEach { level ->
                                    FilterChip(
                                        selected = true,
                                        onClick = { vm.toggleLevel(level) },
                                        label = { Text(getLevelName(level), maxLines = 1) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = null,
                                                modifier = Modifier.height(16.dp).width(16.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = getLevelColor(level)
                                        )
                                    )
                                }
                            }
                            // 清除所有筛选（"筛选"标签与清除键固定两侧，chips 在中间滚动）
                            IconButton(onClick = { vm.clearFilter() }) {
                                Icon(Icons.Default.Clear, null)
                            }
                        }
                        HorizontalDivider()
                    }
                }

                // ===================== 吸顶区（11-11 用户令）=====================
                // 三行：①筛选开关 ②书名+切换书 ③章名+切换章。
                // 只在有书章时显示书章两行；筛选行始终显示（它是日常操作）。
                // ⚠️ 放在 topBar 里而不是列表 stickyHeader：topBar 天然不随列表滚动，
                // 且与搜索行/分隔线同属一个固定区，不会出现「sticky 与 topBar 抢层级」。
                // 11-13 用户令：「日志空白页不应该显示这些，得有能筛选的东西才显示」。
                // 没有任何日志时不渲染筛选区（含下方分隔线），空页只剩标题与搜索。
                if (vm.logs.isNotEmpty())
                PinnedLogHeader(
                    autoFollow = vm.autoScrollToBottom.value,
                    onToggleAutoFollow = {
                        vm.autoScrollToBottom.value = !vm.autoScrollToBottom.value
                    },
                    errorOnly = vm.isErrorsOnly(),
                    onToggleErrorOnly = {
                        if (vm.isErrorsOnly()) vm.showAllLevels() else vm.showErrorsOnly()
                    },
                    showRule = vm.showSpeechRuleLogs.value,
                    onToggleRule = { vm.showSpeechRuleLogs.value = !vm.showSpeechRuleLogs.value },
                    showPlugin = vm.showPluginLogs.value,
                    onTogglePlugin = { vm.showPluginLogs.value = !vm.showPluginLogs.value },
                    book = logGroups.pinnedBook,
                    bookCount = logGroups.pinnedBookCount,
                    bookTotal = logGroups.bookTotal,
                    chapter = logGroups.pinnedChapter,
                    chapterCount = logGroups.pinnedChapterCount,
                    onSwitchBook = { bookPickerFor = it },
                    onSwitchChapter = { b, c -> chapterPickerFor = b to c },
                    // 筛选条件（与"视野"分开）：null=不限
                    scopeBook = vm.scopeBook.value,
                    scopeChapter = vm.scopeChapter.value,
                    // 视野：跟着滚动显示"你看的是哪本哪章"（用户：「我划到哪就显示哪本书」）
                    viewBook = viewBookChapter.first,
                    viewChapter = viewBookChapter.second,
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
                if (vm.logs.isNotEmpty())
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
            list = displayLogs,
            listState = listState,
            // 搜索定位期间不自动滚底，避免与跳转互相拉扯；多选中暂停（1003）——
            // 拖选途中新日志一到就把列表拉到尾，指尖下的行会整批选错
            autoScrollToBottom = vm.autoScrollToBottom.value && searchQuery.isEmpty() && !selectionMode,
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
            locateHighlight = locateHighlight,
            // 书籍条右侧「切换书」：开书单弹窗（只切日志查看范围，用户 11-10 明确）
            onSwitchBook = { bookPickerFor = it },
            // 章条右侧「切换章」：弹章节列表（用户 11-11 令，与「切换书」对称）
            onSwitchChapter = { book, chapter -> chapterPickerFor = book to chapter },
            // 视野书章回传（11-11）：吸顶区那行"当前：《书名》第N章"跟着滚动变。
            // 只在首项所属书章变化时触发（LogScreen 内部已按 derivedStateOf 收敛）。
            onViewChanged = { b, c -> viewBookChapter = b to c },
            // 切过去时强制滚底（见 LogScreen 里 autoScrollToBottom 的注释）
            jumpToLiveTick = jumpToLiveTick,
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

@Composable
private fun getLevelName(level: Int): String {
    return when (level) {
        LogLevel.ERROR -> "ERROR"
        LogLevel.WARN -> "WARN"
        LogLevel.INFO -> "INFO"
        LogLevel.DEBUG -> "DEBUG"
        LogLevel.TRACE -> "VERBOSE"
        else -> "UNKNOWN"
    }
}

@Composable
private fun getLevelColor(level: Int): Color {
    return when (level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
        LogLevel.WARN -> Color(0xFFFFF3E0)
        LogLevel.INFO -> MaterialTheme.colorScheme.secondaryContainer
        LogLevel.DEBUG -> MaterialTheme.colorScheme.primaryContainer
        // 10-10 纯白表：UNKNOWN 档本意中性灰，surfaceVariant 未被新管线覆写、绿主题下带绿灰，改灰档
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
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

// ===================== 日志吸顶区（11-11 用户令「书名和章节名一直置顶」）=====================
// 三行结构，从上到下：
//   ① 筛选开关：实时 | 错误 ‖ 朗读规则 | 插件
//      （11-11 用户令删掉 DEBUG：它只在插件用 console.debug() 打点时有用，默认本就关闭，
//       他的规则脚本与插件里 console.debug 是 0 处，整排留一颗永远不亮的键只占宽度）
//      （11-11 用户令改短：「实时最新」→「实时」、「只看错误」→「错误」；
//       原设计这里是竖线分档，但那道竖线早已撤掉，只剩左端标签旁的一道）
//   ② 书/章筛选 + 视野（筛选书 / 选章节）
//
// 书章行只在有数据时显示（老规则/单语音模式下整块隐藏，不出现空条）。
// 字号按主次：书名 12.5sp 半粗 > 章名 11.5sp > 筛选 11sp（11-11 用户令全排统一）。
// 关键判据：书名字号略小于日志正文，避免吸顶抢内容的视觉重心——它只是路标。
@Composable
private fun PinnedLogHeader(
    autoFollow: Boolean,
    onToggleAutoFollow: () -> Unit,
    errorOnly: Boolean,
    onToggleErrorOnly: () -> Unit,
    showRule: Boolean,
    onToggleRule: () -> Unit,
    showPlugin: Boolean,
    onTogglePlugin: () -> Unit,
    book: String,
    bookCount: Int,
    /** 不同书数：<2 时「筛选书」没有可筛对象，不显示（用户 11-13 口径）。 */
    bookTotal: Int = 0,
    chapter: String,
    chapterCount: Int,
    onSwitchBook: (String) -> Unit,
    onSwitchChapter: (String, String) -> Unit,
    // 11-11 用户定案「全部日志 + 其下方筛选」：
    //   · scopeBook / scopeChapter 是**筛选条件**（null=不限）；
    //   · viewBook / viewChapter 是**跟随滚动的视野信息**（当前看到哪本书哪一章）。
    // 两者必须分开：筛选决定列表范围，视野只用于"你看的是哪本"这行小字。
    scopeBook: String? = null,
    scopeChapter: String? = null,
    viewBook: String = "",
    viewChapter: String = "",
    onClearScope: () -> Unit = {},
    // 「切过去」＝回到全部日志 + 实时最新 + 滚到底（用户 11-11 定案 A）。
    // ⚠️ 原来这里错用了 onSwitchBook(readingBook)，那只是**打开书单弹窗**，
    // 既没清筛选也没滚到底，用户实报「你说的切过去是切到全部日志的实时最新吧」——
    // 他说的是对的，那是正确行为；原实现是 bug。
    onJumpToLive: () -> Unit = {},
    // 正在朗读的书（与筛选不同）：非空且与 scopeBook 不一致时给「切过去」提示条
    readingBook: String = "",
    readingChapter: String = "",
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
        // ① 筛选开关行
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
                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            // 间距回 3dp：删掉 DEBUG 只剩 4 颗，每颗分到 71.8dp（四字只需 61dp 含内衬），
            // 不必再挤成 2dp。
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // 11-11 用户令：这排到底是什么，得有个名分。
            // 左侧「筛选」标签自带图标 + 一道竖线分隔，把「这是筛选器」讲清楚；
            // 竖线只留这一道——胶囊之间不再插分隔线（用户：「后边就不要有分隔线，
            // 不然就有点儿区分不开」：一道线是「标签｜选项」的分界，两道以上就成了装饰噪音）。
            PinnedFilterLabel()
            // 11-11 用户令「写得更省略一点，第一个就写实时」：文案「实时最新」→「实时」。
            // 等分宽度下两颗四字标签最吃紧，缩成两字后余量充足，也不会再被省略号吃掉。
            PinnedChip(
                label = "实时",
                selected = autoFollow,
                onClick = onToggleAutoFollow,
                modifier = Modifier.weight(1f),
                horizontalPadding = 6.dp,
            )
            // 11-11 用户令：保留红底红字（「填充色是红色，字号也是红色也行吧，要不就不改了」），
            // 排障语义"红色=错误"维持。
            // 11-11 用户令「第二个就写错误」：文案「只看错误」→「错误」。
            // 红色语义不变（选中=淡红底+红字），缩短后仍能一眼认出这是排障开关。
            PinnedChip(
                label = "错误",
                selected = errorOnly,
                onClick = onToggleErrorOnly,
                error = true,
                modifier = Modifier.weight(1f),
                horizontalPadding = 6.dp,
            )
            PinnedChip("朗读规则", showRule, onToggleRule, modifier = Modifier.weight(1f), horizontalPadding = 6.dp)
            PinnedChip("插件", showPlugin, onTogglePlugin, modifier = Modifier.weight(1f), horizontalPadding = 6.dp)
        }

        // ===== ② 筛选 + 视野（11-11 用户定案「全部日志 + 其下方筛选」）=====
        //
        // 结构定案（用户逐条确认过）：
        //   · 「全部日志」不再是"某一本书"，而是常驻标题；书名/章名降级成它下面的**筛选条件**；
        //   · 书筛选常驻（「书：全部」/「书：《书名》」）；**章筛选只在选了书之后出现**
        //     ——章脱离书没有可筛范围，单独摆出来只会让人困惑；
        //   · 另有一行小字显示**视野**（跟着滚动变：你看的是哪本书哪一章），它不是按钮；
        //   · 筛着 A 书却在读 B 时，追加一条提示条，可一键切过去（不劫持筛选）。
        //
        // ⚠️ 与旧版的区别：旧版把书条/章条当"切换"用（点按钮弹列表，语义是导航）；
        // 用户指出「你现在是筛选，不是切换」——所以胶囊文案与语义都改成筛选。

        // 第二排：书/章信息 + 行尾两个动作。**这一排不横滚**（11-11 用户实报后改）。
        //
        // ⚠️ 根因（别再套回 horizontalScroll）：
        // 原实现给这排套了 horizontalScroll，书名又用 weight(1f, fill=false)。
        // 横滚行给子项的宽度上界是**无限**，weight 分到的剩余空间也是无限，
        // 于是 fill=false 的文本按**原始宽度**铺开、永远不省略——书名有多长，这排就多宽，
        // 行尾的「筛选书」「选章节」被一路推出屏幕，用户只能横滑到最右才看得见
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
                .padding(start = 14.dp, end = 10.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // ===== 书/章：左侧信息 + 右侧按钮（11-11 用户选 B 方案定稿）=====
            //
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
            val shownBook = if (filtered) scopeBook.orEmpty() else viewBook
            val shownChapter = if (filtered) scopeChapter.orEmpty() else viewChapter
            val chapLabel = shortChapterLabel(shownChapter).let { if (it.isBlank()) "" else " $it" }

            // 左侧信息槽：占满行内剩余宽度，右侧两个按钮先量、先占位。
            // 槽内书名可省略，章名不让位——长书名不能把「第N章」或右侧按钮挤没。
            Box(Modifier.weight(1f)) {
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
                            Text(
                                text = "《$shownBook》",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (chapLabel.isNotBlank()) {
                                Text(
                                    text = chapLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                )
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
                        // ---- 未筛选：纯灰字（不可点）----
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "《$shownBook》",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (chapLabel.isNotBlank()) {
                                Text(
                                    text = chapLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // 右侧：筛选书——11-13 用户令「得出现两本才能筛吧」：只有 ≥2 本书才显示
            if (bookTotal >= 2) {
            PinnedChip(
                label = "筛选书",
                selected = filtered,
                onClick = { onSwitchBook(scopeBook ?: viewBook.ifBlank { book }) },
                // 第二排不是等分布局，仍按四字最小宽撑起（62dp = 四字44 + 内衬18）
                minWidth = 62.dp,
            )
            }

            // 右侧：跳到第N章（筛了书之后才出现，纯跳转）
            if (filtered) {
                // 这里只为判断"这本书有没有章"——有章才给选章入口；
                // 章号本身不再拼进按钮文案（改「选章节」后由弹窗表达）。
                val curChap = shortChapterLabel(scopeChapter ?: chapter)
                if (curChap.isNotBlank()) {
                    PinnedChip(
                        // 11-11 用户令：文案改「选章节」。
                        // 原来写「跳到第6章 ▾」有歧义——它显示的永远是**当前章**，
                        // 用户读作"我在第6章，为什么还让我跳到第6章？"。
                        // 改成"选章节"后，动作性质（从列表里挑一章跳过去）一眼就明白，
                        // 具体跳到哪由弹窗决定，不必写在按钮上。
                        label = "选章节",
                        selected = false,
                        onClick = { onSwitchChapter(scopeBook ?: book, scopeChapter ?: chapter) },
                        minWidth = 62.dp,
                    )
                }
            }

            // 注：「取消筛选」由左侧胶囊里的「取消」承担，不再单独放按钮
            //（用户 11-11 选 B 方案：一个动作一个入口）。
        }

        // ===== ④ 跳转提示条：筛着 A 书、却在读 B =====
        // 用户：「如果正在朗读其他的再来一个跳转」。只在"有筛选 且 正在读的不是筛选的那本"时出现。
        val readingMismatch = !scopeBook.isNullOrBlank() &&
            readingBook.isNotBlank() && readingBook != scopeBook
        if (readingMismatch) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
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
private fun shortChapterLabel(chapter: String): String {
    if (chapter.isBlank()) return ""
    val m = Regex("^第\\s*[0-9一二三四五六七八九十百千零两]+\\s*章").find(chapter)?.value
    if (m != null) return m
    return if (chapter.length > 6) chapter.take(6) else chapter
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
    // 第二排的「筛选书/选章节」不是等分的，仍靠 minWidth 撑成四字宽。
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
        selected -> cs.primary
        else -> cs.onSurfaceVariant
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        // 全排统一 11sp（11-11 用户令「字号都要一致」）：
        // 原 DEBUG 走 small = 10.5sp 分支，是唯一的字号差异来源，已撤。
        fontSize = 11.sp,
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
            // 内衬收到 5dp：等分宽度下四字标签只剩 ~2dp 余量，
            // 9dp 内衬会把「实时最新」挤成省略/换行。5dp 仍能包住文字不贴边。
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
        Icon(
            Icons.Default.FilterList,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = "筛选",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = cs.onSurfaceVariant,
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
