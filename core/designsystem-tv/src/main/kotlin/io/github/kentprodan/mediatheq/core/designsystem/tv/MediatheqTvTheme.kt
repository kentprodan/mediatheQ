package io.github.kentprodan.mediatheq.core.designsystem.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeTonalSpot

/**
 * Iris, the app's seed color. The same value as the phone's `MqThemeOptions.IRIS_SEED`: the two design systems share
 * tokens by value, not by module (ADR 0037), so a change of the seed is made in both.
 */
const val IRIS_SEED: Int = 0xFF5B45D6.toInt()

/**
 * The TV app's theme (ADR 0037, #517): dark, neutral surfaces with [seed] as the accent, the tv-material type scale with the system
 * font (the phone's default font, ADR 0026; a font choice on TV comes with its settings).
 */
@Composable
fun MediatheqTvTheme(
    seed: Int = IRIS_SEED,
    content: @Composable () -> Unit,
) {
    // Built once per seed: two dynamic schemes and some thirty colors.
    val scheme = remember(seed) { tvColorScheme(seed) }
    MaterialTheme(colorScheme = scheme) {
        // tv-material's Text is black outside a Surface: text on the plain background (headers, Now Playing) reads
        // onSurface instead.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
    }
}

/**
 * The dark tv-material scheme (#517, Google's TV color guidance): neutral surfaces, background and secondary roles
 * (an almost colorless scheme), [seed] only as the accent of the primary roles (the active entry, the primary button,
 * progress), so the app is not dominated by one color.
 */
fun tvColorScheme(seed: Int): ColorScheme {
    fun scheme(neutral: Boolean) =
        if (neutral) {
            SchemeNeutral(
                Hct.fromInt(seed),
                isDark = true,
                contrastLevel = 0.0,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
                platform = DynamicScheme.Platform.PHONE,
            )
        } else {
            SchemeTonalSpot(
                Hct.fromInt(seed),
                isDark = true,
                contrastLevel = 0.0,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
                platform = DynamicScheme.Platform.PHONE,
            )
        }
    val accent = scheme(neutral = false)
    val neutral = scheme(neutral = true)
    val colors = MaterialDynamicColors()

    fun accent(color: DynamicColor) = Color(color.getArgb(accent))

    fun neutral(color: DynamicColor) = Color(color.getArgb(neutral))
    return darkColorScheme(
        primary = accent(colors.primary()),
        onPrimary = accent(colors.onPrimary()),
        primaryContainer = accent(colors.primaryContainer()),
        onPrimaryContainer = accent(colors.onPrimaryContainer()),
        inversePrimary = accent(colors.inversePrimary()),
        secondary = neutral(colors.secondary()),
        onSecondary = neutral(colors.onSecondary()),
        secondaryContainer = neutral(colors.secondaryContainer()),
        onSecondaryContainer = neutral(colors.onSecondaryContainer()),
        tertiary = neutral(colors.tertiary()),
        onTertiary = neutral(colors.onTertiary()),
        tertiaryContainer = neutral(colors.tertiaryContainer()),
        onTertiaryContainer = neutral(colors.onTertiaryContainer()),
        background = neutral(colors.background()),
        onBackground = neutral(colors.onBackground()),
        surface = neutral(colors.surface()),
        onSurface = neutral(colors.onSurface()),
        surfaceVariant = neutral(colors.surfaceVariant()),
        onSurfaceVariant = neutral(colors.onSurfaceVariant()),
        // No tint of elevated surfaces by the accent.
        surfaceTint = neutral(colors.surfaceTint()),
        inverseSurface = neutral(colors.inverseSurface()),
        inverseOnSurface = neutral(colors.inverseOnSurface()),
        error = accent(colors.error()),
        onError = accent(colors.onError()),
        errorContainer = accent(colors.errorContainer()),
        onErrorContainer = accent(colors.onErrorContainer()),
        border = neutral(colors.outline()),
        borderVariant = neutral(colors.outlineVariant()),
        scrim = neutral(colors.scrim()),
    )
}
