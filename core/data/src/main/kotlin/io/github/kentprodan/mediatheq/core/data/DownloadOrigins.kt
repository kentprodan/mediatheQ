package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.database.dao.DownloadOriginDao
import io.github.kentprodan.mediatheq.core.database.entity.DownloadOriginEntity
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * What a download was started from (#168): the Downloads tab groups by it. An [auto] origin is a favorite's automatic
 * download (#145), a different origin from the manual one: removing it keeps what the manual one holds.
 */
sealed interface DownloadOrigin {
    val auto: Boolean get() = false

    data class Album(
        val id: MediaId,
        override val auto: Boolean = false,
    ) : DownloadOrigin

    data class Playlist(
        val id: MediaId,
        override val auto: Boolean = false,
    ) : DownloadOrigin

    /** The track on its own (a track menu, the Songs tab). */
    data object Single : DownloadOrigin

    /** A favorite track, downloaded by itself (#145). */
    data object Favorite : DownloadOrigin {
        override val auto: Boolean get() = true
    }
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

    /** Forgets [origin] for [tracks] only (e.g. a song removed from the Songs list). */
    suspend fun remove(
        tracks: List<MediaId>,
        origin: DownloadOrigin,
    )

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
            // Single and Favorite origins belong to their tracks: they are removed per track.
            if (server != null) dao.removeCollection(origin.kind, server.value, origin.collectionId)
        }

        override suspend fun remove(
            tracks: List<MediaId>,
            origin: DownloadOrigin,
        ) = tracks.forEach { dao.removeOrigin(it.serverId.value, it.itemId, origin.kind, origin.collectionId) }

        override suspend fun forget(tracks: List<MediaId>) =
            tracks.groupBy { it.serverId }.forEach { (server, ids) ->
                dao.removeTracks(server.value, ids.map { it.itemId })
            }

        private val DownloadOrigin.kind: String
            get() =
                when (this) {
                    is DownloadOrigin.Album -> if (auto) AUTO_ALBUM else ALBUM
                    is DownloadOrigin.Playlist -> if (auto) AUTO_PLAYLIST else PLAYLIST
                    DownloadOrigin.Single -> SINGLE
                    DownloadOrigin.Favorite -> FAVORITE
                }

        private val DownloadOrigin.collectionId: String
            get() =
                when (this) {
                    is DownloadOrigin.Album -> id.itemId
                    is DownloadOrigin.Playlist -> id.itemId
                    DownloadOrigin.Single, DownloadOrigin.Favorite -> ""
                }

        // The collection is on the track's server; an unknown kind (a newer app's) is skipped.
        private fun DownloadOriginEntity.toOrigin(): DownloadOrigin? =
            when (kind) {
                ALBUM -> DownloadOrigin.Album(MediaId(ServerId(serverId), collectionId))
                PLAYLIST -> DownloadOrigin.Playlist(MediaId(ServerId(serverId), collectionId))
                SINGLE -> DownloadOrigin.Single
                AUTO_ALBUM -> DownloadOrigin.Album(MediaId(ServerId(serverId), collectionId), auto = true)
                AUTO_PLAYLIST -> DownloadOrigin.Playlist(MediaId(ServerId(serverId), collectionId), auto = true)
                FAVORITE -> DownloadOrigin.Favorite
                else -> null
            }

        private companion object {
            const val ALBUM = "Album"
            const val PLAYLIST = "Playlist"
            const val SINGLE = "Single"

            // Automatic downloads of favorites (#145); an older app skips these kinds.
            const val AUTO_ALBUM = "AutoAlbum"
            const val AUTO_PLAYLIST = "AutoPlaylist"
            const val FAVORITE = "Favorite"
        }
    }
