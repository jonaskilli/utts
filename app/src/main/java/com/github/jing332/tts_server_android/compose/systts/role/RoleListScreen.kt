package com.github.jing332.tts_server_android.compose.systts.role

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drake.net.utils.withIO
import com.github.jing332.common.utils.limitByDisplayWidth
import com.github.jing332.tts_server_android.compose.theme.pluginChipColor
import com.github.jing332.common.ACCENT_GREEN
import com.github.jing332.tts_server_android.compose.theme.AppShapes
import com.github.jing332.tts_server_android.compose.systts.common.PreviewIconButton
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
import com.github.jing332.tts_server_android.compose.TruncatedAnnotatedText
import com.github.jing332.tts_server_android.compose.LocalBottomBarBehavior
import com.github.jing332.tts_server_android.compose.systts.common.VoiceOverflowMenu
import com.github.jing332.tts_server_android.compose.systts.common.VoicePickerDialog
import com.github.jing332.tts_server_android.service.systts.SystemTtsService
import com.github.jing332.tts_server_android.service.systts.help.CharacterRecordsFile
import com.github.jing332.tts_server_android.service.systts.help.KeyListFile
import com.github.jing332.tts_server_android.service.systts.help.VoiceMarksFile
import kotlinx.coroutines.launch
import com.github.jing332.database.entities.systts.SpeechRuleIds

/**
 * 角色管理·1:1 复刻（拍板「照 v10 插件原样搬，之后他再改」）：
 * 对照 角色管理v10_主题密钥增强.js 逐函数复刻——
 * **平铺列表**（非分组）：每行=左名字列（主名+别名每行一个、性别色圆点、收藏【】、主角👑）
 * + 右侧两行（11-08 重构）：
 *   第一行=标签框（只放分类 tag，点=换声弹窗 / 长按=随机换声）+ ▶试听（三态）+ ⋮菜单
 *   （❤️🚶😈 标记 toggle + 删除配置项，11-10 起直接复用 VoiceOverflowMenu 共用组件）；
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
 *
 * 11-11：与 softFillBarColor() **同公式同步改**（lerp(secondaryContainer, White, 0.42)）。
 * 这两个函数在 C 档时期色值完全相同，用户认知里就是"同一个颜色"；只改一支会让
 * 分类胶囊/日志标签还是旧暗绿，而同屏的书籍框/显示名标签已经提亮——正是历史上
 * 被用户批评过的"一大一小两档绿"。以后调档位请两处一起动。
 */
@Composable
internal fun softContainerColor(): Color = softFillBarColor()

/**
 * 全站「小填充」统一色（书籍框 / 显示名标签框 / 更换发音人选中态 / 分类胶囊共用，共 33 处）。
 *
 * ## 11-11 A 档定稿（用户拍板，改动前先读这段）
 *
 * 用户第三轮反馈：「绿色主题这个书籍框的填充色，我这次还是不满意…我感觉暗暗的」。
 *
 * 真因不是"不够绿"，而是**往灰里混**：
 * 旧公式 `lerp(secondaryContainer, surfaceContainerLow, 0.10)` 把颜色往**中性灰卡底**
 * 稀释，混出来必然发闷（绿主题 #DAF2E2，L=90%）。同一枚 secondaryContainer
 * **往白里混**出来是清亮的——同样的色相和饱和度，只是不再被灰压暗。
 *
 * 现公式：`lerp(secondaryContainer, White, 0.42)`，绿主题 ≈ **#E7F7EC**（L=94%）。
 * 四套主题：绿 #E7F7EC / 紫 #F5F0FF / 雾 #ECF3FF / 墨 #F2F3F5（浅色）。
 * 卡上文字（onSurface 深色）对比 13~15，标签字完全不受影响。
 *
 * ## 用户明确不借鉴的东西（别再往这个方向改）
 *
 * 用户发过两张"很清爽"的系统截图（运营商流量页），那种绿是**底极淡 + 字用饱和青绿**
 * 的结构、色相偏 170° 薄荷。用户 11-11 明确：**"暂时不参考它这个结构和颜色"**，
 * 只取"底提亮"这一点。所以：
 *   · 不要改文字的染色（标签字仍是 onSurface 深色，不染主题绿）
 *   · 不要动色相（跟随各主题 secondaryContainer）
 *
 * ## 公式起点为什么必须是 secondaryContainer
 *
 * **不能**用中性卡底混 primary——primary 是深色，混进浅底只会被稀释成灰绿，越调越灰。
 * 这是这条色改过十几轮的根因。
 *
 * ## 为什么抽成单源
 *
 * 用户在角色页上有多个相邻的可点条（书籍框、显示名标签框），浓度必须**永远一致**。
 * 写成多处 `lerp(...)` 也能跑，但调浓度时极易只改一处，又变成"一大一小两档绿"。
 */
@Composable
internal fun softFillBarColor(): Color {
    val cs = MaterialTheme.colorScheme
    // 深色必须单独一档：往白里混 42% 会得到 #868C88 这种中灰块（对深页底 5.36），
    // 底上的浅色文字对比只剩 2.47——直接糊掉。深色改为往上一级浅槽混，
    // 保持"底色比页底亮一档"的原关系（对页底 1.55、文字对比 8.4）。
    return if (cs.background.luminance() > 0.5f)
        lerp(cs.secondaryContainer, Color.White, 0.42f)
    else lerp(cs.secondaryContainer, cs.surfaceContainerHighest, 0.42f)
}

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
                            // 11-11 用户令「编辑键跟后面的管理键间距有点儿宽」：
                            // 12dp → 8dp，与右侧「管理」的 8dp 对称（两键视觉同重）
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    // 11-11 用户令：两键之间原为「✎内衬12 + Spacer10 + 管理内衬8 = 30dp」，
                    // 中间那个纯 Spacer 占 10dp 是主要空隙来源。收到 6dp 后合计 22dp：
                    // 不再明显发空，同时守住点区（✎ 点区仍有字形 + 左右各 8dp）。
                    // ⚠️ 「管理」的 8dp 内衬不动——它字形比 ✎ 宽，视觉本就需要多留一点。
                    Spacer(Modifier.width(6.dp))
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
                    // 插件名（注释行缀尾；11-10 改按显示宽度 8 = 中文4字/英文8字母，与日志插件牌同口径）
                    // 注意：config 静态类型是 IConfiguration（无 source 字段），
                    // 必须先 as? TtsConfigurationDTO 才能取 source（CI 实锤 :538 Unresolved 'source'）
                    val pluginNameShort = remember(anchorEntity?.id) {
                        val src = (anchorEntity?.config as? TtsConfigurationDTO)?.source as? PluginTtsSource
                        src?.let { dbm.pluginDao.getMetaByPluginId(it.pluginId)?.name }
                            ?.limitByDisplayWidth()
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
        val tagRuleIdDel = SpeechRuleIds.RULE_ID
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
                                d.speechRule.tag == delTag
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
            // 28 改（同 27 改口径）：锚点兜底优先取**启用配置**的首个标签，
            // 池文件只在启用配置为空时兜底。
            // 原因：fayinren.json 由规则脚本运行时生成，可能过时/为空（规则版本未同步、
            // saveVoicesToFile 关闭、duihua 解析失败）；拿它当锚点会得到一个已停用的标签，
            // 弹窗初始显示名查不到、参数基准也不对。启用配置是用户真实配过的，必定有效。
            ?: dbm.systemTtsV2.allEnabled.firstOrNull()
                ?.let { (it.config as? TtsConfigurationDTO)?.speechRule?.tag }
                ?.takeIf { it.isNotEmpty() }
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
            // 28 改（同 27 改口径）：锚点兜底优先取**启用配置**的首个标签，
            // 池文件只在启用配置为空时兜底。
            // 原因：fayinren.json 由规则脚本运行时生成，可能过时/为空（规则版本未同步、
            // saveVoicesToFile 关闭、duihua 解析失败）；拿它当锚点会得到一个已停用的标签，
            // 弹窗初始显示名查不到、参数基准也不对。启用配置是用户真实配过的，必定有效。
            ?: dbm.systemTtsV2.allEnabled.firstOrNull()
                ?.let { (it.config as? TtsConfigurationDTO)?.speechRule?.tag }
                ?.takeIf { it.isNotEmpty() }
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

/**
 * 角色行（11-08 重构定稿）：
 * 左名字列竖排（主名第一行，别名从第二行起各占一行，每行=性别圆点+名称+收藏【】+主角👑）；
 * 右侧两行：
 *   第一行=发音人标签框（只放**分类 tag**如「女青年01」，点=换声、长按=随机换声）
 *     + ▶ 试听（▶/…/■ 三态，TaggedTtsPreviewPlayer 全局单实例）
 *     + ⋮ 菜单（❤️🚶😈 标记 toggle + 删除配置项，11-10 起复用 VoiceOverflowMenu）；
 *   第二行=注释行：displayName（查不到=tag+⚠）·插件名（前4字）在左，
 *     已点亮标记 chip 钉**行尾**（点击即取消——QQ 式；11-08 用户令：
 *     chip 不够宽时削的是 displayName 字数，chip 永不被挤掉、更不竖排）。
 * 点击范围（11-08 用户令）：除标签框/▶/⋮ 各自吃掉点击外，**整行其余任何区域**
 * 点击=选中角色、长按=操作菜单。
 */

/**
 * 角色标签框的文字色 = **与左侧角色名同一个颜色**（onSurface）。
 *
 * ## 11-10 二十二改（用户令「发音人列表显示名的颜色改成跟角色名一样的颜色就行」）
 *
 * 改为与角色名同色后，一屏里「角色名 + 它的发音人标签」是一组同色文字，
 * 读起来是"这个人叫什么 / 用什么声音"，不再靠色相暗示层级。
 *
 * ## 沿革（别再往回改）
 *
 * 10-13：绿底绿字（secondaryContainer 底 + primary 字）。
 * 11-10 五改：浅胶囊形态，给绿色主题单配深绿 #0A6E3B。
 * 11-10 六改：撤销 #0A6E3B，四套主题一律 primary（用户「发音人标签的绿改成和
 *   主题这个绿一样」）。
 * 11-10 二十一改：改 onSecondaryContainer（用户「显示名标签的颜色不能是主题色」）
 *   —— 绿色 #0D1F13 / 紫色 #3B2B55 / 蓝色 #24335C / 黑色 #33353A，
 *   带各自色相，但仍是"彩色字"。
 * 11-10 二十二改：**改为 onSurface，与角色名完全同色**（用户令）。
 *   绿色 #191C19 / 紫色 #26242C / 蓝色 #21252F / 黑色 #23252A —— 都是近黑的中性深色。
 *
 * ⚠️ 别再改回 primary 或 onSecondaryContainer。
 */
@Composable
private fun tagTextColor(): Color = MaterialTheme.colorScheme.onSurface

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
    // 单名对齐（用户 11-11）：「角色名中线要对齐右侧**第一行**的中线」，不是整块行居中。
    // 右列第一行高 = max(标签框 22dp, ▶ 图标盒 24dp)：
    //   有 ▶（hasAnchor）→ 24dp，中线 12dp
    //   无 ▶（失效标签）  → 22dp，中线 11dp
    // 左列把名字行**撑到同一高度并居中**，中线自然重合——不再用位移去凑数字。
    // ⚠️ 用 heightIn(min=) 而不是固定 height + 位移：字号调大时名字行可以自然长高，
    //    固定高度会把文字压到框外（这是上一版 −1dp 位移在大字号下会歪的根因）。
    val firstRowHeight = if (hasAnchor) 24.dp else 22.dp
    // ▶ 三态色（与换声弹窗 previewLabelColor 同源）：空闲=主色 / 合成中=琥珀 / 播放中=红
    val previewColor = when {
        !previewActive -> MaterialTheme.colorScheme.primary
        previewSynthesizing -> Color(0xFFFF6F00)
        else -> Color(0xFFF44336)
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
            // 11-11 用户令终稿「居中对齐」：外层 Row 用 CenterVertically，
            // 左列名字块在右侧两行的高度内垂直居中（多别名）。
            //
            // ## 右列两行高度（11-11 用 24dp ▶ 盒 / 24dp ⋮ 盒后）
            //   第一行 = max(标签框 22dp, ▶ 盒 24dp) = 24dp，中线 12dp
            //   间隔   = 1dp
            //   第二行 = max(文字 16dp, ⋮ 盒 24dp) = 24dp
            //   ⇒ 右列总高 49dp；名字行 24sp，多别名整块居中。
            //
            // 单名（用户 11-11「角色名中线要对齐右侧第一行的中线」）：外层 Top，
            // 名字行 heightIn(min = 第一行高) 并在其中垂直居中 ⇒ 中线重合
            //（有 ▶ 时 24dp/12dp，无 ▶ 时 22dp/11dp，见 firstRowHeight）。
            // 多别名：CenterVertically，整块在 49dp 里居中。
            // ⚠️ 为什么不能一直用 Top：多别名时左列会从顶上开始排，
            //    与右侧两行整体错开（历史踩过，注释见上）。
            // ⚠️ 沿革：曾按「右列中心差 21dp」把行高压到 21sp，那是错的前提
            //    （右列中心差随 ⋮ 盒/内衬漂移，左列被动跟变）——已在 11-11 废弃。
            verticalAlignment = if (nameList.size == 1) Alignment.Top else Alignment.CenterVertically
        ) {
            // 名字列：照 v9 排布（主名第一行，别名逐个竖排，每行带性别色圆点）
            Column(
                // 11-11：撤掉 heightIn(min = 44.dp)。它把左列撑到 44dp，导致单名时名字
                // 贴在 44dp 的顶部、居不中（外层 CenterVertically 也救不回来，因为列本身已 44dp）。
                // 现在高度由内容决定（1 名 22dp / 2 名 44dp / 3 名 66dp），行高由下面的
                // lineHeight = 22.dp 控制，外层 Row 负责整体居中。
                Modifier
                    .widthIn(max = halfRow)
                    // 单名：先 wrapContentHeight 让 Column 不被外层 Row 拉伸到 49dp，
                    // 高度再交给里面名字行的 heightIn(min = 右列第一行高) —— 两边等高，
                    // 名字行内 CenterVertically 后中线重合（24dp 盒 → 12dp = 第一行中线）。
                    .then(
                        if (nameList.size == 1) Modifier
                            .wrapContentHeight(Alignment.Top) else Modifier
                    ),
                // 单名/多别名都 Top：单名的对位由名字行的 heightIn 下限 + 行内居中完成；
                // 多别名各行依次排下，整块由外层 CenterVertically 居中。
                verticalArrangement = Arrangement.Top,
            ) {
                nameList.forEachIndexed { idx, name ->
                    // 圆点用 CenterVertically 跟行中线（原来写死 top=9dp，行高变了会偏）。
                    // 单名对位：本行 heightIn(min = 右列第一行高)，字形在其中垂直居中，
                    // 中线与右侧第一行（标签框/▶）重合；多别名不设下限。
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        // 单名：行高撑到与右列第一行等高（24dp / 无 ▶ 时 22dp），
                        // 名字行框在其中垂直居中 ⇒ 中线 = 第一行中线。
                        // 多别名：不设下限，各行按 24sp 紧排。
                        modifier = if (nameList.size == 1)
                            Modifier.heightIn(min = firstRowHeight) else Modifier,
                    ) {
                        Spacer(
                            Modifier
                                .padding(start = 4.dp)
                                .size(6.dp)
                                .background(genderDotColor(rec.voice), CircleShape)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = buildString {
                                if (isFav) append("【$name】") else append(name)
                                if (idx == 0 && isProtagonist) append(" 👑")
                            },
                            // 11-11 用户令：行高 **24sp**（原话「上个问题改为24sp」）。
                            //
                            // ## 不再把行高绑到「右列中心差」上（推翻此前 21sp 的前提）
                            // 沿革：左列行高最初 22sp（bodyLarge 默认 24sp 压下来的）→
                            // 0303d377 被当纯文字行压到 **16sp** → 之后我一路按
                            // 「= 右列两行中心差」在 20/21sp 之间微调。
                            // 这是**错的前提**：右列中心差会随 ⋮ 盒、标签框内衬变化而变，
                            // 左列就被动跟着变，最后比最初的 22sp 还小；
                            // 而左列是 16sp 的字，配 21sp 本就偏紧（用户全程觉得「左侧行间距小」）。
                            // 正解：左列按自己的字号定舒服的行高（16sp 字配 24sp 是正文常规比例），
                            // 右列去匹配它，而不是反过来。
                            //
                            // ## 对齐关系
                            // 单名：外层 Top + 本行 heightIn(min = 右列第一行高)，中线重合。
                            // 多别名：不逐行对齐，整块在右侧两行里垂直居中。
                            // ⚠️ 日后若要恢复逐行严格对齐，去调右列（把注行补到与标签框等高 22dp，
                            //    中心差变 22dp），**不要回来压左列行高**。
                            // 行高统一 24sp（用户令）。
                            // ⚠️ 2026-11-11 修正：此前令单名取 22sp 是错的——
                            // lineHeight 对**单行**不产生任何行距，它只决定这一行的行框高度；
                            // 拿它去凑 22dp 容器是把参数用偏了。单名若需对齐标签框，
                            // 应当位移（见下方 offset），而不是改行高。
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            // 名字超半屏折行续排，不省略（10-12「显示完全」口径保持）
                        )
                    }
                    // 原 Spacer(height = 2dp) 已撤：行距完全由上面的 lineHeight 表达，
                    // 再叠 Spacer 会让实际行距大于设定值。
                }
            }
            // 右侧两行（voice 空不渲染，照插件）
            if (rec.voice.isNotBlank()) {
                // 11-11 用户令「角色名和右侧显示名中间也得保留一部分间距」：10 → 16dp。
                Spacer(Modifier.width(16.dp))
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
                        // 11-11 用户令「播放键太靠上」：Top → CenterVertically。
                        // 第一行高度由标签框（13sp + 上下 5dp ≈ 26dp）决定，而 ▶ 字形只有 16sp，
                        // Top 对齐会让它贴上沿、悬在标签框上半部。居中后与标签框垂直对齐。
                        verticalAlignment = Alignment.CenterVertically,
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
                                // 11-10 二十三改（用户令「显示名太短的话标签框太短，
                                // 点击/更换发音人操作不太方便，给它设个最短宽度」）：
                                // **加 96dp 下限**。两字名（「清一」「王姨」）原先框只有约 83dp
                                // （实测截图 1260 宽下 250px ≈ 83dp），既是点击目标也是长按
                                // 随机换声的热区，偏窄时容易点偏。96dp ≈ 4.2 个汉字宽，
                                // 常见短名留白舒服、触点在框内宽松，且不会像 120dp 那样显空。
                                // 上限（11-10 二十四改，用户实拍「第二个字右侧不完整、
                                // 空间很充裕却被截断」）：
                                // 原上限是 halfRow（半屏 ≈164dp）——那是"左名右签各占一半"
                                // 的假设，但左侧名字常常只有两三个字（「男人」「皂隶」），
                                // 右列早就通过 weight(1f) 拿到了更多空间，标签框却仍被
                                // 半个屏幕锁死，「度星河｜广告男声｜配」正好卡在 164dp 被切。
                                // 改为**跟随父级剩余宽**：用 weight(1f, fill = false)——
                                // 「最多吃满剩余，但不强行撑开」：
                                //   · 内容短 → 按内容宽收缩（fill=false 不拉出空白）
                                //   · 内容长 → 用满 Row 里剩下的宽，直到贴住 ▶
                                //   · ▶ / ⋮ 是固定宽兄弟，weight 只会分给"剩余"，
                                //     所以标签框再怎么长也挤不掉它们（这是去掉 max 后
                                //     必须补的保护，否则 Arrangement.End 下长名字会把 ▶ 顶出屏）
                                // ⚠️ 11-11 用户令：最小宽 **96 → 72dp**（用户先问"这个宽度是否合适"，
                                // 看过预览后定 72）。
                                //
                                // ## 为什么 96dp 是错的
                                // 实测截图（屏宽 1260px = 360dp，3.5px/dp）：四个角色的框**全是 108dp**，
                                // 而文字只有 26~54dp —— 框里空白占 50~76%：
                                //   然月    框108dp 文字42dp 空66dp
                                //   云希HD  框108dp 文字26dp 空82dp
                                //   悦小妮  框108dp 文字54dp 空54dp
                                // 视觉上就是"一大块绿底中间浮着几个字"。96dp 这个下限当初是为
                                // 短名的点击区设的，但它把**所有**名字的框都撑到同一宽度，
                                // 名字长短的差异被抹平了。
                                //
                                // ## 72dp 的依据
                                // 框内可用宽 = 72 − 左右内衬各 10 = **52dp ≈ 4 个汉字**，
                                // 正好覆盖常见名字长度，且不再有假空白：
                                //   悦小妮 39dp → 两侧各 6.5dp ｜ 云希HD 45dp → 各 3.5dp
                                //   然月   26dp → 两侧各 13dp（最短名仍略空，但可接受）
                                // 长名字照旧走 weight(1f, fill=false) 吃到剩余宽、按整字截断，
                                // 不受这个下限影响（下限只作用于短名）。
                                //
                                // ## 对齐方式：保持**框内居中**，不改分散/左对齐
                                // 用户问过"文字在框里要不要分散对齐"，结论是不改：
                                //   分散对齐要把剩余空间摊到字与字之间，2 字名会拉开一整格
                                //   （「然月」→ 中间空 26dp），而角色名 2/3/4 字混杂，
                                //   短名会被拆得很怪；左对齐则右缘空、像没写完。
                                //   方块底色 + 居中文字 = 徽章语义，是这个设计的本体。
                                .widthIn(min = 72.dp)
                                .weight(1f, fill = false),
                            // ⚠️ 11-11 删掉这里的 `.minimumInteractiveComponentSize()`（用户
                            // 11-11 首条反馈「右侧的行间距还是远大于左侧，跟没缩前是不是差不多」
                            // 的真根因）。它把标签框强行钉到 **48dp 高**，于是第一行永远 48dp，
                            // 右列两行中心差变成 33dp、而左列名字只有 20dp——用户看到的就是
                            // 「右边松、左边紧」。之前几轮一直在缩 ⋮ 的视觉盒（32→20→16dp）
                            // 都压不动，因为行高根本不是 ⋮ 决定的，是这里钉的。
                            // 截图实测印证：右列两行文字中心差 = 119.5px / 3.575 = 33.4dp ≈ 48+1+16 的中心差。
                            // 删掉后第一行回到内容高 22dp（13sp lineHeight 16 + 上下 3dp），
                            // 与代码注释里的设计意图一致（「第一行 22dp（标签框 13sp + 上下 3dp ≈ 22dp）」）。
                            //
                            // ⚠️ 代价：标签框的触控高度从 48dp 降到 22dp（宽度仍是 96dp 起）。
                            //    整行另有 10dp 上下内衬 + 整行可点（点行=选中角色），
                            //    且换声的主入口是第一行的 ⋮ 与角色列表点击，可接受。
                            //    若日后要恢复 48dp 触控，别再用这个修饰符——它会连带把行距撑开；
                            //    改用外层叠加触控区的写法。
                        ) {
                            // 11-10 二十五改（用户反复强调「不要省略号，超过就截断」，
                            // 且实拍「字显示不全、不是完整的汉字」）：
                            // TextOverflow.Clip 是**按像素**裁剪，汉字宽 13sp 且非整像素对齐，
                            // 裁在字中间就留下半个字。改为先按**完整字符**算好能放几个字再渲染。
                            val tagStyle = MaterialTheme.typography.bodyMedium.copy(
                                    // 字号（11-10 十五改，用户实拍「这个里面的文字字号不对吧？
                                    // 我记得以前它比角色名字号要小一点」）：
                                    // 14sp → 13sp，回到 bdaf2058 建立标签形态时的原值。
                                    // 沿革：13sp（11-09 00:11 立）→ 14sp（11-09 03:50 c0ffacbf，
                                    // 当时为配合"标签化+面板一行式"顺手提的，与"标签应比角色名小"
                                    // 的视觉层级冲突）→ **13sp（本次回退）**。
                                    // 角色名 16sp，13sp 正好低 3 档，主次分明。
                                    fontSize = 13.sp,
                                    // 行高必须跟着字号一起回：bodyMedium 自带 lineHeight 20sp
                                    //（M3 按 14sp 配的），只改 fontSize 不改行高的话，字小了但
                                    // 行高仍 20sp —— 框比原版厚 4sp，视觉上"字变小了框却没小"。
                                    // 原版 bodySmall 的行高就是 16sp，这里显式写回。
                                    lineHeight = 16.sp,
                                    // 11-09 用户令「标签内的字重太重了」：撤 SemiBold
                                    // 回 normal——灰底已提供块感，加粗叠加太重
                                )
                            val tagColor =
                                if (voiceName == null) MaterialTheme.colorScheme.onSurfaceVariant
                                else tagTextColor()
                            val tagText = buildAnnotatedString {
                                append(displayName?.replace('|', '｜') ?: rec.voice)
                                if (voiceName == null) {
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                                        append(" ⚠")
                                    }
                                }
                            }
                            // padding 放在 BoxWithConstraints **外面**：这样 maxWidth
                            // 就是文字真正可用的宽度，测量与渲染口径一致（放里面的话
                            // 量到的是含内衬的宽，截断点会偏后）。内衬沿 5dp 原值。
                            //
                            // 11-11 用户令「标签框有最低宽度，但里面的文字应该居中，不该居左」：
                            // 框有 96dp 下限，两三个字的短名（「墨老大」「安小航」）被撑到
                            // 96dp 后，文字原先贴左、右侧空一大块，看着像"没对齐"。
                            // BoxWithConstraints 给**子节点**传的约束仍是 minWidth=0，所以
                            // 内部的 Text 不会自动撑满→直接 contentAlignment 居中即可。
                            // 11-11 用户令「第一行和第二行间距有点宽」：垂直内衬 5dp → 3dp。
                            // 实测两行之间本来只有 padding(top = 1dp)，看到的那点空隙主要来自
                            // 这个方框的内衬（框底边 → 文字 → 第二行文字）。
                            // 标签框高 26dp → 22dp 后，第一行高度改由 ▶（约 24dp）决定，
                            // 整体更紧凑；13sp 文字在 22dp 框里仍宽松（上下各 3dp）。
                            BoxWithConstraints(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TruncatedAnnotatedText(
                                    text = tagText,
                                    style = tagStyle,
                                    color = tagColor,
                                    maxWidth = maxWidth,
                                )
                            }
                        }
                        // ▶ 试听（与换声弹窗顶部试听同链）：无锚点配置项（失效标签）不渲染。
                        // 11-11 用户令：▶ 与 ⋮ **同级**——同为 20dp 图标 + 24dp 盒。
                        // 原来 ▶ 是 16sp 字符（字形只有约 9~13dp 高），⋮ 是 14dp 图标，
                        // 用户看到的「一大一小」就来自这里；字符放大到 20dp 要 26sp+ 且撑行高、
                        // 还随字体回落漂移，故改图标（见 PreviewIconButton 注释）。
                        // 盒前补 6dp：图标在 24dp 盒内居中自带 2dp，视觉间距 = 8dp（与原一致）。
                        // 右缘：盒贴内容区右缘，与第二行的 ⋮ 盒同一条竖线（两行图标都居中，右距 2dp）。
                        // 触控：24dp 见方（与 ⋮ 同款取舍）；整行另有点击，点 ▶ 优先消费为试听。
                        if (hasAnchor) {
                            Spacer(Modifier.width(6.dp))
                            PreviewIconButton(
                                playing = previewActive,
                                synthesizing = previewSynthesizing,
                                tint = previewColor,
                                onClick = onPreview,
                            )
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
                        // 11-11 用户令「角色管理也要同级，建议把三个点菜单调大」：
                        // 本行高度现在由 ⋮ 的 24dp 盒决定（文字只有 16dp），比改前高 8dp；
                        // 用户明确表示行距略松没关系。
                        modifier = Modifier.padding(top = 1.dp),
                    ) {
                        // 分类 · 插件名（纯文字标注；分类=tag id「女青年29」）
                        //
                        // 11-10 十七改（用户选 D1）：**分类加深、插件名保持原灰**，
                        // 用明度分主次，不给分类上色。两个候选（B 上主题色 / D1 排版分层）
                        // 出过预览，用户选 D1。
                        //
                        // ## 为什么不上色
                        //
                        // ① 同屏已两处绿：第一行标签框底（secondaryContainer 淡绿）+ 框内
                        //    主色绿字。分类再上主色，一行里三处同色相绿，"重要"失去区分力
                        //    ——颜色只有在能区分时才有信息量。
                        // ② 对比度不够：本行 labelSmall 11sp（小字门槛 4.5），
                        //    主色 #109D58 压页底只有 3.45:1，上色反而更糊。
                        // ③ 分类与插件名是**同一类信息**（这角色用谁的声音、哪套服务合成），
                        //    分开染色等于谎称两者不同类。
                        //
                        // ## 18 改（用户令「它跟插件区分不开」后定稿）
                        //
                        // 分类 onSurface（#191C19，16.89:1）最深、**不加粗**；
                        // 插件名改用 **secondary**（主题自分容器，带主题色相）。
                        //
                        // ⚠️ 为什么不是"分类浅一点、插件名也灰"：
                        // 17 改曾让分类 onSurfaceVariant + 插件名也 onSurfaceVariant，
                        // 两者 ΔE=0.000（**完全同色**，压根分不开）——这正是用户最初
                        // 提"得有点区别"的由来。后来 D1 只拉明度（ΔL=0.174、ΔE=0.174），
                        // 视觉上仍是"一个深灰一个浅灰"，用户实拍「它跟插件区分不开呀」。
                        //
                        // 18 改加**色相**维度：secondary 各主题自带色相
                        // （绿 #4F6353 绿灰 / 紫 #706A7A 紫灰 / 蓝 #6C758D 蓝灰 /
                        // 深灰 #616367），ΔE 提到 0.235~0.299，明度+色相双重区分。
                        // 语义也正：secondary 是 M3 的「次要信息色」，插件名正属次要。
                        //
                        // 对比度（11sp 小字需 ≥4.5）：绿 6.36 / 紫 4.97 / 蓝 4.51 / 深灰 5.92，
                        // 四套全部达标。紫色 secondary 原值 #787382 只有 4.38，
                        // 已在 Color2.kt 压深到 #706A7A（同色相、略深，ΔE 仍 0.270）。
                        //
                        // 25 改（用户令「显示名+插件名+emoji 这个长度是固定的，超出应该缩减
                        // 插件名，不要省略号直接截断」）：
                        // 从"一条 Text 叠两个 SpanStyle"改成 **Row + 两个 Text**。
                        //
                        // 为什么非改不可：原来整条 Text 挂 widthIn(max=halfRow) + Clip，
                        // 截断点由 Text 自己定——超长时被砍的是整条文本尾部，虽然"先砍插件名"，
                        // 但砍在哪一个像素不可控（可能半个字），而且分类一旦变长就整体挤。
                        // 现在：分类按内容宽固定、插件名吃剩余（weight）+ Ellipsis——
                        // **只有插件名会被缩**，分类与 emoji 都保住。
                        // 截断方式：用户要"不要省略号直接截断"，故插件名用 Clip；
                        // 但外层给的是有界宽度（weight），Clip 会干净地砍在字符边界。
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.widthIn(max = halfRow),
                        ) {
                            Text(
                                rec.voice,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                // ⚠️ 分类**不能**用 weight：与下面插件名的 weight 会平分
                                // 剩余宽度，分类被无故拉开、插件名被挤窄。
                                // 按内容宽占位即可（分类最多 4 字，正常不会超）。
                                modifier = Modifier,
                            )
                            if (pluginName != null) {
                                // 插件名吃剩余宽度，超长时**只缩它**；同样按整字截断
                                // （11-10 用户令「不要省略号，超过就截断」且不能切出半个汉字）
                                // ⚠️ 必须是 weight(1f, fill = false)，不能只写 weight(1f)：
                                // fill=true 会让插件名**占满**半屏剩余宽度，短插件名后面的
                                // 空地也被它吃掉，⋮ 被推到很远——这就是用户实拍
                                // 「插件和三个点菜单之间有非常大的一个空格」的根因。
                                // fill=false 时：短名按内容宽收缩、⋮ 紧跟；
                                // 长名仍能吃到剩余宽并按整字截断（fill=false 只是不强行撑开）。
                                BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                                    TruncatedAnnotatedText(
                                        text = AnnotatedString(" · $pluginName"),
                                        style = MaterialTheme.typography.labelSmall,
                                        // 11-10 用户令「参考日志里插件名的颜色」：日志插件牌是
                                        // 固定灰青 #5B6472 / 深色 #9AA0A8（信息类别色，不随主题变）
                                        color = pluginChipColor(),
                                        maxWidth = maxWidth,
                                    )
                                }
                            }
                        }
                        // 已点亮标记 emoji（11-11 用户令：从第一行**挪回第二行**，
                        // 位置定在插件名之后、⋮ 之前；并且**不加任何底色**）：
                        // · 为什么回第二行：第一行里有标签框和 ▶ 两个热区，emoji 夹在中间
                        //   视觉上把「标签—试听」这一对拆开了；第二行是纯标注行，附加信息
                        //   放这里不抢第一行。
                        // · 为什么在 ⋮ 前而不是行首：行首会让 emoji 看起来像分类的一部分
                        //   （读成「❤️ 男青年45 · 本悦读」），主次倒置；放 ⋮ 前则是
                        //   「分类 · 插件 → 标记 → 菜单」，标记作为状态收在行尾，与菜单同侧。
                        // · 为什么去掉底色：11-11 用户令「现在 emoji 它是有底色的，你不应该加」。
                        //   只保留 emoji 本身，不再画淡主题色圆角底。
                        // · 仍**只显示、不可点**（11-10 用户令：emoji 容易误触，取消标记走 ⋮ 菜单）。
                        // 行高固定 16sp，避免 emoji 全高字形撑高纯文字行。
                        if (litMarks.isNotEmpty()) {
                            litMarks.forEach { (_, emojiText) ->
                                Text(
                                    emojiText,
                                    fontSize = 13.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                            }
                            Spacer(Modifier.width(2.dp))
                        }
                        // ⋮ 菜单（11-10 用户令：从第一行挪到**第二行末尾**，
                        // 「分类名/插件名这一块和三个点紧密挨着、整体靠右对齐」）。
                        // 用紧凑模式：第二行是纯文字行，标准 48dp IconButton 会把行撑高、
                        // 连带两行与左侧角色名的顶对齐一起偏。触控靠 VoiceOverflowMenu 自身。
                        // 无锚点配置项时删除项置灰（deleteEnabled=hasAnchor）。
                        if (hasAnchor) {
                            VoiceOverflowMenu(
                                marks = marks,
                                onToggleMark = onToggleMark,
                                deleteEnabled = hasAnchor,
                                onDelete = onDeleteConfig,
                                // 11-11 用户令「角色管理也要同级，建议把三个点菜单调大，
                                // 行间距稍微大一点也没关系」：18/14 → 24/20，与换声面板同档。
                                // 代价：第二行高 16 → 24dp，整行 41 → 49dp，行距略松（用户已接受）。
                                boxSize = 24.dp,
                                iconSize = 20.dp,
                            )
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
