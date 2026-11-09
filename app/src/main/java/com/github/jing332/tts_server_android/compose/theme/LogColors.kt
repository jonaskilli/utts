package com.github.jing332.tts_server_android.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.github.jing332.common.ACCENT_GREEN
import com.github.jing332.common.LogLevel

/* ============================================================================
 * 日志配色（11-10 立；同日二改收紧范围，用户实锤「声音信息当时有原本的颜色」）
 *
 * ## 一句话判据
 *
 * **只有 INFO（朗读规则/普通信息行）跟主题，其余一切保持原色；
 * 但绿色主题例外——INFO 用回老日志绿 #4CAF50。**
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
 * ## 11-10 五改：绿色主题例外（用户令「这个绿色就是精髓呀」）
 *
 * 四改前 INFO 一律取 `colorScheme.primary`，绿色主题下就变成主色 #109D58——
 * 比老日志绿 #4CAF50 沉一档。用户看实机后指出「这个绿是精髓」，
 * 也就是说：**绿色主题下的 INFO 本来就该是那颗老绿，不该跟着主色走**。
 *
 * 实现=只给 GREEN 一个例外：其余三套（紫/蓝/黑）继续跟主题主色，
 * 用户原意是"紫蓝黑下别一片绿"，绿色主题下"本来就是绿"不构成割裂。
 *
 * ⚠️ 别再扩大范围：新增级别或想改某级别色，先确认它是「一屏占比最大的那类」还是
 *    「含义固定的信号色」。后者一律不动。
 * ========================================================================== */

/** 日志正文的级别色：INFO 跟主题（绿色主题例外，用回老日志绿），其余保持原色。 */
@Composable
fun logLevelColor(level: Int): Color {
    val dark = isAppInDarkTheme()
    return when (level) {
        // 主题化级别：规则行/普通信息。
        // 绿色主题=老日志绿 #4CAF50（11-10 五改，用户令「这个绿色就是精髓」）：
        // 该主题下 INFO 本来就是绿，跟主色走反而变沉、丢了原味。
        // 其余主题=主题主色，解决"紫/蓝/黑界面里一片绿"的割裂（用户 11-10 原始诉求）。
        LogLevel.INFO ->
            if (getAppTheme() == AppTheme.GREEN) Color(ACCENT_GREEN)
            else MaterialTheme.colorScheme.primary
        // 以下全部回退为原值（11-10 二改）。
        LogLevel.TRACE -> if (dark) Color(0xFFB0BEC5) else Color(0xFF9E9E9E)
        LogLevel.DEBUG -> if (dark) Color(0xFF64B5F6) else Color(0xFF2196F3)
        LogLevel.WARN -> if (dark) Color(0xFFFFD54F) else Color(0xFFFFC107)
        LogLevel.ERROR -> if (dark) Color(0xFFE57373) else Color(0xFFF44336)
        LogLevel.SUCCESS -> if (dark) Color.White else Color.Black
        else -> if (dark) Color.White else Color.Black
    }
}
