package com.github.jing332.tts_server_android.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.github.jing332.common.LogLevel

/* ============================================================================
 * 日志配色（11-10 立；同日二改收紧范围，用户实锤「声音信息当时有原本的颜色」）
 *
 * ## 一句话判据
 *
 * **只有 INFO（朗读规则/普通信息行）跟主题，其余一切保持原色。**
 *
 * ## 为什么只有 INFO
 *
 * 用户 11-10 的原话是「日志界面不论换啥主题日志都是这个绿色，得根据主题换」——
 * 指的是一屏里占比最大的那类**规则行**（`[SpeechRule] …` 那种整段绿字）。
 * 它们在紫色主题下还是一片绿，跟界面打架，所以要跟主题。
 *
 * 一改时我把 DEBUG/WARN/ERROR 也一起换成主题槽 + 新语义色，属于**过度修改**：
 *
 * - DEBUG 原本是蓝 `#2196F3`、WARN 原本是琥珀 `#FFC107`、ERROR 原本是红 `#F44336`——
 *   这些是用户长期看惯的级别信号，换主题时它们**本来就不该变**（红就是错，蓝就是调试，
 *   跟主题色相无关）。二改全部回退。
 * - TRACE 原本中性灰 `#9E9E9E`，同上回退。
 * - SUCCESS 原本就是跟随深浅的黑/白（原注释「SUCCESS 跟随主题」），维持不变。
 *
 * ## 为什么不用 lib-common 的 toArgb()
 *
 * INFO 要读 `MaterialTheme.colorScheme.primary`，那是 @Composable 作用域才有的。
 * 所以本函数是 @Composable；lib-common 的 `toArgb()` 保留作**非 Composition 场景**
 * （如纯 Java 侧日志格式化）的兜底，两处的非 INFO 部分数值一致。
 *
 * ⚠️ 别再扩大范围：新增级别或想改某级别色，先确认它是「一屏占比最大的那类」还是
 *    「含义固定的信号色」。后者一律不动。
 * ========================================================================== */

/** 日志正文的级别色：INFO 跟主题，其余保持原色。 */
@Composable
fun logLevelColor(level: Int): Color {
    val dark = isAppInDarkTheme()
    return when (level) {
        // 唯一的主题化级别：规则行/普通信息。原为 ACCENT_GREEN #4CAF50（白底 2.78:1），
        // 换成主题主色后各主题 5.7~5.8，且换主题时整片规则行跟着变——这是用户要的那条。
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        // 以下全部回退为原值（11-10 二改）。
        LogLevel.TRACE -> if (dark) Color(0xFFB0BEC5) else Color(0xFF9E9E9E)
        LogLevel.DEBUG -> if (dark) Color(0xFF64B5F6) else Color(0xFF2196F3)
        LogLevel.WARN -> if (dark) Color(0xFFFFD54F) else Color(0xFFFFC107)
        LogLevel.ERROR -> if (dark) Color(0xFFE57373) else Color(0xFFF44336)
        LogLevel.SUCCESS -> if (dark) Color.White else Color.Black
        else -> if (dark) Color.White else Color.Black
    }
}
