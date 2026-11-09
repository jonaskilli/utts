package com.github.jing332.tts_server_android.compose.theme

import androidx.compose.ui.graphics.Color

/* ============================================================================
 * 静态主题 surfaceContainer 七槽（11-10 重做，生成器=临时文件 gen2.py，勿手改）
 *
 * ⚠️ 11-10 二次修正（用户实拍「灰灰的」）：初版照抄 dsh-Go 的「中性彩度 = 主色彩度 ×0.12」，
 * 但 dsh-Go 的主色彩度本身就很低（0.068~0.126，它整套是低彩度设计），而 UTTS 暮紫主色
 * 彩度 0.078 × 0.12 = 0.0094 —— 几乎纯灰。旧表同槽是 0.032~0.046，所以初版看起来"褪色"。
 * 现改：**彩度 = 主色彩度 × 0.40，上限 0.040**（对齐旧表手感，又不至于像 ×0.55 那样艳）。
 * 深色档同理：初版 0.0075~0.0099 偏灰，现取 min(0.013, max(C×0.16, 0.006))。
 *
 * 口径抄 dsh-Go 的 NeutralTones：**明度固定成一条表**，
 * 色相锁死 = 主色色相。好处是每套主题的灰都带自己的一点色，且各级文字的对比度
 * 不随主题漂（换主题只换观感，不换可读性）。
 *
 * 明度表：浅 0.991 / 0.982 / 0.958 / 0.940 / 0.916 / 0.930 / 0.992
 *         深 0.190 / 0.240 / 0.275 / 0.310 / 0.350 / 0.205 / 0.400
 * 顺序固定：[lowest, low, container, high, highest, dim, bright]，消费方=Theme.kt themedNeutral()。
 *
 * 深色档：旧表（10-10 版）彩度 0.007~0.010 偏灰、主题色几乎消失；本例现值 0.012~0.015，
 * 比旧表略带电色相，但没到 dsh-Go 的 0.0205（那个在 UTTS 的密集界面上偏艳）。
 *
 * 标签/选中档仍不在本表：显示名标签底取 Highest 槽，字取主题主色（对比度见 Color2 注释）。
 * ========================================================================== */

internal val lightSurfaceTones: Map<AppTheme, List<Color>> = mapOf(
    AppTheme.GREEN to listOf(Color(0xFFFAFEFB), Color(0xFFF9F9F9), Color(0xFFF2F3F2), Color(0xFFEAEBEA), Color(0xFFE4E6E5), Color(0xFFF2F3F2), Color(0xFFFBFEFB)),
    AppTheme.PURPLE to listOf(Color(0xFFFDFCFF), Color(0xFFF9F9F9), Color(0xFFF3F2F3), Color(0xFFEBEAEC), Color(0xFFE6E5E7), Color(0xFFF3F2F3), Color(0xFFFDFCFF)),
    AppTheme.MIST to listOf(Color(0xFFFBFDFF), Color(0xFFF9F9F9), Color(0xFFF2F3F4), Color(0xFFEAEAEC), Color(0xFFE5E5E7), Color(0xFFF2F3F4), Color(0xFFFCFDFF)),
    AppTheme.GRAPHITE to listOf(Color(0xFFFDFDFD), Color(0xFFF9F9F9), Color(0xFFF3F3F3), Color(0xFFEAEAEA), Color(0xFFE5E5E5), Color(0xFFF3F3F3), Color(0xFFFDFDFD)),
)

internal val darkSurfaceTones: Map<AppTheme, List<Color>> = mapOf(
    AppTheme.GREEN to listOf(Color(0xFF121512), Color(0xFF1D201E), Color(0xFF252926), Color(0xFF2E312E), Color(0xFF383C38), Color(0xFF151816), Color(0xFF454946)),
    AppTheme.PURPLE to listOf(Color(0xFF141316), Color(0xFF201F22), Color(0xFF28272B), Color(0xFF312F34), Color(0xFF3B3A3E), Color(0xFF18161A), Color(0xFF48474B)),
    AppTheme.MIST to listOf(Color(0xFF131417), Color(0xFF1E1F22), Color(0xFF26282B), Color(0xFF2F3034), Color(0xFF393A3F), Color(0xFF16171A), Color(0xFF46484C)),
    AppTheme.GRAPHITE to listOf(Color(0xFF141414), Color(0xFF1F1F1F), Color(0xFF282828), Color(0xFF303030), Color(0xFF3A3A3A), Color(0xFF171717), Color(0xFF484848)),
)
