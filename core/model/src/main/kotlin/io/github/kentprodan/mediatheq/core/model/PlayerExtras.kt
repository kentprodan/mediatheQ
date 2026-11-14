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
)

/** The kinds of media segments the player acts on (#277); Intro Skipper's credits arrive as [Outro]. */
enum class SegmentType { Intro, Recap, Preview, Commercial, Outro }

/** A part of a video that can be skipped, from Jellyfin's media segments (#277). */
data class MediaSegment(
    val type: SegmentType,
    val start: Duration,
    val end: Duration,
)
