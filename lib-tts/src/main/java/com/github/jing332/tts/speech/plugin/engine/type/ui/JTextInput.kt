package com.github.jing332.tts.speech.plugin.engine.type.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.widget.FrameLayout
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView

@Suppress("unused")
@SuppressLint("ViewConstructor")
class JTextInput(context: Context, hint: CharSequence? = null) : FrameLayout(context) {

    // hint 状态化：插件 JS 运行时调用 setHint 也能实时更新 Compose label
    private var mHint by mutableStateOf(hint?.toString() ?: "")

    fun setHint(hint: CharSequence) {
        mHint = hint.toString()
    }

    fun getHint(): String = mHint

    // 插件惯用 EditText 式赋值/单行声明（moting 调 setText、qianwen 调 setSingleLine）
    fun setText(text: CharSequence) {
        mText = text.toString()
    }

    fun setSingleLine(singleLine: Boolean) {
        mMaxLines = if (singleLine) 1 else Int.MAX_VALUE
    }

    interface OnTextChangedListener {
        fun onChanged(text: CharSequence)
    }

    private val listeners = mutableSetOf<OnTextChangedListener>()

    fun addTextChangedListener(listener: OnTextChangedListener) {
        listeners.add(listener)
    }

    fun removeTextChangedListener(listener: OnTextChangedListener) {
        listeners.remove(listener)
    }

    private var mOnTextChangedListeners: OnTextChangedListener? = null

    fun setOnTextChangedListener(listener: OnTextChangedListener) {
        listeners.add(listener)
    }

    init {
        val compose = ComposeView(context)
        this.addView(compose)
        compose.setContent {
            Content()
        }
    }

    val text: Editable by lazy { MyEditable() }
    private var mText by mutableStateOf("")

    private var mMaxLines by mutableIntStateOf(Int.MAX_VALUE)
    var maxLines: Int
        get() = mMaxLines
        set(value) {
            mMaxLines = value
        }


    @Composable
    fun Content() {
        OutlinedTextField(
            value = mText,
            onValueChange = {
                mText = it
                for (listener in listeners) {
                    runCatching {
                        listener.onChanged(it)
                    }.onFailure {
                        it.printStackTrace()
                    }
                }
            },
            maxLines = mMaxLines,
            label = {
                if (mHint.isNotEmpty()) Text(mHint)
            }
        )
    }

    inner class MyEditable() : Editable {
        // 必须每次读取都取当前文本长度：写成 `val length = mText.length` 只在
        // MyEditable 构造时求值一次，之后 setText/append 改动了文本，length 仍是旧值，
        // 插件脚本按 length 做 subSequence/循环时会越界或读不到新内容。
        override val length: Int
            get() = mText.length

        override fun get(index: Int): Char {
            return mText[index]
        }

        fun set(text: CharSequence) {
            mText = text.toString()
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            return mText.subSequence(startIndex, endIndex)
        }

        override fun getChars(start: Int, end: Int, dest: CharArray?, destoff: Int) {
            // 与 String.getChars 同语义：把 [start, end) 复制到 dest。
            // dest 为 null 在 Android 契约里本就非法，这里静默返回而不是 NPE/抛异常，
            // 避免插件脚本传错参数时直接中断。
            if (dest == null) return
            mText.toCharArray(dest, destoff, start, end)
        }

        /*
         * 富文本（span）能力未实现：本类只是插件 JS 读写的纯文本包装，
         * 不承载样式信息。这里一律返回安全的空值而不是抛异常——
         * 插件脚本里常有「顺手设个颜色/样式」的写法，抛异常会直接中断插件运行，
         * 静默忽略对纯文本输入框而言是可接受且更稳妥的行为。
         */
        override fun <T : Any?> getSpans(start: Int, end: Int, type: Class<T>?): Array<T> {
            @Suppress("UNCHECKED_CAST")
            return emptyArray<T>()
        }

        override fun getSpanStart(tag: Any?): Int = -1

        override fun getSpanEnd(tag: Any?): Int = -1

        override fun getSpanFlags(tag: Any?): Int = 0

        override fun nextSpanTransition(start: Int, limit: Int, type: Class<*>?): Int = limit

        override fun setSpan(what: Any?, start: Int, end: Int, flags: Int) {
            // 无样式存储，忽略
        }

        override fun removeSpan(what: Any?) {
            // 无样式存储，忽略
        }

        override fun append(text: CharSequence?): Editable {
            mText += text
            return this
        }

        override fun append(text: CharSequence?, start: Int, end: Int): Editable {
            mText += text?.subSequence(start, end) ?: ""
            return this
        }

        override fun append(text: Char): Editable {
            mText += text
            return this
        }

        override fun replace(
            st: Int,
            en: Int,
            source: CharSequence?,
            start: Int,
            end: Int,
        ): Editable {
            return replace(st, en, source?.subSequence(start, end))
        }

        override fun replace(st: Int, en: Int, text: CharSequence?): Editable {
            val from = st.coerceIn(0, mText.length)
            val to = en.coerceIn(from, mText.length)
            mText = mText.replaceRange(from, to, text?.toString() ?: "")
            return this
        }

        override fun insert(where: Int, text: CharSequence?, start: Int, end: Int): Editable {
            return insert(where, text?.subSequence(start, end))
        }

        override fun insert(where: Int, text: CharSequence?): Editable {
            // 越界钳制：插件脚本常按字符偏移直接插入，偏移超长时不应崩。
            val at = where.coerceIn(0, mText.length)
            mText = mText.substring(0, at) + (text?.toString() ?: "") + mText.substring(at)
            return this
        }

        override fun delete(st: Int, en: Int): Editable {
            // 同上，起止顺序颠倒或越界时钳制到合法区间
            val from = st.coerceIn(0, mText.length)
            val to = en.coerceIn(from, mText.length)
            mText = mText.removeRange(from, to)
            return this
        }

        override fun clear() {
            mText = ""
        }

        override fun clearSpans() {
            // 无样式存储，忽略
        }

        // 输入过滤对以 JS 直接改文本的路径不生效，保存下来以便插件查询/回写，
        // 至少保证 setFilters/getFilters 对称，不会因调用而崩溃。
        private var mFilters: Array<out InputFilter> = emptyArray()

        override fun setFilters(filters: Array<out InputFilter>?) {
            mFilters = filters ?: emptyArray()
        }

        override fun getFilters(): Array<InputFilter> {
            @Suppress("UNCHECKED_CAST")
            return mFilters as Array<InputFilter>
        }

    }
}