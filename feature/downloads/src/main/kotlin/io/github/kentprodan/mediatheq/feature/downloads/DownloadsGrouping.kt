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

    fun <O : DownloadOrigin> byOrigin(type: Class<O>) =
        done
            .flatMap { download -> download.origins.filterIsInstance(type).map { it to download } }
            .groupBy({ it.first }, { it.second })
    return DownloadsUiState.Content(
        totals = DownloadTotals(done.sumOf { it.bytes }, done.duration(), freeBytes),
        downloading = downloading,
        albums =
            byOrigin(DownloadOrigin.Album::class.java)
                .map { (origin, tracks) ->
                    val album =
                        albums[origin.id] ?: Album(
                            origin.id,
                            tracks
                                .first()
                                .track.albumTitle
                                .orEmpty(),
                            emptyList(),
                        )
                    DownloadedAlbum(album, tracks.map { it.track }, tracks.sumOf { it.bytes }, tracks.duration())
                }.sortedBy { it.album.sortName.lowercase() },
        playlists =
            byOrigin(DownloadOrigin.Playlist::class.java)
                .map { (origin, tracks) ->
                    // shortcut: tracks in album order, not the playlist's; read the playlist's items if it matters.
                    DownloadedPlaylist(
                        playlists[origin.id] ?: Playlist(origin.id, ""),
                        tracks.map { it.track },
                        tracks.sumOf { it.bytes },
                        tracks.duration(),
                    )
                }.sortedBy { it.playlist.sortName.lowercase() },
        songs = done.filter { DownloadOrigin.Single in it.origins }.sortedBy { it.track.sortName.lowercase() },
    )
}

private fun List<TrackDownload>.duration(): Duration = fold(Duration.ZERO) { total, it -> total + it.track.duration }
