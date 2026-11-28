package com.github.jing332.tts_server_android.service.systts.help

import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 章节顺序解析（11-11 用户令「章节顺序按实际来，又要方便又要快」）。
 *
 * 【为什么需要它】日志里的章序原本靠「首次出现在日志流里的顺序」（TtsLogViewModel.chaptersOf），
 * 顺序朗读时正确，但一旦跳章（先听第 15 章、再回第 3 章），列表就变成 15、3、4…
 * 与书里真实章序完全不符。
 *
 * 【权威数据源】`chajian/<ruleId>/data.json` 的 `texts` 全文——朗读规则自己就用它判断章节
 * （locateParagraphInFullText 按「行首无缩进」切章），是书内真实顺序。章名直接取原文，
 * 天然支持用户书里的不规则写法：
 *   · 「第 14 章」（第与数字间有空格）
 *   · 「第二百六十二章」（中文数字）
 *   · 「第1章(5)」「第1章(8)」（同号重复）
 *   · 「第三卷」（卷行，不属于章序列）
 * 不解析数字、不重排，照原文顺序出列即可——这正是不规则章号唯一不会错的做法。
 *
 * 【性能】只在切书或 data.json 改动时读一次盘；同一 (ruleId, mtime, size) 命中缓存直接返回。
 * 读全文可能几 MB，但只发生在首次进日志页/切书，且解析是单次正则扫描（不是逐行 indexOf）。
 */
object ChapterOrderFile {
    private const val TAG = "ChapterOrderFile"
    private const val BASE_DIR = "/storage/emulated/0/Download/chajian"

    /** 缓存项：data.json 的 (mtime, size) 变化即失效 */
    private class Entry(
        val mtime: Long,
        val size: Long,
        val bookName: String,
        val chapters: List<String>,
    )

    @Volatile
    private var cached: Entry? = null

    private fun dataFile(ruleId: String) = File(File(BASE_DIR, ruleId), "data.json")

    /**
     * 按书内真实顺序返回章标题。
     *
     * @param ruleId 朗读规则的 ruleId（chajian 下的目录名）
     * @return 章标题列表；文件缺失/解析失败返回空列表（调用方退回按出现顺序）
     */
    fun readChapterOrder(ruleId: String): List<String> {
        if (ruleId.isBlank()) return emptyList()
        val f = dataFile(ruleId)
        if (!f.exists()) return emptyList()

        val mtime = f.lastModified()
        val size = f.length()
        cached?.let { c ->
            if (c.mtime == mtime && c.size == size) return c.chapters
        }

        return try {
            // ⚠️ 只读一次盘：数据文件可达数 MB，读两遍等于白翻一倍
            val obj = JSONObject(f.readText())
            val texts = obj.optString("texts")
            val book = obj.optString("bookName")
            val chapters = splitChapters(texts)
            cached = Entry(mtime, size, book, chapters)
            chapters
        } catch (e: Exception) {
            Log.w(TAG, "readChapterOrder failed: ${e.message}")
            emptyList()
        }
    }

    /** 当前书的名字（data.json 的 bookName），读不到返回空串 */
    fun readBookName(ruleId: String): String {
        cached?.let { return it.bookName }
        readChapterOrder(ruleId)
        return cached?.bookName.orEmpty()
    }

    /**
     * 按朗读规则同款口径切章：**行首无缩进**的行是章标题，带缩进（全角空格/半角空格）的是正文。
     * 与规则 locateParagraphInFullText 的判据一字不差，保证两边认出的章序一致。
     */
    private fun splitChapters(texts: String): List<String> {
        if (texts.isBlank()) return emptyList()
        val out = ArrayList<String>(64)
        // 逐行扫，但只在「上一行状态翻转」时收章，不做字符串拼接（几 MB 全文也快）
        texts.lineSequence().forEach { raw ->
            if (raw.isBlank()) return@forEach
            val startsWithIndent = raw[0] == '\u3000' || raw[0] == ' '
            if (!startsWithIndent) {
                val title = raw.trim()
                // 跳过明显的正文残留（含引号且较长的不太可能是章名，但没有绝对判据；
                // 规则本身也不做长度过滤，这里保持一致，避免"我这本正常、那本丢章"）
                if (title.isNotEmpty()) out.add(title)
            }
        }
        return out
    }

    /**
     * 把「日志里出现过的章」按书内真实顺序排列。
     *
     * ===== 11-28（用户实报「章节下拉顺序不对：28、29、22、23…」）：部分覆盖时按章号归位 =====
     *
     * ## 现场
     * data.json 的 texts **只有第 28–32 章**这 5 个章标题（用户只往规则里贴了这一段），
     * 而日志里有第 22–29 章。旧逻辑「order 里没有的保序追加到末尾」于是得到
     * `28、29、22、23、24、25、26、27` —— 用户一眼看出不对。
     *
     * ## 修法
     * order 只覆盖一部分时，**未覆盖的章不能盲追末尾**：按从章名里解析出的数字
     * 插到它在 order 中的自然位置（比 order 首章小 ⇒ 排在它前面）。
     * 这样 `order=[28..32]` + `seen=[22..29]` ⇒ `22,23,24,25,26,27,28,29`，正确。
     *
     * ## 兜底边界
     *   · 章名解析不出数字（「第三卷」「番外」等）⇒ 退回旧行为（追加末尾）；
     *   · order 本身解析不出数字 ⇒ 整份退回旧行为（无法判断自然序，别乱插）；
     *   · **完全覆盖**（order 含全部 seen）⇒ 走原路径，逐字不变。
     * 也就是说：新逻辑只在「order 不完整」这种本来就出错的情况下生效。
     */
    fun sortByBookOrder(order: List<String>, seen: List<String>): List<String> {
        if (order.isEmpty()) return seen
        val seenSet = seen.toHashSet()
        val placed = ArrayList<String>(seen.size)
        order.forEach { if (it in seenSet) placed.add(it) }
        val placedSet = placed.toHashSet()
        val unknown = seen.filter { it !in placedSet }
        // 完全覆盖（order 含全部 seen）⇒ 逐字保持原行为，一个都不动
        if (unknown.isEmpty()) return placed

        // 部分覆盖 ⇒ 未覆盖的章按章号归位到自然位置。
        //
        // 前置条件（任一不满足就退回「保序追加末尾」的旧行为，不乱插）：
        //   · order 的章号全部解析得出，且**严格递增**（说明这份 order 就是按章号排的，
        //     用它做锚点排序不会改变它自己的相对序）；
        //   · 每个待插入的章号也解析得出。
        // 这样做的好处：order 内部顺序永远不被改写，新增章只是"插进空档"。
        val orderNums = order.map { chapterNumber(it) }
        val orderAscending = orderNums.all { it != null } &&
            orderNums.filterNotNull().zipWithNext().all { (a, b) -> a < b }
        val unknownNums = unknown.map { chapterNumber(it) }
        if (!orderAscending || unknownNums.any { it == null }) {
            placed.addAll(unknown)   // 旧行为：保序追末尾（章名无法比较时不猜）
            return placed
        }
        // 归位：已排好的章与未覆盖的章都拿得到章号 ⇒ 按章号合并排序。
        // ⚠️ 章号必须跟着「章名」走，不能用 placed 的下标去索引 orderNums——
        // placed 已经滤掉了 order 里 seen 没有的项，两者下标不对齐。
        val merged = ArrayList<Pair<String, Int>>(placed.size + unknown.size)
        order.forEachIndexed { idx, name ->
            if (name in seenSet) merged.add(name to orderNums[idx]!!)
        }
        unknown.forEachIndexed { i, name -> merged.add(name to unknownNums[i]!!) }
        // sortedBy 是稳定排序：章号相同时保持上面的相对顺序（已排章在前，与旧行为一致）
        return merged.sortedBy { it.second }.map { it.first }
    }

    /**
     * 从章名里取出章号，用于「部分覆盖时归位」。
     *
     * 支持本书实际写法：`第28章`、`第 28 章`、`第二百六十二章`、`第1章(5)`。
     * 取第一个数字；中文数字转阿拉伯（读到万位足够——网文没有更大的章号）。
     * 解析不出返回 null（「第三卷」「番外」「作者的话」这类，不参与自然序）。
     *
     * ⚠️ 只用于**排序**，绝不用于显示或去重——章名原文始终是唯一权威（见文件头注释）。
     */
    private fun chapterNumber(title: String): Int? {
        val t = title.trim()
        if (t.isEmpty()) return null
        // 1) 阿拉伯数字优先：第28章 / 第 28 章 / 第1章(5)（括号里的第二个数不取）
        Regex("\\d+").find(t)?.let { m -> return m.value.toIntOrNull() }
        // 2) 中文数字：第X章 / 第X节 / 第X卷里的 X
        val m = Regex("第([零一二三四五六七八九十百千两]+)[章节回卷]").find(t) ?: return null
        return chineseToInt(m.groupValues[1])
    }

    /** 中文数字转整数（支持 1~99999：十二/二十/一百零五/两千三百 这类） */
    private fun chineseToInt(s: String): Int? {
        if (s.isEmpty()) return null
        val digit = mapOf(
            '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        val unit = mapOf('十' to 10, '百' to 100, '千' to 1000)
        var total = 0
        var cur = 0
        for (ch in s) {
            when {
                ch in digit -> cur = digit.getValue(ch)
                ch == '万' -> { total = (total + cur) * 10000; cur = 0 }
                ch in unit -> {
                    val u = unit.getValue(ch)
                    // 「十二」= 十位隐含 1；「一百零五」里零后面直接跟个位
                    total += (if (cur == 0) 1 else cur) * u
                    cur = 0
                }
                else -> return null
            }
        }
        return total + cur
    }
}
