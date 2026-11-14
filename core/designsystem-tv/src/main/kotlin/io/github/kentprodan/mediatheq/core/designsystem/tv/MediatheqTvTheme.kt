package io.github.kentprodan.mediatheq.core.designsystem.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.materialkolor.scheme.SchemeVibrant

/**
 * Iris, the app's seed color. The same value as the phone's `MqThemeOptions.IRIS_SEED`: the two design systems share
 * tokens by value, not by module (ADR 0037), so a change of the seed is made in both.
 */
const val IRIS_SEED: Int = 0xFF5B45D6.toInt()

/**
 * The TV app's theme (ADR 0037): dark, the tv-material scheme from [seed], the tv-material type scale with the system
 * font (the phone's default font, ADR 0026; a font choice on TV comes with its settings).
 */
@Composable
fun MediatheqTvTheme(
    seed: Int = IRIS_SEED,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = tvColorScheme(seed)) {
        // tv-material's Text is black outside a Surface: text on the plain background (headers, Now Playing) reads
        // onSurface instead.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
    }
}

/** The dark tv-material scheme from [seed], with the phone's generator (vibrant, spec 2025): same seed, same colors. */
fun tvColorScheme(seed: Int): ColorScheme {
    val scheme =
        SchemeVibrant(
            Hct.fromInt(seed),
            isDark = true,
            contrastLevel = 0.0,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            platform = DynamicScheme.Platform.PHONE,
        )
    val colors = MaterialDynamicColors()

    fun role(color: DynamicColor) = Color(color.getArgb(scheme))
    return darkColorScheme(
        primary = role(colors.primary()),
        onPrimary = role(colors.onPrimary()),
        primaryContainer = role(colors.primaryContainer()),
        onPrimaryContainer = role(colors.onPrimaryContainer()),
        inversePrimary = role(colors.inversePrimary()),
        secondary = role(colors.secondary()),
        onSecondary = role(colors.onSecondary()),
        secondaryContainer = role(colors.secondaryContainer()),
        onSecondaryContainer = role(colors.onSecondaryContainer()),
        tertiary = role(colors.tertiary()),
        onTertiary = role(colors.onTertiary()),
        tertiaryContainer = role(colors.tertiaryContainer()),
        onTertiaryContainer = role(colors.onTertiaryContainer()),
        background = role(colors.background()),
        onBackground = role(colors.onBackground()),
        surface = role(colors.surface()),
        onSurface = role(colors.onSurface()),
        surfaceVariant = role(colors.surfaceVariant()),
        onSurfaceVariant = role(colors.onSurfaceVariant()),
        surfaceTint = role(colors.surfaceTint()),
        inverseSurface = role(colors.inverseSurface()),
        inverseOnSurface = role(colors.inverseOnSurface()),
        error = role(colors.error()),
        onError = role(colors.onError()),
        errorContainer = role(colors.errorContainer()),
        onErrorContainer = role(colors.onErrorContainer()),
        border = role(colors.outline()),
        borderVariant = role(colors.outlineVariant()),
        scrim = role(colors.scrim()),
    )
}
