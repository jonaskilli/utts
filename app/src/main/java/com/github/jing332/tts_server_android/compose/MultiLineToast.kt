package com.github.jing332.tts_server_android.compose

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

/**
 * 提示条（11-21 终稿二改：短文案回原生 Toast，长文案才用自定义胶囊）。
 *
 * 背景：第一版 MultiLineToast 无条件替换成自定义 view，但 GradientDrawable 只设了
 * 圆角没设颜色（透明底）、没有应用图标、文字左对齐——用户实机截图指出「根本不是
 * 原来的格式」（原格式=ROM 文本 Toast：白胶囊底 + 左侧应用图标 + 居中文字）。
 *
 * 现规则：
 * - 短文案（单行放得下）：直接 Toast.makeText，样式与位置完全由 ROM 决定，
 *   与改动前逐像素一致 = 「原来的格式」。
 * - 长文案（ROM 文本 Toast 单行截断，唯一真问题）：自定义胶囊 view，仿照原样式
 *   （胶囊底色 + 应用图标 + 居中、可换行），固定屏幕底部居中。
 * - 自定义 view 在个别 ROM 受限时兜底回系统 Toast。
 */
object MultiLineToast {

    fun show(context: Context, message: CharSequence, long: Boolean = false) {
        val duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        if (fitsOneLine(context, message)) {
            runCatching { Toast.makeText(context, message, duration).show() }
            return
        }
        runCatching { showPillToast(context, message, duration) }
            .onFailure {
                runCatching { Toast.makeText(context, message, duration).show() }
            }
    }

    fun show(@StringRes resId: Int, context: Context, vararg args: Any) {
        show(context, context.getString(resId, *args))
    }

    private fun dp(context: Context, v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()

    /** 短文案判定：无换行符，且按 16sp 量宽放得进「屏宽 - 胶囊内边距/图标/边距(留足余量)」 */
    private fun fitsOneLine(context: Context, message: CharSequence): Boolean {
        if (message.contains('\n')) return false
        val dm = context.resources.displayMetrics
        val paint = TextPaint().apply { textSize = 16 * dm.scaledDensity }
        val available = dm.widthPixels - dp(context, 140)
        return paint.measureText(message.toString()) <= available
    }

    /** 仿 ROM 文本 Toast 样式：胶囊底 + 应用图标 + 居中文字（可换行），跟随深浅色 */
    private fun showPillToast(context: Context, message: CharSequence, duration: Int) {
        val dm = context.resources.displayMetrics
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 24), dp(context, 14), dp(context, 24), dp(context, 14))
            background = GradientDrawable().apply {
                cornerRadius = 100f * dm.density // 远超半高 → 渲染为胶囊
                setColor(if (dark) 0xFF2C2C2E.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        val icon = ImageView(context).apply {
            // ROM 文本 Toast 左侧就是应用图标，保持一致
            setImageDrawable(ContextCompat.getDrawable(context, context.applicationInfo.icon))
        }
        val tv = TextView(context).apply {
            text = message
            textSize = 16f
            // 关键：不设 singleLine / maxLines —— 长文案自动换行显示全部
            setTextColor(if (dark) 0xFFFFFFFF.toInt() else 0xFF222222.toInt())
            gravity = Gravity.CENTER
        }
        container.addView(
            icon,
            LinearLayout.LayoutParams(dp(context, 22), dp(context, 22)).apply {
                marginEnd = dp(context, 9)
            },
        )
        container.addView(tv)
        val toast = Toast.makeText(context, "", duration)
        toast.view = container
        toast.setGravity(
            Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM,
            0,
            dp(context, 140),
        )
        toast.show()
    }
}
