package com.github.jing332.script.runtime

import com.drake.net.NetConfig
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 按毫秒缓存的「本次请求专用」OkHttpClient。
 *
 * 为什么要有它：U·TTS 全局超时被 NetConfig 定成 300s（见 App.kt，为长合成留的），
 * 对插件/规则里的 AI 调用过宽——`ttsrv.httpPost` 曾因此把单请求超时参数整个忽略，
 * 6s 想切钥根本切不动（规则只能设 header 'Timeout'，宿主不认）。
 *
 * 只在调用方显式传了 timeoutMs(>0) 时才克隆；传 0/不传一律走全局 client，
 * 既有调用的行为逐字节不变。按毫秒值缓存，避免每次请求 clone 一次。
 */
internal object NetClients {
    private val cache = ConcurrentHashMap<Long, OkHttpClient>()

    fun withTimeout(timeoutMs: Long): OkHttpClient =
        cache.getOrPut(timeoutMs) {
            NetConfig.okHttpClient.newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
        }
}
