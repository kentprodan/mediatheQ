package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.database.dao.AlbumWithArtists
import io.github.kentprodan.mediatheq.core.database.dao.TrackWithArtists
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.ArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.ImageColumns
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.UserDataColumns
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

// Domain <-> Room rows. Durations and instants become epoch milliseconds only here.

internal fun Artist.toEntity(isAlbumArtist: Boolean) =
    ArtistEntity(id.serverId.value, id.itemId, name, sortName, isAlbumArtist, artwork.toColumns(), userData.toColumns())

internal fun Album.toEntity() =
    AlbumEntity(
        serverId = id.serverId.value,
        id = id.itemId,
        title = title,
        sortName = sortName,
        year = year,
        trackCount = trackCount,
        durationMillis = duration.inWholeMilliseconds,
        dateAddedMillis = dateAdded?.toEpochMilliseconds(),
        normalizationGainDb = normalizationGainDb,
        image = artwork.toColumns(),
        userData = userData.toColumns(),
    )

internal fun Album.artistLinks() =
    albumArtists.mapIndexed {
        position,
        artist,
        ->
        AlbumArtistEntity(id.serverId.value, id.itemId, position, artist.id.itemId)
    }

internal fun Track.toEntity() =
    TrackEntity(
        serverId = id.serverId.value,
        id = id.itemId,
        title = title,
        sortName = sortName,
        albumId = albumId?.itemId,
        albumTitle = albumTitle,
        trackNumber = trackNumber,
        discNumber = discNumber,
        durationMillis = duration.inWholeMilliseconds,
        dateAddedMillis = dateAdded?.toEpochMilliseconds(),
        normalizationGainDb = normalizationGainDb,
        codec = audioFormat?.codec,
        sampleRateHz = audioFormat?.sampleRateHz,
        bitDepth = audioFormat?.bitDepth,
        channels = audioFormat?.channels,
        bitrateKbps = audioFormat?.bitrateKbps,
        spatialFormat = audioFormat?.spatialFormat?.name,
        image = artwork.toColumns(),
        userData = userData.toColumns(),
    )

internal fun Track.artistLinks() =
    artists.mapIndexed {
        position,
        artist,
        ->
        TrackArtistEntity(id.serverId.value, id.itemId, position, artist.id.itemId)
    }

internal fun Playlist.toEntity() =
    PlaylistEntity(
        serverId = id.serverId.value,
        id = id.itemId,
        name = name,
        sortName = sortName,
        trackCount = trackCount,
        durationMillis = duration.inWholeMilliseconds,
        dateAddedMillis = dateAdded?.toEpochMilliseconds(),
        image = artwork.toColumns(),
        userData = userData.toColumns(),
    )

internal fun PlaylistEntry.toEntity(
    playlistId: MediaId,
    position: Int,
) = PlaylistEntryEntity(playlistId.serverId.value, playlistId.itemId, position, entryId, trackId.itemId)

internal fun AlbumWithArtists.toDomain(): Album {
    val serverId = ServerId(album.serverId)
    return Album(
        id = MediaId(serverId, album.id),
        title = album.title,
        albumArtists = credits.sortedBy { it.link.position }.mapNotNull { it.artist?.toDomain() },
        year = album.year,
        trackCount = album.trackCount,
        duration = album.durationMillis.milliseconds,
        sortName = album.sortName,
        dateAdded = album.dateAddedMillis?.let(Instant::fromEpochMilliseconds),
        normalizationGainDb = album.normalizationGainDb,
        artwork = album.image.toDomain(),
        userData = album.userData.toDomain(),
    )
}

internal fun TrackWithArtists.toDomain(): Track {
    val serverId = ServerId(track.serverId)
    return Track(
        id = MediaId(serverId, track.id),
        title = track.title,
        artists = credits.sortedBy { it.link.position }.mapNotNull { it.artist?.toDomain() },
        duration = track.durationMillis.milliseconds,
        albumId = track.albumId?.let { MediaId(serverId, it) },
        albumTitle = track.albumTitle,
        trackNumber = track.trackNumber,
        discNumber = track.discNumber,
        audioFormat =
            track.codec?.let { codec ->
                AudioFormat(
                    codec = codec,
                    sampleRateHz = track.sampleRateHz,
                    bitDepth = track.bitDepth,
                    channels = track.channels,
                    bitrateKbps = track.bitrateKbps,
                    spatialFormat = SpatialFormat.entries.firstOrNull { it.name == track.spatialFormat },
                )
            },
        sortName = track.sortName,
        dateAdded = track.dateAddedMillis?.let(Instant::fromEpochMilliseconds),
        normalizationGainDb = track.normalizationGainDb,
        artwork = track.image.toDomain(),
        userData = track.userData.toDomain(),
    )
}

internal fun PlaylistEntity.toDomain() =
    Playlist(
        id = MediaId(ServerId(serverId), id),
        name = name,
        trackCount = trackCount,
        duration = durationMillis.milliseconds,
        sortName = sortName,
        dateAdded = dateAddedMillis?.let(Instant::fromEpochMilliseconds),
        artwork = image.toDomain(),
        userData = userData.toDomain(),
    )

internal fun ArtistEntity.toDomain() =
    Artist(MediaId(ServerId(serverId), id), name, sortName, image.toDomain(), userData.toDomain())

private fun Artwork?.toColumns() = ImageColumns(this?.itemId, this?.tag, this?.blurHash)

private fun ImageColumns.toDomain(): Artwork? {
    val owner = itemId ?: return null
    return Artwork(owner, tag ?: return null, blurHash)
}

internal fun UserData.toColumns() =
    UserDataColumns(isFavorite, rating, playCount, lastPlayedAt?.toEpochMilliseconds(), played)

internal fun UserDataColumns.toDomain() =
    UserData(isFavorite, rating, playCount, lastPlayedAtMillis?.let(Instant::fromEpochMilliseconds), played)
