package io.github.kentprodan.mediatheq.provider.jellyfin.mapper

import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.PlaylistEntry
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.SpatialFormat
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
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
    return Artist(MediaId(serverId, id), name, sortName ?: name, artwork(), userData.toDomain())
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
        audioFormat = mediaStreams.firstOrNull { it.type == "Audio" }?.toAudioFormat(),
        sortName = sortName ?: name,
        dateAdded = instant(dateCreated),
        normalizationGainDb = normalizationGain,
        // Most tracks have no image of their own and show the album cover.
        artwork = artwork() ?: albumArtwork(),
        userData = userData.toDomain(),
    )
}

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

private fun BaseItemDto.albumArtwork(): Artwork? {
    val tag = albumPrimaryImageTag ?: return null
    return Artwork(albumId ?: return null, tag, imageBlurHashes["Primary"]?.get(tag))
}

private fun UserItemDataDto?.toDomain(): UserData =
    if (this == null) {
        UserData()
    } else {
        UserData(isFavorite, rating, playCount, instant(lastPlayedDate), played)
    }

private fun instant(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }
