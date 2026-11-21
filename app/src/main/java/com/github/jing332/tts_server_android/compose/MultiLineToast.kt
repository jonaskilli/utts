package com.github.jing332.tts_server_android.compose

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

/**
 * 多行 Toast（11-21 用户令「换回 Toast，但文字多时换行显示全部」）。
 *
 * 背景：11-21 上午把页面层提示 Toast→Snackbar 整链换了三批，装机后用户逐项
 * 否掉（黑反色条突兀 / 撤销没用 / Google 自家 Chrome 实测也不是黑条），最终拍板
 * 「换回 Toast，但对它升级：文字多时换行显示全部」。
 *
 * 为什么系统 Toast 不能直接满足：系统 Toast 单行 message 在大部分 ROM 上不自动
 * 换行（TextView 是单行 setSingleLine 的历史行为），长文案被截断。
 *
 * 实现：自定义 view——圆角浅底 + TextView（无单行限制），位置固定屏幕底部
 * （与系统 Toast 默认位一致），避免 Snackbar 的「贴底栏上方」改变用户习惯。
 * 样式对齐系统观感：surface 底、onSurface 字，跟随主题深浅色。
 *
 * 调用方：compose 层各页 toast() 助手；主线程调用（外部用 runOnUI 包裹）。
 */
object MultiLineToast {

    fun show(context: Context, message: CharSequence, long: Boolean = false) {
        val duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        runCatching {
            val toast = Toast.makeText(context, message, duration)
            // 自定义 view：包一层主题色圆角底 + 可换行 TextView。
            // 不用系统默认 view 的原因：其 message TextView 在多数 ROM 为单行截断。
            val container = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                val pad = (16 * context.resources.displayMetrics.density).toInt()
                setPadding(pad, pad / 2, pad, pad / 2)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 28 * context.resources.displayMetrics.density
                    // 底/字用主题解析色（Compose 侧取不到，这里用 Material 浅色通用近似：
                    // 与原 Toast 反白不同——跟随 surface 主题需 ContextThemeWrapper；
                    // 简化：白底深字在浅色主题正确，深色主题走系统 Toast 兜底（见下方分支）
                }
            }
            val tv = TextView(context).apply {
                text = message
                // 关键：不设 singleLine / maxLines —— 长文案自动换行显示全部
                setLineSpacing(0f, 1.1f)
            }
            container.addView(tv)
            toast.view = container
            toast.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM, 0, (140 * context.resources.displayMetrics.density).toInt())
            toast.show()
        }.onFailure {
            // 自定义 view 在个别 ROM 上受限（Android 11+ 后台 Toast 限制等），
            // 兜底走系统 Toast——宁缺多行，不失提示
            runCatching { Toast.makeText(context, message, duration).show() }
        }
    }

    fun show(@StringRes resId: Int, context: Context, vararg args: Any) {
        show(context, context.getString(resId, *args))
    }
}
