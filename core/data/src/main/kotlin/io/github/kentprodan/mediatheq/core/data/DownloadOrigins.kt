package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.database.dao.DownloadOriginDao
import io.github.kentprodan.mediatheq.core.database.entity.DownloadOriginEntity
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** What a download was started from (#168): the Downloads tab groups by it. */
sealed interface DownloadOrigin {
    data class Album(
        val id: MediaId,
    ) : DownloadOrigin

    data class Playlist(
        val id: MediaId,
    ) : DownloadOrigin

    /** The track on its own (a track menu, the Songs tab). */
    data object Single : DownloadOrigin
}

/**
 * The origins of the downloaded tracks, kept across syncs and sign-outs like the downloads. A track downloaded through
 * two collections has both.
 */
interface DownloadOrigins {
    /** Track -> its origins. */
    val origins: Flow<Map<MediaId, Set<DownloadOrigin>>>

    suspend fun add(
        tracks: List<MediaId>,
        origin: DownloadOrigin,
    )

    /** Forgets [origin]; its tracks keep their other origins. */
    suspend fun removeCollection(origin: DownloadOrigin)

    /** Forgets every origin of [tracks] (their downloads are gone). */
    suspend fun forget(tracks: List<MediaId>)
}

internal class DefaultDownloadOrigins
    @Inject
    constructor(
        private val dao: DownloadOriginDao,
    ) : DownloadOrigins {
        override val origins: Flow<Map<MediaId, Set<DownloadOrigin>>> =
            dao.all().map { rows ->
                rows
                    .groupBy({ MediaId(ServerId(it.serverId), it.trackId) }) { it.toOrigin() }
                    .mapValues { (_, origins) -> origins.filterNotNull().toSet() }
            }

        override suspend fun add(
            tracks: List<MediaId>,
            origin: DownloadOrigin,
        ) = dao.add(tracks.map { DownloadOriginEntity(it.serverId.value, it.itemId, origin.kind, origin.collectionId) })

        override suspend fun removeCollection(origin: DownloadOrigin) {
            val server =
                (origin as? DownloadOrigin.Album)?.id?.serverId ?: (origin as? DownloadOrigin.Playlist)?.id?.serverId
            // A Single origin belongs to its tracks: they are removed with forget.
            if (server != null) dao.removeCollection(origin.kind, server.value, origin.collectionId)
        }

        override suspend fun forget(tracks: List<MediaId>) =
            tracks.groupBy { it.serverId }.forEach { (server, ids) ->
                dao.removeTracks(server.value, ids.map { it.itemId })
            }

        private val DownloadOrigin.kind: String
            get() =
                when (this) {
                    is DownloadOrigin.Album -> ALBUM
                    is DownloadOrigin.Playlist -> PLAYLIST
                    DownloadOrigin.Single -> SINGLE
                }

        private val DownloadOrigin.collectionId: String
            get() =
                when (this) {
                    is DownloadOrigin.Album -> id.itemId
                    is DownloadOrigin.Playlist -> id.itemId
                    DownloadOrigin.Single -> ""
                }

        // The collection is on the track's server; an unknown kind (a newer app's) is skipped.
        private fun DownloadOriginEntity.toOrigin(): DownloadOrigin? =
            when (kind) {
                ALBUM -> DownloadOrigin.Album(MediaId(ServerId(serverId), collectionId))
                PLAYLIST -> DownloadOrigin.Playlist(MediaId(ServerId(serverId), collectionId))
                SINGLE -> DownloadOrigin.Single
                else -> null
            }

        private companion object {
            const val ALBUM = "Album"
            const val PLAYLIST = "Playlist"
            const val SINGLE = "Single"
        }
    }
