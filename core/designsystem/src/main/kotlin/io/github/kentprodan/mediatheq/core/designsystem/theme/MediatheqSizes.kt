package io.github.kentprodan.mediatheq.core.designsystem.theme

import androidx.compose.ui.unit.dp

/** Size tokens for fixed-size elements (expressive-design skill, section 2). */
object MediatheqSizes {
    /** Width of an album card in a horizontal row (Home): about two and a half cards on a phone hint at scrolling. */
    val albumCardInRow = 144.dp

    /** An audio format badge (stereo, Dolby Atmos, ...) before a track's artists. */
    val formatBadge = 18.dp

    /** A color swatch (Settings -> Theme, font and colors). */
    val colorSwatch = 40.dp

    /** The outline of a [colorSwatch], so a swatch in the surface's own color still shows. */
    val swatchOutline = 1.dp
}
