package com.github.jing332.tts_server_android

import android.util.Log
import com.github.jing332.tts_server_android.constant.AppConst
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 崩溃日志增量转发（2026-11-13，对照参考版 TTS Server 的 CrashForwarder 移植）。
 *
 * 背景：[CrashCapture] 把上次闪退写进私有 filesDir/crash_last.txt，靠「下次进主页弹窗」才能看到；
 * 如果用户没进主页、弹窗被跳过，或者想直接从文件管理器取，就抓不到。
 * 这里在每次启动（App.onCreate）时把 crash_last.txt 的内容追加一份到
 * [AppConst.logDir]/crash_forward.log（Download/chajian/log 下，文件管理器直接可见）。
 *
 * 幂等：用 SHA-256 记下已转发的内容，内容没变就不重复追加——
 * crash_last.txt 要等弹窗读取后才清，期间多次冷启动不会产生重复记录。
 * 只读不删 crash_last.txt：原有弹窗逻辑完全不变。
 */
object CrashForwarder {
    private const val TAG = "CrashForwarder"

    /** 已转发内容指纹（存在 logDir 下，进程重启后仍有效） */
    private const val MARKER = "crash_forward.sha"

    /** App.onCreate 调用一次；任何失败都静默——排障设施绝不能影响启动。 */
    fun forward() {
        runCatching {
            val src = File(App.instance.filesDir, "crash_last.txt")
            if (!src.exists() || src.length() == 0L) return
            val content = src.readText()
            if (content.isBlank()) return

            val digest = sha256(content)
            val marker = File(AppConst.logDir, MARKER)
            // 已转发过同样内容（弹窗清文件前的重复冷启动）→ 不重复追加
            if (runCatching { marker.readText() }.getOrNull() == digest) return

            val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val out = File(AppConst.logDir, "crash_forward.log")
            synchronized(this) {
                out.appendText("———— $time 崩溃（转发自 crash_last.txt）————\n$content\n")
                marker.writeText(digest)
            }
            Log.e(TAG, "崩溃记录已转发到 ${out.absolutePath}")
        }.onFailure { Log.e(TAG, "forward failed", it) }
    }

    private fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") {
            "%02x".format(it.toInt() and 0xFF)
        }
    }
}