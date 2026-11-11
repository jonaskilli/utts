package com.github.jing332.tts_server_android.constant

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.jing332.tts_server_android.BuildConfig
import com.github.jing332.tts_server_android.app
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

@SuppressLint("SimpleDateFormat")
@Suppress("DEPRECATION")
object AppConst {
    val fileProviderAuthor = BuildConfig.APPLICATION_ID + ".fileprovider"
    
    val localBroadcast: LocalBroadcastManager by lazy { LocalBroadcastManager.getInstance(app as Context) }
    val externalFilesDir: File by lazy { checkNotNull((app as Context).getExternalFilesDir("")) { "getExternalFilesDir() == null" } }
    val externalCacheDir: File by lazy { checkNotNull((app as Context).externalCacheDir) { "externalCacheDir == null" } }

    /** 用户可见的数据根目录：角色卡/书单/密钥等 JS 落盘文件都在这里（规则脚本同址写） */
    const val CHAJIAN_BASE_DIR = "/storage/emulated/0/Download/chajian"

    /**
     * 日志目录（11-11 用户令：搬到 Download/chajian/log，文件管理器里直接可见）。
     *
     * ⚠️ 必须有回退：这个目录要「所有文件访问」权限才能写。权限被撤、或首次启动还没来得及授权时，
     * 直接写会失败——而**日志写不进去恰恰是最需要日志的时候**，静默不写是最坏结果。
     * 所以先做一次真实写入探测（建目录 + 写个空文件 + 删掉），失败就退回旧的私有目录
     * `Android/data/<包名>/cache/log`（那个不需要任何权限，永远可写）。
     *
     * 探测只在首次取值时做一次（by lazy）：目录一旦确定，整个进程周期不再变，
     * 避免运行中权限变化导致一半日志在 A、一半在 B。
     */
    val logDir: File by lazy {
        val preferred = File(CHAJIAN_BASE_DIR, "log")
        // 兜底目录也包一层 runCatching：externalFilesDir 在存储未挂载时会抛异常，
        // 而"取日志目录"这件事本身绝不该把进程带崩。
        val fallback = runCatching { File(externalFilesDir.parentFile, "cache/log") }
            .getOrElse { File(app.filesDir, "log") }
        val writable = runCatching {
            preferred.mkdirs()
            val probe = File(preferred, ".write_probe")
            try {
                probe.writeText("")
                true
            } finally {
                runCatching { probe.delete() }
            }
        }.getOrDefault(false)
        if (writable) preferred else fallback.also { runCatching { it.mkdirs() } }
    }

    /**
     * 老日志迁移（11-11）：目录换到 chajian/log 后，把旧私有目录里的日志文件复制过去
     * （目标同名文件已存在就跳过，不覆盖新日志）。只在启动时跑一次，失败静默——
     * 迁移失败最多是"旧日志看不到"，绝不能因此影响启动。
     */
    fun migrateOldLogs() {
        runCatching {
            val old = File(externalFilesDir.parentFile, "cache/log")
            val new = logDir
            if (old.absolutePath == new.absolutePath) return
            if (!old.isDirectory) return
            old.listFiles()?.forEach { f ->
                if (!f.isFile) return@forEach
                val target = File(new, f.name)
                if (!target.exists()) runCatching { f.copyTo(target) }
            }
        }
    }

    var isSysTtsLogEnabled = true
    var isServerLogEnabled = false

    @OptIn(ExperimentalSerializationApi::class)
    val jsonBuilder: Json by lazy {
        Json {
            allowStructuredMapKeys = true
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
            explicitNulls = false
        }
    }

    // 第1项: 导出文件用紧凑 JSON, 关闭 prettyPrint。
    // prettyPrint 会让 3-4MB JSON 膨胀 30~60% 且拖慢序列化, 紧凑版体积更小、速度更快。
    // 导出页已改为只预览前 32KB, 显示不再依赖格式; 文件/复制/上传均用紧凑版。
    @OptIn(ExperimentalSerializationApi::class)
    val compactJsonBuilder: Json by lazy {
        Json {
            allowStructuredMapKeys = true
            ignoreUnknownKeys = true
            prettyPrint = false
            isLenient = true
            explicitNulls = false
        }
    }

    val isCnLocale: Boolean
        get() = (app as Context).resources.configuration.locale.language.endsWith("zh")

    val locale: Locale
        get() = (app as Context).resources.configuration.locale

    val localeCode: String
        get() = locale.run { "$language-$country" }

    val appInfo: AppInfo by lazy {
        val appInfo = AppInfo()
        val ctx = app as Context
        try {
            val info: PackageInfo = ctx.packageManager.getPackageInfo(
                ctx.packageName, 
                PackageManager.GET_ACTIVITIES
            )
            appInfo.versionName = info.versionName ?: ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                appInfo.versionCode = info.longVersionCode
            } else {
                appInfo.versionCode = info.versionCode.toLong()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        appInfo
    }

    data class AppInfo(
        var versionCode: Long = 0L,
        var versionName: String = "",
    )
}
