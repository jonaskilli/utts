package com.github.jing332.tts_server_android.compose.theme

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlin.math.cbrt
import kotlin.math.pow
import com.github.jing332.tts_server_android.conf.AppConfig

/**
 * 获取当前主题
 */
@Composable
fun appTheme(
    themeType: AppTheme,
    // 默认走 App 内深浅设置（跟随系统/浅/深），不再直取系统值——否则设置里的开关对这里无效
    darkTheme: Boolean = isAppInDarkTheme(),
    context: Context = LocalContext.current,
): ColorScheme {
    // 11-10：静态主题从 3 套扩到 8 套。when 必须穷举（AppTheme 是 enum），
    // 新增主题时这里漏写一支 = 编译期直接报 when exhaustiveness，不会静默漏掉
    val base = when (themeType) {
        AppTheme.DEFAULT, AppTheme.GREEN -> greenTheme(darkTheme)
        AppTheme.DYNAMIC_COLOR -> dynamicColorTheme(darkTheme, context)
        AppTheme.PURPLE -> purpleTheme(darkTheme)
        AppTheme.MIST -> mistTheme(darkTheme)
        AppTheme.GRAPHITE -> graphiteTheme(darkTheme)
    }
    // 动态取色（Android 12+ 壁纸派生）：浅色同样走纯白底（与静态主题同口径），深色用官方暗表现算
    return if (themeType == AppTheme.DYNAMIC_COLOR) dynamicNeutral(base, darkTheme) else themedNeutral(base, themeType, darkTheme)
}

/**
 * 「浅结构档」——五页搜索胶囊共用（主界面底栏已退页底色，10-13 深夜用户令）：
 * 结构槽 surfaceContainer 往页底混七成（30% Container + 70% background），
 * 比卡底浅一丝、比页底深约 8 级：胶囊在页面上能站出来。
 * 沿革：0.6f（装机隐形）→ 0.4f（装机仍不显，框线方案一并否决）→ 现行 0.3f。
 * 运行时 lerp 而非预生成表：十套静态主题 × 深浅色 + 动态取色（Android 12+）全成立；
 * 深浅要再调，只改这里的 0.3f（越小越深、越大越浅）。
 */
@Composable
fun softStructureColor(): Color = lerp(
    MaterialTheme.colorScheme.surfaceContainer,
    MaterialTheme.colorScheme.background,
    0.3f,
)

/**
 * Switch 未选中轨道——沿革三稿（GREEN 浅色）：
 * ① Highest 槽本色 #DAF1E1（10-13 晚）→ 用户令「未开启的不应该有颜色」太绿；
 * ② 纯无彩灰 #E2E2E2（10-13 深夜）→ 用户令「还是得加颜色，不过以前那个不行」太死板；
 * ③ 现行：中性灰往主题 Highest 槽混四成（GREEN ≈ #DFE8E2）——带一丝主题色相、
 *    比旧档淡一截，十套主题 × 深浅色自动成立。深色仍走 Highest 槽（未验收不盲改）。
 * 五处 Switch 取用（设置项行/转发器/合并插入/仅UI模式/日志调试面板）。
 */
@Composable
fun neutralSwitchUncheckedTrackColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f)
        lerp(Color(0xFFE2E2E2), MaterialTheme.colorScheme.surfaceContainerHighest, 0.4f)
    else MaterialTheme.colorScheme.surfaceContainerHighest

/**
 * 配置项 / 密钥条目卡片的卡面 —— 就是主题的 surfaceContainerLow（11-11 用户令三度定稿）。
 *
 * ## 为什么最后落回这一档（改动前先读，别再来回折腾）
 *
 * 用户三次反馈：
 * ① 写死 #F7F2FA（微紫白 H=277°S=44%）→「某些主题下太突兀」：微紫在绿底/雾蓝底上串色；
 * ② 我出四档中性灰预览让用户挑 →「我看你给我出的那几个档，我也没啥感觉」（分不出，也不需要多出来一档）；
 * ③ 结论：**回退到之前那个中性灰**，不要再自造新色值。
 *
 * 「之前那个中性灰」的准确来源不是某个硬编十六进制，而是**主题的 surfaceContainerLow**
 * （7efb66e6 之前的写法）。它在四套浅色里都被中性化成了 #F9F9F9，
 * 深色则各取自己的深槽（#1D201E / #201F22 / #1E1F22 / #1F1F1F），比写死单一值更合理。
 *
 * 已知取舍（用户已接受，别再"顺手修正"）：浅色下这枚灰对紫页底只有 1.004、对绿页底 1.028，
 * 卡片与页底几乎齐平，边界主要靠 ElevatedCard 自带的 elevation 阴影。用户明确说够用。
 */
@Composable
fun neutralCardColor(): Color = MaterialTheme.colorScheme.surfaceContainerLow

/**
 * 密钥页「组头色」（卡片上方那条接口名 + 网址的色带，11-11 用户令）。
 *
 * ## 为什么是这条公式（改动前先读）
 *
 * 用户先要「恢复以前那个绿」，随即补充「但你也得根据主题色来变化，不能都只是绿」。
 * 历史核对（git 与旧 SurfaceTones 表）：
 * · 11-07 → 11-10 01:34 绿主题组头 = surfaceContainer 槽 = **#EAF7EF**，这是用户记的那个绿
 * · 11-11 曾写死 #E4F7E8 —— 那是 11-10 03:30「中性槽彩度修正」的中间产物，
 *   只存在 1 小时 9 分就被 04:39 的「底色中性化」覆盖，**不是用户印象里的绿**
 *   （差在色相：老绿 143° 偏青，那个 133° 偏黄绿，在一套 150° 主色的界面里发荧光）。
 * · 旧表的 PURPLE/MIST/GRAPHITE 各有一套自己的组头色，本来就**不是全绿**。
 *
 * 现公式：浅色 `lerp(secondaryContainer, background, 0.55)`，
 * 深色 `lerp(secondaryContainer, neutralCardColor(), 0.30)`。
 * 绿主题算出 #EAF7EF（与 11-07 老值逐通道仅差 3/255），紫/雾/墨自动落成各自的浅色；
 * 各主题对页底对比 1.07~1.09，四套之间几乎一致（不会换主题就冒出一块绿）。
 */
@Composable
fun keyGroupHeaderColor(): Color {
    // ===== 11-13 晚（用户令「要换，统一下……这都是跟主题色配套的」）=====
    // 原公式 lerp(secondaryContainer, background, 0.55f) → 绿主题 #EAF8ED，
    // 与「书籍框/角色标签」的 softFillColor()（#E7F7EC）只差 3 个色阶，
    // 肉眼看不出但**公式是两套**：调色时要改两处，且紫/雾/墨三套各自的落点也略有出入。
    // 现统一委托给 softFillColor()，全站「浅填充绿」只有一个来源。
    //
    // 历史保留：上一版为什么那么写、绿主题逐通道核对过程见上方注释——
    // 那些结论（#EAF7EF 才是用户记的老绿）仍然有效，只是不再单独实现一遍。
    return softFillColor()
}

/**
 * 全站「浅填充色」单一来源（11-13 晚统一）。
 *
 * 用在：角色列表标签框、换声面板分段选中态、**书籍框填充**、**日志页章首底色**、
 * **密钥管理组头色带**——凡是「需要一层比页底略深/略彩、又不抢正文的浅底」都走这里。
 *
 * 浅色 `lerp(secondaryContainer, White, 0.42)`，绿主题算出 **#E7F7EC**；
 * 深色往上一级浅槽混，保持"底色比页底亮一档"的原关系（往白里混会糊成中灰）。
 *
 * ⚠️ 调浓度时**只改这里**，别再各处写 lerp——否则又会出现「一大一小两档绿」
 * （这个坑 11-11 已经踩过一次）。
 */
@Composable
fun softFillColor(): Color {
    val cs = MaterialTheme.colorScheme
    return if (cs.background.luminance() > 0.5f)
        lerp(cs.secondaryContainer, Color.White, 0.42f)
    else lerp(cs.secondaryContainer, cs.surfaceContainerHighest, 0.42f)
}

/**
 * surfaceContainer 七槽——M3 官方 baseline tone 阶梯（2026-10-10 起 replacing 自创偏移表）。
 *
 * 旧法：surface 各通道 ±固定值再掺 4% primary——相邻槽只差 2~4 个 RGB 灰阶，
 * 与页底/列表卡/设置卡/底栏全挤在 3% 亮度带里，层级肉眼读不出（用户实测「层级关系一般」）。
 * 新法：tone 抄官方表（浅 100/96/94/92/90/87/98、深 4/10/12/17/22/6/24，Material Theme Builder 同款），
 * 层级差来自官方表本身（相邻槽 2~5 个 L* 步长）；静态主题查 SurfaceTones.kt 预生成表，
 * 深色锚到各主题 background（官方锚 surface=tone6，本 app 页底=background），其余原样。
 */
private fun themedNeutral(
    scheme: ColorScheme,
    themeType: AppTheme,
    darkTheme: Boolean,
): ColorScheme {
    val tones = (if (darkTheme) darkSurfaceTones else lightSurfaceTones)[themeType]
        ?: return scheme
    // 10-10 晚：浅色回退纯白底（ce2e3ce），回归带主题色相的淡底+淡灰卡——
    // 上午的官方 tone 表阶梯引擎保留（层级差来自表本身），background/surface 用各主题原值
    return scheme.copy(
        surfaceContainerLowest = tones[0],
        surfaceContainerLow = tones[1],
        surfaceContainer = tones[2],
        surfaceContainerHigh = tones[3],
        surfaceContainerHighest = tones[4],
        surfaceDim = tones[5],
        surfaceBright = tones[6],
    )
}

/**
 * 动态取色（壁纸派生）没有静态表可查：按同一 fresh green 结构现算，
 * 色相/彩度取壁纸派生的 primary（彩度截到 8，与静态表浅色同口径），锚到派生 background。
 */
private fun dynamicNeutral(scheme: ColorScheme, darkTheme: Boolean): ColorScheme {
    val steps = if (darkTheme)
        floatArrayOf(4f, 10f, 12f, 17f, 22f, 6f, 24f)
    else
        floatArrayOf(99f, 96.5f, 94.5f, 92.5f, 90.5f, 88f, 98f)
    val bg = labL(scheme.background)
    val shift = bg - (if (darkTheme) 6f else 96.5f)   // 深锚官方 tone6；浅锚卡底 Low 槽 96.5（fresh green）
    val p = scheme.primary
    val (_, pa, pb) = labOf(p.red, p.green, p.blue)
    // 深色彩度截 3（官方暗色中性度）；浅色彩度截 8（fresh green，与静态表/desat_containers 同口径）
    val chromaCap = if (darkTheme) 3.0 else 8.0
    val chroma = minOf(Math.hypot(pa.toDouble(), pb.toDouble()).toFloat(), chromaCap.toFloat())
    val hue = Math.atan2(pb.toDouble(), pa.toDouble())
    fun tone(t: Float) = labColor((t + shift).coerceIn(0f, 100f), (chroma * Math.cos(hue)).toFloat(), (chroma * Math.sin(hue)).toFloat())
    return scheme.copy(
        surfaceContainerLowest = tone(steps[0]),
        surfaceContainerLow = tone(steps[1]),
        surfaceContainer = tone(steps[2]),
        surfaceContainerHigh = tone(steps[3]),
        surfaceContainerHighest = tone(steps[4]),
        surfaceDim = tone(steps[5]),
        surfaceBright = tone(steps[6]),
    )
}

// ---- CIELAB（D65）最小实现：tone 即 L*，供动态取色现算 ----
private fun labL(color: Color): Float = labOf(color.red, color.green, color.blue)[0]

private fun labOf(r: Float, g: Float, b: Float): FloatArray {
    fun lin(c: Float) = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    fun f(t: Float) = if (t > 216f / 24389f) cbrt(t) else (24389f / 27f * t + 16f) / 116f
    val rl = lin(r); val gl = lin(g); val bl = lin(b)
    val x = (0.4124564f * rl + 0.3575761f * gl + 0.1804375f * bl) / 0.95047f
    val y = 0.2126729f * rl + 0.7151522f * gl + 0.0721750f * bl
    val z = (0.0193339f * rl + 0.1191920f * gl + 0.9503041f * bl) / 1.08883f
    val fx = f(x); val fy = f(y); val fz = f(z)
    return floatArrayOf(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
}

private fun labColor(L: Float, a: Float, b: Float): Color {
    val fy = (L + 16f) / 116f
    val fx = fy + a / 500f
    val fz = fy - b / 200f
    fun finv(t: Float): Float {
        val t3 = t * t * t
        return if (t3 > 216f / 24389f) t3 else (116f * t - 16f) / (24389f / 27f)
    }
    val x = finv(fx) * 0.95047f
    val y = finv(fy)
    val z = finv(fz) * 1.08883f
    var rl = 3.2404542f * x - 1.5371385f * y - 0.4985314f * z
    var gl = -0.9692660f * x + 1.8760108f * y + 0.0415560f * z
    var bl = 0.0556434f * x - 0.2040259f * y + 1.0572252f * z
    fun gam(c: Float): Float {
        val v = c.coerceIn(0f, 1f)
        return if (v <= 0.0031308f) 12.92f * v else 1.055f * v.pow(1f / 2.4f) - 0.055f
    }
    return Color(gam(rl), gam(gl), gam(bl))
}

//全局主题状态
private val themeTypeState: MutableState<AppTheme> by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
    // 「默认」档退役（10-12 用户令）：初始/兜底态=绿色
    mutableStateOf(AppTheme.GREEN)
}

@Composable
private fun InitTheme() {
    val theme = try {
        // 存量选了被删主题（默认/八套彩色）的兜底改选绿色（11-08 起仅剩绿色+动态两档）
        AppConfig.theme.value.takeIf { it != AppTheme.DEFAULT } ?: AppTheme.GREEN
    } catch (e: Exception) {
        e.printStackTrace()
        AppTheme.GREEN
    }
    setAppTheme(themeType = theme)
}

/**
 * 设置主题
 */
fun setAppTheme(themeType: AppTheme) {
    themeTypeState.value = themeType
    AppConfig.theme.value = themeType
}

/**
 * 获取当前主题
 */
fun getAppTheme(): AppTheme = themeTypeState.value

/**
 * 根Context
 */
@Suppress("DEPRECATION")
/**
 * 当前应走的深浅（11-10 立）：App 内设置优先于系统。
 *
 * [AppConfig.darkModePref]：0=跟随系统、1=浅色、2=深色。这是**唯一**该被界面
 * 读取的深浅判据——各页面别再直接 `isSystemInDarkTheme()`，否则用户在设置里
 * 切了深色，日志页/密钥页这些自己判断深浅的地方会不跟（改造前就是各判各的）。
 */
@Composable
fun isAppInDarkTheme(): Boolean =
    when (AppConfig.darkModePref.value.coerceIn(0, 2)) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

@Composable
fun AppTheme(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isAppInDarkTheme(),
    content: @Composable () -> Unit,
) {
    //初始化主题
    InitTheme()

    //获取当前主题
    val targetTheme = appTheme(themeType = themeTypeState.value)
    val activity = LocalView.current.context as ComponentActivity

    MaterialTheme(
        colorScheme = themeAnimation(targetTheme = targetTheme),
        typography = Typography,
        // 11-10：shapes 此前一直没传（走 M3 出厂值），于是 Dialog/BottomSheet/Menu
        // 一套圆角、手写控件另一套，11 种字面值就是这么长出来的。接上后统一到
        // AppShapes 五档；extraLarge 锁 28dp 保 27 个 AppDialog 观感零变化。
        shapes = AppMaterialShapes,
    ) {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.background,
            content = content
        )
    }
}

/**
 * 插件名（"来源标识"）的统一显示色：固定灰青，**不随主题变**。
 *
 * 11-10 用户令「参考日志里插件名的颜色，把角色列表和换声面板插件名颜色改一下」。
 * 日志页的插件牌一直是这个固定色（`LogScreen.pluginChipColor`），因为它和发音人信息、
 * 角色名一样属于**信息类别色**——一眼分清"哪个是插件/哪个是人/哪句是正文"。
 * 角色列表与换声面板原先用主题 `secondary`，换主题就变绿变紫，语义上跑偏了，
 * 故抽到此处共用，避免三处各写一份色值后再漂移。
 *
 * 浅色 #5B6472 / 深色 #9AA0A8（与 LogScreen 现行值一致）。
 */
@Composable
fun pluginChipColor(): Color {
    val dark = isAppInDarkTheme()
    return if (dark) Color(0xFF9AA0A8) else Color(0xFF5B6472)
}
