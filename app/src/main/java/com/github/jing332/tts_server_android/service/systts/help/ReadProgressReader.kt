package com.github.jing332.tts_server_android.service.systts.help

import android.os.FileObserver
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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
 * 【两条读通道（11-15 拆）】
 *  - [read]     播放活跃态：`playing=false` 或超时一律 null。给「正在朗读」高亮/跟随用，
 *               它们只在真的在念时才该有值。
 *  - [readRaw]  原样快照：不看 `playing`，只按文件新鲜度兜底。给吸顶区进度行用——
 *               用户 11-15 明确「停止朗读时，合成进度行照样显示」，
 *               若沿用 [read] 会把整行连同合成进度一起滤掉。
 *
 * 【性能】mtime/size 缓存，与 [ChapterOrderFile] 同思路；文件只有几十字节，
 * 但日志页 1000ms 轮询一次，缓存仍能省掉绝大多数实际读盘。两条通道共用同一份缓存。
 */
object ReadProgressReader {
    private const val TAG = "ReadProgressReader"
    // 与 U阅 EngineDataDir.baseDir() 同源：全局只有一条朗读规则 mingwuyan
    private const val BASE_DIR = "/storage/emulated/0/Download/chajian/mingwuyan"
    const val FILE_NAME = "readProgress.json"

    /** U阅 写入时的静默阈值：超时视为已停止朗读（防 U阅 被杀来不及写 playing=false） */
    const val STALE_MS = 15_000L

    /**
     * [readRaw] 的宽限期（11-15）：停止朗读后文件不再刷新，但仍要显示合成进度，
     * 所以不能套用 15 秒的播放阈值。给 30 分钟——足够覆盖「停下来看一眼再继续」，
     * 又不至于让 U阅 被杀一整晚后还有一行过期进度挂在吸顶区。
     */
    const val RAW_STALE_MS = 30 * 60 * 1000L

    data class Snapshot(
        val playing: Boolean,
        val book: String,
        val chapterTitle: String = "",
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
        /**
         * 11-22（用户令「跨章备的量得显示」）：从当前播放段往后，U阅 侧已备好的
         * **总段数**（跨章累加）。吸顶进度条「浅绿段 + 缓冲 N 段」的数据源。
         *
         * -1 = U阅 未上报该字段（旧版本 App）⇒ 渲染侧退回旧画法（只画本章已备比例，
         * 不显示缓冲段数），不显示假数据。
         *
         * 为什么不自己算：UTTS 只知道「合成前沿在第几章第几段」，算不出往后备了多少；
         * 而「还能连着听多少」正是这一行要回答的核心问题。U阅 合成循环天然有全部信息。
         */
        val bufferedAheadSegs: Int = -1,
    )

    private data class Cache(val mtime: Long, val size: Long, val snap: Snapshot)

    // 11-16 晚：跨线程可见（FileObserver 回调线程会写 null，读方在 IO 线程）
    @Volatile
    private var cached: Cache? = null

    // ===== 11-16 晚：文件变更事件（修「正在朗读高亮迟 1~2s」）=====
    // 时延构成：U阅 段切换 → 200ms 字幕循环写 readProgress.json → 本页（UTTS 日志页）
    // 1s 轮询读。轮询是第二级、也是最大头（0~1s，实测感知「迟个一两秒」）。
    // 现在盯文件变更：U阅 一写立即 tryEmit，消费方（TtsLogScreen 两个 produceState）
    // 收到事件马上重读，时延压到 ~0.2-0.4s；1s 轮询降级为兜底——个别机型
    // FUSE 可能不派 inotify 事件，兜底保证功能不失效。
    // ⚠️ 盯**目录**而不是文件：U阅 平时是读改写（writeText 同一路径截断重写），
    // 但 stopSubtitleSync 的 clear() 会**删文件**，下次朗读再新建——盯文件在
    // inode 更换后会静默失效；盯目录按文件名过滤，删/建/写三种都收得到。
    val changes: SharedFlow<Unit> get() = _changes
    private val _changes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val observer: FileObserver by lazy {
        object : FileObserver(File(BASE_DIR), CLOSE_WRITE or DELETE or MOVED_TO or CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == FILE_NAME) {
                    cached = null // 事件线程清缓存：下次读必落盘，不吃旧 mtime
                    _changes.tryEmit(Unit)
                }
            }
        }
    }

    @Volatile
    private var watching = false
    private val watcherLock = Any()

    /** 首次读之前把观察器拉起来（read/readRaw 都会经过 rawSnapshot）。 */
    private fun ensureWatching() {
        if (watching) return
        synchronized(watcherLock) {
            if (watching) return
            observer.startWatching()
            watching = true
        }
    }

    /** 播放活跃态：读不到 / playing=false / 超时都返回 null。给「正在朗读」高亮与跟随用。 */
    fun read(): Snapshot? = rawSnapshot()?.takeIf {
        it.playing && System.currentTimeMillis() - it.atMs <= STALE_MS
    }

    /**
     * 原样快照（11-15 新增）：**不看 playing**，只按 [RAW_STALE_MS] 判断新鲜度。
     * 给吸顶区进度行用——停止朗读后仍要显示「合成进度：…」，同时朗读段由调用方
     * 按 `snap.playing` 决定是否画。
     */
    fun readRaw(): Snapshot? = rawSnapshot()?.takeIf {
        System.currentTimeMillis() - it.atMs <= RAW_STALE_MS
    }

    /** 纯解析 + mtime/size 缓存，不做任何语义过滤。 */
    private fun rawSnapshot(): Snapshot? {
        ensureWatching()
        val f = dataFile()
        if (!f.exists()) return null
        val mtime = f.lastModified()
        val size = f.length()
        cached?.let { c -> if (c.mtime == mtime && c.size == size) return c.snap }
        return try {
            val obj = JSONObject(f.readText())
            val snap = Snapshot(
                playing = obj.optBoolean("playing", false),
                book = obj.optString("book"),
                chapterTitle = obj.optString("chapterTitle"),
                chapterIndex = obj.optInt("chapterIndex", -1),
                segIndex = obj.optInt("segIndex", -1),
                totalSegs = obj.optInt("totalSegs", 0),
                segText = obj.optString("segText"),
                atMs = obj.optLong("at", 0L),
                synthChapterIndex = obj.optInt("synthChapterIndex", -1),
                synthSegIndex = obj.optInt("synthSegIndex", -1),
                synthChapterTitle = obj.optString("synthChapterTitle"),
                synthTotalSegs = obj.optInt("synthTotalSegs", 0),
                bufferedAheadSegs = obj.optInt("bufferedAheadSegs", -1),
            )
            cached = Cache(mtime, size, snap)
            snap
        } catch (e: Exception) {
            Log.w(TAG, "readProgress failed: ${e.message}")
            null
        }
    }

    private fun dataFile(): File = File(BASE_DIR, FILE_NAME)
}
