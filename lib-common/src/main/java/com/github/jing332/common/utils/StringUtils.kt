package com.github.jing332.common.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.ln
import kotlin.math.pow

object StringUtils {
    const val WARNING_EMOJI = """⚠️"""

    private val silentPattern by lazy { Pattern.compile("[\\s\\p{C}\\p{P}\\p{Z}\\p{S}]") }
    private val splitSentencesRegex by lazy { Pattern.compile("[。？?！!;；]") }

    fun formattedDate(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    /**
     *  是否为不发音的字符串
     */
    fun isSilent(s: String): Boolean {
        return silentPattern.matcher(s).replaceAll("").isEmpty()
    }

    /**
     * 分割长句并保留分隔符
     */
    fun splitSentences(s: String): List<String> {
        val m = splitSentencesRegex.matcher(s)
        val list = splitSentencesRegex.split(s)
        //保留分隔符
        if (list.isNotEmpty()) {
            var count = 0
            while (count < list.size) {
                if (m.find()) {
                    list[count] += m.group()
                }
                count++
            }
        }

        return list.filter { it.replace("”", "").isNotBlank() }
    }

    /**
     * 限制字符串长度
     */
    fun String.limitLength(maxLength: Int = 20, suffix: String = ""): String {
        return if (length >= maxLength)
            substring(0, maxLength) + suffix
        else this
    }

}

/**
 * json字符串头尾加[ ]
 */
fun String.toJsonListString(): String {
    var s = this.trim().removeSuffix(",")
    if (!s.startsWith("[")) s = "[$s"
    if (!s.endsWith("]")) s += "]"
    return s
}


/**
 * 字符串中汉字数量
 */
fun String.lengthOfChinese(): Int {
    var count = 0
    val c: CharArray = toCharArray()
    for (i in c.indices) {
        val len = Integer.toBinaryString(c[i].code)
        if (len.length > 8) count++
    }
    return count
}

/**
 * 转为html粗体标签
 */
fun String.toHtmlBold(): String {
    return "<b>$this</b>"
}

fun String.toHtmlItalic(): String {
    return "<i>$this</i>"
}

fun String.toHtmlSmall(): String {
    return "<small>$this</small>"
}

fun String.appendHtmlBr(count: Int = 1): String {
    val strBuilder = StringBuilder(this)
    for (i in (1..count)) {
        strBuilder.append("<br>")
    }
    return strBuilder.toString()
}

fun String.toNumberInt(): Int {
    return this.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
}

fun String.bytesToReadable(bytes: Long): String {
    val kb = 1024
    val mb = kb * 1024
    val gb = mb * 1024

    return when {
        bytes < mb -> "${"%.2f".format(bytes.toDouble() / kb)} KB"
        bytes < gb -> "${"%.2f".format(bytes.toDouble() / mb)} MB"
        else -> "$bytes B"
    }
}

/**
 * @return first char upper case
 */
fun String.firstCharUpperCase(): String {
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

/**
 *  Input 3166 alpha-2 [Locale.getCountry]
 */
fun String.toCountryFlagEmoji(): String {
    if (this.length != 2) {
        return this
    }
    if (!this[0].isLetter() || !this[1].isLetter()) {
        return this
    }
    val firstLetter = Character.codePointAt(this.uppercase(), 0) - 0x41 + 0x1F1E6
    val secondLetter = Character.codePointAt(this.uppercase(), 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
}

fun String.limitLength(max: Int): String {
    return if (this.length > max) this.substring(0, max) else this
}

/**
 * 显示名限长的统一截断（10-06 用户拍板默认 15 字，各处共享）：
 * 超长按**码点**取前 [max] 个（emoji/代理对不腰斩），再清尾——
 * ① 段回退：名字是 `主名 | 属性|属性` 结构时，截断落在最后一个竖线段中间
 *    会留半截尾巴（如「MINI_M」），回退到上一个段界；回退后剩余不足 6 字
 *    （信息太少）或正好切在段界（无残段）则不回退。
 *    半角 | 与全角｜都认——日志侧显示前会把半角转全角，其余处保持半角；
 * ② 剪掉悬空竖线/空格与切剩的半个代理对。
 * **不补省略号**（10-06 用户令：… 占一字宽，去掉可多显示一个字；截断落在
 * 段界上观感即"完整的短名"）。[suffix] 保留给确需提示截断的调用方。
 * 与旧 [limitLength] 的差异：旧版按 UTF-16 char 切、不清尾。
 */
fun String.limitDisplayLength(max: Int, suffix: String = ""): String {
    if (length <= max) return this
    // 码点数守卫：emoji 占比高的字符串 UTF-16 长度会超 max、码点数却不足，
    // 直接 offsetByCodePoints(0, max) 越界崩溃（JS 原型测试没暴露这坑）
    if (codePointCount(0, length) <= max) return this
    val cut = offsetByCodePoints(0, max)   // 按码点切，emoji 不腰斩
    var head = substring(0, cut)
    val lastBar = maxOf(head.lastIndexOf('|'), head.lastIndexOf('｜'))
    if (lastBar > 0 && lastBar < head.length - 1) {
        val kept = head.substring(0, lastBar)
        if (kept.length >= 6) head = kept
    }
    head = head.trimEnd(' ', '|', '｜')
    while (head.isNotEmpty() && Character.isHighSurrogate(head.last())) {
        head = head.dropLast(1)
    }
    if (head.isEmpty()) head = substring(0, cut)
    return head + suffix
}

/**
 * 插件名显示用的截断（11-10 二次定稿：改为**遇符号即切**）。
 *
 * ## 规则（用户 11-10「我之前说要前缀，括号或者其他符号后面跟的内容就直接留前面的」）
 *
 * 1. **先按符号切**：遇到括号/下划线/竖线/点/冒号/顿号等符号，只保留它**前面**的部分。
 *    · 「剪映_v12」   → 「剪映」
 *    · 「讯飞（Pro）」 → 「讯飞」
 *    · 「本悦读_v」    → 「本悦读」
 * 2. **连字符 `-` 不切**：插件名里「豆包-猫箱」「萧文-超拟人」是真实名，`-` 连接两个有效词，
 *    按符号切会丢信息。这也是本函数唯一放行的连接符。
 * 3. 切出来的前缀为空（名字以符号开头）→ 原样返回，不切。
 * 4. 最后仍按**显示宽度预算**兜底截断（默认 12 = 中文 6 字 / 英文 12 字母），
 *    保证超长前缀不会把行撑爆。预算从 8 放宽到 12（11-10）：切完符号后名字已经
 *    在 6 字量级，「豆包-猫箱」「萧文-超拟人」这类真实插件名在 8 宽下会被拦腰砍断，
 *    而 12 宽下恰好完整，且仍比改动前的 12 字+省略号更收敛。
 *
 * ## 为什么不按补全语义保留一个字
 *
 * 曾经考虑过「符号后面是中文就留一个字」（如「剪映（需填变量）」→「剪映需」），
 * 但插件名本身符号很少、带中文尾巴的极罕见，收益远小于「多一个字就多占一格」的成本。
 * 用户 11-10 定稿：直接留前面的。
 *
 * 不补省略号（插件牌是标签不是句子，10-06 口径）。
 */
fun String.limitByDisplayWidth(budget: Int = 12): String {
    if (isEmpty()) return this
    // 切点符号：成对括号（开闭都算）、竖线、下划线、点、冒号、顿号、波浪号、各种空格。
    // 刻意**不含** '-'：插件名里的连字符连接两个有效词，切了会丢信息。
    val cutChars = "（(【[《<「{）)】]》>」}｜|_·．.。：:;；~～、，, \u3000"
    val cutIndex = indexOfFirst { it in cutChars }
    val head = if (cutIndex > 0) substring(0, cutIndex).trim() else this.trim()
    val base = head.ifEmpty { this }

    fun widthOf(c: Char): Int {
        val b = Character.UnicodeBlock.of(c)
        val wide = b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                b == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
                b == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS ||
                b == Character.UnicodeBlock.HIRAGANA ||
                b == Character.UnicodeBlock.KATAKANA ||
                b == Character.UnicodeBlock.HANGUL_SYLLABLES
        return if (wide) 2 else 1
    }
    var acc = 0
    val sb = StringBuilder()
    for (c in base) {
        val w = widthOf(c)
        if (acc + w > budget) break
        sb.append(c); acc += w
    }
    // 宽度截断后可能留下末尾悬空符号（如「剪映_」），再清一次
    var out = sb.toString()
    while (out.isNotEmpty() && out.last() in "_-·|｜.。,,、~～ 　:：;；") {
        out = out.dropLast(1)
    }
    return out.ifEmpty { base }
}

fun String.fromCookie(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val cookies = this.split(";")

    for (cookie in cookies) {
        val parts = cookie.split("=")
        if (parts.size == 2) {
            map[parts[0].trim()] = parts[1].trim()
        }
        if (parts.size == 1) {
            map[parts[0].trim()] = ""
        }
    }

    return map
}


fun Int.sizeToReadable(locale: Locale = Locale.getDefault()): String =
    this.toLong().sizeToReadable(locale)

fun Long.sizeToReadable(locale: Locale = Locale.getDefault()): String {
    val bytes = this
    val unit = 1024
    if (bytes < unit) return "$bytes B"
    val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
    // K/M 不带 i（10-10 用户令：KiB 生僻，通用写法 KB/MB）；换算仍按 1024
    val pre = "KMGTPE"[exp - 1]
    return String.format(locale, "%.1f %sB", bytes / unit.toDouble().pow(exp.toDouble()), pre)
}