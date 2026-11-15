package com.github.jing332.tts_server_android.compose

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberBottomAppBarState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.github.jing332.compose.widgets.ControlBottomBarVisibility
import com.github.jing332.compose.widgets.rememberA11TouchEnabled
import com.github.jing332.tts_server_android.compose.settings.SettingsScreen
import com.github.jing332.tts_server_android.compose.systts.MigrationTips
import com.github.jing332.tts_server_android.compose.systts.TtsLogScreen
import com.github.jing332.tts_server_android.compose.systts.list.ListManagerScreen
import com.github.jing332.tts_server_android.compose.RoleManagementScreen
import com.github.jing332.tts_server_android.conf.AppConfig
import kotlinx.coroutines.launch


/**
 * 底栏**内容**高度（不含手势条 inset）。11-15 用户令：从官方 NavigationBar 的 80dp 压到 60dp。
 *
 * 为什么自绘而不是给官方组件传 Modifier.height：
 * M3 `NavigationBar` 内部把内容高度写死成 `NavigationBarHeight`(80dp)，外部约束压下去
 * 只会把「图标+文字」硬裁（标签被切半），静态无法验证是否裁到——所以按用户
 * 「一次改成功」的要求，改成自绘 60dp，高度完全可控。
 *
 * 视觉（11-16 定稿）：**仿微信/QQ —— 无选中胶囊、图标 26dp + 四个标签常显 11.5sp、
 * 选中只换颜色（primary，落在图标和文字上）+ 顶缘 1dp 分隔线**。中间一版是 M3 官方那套
 *（选中胶囊 + 文字仅选中显示）压到 60dp，装机后用户拿微信/QQ 对比否掉了：
 * 胶囊在 60dp 里重心偏上，且三个无字图标看不出是哪栏；
 * 再一版改成主题色填充胶囊，也被否掉（「绿色显示在图标和文字上，而不是作为填充色」）。
 *
 * ⚠️ 全仓凡是要「避让底栏」的地方一律引用本常量，不要再写 80.dp 字面量
 *（11-15 用户令：上次改高改矮之后到处漏适配，这次必须一次改全）。
 * 目前引用点：MainPager 的 bottomBarHeightPx、ListManagerScreen 的 FAB 底部内距。
 */
internal val BottomBarHeight = 60.dp

@OptIn(ExperimentalMaterial3Api::class)
val LocalBottomBarBehavior =
    compositionLocalOf<BottomAppBarScrollBehavior>() { error("LocalBottomBarBehavior not initialized") }
val LocalOverlayController =
    compositionLocalOf<OverlayController> { error("LocalOverlayController not initialized") }

@OptIn(
    ExperimentalFoundationApi::class, ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun AnimatedContentScope.MainPager(sharedVM: SharedViewModel) {
    val pagerState =
        rememberPagerState(
            initialPage = AppConfig.fragmentIndex.value
                .coerceAtMost(PagerDestination.routes.size - 1)
        ) { PagerDestination.routes.size }
    DisposableEffect(pagerState) {
        onDispose {
            AppConfig.fragmentIndex.value = pagerState.currentPage
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    MigrationTips()

    val a11yTouchEnabled = rememberA11TouchEnabled()
    val density = LocalDensity.current
    // 底栏满高 = 内容高度 BottomBarHeight + 手势条 inset。
    // ⚠️ 三修定稿（10-12 装机二轮回滚/到底不出现）：前两版用 onSizeChanged 实测——但底栏一旦
    // 开始隐藏，外层 bottomBarHeight 就把可用高度裁到 (满高−已藏)，量到的高度跟着缩水 ⇒
    // 「越藏越藏不动、回滚也算不对」的反馈环（挂 Surface 或挂 NavigationBar 都不行，两者同受约束）。
    // 改为确定性计算：WindowInsets.navigationBars 取手势条高度，不加任何测量依赖。
    // 11-15：内容高度 80 → 60dp（自绘紧凑栏，见 BottomBarHeight 注释）。
    val navBarInsetPx = WindowInsets.navigationBars.getBottom(density)
    val bottomBarHeightPx = with(density) { BottomBarHeight.roundToPx() } + navBarInsetPx
    // 自建 state 把 heightOffsetLimit 设为**满高**（默认 exitAlways 的 limit=48dp——BottomAppBarDefaults
    // 反编译实锤，首参可传自建 state 且 limit 可写）：limit=满高后隐藏/回弹/出现全走官方原生逻辑
    val bottomAppBarState = rememberBottomAppBarState(
        initialHeightOffsetLimit = -bottomBarHeightPx.toFloat(),
        initialHeightOffset = 0f,
        initialContentOffset = 0f
    )
    androidx.compose.runtime.LaunchedEffect(bottomBarHeightPx) {
        // 手势条高度变化（横竖屏/三键导航切换）时同步 limit
        bottomAppBarState.heightOffsetLimit = -bottomBarHeightPx.toFloat()
    }
    val scrollBehavior = BottomAppBarDefaults.exitAlwaysScrollBehavior(
        state = bottomAppBarState,
        canScroll = { !a11yTouchEnabled }
    )
    ControlBottomBarVisibility(a11yTouchEnabled, scrollBehavior)

    val overlayController = rememberOverlayController()

    CompositionLocalProvider(
        LocalBottomBarBehavior provides scrollBehavior,
        LocalOverlayController provides overlayController,
    ) {
        Box(Modifier.fillMaxSize()) {
            val backgroundColor by animateColorAsState(
                targetValue = if (overlayController.visible) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                } else {
                    Color.Transparent
                },
                animationSpec = tween(durationMillis = 600, easing = LinearEasing),
                label = "background color animation"
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .then(
                        if (overlayController.visible) {
                            Modifier.clickable(
                                interactionSource = null,
                                indication = null,
                                onClick = { overlayController.hide() }
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {}

            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                bottomBar = {
                    // 11-15 用户令「底栏改低一点」→ 自绘 60dp 紧凑栏（当晚会同令改为
                    // 四标签常显，见 BottomBarHeight 注释）。
                    // 视觉口径全部沿用官方那套，只是矮 20dp（细节见 BottomBarHeight 注释）。
                    // 滚动隐藏 v2 布局式裁剪原样适用（bottomBarHeight 裁的是整体高度，
                    // 自绘 Row 当内容装进去即可）；手势条 inset 由下方 Column 的
                    // windowInsetsPadding 补——官方组件原来自带，自绘必须自己来。
                    // 10-13 深夜用户令「底栏要跟上方空白区域一样的颜色」：底栏=background 页底色，
                    // 与页底的分界只靠顶缘 1dp outlineVariant 浅线（overlay 画法，不占布局）。
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bottomBarHeight({ scrollBehavior.state.heightOffset }, bottomBarHeightPx),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                // 手势条区域：官方 NavigationBar 自带 navigationBarsPadding，
                                // 自绘必须自己补（漏了的话内容和手势条重叠、点不到）
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(BottomBarHeight),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                for (destination in PagerDestination.routes) {
                                    val isSelected = pagerState.currentPage == destination.index
                                    val barPrimary = MaterialTheme.colorScheme.primary
                                    // 未选中降到 78%：与日志页筛选行、书章同一档（11-14 定的口径）。
                                    // 100% 的 onSurfaceVariant 在 22dp 细线图标上仍偏重，压掉
                                    // 「没选中但抢眼」的问题——微信/QQ 未选中都是浅一档的深灰。
                                    val barIdle = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            // selectable 自带选中态语义 + Role.Tab，
                                            // 读屏能念出「日志，已选中，标签页」——
                                            // 官方 NavigationBarItem 原本负责这块，自绘要自己给
                                            .selectable(
                                                selected = isSelected,
                                                role = Role.Tab,
                                                onClick = {
                                                    scope.launch {
                                                        pagerState.animateScrollToPage(destination.index)
                                                    }
                                                },
                                            )
                                            .semantics {
                                                contentDescription = context.getString(destination.contentDescId)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                // 11-16 用户令（晚）：填充胶囊否掉——「这个绿色
                                                // 显示在图标和文字上，而不是作为填充色」。
                                                // 即回到微信/QQ 的做法：选中只换颜色不换底，
                                                // 图标+文字一起用主题 primary（换主题/深色自动跟随，
                                                // 不写死绿色）；未选中=无底、灰字。
                                                .padding(horizontal = 10.dp, vertical = 3.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            // 11-15 晚：用户拿微信/QQ 底栏对比后令「抄他们的思路」：
                                            // 四个标签常显、选中不靠"有没有字"区分（这条保留）；
                                            // 11-16 晚：选中态定为「图标+文字同用主题色」，
                                            // 撤掉填充胶囊（见上方 Column 注释）。
                                            CompositionLocalProvider(
                                                LocalContentColor provides
                                                    if (isSelected) barPrimary else barIdle
                                            ) {
                                                destination.icon()
                                            }
                                            // 11-16：10sp→**11.5sp**、行高 14→16。
                                            // 用户拿 QQ 实拍对比：「图标和字都比咱们的大」——QQ 那档
                                            // 标签约 12sp、图标约 26dp。60dp 内容高里
                                            // 26 图标 + 2 间距 + 16 行高 = 44dp，上下各 8dp 够用；
                                            // 系统字号 1.12 时 11.5→12.9sp，仍单行（「系统TTS」5 字 ≈65dp，
                                            // 单格 ≈90dp）。
                                            Text(
                                                text = stringResource(destination.strId),
                                                fontSize = 11.5.sp,
                                                lineHeight = 16.sp,
                                                maxLines = 1,
                                                color = if (isSelected) barPrimary else barIdle,
                                                modifier = Modifier.padding(top = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // 顶缘分隔线（outlineVariant@50%）——11-16 加回来。
                        // 11-15 晚撤它是照搬微信/QQ「没线」，但漏了人家的前提：
                        // **人家的底栏底色与内容区不同**（白/灰分工），靠色差分带；
                        // 我们的底栏与页底同色（10-13 用户令），没有线就没有边界。
                        // 用户 11-16 原话：「人家不加分隔线是因为颜色不一样，咱们颜色一样
                        // 就得加分隔线，反正你要改颜色要不加分隔线」。
                        // 选**加线**而不是改底色：全站只有一个页面底 (#FBFDF8)，
                        // 为底栏新造一个色阶等于引入新色相，换主题/深色还要再配一套；
                        // 一条 50% 的 1dp 线是现成档位，和日志页条目线同一口径。
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                        }
                    }
                }
            ) { paddingValues ->
                val bottomPad = paddingValues.calculateBottomPadding()
                HorizontalPager(
                    modifier = Modifier
                        // 系统TTS列表页不收缩视口（其列表用 contentPadding 自行避让底栏，
                        // 展开项可填满整屏不被"白条"截断）；其余页维持外层收缩
                        .padding(bottom = if (pagerState.currentPage == PagerDestination.SystemTts.index) 0.dp else bottomPad)
                        .fillMaxSize(),
                    state = pagerState,
                    // 关闭左右滑动换栏（11-11 用户令「把那个每栏左右滑切换给它删掉」）。
                    // 四栏各有自己的横向手势，全局横滑必然抢戏：
                    //   配置列表/角色管理=卡片左滑删除 + 长按拖动排序；
                    //   日志页=自带那套 awaitEachGesture 长按拖选（横滑会被它判成"换栏意图"）；
                    // 而且误触一次就整屏换栏、滚动位置和展开态全丢。
                    // 去掉后换栏只剩底部导航栏一个入口——单入口、不可能误触。
                    userScrollEnabled = false,
                    // 保留相邻1页状态，避免角色管理栏UI被销毁重建导致状态丢失
                    beyondViewportPageCount = 1,
                ) { index ->
                    when (index) {
                        PagerDestination.SystemTts.index -> ListManagerScreen(sharedVM, listBottomPadding = bottomPad)
                        PagerDestination.Tool.index -> RoleManagementScreen(sharedVM, pagerState)
                        PagerDestination.SystemTtsLog.index -> TtsLogScreen()
                        PagerDestination.Settings.index -> SettingsScreen()
                    }
                }
            }
        }
    }
}

/**
 * 底栏滚动隐藏（布局式裁剪，v4 简化）：按 [heightOffset]（px，负=隐藏量）实时裁底栏高度，
 * Scaffold 槽位随高度同步缩小，滚动内容真的多出空间。
 * 隐藏上限已改在 state 上（自建 BottomAppBarState，limit=实测满高，见上方注释）——
 * offset 天然能走到全藏，本函数回归纯裁剪，不再做任何映射/兜底换算。
 */
private fun Modifier.bottomBarHeight(heightOffsetState: () -> Float, fullHeightPx: Int): Modifier =
    layout { measurable, constraints ->
        val hidden = (-heightOffsetState()).roundToInt().coerceIn(0, fullHeightPx)
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = (fullHeightPx - hidden).coerceAtLeast(0)))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
