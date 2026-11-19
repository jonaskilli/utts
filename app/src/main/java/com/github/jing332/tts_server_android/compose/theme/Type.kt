package com.github.jing332.tts_server_android.compose.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.jing332.tts_server_android.R

/**
 * 段号（N/M）专用字体：IBM Plex Mono Medium。
 *
 * 11-19 用户拍板换字体（原为 Roboto），11-20 装机实拍「太重」→ 降一档 500。
 * 只用于「16/359」这一处读数：
 *   · 日志行右上角段号（LogScreen.kt）；
 *   · 吸顶「当前：…｜缓存：…」行里的 N/M（TtsLogScreen.kt 的 boldSeg）。
 * 时间、胶囊、正文、进度行的中文一律不动。
 *
 * ⚠️ 为什么要显式写 FontWeight.Medium：
 * 字体文件本身就是 500 字重（OS/2 usWeightClass=500），但 Compose 只认
 * 这里声明的 weight 值——不写就按 Normal(400) 登记。而两处调用方都是
 * SpanStyle(fontWeight = Medium) 去要 500，登记对不上就会叠一层**合成伪粗**
 * （FontSynthesis），字会糊一圈、还比设计稿重。写上 Medium 是「告诉 Compose
 * 这个文件是什么」，不是额外打包字重：整个字体只有一个文件，约 2.1 KB。
 * 调用方的 SpanStyle(fontWeight = Medium) 必须与这里同步改——两处都要改。
 */
val SegNumberFont = FontFamily(
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium)
)

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)