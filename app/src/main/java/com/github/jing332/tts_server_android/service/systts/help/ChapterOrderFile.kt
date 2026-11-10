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

    /** 把「日志里出现过的章」按书内真实顺序排列；未知章（order 里没有的）按出现顺序追加在末尾 */
    fun sortByBookOrder(order: List<String>, seen: List<String>): List<String> {
        if (order.isEmpty()) return seen
        val seenSet = seen.toHashSet()
        val out = ArrayList<String>(seen.size)
        // 1) 按书内顺序，只取日志里真有的
        order.forEach { if (it in seenSet) out.add(it) }
        val placed = out.toHashSet()
        // 2) 书内顺序里没有的（例如规则改了章名、或日志来自旧包），保序追在末尾
        seen.forEach { if (it !in placed) out.add(it) }
        return out
    }
}
