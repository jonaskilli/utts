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
    AppTheme.GREEN to listOf(Color(0xFFF9FDFA), Color(0xFFF1FDF3), Color(0xFFE4F7E8), Color(0xFFDBF2E0), Color(0xFFD1EBD6), Color(0xFFD9EEDE), Color(0xFFFBFDFB)),
    AppTheme.PURPLE to listOf(Color(0xFFFCFBFF), Color(0xFFFAF7FF), Color(0xFFF3EEFE), Color(0xFFEEE8FB), Color(0xFFE6DFF6), Color(0xFFEAE4F7), Color(0xFFFDFCFE)),
    AppTheme.MIST to listOf(Color(0xFFFAFCFF), Color(0xFFF4F9FF), Color(0xFFE8F1FF), Color(0xFFE1EBFF), Color(0xFFD7E3FF), Color(0xFFDEE8FE), Color(0xFFFBFCFF)),
    AppTheme.GRAPHITE to listOf(Color(0xFFFCFCFC), Color(0xFFF8F9FA), Color(0xFFF0F1F3), Color(0xFFEAEBED), Color(0xFFE2E3E6), Color(0xFFE7E8EA), Color(0xFFFCFCFD)),
)

internal val darkSurfaceTones: Map<AppTheme, List<Color>> = mapOf(
    AppTheme.GREEN to listOf(Color(0xFF101511), Color(0xFF1B211C), Color(0xFF232A25), Color(0xFF2B322D), Color(0xFF353D37), Color(0xFF131914), Color(0xFF424A44)),
    AppTheme.PURPLE to listOf(Color(0xFF141318), Color(0xFF201E24), Color(0xFF28262D), Color(0xFF312F36), Color(0xFF3B3941), Color(0xFF18161C), Color(0xFF49464E)),
    AppTheme.MIST to listOf(Color(0xFF121419), Color(0xFF1D1F25), Color(0xFF25282E), Color(0xFF2D3037), Color(0xFF373A42), Color(0xFF14171D), Color(0xFF44484F)),
    AppTheme.GRAPHITE to listOf(Color(0xFF131416), Color(0xFF1E1F22), Color(0xFF26282B), Color(0xFF2F3034), Color(0xFF393B3E), Color(0xFF16171A), Color(0xFF46484B)),
)
