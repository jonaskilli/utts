package com.github.jing332.tts_server_android.service.systts.help

import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 朗读进度读取（11-14 新增）：U阅 侧 `ReadProgressFile` 写、本对象读。
 *
 * 【用途】UTTS 日志页需要两件 U阅 才知道的事：
 *  1. **正在朗读哪段**——日志按请求顺序排，而请求是预取提前发的，
 *     「最新一条」≠「正在念的那句」。U阅 在段落切换时把 (chapterIndex, segIndex)
 *     写进来，日志页据此把对应卡片定位出来。
 *  2. **本章总段数**——合成进度分母口径「从当前播放段到章末」需要它做起点。
 *
 * 【与合成位置的关系】预取可能跑到后一两章：播放停在第五章时合成可能在第七章。
 * 文件里只有播放组字段；合成位置 UTTS 自己知道（在合什么请求它自己发的），不在此文件。
 *
 * 【性能】mtime/size 缓存，与 [ChapterOrderFile] 同思路；文件只有几十字节，
 * 但日志页 500ms 轮询一次，缓存仍能省掉绝大多数实际读盘。
 */
object ReadProgressReader {
    private const val TAG = "ReadProgressReader"
    // 与 U阅 EngineDataDir.baseDir() 同源：全局只有一条朗读规则 mingwuyan
    private const val BASE_DIR = "/storage/emulated/0/Download/chajian/mingwuyan"
    const val FILE_NAME = "readProgress.json"

    /** U阅 写入时的静默阈值：超时视为已停止朗读（防 U阅 被杀来不及写 playing=false） */
    const val STALE_MS = 15_000L

    data class Snapshot(
        val playing: Boolean,
        val book: String,
        val chapterIndex: Int,
        val segIndex: Int,
        val totalSegs: Int,
        val segText: String = "",
        val atMs: Long,
        // 合成前沿（U阅 合成循环每笔提交时刷新；预取跨章时与播放章不同）
        val synthChapterIndex: Int = -1,
        val synthSegIndex: Int = -1,
        val synthChapterTitle: String = "",
        val synthTotalSegs: Int = 0,
    )

    private data class Cache(val mtime: Long, val size: Long, val snap: Snapshot)

    @Volatile
    private var cached: Cache? = null

    /** 读不到/已过期时返回 null（playing=false 不算 null，界面要区分「停止」和「读不到」） */
    fun read(): Snapshot? {
        val f = dataFile()
        if (!f.exists()) return null
        val mtime = f.lastModified()
        val size = f.length()
        cached?.let { c -> if (c.mtime == mtime && c.size == size) return validOrNull(c.snap) }
        return try {
            val obj = JSONObject(f.readText())
            val snap = Snapshot(
                playing = obj.optBoolean("playing", false),
                book = obj.optString("book"),
                chapterIndex = obj.optInt("chapterIndex", -1),
                segIndex = obj.optInt("segIndex", -1),
                totalSegs = obj.optInt("totalSegs", 0),
                segText = obj.optString("segText"),
                atMs = obj.optLong("at", 0L),
                synthChapterIndex = obj.optInt("synthChapterIndex", -1),
                synthSegIndex = obj.optInt("synthSegIndex", -1),
                synthChapterTitle = obj.optString("synthChapterTitle"),
                synthTotalSegs = obj.optInt("synthTotalSegs", 0),
            )
            cached = Cache(mtime, size, snap)
            validOrNull(snap)
        } catch (e: Exception) {
            Log.w(TAG, "readProgress failed: ${e.message}")
            null
        }
    }

    private fun validOrNull(s: Snapshot): Snapshot? =
        if (s.playing && System.currentTimeMillis() - s.atMs <= STALE_MS) s else null

    private fun dataFile(): File = File(BASE_DIR, FILE_NAME)
}
