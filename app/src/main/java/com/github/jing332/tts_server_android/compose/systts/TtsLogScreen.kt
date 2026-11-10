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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.github.jing332.tts_server_android.compose.theme.AppShapes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.CompositionLocalProvider
import com.github.jing332.common.LogEntry
import com.github.jing332.common.LogLevel
import com.github.jing332.common.utils.toast
import com.github.jing332.tts_server_android.R
import com.github.jing332.compose.widgets.DenseTextField
import com.github.jing332.tts_server_android.compose.systts.role.FlatTextAction
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
    var searchQuery by remember { mutableStateOf("") }
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

    // 归组结果（10-06 全链卡）：与 LogScreen 共用同一实例——搜索跳转要把条目下标
    // 换算成列表项下标（一张卡=一个列表项），必须用同一份归组才不会错位
    val logGroups = remember(displayLogs) { LogGroups.build(displayLogs) }

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
    val fullGroups = remember(vm.filteredLogs) { LogGroups.build(vm.filteredLogs) }
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
                        //（实时最新/只看错误/朗读规则/插件/DEBUG），弹窗不再需要。

                        // 文件夹按钮 - 先弹日志文件列表自由选择（用户 09-08），点击文件再用外部查看器打开
                        IconButton(onClick = { showLogFilesDialog = true }) {
                            Icon(Icons.Default.FolderOpen, stringResource(R.string.open_log_folder))
                        }

                        // 清空按钮
                        IconButton(onClick = { vm.clear() }) {
                            Icon(Icons.Default.DeleteOutline, stringResource(id = R.string.clear_log))
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
                AnimatedVisibility(
                    visible = vm.selectedLevels.isNotEmpty(),
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
                    showDebug = vm.showDebugLogs.value,
                    onToggleDebug = { vm.showDebugLogs.value = !vm.showDebugLogs.value },
                    book = logGroups.pinnedBook,
                    bookCount = logGroups.pinnedBookCount,
                    chapter = logGroups.pinnedChapter,
                    chapterCount = logGroups.pinnedChapterCount,
                    onSwitchBook = { bookPickerFor = it },
                    onSwitchChapter = { b, c -> chapterPickerFor = b to c },
                )
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

    // 日志文件列表（用户 09-08：自由选择要打开的日志文件）
    if (showLogFilesDialog) {
        val logFiles = remember {
            File(vm.logDir()).listFiles()
                ?.filter { it.isFile }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
        }
        val timeFmt = remember {
            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        }
        AlertDialog(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = { showLogFilesDialog = false },
            title = { Text("日志文件") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (logFiles.isEmpty()) {
                        Text(
                            "日志目录为空",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    logFiles.forEach { f ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showLogFilesDialog = false
                                    openLogFileWithViewer(context, f)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    f.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                Text(
                                    "${f.length() / 1024} KB · " + timeFmt.format(f.lastModified()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogFilesDialog = false
                    openLogFileWithViewer(context, File(vm.logDir()))
                }) {
                    Text("打开整个目录")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogFilesDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 11-11 用户令：筛选弹窗整体删除——9 个控件全部挪进吸顶区（筛选开关行）
    // 与书章切换（书条/章条的「切换书/切换章」）。弹窗里的 scopeBook/scopeChapter
    // 两个「只看本书/只看本章」也一并作废：切书后列表本来就只显示那本书。

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
private fun openLogFileWithViewer(context: android.content.Context, file: java.io.File) {
    val mime = if (file.isDirectory) "*/*" else "text/plain"
    kotlin.runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }.onFailure {
        kotlin.runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
            )
        }
    }
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
//   ① 筛选开关：实时最新 | 只看错误 ‖ 朗读规则 | 插件 ‖ DEBUG（竖线分三档，按用户给的优先级排）
//   ② 书名 + 切换书
//   ③ 章名 + 切换章
// 书章两行只在有数据时显示（老规则/单语音模式下整块隐藏，不出现空条）。
// 字号按主次：书名 12.5sp 半粗 > 章名 11.5sp > 筛选 11sp > DEBUG 10.5sp。
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
    showDebug: Boolean,
    onToggleDebug: () -> Unit,
    book: String,
    bookCount: Int,
    chapter: String,
    chapterCount: Int,
    onSwitchBook: (String) -> Unit,
    onSwitchChapter: (String, String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                // 比页底深半档，让吸顶区与日志流有明确分界（滚动时不会糊在一起）
                lerp(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    0.55f
                )
            )
    ) {
        // ① 筛选开关行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            PinnedChip("实时最新", autoFollow, onToggleAutoFollow)
            PinnedChip("只看错误", errorOnly, onToggleErrorOnly, error = true)
            PinnedDivider()
            PinnedChip("朗读规则", showRule, onToggleRule)
            PinnedChip("插件", showPlugin, onTogglePlugin)
            PinnedDivider()
            PinnedChip("DEBUG", showDebug, onToggleDebug, small = true)
        }

        // ② 书名行（无书名不显示）
        if (book.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "《$book》",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (bookCount > 0) {
                    Text(
                        text = "$bookCount 条",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                PinnedSwitchKey("切换书") { onSwitchBook(book) }
            }
        }

        // ③ 章名行（无章名不显示）
        if (chapter.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 章号小牌：只取「第N章」那段；取不到就把整串当章名（不解析数字，避免不规则章号出错）
                val idx = Regex("^第\\s*[0-9一二三四五六七八九十百千零两]+\\s*章").find(chapter)?.value
                if (idx != null) {
                    Text(
                        text = idx,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(AppShapes.xs)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 5.dp, vertical = 0.5.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = idx?.let { chapter.removePrefix(it).trim() }?.ifEmpty { chapter } ?: chapter,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (chapterCount > 0) {
                    Text(
                        text = "$chapterCount 条",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                PinnedSwitchKey("切换章") { onSwitchChapter(book, chapter) }
            }
        }
    }
}

/** 吸顶筛选开关：选中=浅绿底+描边+半粗；错误档选中时用红底保留排障语义 */
@Composable
private fun PinnedChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    error: Boolean = false,
    small: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val bg = when {
        selected && error -> cs.errorContainer
        selected -> cs.secondaryContainer
        else -> cs.surfaceContainerLow
    }
    val fg = when {
        selected && error -> cs.onErrorContainer
        selected -> cs.primary
        else -> cs.onSurfaceVariant
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontSize = if (small) 10.5.sp else 11.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(
                if (selected && !error)
                    Modifier.border(1.dp, cs.outlineVariant, RoundedCornerShape(50))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 3.5.dp),
    )
}

/** 三档之间的细分隔线（视觉上把「视线类/来源类/开发类」分开） */
@Composable
private fun PinnedDivider() {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(14.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
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
