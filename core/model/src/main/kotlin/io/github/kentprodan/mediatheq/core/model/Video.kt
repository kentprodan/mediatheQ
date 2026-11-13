package io.github.kentprodan.mediatheq.core.model

import kotlin.time.Duration
import kotlin.time.Instant

enum class VideoKind { Movie, Episode }

/** The dynamic range of a video stream; Dolby Vision wins over the HDR10 or HLG base layer it carries. */
enum class VideoRange { Sdr, Hdr10, Hdr10Plus, Hlg, DolbyVision }

/** The format of a video's one video stream (ADR 0048: folded into the video row). */
data class VideoFormat(
    val codec: String,
    val width: Int? = null,
    val height: Int? = null,
    val range: VideoRange = VideoRange.Sdr,
    val bitrateKbps: Int? = null,
)

enum class StreamType { Audio, Subtitle }

/** An audio or subtitle stream of a video, by its index in the file (subtitle files beside it included). */
data class MediaStreamInfo(
    val index: Int,
    val type: StreamType,
    val codec: String? = null,
    val language: String? = null,
    val title: String? = null,
    val displayTitle: String? = null,
    val channels: Int? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false,
)

/** A movie or an episode (one table, ADR 0048); the series fields are `null` for a movie. */
data class Video(
    val id: MediaId,
    val kind: VideoKind,
    val title: String,
    val sortName: String = title,
    val overview: String? = null,
    val year: Int? = null,
    val premiereDate: Instant? = null,
    val dateAdded: Instant? = null,
    val duration: Duration = Duration.ZERO,
    val seriesId: MediaId? = null,
    val seriesName: String? = null,
    val seasonId: MediaId? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val genres: List<String> = emptyList(),
    val tagline: String? = null,
    val officialRating: String? = null,
    val format: VideoFormat? = null,
    val sizeBytes: Long? = null,
    val artwork: Artwork? = null,
    val backdrop: Artwork? = null,
    val userData: UserData = UserData(),
    /** Filled by the sync and by `VideoRepository.streams`; list reads leave it empty. */
    val streams: List<MediaStreamInfo> = emptyList(),
)

data class Series(
    val id: MediaId,
    val title: String,
    val sortName: String = title,
    val overview: String? = null,
    val year: Int? = null,
    val dateAdded: Instant? = null,
    val genres: List<String> = emptyList(),
    val artwork: Artwork? = null,
    val backdrop: Artwork? = null,
    val userData: UserData = UserData(),
)

data class Season(
    val id: MediaId,
    val seriesId: MediaId,
    val title: String,
    val number: Int? = null,
    val artwork: Artwork? = null,
    val userData: UserData = UserData(),
)
