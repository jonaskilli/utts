package com.github.jing332.tts_server_android.compose.theme

import androidx.compose.ui.graphics.Color

/* ============================================================================
 * 静态主题 surfaceContainer 七槽（11-10 重做，生成器=临时文件 gen2.py，勿手改）
 *
 * 口径抄 dsh-Go 的 NeutralTones：**明度固定成一条表，彩度 = 主色彩度 × 0.12**，
 * 色相锁死 = 主色色相。好处是每套主题的灰都带自己的一点色，且各级文字的对比度
 * 不随主题漂（换主题只换观感，不换可读性）。
 *
 * 明度表：浅 0.991 / 0.982 / 0.958 / 0.940 / 0.916 / 0.930 / 0.992
 *         深 0.190 / 0.240 / 0.275 / 0.310 / 0.350 / 0.205 / 0.400
 * 顺序固定：[lowest, low, container, high, highest, dim, bright]，消费方=Theme.kt themedNeutral()。
 *
 * 旧表（10-10/10-11 版）的毛病（11-10 审计）：深色档中性阶彩度只有 0.007~0.010，
 * 而 dsh-Go 同槽是 0.0205——深色下主题色"消失"成纯灰。现浅深两档同一口径。
 *
 * 标签/选中档仍不在本表：显示名标签底取 Highest 槽，字取主题主色（对比度见 Color2 注释）。
 * ========================================================================== */

internal val lightSurfaceTones: Map<AppTheme, List<Color>> = mapOf(
    AppTheme.GREEN to listOf(Color(0xFFFAFDFA), Color(0xFFF5FBF6), Color(0xFFECF3ED), Color(0xFFE6EEE7), Color(0xFFDDE6DF), Color(0xFFE3EAE4), Color(0xFFFAFDFB)),
    AppTheme.PINK to listOf(Color(0xFFFFFBFC), Color(0xFFFDF7FA), Color(0xFFF7EEF2), Color(0xFFF1E8EC), Color(0xFFEAE0E4), Color(0xFFEDE5E8), Color(0xFFFFFBFD)),
    AppTheme.PURPLE to listOf(Color(0xFFFCFBFF), Color(0xFFFAF8FD), Color(0xFFF2F0F6), Color(0xFFECEAF1), Color(0xFFE4E2E9), Color(0xFFE8E7ED), Color(0xFFFDFCFF)),
    AppTheme.MIST to listOf(Color(0xFFFAFCFF), Color(0xFFF6F9FF), Color(0xFFEDF1FB), Color(0xFFE6EBF6), Color(0xFFDEE3EE), Color(0xFFE4E8F1), Color(0xFFFAFCFF)),
    AppTheme.DAIQING to listOf(Color(0xFFF9FDFF), Color(0xFFF5FAFD), Color(0xFFEBF2F7), Color(0xFFE5ECF1), Color(0xFFDDE4EA), Color(0xFFE2E9ED), Color(0xFFFAFDFF)),
    AppTheme.TURQUOISE to listOf(Color(0xFFF9FDFC), Color(0xFFF5FAFA), Color(0xFFECF3F2), Color(0xFFE5EDEC), Color(0xFFDDE5E4), Color(0xFFE3E9E8), Color(0xFFFAFDFD)),
    AppTheme.AMBER to listOf(Color(0xFFFEFCF9), Color(0xFFFCF8F4), Color(0xFFF5F0EB), Color(0xFFF0EAE4), Color(0xFFE8E2DC), Color(0xFFECE7E2), Color(0xFFFEFCFA)),
    AppTheme.GRAPHITE to listOf(Color(0xFFFBFCFE), Color(0xFFF8F9FC), Color(0xFFEFF1F5), Color(0xFFE9EBEF), Color(0xFFE1E3E8), Color(0xFFE6E8EC), Color(0xFFFCFCFE)),
)

internal val darkSurfaceTones: Map<AppTheme, List<Color>> = mapOf(
    AppTheme.GREEN to listOf(Color(0xFF121512), Color(0xFF1D201D), Color(0xFF242925), Color(0xFF2D322E), Color(0xFF373C38), Color(0xFF141815), Color(0xFF444945)),
    AppTheme.PINK to listOf(Color(0xFF161314), Color(0xFF221E20), Color(0xFF2B2628), Color(0xFF342F31), Color(0xFF3F393B), Color(0xFF1A1617), Color(0xFF4B4648)),
    AppTheme.PURPLE to listOf(Color(0xFF141316), Color(0xFF201F22), Color(0xFF28272B), Color(0xFF312F34), Color(0xFF3B3A3E), Color(0xFF18161A), Color(0xFF48474B)),
    AppTheme.MIST to listOf(Color(0xFF121418), Color(0xFF1D1F24), Color(0xFF25282E), Color(0xFF2D3037), Color(0xFF373A42), Color(0xFF15171C), Color(0xFF45484E)),
    AppTheme.DAIQING to listOf(Color(0xFF121416), Color(0xFF1C2022), Color(0xFF24282B), Color(0xFF2D3134), Color(0xFF363B3F), Color(0xFF14181A), Color(0xFF44484B)),
    AppTheme.TURQUOISE to listOf(Color(0xFF121414), Color(0xFF1D2020), Color(0xFF242928), Color(0xFF2D3131), Color(0xFF373C3B), Color(0xFF141817), Color(0xFF444948)),
    AppTheme.AMBER to listOf(Color(0xFF151311), Color(0xFF211F1C), Color(0xFF2A2724), Color(0xFF33302C), Color(0xFF3E3A36), Color(0xFF191714), Color(0xFF4A4744)),
    AppTheme.GRAPHITE to listOf(Color(0xFF131415), Color(0xFF1E1F21), Color(0xFF26282A), Color(0xFF2F3033), Color(0xFF393A3D), Color(0xFF161719), Color(0xFF46484A)),
)
