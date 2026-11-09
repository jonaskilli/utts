package com.github.jing332.tts_server_android.compose.theme

import androidx.compose.ui.graphics.Color
import com.github.jing332.tts_server_android.R

/**
 * 主题清单（11-10 重做，用户令「按推荐方案开工」）。
 *
 * **四套静态主题 + 动态颜色**：青竹（原绿）/ 暮紫（原紫）为老主题重调，
 * 雾蓝 / 石墨 为 11-10 按 dsh-Go 的配色体系新增。
 * 11-10 同日删除五套：琥珀（「好难看」）、藕粉、黛青、松石（用户 11-10 逐次点名删除）。
 * 每套的色板由同一张 OKLCH 明度表推出（见 Color2.kt 顶部注释），新增一套 =
 * ①本枚举加项 ②Color2 加色板 ③SurfaceTones 加两行 ④AppThemeConfiguration 加函数
 * ⑤Theme.kt when 加分支 ⑥strings.xml 中英加名字（六处，缺一处编译或运行就断）。
 *
 * DEFAULT（id=""）保留在枚举里但任何 UI 都不列出：它是存量数据「最早版本无主题设置」
 * 的占位值，删掉会让 AppConfig 的 values().find 对旧存量返回 null 走 GREEN 兜底——
 * 结果一样但每次启动都丢一次持久化值；留着则升级路径平直。
 *
 * color 字段=主题选择器上那个小色点（取浅色主色）：
 * 旧版这里放的是各主题原始 seed，粉 #FA7298 亮到在白底卡片上看不清；现统一取新 seed。
 */
enum class AppTheme(val id: String, val stringResId: Int = -1, val color: Color) {
    DEFAULT("", R.string.theme_default, green_seed),
    DYNAMIC_COLOR("dynamicColor", R.string.dynamic_color, Color.Unspecified),

    // —— 老主题重调（三套）——
    GREEN("green", R.string.theme_green, green_seed),
    PURPLE("purple", R.string.theme_purple, purple_seed),

    // —— 新增（四套，色相/彩度取自 dsh-Go；琥珀 11-10 用户令删除，嫌难看）——
    MIST("mist", R.string.theme_mist, mist_seed),
    GRAPHITE("graphite", R.string.theme_graphite, graphite_seed),
}
