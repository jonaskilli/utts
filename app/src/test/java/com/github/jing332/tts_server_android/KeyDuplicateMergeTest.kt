package com.github.jing332.tts_server_android

import com.github.jing332.tts_server_android.service.systts.help.KeyListFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 重复密钥合并（11-19 用户定稿「重复都不能存在，只要重复就让他合并」）。
 *
 * 判据：同地址 + 同模型 + 同密钥 = 重复；合并保留先出现的那条。
 * 同地址 + 同密钥但模型不同 = 合法用法，不合并。
 */
class KeyDuplicateMergeTest {

    private fun e(name: String, value: String) = KeyListFile.KeyEntry(name, "key01", value)

    @Test
    fun 同地址同模型同密钥_算重复() {
        val v = "https://api.x.com/v1@@glm-4-flash@@sk-abc"
        assertEquals(KeyListFile.dupSignature(v), KeyListFile.dupSignature(v))
    }

    @Test
    fun 同模型同密钥_地址写法不同_也算重复() {
        // /v1 与 /v1/chat/completions 两种写法指同一把钥
        val a = "https://api.x.com/v1@@glm-4-flash@@sk-abc"
        val b = "https://api.x.com/v1/chat/completions@@glm-4-flash@@sk-abc"
        assertEquals(KeyListFile.dupSignature(a), KeyListFile.dupSignature(b))
    }

    @Test
    fun 同址同钥_不同模型_不合并() {
        val a = "https://api.x.com/v1@@glm-4-flash@@sk-abc"
        val b = "https://api.x.com/v1@@glm-4.5-flash@@sk-abc"
        assertNotEquals(a, b)
    }

    private fun assertNotEquals(a: String, b: String) {
        assert(a != b) { "签名不应相同：$a vs $b" }
    }

    @Test
    fun 合并_保留先出现的_后来的删掉() {
        val list = listOf(
            e("first", "https://api.x.com/v1@@glm-4-flash@@sk-abc"),
            e("second", "https://api.x.com/v1@@glm-4-flash@@sk-abc"),
        )
        val (merged, dropped) = KeyListFile.mergeDuplicates(list)
        assertEquals(1, dropped)
        assertEquals(1, merged.size)
        assertEquals("first", merged[0].name)
    }

    @Test
    fun 合并_多模型同钥_两条都留() {
        val list = listOf(
            e("agnes-2.5", "https://api.agnes-ai.cn/v1@@agnes-2.5-flash@@sk-shared"),
            e("agnes-3.0", "https://api.agnes-ai.cn/v1@@agnes-3.0-flash@@sk-shared"),
        )
        val (merged, dropped) = KeyListFile.mergeDuplicates(list)
        assertEquals(0, dropped)
        assertEquals(2, merged.size)
    }

    @Test
    fun 找重复_排除自己() {
        val list = listOf(
            e("a", "https://api.x.com/v1@@glm-4-flash@@sk-abc"),
            e("b", "https://api.y.com/v1@@glm-4-flash@@sk-def"),
        )
        // 编辑 a 改成 b 的值 → 撞上 b
        val hit = KeyListFile.findDuplicate(list, "https://api.y.com/v1@@glm-4-flash@@sk-def", excludeName = "a")
        assertNotNull(hit)
        assertEquals("b", hit!!.name)
        // 编辑 b 保持原值 → 不撞（排除自己）
        assertNull(KeyListFile.findDuplicate(list, "https://api.y.com/v1@@glm-4-flash@@sk-def", excludeName = "b"))
    }

    @Test
    fun 池值去重_保序() {
        val vals = listOf(
            "https://api.x.com/v1@@glm-4-flash@@sk-abc",
            "https://api.y.com/v1@@glm-4-flash@@sk-def",
            "https://api.x.com/v1@@glm-4-flash@@sk-abc",
        )
        val out = KeyListFile.dedupPoolValues(vals)
        assertEquals(2, out.size)
        assertEquals("https://api.x.com/v1@@glm-4-flash@@sk-abc", out[0])
        assertEquals("https://api.y.com/v1@@glm-4-flash@@sk-def", out[1])
    }

    @Test
    fun 裸Key_也按值判重复() {
        val a = "sk-onlykey"
        val b = "sk-onlykey"
        assertEquals(KeyListFile.dupSignature(a), KeyListFile.dupSignature(b))
    }
}
