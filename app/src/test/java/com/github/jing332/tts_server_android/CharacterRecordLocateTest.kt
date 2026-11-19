package com.github.jing332.tts_server_android

import com.github.jing332.tts_server_android.service.systts.help.CharacterRecordsFile
import com.github.jing332.tts_server_android.service.systts.help.CharacterRecordsFile.RoleIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 角色定位：显示序 ≠ 文件序时也必须改对人（11-19 用户实报「想改 A 结果改了 B」）。
 *
 * 真实数据取自 2026-11-19 20:20 的真机 `characterRecords.json`（呼啸山庄）：
 * ```
 * 0 洛克乌德先生 男中年20
 * 1 耐莉        女中年02
 * 2 希刺克厉夫  男中年24
 * 3 凯瑟琳      少女08
 * 4 林惇夫人    女中年04
 * 5 伊莎贝拉    女青年01
 * 6 辛德雷·恩萧 男青年30
 * 7 弗兰西斯·恩萧 女青年04
 * ```
 *
 * 核心场景：规则朗读时 `moveRecordToTop` 把文件顺序改成「最近说话在前」，
 * 而界面显示顺序被 `frozenOrder` 钉住 ⇒ 用户按「第 3 行」时，文件第 3 条早已不是它。
 */
class CharacterRecordLocateTest {

    private val fileNames = listOf(
        "洛克乌德先生", "耐莉", "希刺克厉夫", "凯瑟琳",
        "林惇夫人", "伊莎贝拉", "辛德雷·恩萧", "弗兰西斯·恩萧",
    )
    private val fileVoices = listOf(
        "男中年20", "女中年02", "男中年24", "少女08",
        "女中年04", "女青年01", "男青年30", "女青年04",
    )
    private val mgr = CharacterRecordsFile

    private fun locate(name: String, voice: String = "", ordinal: Int = 0): Int? =
        mgr.locateIn(fileNames, fileVoices, RoleIdentity(name, voice, ordinal))

    @Test
    fun 名字唯一时_按名字定位_不看下标() {
        // 用户长按「耐莉」：无论它在界面上排第几行，都该定位到文件里的第 1 条
        assertEquals(1, locate("耐莉"))
        assertEquals(2, locate("希刺克厉夫"))
        assertEquals(0, locate("洛克乌德先生"))
    }

    @Test
    fun 显示序与文件序不同的场景_仍改对人() {
        // 真实场景复现：文件顺序被规则改过，界面顺序仍是冻结的旧顺序。
        //
        // 文件（朗读后）：希刺克厉夫最近说话 → 被 moveRecordToTop 挪到第 0 条
        //   [0]希刺克厉夫 [1]洛克乌德先生 [2]耐莉 [3]凯瑟琳 [4]林惇夫人 ...
        // 界面（冻结）：   [0]洛克乌德先生 [1]耐莉 [2]希刺克厉夫 [3]凯瑟琳 ...
        //
        // 用户在界面上点「耐莉」那一行 = 界面第 1 行 → 旧实现传 idx=1
        //   → 文件第 1 条 = 洛克乌德先生 ⇒ **改了别人**（正是用户报的现象）
        val movedNames = listOf("希刺克厉夫") + fileNames.filter { it != "希刺克厉夫" }
        val movedVoices = listOf("男中年24") + fileNames.indices
            .filter { fileNames[it] != "希刺克厉夫" }
            .map { fileVoices[it] }

        // 旧实现会取到谁（界面第 1 行 → 文件第 1 条）
        assertEquals("洛克乌德先生", movedNames[1])

        // 新实现：按身份定位，命中的必须真是耐莉
        val idx = mgr.locateIn(movedNames, movedVoices, RoleIdentity("耐莉", "女中年02"))!!
        assertEquals("耐莉", movedNames[idx])
        assertEquals("女中年02", movedVoices[idx])
        // 而且它已经不是界面上的第 1 行（2），证明修复确实没有沿用行号
        assertEquals(2, idx)
    }

    @Test
    fun 每个角色都能定位到自己的那一条() {
        fileNames.forEachIndexed { i, n ->
            val idx = locate(n, fileVoices[i])!!
            assertEquals(n, fileNames[idx])
            assertEquals(fileVoices[i], fileVoices[idx])
        }
    }

    @Test
    fun 同名多条_按标签区分() {
        val names = listOf("张三", "张三", "李四")
        val voices = listOf("男青年01", "男青年02", "男青年03")
        // 张三@男青年02 → 第 1 条（不是「第一条张三」）
        assertEquals(1, mgr.locateIn(names, voices, RoleIdentity("张三", "男青年02")))
        assertEquals(0, mgr.locateIn(names, voices, RoleIdentity("张三", "男青年01")))
    }

    @Test
    fun 同名同标签_按序号区分() {
        val names = listOf("张三", "张三", "张三")
        val voices = listOf("男青年01", "男青年01", "男青年01")
        assertEquals(0, mgr.locateIn(names, voices, RoleIdentity("张三", "男青年01", 0)))
        assertEquals(1, mgr.locateIn(names, voices, RoleIdentity("张三", "男青年01", 1)))
        assertEquals(2, mgr.locateIn(names, voices, RoleIdentity("张三", "男青年01", 2)))
    }

    @Test
    fun 名字大小写与空白归一() {
        val names = listOf("Nelly", "耐莉")
        val voices = listOf("女中年02", "女中年02")
        assertEquals(0, mgr.locateIn(names, voices, RoleIdentity("nelly", "女中年02")))
        assertEquals(0, mgr.locateIn(names, voices, RoleIdentity("  NELLY ", "女中年02")))
        assertEquals(1, mgr.locateIn(names, voices, RoleIdentity(" 耐莉 ", "女中年02")))
    }

    @Test
    fun 找不到时返回null_不乱指() {
        assertNull(locate("不存在的角色"))
        assertNull(locate(""))
        assertNull(locate("   "))
    }

    @Test
    fun 列表长度不一致时返回null_不越界() {
        assertNull(mgr.locateIn(listOf("A", "B"), listOf("男青年01"), RoleIdentity("A")))
    }
}
