package io.github.kentprodan.mediatheq.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The app scheme for [options] when the theme [isDark] and [isBlack]: Iris, Material You ([dynamic] gives the
 * wallpaper scheme, Android 12+) or a scheme generated from the custom seed (`SchemeVibrant`, like Iris).
 */
fun appColorScheme(
    options: MqThemeOptions,
    isDark: Boolean,
    isBlack: Boolean,
    dynamic: (isDark: Boolean) -> ColorScheme,
): ColorScheme {
    val scheme =
        when (options.colorSource) {
            MqColorSource.Iris -> if (isDark) IrisDarkColorScheme else IrisLightColorScheme
            MqColorSource.MaterialYou -> dynamic(isDark)
            MqColorSource.Custom -> coverColorScheme(options.customSeed, isDark, vibrant = true)
        }
    return if (isBlack) scheme.blackened() else scheme
}

/**
 * The player scheme of a cover's [colors] under [theme]: `null` (the app theme) without colors or with cover colors
 * off. Black keeps the player black, unless background and surfaces come from the app theme anyway.
 */
fun playerColorScheme(
    colors: CoverColors?,
    theme: MqThemeState,
): ColorScheme? {
    val options = theme.options
    if (colors == null || options.playerColors == MqPlayerColors.Off) return null
    val scheme = colors.scheme(theme.isDark, vibrant = options.playerColors == MqPlayerColors.CoverVibrant)
    return if (theme.isBlack && !options.ignoreSurfaces) scheme.blackened() else scheme
}

/** Black mode (OLED): background and the base surfaces pure black; containers keep their tones, so cards still show. */
fun ColorScheme.blackened(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
    )

/** This scheme's accents with [app]'s background and surface roles ("ignore background and surfaces"). */
fun ColorScheme.withSurfacesOf(app: ColorScheme): ColorScheme =
    copy(
        background = app.background,
        onBackground = app.onBackground,
        surface = app.surface,
        onSurface = app.onSurface,
        surfaceVariant = app.surfaceVariant,
        onSurfaceVariant = app.onSurfaceVariant,
        inverseSurface = app.inverseSurface,
        inverseOnSurface = app.inverseOnSurface,
        surfaceBright = app.surfaceBright,
        surfaceDim = app.surfaceDim,
        surfaceContainer = app.surfaceContainer,
        surfaceContainerHigh = app.surfaceContainerHigh,
        surfaceContainerHighest = app.surfaceContainerHighest,
        surfaceContainerLow = app.surfaceContainerLow,
        surfaceContainerLowest = app.surfaceContainerLowest,
    )
