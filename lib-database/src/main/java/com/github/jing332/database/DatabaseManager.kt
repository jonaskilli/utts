package com.github.jing332.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.jing332.database.dao.PluginDao
import com.github.jing332.database.dao.ReplaceRuleDao
import com.github.jing332.database.dao.SpeechRuleDao
import com.github.jing332.database.dao.SystemTtsDao
import com.github.jing332.database.dao.SystemTtsV2Dao
import com.github.jing332.database.entities.SpeechRule
import com.github.jing332.database.entities.plugin.Plugin
import com.github.jing332.database.entities.replace.ReplaceRule
import com.github.jing332.database.entities.replace.ReplaceRuleGroup
import com.github.jing332.database.entities.systts.SystemTtsGroup
import com.github.jing332.database.entities.systts.clearSubGroupAudioParamsJson
import com.github.jing332.database.entities.systts.SystemTtsV2
import com.github.jing332.database.entities.systts.v1.SystemTts
import splitties.init.appCtx

val dbm: DatabaseManager by lazy {
    Room.databaseBuilder(appCtx, DatabaseManager::class.java, "systts.db")
        .addMigrations(
            DatabaseManager.MIGRATION_31_32,
            DatabaseManager.MIGRATION_32_33,
            DatabaseManager.MIGRATION_33_34,
            // ⚠️ 必加：@Database(version = 35) 升版后，这个懒加载单例若不含 34→35，
            // Room 打开库时找不到升级路径会直接抛 IllegalStateException，
            // **表现为应用一启动就闪退、连界面都到不了**（11-11 装机实锤）。
            // 注意本文件里的 createDatabase() 也注册了它，但那个函数全仓零调用；
            // 真正生效的是这里。加迁移时两处都要对，别只改看得见的那一处。
            DatabaseManager.MIGRATION_34_35,
            // ⚠️ 同上：@Database(version = 36) 升版后这里必须同步加 35→36，
            // 否则升级路径缺失 → 启动即闪退（11-11 装机实锤过同类问题）。
            DatabaseManager.MIGRATION_35_36,
        )
        .allowMainThreadQueries()
        .openHelperFactory(LargeCursorOpenHelperFactory())
        .build()
}


@Database(
    version = 36,
    entities = [
        SystemTts::class,
        SystemTtsV2::class,
        SystemTtsGroup::class,
        ReplaceRule::class,
        ReplaceRuleGroup::class,
        Plugin::class,
        SpeechRule::class,
    ],
    autoMigrations = [
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13, DatabaseManager.DeleteSystemTtsColumn::class),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
        // 15-16
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 21, to = 22),
        AutoMigration(from = 22, to = 23),
        AutoMigration(from = 23, to = 24),
        AutoMigration(from = 24, to = 25),
        AutoMigration(from = 25, to = 26),
        AutoMigration(from = 26, to = 27),
        AutoMigration(from = 27, to = 28),
        AutoMigration(from = 28, to = 29),
        AutoMigration(from = 29, to = 30),
        AutoMigration(from = 30, to = 31),
    ]
)
abstract class DatabaseManager : RoomDatabase() {
    abstract val systemTtsDao: SystemTtsDao
    abstract val systemTtsV2: SystemTtsV2Dao
    abstract val replaceRuleDao: ReplaceRuleDao
    abstract val pluginDao: PluginDao
    abstract val speechRuleDao: SpeechRuleDao

    companion object {
        private const val DATABASE_NAME = "systts.db"

        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Plugin ADD COLUMN pluginHandlesSpeed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Plugin ADD COLUMN pluginHandlesVolume INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Plugin ADD COLUMN pluginHandlesPitch INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE SystemTtsGroup ADD COLUMN audioParams_reverbEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Group/subgroup audio parameter scopes were removed. Keep the columns and JSON shape for
         * database/backup compatibility, but wipe every stored value once so no hidden multiplier
         * can survive the upgrade. JSON keys remain because they represent empty subgroup paths.
         */
        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE SystemTtsGroup SET " +
                        "audioParams_speed = 1.0, audioParams_volume = 1.0, " +
                        "audioParams_pitch = 1.0, audioParams_reverbEnabled = 0"
                )
                db.query("SELECT groupId, subGroupAudioParamsJson FROM SystemTtsGroup").use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow("groupId")
                    val jsonIndex = cursor.getColumnIndexOrThrow("subGroupAudioParamsJson")
                    while (cursor.moveToNext()) {
                        val cleared = clearSubGroupAudioParamsJson(cursor.getString(jsonIndex).orEmpty())
                        db.execSQL(
                            "UPDATE SystemTtsGroup SET subGroupAudioParamsJson = ? WHERE groupId = ?",
                            arrayOf(cleared, cursor.getLong(idIndex))
                        )
                    }
                }
            }
        }



        /**
         * 34 → 35：把 `tagRuleId` 从数据里彻底清掉（用户令 11-11）。
         *
         * 字段已从 `SpeechRuleInfo` 实体删除。这个迁移负责两件事：
         *
         * **1) v1 表 `sysTts` 的 `speechRule_tagRuleId` 列必须删掉。**
         * `SystemTts` 用 `@Embedded("speechRule_")` 把这个字段映射成真实列，字段删了列还在，
         * Room 启动校验（expected vs found）会直接崩。仓内有同样先例：12→13 为了删
         * `sysTts.isBgm` 专门写了 `DeleteSystemTtsColumn`。
         * ⚠️ `ALTER TABLE ... DROP COLUMN` 要 SQLite 3.35+（Android 12），本项目 minSdk=21，
         * 老设备不支持 → 按版本分支：支持就直接删列，不支持就重建表。
         *
         * **2) v2 表 `system_tts_v2` 的 config JSON 里删掉这个键。**
         * 该列是整段 JSON 文本，旧数据里仍带着 `"tagRuleId": "mingwuyan"`。不清掉的话
         * 实际数据里还有它，导出自然也还有——与用户要求不符。
         * 解析失败一律跳过（宁可留旧值，也不写坏配置）。
         */
        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropSysTtsTagRuleIdColumn(db)
                stripTagRuleIdFromConfigs(db)
            }

            /** 删 v1 表的 speechRule_tagRuleId 列（SQLite < 3.35 时退化为重建表）。 */
            private fun dropSysTtsTagRuleIdColumn(db: SupportSQLiteDatabase) {
                if (!tableExists(db, "sysTts")) return
                if (!columnExists(db, "sysTts", "speechRule_tagRuleId")) return
                // DROP COLUMN 自 SQLite 3.35（Android 12 / API 31）起支持。
                // 用 API 版本判断而非 runCatching 试探：后者失败时错误状态难收拾，
                // 且部分版本会先把表写坏再报错。API 31+ 直接删列，其余重建表。
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    db.execSQL("ALTER TABLE `sysTts` DROP COLUMN `speechRule_tagRuleId`")
                } else {
                    rebuildSysTts(db)
                }
            }

            /** 本机 SQLite 不支持 DROP COLUMN 时的兜底：重建表并搬数据（保留全部其它列）。 */
            private fun rebuildSysTts(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sysTts_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `groupId` INTEGER NOT NULL DEFAULT 1,
                        `displayName` TEXT,
                        `isEnabled` INTEGER NOT NULL,
                        `tts` TEXT NOT NULL,
                        `order` INTEGER NOT NULL DEFAULT 0,
                        `speechRule_target` INTEGER NOT NULL,
                        `speechRule_isStandby` INTEGER NOT NULL,
                        `speechRule_specifiedStandbyId` INTEGER,
                        `speechRule_tag` TEXT NOT NULL,
                        `speechRule_tagName` TEXT NOT NULL,
                        `speechRule_tagData` TEXT NOT NULL,
                        `speechRule_configId` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `sysTts_new` (
                        `id`, `groupId`, `displayName`, `isEnabled`, `tts`, `order`,
                        `speechRule_target`, `speechRule_isStandby`, `speechRule_specifiedStandbyId`,
                        `speechRule_tag`, `speechRule_tagName`, `speechRule_tagData`, `speechRule_configId`
                    )
                    SELECT
                        `id`, `groupId`, `displayName`, `isEnabled`, `tts`, `order`,
                        `speechRule_target`, `speechRule_isStandby`, `speechRule_specifiedStandbyId`,
                        `speechRule_tag`, `speechRule_tagName`, `speechRule_tagData`, `speechRule_configId`
                    FROM `sysTts`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `sysTts`")
                db.execSQL("ALTER TABLE `sysTts_new` RENAME TO `sysTts`")
            }

            /** 清掉 v2 表 config JSON 里的 speechRule.tagRuleId 键。 */
            private fun stripTagRuleIdFromConfigs(db: SupportSQLiteDatabase) {
                val updates = ArrayList<Pair<Long, String>>()
                db.query("SELECT id, config FROM system_tts_v2").use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow("id")
                    val cfgIndex = cursor.getColumnIndexOrThrow("config")
                    while (cursor.moveToNext()) {
                        val raw = cursor.getString(cfgIndex).orEmpty()
                        if (!raw.contains("tagRuleId")) continue
                        val cleaned = stripTagRuleId(raw) ?: continue
                        updates.add(cursor.getLong(idIndex) to cleaned)
                    }
                }
                updates.forEach { (id, json) ->
                    db.execSQL(
                        "UPDATE system_tts_v2 SET config = ? WHERE id = ?",
                        arrayOf(json, id)
                    )
                }
            }

            private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
                db.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(table)
                ).use { it.moveToFirst() }

            private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
                db.query("PRAGMA table_info(`$table`)").use { c ->
                    val nameIndex = c.getColumnIndexOrThrow("name")
                    while (c.moveToNext()) if (c.getString(nameIndex) == column) return true
                    false
                }

            /** 删掉 config JSON 里的 `speechRule.tagRuleId`；无需改动或解析失败返回 null。 */
            private fun stripTagRuleId(raw: String): String? = runCatching {
                val root = org.json.JSONObject(raw)
                val rule = root.optJSONObject("speechRule") ?: return null
                if (!rule.has("tagRuleId")) return null
                rule.remove("tagRuleId")
                root.put("speechRule", rule)
                root.toString()
            }.getOrNull()
        }

        /**
         * 插件并发安全标记（2026-11-15，任务书-UTTS引擎实例池）。
         *
         * 新列 allowParallel 默认 0（安全优先）——未判定插件一律走单实例路径，
         * 行为与升版前逐字节一致。这里把 11-15 实读 23 项插件 JS 判定出的
         * 「可开池」白名单灌成 1（判据见《技术文档/任务书-UTTS引擎实例池-20261115.md》§3.1）。
         *
         * 为什么用白名单而不是黑名单：判错的代价不对称。把不可开池插件误判成可开
         * → 第二笔请求重置第一笔的 WS（「请等待当前请求完成/Broken pipe」）；
         * 把可开池插件漏判 → 只是没提速，不出错。所以只灌确定的。
         *
         * pluginId 精确匹配（不做前缀通配）：同平台不同作者改的插件形态可能不同
         * （如千问分享版与千问QK39 同为通义但一个走账号级壳会话），通配会误伤。
         */
        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 表不存在说明是全新库（Room 会直接建到最新版本），无需补列
                val hasTable = db.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='Plugin'"
                ).use { it.moveToFirst() }
                if (!hasTable) return
                db.execSQL("ALTER TABLE Plugin ADD COLUMN allowParallel INTEGER NOT NULL DEFAULT 0")
                for (pid in PARALLEL_SAFE_PLUGIN_IDS) {
                    db.execSQL("UPDATE Plugin SET allowParallel = 1 WHERE pluginId = ?", arrayOf(pid))
                }
            }
        }

        /**
         * 11-15 判定：每请求自建连接/局部状态、无模块级共享 WS 或忙标志的插件。
         * 主力五个（本悦读/纳米AI36/百度云端/讯飞配音/火山翻译）全部在列。
         */
        private val PARALLEL_SAFE_PLUGIN_IDS = arrayOf(
            // 主力：旁白/对话实际在用的五个
            "readin.official.zhcn.54.direct.emotion.20260817",      // 本悦读（每段新建 TCP，socket 是局部变量）
            "nami.ai.official.36.nologin.jtts.fixed.20260816",      // 纳米AI36（纯无状态 POST）
            "com.baidu.webdemo.tts.nokey.dual.cn73.universal",      // 百度云端（自带 2 路并发+信号量）
            "peiyin.xunfei.cm.officialcn348.jreadurl.fxpicker.20260703", // 讯飞配音（签名→GET 签名 URL）
            "translate.volcengine.com",                             // 火山翻译（整包 base64）
            // 其余实读判定的可开池插件
            "com.microsoft.translator",                             // 模块级只缓存 token，401 才重取
            "ytbarrage.tencent.official.25.nologin.emobridge.jtts.20260816", // 仅 auth 缓存（时间戳）
            "com.tencent.hunyuan.tts",                              // 签名→下载
            "cn.ttson.acgn.zh",                                     // 海豚配音（每次尝试换 IP+设备身份）
            "ai.breezeblue.tts",                                    // 微风蓝
            "ai.breezeblue.tts.pro",                                // 微风蓝 Pro
            "moting.peiyinshenqi.web.20261104",                      // 配音神器（提交→轮询→下载）
            "moting.fishaudio.voice.marketplace.zh.20260903",        // Fish 广场（每请求新 openConnection）
            "moting.fish.clone.mimo-library.20261014",               // Fish MiMo（httpPost 单请求）
            "com.yx520.tts",                                        // 剪映 yx520 中转版（第三方 HTTP）
            "jread.yuanbao.official.zh11.ws.v1",                     // 元宝（WS 但每请求新建+局部 state）
            "wenxin.official.5.nologin.emotion.20260814",            // 文心（同元宝型，每块新建）
        )

        fun createDatabase(context: Context) = Room
            .databaseBuilder(context, DatabaseManager::class.java, DATABASE_NAME)
            .addMigrations(
                MIGRATION_31_32,
                MIGRATION_32_33,
                MIGRATION_33_34,
                MIGRATION_34_35,
                MIGRATION_35_36,
            )
            .allowMainThreadQueries()
            .openHelperFactory(LargeCursorOpenHelperFactory())
            .build()
    }

    @DeleteColumn(tableName = "sysTts", columnName = "isBgm")
    class DeleteSystemTtsColumn : AutoMigrationSpec
}