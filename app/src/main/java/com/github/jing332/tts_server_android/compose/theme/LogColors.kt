package com.github.jing332.tts_server_android.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.github.jing332.common.LogLevel

/* ============================================================================
 * 日志配色（11-10 立，用户令「日志界面不论换啥主题都是这个绿色，得根据主题换」）
 *
 * ## 分两类，别混
 *
 * **① 结构色——跟主题走。**
 * 规则行（INFO）、角色名小牌、发音人信息、插件名、时间戳这些是「日志的排版层」，
 * 换主题就该换色。旧版全写死在 lib-common/LogLevel.kt 的 toArgb() 里
 * （INFO 恒为 #4CAF50），所以紫色主题下整页还是绿的。
 *
 * **② 语义色——不跟主题走。**
 * 成功 / 失败 / 重试 / 切备用 四态：绿=对、红=错，这层信息如果跟着主题漂
 * （比如紫色主题把"失败"染紫），读者就得重新学一遍颜色含义。低彩度主题下
 * 这些颜色反而要保饱和度才认得出。
 *
 * ## 为什么 INFO 不直接继续用 #4CAF50
 *
 * 实测 #4CAF50 在米白页底（#FBFEFC）上对比度 2.78:1，**低于 WCAG 正文 4.5**，
 * 一屏大量小字长时间看是糊的（用户 11-10 日志截图里的那个绿就是它）。
 * 改用主题 primary 后，各主题 INFO 对比度 5.7~5.8，同时和界面同色相。
 *
 * ⚠️ 只做 @Composable：颜色依赖 MaterialTheme，不能在 lib-common 里算。
 *    lib-common 的 toArgb() 保留给「拿不到 Composition 的极端场景」兜底。
 * ========================================================================== */

/** 日志正文的级别色（结构色跟主题、语义色固定）。 */
@Composable
fun logLevelColor(level: Int): Color {
    val scheme = MaterialTheme.colorScheme
    return when (level) {
        // 规则行/普通信息：跟主题主色（这是一屏里占比最大的那类）
        LogLevel.INFO -> scheme.primary
        // 调试/详细：退到中性色，别和 INFO 抢注意力
        LogLevel.DEBUG -> scheme.tertiary
        LogLevel.TRACE -> scheme.onSurfaceVariant
        // 以下三档是语义色，不跟主题
        LogLevel.WARN -> LogSemantic.Amber
        LogLevel.ERROR -> LogSemantic.Danger
        LogLevel.SUCCESS -> scheme.onSurface
        else -> scheme.onSurface
    }
}

/**
 * 日志里的语义色常量（成功/失败/重试/备用）。
 *
 * 浅色档压到 4.5 以上：旧版成功绿 #4CAF50 在淡绿丸底上只有 2.58，
 * 现在的 #1B7F3B 在八套主题的页底上都 ≥4.85（暖偏向最差 4.79）。
 * 深色档提亮，暗底上才读得出。
 */
object LogSemantic {
    /** 成功 · 绿（不随主题）。 */
    val Success = Color(0xFF1B7F3B)
    val SuccessDark = Color(0xFF7FD69A)

    /** 失败 · 红（与 M3 error 同族，但不读主题的 error，避免主题化后失真）。 */
    val Danger = Color(0xFFC62828)
    val DangerDark = Color(0xFFEF9A9A)

    /** 重试 · 中性灰蓝。 */
    val Neutral = Color(0xFF4A5568)
    val NeutralDark = Color(0xFF9AA0A8)

    /** 切备用 · 琥珀。 */
    val Amber = Color(0xFF8A6A10)
    val AmberDark = Color(0xFFD9BC6A)
}

/** 按深浅取语义色。 */
fun semanticColor(light: Color, dark: Color, isDark: Boolean): Color =
    if (isDark) dark else light
