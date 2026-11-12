package com.github.jing332.deepseekproxy

import android.app.Application

/**
 * 独立「混元太极」APP 的 Application 入口。
 * App 重启后按上次状态自动恢复混元太极服务（与转发器一致）。
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 混元太极：若上次为「已开启」状态，App 重启后自动按原状态恢复服务。
        // ⚠️ Android 12+ 会拦「非前台进程起前台服务」：onCreate 时进程还不算前台，
        // 这次尝试可能失败——失败只记录，不拖垮启动；界面进入前台后由
        // MainActivity.onStart 再兜底一次（那里启动合法）。
        if (ProxyService.isSavedRunning(this)) {
            runCatching { ProxyService.startFromSaved(this) }
                .onFailure { com.github.jing332.deepseekproxy.proxy.LogStore.e("Proxy", "启动时自动恢复失败（等前台兜底）: ${it.message}") }
        }
    }
}
