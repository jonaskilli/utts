package com.github.jing332.script.simple.ext

import com.drake.net.Net
import com.drake.net.exception.ConvertException
import com.github.jing332.script.annotation.ScriptInterface
import com.github.jing332.script.runtime.NetClients
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

open class JsNet(private val engineId: String) {
    private val groupId by lazy { engineId + hashCode() }

    /**
     * 最近一次 [httpPostRace] 的统计（JSON 文本），供规则在拿到结果后立刻读一次打日志。
     * 形如 {"winner":"A","aMs":7200,"bMs":6400,"tie":false,"bSent":true,"timedOut":false}。
     * 为什么不把这些挂在返回的 Response 上：WrapFactory 会把 Response 统一包成
     * NativeResponse，加不了自定义字段；单独一个只读口最省事，也不影响返回类型。
     * 竞速线程写、JS 线程读，故 @Volatile。
     */
    @Volatile
    private var lastRaceStats: String = ""

    /** 读最近一次加赛统计（没跑过加赛返回空串）。 */
    @ScriptInterface
    fun getRaceStats(): String = lastRaceStats

    fun cancelNetwork() {
        Net.cancelGroup(groupId)
    }

    // timeoutMs 参数为兼容 JRead 插件生态的调用习惯（如 httpGet(url, headers, timeout)）。
    // 11-17（竞速/慢钥改造）：**timeoutMs > 0 时真的生效**——按毫秒克隆一份 client 只作用于
    // 本次请求（connect/read/write/call 四档同值，见 NetClients）。传 0/不传 = 走全局 300s，
    // 与改造前逐字节一致。此前这个参数被完全忽略，规则里想切钥根本切不动（只设 header
    // 'Timeout'，宿主不认），慢钥一贴就是 24~80s，直到 U阅 30s 首回调看门狗清实例重来。
    @JvmOverloads
    @ScriptInterface
    fun httpGet(
        url: CharSequence,
        headers: Map<CharSequence, CharSequence>? = null,
        timeoutMs: Long = 0L
    ): Response {
        return Net.get(url.toString()) {
            setGroup(groupId)
            headers?.let {
                it.forEach {
                    setHeader(it.key.toString(), it.value.toString())
                }
            }
            if (timeoutMs > 0L) okHttpClient = NetClients.withTimeout(timeoutMs)
        }.execute()
    }

    /**
     * HTTP GET
     */
    @JvmOverloads
    @ScriptInterface
    fun httpGetString(
        url: CharSequence, headers: Map<CharSequence, CharSequence>? = null
    ): String? {
        return try {
            Net.get(url.toString()) {
                setGroup(groupId)
                headers?.let {
                    it.forEach {
                        setHeader(it.key.toString(), it.value.toString())
                    }
                }
            }.execute<String>()
        } catch (e: ConvertException) {
            throw Exception("Body is not a String, HTTP-${e.response.code}=${e.response.message}")
        }
    }

    @JvmOverloads
    @ScriptInterface
    fun httpGetBytes(
        url: CharSequence, headers: Map<CharSequence, CharSequence>? = null
    ): ByteArray? {
        return try {
            httpGet(url, headers).body?.bytes()
        } catch (e: ConvertException) {
            throw Exception("Body is not a Bytes, HTTP-${e.response.code}=${e.response.message}")
        }
    }

    /**
     * HTTP POST
     */
    /**
     * 发一笔 POST（单发路径的实现，[httpPost] 与 [httpPostRace] 共用）。
     * [group] 显式传入而不是用字段 [groupId]——加赛时两笔要各自成组，
     * 输掉那笔可以被单独掐掉，不能把赢家一起取消。
     */
    private fun execPost(
        url: String,
        body: CharSequence?,
        headers: Map<CharSequence, CharSequence>?,
        timeoutMs: Long,
        group: String,
    ): Response {
        return Net.post(url) {
            setGroup(group)
            body?.let { this.body = it.toString().toRequestBody() }
            headers?.let {
                it.forEach {
                    setHeader(it.key.toString(), it.value.toString())
                }
            }
            if (timeoutMs > 0L) okHttpClient = NetClients.withTimeout(timeoutMs)
        }.execute()
    }

    // timeoutMs 参数为兼容 JRead 插件生态的调用习惯；11-17 起 > 0 时真实生效（见类头注释）
    @JvmOverloads
    @ScriptInterface
    fun httpPost(
        url: CharSequence,
        body: CharSequence? = null,
        headers: Map<CharSequence, CharSequence>? = null,
        timeoutMs: Long = 0L
    ): Response = execPost(url.toString(), body, headers, timeoutMs, groupId)

    /**
     * **加赛**：同一份提问，两把钥同时发，谁先出结果用谁，输的那笔立刻掐掉。
     *
     * 为什么不是「6s 超时换钥」：用户自己的好钥常有十几秒才返回，6s 硬切会把好结果扔掉。
     * 加赛的第一把**不会被杀**——它慢就慢，只是过了 [raceDelayMs] 还没回时再发一把，
     * 两把谁先成功谁赢；两把都超了 [timeoutMs] 才放弃（返回 null，调用方走兜底）。
     *
     * 平手口径（用户令「同一秒内回来得有取舍」）：按**完成时刻**比较，不看秒；
     * 两把差 < [RACE_TIE_MS] 视为平手 → **固定取先发起的 A**，不掷硬币，日志可复现。
     *
     * 竞速跑在共享线程池里（4 条，见线程池注释），JS 线程只在 [timeoutMs] 上限内阻塞等结果；
     * 输掉那条分支的 Net group 由宿主自己 `Net.cancelGroup` 掐掉，JS 不用管。
     *
     * @return 胜出的 Response（RhinoWrapFactory 包成 NativeResponse）；
     *         **两把都没成（超时）返回 null**，不抛异常——调用方用 null 判「两把都慢」；
     *         两把都是真错误（非超时）则抛出最后一条，走既有失败分支。
     */
    @JvmOverloads
    @ScriptInterface
    fun httpPostRace(
        aUrl: CharSequence, aBody: CharSequence?, aHeaders: Map<CharSequence, CharSequence>?,
        bUrl: CharSequence, bBody: CharSequence?, bHeaders: Map<CharSequence, CharSequence>?,
        raceDelayMs: Long = 6000L,
        timeoutMs: Long = 20000L,
    ): Response? {
        val totalMs = timeoutMs.coerceAtLeast(1L)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(totalMs)
        // 两笔各自成组：输家整组掐掉，不会误伤赢家
        val aGroup = groupId + "#raceA$deadline"
        val bGroup = groupId + "#raceB$deadline"
        val completion = ExecutorCompletionService<RaceResult>(raceExecutor)
        val aFinish = java.util.concurrent.atomic.AtomicLong(0L)
        val bFinish = java.util.concurrent.atomic.AtomicLong(0L)
        val t0 = System.nanoTime()

        fun submit(isA: Boolean, group: String, url: CharSequence, body: CharSequence?, headers: Map<CharSequence, CharSequence>?) {
            completion.submit(Callable {
                try {
                    val resp = execPost(url.toString(), body, headers, totalMs, group)
                    (if (isA) aFinish else bFinish).set(System.nanoTime())
                    RaceResult(isA, resp, null)
                } catch (e: Throwable) {
                    RaceResult(isA, null, e)
                }
            })
        }

        submit(true, aGroup, aUrl, aBody, aHeaders)
        val sameAsA = aUrl.toString() == bUrl.toString() && (aBody ?: "").toString() == (bBody ?: "").toString()
        var bSubmitted = false
        // 第①段里 A 可能先失败：这条结果必须留住，否则第②段会当成「A 还没回来」白等到超时
        var aEarly: RaceResult? = null

        // ① A 成功 → 收工；A 失败不算冲突证据，到了点照旧发 B
        if (!sameAsA && raceDelayMs > 0L) {
            val wakeAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(raceDelayMs)
            while (true) {
                val left = wakeAt - System.nanoTime()
                if (left <= 0) break
                val polled = completion.poll(left, TimeUnit.NANOSECONDS) ?: break
                val r = polled.get()
                if (r.response != null) {
                    val ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)
                    lastRaceStats = """{"winner":"A","aMs":$ms,"bMs":0,"tie":false,"bSent":false,"timedOut":false,"failBranch":""}"""
                    return r.response
                }
                aEarly = r // A 失败：留着，接下来发 B 比一次
            }
        }
        if (!sameAsA) {
            if (System.nanoTime() >= deadline) {
                Net.cancelGroup(aGroup)
                lastRaceStats = """{"winner":"","aMs":0,"bMs":0,"tie":false,"bSent":false,"timedOut":true,"failBranch":""}"""
                return null
            }
            submit(false, bGroup, bUrl, bBody, bHeaders)
            bSubmitted = true
        }

        // ② 收结果：谁先成功谁赢，但**赢家是 B 时要回头等 A 一小会儿**——
        //    平手判定只看 50ms（RACE_TIE_MS），先到的若是 B，A 可能就在这几毫秒里也回了。
        //    只给这么点宽限，不能等满 20s：否则「B 6.4s 赢、A 12.4s 才回」会被拖成 12.4s，
        //    B 快出来的那 6 秒全白等（早期实现就是这个坑）。
        var aResult: RaceResult? = aEarly
        var bResult: RaceResult? = null
        var lastError: Throwable? = aEarly?.error
        // 出错的分支：交给 JS 用来「只扣那把钥的失败计数」。
        // 没有它，上层只知道「这轮失败了」，两把钥都会各扣一次 → 撞「连续 3 次删池」。
        var failBranches: String = if (aEarly?.error != null) "A" else ""
        var aDone = aEarly != null
        var bDone = !bSubmitted
        loop@ while (!aDone || !bDone) {
            val left = deadline - System.nanoTime()
            if (left <= 0) break
            val polled = completion.poll(left, TimeUnit.NANOSECONDS) ?: break
            val r = polled.get()
            if (r.isA) { aDone = true; aResult = r } else { bDone = true; bResult = r }
            if (r.response == null) {
                lastError = r.error
                val br = if (r.isA) "A" else "B"
                if (!failBranches.contains(br)) failBranches += br
            }
            if (aDone && aResult?.response != null) break@loop // A 已成功：不必再等 B
            if (bDone && bResult?.response != null && !aDone) {
                // B 先成功：只等 A 到平手窗口为止
                val grace = RACE_TIE_MS.toLong() * 1_000_000L
                val until2 = minOf(deadline, System.nanoTime() + grace)
                val maybeA = completion.poll(maxOf(until2 - System.nanoTime(), 0L), TimeUnit.NANOSECONDS)
                if (maybeA != null) {
                    val ra = maybeA.get()
                    aDone = true
                    if (ra.response != null) aResult = ra else lastError = ra.error
                }
                break@loop // 无论 A 回不回，B 都已是候选赢家，交给下方平手判定
            }
        }

        // ③ 平手判定：两把都成功且完成时刻差 < 50ms → 取 A
        val aOk = aResult?.response
        val bOk = bResult?.response
        val winner: RaceResult? = when {
            aOk != null && bOk != null -> {
                val dA = aFinish.get(); val dB = bFinish.get()
                // 两把都成功时完成时刻必定都 >0；差值 < 50ms 视为同时 → 固定取 A
                val diffMs = kotlin.math.abs(dB - dA) / 1_000_000.0
                if (diffMs < RACE_TIE_MS) aResult else if (dA <= dB) aResult else bResult
            }
            aOk != null -> aResult
            bOk != null -> bResult
            else -> null
        }

        if (winner == null) {
            Net.cancelGroup(aGroup)
            if (bSubmitted) Net.cancelGroup(bGroup)
            val ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)
            val realError = lastError?.takeIf { !isCancelOrTimeout(it) }
            if (realError != null) {
                // 有真错误（401/连不上/解析前失败）：原样上抛，并标明是哪条分支的错。
                // 「超时」不在此列——超时要走慢钥路径（暂停 10 分钟），不能算成钥坏了。
                lastRaceStats = """{"winner":"","aMs":$ms,"bMs":$ms,"tie":false,"bSent":$bSubmitted,"timedOut":false,"failBranch":"$failBranches"}"""
                throw realError
            }
            lastRaceStats = """{"winner":"","aMs":$ms,"bMs":$ms,"tie":false,"bSent":$bSubmitted,"timedOut":true,"failBranch":""}"""
            return null
        }
        // 输家整组掐掉：A 赢掐 B 组；B 赢掐 A 组
        Net.cancelGroup(if (winner.isA) bGroup else aGroup)
        // 平手判定与统计（两种都算成功时才算差值；只有一把成功时另一方耗时记 0）
        val aMs = if (aFinish.get() == 0L) 0L else TimeUnit.NANOSECONDS.toMillis(aFinish.get() - t0)
        val bMs = if (bFinish.get() == 0L) 0L else TimeUnit.NANOSECONDS.toMillis(bFinish.get() - t0)
        val tie = aOk != null && bOk != null && aFinish.get() > 0L && bFinish.get() > 0L &&
                kotlin.math.abs(aFinish.get() - bFinish.get()) / 1_000_000.0 < RACE_TIE_MS
        lastRaceStats = """{"winner":"${if (winner.isA) "A" else "B"}","aMs":$aMs,"bMs":$bMs,"tie":$tie,"bSent":$bSubmitted,"timedOut":false,"failBranch":""}"""
        return winner.response
    }

    /**
     * 这条异常是「超时/被取消」还是「真失败」（401、连不上、DNS 等）。
     * 判据：SocketTimeoutException 属于 InterruptedIOException；cancelGroup 引起的
     * 取消通常抛 CancellationException 或 IOException("Canceled")。这几类走慢钥路径
     * （暂停 10 分钟、不扣失败计数）；其余一律当真失败，交给上层按钥记账。
     */
    private fun isCancelOrTimeout(e: Throwable): Boolean {
        if (e is java.io.InterruptedIOException) return true
        if (e is java.util.concurrent.CancellationException) return true
        if (e is java.util.concurrent.TimeoutException) return true
        val m = (e.message ?: "").lowercase()
        return m.contains("canceled") || m.contains("cancelled") ||
            m.contains("timeout") || m.contains("timed out")
    }

    /** 加赛里一条分支的结果：是不是 A、成功响应、或异常。 */
    private class RaceResult(val isA: Boolean, val response: Response?, val error: Throwable?)

    private companion object {
        /** 平手阈值（毫秒）：两把完成时刻差小于它视为同时，固定取先发起的 A。 */
        private const val RACE_TIE_MS = 50.0

        /**
         * 竞速线程池：固定 4 条。
         * 为什么不是 2：预热批次 concurrency=2 时会并发跑两段，每段又要两把加赛 = 4 条在飞；
         * 只给 2 条会让第二段的加赛排队等线程，等于把「并发」白送掉。4 条刚好覆盖
         * 「预热 2 段 × 加赛 2 把」，播放请求再外加时排队也只在 20s 上限内。
         * 守护线程：不挡进程退出。
         */
        private val raceExecutor = Executors.newFixedThreadPool(4) { r ->
            Thread(r, "jsnet-race").apply { isDaemon = true }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @ScriptInterface
    private fun postMultipart(type: String, form: Map<String, Any>): MultipartBody.Builder {
        val multipartBody = MultipartBody.Builder()
        multipartBody.setType(type.toMediaType())

        form.forEach { entry ->
            when (entry.value) {
                // 文件表单
                is Map<*, *> -> {
                    val filePartMap = entry.value as Map<String, Any>
                    val fileName = filePartMap["fileName"] as? String
                    val body = filePartMap["body"]
                    val contentType = filePartMap["contentType"] as? String

                    val mediaType = contentType?.toMediaType()
                    val requestBody = when (body) {
                        is File -> body.asRequestBody(mediaType)
                        is ByteArray -> body.toRequestBody(mediaType)
                        is String -> body.toRequestBody(mediaType)
                        else -> body.toString().toRequestBody()
                    }

                    multipartBody.addFormDataPart(entry.key, fileName, requestBody)
                }

                // 常规表单
                else -> multipartBody.addFormDataPart(entry.key, entry.value as String)
            }
        }

        return multipartBody
    }

    @JvmOverloads
    @ScriptInterface
    fun httpPostMultipart(
        url: CharSequence,
        form: Map<String, Any>,
        type: String = "multipart/form-data",
        headers: Map<CharSequence, CharSequence>? = null
    ): Response {
        return Net.post(url.toString()) {
            setGroup(groupId)
            headers?.let {
                it.forEach {
                    setHeader(it.key.toString(), it.value.toString())
                }
            }
            body = postMultipart(type, form).build()
        }.execute()
    }
}