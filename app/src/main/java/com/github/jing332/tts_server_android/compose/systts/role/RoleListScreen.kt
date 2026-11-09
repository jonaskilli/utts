package com.github.jing332.tts_server_android.compose.systts.role

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drake.net.utils.withIO
import com.github.jing332.common.ACCENT_GREEN
import com.github.jing332.tts_server_android.compose.theme.AppShapes
import com.github.jing332.compose.widgets.ControlBottomBarVisibility
import com.github.jing332.database.dbm
import com.github.jing332.database.entities.systts.SystemTtsV2
import com.github.jing332.database.entities.systts.TtsConfigurationDTO
import com.github.jing332.database.entities.systts.source.PluginTtsSource
import com.github.jing332.tts.CachedEngineManager
import com.github.jing332.tts.PreviewState
import com.github.jing332.tts.TaggedTtsPreviewPlayer
import com.github.jing332.tts_server_android.R
import com.github.jing332.tts_server_android.compose.ListGutter
import com.github.jing332.tts_server_android.compose.LocalBottomBarBehavior
import com.github.jing332.tts_server_android.compose.systts.common.VoicePickerDialog
import com.github.jing332.tts_server_android.service.systts.SystemTtsService
import com.github.jing332.tts_server_android.service.systts.help.CharacterRecordsFile
import com.github.jing332.tts_server_android.service.systts.help.KeyListFile
import com.github.jing332.tts_server_android.service.systts.help.VoiceMarksFile
import kotlinx.coroutines.launch

/**
 * 角色管理·1:1 复刻（拍板「照 v10 插件原样搬，之后他再改」）：
 * 对照 角色管理v10_主题密钥增强.js 逐函数复刻——
 * **平铺列表**（非分组）：每行=左名字列（主名+别名每行一个、性别色圆点、收藏【】、主角👑）
 * + 右侧两行（11-08 重构）：
 *   第一行=标签框（只放分类 tag，点=换声弹窗 / 长按=随机换声）+ ▶试听（三态）+ ⋮菜单
 *   （❤️🚶😈 标记 toggle + 删除配置项，与换声弹窗 VoiceOverflowMenu 同款）；
 *   第二行=注释行：发音人 displayName·插件名(前4字) 在左，已点亮标记 chip 钉行尾（点击取消）。
 *   宽度不够时削减顺序=displayName 先缩（chip/插件名保住）——11-08 用户令。
 * 除右侧标签框/▶/⋮ 各自吃掉点击外，整行其余区域点击=选中角色（11-08 用户令「点击范围」）。
 * 点击名字=勾选（选中背景高亮）；长按=操作菜单（合并+跟随/合并+选发音人（标记≥2）、
 * 释放删除已合并角色（有别名）、修改角色名（单选）、删除角色、设为主角）。
 * 发音人标签框点击=换声弹窗（与日志弹窗同款，桥接已通过）。
 * 顶部：书籍栏（书名/切换/修改书名）；密钥管理与备份恢复入口在宿主顶栏（给角色区留空）。
 * 列表下方「+ 添加角色」。主题🎨按钮不搬（原生即 MD3 主题）。
 */

/**
 * 性别圆点色：男/少年 = 青蓝，女/少女 = 粉红，判不出 = 灰。
 *
 * **只看发音人分类标签**（「分性别只看分类标签就够了」）——标签本身就是
 * 「中文分类词 + 数字序号」（如 `女青年01`、`少年01`、`少女01`），性别信息已经带在里头，
 * 不必再读记录的 `gender` 字段（那字段常为空，反而会把圆点判成灰）。
 *
 * ⚠️「少年 / 少女」必须点名处理，不能只匹配「男 / 女」：
 * `少年` 里**没有「男」字**（少年 = 少 + 年），只按 男/女 匹配会落进 else 变灰。
 * 故顺序固定：少女 → 少年 → 女 → 男（先点名这一对，再走通用词）。
 */
internal fun genderDotColor(tag: String): Color = when {
    tag.contains("少女") -> Color(0xFFE91E63)
    tag.contains("少年") -> Color(0xFF1976D2)
    tag.contains("女") -> Color(0xFFE91E63)
    tag.contains("男") -> Color(0xFF1976D2)
    else -> Color(0xFF9E9E9E)
}

/** 释放并固定的彩色圆点阵（照插件 releaseDotColors 顺序） */
private val releaseDotColors = listOf(
    0xFF7E57C2, 0xFF5C6BC0, 0xFF26A69A, 0xFF8D6E63,
    0xFF66BB6A, 0xFFEC407A, 0xFFFF7043, 0xFF42A5F5,
)

/**
 * 书籍栏书名的颜色：照 v10 `bookNameEditor.setTextColor("#333333")` 取同一枚深灰。
 *
 * 为什么硬编码不走 colorScheme：v10 里这枚色是固定的（与主题无关），
 * 「书名的字体和颜色参考 v10」；之前用过 primary（绿主题下书名染成绿色）已被否。
 * 集中成常量便于日后调，不要在调用点写散色值。
 */
private val BOOK_NAME_COLOR = Color(0xFF333333)

/**
 * 标签/书栏/胶囊统一底色（2026-10-11 Radix 版，用户拍板「标签用 step4」）：
 * 取 secondaryContainer——Color2 浅色里已改为 Radix accent step4（GREEN=#D6F1DF），
 * 比卡底（surfaceContainerLow=accent step2 #F4FBF6）深两档，标签块在页底/卡上都看得见；
 * 十主题自动对齐各自 Radix 族的 step4，深色走各族 dark 表。
 *
 * 演进史：secondaryContainer→background 插值 0.4→0.65→14f217c 与卡底同源（=surfaceContainerLow，
 * 装机实锤「标签太淡」：与页底只差 9/5/4 个 RGB，块看不见）→ 改回 secondaryContainer 但
 * 色阶已整体换 Radix，标签(step4)与卡底(step2)天生分开，不再撞色。
 * 文字仍用 onSecondaryContainer：对比度只会更大。
 *
 * 10-12 晚本页「发音人标签」曾另立一档硬编码浅色（底 #F0FAF3 / 字 #006D3A），
 * 10-13 用户实拍发现「它不跟随主题而变化」⇒ 该档退役，退回下面这个单源。
 * ⚠️ 别再给本页标签引浅色专用色值：十套主题 × 深浅色共 20 组，硬编码只能覆盖一组。
 */
@Composable
internal fun softContainerColor(): Color = MaterialTheme.colorScheme.secondaryContainer

/**
 * 浅色填充条（书籍框 / 显示名标签框共用·11-10 十四改）。
 *
 * ## 为什么抽成一个函数
 *
 * 用户令「书籍框调成和标签框一样的中淡绿」。两者在角色页上是相邻的两个可点条状物，
 * 浓度必须**永远一致**——写成两处 `lerp(..., 0.10f)` 也能跑，但以后调浓度时
 * 极容易只改一处，又变成"一大一小两档绿"（这正是 12 改之前的毛病）。
 * 抽成单源后，改浓度只改下面这个 0.10f。
 *
 * ## 公式
 *
 * 卡底（surfaceContainerLow，中性灰·用户 04:39 定）往主色混 10%。
 * - 起点是**卡底**而不是 Highest：Highest 本身 L≈0.92，再混主色会掉到 0.86，发暗发脏。
 * - 混的是 primary 而不是从 secondaryContainer 取：用户 06:10 从五档里选定的
 *   就是「卡底混 10%」这一档（绿 ≈ #E2F0E9），这里只是把它复用给书籍框。
 *
 * 四套主题自动成立，各自带自己色相（绿 #E2F0E9 / 紫 / 蓝 / 深灰）。
 */
@Composable
internal fun softFillBarColor(): Color = lerp(
    MaterialTheme.colorScheme.surfaceContainerLow,
    MaterialTheme.colorScheme.primary,
    0.10f
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RoleListScreen(
    tagRuleId: String,
    reloadKey: Int,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ===== 数据 =====
    var version by remember { mutableIntStateOf(0) }
    var records by remember { mutableStateOf<List<CharacterRecordsFile.RoleRecord>>(emptyList()) }
    var marks by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var voiceNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var currentBook by remember { mutableStateOf("") }
    LaunchedEffect(version, reloadKey) {
        val loaded = withIO {
            KeyListFile.writeFolderGuide(tagRuleId) // 文件说明.txt（幂等；RoleManagementScreen 与本页共用此加载链）
            val recs = CharacterRecordsFile.readRecords(tagRuleId)
            val groups = dbm.systemTtsV2.getAllGroupWithTts()
            val nameMap = LinkedHashMap<String, String>()
            groups.forEach { g ->
                g.list.forEach { item ->
                    // 只认**启用**配置（与角色管理 v10 getVoiceByTag 查 allEnabled 同口径）：
                    // 某标签一条启用项都查不到 → 角色行显示「标签 + ⚠」失效态
                    // （删掉启用项后标签就该变成失效态，这是以前的逻辑）
                    if (!item.isEnabled) return@forEach
                    val cfg = item.config as? TtsConfigurationDTO ?: return@forEach
                    val tag = cfg.speechRule?.tag?.trim().orEmpty()
                    if (tag.isEmpty()) return@forEach
                    if (tag !in nameMap) nameMap[tag] = item.displayName
                }
            }
            listOf(recs, nameMap)
        }
        @Suppress("UNCHECKED_CAST")
        records = loaded[0] as List<CharacterRecordsFile.RoleRecord>
        @Suppress("UNCHECKED_CAST")
        voiceNames = loaded[1] as Map<String, String>
        marks = VoiceMarksFile.readAll(tagRuleId)
        currentBook = CharacterRecordsFile.readCurrentBook(tagRuleId)
    }

    fun reload() { version++ }

    // 照插件 onLoadUI：①先跑 initializeFileSystem 自愈（cunfang 空→「默认」、角色数据同步进
    // shuming.<当前书>、当前书补进 liebiao.json 末尾、保证「默认」在列表、去重后落盘）
    // ②再按自动备份开关执行一次备份
    LaunchedEffect(reloadKey) {
        withIO {
            CharacterRecordsFile.initializeFileSystem(tagRuleId)
            if (CharacterRecordsFile.readAutoBackupEnabled(tagRuleId)) {
                CharacterRecordsFile.backupAllFiles(tagRuleId)
            }
        }
    }

    fun toast(resId: Int, vararg args: Any) {
        // 无参不过 format（见 lib-common ToastUtils.getStringSafe）：否则串里留 %1$s 就崩
        val msg = if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args)
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    // ===== 底栏回显钩子（10-14 用户实报「滑动时有时候底栏出不来」，老大难收口）=====
    // 病根：本页是全站唯一没挂回显钩子的滚动列表页——日志/系统TTS列表/设置三页都有
    // ControlBottomBarVisibility，唯独角色管理漏了。底栏隐藏靠 M3 exitAlways（列表上滑收起），
    // 但它的自动回弹阈值=一整条底栏高（80dp），列表中间轻轻往回滑一点不出来；
    // 滑到列表底部继续上滑更不回。挂上同款钩子：往回滑一点、滑到顶、滑到底都立即复位底栏。
    val listState = rememberLazyListState()
    ControlBottomBarVisibility(listState, LocalBottomBarBehavior.current)

    // ===== 搜索 + 多选标记 =====
    var keyword by rememberSaveable { mutableStateOf("") }
    // 标记用**文件下标**（照插件 markedIndices）：同名两条记录能分别标记/删除。
    // 旧版标记的是名字，同名记录只能一起操作——「删一条结果两条都没了」就是这么来的。
    var markedIdx by remember { mutableStateOf<Set<Int>>(emptySet()) }
    fun toggleMark(idx: Int) {
        markedIdx = if (idx in markedIdx) markedIdx - idx else markedIdx + idx
    }
    // 带文件下标的筛选结果：列表行 key / 标记 / 删除 / 改名 / 设主角全部用下标做身份，名字不再承担身份。
    // 搜索字段照插件 filterCharacterList(8019-8031)：**name / aliases / gender / age / voice 五字段**，
    // 任一命中即显示（旧版只看 name + aliases —— 按「男主」「男青年01」这类性别/年龄/发音人查会以为没这个角色）。
    val filtered = records.withIndex().filter { (_, rec) ->
        keyword.isBlank() ||
            rec.name.contains(keyword, true) ||
            CharacterRecordsFile.splitAliases(rec.aliases).any { it.contains(keyword, true) } ||
            rec.gender.contains(keyword, true) ||
            rec.age.contains(keyword, true) ||
            rec.voice.contains(keyword, true)
    }
    val selectableIdx = filtered.map { it.index }.toSet()

    // ===== 弹窗状态 =====
    var menuFor by remember { mutableStateOf<Pair<Int, CharacterRecordsFile.RoleRecord>?>(null) }
    var deleteIdx by remember { mutableStateOf<Set<Int>?>(null) }
    var editFor by remember { mutableStateOf<Pair<Int, CharacterRecordsFile.RoleRecord>?>(null) }
    var releaseForIdx by remember { mutableStateOf<Int?>(null) }
    var releaseTargetFor by remember { mutableStateOf<Pair<String, String>?>(null) } // (ownerName, releaseName) 释放并固定 → 换声弹窗
    // 添加角色（终版）：直接开绑定类换声弹窗，顶部内嵌角色名填写框，
    // 选好发音人确认即建记录（voice=tag id）——独立名字弹窗/选关键词旧流程均废
    var addingChar by remember { mutableStateOf(false) }
    var mergeFollowFor by remember { mutableStateOf<List<String>?>(null) } // 标记的角色名列表，选目标
    var mergeVoiceTarget by remember { mutableStateOf<String?>(null) } // 合并+选择发音人：目标角色
    var pickerFor by remember { mutableStateOf<CharacterRecordsFile.RoleRecord?>(null) } // 换声（标签框点击）
    // ⋮ → 删除配置项（11-08 角色行加 ⋮ 菜单）：复用换声弹窗同款删除链，
    // 弹窗渲染照 VoicePickerDialog.deleteConfirmTarget 抄（确认文案+联动重分配在 onDeleteConfigConfirmed）
    var deleteConfigTarget by remember { mutableStateOf<SystemTtsV2?>(null) }
    // ▶ 行内试听状态（与换声弹窗同链）：previewingIdx=正在试听的行下标（全局单实例播放器）
    val previewState by TaggedTtsPreviewPlayer.state.collectAsStateWithLifecycle()
    var previewingIdx by remember { mutableStateOf<Int?>(null) }
    var showBookDialog by remember { mutableStateOf(false) }
    // 密钥管理/备份恢复入口已上移到宿主顶栏（给角色区留空），
    // 弹窗状态与渲染都在 RoleManagementScreen，本页不再持有

    Column(modifier) {
        // ===== 书籍栏（照插件：圆角卡片 = 📖 + 书名 + ✎ 行内改名 + ▾ 管理；
        //      整条卡片点击即展开书籍列表弹窗（插件 showBookSwitchDialog 同入口：书名框与箭头共用））=====
        var editingBook by remember { mutableStateOf(false) }
        var bookEditName by remember { mutableStateOf(TextFieldValue("")) }
        // 书名编辑收尾（照插件 v10 endBookEdit 口径，所有退出路径共用）：
        // 空名回滚原书名、与原书名相同直接退出、真改了才写文件
        fun endBookEdit(save: Boolean) {
            if (!editingBook) return
            val target = bookEditName.text.trim()
            editingBook = false
            bookEditName = TextFieldValue(currentBook, TextRange(currentBook.length))
            if (!save || target.isEmpty() || target == currentBook) return
            scope.launch {
                val ok = withIO { CharacterRecordsFile.renameCurrentBook(tagRuleId, target) }
                toast(if (ok) R.string.role_book_renamed else R.string.role_list_failed, target)
                if (ok) reload()
            }
        }
        // 返回键先退编辑态（编辑态原本只有 ✓ 一个出口，✓ 一旦不可用就困在里面）
        BackHandler(enabled = editingBook) { endBookEdit(save = false) }
        // 进编辑态即聚焦（照插件 v10：requestFocus + 弹软键盘，省得再点一下输入框）
        val bookFocus = remember { FocusRequester() }
        LaunchedEffect(editingBook) {
            if (editingBook) runCatching { bookFocus.requestFocus() }
        }
        Surface(
            shape = AppShapes.lg,
            // 书栏底色（11-10 十四改，用户令「调成和标签框一样的中淡绿」）：
            // 与角色行显示名标签框**同一个公式、同一个浓度**——
            // lerp(surfaceContainerLow, primary, 0.10f)，GREEN 浅色 ≈ #E2F0E9
            //（L=0.943 / C=0.018）。
            //
            // ## 为什么改成和标签框一致
            //
            // 十二改曾用 10-13 旧公式 lerp(secondaryContainer, Low, 0.25f) → #DFF3E6。
            // 那个值**彩度其实不低（0.027）、但明度 0.946 是几档里最亮的**，
            // 所以用户实拍「书籍框这个填充色太浅了」——问题在明度不在彩度。
            // 统一成标签框那档后：明度降到 0.943、与标签完全同色，
            // 页面上"书籍条 / 标签"两个可点块连成一套语汇，不再一大一小两档绿。
            //
            // ⚠️ 抽成 bookBarFillColor() 是为了让标签框和书籍框**永远同步**：
            // 以后调浓度只改这一处，不会出现"改了标签忘了书籍框"。
            //
            // 卡底（surfaceContainerLow）保持中性灰不动——那是用户 04:39 定下的；
            // 本处只是"从灰卡底上浮起一块带主题色的条"，不改卡底本身。
            color = softFillBarColor(),
            modifier = Modifier
                .fillMaxWidth()
                // 卡缘 = ListGutter 16（v3 边距统一）：与搜索框描边/列表行同线，
                // 框的从属靠框内 padding（内层 start 4）表达
                .padding(horizontal = ListGutter, vertical = 4.dp)
                // 编辑态不触发展开，避免打断改名输入
                .clickable(enabled = !editingBook) { showBookDialog = true },
        ) {
            Row(
                // start=4：外层 16（gutter）+ 内层 4 = 20dp 文字左缘（框内自理，不再对齐外部元素；
                // 旧口径「外 8+内 8=16 与搜索框对齐」随 v3 边距统一作废——框缘已与搜索框同线）
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 彩色 📖 emoji（拍板：书籍图标要彩色；推翻 09-14「入口类 emoji
                // 清零」口径——那条是通用纪律，此处是用户对书籍图标的明确偏好，偏好优先）
                Text(
                    "📖",
                    // 14sp 照 v10（bookLabel.setTextSize(14)）：18sp 的 emoji 白吃约 4dp 宽，
                    // 书名一行放不下就折第二行
                    fontSize = 14.sp,
                )
                Spacer(Modifier.width(8.dp))
                if (editingBook) {
                    // 紧凑行内编辑（二次修：去内层描边/填色——书栏卡本身已是
                    // 灰底块，里面再套一个描边输入框成「框套框」；改无框裸 BasicTextField
                    // 直躺卡上，光标即编辑态，✓✕ 收尾不变。
                    // 出口三个：✓ / ✕ / 键盘回车，空名回滚由 endBookEdit 兜底）
                    Box(Modifier.weight(1f).height(40.dp)) {
                        BasicTextField(
                            value = bookEditName,
                            onValueChange = { bookEditName = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSecondaryContainer),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { endBookEdit(save = true) }),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)
                                .focusRequester(bookFocus),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (bookEditName.text.isBlank()) Text(
                                        stringResource(R.string.role_book_name),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    inner()
                                }
                            }
                        )
                    }
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable { endBookEdit(save = true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable { endBookEdit(save = false) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                    }
                } else {
                    Text(
                        currentBook,
                        // 字体与颜色照 v10 —— bookNameEditor.setTextSize(16) +
                        // Typeface.DEFAULT_BOLD + setTextColor("#333333") ⇒ 16sp 加粗深灰；
                        // 这是硬编码色（与 v10 一致），不走 colorScheme
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = BOOK_NAME_COLOR,
                        modifier = Modifier.weight(1f),
                    )
                    // ✎ / 管理 两个裸文本键（内边距 12/4/12/4 与 8/4/8/4、14sp）：
                    // 原先的 TextButton 自带 58dp 最小宽 + 12dp 内边距，两个键多占约 23dp
                    Text(
                        // 「编辑」→ 回 ✎（复验：汉字键「很一般」，且加粗书名字宽涨 5%、
                        // 12 字需求 192→约 202dp，汉字键比 ✎ 宽约 10dp 正好把书名挤到第二行）
                        "✎",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(AppShapes.sm)
                            .clickable {
                                bookEditName = TextFieldValue(currentBook, TextRange(currentBook.length))
                                editingBook = true
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        // 去「▾」前缀：箭头语义是「就地展开」，而本键打开的是书籍列表
                        // 弹窗；且整条卡片本身可点、入口相同，箭头无信息增量，还白占约 12dp 宽
                        stringResource(R.string.role_book_manage),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(AppShapes.sm)
                            .clickable { showBookDialog = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        // ===== 角色区头（精简：删「👤 角色列表:」前缀——搜索框 hint 自说明，
        //      搜索框（12dp圆角、hint自带🔍、无放大镜图标）占满整行，
        //      全选=文字键嵌搜索框右端，选中态红色显「取消全选」）=====
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val allSelected = markedIdx.containsAll(selectableIdx) && selectableIdx.isNotEmpty()
            // 紧凑搜索框（OutlinedTextField 最小高 56dp 偏高）——
            // Surface+BasicTextField 手搓 44dp，外观保持 12dp 圆角描边；hint 手绘、光标主色
            Surface(
                shape = AppShapes.lg,
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                // 左右各 8 + 外层行 padding 8 ⇒ 框缘 16，与书籍卡 / 列表行 / 提示行同线。
                // 原只有 start=8：框右缘落在 8、比书籍卡（16）右凸 8dp，整页两套右缘（10-04 用户实机指出）
                modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp).height(44.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                        decorationBox = { inner ->
                            Box {
                                if (keyword.isBlank()) Text(
                                    stringResource(R.string.role_search_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                inner()
                            }
                        }
                    )
                    // 方案A 文字键（用户 09-14 定）：无框无底、主色文字——原「填色+描边」
                    // 双重强调是全页唯一彩色填充控件，与「填色=区块/状态」全局语汇冲突；
                    // 选中态=红色「取消全选」，警示语义保留（与模型拉取弹窗 TextButton 同款）；
                    // 关涟漪防高亮叠在搜索框描边内显得脏（同名字列口径）
                    Text(
                        stringResource(
                            if (allSelected) R.string.role_select_all_cancel
                            else R.string.role_list_select_all
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (allSelected) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                markedIdx = if (allSelected) emptySet() else selectableIdx
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
        // 操作提示行（10-08 用户令：框下文字要缩进一档——它是搜索框的附属说明，
        // 跟框内文字（框缘 16 + start 12 = 28）同线，不再与列表行齐 16）
        Text(
            stringResource(R.string.role_hint_line),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                start = ListGutter + 12.dp, end = ListGutter, top = 2.dp, bottom = 2.dp
            )
        )

        // ===== 平铺角色列表（完全展开）=====
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            // 左右基准线 16（v3 边距统一，M3 gutter）：行内水平 padding 归零后，
            // 圆点/文字/选中块直接落这条线，与搜索框描边（16）、书籍卡缘（16）同线
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = ListGutter, end = ListGutter, top = 2.dp, bottom = bottomPadding + 96.dp
            )
        ) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.role_list_empty),
                        // 空状态字号缩小一档（bodyLarge 16sp → bodyMedium 14sp），
                        // 文案已精简为「暂无角色，朗读后自动生成」
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            filtered.forEach { (idx, rec) ->
                // 行 key 用下标：同名两条记录（合并产物 / 框架回写）不会再撞 key（旧版 name+voice 撞了会崩）
                val key = "row_$idx"
                item(key = key) {
                    // 发音人配置项（试听/⋮删除配置项的锚点）：按 rec.voice 查启用配置，
                    // 查不到（失效标签）时 ▶/⋮ 相应禁用——▶ 照旧显示但点了无效，
                    // ⋮ 删除项置灰（deleteEnabled=false，与换声弹窗顶部菜单同口径）
                    val anchorEntity = remember(rec.voice, version) {
                        dbm.systemTtsV2.allEnabled.firstOrNull { item ->
                            (item.config as? TtsConfigurationDTO)?.speechRule?.tag == rec.voice
                        }
                    }
                    // 插件名（注释行缀尾，前 4 字——与日志插件牌 PLUGIN_NAME_MAX_CHARS 同口径）
                    // 注意：config 静态类型是 IConfiguration（无 source 字段），
                    // 必须先 as? TtsConfigurationDTO 才能取 source（CI 实锤 :538 Unresolved 'source'）
                    val pluginNameShort = remember(anchorEntity?.id) {
                        val src = (anchorEntity?.config as? TtsConfigurationDTO)?.source as? PluginTtsSource
                        src?.let { dbm.pluginDao.getMetaByPluginId(it.pluginId)?.name }
                            ?.replace('|', '｜')
                            ?.take(4)
                            ?.takeUnless { it.isBlank() }
                    }
                    RoleRow(
                        rec = rec,
                        voiceName = voiceTagText(rec.voice, voiceNames),
                        displayName = voiceNames[rec.voice],
                        pluginName = pluginNameShort,
                        hasAnchor = anchorEntity != null,
                        marks = marks[rec.voice].orEmpty(),
                        marked = idx in markedIdx,
                        onNameClick = { toggleMark(idx) },
                        onNameLongClick = { menuFor = idx to rec },
                        onTagClick = { pickerFor = rec },
                        onReassign = {
                            scope.launch {
                                val newVoice = withIO {
                                    // 当前启用标签集合（与换声弹窗候选池同口径：只认启用配置）
                                    val enabledTags = dbm.systemTtsV2.allEnabled.mapNotNull { item ->
                                        (item.config as? TtsConfigurationDTO)?.speechRule?.tag
                                            ?.takeIf { it.isNotEmpty() }
                                    }.toSet()
                                    CharacterRecordsFile.rerollVoice(tagRuleId, idx, enabledTags)
                                }
                                if (newVoice != null) {
                                    toast(R.string.role_reassign_ok, rec.name, newVoice)
                                    reload()
                                } else {
                                    toast(R.string.role_reassign_none)
                                }
                            }
                        },
                        // ▶ 试听（与换声弹窗顶部试听同链）：TaggedTtsPreviewPlayer 全局单实例，
                        // 同一时刻只有一个试听；再点=停。实体=该角色绑定的启用配置项。
                        onPreview = {
                            val entity = anchorEntity
                            if (entity != null) {
                                if (previewingIdx == idx && previewState != PreviewState.IDLE) {
                                    TaggedTtsPreviewPlayer.stop()
                                    previewingIdx = null
                                } else {
                                    previewingIdx = idx
                                    // 点击即置「合成中」（v10 口径）：不等 play() 异步置位
                                    TaggedTtsPreviewPlayer.markSynthesizing()
                                    scope.launch {
                                        TaggedTtsPreviewPlayer.play(
                                            context, entity, "你好，这是试听语音。"
                                        )
                                    }
                                }
                            }
                        },
                        previewActive = previewingIdx == idx && previewState != PreviewState.IDLE,
                        previewSynthesizing = previewingIdx == idx &&
                            previewState == PreviewState.SYNTHESIZING,
                        // 标记 toggle（QQ 式取消）：文件通道无观察者，靠 marksVersion 刷新
                        onToggleMark = { mark ->
                            scope.launch {
                                if (VoiceMarksFile.toggle(tagRuleId, rec.voice, mark)) {
                                    marks = VoiceMarksFile.readAll(tagRuleId)
                                }
                            }
                        },
                        // ⋮ → 删除配置项：复用换声弹窗同款删除链（确认弹窗 + 联动重分配）
                        onDeleteConfig = {
                            anchorEntity?.let { deleteConfigTarget = it }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
            // 「+ 添加角色」行（照插件：列表下方）
            item(key = "add_row") {
                // 照插件 addCharacterRow：低调灰色小字（12sp #9E9E9E 居中），刻意不抢眼
                Text(
                    stringResource(R.string.role_add_character),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color(0xFF9E9E9E),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { addingChar = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    // ===== 换声（发音人标签框点击，与日志弹窗同款）=====
    pickerFor?.let { rec ->
        VoicePickerDialog(
            anchorConfigId = null,
            anchorTag = rec.voice,
            bindingKey = rec.name,
            titleBadge = rec.name,
            onChanged = { _, _ -> reload() },
            onDismissRequest = { pickerFor = null },
        )
    }
    // ===== ⋮ → 删除配置项（11-08 角色行菜单）：确认弹窗+删除链照换声弹窗
    // deleteConfirmTarget / deletePreviewedConfig 一字抄（VoicePickerDialog.kt:1679/425）——
    // ①删配置项+清失效引擎缓存 ②标签删空才从池子移除 ③删空立即给受影响角色重分配；
    // 本页无 anchorVersion/dataVersion 机制，落库后 reload() 重读全部文件即同步
    deleteConfigTarget?.let { delTarget ->
        val delDto = delTarget.config as? TtsConfigurationDTO
        val delTag = delDto?.speechRule?.tag.orEmpty()
        val tagRuleIdDel = delDto?.speechRule?.tagRuleId.orEmpty()
        val tagLabel = voiceNames[delTag]
        val tagShown = tagLabel?.ifBlank { null } ?: delTag
        val assigned = remember(delTarget.id) {
            CharacterRecordsFile.readVoiceOwnerMap(tagRuleIdDel)[delTag]
                .orEmpty().isNotEmpty()
        }
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = { deleteConfigTarget = null },
            title = { Text(stringResource(R.string.role_voice_del_title)) },
            text = {
                Text(
                    stringResource(R.string.role_voice_del_msg_title, tagShown, delTarget.displayName) +
                        "\n\n" +
                        stringResource(
                            if (assigned) R.string.role_voice_del_msg_used
                            else R.string.role_voice_del_msg_unused
                        ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfigTarget = null
                    scope.launch {
                        var reassignedCount = 0
                        val removed = withIO {
                            dbm.systemTtsV2.delete(delTarget)
                            // delDto 非空才清引擎缓存（removeEngine 不收可空）；
                            // config 非 TtsConfigurationDTO 的极端条目无需清（无引擎实例）
                            (delDto?.source)?.let { src ->
                                runCatching { CachedEngineManager.removeEngine(src) }
                            }
                            val stillEnabled = dbm.systemTtsV2.allEnabled.any { item ->
                                val d = item.config as? TtsConfigurationDTO ?: return@any false
                                d.speechRule.tagRuleId == tagRuleIdDel && d.speechRule.tag == delTag
                            }
                            if (!stillEnabled && delTag.isNotBlank()) {
                                CharacterRecordsFile.removeFromPool(tagRuleIdDel, delTag)
                                val enabledTags = dbm.systemTtsV2.allEnabled.mapNotNull { item ->
                                    (item.config as? TtsConfigurationDTO)?.speechRule?.tag
                                        ?.takeIf { it.isNotEmpty() }
                                }.toSet()
                                reassignedCount = CharacterRecordsFile.reassignVoicesForTag(
                                    tagRuleIdDel, delTag, enabledTags
                                )
                            }
                            true
                        }
                        SystemTtsService.notifyUpdateConfig()
                        // toast 按文案实际占位符个数传参（getString 多余 vararg 不报错但不严谨）：
                        // reassigned 文案要 2 参（名+数）、deleted 文案只收 1 参（名）
                        if (removed) {
                            if (reassignedCount > 0) {
                                toast(R.string.role_voice_del_reassigned, delTarget.displayName, reassignedCount)
                            } else {
                                toast(R.string.role_voice_del_toast, delTarget.displayName)
                            }
                        }
                        // 被删的恰是本行绑定：失效态立刻反映（voiceNames 重读后查不到配置 → ⚠）
                        reload()
                    }
                }) { Text(stringResource(R.string.role_voice_del_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfigTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    // ===== 合并+选择发音人：目标=长按角色，选完发音人后把其余标记角色并入 =====
    mergeVoiceTarget?.let { target ->
        val others = markedIdx.mapNotNull { records.getOrNull(it)?.name }.filter { it != target }.toSet()
        VoicePickerDialog(
            anchorConfigId = null,
            anchorTag = records.firstOrNull { it.name == target }?.voice.orEmpty(),
            bindingKey = target,
            titleBadge = target,
            onChanged = { _, _ ->
                scope.launch {
                    val n = withIO { CharacterRecordsFile.mergeCharacters(tagRuleId, target, others) }
                    toast(if (n > 0) R.string.role_list_merge_toast else R.string.role_list_failed, n)
                    markedIdx = emptySet()
                    reload()
                }
            },
            onDismissRequest = { mergeVoiceTarget = null },
        )
    }

    // ===== 长按操作菜单（照插件 showFirstDialog 动态项）=====
    menuFor?.let { (idx, rec) ->
        val markCount = markedIdx.size + if (idx in markedIdx) 0 else 1
        val hasMerged = CharacterRecordsFile.splitAliases(rec.aliases)
            .any { it.trim() != rec.name.trim() }
        AlertDialog(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = { menuFor = null },
            title = { Text(stringResource(R.string.role_menu_title)) },
            text = {
                Column {
                    if (markCount >= 2) {
                        MenuActionRow(stringResource(R.string.role_menu_merge_follow), "👥") {
                            menuFor = null
                            mergeFollowFor = (markedIdx + idx).mapNotNull { records.getOrNull(it)?.name }
                        }
                        MenuActionRow(stringResource(R.string.role_menu_merge_voice), "🤝") {
                            menuFor = null
                            markedIdx = markedIdx + idx
                            mergeVoiceTarget = rec.name
                        }
                    }
                    if (hasMerged) {
                        MenuActionRow(stringResource(R.string.role_menu_release), "🔓") {
                            menuFor = null
                            releaseForIdx = idx
                        }
                    }
                    if (markCount < 2) {
                        MenuActionRow(stringResource(R.string.role_list_menu_rename), "✏️") {
                            menuFor = null
                            editFor = idx to rec
                        }
                    }
                    MenuActionRow(stringResource(R.string.role_list_menu_delete), "🗑️") {
                        menuFor = null
                        deleteIdx = markedIdx + idx
                    }
                    MenuActionRow(stringResource(R.string.role_list_menu_set_main), "⭐") {
                        menuFor = null
                        scope.launch {
                            // 按下标改（照插件 setAsMainCharacter：`characterRecords[longPressedIndex]`
                            // 只动长按的那一条；旧版按名字改，同名两条会一起变主角）
                            val ok = withIO { CharacterRecordsFile.setMainCharacterAt(tagRuleId, idx) }
                            toast(
                                if (ok) R.string.role_list_set_main_toast else R.string.role_list_failed,
                                rec.name
                            )
                            if (ok) reload()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { menuFor = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    // ===== 合并+跟随角色：在标记里选目标 =====
    mergeFollowFor?.let { candidates ->
        AlertDialog(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = { mergeFollowFor = null },
            title = { Text(stringResource(R.string.role_merge_follow_title)) },
            text = {
                Column {
                    candidates.forEach { name ->
                        MenuActionRow(name) {
                            mergeFollowFor = null
                            val others = candidates.filter { it != name }.toSet()
                            scope.launch {
                                val n = withIO { CharacterRecordsFile.mergeCharacters(tagRuleId, name, others) }
                                toast(if (n > 0) R.string.role_list_merge_toast else R.string.role_list_failed, n)
                                markedIdx = emptySet()
                                reload()
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { mergeFollowFor = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // ===== 释放/删除已合并角色（照插件：名字列表逐行 释放并固定/删除）=====
    // 存**下标**而不是记录快照：一个别名被释放之后（尤其释放的正是主名——aliases 首个顶上），
    // 在同一个弹窗里接着释放第二个时，若拿旧快照的 ownerName 去定位会匹配不上原角色
    // （releaseAndFix 按名字找 owner）。按下标每帧重取最新记录，连续释放才对得上。
    releaseForIdx?.let { ridx ->
        records.getOrNull(ridx)?.let { rec ->
            ReleaseDialog(
                rec = rec,
                onDismiss = { releaseForIdx = null },
                onFix = { owner, name ->
                    releaseTargetFor = owner to name
                },
                onDelete = { owner, name ->
                    scope.launch {
                        val ok = withIO { CharacterRecordsFile.removeNameFromRecord(tagRuleId, owner, name) }
                        toast(if (ok) R.string.role_list_delete_toast else R.string.role_list_failed, 1)
                        if (ok) reload()
                    }
                },
            )
        }
    }
    // 释放并固定 → 直接开换声弹窗（这一步本质＝「解绑别名 + 另立门户换发音人」，
    // 先选一个关键词、再拿关键词当发音人是多余一跳）。落库仍走 releaseAndFix，其余链路
    // （候选 / 试听 / 确认键 / Toast）与「+ 添加角色」完全同源——只有一处实现。
    // 顺带修正口径：原先写进 voice 的是裸关键词（「女青年」），现在是 tag id（「女青年01」），
    // 与本 App「voice=tag id」的数据链一致——旧写法朗读时根本匹配不上发音人。
    // 锚点（11-08 用户令「分类跟随之前」）：换声弹窗的分类下拉默认选中锚点 tag 的分类，
    // 旧写法抓「第一条有声音的记录」当锚点 → 释放后分类落在别人头上，每次手动切。
    // 改为取**被释放名字所属记录**的 voice 当锚点：从哪个分类释放的，就停在哪个分类。
    // 记录按名字匹配（主名或别名）；voice 空白才回落池子首个（与添加角色同兜底）。
    releaseTargetFor?.let { (owner, name) ->
        val ownerTag = records.firstOrNull { rec ->
            rec.name == name || CharacterRecordsFile.splitAliases(rec.aliases).contains(name)
        }?.voice.takeUnless { it.isNullOrBlank() }
        val anchorTag = ownerTag
            ?: records.firstOrNull { it.voice.isNotBlank() }?.voice
            ?: CharacterRecordsFile.readVoicePool(tagRuleId).firstOrNull().orEmpty()
        VoicePickerDialog(
            anchorConfigId = null,
            anchorTag = anchorTag,
            bindingKey = "",
            titleBadge = "",
            createIfMissing = true,
            releaseOwnerName = owner,
            releaseName = name,
            onChanged = { _, _ -> reload() },
            onDismissRequest = { releaseTargetFor = null },
        )
    }

    // ===== 修改角色名（多行名称编辑器：主名+别名）=====
    editFor?.let { (idx, rec) ->
        EditNamesDialog(
            initialNames = listOf(rec.name) + CharacterRecordsFile.splitAliases(rec.aliases).filter { it != rec.name },
            onDismiss = { editFor = null },
            onConfirm = { names ->
                scope.launch {
                    // 按下标改（照插件 showEditCharacterDialog 用 position 定位、names 去重）：
                    // 旧版按名字改，同名两条会一起被改，且重复名会被写进 aliases 两条
                    val ok = withIO { CharacterRecordsFile.editCharacterNamesAt(tagRuleId, idx, names) }
                    toast(if (ok) R.string.role_list_rename_toast else R.string.role_list_failed)
                    editFor = null
                    if (ok) reload()
                }
            }
        )
    }

    // ===== 删除角色（单/多共用，对全部标记生效）=====
    deleteIdx?.let { targets ->
        val names = targets.mapNotNull { records.getOrNull(it)?.name }
        AlertDialog(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = { deleteIdx = null },
            title = { Text(stringResource(R.string.role_list_delete_title)) },
            text = { Text(stringResource(R.string.role_list_delete_text, names.size, names.take(5).joinToString("、"))) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        // 按下标删（照插件 doDeleteCharacterOperation 只保留未标记 index）：
                        // 旧版按名字删，文件里同名两条会一起没
                        val n = withIO { CharacterRecordsFile.deleteRecordsAt(tagRuleId, targets) }
                        toast(if (n > 0) R.string.role_list_delete_toast else R.string.role_list_failed, n)
                        deleteIdx = null
                        markedIdx = emptySet()
                        if (n > 0) reload()
                    }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteIdx = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // ===== 添加角色（终版）：直接开绑定类换声弹窗，角色名在弹窗顶部填写，
    // 选好发音人确认即建记录（voice=tag id）——锚点只需有效（参数/显示基准）：
    // 优先取现有角色的绑定标签，无记录回落池子首个；候选列表=池子∩启用标签，与锚点无关 =====
    if (addingChar) {
        val anchorTag = records.firstOrNull { it.voice.isNotBlank() }?.voice
            ?: CharacterRecordsFile.readVoicePool(tagRuleId).firstOrNull().orEmpty()
        VoicePickerDialog(
            anchorConfigId = null,
            anchorTag = anchorTag,
            bindingKey = "",
            titleBadge = "",
            createIfMissing = true,
            onChanged = { _, _ -> reload() },
            onDismissRequest = { addingChar = false },
        )
    }

    // ===== 书籍管理弹窗（切换/✕删除/新增/多选删除）=====
    if (showBookDialog) {
        BookManagerDialog(
            tagRuleId = tagRuleId,
            onDismiss = { showBookDialog = false },
            onSwitched = { reload() },
        )
    }
    // 修改书名：已改为书籍栏 ✎ 行内编辑（照插件），弹窗通道退役
}

/**
 * 菜单动作行（emoji + 文字；0919 用户拍板：彩色圆点→矢量图标→emoji 终稿——emoji 自带彩色、
 * 表意直白，比单色矢量图标更有辨识度）：合并跟随 👥 / 合并音色 🎭 / 释放 ✂️ /
 * 修改角色名 ✏️ / 删除角色 🗑️ / 设为主角 ⭐。
 * 字号 bodyLarge 16sp（0919 用户反馈偏小，推翻 0911 对齐 14sp 旧口径——以用户最新体感为准）。
 * emoji 传 null = 纯文字（合并跟随的候选名单行用）。
 */
@Composable
private fun MenuActionRow(
    text: String,
    emoji: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (emoji != null) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * 发音人标签有效性（11-08 重构）：tag 在启用配置里能查到显示名=有效；查不到=null
 * （RoleRow 标签框回落 tag + 红 ⚠）。旧「tag+显示名连写 + 15 字预算预切」整链退役——
 * 11-08 重构后标签框只放分类 tag（用户令），显示名沉到注释行、按实际框宽 Ellipsis。
 */
private fun voiceTagText(tag: String, nameMap: Map<String, String>): String? =
    nameMap[tag]

/** 标记 emoji → mark id 反查（chip 点击取消用；MARK_ITEMS 顺序即显示顺序） */
private fun emojiMarkOf(emoji: String): String =
    VoiceMarksFile.MARK_ITEMS.firstOrNull { it.second == emoji }?.first ?: ""

/**
 * 角色行（11-08 重构定稿）：
 * 左名字列竖排（主名第一行，别名从第二行起各占一行，每行=性别圆点+名称+收藏【】+主角👑）；
 * 右侧两行：
 *   第一行=发音人标签框（只放**分类 tag**如「女青年01」，点=换声、长按=随机换声）
 *     + ▶ 试听（▶/…/■ 三态，TaggedTtsPreviewPlayer 全局单实例）
 *     + ⋮ 菜单（❤️🚶😈 标记 toggle + 删除配置项，与换声弹窗 VoiceOverflowMenu 同款）；
 *   第二行=注释行：displayName（查不到=tag+⚠）·插件名（前4字）在左，
 *     已点亮标记 chip 钉**行尾**（点击即取消——QQ 式；11-08 用户令：
 *     chip 不够宽时削的是 displayName 字数，chip 永不被挤掉、更不竖排）。
 * 点击范围（11-08 用户令）：除标签框/▶/⋮ 各自吃掉点击外，**整行其余任何区域**
 * 点击=选中角色、长按=操作菜单。
 */

/**
 * 角色标签的文字色（11-10 五改，用户选定「浅胶囊」形态后配套定义）。
 *
 * ## 沿革（别再给绿色主题单独配色）
 *
 * 10-13 起：绿底绿字（secondaryContainer 底 + primary 字）。
 * 11-10 五改：形态改浅胶囊后，我给绿色主题单独配了深绿 #0A6E3B
 *（理由是"主色压灰底只 2.80:1，加深好读"）。
 * **11-10 六改：撤销那枚 #0A6E3B，四套主题一律用 primary**——
 * 用户明确要「发音人标签的绿改成和主题这个绿一样」，
 * 那枚加深的深绿偏离了主题主色，看着发闷，不是他要的那个绿。
 *
 * 代价如实记：绿色主题下 #109D58 压浅灰药丸底约 2.8~3.0:1，低于正文线 4.5。
 * 这是**用户明确选择**的结果，不是疏漏，别再擅自加深或换色。
 */
@Composable
private fun tagTextColor(): Color = MaterialTheme.colorScheme.primary

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RoleRow(
    rec: CharacterRecordsFile.RoleRecord,
    voiceName: String?,
    displayName: String?,
    pluginName: String?,
    hasAnchor: Boolean,
    marks: List<String>,
    marked: Boolean,
    onNameClick: () -> Unit,
    onNameLongClick: () -> Unit,
    onTagClick: () -> Unit,
    onReassign: () -> Unit,
    onPreview: () -> Unit,
    previewActive: Boolean,
    previewSynthesizing: Boolean,
    onToggleMark: (String) -> Unit,
    onDeleteConfig: () -> Unit,
) {
    val isFav = rec.obj.optInt("usageCount", 0) == 50
    val isProtagonist = rec.isMain
    // 名字列竖排的全部名称（照 v9：第一行主名，别名从第二行起各占一行）
    val nameList = (listOf(rec.name) + CharacterRecordsFile.splitAliases(rec.aliases))
        .distinctBy { it.trim() }
    // 名字列封顶半屏（沿 10-14 定稿）；右侧两行吃剩余
    val halfRow = ((LocalConfiguration.current.screenWidthDp - 2 * ListGutter.value) / 2).dp
    // ▶ 三态色（与换声弹窗 previewLabelColor 同源）：空闲=主色 / 合成中=琥珀 / 播放中=红
    val previewColor = when {
        !previewActive -> MaterialTheme.colorScheme.primary
        previewSynthesizing -> Color(0xFFFF6F00)
        else -> Color(0xFFF44336)
    }
    val previewLabel = when {
        !previewActive -> "▶"
        previewSynthesizing -> "…"
        else -> "■"
    }
    // 已点亮标记 chip（固定顺序，每枚一个 chip；未点亮不渲染）
    val litMarks = VoiceMarksFile.MARK_ITEMS.filter { it.first in marks }
    Surface(
        // 选中态（用户 09-14 二次修）：整行染色保留，但加 12dp 圆角、
        // 色阶从 surfaceContainerHighest 降到 surfaceContainerHigh 浅一档——
        // 原先是「一整块直角淡紫」很丑；行内容 padding 不变，选中不跳位
        shape = AppShapes.lg,
        color = if (marked) MaterialTheme.colorScheme.surfaceContainerHigh
        else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            // 纵向 10dp 保留（行高 ≥44dp 保证点击区域）。
            // 整行点击=选中/长按菜单（11-08 用户令「除标签外都能选中」）：
            // 关涟漪防与选中底色叠成第二块灰（名字列旧口径同款）；标签框/▶/⋮
            // 各自的 handler 挂在内层组件上，点击时内层优先消费、不会冒泡到这
            Modifier.fillMaxWidth()
                .combinedClickable(
                    onClick = onNameClick,
                    onLongClick = onNameLongClick,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                )
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 名字列：照 v9 排布（主名第一行，别名逐个竖排，每行带性别色圆点）
            Column(
                Modifier
                    .widthIn(max = halfRow)
                    .heightIn(min = 44.dp),
                // 11-09 用户令（图一实锤「第一行没对齐」）：撤 Center 改 Top——名字列
                // 与右列同一 Top 基准，标签框与第一行名字顶齐（原 Center 让名字短时
                // 名字块垂直居中，两列基准错位）
                verticalArrangement = Arrangement.Top,
            ) {
                nameList.forEachIndexed { idx, name ->
                    // 圆点 top 9dp 钉在首行文字中线（16sp/行高 24sp 几何中心，10-14 甲案）
                    Row(verticalAlignment = Alignment.Top) {
                        Spacer(
                            Modifier
                                .padding(start = 4.dp)
                                .padding(top = 9.dp)
                                .size(6.dp)
                                .background(genderDotColor(rec.voice), CircleShape)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = buildString {
                                if (isFav) append("【$name】") else append(name)
                                if (idx == 0 && isProtagonist) append(" 👑")
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            // 名字超半屏折行续排，不省略（10-12「显示完全」口径保持）
                        )
                    }
                    if (idx < nameList.lastIndex) Spacer(Modifier.height(2.dp))
                }
            }
            // 右侧两行（voice 空不渲染，照插件）
            if (rec.voice.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                // 11-09 用户令（图一实锤三问题）版式修正：
                // ①Column 改 Top 对齐——标签框与第一行角色名顶对齐（原 Center 让两行
                //   整体垂直居中，多行名字时标签框下坠不对齐）；
                // ②第一行 Row 加 fillMaxWidth——标签框/▶/⋮ 贴右缘（原 Row 只在
                //   Column 内容宽内 End，名字短时整组吊在名字旁边不贴右）
                // ③标签区域宽度规则不变=「行宽−名字列宽」，名字列仍按内容占宽封顶半屏
                //   （widthIn(max=halfRow)，上方名字列注释）——标签区域大小由名字长度间接决定
                // 11-09 装机二轮复盘（用户实锤两问题）：
                // ①「有 emoji 的行，角色名和第一行做不到对齐」——emoji 13sp 字形渲染
                //   高度把第二行撑高，外层行 CenterVertically 分配高度差 → 名字/标签框
                //   相对错位。修：emoji/chip Text 加固定行高（与 labelSmall 一致），
                //   第二行高度回归文字级，两行高度稳定后对齐稳定
                // ②「第二行还是超半屏——显示名+插件名+emoji 加一块不能超过半屏」——
                //   上一版 halfRow 挂 Column，但 Column 有 weight(1f)，weight 分配宽
                //   ≥halfRow 时上限失效（打架换了位置）。修：halfRow 移挂**第二行 Row**
                //   （Row 无 fillMaxWidth，宽=min(内容,半屏) 不打架），Column 撤 widthIn
                //   恢复 weight(1f) 占剩余；第一行 Row 保留 fillMaxWidth 贴右缘
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    // ===== 第一行：显示名标签框 + ▶ + ⋮（贴右缘）=====
                    // 11-09 用户令终稿（层级重排讨论定案「显示名做成标签，分类做纯文字
                    // 标注」）：标签框内容从分类 tag 换成**显示名**——显示名是重要信息
                    // （这个角色用哪个发音人），标签框底色给它视觉重量；长按随机换声
                    // 手势随信息主体回归。分类降级第二行纯文字标注。
                    // 字号 14sp（预览验收：16sp 放底色块里太重）；失效标签灰底+红 ⚠。
                    // 宽度封顶半屏（与预览 max-width:100% 一致），放不下 Clip 截断
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.Top,
                    ) {
                        // 显示名标签框：点=换声弹窗、长按=随机换声
                        // （仅 14 类可换类别响应，单例类别长按无动作）；失效标签灰底+红 ⚠
                        // 11-09 用户令「显示名卡片的不好看…首选框是一定要的」：框形态保留，
                        // 底色浅绿→灰底（surfaceContainerHighest，与换声弹窗分类字段同款
                        // 灰底），文字换主色绿加粗——灰底中性+绿字与主题 accent 同族，
                        // 十主题深浅色自动成立
                        // 11-10 六改（用户令「标签全部回 8dp 方框，以前的挺好的」）：
                        // 五改曾改成 50% 药丸，实机否掉，回 8dp 方框（AppShapes.md）。
                        // 同时与 TTS 列表的分类标签（Item.kt TagScreen，原 6dp）统一到同一档。
                        Surface(
                            shape = AppShapes.md,
                            // 标签底（11-10 十三改：**恢复用户自己选的档位**）：
                            // 这是 06:10 用户从五个候选里明确选定「③ 中淡绿」的产物——
                            // **卡底混 primary 10%**，绿主题 ≈ #E2F0E9（L=0.943 / C=0.018）。
                            //
                            // ⚠️ 十二改曾误把它改回 secondaryContainer 本色 #D6F1DF，
                            // 理由写的是"恢复以前的绿"，但用户澄清：「显示名的填充色我没有
                            // 问题，这个我还专门调过」——它不属"要恢复"的范围，是已定稿项。
                            // 已按用户令回退。**别再动这个值。**
                            //
                            // 失效标签（voiceName==null）仍走 surfaceContainer：失效态本就该
                            // 无彩，与可换声的彩色标签形成区分，这个反差不该抹掉。
                            color = if (voiceName == null) MaterialTheme.colorScheme.surfaceContainer
                                    else softFillBarColor(),
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = onTagClick,
                                    onLongClick = onReassign.takeIf {
                                        CharacterRecordsFile.categoryBaseOf(rec.voice) != null
                                    },
                                    onLongClickLabel = stringResource(R.string.role_menu_reassign),
                                )
                                .widthIn(max = halfRow)
                                .minimumInteractiveComponentSize(),
                        ) {
                            Text(
                                buildAnnotatedString {
                                    append(displayName?.replace('|', '｜') ?: rec.voice)
                                    if (voiceName == null) {
                                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                                            append(" ⚠")
                                        }
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    // 字号（11-10 十五改，用户实拍「这个里面的文字字号不对吧？
                                    // 我记得以前它比角色名字号要小一点」）：
                                    // 14sp → 13sp，回到 bdaf2058 建立标签形态时的原值。
                                    // 沿革：13sp（11-09 00:11 立）→ 14sp（11-09 03:50 c0ffacbf，
                                    // 当时为配合"标签化+面板一行式"顺手提的，与"标签应比角色名小"
                                    // 的视觉层级冲突）→ **13sp（本次回退）**。
                                    // 角色名 16sp，13sp 正好低 3 档，主次分明。
                                    fontSize = 13.sp,
                                    // 11-09 用户令「标签内的字重太重了」：撤 SemiBold
                                    // 回 normal——灰底已提供块感，加粗叠加太重
                                ),
                                // 11-10 六改：四套主题一律 primary（撤销五改给绿色单独配的深绿
                                // #0A6E3B）——用户令「发音人标签的绿改成和主题这个绿一样」。
                                // 绿色主题压灰底约 2.8:1，是用户明确选择的代价，别再加深。
                                color = if (voiceName == null) MaterialTheme.colorScheme.onSurfaceVariant
                                        else tagTextColor(),
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        // ▶ 试听（与换声弹窗顶部试听同链）：无锚点配置项（失效标签）不渲染
                        // 11-09 用户令「第一行右侧都留空了」：▶/⋮ 按键 12dp 水平内边距+触控盒
                        // 居中，图标右缘距内容线约 12dp 空白——收窄至 8dp+offset(x=6.dp)
                        // 视觉右移贴线（触控区不动）
                        if (hasAnchor) {
                            Text(
                                previewLabel,
                                color = previewColor,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .clickable(onClick = onPreview)
                                    .padding(horizontal = 8.dp, vertical = 12.dp)
                                    .offset(x = 6.dp),
                            )
                            // ⋮ 菜单（与换声弹窗 VoiceOverflowMenu 同款）：
                            // 标记三项 toggle（点亮打勾、菜单不关可连点）+ 删除配置项
                            var menuOpen by remember { mutableStateOf(false) }
                            Box {
                                Text(
                                    "⋮",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier
                                        .clickable { menuOpen = true }
                                        .padding(horizontal = 8.dp, vertical = 12.dp)
                                        .offset(x = 6.dp),
                                )
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                ) {
                                    VoiceMarksFile.MARK_ITEMS.forEach { (mark, emojiText, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            leadingIcon = { Text(emojiText) },
                                            trailingIcon = {
                                                if (mark in marks) Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = "已点亮",
                                                )
                                            },
                                            onClick = { onToggleMark(mark) },
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("删除配置项", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            menuOpen = false
                                            onDeleteConfig()
                                        },
                                    )
                                }
                            }
                        }
                    }
                    // ===== 第二行：标注行（分类纯文字 · 插件名 + emoji）=====
                    // 11-09 用户令终稿（「显示名做成标签，把女青年什么什么做成普通的
                    // 文字当成标注」）：显示名已升第一行标签框，本行=分类+插件名合并
                    // 纯文字灰标注（两个底色块挤一行太闹，全撤底色）+ emoji 钉行尾。
                    // 整行不可单独点（纯信息，点击冒泡到行=选中）。
                    // 宽度：整行钉住不截（显示名截断压力已在第一行标签框上）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 1.dp),
                    ) {
                        // 分类 · 插件名（纯文字灰标注；分类=tag id「女青年29」）
                        Text(
                            buildString {
                                append(rec.voice)
                                if (pluginName != null) append(" · $pluginName")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.widthIn(max = halfRow),
                        )
                        // 已点亮标记 emoji（钉行尾永不截；点击取消，QQ 式）
                        // 固定行高 16sp：emoji 13sp 字形全高渲染会把本行撑高
                        //（装机实锤「有 emoji 的行第一行与角色名错位」根因）
                        if (litMarks.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            litMarks.forEach { (_, emojiText) ->
                                Text(
                                    emojiText,
                                    fontSize = 13.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .clip(AppShapes.md)
                                        .clickable { onToggleMark(emojiMarkOf(emojiText)) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 释放/删除已合并角色弹窗（照插件 doReleaseOperation：逐名字行 释放并固定/删除） */
@Composable
private fun ReleaseDialog(
    rec: CharacterRecordsFile.RoleRecord,
    onDismiss: () -> Unit,
    onFix: (ownerName: String, name: String) -> Unit,
    onDelete: (ownerName: String, name: String) -> Unit,
) {
    val processed = remember { mutableStateOf<Set<String>>(emptySet()) }
    val allNames = (listOf(rec.name) + CharacterRecordsFile.splitAliases(rec.aliases))
        .distinctBy { it.trim() }
    AlertDialog(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.role_release_title)) },
        text = {
            Column {
                allNames.forEachIndexed { i, name ->
                    val done = name in processed.value
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.size(6.dp).background(
                            Color(releaseDotColors[i % releaseDotColors.size]), CircleShape
                        ))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (done) "$name ✓" else name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (!done) {
                            TextButton(onClick = {
                                // 点了就标 ✓（与删除按钮同款）：换声弹窗关掉后回到本弹窗，
                                // 该名字不再显示按钮，避免同一个名字在本次弹窗里被释放两遍
                                processed.value = processed.value + name
                                onFix(rec.name, name)
                            }) {
                                // 11-10 二改回退：一改曾把这里从全站绿改成主题主色，属超范围。
                                // 用户只点了日志规则行"该跟主题"，其余写死色一律不动。
                                Text(stringResource(R.string.role_release_fix), color = Color(ACCENT_GREEN))
                            }
                            TextButton(onClick = {
                                processed.value = processed.value + name
                                onDelete(rec.name, name)
                            }) {
                                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm)) }
        },
    )
}

/** 修改角色名称：多行名称编辑器（第1个=主名，其余=别名） */
@Composable
private fun EditNamesDialog(
    initialNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    // 光标默认落在末尾（照插件 v10 showEditCharacterDialog：setText 后 setSelection(len) 8249）
    val names = remember {
        mutableStateOf(
            initialNames.map { TextFieldValue(it, TextRange(it.length)) }.toMutableList()
        )
    }
    AlertDialog(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.role_list_menu_rename)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.role_edit_names_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                names.value.forEachIndexed { idx, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { run ->
                                names.value = names.value.toMutableList().also { it[idx] = run }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (names.value.size > 1) {
                            TextButton(onClick = {
                                names.value = names.value.toMutableList().also { it.removeAt(idx) }
                            }) {
                                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                TextButton(onClick = {
                    names.value = (names.value + TextFieldValue("")).toMutableList()
                }) { Text(stringResource(R.string.role_edit_names_add)) }
            }
        },
        confirmButton = {
            val cleaned = names.value.map { it.text.trim() }.filter { it.isNotEmpty() }
            TextButton(
                enabled = cleaned.isNotEmpty(),
                onClick = { onConfirm(cleaned) }
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
