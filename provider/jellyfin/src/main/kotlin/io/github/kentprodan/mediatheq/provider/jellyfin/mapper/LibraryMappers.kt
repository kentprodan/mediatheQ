package io.github.kentprodan.mediatheq.provider.jellyfin.mapper

import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.Genre
import io.github.kentprodan.mediatheq.core.model.ImageType
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.PlaylistEntry
import io.github.kentprodan.mediatheq.core.model.RecordLabel
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.SpatialFormat
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoFormat
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.model.VideoRange
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.BaseItemDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.MediaStreamDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.NameIdPairDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.UserItemDataDto
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

// Every mapper returns null for an item without an id or name: it cannot be stored or shown.

internal fun BaseItemDto.toArtist(serverId: ServerId): Artist? {
    val (id, name) = idAndName() ?: return null
    return Artist(
        MediaId(serverId, id),
        name,
        sortName ?: name,
        artwork(),
        userData.toDomain(),
        backdrop = backdrop(),
        overview = overview?.trim()?.takeIf { it.isNotEmpty() },
    )
}

internal fun BaseItemDto.toAlbum(serverId: ServerId): Album? {
    val (id, name) = idAndName() ?: return null
    return Album(
        id = MediaId(serverId, id),
        title = name,
        albumArtists = albumArtists.mapNotNull { it.toArtist(serverId) },
        year = productionYear,
        trackCount = childCount ?: 0,
        duration = ticksToDuration(runTimeTicks),
        sortName = sortName ?: name,
        dateAdded = instant(dateCreated),
        normalizationGainDb = normalizationGain,
        artwork = artwork(),
        userData = userData.toDomain(),
        genres = genres(serverId),
        labels = labels(serverId),
        overview = overview?.trim()?.takeIf { it.isNotEmpty() },
        releaseDate = instant(premiereDate),
    )
}

internal fun BaseItemDto.toTrack(serverId: ServerId): Track? {
    val (id, name) = idAndName() ?: return null
    return Track(
        id = MediaId(serverId, id),
        title = name,
        artists = artistItems.mapNotNull { it.toArtist(serverId) },
        duration = ticksToDuration(runTimeTicks),
        albumId = albumId?.takeIf { it.isNotEmpty() }?.let { MediaId(serverId, it) },
        albumTitle = album,
        trackNumber = indexNumber,
        discNumber = parentIndexNumber,
        audioFormat =
            mediaSources
                .firstOrNull()
                ?.mediaStreams
                ?.firstOrNull { it.type == "Audio" }
                ?.toAudioFormat(),
        sizeBytes = mediaSources.firstOrNull()?.size,
        sortName = sortName ?: name,
        dateAdded = instant(dateCreated),
        normalizationGainDb = normalizationGain,
        // Most tracks have no image of their own and show the album cover.
        artwork = artwork() ?: albumArtwork(),
        userData = userData.toDomain(),
        genres = genres(serverId),
    )
}

/** A movie or an episode (#270); an episode without its series id is dropped (it could not be listed anywhere). */
internal fun BaseItemDto.toVideo(
    serverId: ServerId,
    kind: VideoKind,
): Video? {
    val (id, name) = idAndName() ?: return null
    val episode = kind == VideoKind.Episode
    val series = seriesId?.takeIf { it.isNotEmpty() }?.let { MediaId(serverId, it) }
    if (episode && series == null) return null
    val source = mediaSources.firstOrNull()
    val streams = source?.mediaStreams.orEmpty()
    return Video(
        id = MediaId(serverId, id),
        kind = kind,
        title = name,
        sortName = sortName ?: name,
        overview = overview?.trim()?.takeIf { it.isNotEmpty() },
        year = productionYear,
        premiereDate = instant(premiereDate),
        dateAdded = instant(dateCreated),
        duration = ticksToDuration(runTimeTicks),
        seriesId = series.takeIf { episode },
        seriesName = seriesName.takeIf { episode },
        seasonId = seasonId?.takeIf { episode && it.isNotEmpty() }?.let { MediaId(serverId, it) },
        seasonNumber = parentIndexNumber.takeIf { episode },
        episodeNumber = indexNumber.takeIf { episode },
        genres = genreNames(),
        tagline = taglines.firstOrNull { it.isNotBlank() },
        officialRating = officialRating?.takeIf { it.isNotBlank() },
        format = streams.firstOrNull { it.type == "Video" }?.toVideoFormat(),
        sizeBytes = source?.size,
        artwork = artwork(),
        backdrop = backdrop(),
        userData = userData.toDomain(),
        streams = streams.mapNotNull { it.toStreamInfo() },
    )
}

internal fun BaseItemDto.toSeries(serverId: ServerId): Series? {
    val (id, name) = idAndName() ?: return null
    return Series(
        id = MediaId(serverId, id),
        title = name,
        sortName = sortName ?: name,
        overview = overview?.trim()?.takeIf { it.isNotEmpty() },
        year = productionYear,
        dateAdded = instant(dateCreated),
        genres = genreNames(),
        artwork = artwork(),
        backdrop = backdrop(),
        userData = userData.toDomain(),
    )
}

/** A season without its own image shows the series poster; one without its series id is dropped. */
internal fun BaseItemDto.toSeason(serverId: ServerId): Season? {
    val (id, name) = idAndName() ?: return null
    val series = seriesId?.takeIf { it.isNotEmpty() } ?: return null
    return Season(
        id = MediaId(serverId, id),
        seriesId = MediaId(serverId, series),
        title = name,
        number = indexNumber,
        artwork =
            artwork() ?: seriesPrimaryImageTag?.let { Artwork(series, it, imageBlurHashes["Primary"]?.get(it)) },
        userData = userData.toDomain(),
    )
}

private fun BaseItemDto.genreNames(): List<String> =
    genreItems.mapNotNull { pair -> pair.name?.takeIf { it.isNotBlank() } }.distinct()

internal fun MediaStreamDto.toVideoFormat(): VideoFormat? {
    val codec = codec?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    val range =
        when {
            videoRangeType?.startsWith("DOVI") == true -> VideoRange.DolbyVision
            videoRangeType == "HDR10Plus" -> VideoRange.Hdr10Plus
            videoRangeType == "HDR10" -> VideoRange.Hdr10
            videoRangeType == "HLG" -> VideoRange.Hlg
            else -> VideoRange.Sdr
        }
    return VideoFormat(codec, width, height, range, bitRate?.let { it / 1000 })
}

/** Audio and subtitle streams only; one without an index cannot be picked, so it is dropped. */
internal fun MediaStreamDto.toStreamInfo(): MediaStreamInfo? {
    val streamType =
        when (type) {
            "Audio" -> StreamType.Audio
            "Subtitle" -> StreamType.Subtitle
            else -> return null
        }
    return MediaStreamInfo(
        index = index ?: return null,
        type = streamType,
        codec = codec?.lowercase(),
        language = language?.takeIf { it.isNotBlank() && it != "und" },
        title = title?.takeIf { it.isNotBlank() },
        displayTitle = displayTitle?.takeIf { it.isNotBlank() },
        channels = channels,
        isDefault = isDefault,
        isForced = isForced,
        isExternal = isExternal,
    )
}

/** The album's record labels, like [genres]; MusicBrainz's "[no label]" placeholder is not a label (R18). */
private fun BaseItemDto.labels(serverId: ServerId): List<RecordLabel> =
    studios
        .mapNotNull { pair ->
            val id = pair.id?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = pair.name?.takeIf { it.isNotBlank() && it != NO_LABEL } ?: return@mapNotNull null
            RecordLabel(MediaId(serverId, id), name)
        }.distinctBy { it.id }

private const val NO_LABEL = "[no label]"

/** The item's genres; entries without an id or a name are skipped, and a genre listed twice counts once. */
private fun BaseItemDto.genres(serverId: ServerId): List<Genre> =
    genreItems
        .mapNotNull { pair ->
            val id = pair.id?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = pair.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Genre(MediaId(serverId, id), name)
        }.distinctBy { it.id }

internal fun BaseItemDto.toPlaylist(serverId: ServerId): Playlist? {
    val (id, name) = idAndName() ?: return null
    return Playlist(
        id = MediaId(serverId, id),
        name = name,
        trackCount = childCount ?: 0,
        duration = ticksToDuration(runTimeTicks),
        sortName = sortName ?: name,
        dateAdded = instant(dateCreated),
        artwork = artwork(),
        userData = userData.toDomain(),
    )
}

/** Entries without their own entry id are dropped: removing by the track id would hit the wrong position. */
internal fun BaseItemDto.toPlaylistEntry(serverId: ServerId): PlaylistEntry? {
    val trackId = id?.takeIf { it.isNotEmpty() } ?: return null
    val entryId = playlistItemId?.takeIf { it.isNotEmpty() } ?: return null
    return PlaylistEntry(entryId = entryId, trackId = MediaId(serverId, trackId))
}

/** Jellyfin durations are 100 ns ticks (jellyfin skill, section 5). */
internal fun ticksToDuration(ticks: Long?): Duration = ((ticks ?: 0) / 10_000).milliseconds

internal fun MediaStreamDto.toAudioFormat(): AudioFormat? {
    val codec = codec?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return AudioFormat(
        codec = codec,
        sampleRateHz = sampleRate,
        bitDepth = bitDepth,
        channels = channels,
        bitrateKbps = bitRate?.let { it / 1000 },
        spatialFormat =
            when (audioSpatialFormat) {
                "DolbyAtmos" -> SpatialFormat.DolbyAtmos
                "DTSX" -> SpatialFormat.DtsX
                else -> null
            },
    )
}

private fun BaseItemDto.idAndName(): Pair<String, String>? {
    val id = id?.takeIf { it.isNotEmpty() && ':' !in it } ?: return null
    return id to (name ?: return null)
}

private fun NameIdPairDto.toArtist(serverId: ServerId): Artist? {
    val id = id?.takeIf { it.isNotEmpty() } ?: return null
    return Artist(MediaId(serverId, id), name.orEmpty())
}

private fun BaseItemDto.artwork(): Artwork? {
    val tag = imageTags["Primary"] ?: return null
    return Artwork(id ?: return null, tag, imageBlurHashes["Primary"]?.get(tag))
}

private fun BaseItemDto.backdrop(): Artwork? {
    val tag = backdropImageTags.firstOrNull() ?: return null
    return Artwork(id ?: return null, tag, imageBlurHashes["Backdrop"]?.get(tag), ImageType.Backdrop)
}

private fun BaseItemDto.albumArtwork(): Artwork? {
    val tag = albumPrimaryImageTag ?: return null
    return Artwork(albumId ?: return null, tag, imageBlurHashes["Primary"]?.get(tag))
}

/** A rating of 0 is how a cleared rating comes back (R14), so it maps to unrated. */
internal fun UserItemDataDto?.toDomain(): UserData =
    if (this == null) {
        UserData()
    } else {
        UserData(
            isFavorite,
            rating?.takeIf { it > 0 },
            playCount,
            instant(lastPlayedDate),
            played,
            ticksToDuration(playbackPositionTicks),
            playedPercentage,
            unplayedItemCount,
        )
    }

private fun instant(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }
