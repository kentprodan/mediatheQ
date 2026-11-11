package io.github.kentprodan.mediatheq.feature.downloads

import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import kotlin.time.Duration

/**
 * The Downloads tab from the [downloads] (in album order): unfinished ones in their own list, finished ones under each
 * collection they were downloaded through ([albums] and [playlists] give the names and covers; a collection gone from
 * the library keeps a plain name), and alone under songs.
 */
internal fun downloadsContent(
    downloads: List<TrackDownload>,
    albums: Map<MediaId, Album>,
    playlists: Map<MediaId, Playlist>,
    freeBytes: Long,
): DownloadsUiState.Content {
    val (done, downloading) = downloads.partition { it.state == DownloadState.Completed }

    // Grouped by the collection, whether its origin is manual or automatic (#145); automatic when no manual one.
    fun <O : DownloadOrigin> byOrigin(
        type: Class<O>,
        id: (O) -> MediaId,
    ): Map<MediaId, Pair<List<TrackDownload>, Boolean>> =
        done
            .flatMap { download -> download.origins.filterIsInstance(type).map { it to download } }
            .groupBy({ id(it.first) })
            .mapValues { (_, pairs) -> pairs.map { it.second }.distinct() to pairs.all { it.first.auto } }
    return DownloadsUiState.Content(
        totals = DownloadTotals(done.sumOf { it.bytes }, done.duration(), freeBytes),
        downloading = downloading,
        albums =
            byOrigin(DownloadOrigin.Album::class.java) { it.id }
                .map { (id, grouped) ->
                    val (tracks, auto) = grouped
                    val album =
                        albums[id] ?: Album(
                            id,
                            tracks
                                .first()
                                .track.albumTitle
                                .orEmpty(),
                            emptyList(),
                        )
                    DownloadedAlbum(album, tracks.map { it.track }, tracks.sumOf { it.bytes }, tracks.duration(), auto)
                }.sortedBy { it.album.sortName.lowercase() },
        playlists =
            byOrigin(DownloadOrigin.Playlist::class.java) { it.id }
                .map { (id, grouped) ->
                    val (tracks, auto) = grouped
                    // shortcut: tracks in album order, not the playlist's; read the playlist's items if it matters.
                    DownloadedPlaylist(
                        playlists[id] ?: Playlist(id, ""),
                        tracks.map { it.track },
                        tracks.sumOf { it.bytes },
                        tracks.duration(),
                        auto,
                    )
                }.sortedBy { it.playlist.sortName.lowercase() },
        songs =
            done
                .filter { DownloadOrigin.Single in it.origins || DownloadOrigin.Favorite in it.origins }
                .sortedBy { it.track.sortName.lowercase() },
    )
}

private fun List<TrackDownload>.duration(): Duration = fold(Duration.ZERO) { total, it -> total + it.track.duration }
