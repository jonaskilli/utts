package com.github.jing332.tts_server_android.compose.theme

import androidx.compose.ui.graphics.Color

// 静态主题 surfaceContainer 七槽（2026-10-11 晚 V1 版，勿手改；生成器=临时文件/gen_radix_tones_v1.py）。
// 数据源=@radix-ui/colors 3.0.0（MIT，shadcn/ui 同款）sRGB 主值。
// V1（用户拍板「按推荐的来」，修 Radix 版结构层发灰发暗）：浅色结构层弃配对中性灰，
//   改随主题 accent 淡彩——比卡底深一档、比标签(step4)浅一档，全页回到带主题色的清亮：
//   Lowest=step1（页底） Low=step2（卡底） Container=step3（弹窗底/搜索框/次级分组条）
//   High=step3→step4 六成混白（主分组条深槽） Highest=step4→step5 三成混白（最深嵌套槽）
//   Dim=step3 Bright=step1。
// 10-12 L2（用户实拍「卡片色丑」挑定）：GREEN 三个结构槽整体提亮一档
//   （container E6F6EB→EAF7EF、high DCF3E4→E2F4E8、highest D1EEDB→DAF1E1），
//   三级台阶 3.24/3.13/3.15 全部保留（app 自己接受的最小台阶=页底→卡片 2.52）。
// 10-13 深夜用户令「配置项卡片色用以前的，现在的太深了」：GREEN 卡底（Low 槽）#EFF6F1 回滚 → #F4FBF6
//   （反转 80738f6 当晚挑的「微深一档」；#F4FBF6 = 10-11 V1 起用了两天的旧卡底）。
//   组头带=lerp(background, Low, 0.5) 自动跟移（≈#F8FCF7）。
// 11-08 用户令「主题只留动态颜色和绿色」：其余九套彩色彩板退役，本表随之只剩
//   DEFAULT/GREEN 两行（动态取色无静态表，Theme.kt dynamicNeutral() 现算）。
// 标签/选中档不在本表：Color2 浅色 secondaryContainer=accent step4，softContainerColor() 取之（不动）。
// 深色表维持 10-10 版未动（未装机验收过，不随本批盲改）。
// 列表顺序固定：[lowest, low, container, high, highest, dim, bright]，消费方=Theme.kt themedNeutral()。

internal val lightSurfaceTones: Map<AppTheme, List<Color>> = mapOf(
    AppTheme.DEFAULT to listOf(Color(0xFFFBFEFD), Color(0xFFF4FBF7), Color(0xFFE6F7ED), Color(0xFFDCF3E7), Color(0xFFD0EFDF), Color(0xFFE6F7ED), Color(0xFFFBFEFD)),
    AppTheme.GREEN to listOf(Color(0xFFFBFEFC), Color(0xFFF4FBF6), Color(0xFFEAF7EF), Color(0xFFE2F4E8), Color(0xFFDAF1E1), Color(0xFFEAF7EF), Color(0xFFFBFEFC)),
)

internal val darkSurfaceTones: Map<AppTheme, List<Color>> = mapOf(
    AppTheme.DEFAULT to listOf(Color(0xFF161814), Color(0xFF222420), Color(0xFF262824), Color(0xFF31332F), Color(0xFF3C3E3A), Color(0xFF1A1C18), Color(0xFF40433E)),
    AppTheme.GREEN to listOf(Color(0xFF141815), Color(0xFF202421), Color(0xFF242925), Color(0xFF2F3330), Color(0xFF3A3E3B), Color(0xFF181C19), Color(0xFF3E433F)),
)
