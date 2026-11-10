package com.github.jing332.tts_server_android.compose.theme

import androidx.compose.ui.graphics.Color

/* ============================================================================
 * 主题色板（11-10 重做，用户令「按推荐方案开工」）
 *
 * ## 这套表怎么来的
 *
 * 参考坐标 = dsh-Go（github.com/ccch1mneyyy/dsh-Go）的配色体系：**不硬编每套主题的
 * 几十个色值**，而是一条 OKLCH 明度表 + 每套只换色相/彩度。中性灰（页面/卡片/标签底）
 * 的彩度 = 主色彩度 × 0.12，所以每套主题的"灰"都带着自己的一点色相，而不是十套共用同一批灰。
 *
 * ## 旧表（原作者 + 手调）的三个硬伤（11-10 审计实锤，数字见下）
 *
 * ① 主色明度各走各的：绿 L=0.61 / 粉 L=0.72 / 紫 L=0.48。粉亮到**白字压上去只有 2.65:1**
 *    （正文要求 4.5），主按钮字发虚；紫又重到 7.28。现在全部收到 L≈0.50，白字 5.9+。
 * ② 标签卡（角色列表第一行显示名）＝ 主色字压 surfaceContainerHighest 底：
 *    绿 2.95 / 粉 2.03 全都不达标，只有紫 5.73 和动态取色 4.97 碰巧过线——
 *    这正是用户「只对动态颜色满意」的真因，不是审美问题。现主色对标签底 ≥4.6。
 * ③ 中性槽色相跑偏：粉主色 H=5、中性槽却 H=337~342；紫主色 H=295、槽 H=311~313
 *    （当时零散手调留下的）。现在浅深两表都锁在主色色相上。
 *
 * ## 命名约定（改动时别乱）
 *
 * 每套 4 组：`<name>_seed`（浅色主色，也是主题选择器上的小色点）、
 * `<name>_md_theme_light_*` / `<name>_md_theme_dark_*`（M3 三十槽）、
 * `<name>_md_theme_light_*` 里的 primary 直接复用 seed（旧表里那一堆
 * `*_md_theme_light_primary` 全仓零引用，本次一并删除）。
 *
 * ⚠️ bean_green_* 整张表已删：注释说它是「Android 12 以下动态取色回落用」，
 *    但 dynamicColorTheme() 实际调的是 greenTheme()，从来没读过这张表（死代码）。
 * ========================================================================== */

// ---------- 绿色（原「绿色」重调：压到与标签底 4.6 的明度线）----------
// OKLCH: L=0.494 C=0.100 H=152；浅色主色对标签底 8.32:1，白字 5.90:1
// 11-10 四改（用户令「这就是以前的绿色主题，别叫青竹」）：主色恢复旧值 #109D58。
// 我早上曾按「标签底对比 ≥4.6」的公式把它压深到 #2D7144（白字 5.90），
// 但那改变了用户习惯的绿。既然观感优先，恢复原值，并如实记下代价：
//   白字压主色 3.51、主色压页底 3.46、主色压标签底 2.95（均低于 4.5）
// 这是**用户明确选择**的结果，不是疏漏，别再"顺手修正"回去。
val green_seed = Color(0xFF109D58)
// 11-10 三改（用户令「恢复旧的鲜艳 #82FAAB」）：本枚恢复 11-08 用户特意要回的鲜艳档。
// 我今天按统一公式曾算成 #D0F1D7（彩度 0.049，只有旧值 0.155 的三分之一），
// 导致 FAB 菜单丸/收拢态 FAB 发淡。**这是全表唯一一枚刻意保留高彩度的容器**，
// 别再按公式统一压——其余容器档走 C≈0.04~0.05 的统一口径。
val green_md_theme_light_primaryContainer = Color(0xFF82FAAB)
val green_md_theme_light_onPrimaryContainer = Color(0xFF00210E)
val green_md_theme_light_secondary = Color(0xFF4F6353)
val green_md_theme_light_onSecondary = Color(0xFFFFFFFF)
val green_md_theme_light_secondaryContainer = Color(0xFFD6F1DF)
val green_md_theme_light_onSecondaryContainer = Color(0xFF0D1F13)
val green_md_theme_light_tertiary = Color(0xFF3A646F)
val green_md_theme_light_onTertiary = Color(0xFFFFFFFF)
val green_md_theme_light_tertiaryContainer = Color(0xFFC4E8D1)
val green_md_theme_light_onTertiaryContainer = Color(0xFF001F26)
val green_md_theme_dark_primary = Color(0xFF83B590)
val green_md_theme_dark_onPrimary = Color(0xFF0E2214)
val green_md_theme_dark_primaryContainer = Color(0xFF354138)
val green_md_theme_dark_onPrimaryContainer = Color(0xFFDCE3DD)
val green_md_theme_dark_secondary = Color(0xFFAABDAE)
val green_md_theme_dark_onSecondary = Color(0xFF1A211C)
val green_md_theme_dark_secondaryContainer = Color(0xFF2F3831)
val green_md_theme_dark_onSecondaryContainer = Color(0xFFDAE2DC)
val green_md_theme_dark_tertiary = Color(0xFFB4D2BB)
val green_md_theme_dark_onTertiary = Color(0xFF17291C)
val green_md_theme_dark_tertiaryContainer = Color(0xFF2C362E)
val green_md_theme_dark_onTertiaryContainer = Color(0xFFD9E0DA)
val green_md_theme_light_background = Color(0xFFFBFDF8)
val green_md_theme_light_onBackground = Color(0xFF191C19)
val green_md_theme_light_surface = Color(0xFFF8FAF5)
val green_md_theme_light_onSurface = Color(0xFF191C19)
val green_md_theme_light_surfaceVariant = Color(0xFFDDE5DB)
val green_md_theme_light_onSurfaceVariant = Color(0xFF414941)
val green_md_theme_light_outline = Color(0xFF717971)
val green_md_theme_light_outlineVariant = Color(0xFFC1C9BF)
val green_md_theme_light_inverseSurface = Color(0xFF2E312E)
val green_md_theme_light_inverseOnSurface = Color(0xFFF0F1EC)
val green_md_theme_light_inversePrimary = Color(0xFF65DD91)
val green_md_theme_dark_background = Color(0xFF121512)
val green_md_theme_dark_onBackground = Color(0xFFD7DCD8)
val green_md_theme_dark_surface = Color(0xFF1D201D)
val green_md_theme_dark_onSurface = Color(0xFFD7DCD8)
val green_md_theme_dark_surfaceVariant = Color(0xFF2D322E)
val green_md_theme_dark_onSurfaceVariant = Color(0xFFADB3AE)
val green_md_theme_dark_outline = Color(0xFF757D76)
val green_md_theme_dark_outlineVariant = Color(0xFF2D322E)
val green_md_theme_dark_inverseSurface = Color(0xFFE1E6E2)
val green_md_theme_dark_inverseOnSurface = Color(0xFF1A211C)
val green_md_theme_dark_inversePrimary = Color(0xFF2D7144)

// ---------- 紫色（原「紫色」重调：抄 dsh-Go dusk 的灰调薰衣草）----------
// OKLCH: L=0.510 C=0.078 H=300；浅色主色对标签底 8.15:1，白字 5.96:1
val purple_seed = Color(0xFF6D5B8C)
val purple_md_theme_light_primaryContainer = Color(0xFFECE0FF)
val purple_md_theme_light_onPrimaryContainer = Color(0xFF3E2660)
// 11-10 十八改：secondary 压深一档 #787382 → #706A7A。
// 原因：secondary 现在被用作**文字色**（角色列表第二行插件名、换声面板插件名），
// 原值对这枚 11sp 小字只有 4.38:1，低于 4.5 小字门槛；压深后 4.97 达标。
// 同色相、只降明度，ΔE 与分类 (onSurface) 仍 0.270，区分度不受影响。
// 另一消费点（TTS 列表分类标签文字）跟着变清楚，方向一致。
val purple_md_theme_light_secondary = Color(0xFF706A7A)
val purple_md_theme_light_onSecondary = Color(0xFFFFFFFF)
val purple_md_theme_light_secondaryContainer = Color(0xFFEEE5FF)
val purple_md_theme_light_onSecondaryContainer = Color(0xFF3B2B55)
val purple_md_theme_light_tertiary = Color(0xFF5A516C)
val purple_md_theme_light_onTertiary = Color(0xFFFFFFFF)
val purple_md_theme_light_tertiaryContainer = Color(0xFFF1E6FF)
val purple_md_theme_light_onTertiaryContainer = Color(0xFF403159)
val purple_md_theme_dark_primary = Color(0xFFB1A4CD)
val purple_md_theme_dark_onPrimary = Color(0xFF1F1A29)
val purple_md_theme_dark_primaryContainer = Color(0xFF3F3B45)
val purple_md_theme_dark_onPrimaryContainer = Color(0xFFE2E0E6)
val purple_md_theme_dark_secondary = Color(0xFFB9B4C5)
val purple_md_theme_dark_onSecondary = Color(0xFF201E24)
val purple_md_theme_dark_secondaryContainer = Color(0xFF36343C)
val purple_md_theme_dark_onSecondaryContainer = Color(0xFFE0DEE5)
val purple_md_theme_dark_tertiary = Color(0xFFCCC4DD)
val purple_md_theme_dark_onTertiary = Color(0xFF26212F)
val purple_md_theme_dark_tertiaryContainer = Color(0xFF34313A)
val purple_md_theme_dark_onTertiaryContainer = Color(0xFFDFDDE3)
val purple_md_theme_light_background = Color(0xFFFAF9FD)
val purple_md_theme_light_onBackground = Color(0xFF26242C)
val purple_md_theme_light_surface = Color(0xFFFAF8FD)
val purple_md_theme_light_onSurface = Color(0xFF26242C)
val purple_md_theme_light_surfaceVariant = Color(0xFFE4E2E9)
val purple_md_theme_light_onSurfaceVariant = Color(0xFF66636B)
val purple_md_theme_light_outline = Color(0xFF87858C)
val purple_md_theme_light_outlineVariant = Color(0xFFDBD9E0)
val purple_md_theme_light_inverseSurface = Color(0xFF2F2D33)
val purple_md_theme_light_inverseOnSurface = Color(0xFFF0EFF3)
val purple_md_theme_light_inversePrimary = Color(0xFFB1A4CD)
val purple_md_theme_dark_background = Color(0xFF141316)
val purple_md_theme_dark_onBackground = Color(0xFFDBDADE)
val purple_md_theme_dark_surface = Color(0xFF201F22)
val purple_md_theme_dark_onSurface = Color(0xFFDBDADE)
val purple_md_theme_dark_surfaceVariant = Color(0xFF312F34)
val purple_md_theme_dark_onSurfaceVariant = Color(0xFFB2B0B5)
val purple_md_theme_dark_outline = Color(0xFF7B7980)
val purple_md_theme_dark_outlineVariant = Color(0xFF312F34)
val purple_md_theme_dark_inverseSurface = Color(0xFFE5E4E7)
val purple_md_theme_dark_inverseOnSurface = Color(0xFF201E24)
val purple_md_theme_dark_inversePrimary = Color(0xFF6D5B8C)

// ---------- 蓝色（新增：dsh-Go 默认配色）----------
// OKLCH: L=0.508 C=0.126 H=267；浅色主色对标签底 8.15:1，白字 5.95:1
val mist_seed = Color(0xFF4560AD)
val mist_md_theme_light_primaryContainer = Color(0xFFD8E7FF)
val mist_md_theme_light_onPrimaryContainer = Color(0xFF1E3168)
val mist_md_theme_light_secondary = Color(0xFF6C758D)
val mist_md_theme_light_onSecondary = Color(0xFFFFFFFF)
val mist_md_theme_light_secondaryContainer = Color(0xFFDEEAFF)
val mist_md_theme_light_onSecondaryContainer = Color(0xFF24335C)
val mist_md_theme_light_tertiary = Color(0xFF44557F)
val mist_md_theme_light_onTertiary = Color(0xFFFFFFFF)
val mist_md_theme_light_tertiaryContainer = Color(0xFFDEEDFF)
val mist_md_theme_light_onTertiaryContainer = Color(0xFF293862)
val mist_md_theme_dark_primary = Color(0xFF90AAE9)
val mist_md_theme_dark_onPrimary = Color(0xFF131C34)
val mist_md_theme_dark_primaryContainer = Color(0xFF373D4C)
val mist_md_theme_dark_onPrimaryContainer = Color(0xFFDDE1EA)
val mist_md_theme_dark_secondary = Color(0xFFACB7D0)
val mist_md_theme_dark_onSecondary = Color(0xFF1B1F28)
val mist_md_theme_dark_secondaryContainer = Color(0xFF303541)
val mist_md_theme_dark_onSecondaryContainer = Color(0xFFDBE0EA)
val mist_md_theme_dark_tertiary = Color(0xFFB8C9EF)
val mist_md_theme_dark_onTertiary = Color(0xFF1A2339)
val mist_md_theme_dark_tertiaryContainer = Color(0xFF2D3340)
val mist_md_theme_dark_onTertiaryContainer = Color(0xFFDADEE8)
val mist_md_theme_light_background = Color(0xFFF7FAFF)
val mist_md_theme_light_onBackground = Color(0xFF21252F)
val mist_md_theme_light_surface = Color(0xFFF6F9FF)
val mist_md_theme_light_onSurface = Color(0xFF21252F)
val mist_md_theme_light_surfaceVariant = Color(0xFFDEE3EE)
val mist_md_theme_light_onSurfaceVariant = Color(0xFF60656E)
val mist_md_theme_light_outline = Color(0xFF818692)
val mist_md_theme_light_outlineVariant = Color(0xFFD5DAE6)
val mist_md_theme_light_inverseSurface = Color(0xFF2A2E37)
val mist_md_theme_light_inverseOnSurface = Color(0xFFEEF0F5)
val mist_md_theme_light_inversePrimary = Color(0xFF90AAE9)
val mist_md_theme_dark_background = Color(0xFF121418)
val mist_md_theme_dark_onBackground = Color(0xFFD8DBE1)
val mist_md_theme_dark_surface = Color(0xFF1D1F24)
val mist_md_theme_dark_onSurface = Color(0xFFD8DBE1)
val mist_md_theme_dark_surfaceVariant = Color(0xFF2D3037)
val mist_md_theme_dark_onSurfaceVariant = Color(0xFFADB1BA)
val mist_md_theme_dark_outline = Color(0xFF767A85)
val mist_md_theme_dark_outlineVariant = Color(0xFF2D3037)
val mist_md_theme_dark_inverseSurface = Color(0xFFE2E4EA)
val mist_md_theme_dark_inverseOnSurface = Color(0xFF1B1F28)
val mist_md_theme_dark_inversePrimary = Color(0xFF4560AD)

// ---------- 深灰（新增：dsh-Go 黑白档；11-10 原名石墨）----------
// 11-10 十二改（用户令「黑色太深了，跟黑煤块一样」）：主色 L 0.301 → 0.400。
// 三处大面积填充都吃 primary——对勾、开关轨道、密钥池的 1/1 徽章，近黑铺开像煤块。
// 现 #44484E：仍是深灰、白字对比 9.20 足够醒目，但不再压抑。
// OKLCH: L=0.400 C=0.012 H=264
val graphite_seed = Color(0xFF44484E)
val graphite_md_theme_light_primaryContainer = Color(0xFFE3E7F0)
val graphite_md_theme_light_onPrimaryContainer = Color(0xFF2F3543)
// 11-10 十二改配套：主色 L 0.301→0.400 后，secondary 原值 #757577 压页底仅 4.40
//（低于正文门槛 4.5），压深到 #616367 得 5.76。secondary 在配置卡的插件名/分类标签上用。
val graphite_md_theme_light_secondary = Color(0xFF616367)
val graphite_md_theme_light_onSecondary = Color(0xFFFFFFFF)
val graphite_md_theme_light_secondaryContainer = Color(0xFFE9EAEE)
val graphite_md_theme_light_onSecondaryContainer = Color(0xFF33353A)
val graphite_md_theme_light_tertiary = Color(0xFF27292C)
val graphite_md_theme_light_onTertiary = Color(0xFFFFFFFF)
val graphite_md_theme_light_tertiaryContainer = Color(0xFFEBEDF1)
val graphite_md_theme_light_onTertiaryContainer = Color(0xFF383B3F)
val graphite_md_theme_dark_primary = Color(0xFFD5D7DD)
val graphite_md_theme_dark_onPrimary = Color(0xFF14161B)
val graphite_md_theme_dark_primaryContainer = Color(0xFF3C3D3F)
val graphite_md_theme_dark_onPrimaryContainer = Color(0xFFE0E1E3)
val graphite_md_theme_dark_secondary = Color(0xFFB6B7B9)
val graphite_md_theme_dark_onSecondary = Color(0xFF1D1F23)
val graphite_md_theme_dark_secondaryContainer = Color(0xFF353537)
val graphite_md_theme_dark_onSecondaryContainer = Color(0xFFDEE0E2)
val graphite_md_theme_dark_tertiary = Color(0xFFC7C9CC)
val graphite_md_theme_dark_onTertiary = Color(0xFF232426)
val graphite_md_theme_dark_tertiaryContainer = Color(0xFF323334)
val graphite_md_theme_dark_onTertiaryContainer = Color(0xFFDDDEE0)
val graphite_md_theme_light_background = Color(0xFFF9FAFC)
val graphite_md_theme_light_onBackground = Color(0xFF23252A)
val graphite_md_theme_light_surface = Color(0xFFF8F9FC)
val graphite_md_theme_light_onSurface = Color(0xFF23252A)
val graphite_md_theme_light_surfaceVariant = Color(0xFFE1E3E8)
val graphite_md_theme_light_onSurfaceVariant = Color(0xFF626569)
val graphite_md_theme_light_outline = Color(0xFF84868B)
val graphite_md_theme_light_outlineVariant = Color(0xFFD8DADF)
val graphite_md_theme_light_inverseSurface = Color(0xFF2C2E32)
val graphite_md_theme_light_inverseOnSurface = Color(0xFFEFF0F2)
val graphite_md_theme_light_inversePrimary = Color(0xFFD5D7DD)
val graphite_md_theme_dark_background = Color(0xFF131415)
val graphite_md_theme_dark_onBackground = Color(0xFFD9DBDD)
val graphite_md_theme_dark_surface = Color(0xFF1E1F21)
val graphite_md_theme_dark_onSurface = Color(0xFFD9DBDD)
val graphite_md_theme_dark_surfaceVariant = Color(0xFF2F3033)
val graphite_md_theme_dark_onSurfaceVariant = Color(0xFFAFB1B4)
val graphite_md_theme_dark_outline = Color(0xFF787A7E)
val graphite_md_theme_dark_outlineVariant = Color(0xFF2F3033)
val graphite_md_theme_dark_inverseSurface = Color(0xFFE3E4E7)
val graphite_md_theme_dark_inverseOnSurface = Color(0xFF1D1F23)
val graphite_md_theme_dark_inversePrimary = Color(0xFF2B2E33)

// 错误色：四套主题共用（语义色，不随主题转）。深色档提亮一档保证暗底可读。
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)

// scrim 恒定
val md_theme_scrim = Color(0xFF000000)
