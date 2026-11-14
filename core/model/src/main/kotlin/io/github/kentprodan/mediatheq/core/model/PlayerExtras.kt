package io.github.kentprodan.mediatheq.core.model

import kotlin.time.Duration

/** A video's chapters and seek previews (#277); empty when the server has none or the app is offline. */
data class PlayerExtras(
    val chapters: List<Chapter> = emptyList(),
    val trickplay: TrickplayInfo? = null,
)

/** A chapter mark; [imageTag] asks for its picture, `null` when it has none. */
data class Chapter(
    val index: Int,
    val start: Duration,
    val name: String?,
    val imageTag: String? = null,
    /** Its picture's URL, filled by the repository; `null` without one. */
    val imageUrl: String? = null,
)

/**
 * The trickplay tiles of one width (#277): [tileWidth] x [tileHeight] thumbnails of [width] x [height] per tile image,
 * one every [interval], [thumbnailCount] in all.
 */
data class TrickplayInfo(
    val mediaSourceId: String,
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbnailCount: Int,
    val interval: Duration,
    /** One URL per tile image, filled by the repository; they need [headers] (the route is authenticated). */
    val tileUrls: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
) {
    /** Which tile image holds the thumbnail at [position], and where in it (#277). Pure. */
    fun cellAt(position: Duration): TrickplayCell {
        val perTile = (tileWidth * tileHeight).coerceAtLeast(1)
        val thumbnail =
            (position.inWholeMilliseconds / interval.inWholeMilliseconds.coerceAtLeast(1))
                .toInt()
                .coerceIn(0, (thumbnailCount - 1).coerceAtLeast(0))
        val cell = thumbnail % perTile
        return TrickplayCell(thumbnail / perTile, cell % tileWidth, cell / tileWidth)
    }

    /** How many tile images there are. */
    val tileCount: Int get() = (thumbnailCount + tileWidth * tileHeight - 1) / (tileWidth * tileHeight).coerceAtLeast(1)
}

/** A thumbnail's place: tile image [tile], column [column] and row [row] in it. */
data class TrickplayCell(
    val tile: Int,
    val column: Int,
    val row: Int,
)

/** The kinds of media segments the player acts on (#277); Intro Skipper's credits arrive as [Outro]. */
enum class SegmentType { Intro, Recap, Preview, Commercial, Outro }

/** What the player does when a segment starts (Settings -> Video -> Skipping, #277). */
enum class SkipMode { Off, Button, Auto }

/** A part of a video that can be skipped, from Jellyfin's media segments (#277). */
data class MediaSegment(
    val type: SegmentType,
    val start: Duration,
    val end: Duration,
)
