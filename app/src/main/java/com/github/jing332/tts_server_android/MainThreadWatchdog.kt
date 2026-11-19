package com.github.jing332.tts_server_android

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.github.jing332.tts_server_android.constant.AppConst
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 主线程卡死看门狗（2026-11-13，对照参考版 TTS Server 的 MainThreadWatchdog 移植）。
 *
 * 补的洞：已有的 [TtsTimeoutWatchdog] 只覆盖「出现超时字样后无新日志」= 合成超时卡死；
 * 主线程被任意同步调用卡住（例如插件 onDestroy 同步调脚本、DB 主线程慢查询）时它不会响，
 * 表现就是「APP 没反应、日志一片空白」，排障时无栈可看。
 *
 * 机制：主 Looper 每 [TICK_MS] 打一次心跳；守护线程每 [POLL_MS] 检查，
 * 心跳停超过 [STALL_MS]（8s）判定卡死 → 全线程栈写进 [AppConst.logDir]/stall.log，
 * 主日志补一行「主线程卡死 Xms」——日志界面重启后可追溯。
 *
 * ⚠️ 只记录、不杀进程：本 APP 是常驻的系统 TTS 引擎，杀进程会打断正在朗读；
 * 「重启兜底」仍由 TtsTimeoutWatchdog 负责，两套判定互相独立、不叠加。
 * 一次卡死只写一次（心跳恢复后重置），持续卡死不会刷爆磁盘。
 *
 * ⚠️ 2026-11-19 补：**必须把「进程被系统冻结/整机休眠」与「主线程被代码卡住」分开**。
 *
 * 真机 13:22:44 报过 `主线程卡死 200518ms`，但同一份 dump 里主线程栈是
 * `MessageQueue.nativePollOnce`（空闲等消息），而且当时朗读早已 stop、三个日志文件
 * 同时断档 3 分钟——是 **vivo 把进程冻结了**，不是代码阻塞。根因是计时口径：
 * `SystemClock.elapsedRealtime()` 含休眠/冻结时间，而守护线程在主线程被卡时仍在跑、
 * 在进程被冻结时却**一起停摆**，两者表现都是「心跳差很大」，旧实现分不开。
 *
 * 现在两道闸：
 * 1. **守护线程自身漂移**：本轮实际间隔比 [POLL_MS] 多出 [FREEZE_DRIFT_MS] 以上，
 *    说明它自己也被挂起了 ⇒ 整进程/整机停摆，重置心跳基准，**不进日志页**。
 * 2. **栈顶判空闲**：dump 时主线程栈顶是 `MessageQueue.nativePollOnce` ⇒ 主线程在等消息、
 *    根本没被阻塞 ⇒ 同样判冻结，**不进日志页**（真卡死时栈顶必然停在业务帧上）。
 *
 * 两道闸任一命中都只写 stall.log 一行留痕（文件，不是日志页），日志页里只保留真卡死。
 */
object MainThreadWatchdog {
    const val TAG = "MtWatchdog"

    /** 心跳停多久判定主线程卡死 */
    private const val STALL_MS = 8_000L

    /** 主线程心跳间隔 */
    private const val TICK_MS = 500L

    /** 守护线程检查间隔 */
    private const val POLL_MS = 2_000L

    /** 守护线程本轮实际间隔比 [POLL_MS] 多出这么多 ⇒ 它自己也被冻结了 */
    private const val FREEZE_DRIFT_MS = 3_000L

    /** 冻结留痕只写到这个大小上限，防止一天几十次把文件撑大 */
    private const val FREEZE_NOTE_MAX_BYTES = 128 * 1024L

    /** 主线程栈只留前 N 帧；其余线程全量但每线程封顶，防栈文件过大 */
    private const val MAIN_FRAMES = 24
    private const val PER_THREAD_FRAMES = 40

    private val lock = Any()

    @Volatile
    private var lastTick = 0L

    @Volatile
    private var dumped = false

    @Volatile
    private var started = false

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    /** App.onCreate 调用一次（进程内幂等，重复调用直接返回）。 */
    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        lastTick = SystemClock.elapsedRealtime()
        handler.post(object : Runnable {
            override fun run() {
                lastTick = SystemClock.elapsedRealtime()
                handler.postDelayed(this, TICK_MS)
            }
        })
        Thread({
            var lastPollAt = SystemClock.elapsedRealtime()
            while (true) {
                try {
                    Thread.sleep(POLL_MS)
                } catch (_: InterruptedException) {
                    // SAM 转 Runnable 的 lambda 里不许非局部 return，用中断+break 退出
                    Thread.currentThread().interrupt()
                    break
                }
                val now = SystemClock.elapsedRealtime()
                val drift = now - lastPollAt - POLL_MS
                lastPollAt = now

                // 闸 1：守护线程自己也睡过头 ⇒ 整进程/整机停摆，不是主线程被阻塞
                if (drift > FREEZE_DRIFT_MS) {
                    val suspended = now - lastTick
                    lastTick = now
                    dumped = false
                    if (suspended >= STALL_MS) noteFreeze(suspended)
                    continue
                }

                val stalled = now - lastTick
                if (stalled >= STALL_MS) {
                    if (!dumped) {
                        dumped = true
                        // 闸 2：主线程空闲等消息 ⇒ 也不是被代码阻塞
                        if (isMainThreadIdle()) {
                            noteFreeze(stalled)
                        } else {
                            runCatching { dump(stalled) }
                        }
                    }
                } else {
                    // 心跳恢复：允许下一次卡死再落一次栈
                    dumped = false
                }
            }
        }, "mt-watchdog").apply { isDaemon = true }.start()
    }

    /**
     * 主线程是否停在「等消息」上（`MessageQueue.nativePollOnce`）。
     *
     * 真卡死时栈顶必然落在业务帧（`/sdcard` stat、SQLite、锁等待……），不会是这个。
     */
    private fun isMainThreadIdle(): Boolean {
        val top = Looper.getMainLooper().thread.stackTrace.firstOrNull() ?: return false
        return top.className == "android.os.MessageQueue" && top.methodName == "nativePollOnce"
    }

    /**
     * 记录一次「被系统冻结/挂起」（非主线程卡死）。
     *
     * **只写 stall.log，不写 system_tts.log**——日志页不该出现这种非故障项
     * （用户 11-19 令："如果不是出现了故障，这个就不要显示在 tts 的日志里"）。
     * U阅 侧自带的 AppFreezeMonitor 已在阅读日志里记同一件事，不必重复上报。
     */
    private fun noteFreeze(suspendedMs: Long) {
        Log.d(TAG, "进程被系统冻结 ${suspendedMs}ms（非主线程卡死，不记日志页）")
        runCatching {
            val stallFile = File(AppConst.logDir, "stall.log")
            if (stallFile.exists() && stallFile.length() > FREEZE_NOTE_MAX_BYTES) return@runCatching
            val time = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
            synchronized(lock) {
                FileWriter(stallFile, true).use {
                    it.append("=== 进程被系统冻结 ${suspendedMs}ms（非主线程卡死，未计入日志页）===\n")
                        .append("time=").append(time).append('\n')
                }
            }
        }.onFailure { Log.e(TAG, "写冻结留痕失败", it) }
    }

    private fun dump(stalledMs: Long) {
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        val main = Looper.getMainLooper().thread

        val sb = StringBuilder(16_384)
        sb.append("=== 主线程卡死 ").append(stalledMs).append("ms ===\n")
        sb.append("time=").append(time).append('\n')
        sb.append("version=").append(runCatching { AppConst.appInfo.versionName }.getOrDefault("?"))
        sb.append(" sdk=").append(Build.VERSION.SDK_INT).append('\n')
        sb.append("\n--- 主线程 \"").append(main.name).append("\" 前 ")
            .append(MAIN_FRAMES).append(" 帧 ---\n")
        main.stackTrace.take(MAIN_FRAMES).forEachIndexed { i, f ->
            sb.append("  stall#").append(i).append(' ').append(f).append('\n')
        }
        sb.append("\n--- 全线程栈 ---\n")
        Thread.getAllStackTraces().forEach { (t, frames) ->
            if (t == main) return@forEach
            sb.append("thread \"").append(t.name).append("\" state=").append(t.state).append('\n')
            frames.take(PER_THREAD_FRAMES).forEach { sb.append("  ").append(it).append('\n') }
        }

        // 栈写独立文件（不与 logback 正在写的三个文件抢句柄）
        val stallFile = File(AppConst.logDir, "stall.log")
        runCatching { stallFile.writeText(sb.toString()) }

        Log.e(TAG, "主线程卡死 ${stalledMs}ms，栈已写入 ${stallFile.absolutePath}")
        // 主日志补一行：日志界面重启后可追溯（写法与 TtsTimeoutWatchdog.writeRestartLog 一致）
        runCatching {
            val logFile = File(AppConst.logDir, "system_tts.log")
            synchronized(lock) {
                FileWriter(logFile, true).use {
                    it.append(time)
                        .append(" | ERROR | [MtWatchdog] 主线程卡死 ${stalledMs}ms，全线程栈见 stall.log\n")
                }
            }
        }.onFailure { Log.e(TAG, "写主日志失败", it) }
    }
}