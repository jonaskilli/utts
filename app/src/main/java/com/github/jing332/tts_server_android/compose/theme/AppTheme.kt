package com.github.jing332.tts_server_android.compose.theme

import androidx.compose.ui.graphics.Color
import com.github.jing332.tts_server_android.R

/**
 * 11-08 用户令（三轮收敛后终态）：红/蓝退役，保留绿/粉/橙/青/紫/棕/灰 + 动态颜色八档，
 * 存量选了被删主题的由 AppConfig 读取处兜底改选绿色。
 *
 * DEFAULT（id=""）保留在枚举里但任何 UI 都不列出：它是存量数据「最早版本无主题设置」
 * 的占位值，删掉会让 AppConfig 的 values().find 对旧存量返回 null 走 GREEN 兜底——
 * 结果一样但每次启动都丢一次持久化值；留着则升级路径平直。
 */
enum class AppTheme(val id: String, val stringResId: Int = -1, val color: Color) {
    DEFAULT("", R.string.theme_default, green_seed),
    DYNAMIC_COLOR("dynamicColor", R.string.dynamic_color, Color.Unspecified),
    GREEN("green", R.string.green, green_seed),
    PINK("pink", R.string.pink, pink_seed),
    CYAN("cyan", R.string.cyan, cyan_seed),
    ORANGE("orange", R.string.orange, orange_seed),
    PURPLE("purple", R.string.purple, purple_seed),
    BROWN("brown", R.string.brown, brown_seed),
    GRAY("gray", R.string.gray, gray_seed),
}
