package com.github.jing332.tts_server_android.compose

import android.content.Context
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * 提示条（11-21 终稿三改：直接复用 ROM 的 Toast 布局，只解除单行限制）。
 *
 * 沿革：11-21 上午 Toast→Snackbar 三批被逐项否掉 → a8395ef6 换回 Toast + 自定义
 * MultiLineToast（自绘胶囊）→ 用户实机指出「根本不是原来的格式」：胶囊没设底色
 * （透明）、缺应用图标、文字左对齐、文字颜色也不对（v1 TextView 无颜色设置，
 * 用了 App 主题默认值，与 ROM Toast 的原色不符）。
 *
 * 教训：自绘仿 ROM 样式永远仿不精确（底色/图标/文字颜色/字号全是猜），改用
 * ROM 自带的 transient_notification 布局 inflate——胶囊底、图标、颜色、字号
 * 全部原样，唯一改动是 TextView 解除单行限制，长文案换行显示全部。
 *
 * 兜底链：ROM 布局 inflate 失败（ROM 改名/资源受限）→ 直接 Toast.makeText
 * （格式正确，仅长文案可能单行截断，宁缺多行不失格式）。
 */
object MultiLineToast {

    fun show(context: Context, message: CharSequence, long: Boolean = false) {
        val duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        if (showWithRomLayout(context, message, duration)) return
        runCatching { Toast.makeText(context, message, duration).show() }
    }

    fun show(@StringRes resId: Int, context: Context, vararg args: Any) {
        show(context, context.getString(resId, *args))
    }

    /**
     * 用 ROM 自己的 Toast 布局弹提示。不设 Gravity——用系统默认位置，
     * 与文本 Toast 完全一致。
     */
    private fun showWithRomLayout(context: Context, message: CharSequence, duration: Int): Boolean =
        runCatching {
            val layId = Resources.getSystem()
                .getIdentifier("transient_notification", "layout", "android")
            if (layId == 0) return false
            val root = LayoutInflater.from(context).inflate(layId, null)
            val tv = findTextView(root) ?: return false
            tv.text = message
            tv.isSingleLine = false
            tv.maxLines = Int.MAX_VALUE
            tv.ellipsize = null
            val toast = Toast.makeText(context, "", duration)
            toast.view = root
            toast.show()
            true
        }.getOrDefault(false)

    private fun findTextView(v: View?): TextView? = when {
        v is TextView -> v
        v is ViewGroup -> {
            for (i in 0 until v.childCount) {
                findTextView(v.getChildAt(i))?.let { return it }
            }
            null
        }
        else -> null
    }
}
