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

    // ===================== 日志总量治理（11-11 用户令）=====================
    //
    // ## 为什么需要这一层
    // logback 那三个文件各自有 maxFileSize/maxHistory，Kotlin 写的三份各自"超限截半"，
    // 但它们**互不知道彼此**：朗读日志 30MB + 调试 20MB + 崩溃 5MB + 其余 10MB = 最多约 65MB。
    // 更要紧的是"定期"这一环根本不存在——全靠文件大小触发；朗读停用后日志不再增长，
    // 旧文件就永远躺在那儿。用户实报「日志文件不是又增加了吗，看看之前的设定要不要改」。
    //
    // ## 口径
    // · **单个文件**仍由各自的滚动/截半管（不动，避免双头治理打架）；
    // · **目录总量**由这里兜底：超过 40MB 就从最旧的文件开始删，直到回到上限以内；
    // · **定期**：启动时清一次，顺手删掉 7 天前的旧文件。
    // 40MB 的来由：正常一轮朗读一天约 1~3MB，40MB 够放两周左右的历史，
    // 而手机存储上它只占很小一块。要更省可以调小这个数，逻辑不用动。

    /** 日志目录总占用上限（字节）：超过就从最旧的文件开始删 */
    private const val LOG_DIR_TOTAL_CAP = 40L * 1024 * 1024

    /** 启动清理时顺带删掉多少天以前的旧文件（按最后修改时间） */
    private const val LOG_KEEP_DAYS = 7L

    /** 当前日志文件的总大小与总上限，供日志文件弹窗显示（用户 11-11 问过会不会涨到几兆） */
    fun logDirUsage(): Pair<Long, Long> {
        val total = runCatching {
            logDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
        }.getOrDefault(0L)
        return total to LOG_DIR_TOTAL_CAP
    }

    /**
     * 日志目录清理（11-11 新增）：
     * ① 删 LOG_KEEP_DAYS 天以前的文件（"定期"这一环，原来完全缺失）；
     * ② 总量超 LOG_DIR_TOTAL_CAP 时，按最后修改时间从旧到新删，直到降回上限以内。
     *
     * ⚠️ 不碰**logback 正在写的三个文件**（system_tts.log / debug.log / crash.log）：
     * 删掉它们会让 RollingFileAppender 的句柄失效，之后每次写都抛异常并回灌日志界面，
     * 自激成一片红直到重启（11-10 踩过）。轮转出来的备份（带日期后缀）可以放心删——
     * 那已经是另一个文件，logback 早已切换过去。
     *
     * 在 IO 线程调用（App 启动时的协程里），失败静默：清理失败不该影响启动。
     */
    fun pruneLogs() {
        runCatching {
            val dir = logDir
            if (!dir.isDirectory) return
            val now = System.currentTimeMillis()
            val cutoff = now - LOG_KEEP_DAYS * 24L * 60L * 60L * 1000L
            // 正在写的那三个，无论多大都不删（见上面注释）
            val liveNames = setOf("system_tts.log", "debug.log", "crash.log")

            val files = dir.listFiles()?.filter { it.isFile }?.toMutableList() ?: return

            // ① 先按时间清旧
            files.removeAll { f ->
                if (f.name in liveNames) return@removeAll false
                val stale = f.lastModified() < cutoff
                if (stale) runCatching { f.delete() }
                stale
            }

            // ② 再看总量
            var total = files.sumOf { it.length() }
            if (total <= LOG_DIR_TOTAL_CAP) return
            // 从最旧的开始删；liveNames 里的三个跳过（它们的体积由 logback 自己滚）
            val candidates = files
                .filter { it.name !in liveNames }
                .sortedBy { it.lastModified() }
            for (f in candidates) {
                if (total <= LOG_DIR_TOTAL_CAP) break
                val len = f.length()
                if (runCatching { f.delete() }.getOrDefault(false)) total -= len
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
