package com.github.jing332.tts_server_android.compose.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ============================================================================
 * 圆角阶梯（11-10 立，用户令「反正都是我接手之后瞎搞的，你看着改」）
 *
 * ## 为什么要立这个文件
 *
 * 立此文件前全仓手写圆角 **11 种字面值**（2/4/5/6/8/9/10/12/20/24/28dp，
 * 59 处调用）：9dp 和 8dp 并排（角色列表标签框 8dp、同行 emoji 丸 9dp）、
 * 5dp 和 6dp 同类控件混用、2dp 在 4dp 高的拖拽条上等于没倒角。
 * 这些值最早来自 Wedow 分支的自定义控件手搓（日志行流、角色列表、密钥页都
 * 没有 M3 现成组件），原作者上游同仓几乎不用手写圆角——**不是设计决定，
 * 是各写各的**。8dp 那条「字段语汇」注释是唯一有意的，本次保留其语义。
 *
 * ## 五档 + 胶囊：为什么是这五个数
 *
 * | 档 | 半径 | 用途 | 依据 |
 * |---|---|---|---|
 * | xs | 4 | 行内高亮底、小字底、拖拽指示条 | M3 官方 extraSmall |
 * | sm | 6 | 小胶囊、图标键、行末操作文字底 | 控件高约 24dp，6 是 1/4 |
 * | md | 8 | 字段底、标签框（角色列表显示名） | M3 官方 small |
 * | lg | 12 | 卡片、整行可点块、按压高亮 | M3 官方 medium |
 * | xl | 28 | 对话框 | M3 官方 extraLarge |
 * | pill | 50% | 真胶囊（状态丸、筛选 chip、签名条） | — |
 *
 * 相邻两档差 1.5~3.3 倍，肉眼分得开；同档内不再有「差 1dp 的第二种」。
 *
 * ## 规矩（新增控件时遵守）
 *
 * - **只能引用这里的常量**，不要再写 `AppShapes.md`。
 * - 要加新档先问：能不能塞进现有五档？塞不进再加，加完更新上表。
 * - 非均匀圆角（如底部弹层只圆上两角）用 `RoundedCornerShape(topStart=…, topEnd=…)`，
 *   半径仍取本文件的 dp 值，别另起。
 * ========================================================================== */

object AppShapes {
    /** 行内高亮底、小字底、4dp 高的拖拽指示条。 */
    val xs = RoundedCornerShape(4.dp)

    /** 小胶囊、图标键、行末操作文字底（控件高约 24dp）。 */
    val sm = RoundedCornerShape(6.dp)

    /** 字段底、标签框（角色列表显示名、换声弹窗分类字段）。 */
    val md = RoundedCornerShape(8.dp)

    /** 卡片、整行可点块、按压高亮。 */
    val lg = RoundedCornerShape(12.dp)

    /** 底部弹层、大卡（M3 官方 large）。 */
    val xl = RoundedCornerShape(16.dp)

    /** 对话框（M3 官方 extraLarge）。 */
    val xxl = RoundedCornerShape(28.dp)

    /** 真胶囊：状态丸、筛选 chip、签名条、性别色点兜底。 */
    val pill = RoundedCornerShape(50)

    // 仰角按需的单值引用（给需要自己拼角形状的地方，如弹层只圆上两角）
    val xsDp = 4.dp
    val smDp = 6.dp
    val mdDp = 8.dp
    val lgDp = 12.dp
    val xlDp = 16.dp
    val xxlDp = 28.dp
}

/**
 * 传给 [androidx.compose.material3.MaterialTheme] 的 shapes。
 *
 * ## 映射口径：**对齐 M3 官方五档**，不自创
 *
 * 11-10 初版曾把 small 压成 6dp、large 压成 12dp 来"凑五档"，结果：
 * 全仓 12+ 处走 `MaterialTheme.shapes.*` 的地方（TTS 分类标签、AboutDialog、
 * 导入面板、BGM 配置…）全部变小——而它们在改造前用的是 M3 官方值。
 * 用户实拍指出「几 dp 得看以前怎么弄的」，逐项核对后确认官方值才是既有标准。
 *
 * ⚠️ 现在五档 = M3 官方 4/8/12/16/28，**不要再为"整齐"去压某档**：
 * - `extraLarge = 28`：AlertDialog 默认取这档，全仓 27 个走 AppDialog 的弹窗靠它
 *   保持观感零变化。
 * - `small = 8`：标签、Chip、字段。**这条尤其别动**——它就是角色列表标签
 *   与 TTS 分类标签统一的那一档（用户 11-10 从 6/8/10/12 里选定 8dp）。
 *
 * 手写处（`RoundedCornerShape(N.dp)`）一律引用 AppShapes 常量，别再写字面量。
 */
val AppMaterialShapes = Shapes(
    extraSmall = AppShapes.xs,      //  4dp — M3 官方
    small = AppShapes.md,           //  8dp — M3 官方（11-10 修：曾误设 6dp，
                                    //         导致 TTS 分类标签等 12+ 处走 shapes.small 的地方变小）
    medium = AppShapes.lg,          // 12dp — M3 官方
    large = AppShapes.xl,           // 16dp — M3 官方（11-10 修：曾误设 12dp）
    extraLarge = AppShapes.xxl,     // 28dp — M3 官方（AlertDialog 取这档）
)

/* ============================================================================
 * 字号阶梯补丁（11-10 立，用户令「按你推荐的改」）
 *
 * ## 为什么需要「补丁」而不是重写 Typography
 *
 * M3 官方阶梯是 11/12/14/16/22 这一套，间隔偏大。这个 App 的高频界面是
 * **密集面板**（日志行流 16 处字号、密钥卡 14 处），M3 那套塞不下——
 * 所以历史上出现了 15sp、13sp、10sp 这些「比官方细一档」的值。
 *
 * 问题不在这些值本身，而在它们**每次都是就地 `.copy(fontSize = 15.sp)`**：
 * 13 处 15sp 散在 10 个文件里，想统一调一档要满仓搜。
 *
 * ## 处理原则
 *
 * 1. **能删的删**：`labelSmall.copy(fontSize = 11.sp)` 共 15 处，M3 的
 *    labelSmall 默认就是 11sp，这句等于没写——全部删成 `labelSmall`。
 * 2. **有语义的提成 token**：下面这几个是把散落的字面量收拢，值一个没改，
 *    所以**观感零变化**，只是以后调档改一处。
 * 3. 日志正文的 13sp 走 AnnotatedString SpanStyle（9 处集中在 LogScreen），
 *    不属于 Text 样式层，不动。
 * ========================================================================== */

object AppTypeScale {
    /** 徽章小字（已启用 N / 未启用）：10sp，比 labelSmall 再小一档。6 处。 */
    val badge = 10.sp

    /** 密集列表正文（对话框条目、分组名、插件名行）：15sp，比 bodyMedium 大 1。13 处。 */
    val dense = 15.sp

    /** 密集列表辅助字（时间戳、说明、行内操作）：13sp。 */
    val denseSmall = 13.sp
}
