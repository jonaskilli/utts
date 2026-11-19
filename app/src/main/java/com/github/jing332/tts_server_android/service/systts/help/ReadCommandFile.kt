package com.github.jing332.tts_server_android.service.systts.help

import android.util.Log
import com.github.jing332.database.entities.systts.SpeechRuleIds
import org.json.JSONObject
import java.io.File

/**
 * UTTS → U阅 的**反向命令通道**（11-20「日志栏喇叭」新增）。
 *
 * ## 为什么需要反向通道
 * 现有跨 App 通道只有一条**单向**的：[ReadProgressReader] 读 U阅 写的
 * `readProgress.json`（U阅 → UTTS：我在读哪本书哪一章哪一段）。
 * 日志栏喇叭要做的「从这段开始读」方向相反——UTTS 知道要读哪段，
 * 但真正能把它读出来的是 U阅 的朗读服务（段表、高亮、跨章续播全在那边）。
 * 于是本对象负责写、U阅 侧（`ReadCommandWatcher`）负责监听并执行。
 *
 * ## 为什么用文件而不是 Binder/AIDL
 * 两个 App 互相不可见（不同签名、无共享 aidl），而 `chajian/<ruleId>/` 是它们
 * 已经共用的数据目录（data.json / readProgress.json / synth_cache 握手文件
 * 都在这一条路上）。文件 + FileObserver 是本机唯一不需要权限、不需要常驻的
 * 跨进程通知方式，与既有实现同构，不引入新面。
 *
 * ## 协议
 * 写 JSON 到 `<ruleId>/readCommand.json`：
 * ```json
 * {"seq": 1732..., "cmd": "playFrom", "book": "...", "chapterTitle": "...",
 *  "chapterIndex": 235, "segIndex": 12, "at": 1732...}
 * ```
 * - `seq`：单调递增的命令序号。消费方靠它去重（FileObserver 可能对同一次写
 *   派发多次事件，同一 seq 只执行一次）；写入方每次 +1。
 * - `cmd`：目前只有 `playFrom`（从该段开始连续朗读）。
 * - `chapterIndex/segIndex`：权威定位（与 readProgress.json 的播放组同口径，
 *   0-based，段号是该章 contentList 的下标）。
 * - `book/chapterTitle`：冗余但必要——消费方要校验「用户还在读这本书吗」，
 *   只靠 index 无法判断是不是换书后残留的旧命令。
 *
 * ## 写入时机与代价
 * 只在用户点喇叭时写一次（不是定时），文件几十字节。失败静默：这是辅助动作，
 * 写不进去最多是「点了没反应」，绝不能影响合成/日志。
 */
object ReadCommandFile {
    private const val TAG = "ReadCommandFile"
    private const val FILE_NAME = "readCommand.json"

    /** 命令类型：从指定段开始连续朗读（可跨章） */
    const val CMD_PLAY_FROM = "playFrom"

    private fun dir(): File = File(SpeechRuleIds.DATA_DIR)

    private fun file(): File = File(dir(), FILE_NAME)

    /**
     * 写一条「从这段开始读」命令。
     *
     * @param book         书名。**必须与 U阅 侧 `ReadBook.book.name` 全等**，
     *                     消费方按它校验当前书，不一致就丢弃（不跳书、不乱读）。
     * @param chapterTitle 章标题。**权威定位靠它**（消费方按书名+章标题查章序）；
     *                     与 U阅 `textChapter.title` 同源——日志行的 `chapterTitle`
     *                     字段由朗读规则透传，两边本来就是同一个字符串。
     * @param chapterIndex 书内章序（0-based）；日志行不带章序，故传 -1 =
     *                     由消费方按 `chapterTitle` 自行解析。
     * @param segIndex     章内段序（0-based，contentList 下标）
     * @param singleParagraph true=只合成/播放这一段就停（对应「重新合成」）；
     *                        false=从这段起连续朗读、可跨章（对应「从这段开始读」）。
     * @param purgeCache      true=先删掉该段在 U阅 侧的 wav 缓存再请求
     *                        （「重新合成」必须为 true，否则会直接播旧音频）。
     * @return 是否写入成功
     */
    fun writePlayFrom(
        book: String,
        chapterTitle: String,
        chapterIndex: Int,
        segIndex: Int,
        singleParagraph: Boolean = false,
        purgeCache: Boolean = false,
    ): Boolean {
        if (book.isBlank() || chapterTitle.isBlank() || segIndex < 0) {
            Log.w(TAG, "writePlayFrom 参数无效: book=$book ch=$chapterTitle seg=$segIndex")
            return false
        }
        return runCatching {
            val d = dir()
            if (!d.exists()) d.mkdirs()
            val json = JSONObject().apply {
                put("seq", System.currentTimeMillis())
                put("cmd", CMD_PLAY_FROM)
                put("book", book)
                put("chapterTitle", chapterTitle)
                put("chapterIndex", chapterIndex)
                put("segIndex", segIndex)
                put("singleParagraph", singleParagraph)
                put("purgeCache", purgeCache)
                put("at", System.currentTimeMillis())
            }
            // tmp + rename：消费方可能正在读，避免读到半截 JSON
            //（与 SynthesizedAudioCache 的写入口径一致）
            val tmp = File(d, "$FILE_NAME.tmp")
            tmp.writeText(json.toString())
            val ok = tmp.renameTo(file())
            if (!ok) {
                // rename 失败（个别 FUSE 场景）：退回直接写
                file().writeText(json.toString())
                tmp.delete()
            }
            Log.d(TAG, "writePlayFrom: $book / $chapterTitle / ch=$chapterIndex seg=$segIndex")
            true
        }.onFailure {
            Log.w(TAG, "writePlayFrom failed: ${it.message}")
        }.getOrDefault(false)
    }
}
