package io.github.kentprodan.mediatheq.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.score.Score

/**
 * The seed color of a cover from its pixels (ARGB), the way Android picks wallpaper colors (ADR 0017); `null` when the
 * image has no usable color (e.g. pure grey), so the caller keeps Iris.
 */
fun coverSeedColor(pixels: IntArray): Int? {
    if (pixels.isEmpty()) return null
    val quantized = QuantizerCelebi.quantize(pixels, MAX_COLORS)
    return Score.score(quantized, desired = 1, fallbackColorArgb = null, filter = true).firstOrNull()
}

/** The full color scheme built from a cover's [seed] (MCU `SchemeContent`, spec 2025, ADR 0017). */
fun coverColorScheme(
    seed: Int,
    isDark: Boolean,
): ColorScheme {
    val scheme =
        SchemeContent(
            sourceColorHct = Hct.fromInt(seed),
            isDark = isDark,
            contrastLevel = 0.0,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            platform = DynamicScheme.Platform.PHONE,
        )
    val colors = MaterialDynamicColors()
    // Every role is set, so lightColorScheme's defaults never apply (also for dark schemes).
    return lightColorScheme(
        primary = Color(colors.primary().getArgb(scheme)),
        onPrimary = Color(colors.onPrimary().getArgb(scheme)),
        primaryContainer = Color(colors.primaryContainer().getArgb(scheme)),
        onPrimaryContainer = Color(colors.onPrimaryContainer().getArgb(scheme)),
        inversePrimary = Color(colors.inversePrimary().getArgb(scheme)),
        secondary = Color(colors.secondary().getArgb(scheme)),
        onSecondary = Color(colors.onSecondary().getArgb(scheme)),
        secondaryContainer = Color(colors.secondaryContainer().getArgb(scheme)),
        onSecondaryContainer = Color(colors.onSecondaryContainer().getArgb(scheme)),
        tertiary = Color(colors.tertiary().getArgb(scheme)),
        onTertiary = Color(colors.onTertiary().getArgb(scheme)),
        tertiaryContainer = Color(colors.tertiaryContainer().getArgb(scheme)),
        onTertiaryContainer = Color(colors.onTertiaryContainer().getArgb(scheme)),
        background = Color(colors.background().getArgb(scheme)),
        onBackground = Color(colors.onBackground().getArgb(scheme)),
        surface = Color(colors.surface().getArgb(scheme)),
        onSurface = Color(colors.onSurface().getArgb(scheme)),
        surfaceVariant = Color(colors.surfaceVariant().getArgb(scheme)),
        onSurfaceVariant = Color(colors.onSurfaceVariant().getArgb(scheme)),
        surfaceTint = Color(colors.surfaceTint().getArgb(scheme)),
        inverseSurface = Color(colors.inverseSurface().getArgb(scheme)),
        inverseOnSurface = Color(colors.inverseOnSurface().getArgb(scheme)),
        error = Color(colors.error().getArgb(scheme)),
        onError = Color(colors.onError().getArgb(scheme)),
        errorContainer = Color(colors.errorContainer().getArgb(scheme)),
        onErrorContainer = Color(colors.onErrorContainer().getArgb(scheme)),
        outline = Color(colors.outline().getArgb(scheme)),
        outlineVariant = Color(colors.outlineVariant().getArgb(scheme)),
        scrim = Color(colors.scrim().getArgb(scheme)),
        surfaceBright = Color(colors.surfaceBright().getArgb(scheme)),
        surfaceContainer = Color(colors.surfaceContainer().getArgb(scheme)),
        surfaceContainerHigh = Color(colors.surfaceContainerHigh().getArgb(scheme)),
        surfaceContainerHighest = Color(colors.surfaceContainerHighest().getArgb(scheme)),
        surfaceContainerLow = Color(colors.surfaceContainerLow().getArgb(scheme)),
        surfaceContainerLowest = Color(colors.surfaceContainerLowest().getArgb(scheme)),
        surfaceDim = Color(colors.surfaceDim().getArgb(scheme)),
        primaryFixed = Color(colors.primaryFixed().getArgb(scheme)),
        primaryFixedDim = Color(colors.primaryFixedDim().getArgb(scheme)),
        onPrimaryFixed = Color(colors.onPrimaryFixed().getArgb(scheme)),
        onPrimaryFixedVariant = Color(colors.onPrimaryFixedVariant().getArgb(scheme)),
        secondaryFixed = Color(colors.secondaryFixed().getArgb(scheme)),
        secondaryFixedDim = Color(colors.secondaryFixedDim().getArgb(scheme)),
        onSecondaryFixed = Color(colors.onSecondaryFixed().getArgb(scheme)),
        onSecondaryFixedVariant = Color(colors.onSecondaryFixedVariant().getArgb(scheme)),
        tertiaryFixed = Color(colors.tertiaryFixed().getArgb(scheme)),
        tertiaryFixedDim = Color(colors.tertiaryFixedDim().getArgb(scheme)),
        onTertiaryFixed = Color(colors.onTertiaryFixed().getArgb(scheme)),
        onTertiaryFixedVariant = Color(colors.onTertiaryFixedVariant().getArgb(scheme)),
    )
}

/** Every role blended between this scheme and [other]; [fraction] 0 is this scheme, 1 is [other]. */
fun ColorScheme.lerp(
    other: ColorScheme,
    fraction: Float,
): ColorScheme =
    copy(
        primary = lerp(primary, other.primary, fraction),
        onPrimary = lerp(onPrimary, other.onPrimary, fraction),
        primaryContainer = lerp(primaryContainer, other.primaryContainer, fraction),
        onPrimaryContainer = lerp(onPrimaryContainer, other.onPrimaryContainer, fraction),
        inversePrimary = lerp(inversePrimary, other.inversePrimary, fraction),
        secondary = lerp(secondary, other.secondary, fraction),
        onSecondary = lerp(onSecondary, other.onSecondary, fraction),
        secondaryContainer = lerp(secondaryContainer, other.secondaryContainer, fraction),
        onSecondaryContainer = lerp(onSecondaryContainer, other.onSecondaryContainer, fraction),
        tertiary = lerp(tertiary, other.tertiary, fraction),
        onTertiary = lerp(onTertiary, other.onTertiary, fraction),
        tertiaryContainer = lerp(tertiaryContainer, other.tertiaryContainer, fraction),
        onTertiaryContainer = lerp(onTertiaryContainer, other.onTertiaryContainer, fraction),
        background = lerp(background, other.background, fraction),
        onBackground = lerp(onBackground, other.onBackground, fraction),
        surface = lerp(surface, other.surface, fraction),
        onSurface = lerp(onSurface, other.onSurface, fraction),
        surfaceVariant = lerp(surfaceVariant, other.surfaceVariant, fraction),
        onSurfaceVariant = lerp(onSurfaceVariant, other.onSurfaceVariant, fraction),
        surfaceTint = lerp(surfaceTint, other.surfaceTint, fraction),
        inverseSurface = lerp(inverseSurface, other.inverseSurface, fraction),
        inverseOnSurface = lerp(inverseOnSurface, other.inverseOnSurface, fraction),
        error = lerp(error, other.error, fraction),
        onError = lerp(onError, other.onError, fraction),
        errorContainer = lerp(errorContainer, other.errorContainer, fraction),
        onErrorContainer = lerp(onErrorContainer, other.onErrorContainer, fraction),
        outline = lerp(outline, other.outline, fraction),
        outlineVariant = lerp(outlineVariant, other.outlineVariant, fraction),
        scrim = lerp(scrim, other.scrim, fraction),
        surfaceBright = lerp(surfaceBright, other.surfaceBright, fraction),
        surfaceContainer = lerp(surfaceContainer, other.surfaceContainer, fraction),
        surfaceContainerHigh = lerp(surfaceContainerHigh, other.surfaceContainerHigh, fraction),
        surfaceContainerHighest = lerp(surfaceContainerHighest, other.surfaceContainerHighest, fraction),
        surfaceContainerLow = lerp(surfaceContainerLow, other.surfaceContainerLow, fraction),
        surfaceContainerLowest = lerp(surfaceContainerLowest, other.surfaceContainerLowest, fraction),
        surfaceDim = lerp(surfaceDim, other.surfaceDim, fraction),
        primaryFixed = lerp(primaryFixed, other.primaryFixed, fraction),
        primaryFixedDim = lerp(primaryFixedDim, other.primaryFixedDim, fraction),
        onPrimaryFixed = lerp(onPrimaryFixed, other.onPrimaryFixed, fraction),
        onPrimaryFixedVariant = lerp(onPrimaryFixedVariant, other.onPrimaryFixedVariant, fraction),
        secondaryFixed = lerp(secondaryFixed, other.secondaryFixed, fraction),
        secondaryFixedDim = lerp(secondaryFixedDim, other.secondaryFixedDim, fraction),
        onSecondaryFixed = lerp(onSecondaryFixed, other.onSecondaryFixed, fraction),
        onSecondaryFixedVariant = lerp(onSecondaryFixedVariant, other.onSecondaryFixedVariant, fraction),
        tertiaryFixed = lerp(tertiaryFixed, other.tertiaryFixed, fraction),
        tertiaryFixedDim = lerp(tertiaryFixedDim, other.tertiaryFixedDim, fraction),
        onTertiaryFixed = lerp(onTertiaryFixed, other.onTertiaryFixed, fraction),
        onTertiaryFixedVariant = lerp(onTertiaryFixedVariant, other.onTertiaryFixedVariant, fraction),
    )

private const val MAX_COLORS = 128
