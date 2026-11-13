package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.database.entity.SeasonEntity
import io.github.kentprodan.mediatheq.core.database.entity.SeriesEntity
import io.github.kentprodan.mediatheq.core.database.entity.UserDataColumns
import io.github.kentprodan.mediatheq.core.database.entity.VideoEntity
import io.github.kentprodan.mediatheq.core.database.entity.VideoStreamEntity
import io.github.kentprodan.mediatheq.core.database.entity.VideoUserColumns
import io.github.kentprodan.mediatheq.core.model.ImageType
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoFormat
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.model.VideoRange
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

// Video domain <-> Room rows (#270), like LibraryEntityMappers. Genres are names, one per line (ADR 0048).

private fun List<String>.joinedGenres() = takeIf { it.isNotEmpty() }?.joinToString("\n")

private fun String?.splitGenres() = this?.split("\n")?.filter { it.isNotEmpty() }.orEmpty()

private fun UserData.toVideoColumns() =
    VideoUserColumns(playbackPosition.inWholeMilliseconds, playedPercentage, unplayedCount)

private fun userData(
    columns: UserDataColumns,
    video: VideoUserColumns,
) = columns.toDomain().copy(
    playbackPosition = video.positionMillis.milliseconds,
    playedPercentage = video.playedPercentage,
    unplayedCount = video.unplayedCount,
)

internal fun Video.toEntity() =
    VideoEntity(
        serverId = id.serverId.value,
        id = id.itemId,
        kind = kind.name,
        title = title,
        sortName = sortName,
        overview = overview,
        year = year,
        premiereDateMillis = premiereDate?.toEpochMilliseconds(),
        dateAddedMillis = dateAdded?.toEpochMilliseconds(),
        durationMillis = duration.inWholeMilliseconds,
        seriesId = seriesId?.itemId,
        seriesName = seriesName,
        seasonId = seasonId?.itemId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        genres = genres.joinedGenres(),
        tagline = tagline,
        officialRating = officialRating,
        videoCodec = format?.codec,
        width = format?.width,
        height = format?.height,
        videoRange = format?.range?.name,
        bitrateKbps = format?.bitrateKbps,
        sizeBytes = sizeBytes,
        image = artwork.toColumns(),
        backdrop = backdrop.toColumns(),
        userData = userData.toColumns(),
        videoUser = userData.toVideoColumns(),
    )

internal fun Video.streamRows() =
    streams.map {
        VideoStreamEntity(
            id.serverId.value,
            id.itemId,
            it.index,
            it.type.name,
            it.codec,
            it.language,
            it.title,
            it.displayTitle,
            it.channels,
            it.isDefault,
            it.isForced,
            it.isExternal,
        )
    }

internal fun VideoEntity.toDomain(): Video {
    val server = ServerId(serverId)
    return Video(
        id = MediaId(server, id),
        kind = VideoKind.entries.firstOrNull { it.name == kind } ?: VideoKind.Movie,
        title = title,
        sortName = sortName,
        overview = overview,
        year = year,
        premiereDate = premiereDateMillis?.let(Instant::fromEpochMilliseconds),
        dateAdded = dateAddedMillis?.let(Instant::fromEpochMilliseconds),
        duration = durationMillis.milliseconds,
        seriesId = seriesId?.let { MediaId(server, it) },
        seriesName = seriesName,
        seasonId = seasonId?.let { MediaId(server, it) },
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        genres = genres.splitGenres(),
        tagline = tagline,
        officialRating = officialRating,
        format =
            videoCodec?.let { codec ->
                VideoFormat(
                    codec,
                    width,
                    height,
                    VideoRange.entries.firstOrNull { it.name == videoRange } ?: VideoRange.Sdr,
                    bitrateKbps,
                )
            },
        sizeBytes = sizeBytes,
        artwork = image.toDomain(),
        backdrop = backdrop.toDomain(ImageType.Backdrop),
        userData = userData(userData, videoUser),
    )
}

internal fun VideoStreamEntity.toDomain(): MediaStreamInfo? =
    MediaStreamInfo(
        index = streamIndex,
        type = StreamType.entries.firstOrNull { it.name == type } ?: return null,
        codec = codec,
        language = language,
        title = title,
        displayTitle = displayTitle,
        channels = channels,
        isDefault = isDefault,
        isForced = isForced,
        isExternal = isExternal,
    )

internal fun Series.toEntity() =
    SeriesEntity(
        serverId = id.serverId.value,
        id = id.itemId,
        title = title,
        sortName = sortName,
        overview = overview,
        year = year,
        dateAddedMillis = dateAdded?.toEpochMilliseconds(),
        genres = genres.joinedGenres(),
        image = artwork.toColumns(),
        backdrop = backdrop.toColumns(),
        userData = userData.toColumns(),
        videoUser = userData.toVideoColumns(),
    )

internal fun SeriesEntity.toDomain() =
    Series(
        id = MediaId(ServerId(serverId), id),
        title = title,
        sortName = sortName,
        overview = overview,
        year = year,
        dateAdded = dateAddedMillis?.let(Instant::fromEpochMilliseconds),
        genres = genres.splitGenres(),
        artwork = image.toDomain(),
        backdrop = backdrop.toDomain(ImageType.Backdrop),
        userData = userData(userData, videoUser),
    )

internal fun Season.toEntity() =
    SeasonEntity(
        serverId = id.serverId.value,
        id = id.itemId,
        seriesId = seriesId.itemId,
        title = title,
        number = number,
        image = artwork.toColumns(),
        userData = userData.toColumns(),
        videoUser = userData.toVideoColumns(),
    )

internal fun SeasonEntity.toDomain(): Season {
    val server = ServerId(serverId)
    return Season(
        id = MediaId(server, id),
        seriesId = MediaId(server, seriesId),
        title = title,
        number = number,
        artwork = image.toDomain(),
        userData = userData(userData, videoUser),
    )
}
