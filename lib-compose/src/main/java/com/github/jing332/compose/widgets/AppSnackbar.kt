package com.github.jing332.compose.widgets

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * 全局 Snackbar 桥（11-21 Toast→Snackbar 替换的基础设施）。
 *
 * **为什么需要桥**：M3 的 Snackbar 必须挂在某个 SnackbarHost 上显示，而提示的
 * 发起点（嵌套 Composable / 非组合逻辑如回调、IO 协程）往往拿不到 host 状态——
 * 逐层传参 35 处调用点不现实。用 CompositionLocal 下发一份状态，
 * 任何层任何时刻都能发；host 挂在页面 Scaffold 上负责呈现。
 *
 * **窗口层级约束（谁该挂 host、谁保持 Toast）**：Compose 的 Dialog/AlertDialog
 * 是独立 window，弹在 App 主窗口**之上**——主窗口的 Snackbar 会被 Dialog 盖住。
 * 因此：
 *   · 页面层（Scaffold 可挂 host）→ 用本桥发 Snackbar；
 *   · Dialog 内部弹出的提示（换声面板、音频参数弹窗、BackupDialog 等）
 *     → **保留 Toast**，盖不住的问题无解，且 Toast 在 Dialog 上层显示正常。
 *
 * 用法：
 *   页面：`Scaffold(snackbarHost = { AppSnackbarHost() }, ...)`
 *   发送：`LocalAppSnackbar.current.send("已重命名")`
 *
 * send 是挂起函数的同步封装：内部自开协程发完即走，不阻塞调用方；重复发送
 * 会被 SnackbarHostState 自然排队（前一条显示完才轮到后一条），与 Toast 的
 * 「各自独立、同时消失」不同——顺序提示是 M3 的规范语义。
 */
class SnackbarController(val hostState: SnackbarHostState) {
    /**
     * 发一条短时提示（对齐 Toast.LENGTH_SHORT）。
     * 主线程即时协程发送，不阻塞调用方；重复发送由 SnackbarHostState 排队
     * （前一条显示完才轮到后一条）——M3 规范语义，与 Toast「各自独立」不同。
     */
    fun send(message: String) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
            .launch { hostState.showSnackbar(message, duration = SnackbarDuration.Short) }
    }
}

val LocalAppSnackbar = compositionLocalOf<SnackbarController> {
    error("LocalAppSnackbar not initialized")
}

/** 页面取当前 controller 挂 host 用（snackbarHost = { SnackbarHost(LocalAppSnackbar.current.hostState) } 更简）。 */
val LocalAppSnackbarHost: SnackbarHostState
    @Composable @ReadOnlyComposable get() = LocalAppSnackbar.current.hostState

/**
 * 便捷挂载：Scaffold 的 snackbarHost 槽直接填它。
 * `snackbarHost = { AppSnackbarHost() }`
 */
@Composable
fun AppSnackbarHost(modifier: Modifier = Modifier) {
    SnackbarHost(
        hostState = LocalAppSnackbarHost,
        modifier = modifier
    )
}
