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
        )
        .allowMainThreadQueries()
        .openHelperFactory(LargeCursorOpenHelperFactory())
        .build()
}


@Database(
    version = 35,
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
         * 34 → 35：`tagRuleId` 整体下线（用户令 11-11「数据库里真的不存，字段整个删掉」）。
         *
         * 两处要处理：
         * 1) **v1 旧表 `sysTts`**：`SpeechRuleInfo` 是 `@Embedded("speechRule_")`，对应三个真实列
         *    （speechRule_tagRuleId 等）。字段从实体删掉后必须同步删列，否则 Room 校验 schema 会崩。
         *    ⚠️ 故意**只手写这一个迁移**、不加 `AutoMigration(34,35)`：AutoMigration 的删列需要
         *    仓库里有 34.json 作对比基线，而 schemas 只到 31，加了必然运行期崩。
         * 2) **v2 现役表 `system_tts_v2`**：`config` 是整段 JSON 文本，`speechRule.tagRuleId` 藏在里面。
         *    逐行读出、去掉该键、写回——这样「实际数据」里就没有这一项，导出自然也没有。
         *    只做一次，之后新写入的 JSON 由实体（无该字段）保证干净。
         */
        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // —— v1 旧表：删掉嵌入列（表可能不存在，旧装机已弃用该表）——
                runCatching {
                    db.execSQL("ALTER TABLE sysTts DROP COLUMN speechRule_tagRuleId")
                }

                // —— v2 现役表：把 config JSON 里的 tagRuleId 键抹掉 ——
                db.query("SELECT id, config FROM system_tts_v2").use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow("id")
                    val cfgIndex = cursor.getColumnIndexOrThrow("config")
                    val updates = ArrayList<Pair<Long, String>>()
                    while (cursor.moveToNext()) {
                        val raw = cursor.getString(cfgIndex).orEmpty()
                        if (!raw.contains("tagRuleId")) continue
                        val cleaned = stripTagRuleIdFromJson(raw)
                        if (cleaned != null) updates.add(cursor.getLong(idIndex) to cleaned)
                    }
                    cursor.close()
                    updates.forEach { (id, json) ->
                        db.execSQL(
                            "UPDATE system_tts_v2 SET config = ? WHERE id = ?",
                            arrayOf(json, id)
                        )
                    }
                }
            }

            /**
             * 从配置 JSON 里剔除 `speechRule.tagRuleId`。返回 null 表示无需改动（解析失败也返回
             * null——宁可留着旧值，也不能把用户配置写坏）。
             */
            private fun stripTagRuleIdFromJson(raw: String): String? = runCatching {
                val root = org.json.JSONObject(raw)
                val rule = root.optJSONObject("speechRule") ?: return null
                if (!rule.has("tagRuleId")) return null
                rule.remove("tagRuleId")
                root.put("speechRule", rule)
                root.toString()
            }.getOrNull()
        }

        fun createDatabase(context: Context) = Room
            .databaseBuilder(context, DatabaseManager::class.java, DATABASE_NAME)
            .addMigrations(
                MIGRATION_31_32,
                MIGRATION_32_33,
                MIGRATION_33_34,
                MIGRATION_34_35,
            )
            .allowMainThreadQueries()
            .openHelperFactory(LargeCursorOpenHelperFactory())
            .build()
    }

    @DeleteColumn(tableName = "sysTts", columnName = "isBgm")
    class DeleteSystemTtsColumn : AutoMigrationSpec
}