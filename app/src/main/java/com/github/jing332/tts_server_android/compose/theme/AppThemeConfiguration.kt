package com.github.jing332.tts_server_android.compose.theme

import android.content.Context
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/* ============================================================================
 * 每套静态主题 → ColorScheme（11-10 重做）
 *
 * 八套主题的槽位全部由同一张 OKLCH 表推出（生成器=临时文件 gen2.py），
 * 所以这里每个函数长得一模一样，只有前缀不同——**别在这手改色值**，
 * 要调就改 Color2.kt / SurfaceTones.kt 的表，两边是同一批数。
 *
 * 与旧版的区别：旧版 lightColorScheme 的 primary 取 `*_seed`、
 * 但表里还留着一份零引用的 `*_md_theme_light_primary`（误导）；本次只留 seed 一份。
 * ========================================================================== */

/**
 * 动态颜色主题（Android 12 以下无壁纸取色 API，回落绿色——与默认静态主题同观感）
 */
@Composable
fun dynamicColorTheme(
    darkTheme: Boolean,
    context: Context = LocalContext.current
): ColorScheme =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    else
        greenTheme(darkTheme)

/**
 * 绿色
 */
@Composable
fun greenTheme(
    darkTheme: Boolean
): ColorScheme =
    if (!darkTheme)
        lightColorScheme(
            primary = green_seed,
            onPrimary = Color(0xFFFFFFFF),
            error = md_theme_light_error,
            onError = md_theme_light_onError,
            errorContainer = md_theme_light_errorContainer,
            onErrorContainer = md_theme_light_onErrorContainer,
            surfaceTint = green_seed,
            scrim = md_theme_scrim,
            primaryContainer = green_md_theme_light_primaryContainer,
            onPrimaryContainer = green_md_theme_light_onPrimaryContainer,
            secondary = green_md_theme_light_secondary,
            onSecondary = green_md_theme_light_onSecondary,
            secondaryContainer = green_md_theme_light_secondaryContainer,
            onSecondaryContainer = green_md_theme_light_onSecondaryContainer,
            tertiary = green_md_theme_light_tertiary,
            onTertiary = green_md_theme_light_onTertiary,
            tertiaryContainer = green_md_theme_light_tertiaryContainer,
            onTertiaryContainer = green_md_theme_light_onTertiaryContainer,
            background = green_md_theme_light_background,
            onBackground = green_md_theme_light_onBackground,
            surface = green_md_theme_light_surface,
            onSurface = green_md_theme_light_onSurface,
            surfaceVariant = green_md_theme_light_surfaceVariant,
            onSurfaceVariant = green_md_theme_light_onSurfaceVariant,
            outline = green_md_theme_light_outline,
            outlineVariant = green_md_theme_light_outlineVariant,
            inverseSurface = green_md_theme_light_inverseSurface,
            inverseOnSurface = green_md_theme_light_inverseOnSurface,
            inversePrimary = green_md_theme_light_inversePrimary,
        )
    else
        darkColorScheme(
            primary = green_md_theme_dark_primary,
            onPrimary = green_md_theme_dark_onPrimary,
            primaryContainer = green_md_theme_dark_primaryContainer,
            onPrimaryContainer = green_md_theme_dark_onPrimaryContainer,
            secondary = green_md_theme_dark_secondary,
            onSecondary = green_md_theme_dark_onSecondary,
            secondaryContainer = green_md_theme_dark_secondaryContainer,
            onSecondaryContainer = green_md_theme_dark_onSecondaryContainer,
            tertiary = green_md_theme_dark_tertiary,
            onTertiary = green_md_theme_dark_onTertiary,
            tertiaryContainer = green_md_theme_dark_tertiaryContainer,
            onTertiaryContainer = green_md_theme_dark_onTertiaryContainer,
            background = green_md_theme_dark_background,
            onBackground = green_md_theme_dark_onBackground,
            surface = green_md_theme_dark_surface,
            onSurface = green_md_theme_dark_onSurface,
            surfaceVariant = green_md_theme_dark_surfaceVariant,
            onSurfaceVariant = green_md_theme_dark_onSurfaceVariant,
            outline = green_md_theme_dark_outline,
            outlineVariant = green_md_theme_dark_outlineVariant,
            inverseSurface = green_md_theme_dark_inverseSurface,
            inverseOnSurface = green_md_theme_dark_inverseOnSurface,
            inversePrimary = green_md_theme_dark_inversePrimary,
            error = md_theme_dark_error,
            onError = md_theme_dark_onError,
            errorContainer = md_theme_dark_errorContainer,
            onErrorContainer = md_theme_dark_onErrorContainer,
            surfaceTint = green_md_theme_dark_primary,
            scrim = md_theme_scrim,
        )

/**
 * 紫色
 */
@Composable
fun purpleTheme(
    darkTheme: Boolean
): ColorScheme =
    if (!darkTheme)
        lightColorScheme(
            primary = purple_seed,
            onPrimary = Color(0xFFFFFFFF),
            error = md_theme_light_error,
            onError = md_theme_light_onError,
            errorContainer = md_theme_light_errorContainer,
            onErrorContainer = md_theme_light_onErrorContainer,
            surfaceTint = purple_seed,
            scrim = md_theme_scrim,
            primaryContainer = purple_md_theme_light_primaryContainer,
            onPrimaryContainer = purple_md_theme_light_onPrimaryContainer,
            secondary = purple_md_theme_light_secondary,
            onSecondary = purple_md_theme_light_onSecondary,
            secondaryContainer = purple_md_theme_light_secondaryContainer,
            onSecondaryContainer = purple_md_theme_light_onSecondaryContainer,
            tertiary = purple_md_theme_light_tertiary,
            onTertiary = purple_md_theme_light_onTertiary,
            tertiaryContainer = purple_md_theme_light_tertiaryContainer,
            onTertiaryContainer = purple_md_theme_light_onTertiaryContainer,
            background = purple_md_theme_light_background,
            onBackground = purple_md_theme_light_onBackground,
            surface = purple_md_theme_light_surface,
            onSurface = purple_md_theme_light_onSurface,
            surfaceVariant = purple_md_theme_light_surfaceVariant,
            onSurfaceVariant = purple_md_theme_light_onSurfaceVariant,
            outline = purple_md_theme_light_outline,
            outlineVariant = purple_md_theme_light_outlineVariant,
            inverseSurface = purple_md_theme_light_inverseSurface,
            inverseOnSurface = purple_md_theme_light_inverseOnSurface,
            inversePrimary = purple_md_theme_light_inversePrimary,
        )
    else
        darkColorScheme(
            primary = purple_md_theme_dark_primary,
            onPrimary = purple_md_theme_dark_onPrimary,
            primaryContainer = purple_md_theme_dark_primaryContainer,
            onPrimaryContainer = purple_md_theme_dark_onPrimaryContainer,
            secondary = purple_md_theme_dark_secondary,
            onSecondary = purple_md_theme_dark_onSecondary,
            secondaryContainer = purple_md_theme_dark_secondaryContainer,
            onSecondaryContainer = purple_md_theme_dark_onSecondaryContainer,
            tertiary = purple_md_theme_dark_tertiary,
            onTertiary = purple_md_theme_dark_onTertiary,
            tertiaryContainer = purple_md_theme_dark_tertiaryContainer,
            onTertiaryContainer = purple_md_theme_dark_onTertiaryContainer,
            background = purple_md_theme_dark_background,
            onBackground = purple_md_theme_dark_onBackground,
            surface = purple_md_theme_dark_surface,
            onSurface = purple_md_theme_dark_onSurface,
            surfaceVariant = purple_md_theme_dark_surfaceVariant,
            onSurfaceVariant = purple_md_theme_dark_onSurfaceVariant,
            outline = purple_md_theme_dark_outline,
            outlineVariant = purple_md_theme_dark_outlineVariant,
            inverseSurface = purple_md_theme_dark_inverseSurface,
            inverseOnSurface = purple_md_theme_dark_inverseOnSurface,
            inversePrimary = purple_md_theme_dark_inversePrimary,
            error = md_theme_dark_error,
            onError = md_theme_dark_onError,
            errorContainer = md_theme_dark_errorContainer,
            onErrorContainer = md_theme_dark_onErrorContainer,
            surfaceTint = purple_md_theme_dark_primary,
            scrim = md_theme_scrim,
        )

/**
 * 蓝色
 */
@Composable
fun mistTheme(
    darkTheme: Boolean
): ColorScheme =
    if (!darkTheme)
        lightColorScheme(
            primary = mist_seed,
            onPrimary = Color(0xFFFFFFFF),
            error = md_theme_light_error,
            onError = md_theme_light_onError,
            errorContainer = md_theme_light_errorContainer,
            onErrorContainer = md_theme_light_onErrorContainer,
            surfaceTint = mist_seed,
            scrim = md_theme_scrim,
            primaryContainer = mist_md_theme_light_primaryContainer,
            onPrimaryContainer = mist_md_theme_light_onPrimaryContainer,
            secondary = mist_md_theme_light_secondary,
            onSecondary = mist_md_theme_light_onSecondary,
            secondaryContainer = mist_md_theme_light_secondaryContainer,
            onSecondaryContainer = mist_md_theme_light_onSecondaryContainer,
            tertiary = mist_md_theme_light_tertiary,
            onTertiary = mist_md_theme_light_onTertiary,
            tertiaryContainer = mist_md_theme_light_tertiaryContainer,
            onTertiaryContainer = mist_md_theme_light_onTertiaryContainer,
            background = mist_md_theme_light_background,
            onBackground = mist_md_theme_light_onBackground,
            surface = mist_md_theme_light_surface,
            onSurface = mist_md_theme_light_onSurface,
            surfaceVariant = mist_md_theme_light_surfaceVariant,
            onSurfaceVariant = mist_md_theme_light_onSurfaceVariant,
            outline = mist_md_theme_light_outline,
            outlineVariant = mist_md_theme_light_outlineVariant,
            inverseSurface = mist_md_theme_light_inverseSurface,
            inverseOnSurface = mist_md_theme_light_inverseOnSurface,
            inversePrimary = mist_md_theme_light_inversePrimary,
        )
    else
        darkColorScheme(
            primary = mist_md_theme_dark_primary,
            onPrimary = mist_md_theme_dark_onPrimary,
            primaryContainer = mist_md_theme_dark_primaryContainer,
            onPrimaryContainer = mist_md_theme_dark_onPrimaryContainer,
            secondary = mist_md_theme_dark_secondary,
            onSecondary = mist_md_theme_dark_onSecondary,
            secondaryContainer = mist_md_theme_dark_secondaryContainer,
            onSecondaryContainer = mist_md_theme_dark_onSecondaryContainer,
            tertiary = mist_md_theme_dark_tertiary,
            onTertiary = mist_md_theme_dark_onTertiary,
            tertiaryContainer = mist_md_theme_dark_tertiaryContainer,
            onTertiaryContainer = mist_md_theme_dark_onTertiaryContainer,
            background = mist_md_theme_dark_background,
            onBackground = mist_md_theme_dark_onBackground,
            surface = mist_md_theme_dark_surface,
            onSurface = mist_md_theme_dark_onSurface,
            surfaceVariant = mist_md_theme_dark_surfaceVariant,
            onSurfaceVariant = mist_md_theme_dark_onSurfaceVariant,
            outline = mist_md_theme_dark_outline,
            outlineVariant = mist_md_theme_dark_outlineVariant,
            inverseSurface = mist_md_theme_dark_inverseSurface,
            inverseOnSurface = mist_md_theme_dark_inverseOnSurface,
            inversePrimary = mist_md_theme_dark_inversePrimary,
            error = md_theme_dark_error,
            onError = md_theme_dark_onError,
            errorContainer = md_theme_dark_errorContainer,
            onErrorContainer = md_theme_dark_onErrorContainer,
            surfaceTint = mist_md_theme_dark_primary,
            scrim = md_theme_scrim,
        )

/**
 * 深灰
 */
@Composable
fun graphiteTheme(
    darkTheme: Boolean
): ColorScheme =
    if (!darkTheme)
        lightColorScheme(
            primary = graphite_seed,
            onPrimary = Color(0xFFFFFFFF),
            error = md_theme_light_error,
            onError = md_theme_light_onError,
            errorContainer = md_theme_light_errorContainer,
            onErrorContainer = md_theme_light_onErrorContainer,
            surfaceTint = graphite_seed,
            scrim = md_theme_scrim,
            primaryContainer = graphite_md_theme_light_primaryContainer,
            onPrimaryContainer = graphite_md_theme_light_onPrimaryContainer,
            secondary = graphite_md_theme_light_secondary,
            onSecondary = graphite_md_theme_light_onSecondary,
            secondaryContainer = graphite_md_theme_light_secondaryContainer,
            onSecondaryContainer = graphite_md_theme_light_onSecondaryContainer,
            tertiary = graphite_md_theme_light_tertiary,
            onTertiary = graphite_md_theme_light_onTertiary,
            tertiaryContainer = graphite_md_theme_light_tertiaryContainer,
            onTertiaryContainer = graphite_md_theme_light_onTertiaryContainer,
            background = graphite_md_theme_light_background,
            onBackground = graphite_md_theme_light_onBackground,
            surface = graphite_md_theme_light_surface,
            onSurface = graphite_md_theme_light_onSurface,
            surfaceVariant = graphite_md_theme_light_surfaceVariant,
            onSurfaceVariant = graphite_md_theme_light_onSurfaceVariant,
            outline = graphite_md_theme_light_outline,
            outlineVariant = graphite_md_theme_light_outlineVariant,
            inverseSurface = graphite_md_theme_light_inverseSurface,
            inverseOnSurface = graphite_md_theme_light_inverseOnSurface,
            inversePrimary = graphite_md_theme_light_inversePrimary,
        )
    else
        darkColorScheme(
            primary = graphite_md_theme_dark_primary,
            onPrimary = graphite_md_theme_dark_onPrimary,
            primaryContainer = graphite_md_theme_dark_primaryContainer,
            onPrimaryContainer = graphite_md_theme_dark_onPrimaryContainer,
            secondary = graphite_md_theme_dark_secondary,
            onSecondary = graphite_md_theme_dark_onSecondary,
            secondaryContainer = graphite_md_theme_dark_secondaryContainer,
            onSecondaryContainer = graphite_md_theme_dark_onSecondaryContainer,
            tertiary = graphite_md_theme_dark_tertiary,
            onTertiary = graphite_md_theme_dark_onTertiary,
            tertiaryContainer = graphite_md_theme_dark_tertiaryContainer,
            onTertiaryContainer = graphite_md_theme_dark_onTertiaryContainer,
            background = graphite_md_theme_dark_background,
            onBackground = graphite_md_theme_dark_onBackground,
            surface = graphite_md_theme_dark_surface,
            onSurface = graphite_md_theme_dark_onSurface,
            surfaceVariant = graphite_md_theme_dark_surfaceVariant,
            onSurfaceVariant = graphite_md_theme_dark_onSurfaceVariant,
            outline = graphite_md_theme_dark_outline,
            outlineVariant = graphite_md_theme_dark_outlineVariant,
            inverseSurface = graphite_md_theme_dark_inverseSurface,
            inverseOnSurface = graphite_md_theme_dark_inverseOnSurface,
            inversePrimary = graphite_md_theme_dark_inversePrimary,
            error = md_theme_dark_error,
            onError = md_theme_dark_onError,
            errorContainer = md_theme_dark_errorContainer,
            onErrorContainer = md_theme_dark_onErrorContainer,
            surfaceTint = graphite_md_theme_dark_primary,
            scrim = md_theme_scrim,
        )

/**
 * 主题颜色状态管理
 */
@Composable
fun themeAnimation(targetTheme: ColorScheme): ColorScheme {
    //动画时长
    val durationMillis = 600
    //插值器
    val animationSpec = TweenSpec<Color>(durationMillis = durationMillis, easing = FastOutLinearInEasing)

    val primary by animateColorAsState(
        targetValue = targetTheme.primary, animationSpec, label = "primary"
    )
    val onPrimary by animateColorAsState(
        targetValue = targetTheme.onPrimary, animationSpec, label = "onPrimary"
    )
    val primaryContainer by animateColorAsState(
        targetValue = targetTheme.primaryContainer, animationSpec, label = "primaryContainer"
    )
    val onPrimaryContainer by animateColorAsState(
        targetValue = targetTheme.onPrimaryContainer, animationSpec, label = "onPrimaryContainer"
    )
    val secondary by animateColorAsState(
        targetValue = targetTheme.secondary, animationSpec, label = "secondary"
    )
    val onSecondary by animateColorAsState(
        targetValue = targetTheme.onSecondary, animationSpec, label = "onSecondary"
    )
    val secondaryContainer by animateColorAsState(
        targetValue = targetTheme.secondaryContainer, animationSpec, label = "secondaryContainer"
    )
    val onSecondaryContainer by animateColorAsState(
        targetValue = targetTheme.onSecondaryContainer, animationSpec, label = "onSecondaryContainer"
    )
    val tertiary by animateColorAsState(
        targetValue = targetTheme.tertiary, animationSpec, label = "tertiary"
    )
    val onTertiary by animateColorAsState(
        targetValue = targetTheme.onTertiary, animationSpec, label = "onTertiary"
    )
    val tertiaryContainer by animateColorAsState(
        targetValue = targetTheme.tertiaryContainer, animationSpec, label = "tertiaryContainer"
    )
    val onTertiaryContainer by animateColorAsState(
        targetValue = targetTheme.onTertiaryContainer, animationSpec, label = "onTertiaryContainer"
    )
    val error by animateColorAsState(
        targetValue = targetTheme.error, animationSpec, label = "error"
    )
    val errorContainer by animateColorAsState(
        targetValue = targetTheme.errorContainer, animationSpec, label = "errorContainer"
    )
    val onError by animateColorAsState(
        targetValue = targetTheme.onError, animationSpec, label = "onError"
    )
    val onErrorContainer by animateColorAsState(
        targetValue = targetTheme.onErrorContainer, animationSpec, label = "onErrorContainer"
    )
    val background by animateColorAsState(
        targetValue = targetTheme.background, animationSpec, label = "background"
    )
    val onBackground by animateColorAsState(
        targetValue = targetTheme.onBackground, animationSpec, label = "onBackground"
    )
    val outline by animateColorAsState(
        targetValue = targetTheme.outline, animationSpec, label = "outline"
    )
    val inverseOnSurface by animateColorAsState(
        targetValue = targetTheme.inverseOnSurface, animationSpec, label = "inverseOnSurface"
    )
    val inverseSurface by animateColorAsState(
        targetValue = targetTheme.inverseSurface, animationSpec, label = "inverseSurface"
    )
    val inversePrimary by animateColorAsState(
        targetValue = targetTheme.inversePrimary, animationSpec, label = "inversePrimary"
    )
    val surfaceTint by animateColorAsState(
        targetValue = targetTheme.surfaceTint, animationSpec, label = "surfaceTint"
    )
    val outlineVariant by animateColorAsState(
        targetValue = targetTheme.outlineVariant, animationSpec, label = "outlineVariant"
    )
    val scrim by animateColorAsState(
        targetValue = targetTheme.scrim, animationSpec, label = "scrim"
    )
    val surface by animateColorAsState(
        targetValue = targetTheme.surface, animationSpec, label = "surface"
    )
    val onSurface by animateColorAsState(
        targetValue = targetTheme.onSurface, animationSpec, label = "onSurface"
    )
    val surfaceVariant by animateColorAsState(
        targetValue = targetTheme.surfaceVariant, animationSpec, label = "surfaceVariant"
    )
    val onSurfaceVariant by animateColorAsState(
        targetValue = targetTheme.onSurfaceVariant, animationSpec, label = "onSurfaceVariant"
    )
    // M3 1.2 新增的 7 个中性槽：值由 Theme.kt 的 themedNeutral() 按各主题色相派生，不在这套旧 token 里。
    // 它们原先恒定（库默认），不参与动画也看不出；现在跟随主题 ⇒ 不一起做过渡就会在切主题时跳变。
    val surfaceContainerLow by animateColorAsState(
        targetValue = targetTheme.surfaceContainerLow, animationSpec, label = "surfaceContainerLow"
    )
    val surfaceContainer by animateColorAsState(
        targetValue = targetTheme.surfaceContainer, animationSpec, label = "surfaceContainer"
    )
    val surfaceContainerHigh by animateColorAsState(
        targetValue = targetTheme.surfaceContainerHigh, animationSpec, label = "surfaceContainerHigh"
    )
    val surfaceContainerHighest by animateColorAsState(
        targetValue = targetTheme.surfaceContainerHighest, animationSpec, label = "surfaceContainerHighest"
    )
    val surfaceDim by animateColorAsState(
        targetValue = targetTheme.surfaceDim, animationSpec, label = "surfaceDim"
    )
    val surfaceBright by animateColorAsState(
        targetValue = targetTheme.surfaceBright, animationSpec, label = "surfaceBright"
    )
    val surfaceContainerLowest by animateColorAsState(
        targetValue = targetTheme.surfaceContainerLowest, animationSpec, label = "surfaceContainerLowest"
    )

    return targetTheme.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        error = error,
        errorContainer = errorContainer,
        onError = onError,
        onErrorContainer = onErrorContainer,
        background = background,
        onBackground = onBackground,
        outline = outline,
        inverseOnSurface = inverseOnSurface,
        inverseSurface = inverseSurface,
        inversePrimary = inversePrimary,
        surfaceTint = surfaceTint,
        outlineVariant = outlineVariant,
        scrim = scrim,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceDim = surfaceDim,
        surfaceBright = surfaceBright,
        surfaceContainerLowest = surfaceContainerLowest,
    )
}
