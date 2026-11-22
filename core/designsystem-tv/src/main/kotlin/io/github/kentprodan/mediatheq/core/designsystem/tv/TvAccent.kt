package io.github.kentprodan.mediatheq.core.designsystem.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score

/**
 * The accent of a poster or cover from its pixels (#517, content-based color): the most suitable color, the way the
 * phone takes a cover's (ADR 0017, by value); `null` when the picture has no usable color (the caller keeps Iris).
 */
fun posterSeedColor(pixels: IntArray): Int? {
    if (pixels.isEmpty()) return null
    val quantized = QuantizerCelebi.quantize(pixels, MAX_COLORS)
    return Score.score(quantized, desired = 1, fallbackColorArgb = null, filter = true).firstOrNull()
}

/**
 * [content] in the TV theme built from [seed] (#517, Settings -> Display -> Accent from the poster): its accent and a
 * background barely tinted by it (the theme's neutral surfaces keep the seed's hue at a very low chroma). Without a
 * seed, the app's theme as it is.
 */
@Composable
fun MqTvAccentTheme(
    seed: Int?,
    content: @Composable () -> Unit,
) {
    if (seed == null) {
        content()
    } else {
        MediatheqTvTheme(seed) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
}

private const val MAX_COLORS = 128
