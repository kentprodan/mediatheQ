package io.github.kentprodan.mediatheq.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The app theme: Iris colors (or Material You when [useDynamicColor]), expressive motion,
 * [MediatheqTypography] and [MediatheqShapes].
 */
@Composable
fun MediatheqTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // minSdk 31 (ADR 0010), so dynamic color is always available.
    val colorScheme =
        when {
            useDynamicColor && darkTheme -> dynamicDarkColorScheme(LocalContext.current)
            useDynamicColor -> dynamicLightColorScheme(LocalContext.current)
            darkTheme -> IrisDarkColorScheme
            else -> IrisLightColorScheme
        }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = MediatheqShapes,
        typography = MediatheqTypography,
        content = content,
    )
}
