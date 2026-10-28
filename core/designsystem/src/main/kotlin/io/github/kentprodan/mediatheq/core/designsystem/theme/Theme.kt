package io.github.kentprodan.mediatheq.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The app theme: colors per [options] ([appColorScheme]), expressive motion, [MediatheqTypography] and
 * [MediatheqShapes]. A color change blends app-wide unless [MqThemeOptions.instantColors].
 */
@Composable
fun MediatheqTheme(
    options: MqThemeOptions = MqThemeOptions(),
    systemDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val state = MqThemeState(options, options.isDark(systemDark), options.isBlack(systemDark))
    // Kept across recompositions: schemes compare by identity, and a new one would restart the blend.
    val target =
        remember(options.colorSource, options.customSeed, state.isDark, state.isBlack) {
            // minSdk 31 (ADR 0010), so dynamic color is always available.
            appColorScheme(options, state.isDark, state.isBlack) { dark ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
        }
    val motionScheme = MotionScheme.expressive()
    val colorScheme = animateColorScheme(target, motionScheme.defaultEffectsSpec(), options.instantColors)
    CompositionLocalProvider(LocalMqTheme provides state) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = motionScheme,
            shapes = MediatheqShapes,
            typography = MediatheqTypography,
            content = content,
        )
    }
}
