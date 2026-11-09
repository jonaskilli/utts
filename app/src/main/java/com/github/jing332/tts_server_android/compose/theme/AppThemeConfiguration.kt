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

/**
 * 动态颜色主题（Android 12 以下无壁纸取色 API，回落绿色——11-08 起仅剩绿色+动态两档）
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
            onPrimary = green_md_theme_light_onPrimary,
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
            error = green_md_theme_light_error,
            errorContainer = green_md_theme_light_errorContainer,
            onError = green_md_theme_light_onError,
            onErrorContainer = green_md_theme_light_onErrorContainer,
            background = green_md_theme_light_background,
            onBackground = green_md_theme_light_onBackground,
            outline = green_md_theme_light_outline,
            inverseOnSurface = green_md_theme_light_inverseOnSurface,
            inverseSurface = green_md_theme_light_inverseSurface,
            inversePrimary = green_md_theme_light_inversePrimary,
            surfaceTint = green_md_theme_light_surfaceTint,
            outlineVariant = green_md_theme_light_outlineVariant,
            scrim = green_md_theme_light_scrim,
            surface = green_md_theme_light_surface,
            onSurface = green_md_theme_light_onSurface,
            surfaceVariant = green_md_theme_light_surfaceVariant,
            onSurfaceVariant = green_md_theme_light_onSurfaceVariant,
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
            error = green_md_theme_dark_error,
            errorContainer = green_md_theme_dark_errorContainer,
            onError = green_md_theme_dark_onError,
            onErrorContainer = green_md_theme_dark_onErrorContainer,
            background = green_md_theme_dark_background,
            onBackground = green_md_theme_dark_onBackground,
            outline = green_md_theme_dark_outline,
            inverseOnSurface = green_md_theme_dark_inverseOnSurface,
            inverseSurface = green_md_theme_dark_inverseSurface,
            inversePrimary = green_md_theme_dark_inversePrimary,
            surfaceTint = green_md_theme_dark_surfaceTint,
            outlineVariant = green_md_theme_dark_outlineVariant,
            scrim = green_md_theme_dark_scrim,
            surface = green_md_theme_dark_surface,
            onSurface = green_md_theme_dark_onSurface,
            surfaceVariant = green_md_theme_dark_surfaceVariant,
            onSurfaceVariant = green_md_theme_dark_onSurfaceVariant,
        )

/**
 * 粉色
 */
@Composable
fun pinkTheme(
    darkTheme: Boolean
): ColorScheme =
    if (!darkTheme)
        lightColorScheme(
            primary = pink_seed,
            onPrimary = pink_md_theme_light_onPrimary,
            primaryContainer = pink_md_theme_light_primaryContainer,
            onPrimaryContainer = pink_md_theme_light_onPrimaryContainer,
            secondary = pink_md_theme_light_secondary,
            onSecondary = pink_md_theme_light_onSecondary,
            secondaryContainer = pink_md_theme_light_secondaryContainer,
            onSecondaryContainer = pink_md_theme_light_onSecondaryContainer,
            tertiary = pink_md_theme_light_tertiary,
            onTertiary = pink_md_theme_light_onTertiary,
            tertiaryContainer = pink_md_theme_light_tertiaryContainer,
            onTertiaryContainer = pink_md_theme_light_onTertiaryContainer,
            error = pink_md_theme_light_error,
            errorContainer = pink_md_theme_light_errorContainer,
            onError = pink_md_theme_light_onError,
            onErrorContainer = pink_md_theme_light_onErrorContainer,
            background = pink_md_theme_light_background,
            onBackground = pink_md_theme_light_onBackground,
            outline = pink_md_theme_light_outline,
            inverseOnSurface = pink_md_theme_light_inverseOnSurface,
            inverseSurface = pink_md_theme_light_inverseSurface,
            inversePrimary = pink_md_theme_light_inversePrimary,
            surfaceTint = pink_md_theme_light_surfaceTint,
            outlineVariant = pink_md_theme_light_outlineVariant,
            scrim = pink_md_theme_light_scrim,
            surface = pink_md_theme_light_surface,
            onSurface = pink_md_theme_light_onSurface,
            surfaceVariant = pink_md_theme_light_surfaceVariant,
            onSurfaceVariant = pink_md_theme_light_onSurfaceVariant,
        )
    else
        darkColorScheme(
            primary = pink_md_theme_dark_primary,
            onPrimary = pink_md_theme_dark_onPrimary,
            primaryContainer = pink_md_theme_dark_primaryContainer,
            onPrimaryContainer = pink_md_theme_dark_onPrimaryContainer,
            secondary = pink_md_theme_dark_secondary,
            onSecondary = pink_md_theme_dark_onSecondary,
            secondaryContainer = pink_md_theme_dark_secondaryContainer,
            onSecondaryContainer = pink_md_theme_dark_onSecondaryContainer,
            tertiary = pink_md_theme_dark_tertiary,
            onTertiary = pink_md_theme_dark_onTertiary,
            tertiaryContainer = pink_md_theme_dark_tertiaryContainer,
            onTertiaryContainer = pink_md_theme_dark_onTertiaryContainer,
            error = pink_md_theme_dark_error,
            errorContainer = pink_md_theme_dark_errorContainer,
            onError = pink_md_theme_dark_onError,
            onErrorContainer = pink_md_theme_dark_onErrorContainer,
            background = pink_md_theme_dark_background,
            onBackground = pink_md_theme_dark_onBackground,
            outline = pink_md_theme_dark_outline,
            inverseOnSurface = pink_md_theme_dark_inverseOnSurface,
            inverseSurface = pink_md_theme_dark_inverseSurface,
            inversePrimary = pink_md_theme_dark_inversePrimary,
            surfaceTint = pink_md_theme_dark_surfaceTint,
            outlineVariant = pink_md_theme_dark_outlineVariant,
            scrim = pink_md_theme_dark_scrim,
            surface = pink_md_theme_dark_surface,
            onSurface = pink_md_theme_dark_onSurface,
            surfaceVariant = pink_md_theme_dark_surfaceVariant,
            onSurfaceVariant = pink_md_theme_dark_onSurfaceVariant,
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
            onPrimary = purple_md_theme_light_onPrimary,
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
            error = purple_md_theme_light_error,
            errorContainer = purple_md_theme_light_errorContainer,
            onError = purple_md_theme_light_onError,
            onErrorContainer = purple_md_theme_light_onErrorContainer,
            background = purple_md_theme_light_background,
            onBackground = purple_md_theme_light_onBackground,
            outline = purple_md_theme_light_outline,
            inverseOnSurface = purple_md_theme_light_inverseOnSurface,
            inverseSurface = purple_md_theme_light_inverseSurface,
            inversePrimary = purple_md_theme_light_inversePrimary,
            surfaceTint = purple_md_theme_light_surfaceTint,
            outlineVariant = purple_md_theme_light_outlineVariant,
            scrim = purple_md_theme_light_scrim,
            surface = purple_md_theme_light_surface,
            onSurface = purple_md_theme_light_onSurface,
            surfaceVariant = purple_md_theme_light_surfaceVariant,
            onSurfaceVariant = purple_md_theme_light_onSurfaceVariant,
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
            error = purple_md_theme_dark_error,
            errorContainer = purple_md_theme_dark_errorContainer,
            onError = purple_md_theme_dark_onError,
            onErrorContainer = purple_md_theme_dark_onErrorContainer,
            background = purple_md_theme_dark_background,
            onBackground = purple_md_theme_dark_onBackground,
            outline = purple_md_theme_dark_outline,
            inverseOnSurface = purple_md_theme_dark_inverseOnSurface,
            inverseSurface = purple_md_theme_dark_inverseSurface,
            inversePrimary = purple_md_theme_dark_inversePrimary,
            surfaceTint = purple_md_theme_dark_surfaceTint,
            outlineVariant = purple_md_theme_dark_outlineVariant,
            scrim = purple_md_theme_dark_scrim,
            surface = purple_md_theme_dark_surface,
            onSurface = purple_md_theme_dark_onSurface,
            surfaceVariant = purple_md_theme_dark_surfaceVariant,
            onSurfaceVariant = purple_md_theme_dark_onSurfaceVariant,
        )
