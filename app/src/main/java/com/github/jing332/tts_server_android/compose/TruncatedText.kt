package com.github.jing332.tts_server_android.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/**
 * **按完整字符截断**的 AnnotatedString 文本（11-10 用户令，反复强调「不要省略号，
 * 超过就截断」，并实拍「字显示不全、不是完整的汉字」）。
 *
 * ## 为什么不能用 TextOverflow.Clip
 *
 * Clip 是**按像素**裁剪：汉字 13sp 宽且非整像素对齐，裁在字中间就留下半个字。
 * 用户看到的「第二个字右侧不完整」正是它。
 *
 * ## 做法
 *
 * [TextMeasurer] 量宽，按**码点**累加，超宽就停在**上一个完整字符**之前，不补省略号。
 * 二分查找让每行测量次数稳定 log2(n)，长列表滚动不掉帧。码点切分保证 emoji 不被劈开。
 */
@Composable
fun TruncatedAnnotatedText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val shown = remember(text, style, maxWidth, density) {
        val plain = text.text
        val cps = plain.codePoints().toArray()
        if (cps.isEmpty() || maxWidth == Dp.Infinity) return@remember text
        val maxPx = with(density) { maxWidth.toPx() }
        fun plainWidth(count: Int): Float =
            measurer.measure(String(cps, 0, count), style, maxLines = 1, softWrap = false)
                .size.width.toFloat()
        if (plainWidth(cps.size) <= maxPx) return@remember text
        var lo = 0
        var hi = cps.size
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (plainWidth(mid) <= maxPx) lo = mid else hi = mid - 1
        }
        if (lo <= 0) return@remember AnnotatedString("")
        // 按码点算出 UTF-16 下标后再切，**同时保留 span 样式**：
        // 直接 AnnotatedString(截断后的字符串) 会把红色 ⚠ 这类 span 丢掉，
        // 用 subSequence 才能带着样式一起裁（AnnotatedString.subSequence 保留区间内的 span）。
        val cutEnd = plain.offsetByCodePoints(0, lo)
        // 11-11 用户令（实拍「度小贤 | 电台男主播 | 资讯 |」）：**截断后末尾是符号的，符号也不要留**。
        // 宽度截断只保证"不超宽"，落点可能正好在分隔符后面（「…资讯 |」），
        // 视觉上像名字被砍了一半、多出一根竖线。这里把结尾的分隔符/空白/未闭合左括号一起回退，
        // 让文字停在上一个完整词上。
        // ⚠️ 只在"确实截断了"的分支里做，未截断时原样返回（名字本身以 · 结尾等是用户数据，不动）。
        val trimmedEnd = trimTrailingSeparators(plain, cutEnd)
        text.subSequence(0, if (trimmedEnd > 0) trimmedEnd else cutEnd)
    }
    Text(
        text = shown,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,   // 已按整字算好，这里只兜底
        modifier = modifier,
    )
}

/**
 * 截断落点若停在分隔符/空白上，把这些尾巴退掉（11-11 用户令）。
 *
 * 只处理**结尾**：`"度小贤｜电台男主播｜资讯｜"` 被切在最后一个 `｜` 之后时，
 * 结果会是「…资讯｜」——用户实拍读起来像"名字被砍半、多一根竖线"。
 * 这里把结尾连续的分隔符/空白退出，退到上一个完整词尾。
 *
 * 保护：退到空串（整串都是符号）时返回原 cutEnd，宁可留符号也不能让整行变空。
 */
private fun trimTrailingSeparators(plain: String, cutEnd: Int): Int {
    var end = cutEnd
    while (end > 0) {
        val c = plain[end - 1]
        val isTail = c.isWhitespace() || c in "|｜·・,，、.。:：;；-—–~～/\\_*+#'\"“”‘’()（）[]【】<>《》"
        if (!isTail) break
        end--
    }
    return if (end > 0) end else cutEnd
}
