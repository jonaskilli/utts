package com.github.jing332.tts.synthesizer

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * 合成结果文本级缓存（2026-11-15，任务书-U阅批量提交多段 · 路 C）。
 *
 * ## 为什么需要它
 *
 * U阅 要的是「后台缓存攒够量」，但它的播放是**一段一个音频文件**，
 * 而系统 TTS 一次 `synthesizeToFile` 只能产出一个文件。所以「一次请求合 N 段、
 * 返回一个连续音频」没法直接接进 U阅 的播放链（段号/跳段/进度会全乱）。
 *
 * 解法：把并行合成变成**预热**——
 * - 批量请求：UTTS 内部并发合成 N 段，各段音频写进本缓存，不回传音频；
 * - 之后 U阅 正常发单段请求时，这里命中 → 秒返回，不必再调插件。
 *
 * 这样 U阅 的播放粒度、缓存文件命名、跳段逻辑**一行都不用改**。
 *
 * ## 缓存键
 *
 * 文本 + 发音源 + 音色 + 语速/音量/音高 + 采样率，全部进键。
 * 任一项变了音频就不同，不能用「只按文本」的粗键——否则改语速后会播到旧音频。
 *
 * ## 容量
 *
 * 超过 [maxBytes] 时按最后访问时间删最旧的，直到降到 [lowWatermarkBytes]。
 * 清理在写缓存时顺带做（不另起定时器，避免多一个常驻协程）。
 */
object SynthesizedAudioCache {
    private const val TAG = "SynthesizedAudioCache"

    /** 缓存总容量上限：200MB。一段 24kHz 单声道 PCM 约每秒 48KB，5 分钟音频约 14MB。 */
    private const val maxBytes: Long = 200L * 1024 * 1024
    private const val lowWatermarkBytes: Long = 150L * 1024 * 1024

    @Volatile
    private var dir: File? = null

    @Volatile
    private var enabled: Boolean = false

    /** 命中次数/未命中次数，仅用于日志诊断。 */
    private val hits = AtomicLong()
    private val misses = AtomicLong()

    /**
     * @param cacheRoot 通常传 context.cacheDir，会建子目录 `synth_cache`。
     *   用 cacheDir 而非 filesDir：这是可再生成的加速数据，系统清缓存时一起清掉正合适。
     */
    fun init(context: Context) {
        if (dir != null) return
        runCatching {
            val d = File(context.cacheDir, "synth_cache")
            if (!d.exists()) d.mkdirs()
            dir = d
            enabled = d.isDirectory && d.canWrite()
            Log.d(TAG, "init: ${d.absolutePath}, enabled=$enabled")
        }.onFailure { Log.w(TAG, "init failed: $it") }
    }

    /**
     * 生成缓存键。
     *
     * @param text 该段的最终送合成文本（规则替换之后）
     * @param sourceKey 发音源标识（插件 id / 本机引擎名），同类文本换插件音频不同
     * @param voice 音色
     * @param locale 语言
     * @param speed 最终语速（已含三层参数乘积）
     * @param volume 最终音量
     * @param pitch 最终音高
     * @param sampleRate 输出采样率
     */
    fun keyOf(
        text: String,
        sourceKey: String,
        voice: String,
        locale: String,
        speed: Float,
        volume: Float,
        pitch: Float,
        sampleRate: Int,
    ): String {
        // 参数保留 3 位小数：避免浮点尾差导致同一设置算出两个键
        val raw = buildString {
            append(text)
            append('\u0001').append(sourceKey)
            append('\u0001').append(voice)
            append('\u0001').append(locale)
            append('\u0001').append(String.format("%.3f", speed))
            append('\u0001').append(String.format("%.3f", volume))
            append('\u0001').append(String.format("%.3f", pitch))
            append('\u0001').append(sampleRate)
        }
        val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    /** 读取已缓存的 PCM；未命中/未启用返回 null。 */
    fun get(key: String): ByteArray? {
        if (!enabled) return null
        val file = fileOf(key) ?: return null
        if (!file.exists() || file.length() <= 0L) {
            misses.incrementAndGet()
            return null
        }
        return runCatching {
            // 命中即刷新访问时间，LRU 清理按它排序
            file.setLastModified(System.currentTimeMillis())
            hits.incrementAndGet()
            file.readBytes()
        }.getOrNull()
    }

    /** 写入 PCM。失败只记日志，绝不影响合成主流程。 */
    fun put(key: String, pcm: ByteArray) {
        if (!enabled || pcm.isEmpty()) return
        val file = fileOf(key) ?: return
        runCatching {
            // 先写临时文件再改名：避免并发写入/中途被杀留下半截文件被当成有效缓存
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(pcm)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            trimIfNeeded()
        }.onFailure { Log.w(TAG, "put failed: $it") }
    }

    /** 清空（插件/音色配置变更时可用；调度策略未接，先留公开入口）。 */
    fun clear() {
        val d = dir ?: return
        runCatching { d.listFiles()?.forEach { it.delete() } }
        hits.set(0)
        misses.set(0)
    }

    fun stats(): String = "hits=${hits.get()}, misses=${misses.get()}, dir=${dir?.absolutePath}"

    private fun fileOf(key: String): File? {
        val d = dir ?: return null
        return File(d, "$key.pcm")
    }

    /** 超限时按最后访问时间删最旧的，降到低水位。 */
    private fun trimIfNeeded() {
        val d = dir ?: return
        val files = d.listFiles()?.filter { it.isFile && it.name.endsWith(".pcm") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        // 旧文件先删；同一毫秒内多个文件时顺序无所谓
        files.sortedBy { it.lastModified() }.forEach { f ->
            if (total <= lowWatermarkBytes) return@forEach
            val len = f.length()
            if (f.delete()) total -= len
        }
        Log.d(TAG, "trim done, now=${total / 1024 / 1024}MB")
    }
}
