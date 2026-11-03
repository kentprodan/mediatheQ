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

    /** The tallest a list inside a dialog grows before it scrolls (navigation entries). */
    val dialogListMaxHeight = 420.dp

    /** An icon among a track row's trailing details (favorite, downloaded, rating stars). */
    val trackDetailIcon = 14.dp

    /** A favorite or downloaded mark on a cover. */
    val coverOverlayIcon = 16.dp

    /** The playback position bar: waveform, wavy or plain slider, the same height so none jumps (#124). */
    val waveformHeight = 48.dp

    /** Now Playing's cover (and the lyrics in its place) at most this wide. */
    val playerCoverMax = 360.dp

    /** A landscape window at least this tall (a tablet) has room for Now Playing's rating stars (#125). */
    val playerRatingMinHeight = 560.dp

    /** A Home carousel's large cover (#127): its preferred width and the carousel's height. */
    val carouselItem = 220.dp

    /** A card in a Home row (artists, playlists; albums use [albumCardInRow]). */
    val homeCard = 128.dp
}
