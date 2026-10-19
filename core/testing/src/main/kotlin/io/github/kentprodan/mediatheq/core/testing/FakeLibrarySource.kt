package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.PlaylistEntry
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.server.LibraryPage
import io.github.kentprodan.mediatheq.core.server.LibrarySource
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import kotlinx.coroutines.CompletableDeferred

/**
 * [LibrarySource] over in-memory lists. [calls] records every request as "albums@500"; [failAt] fails the call with
 * that number (1-based) with [failure]; [pauseAt] suspends that call until [resume] completes.
 */
class FakeLibrarySource : LibrarySource {
    var albumArtists: List<Artist> = emptyList()
    var albums: List<Album> = emptyList()
    var tracks: List<Track> = emptyList()
    var playlists: List<Playlist> = emptyList()
    var entries: Map<MediaId, List<PlaylistEntry>> = emptyMap()

    val calls = mutableListOf<String>()
    var failAt: Int? = null
    var failure: MqError = MqError.Network
    var pauseAt: Int? = null
    val paused = CompletableDeferred<Unit>()
    val resume = CompletableDeferred<Unit>()

    override suspend fun albumArtists(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ) = page("albumArtists", albumArtists, startIndex, limit)

    override suspend fun albums(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ) = page("albums", albums, startIndex, limit)

    override suspend fun tracks(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ) = page("tracks", tracks, startIndex, limit)

    override suspend fun playlists(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ) = page("playlists", playlists, startIndex, limit)

    override suspend fun playlistEntries(
        server: SignedInServer,
        playlistId: MediaId,
        startIndex: Int,
        limit: Int,
    ) = page("entries:${playlistId.itemId}", entries[playlistId].orEmpty(), startIndex, limit)

    private suspend fun <T> page(
        name: String,
        all: List<T>,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<T>> {
        calls += "$name@$startIndex"
        if (calls.size == pauseAt) {
            paused.complete(Unit)
            resume.await()
        }
        if (calls.size == failAt) return MqResult.Failure(failure)
        return MqResult.Success(LibraryPage(all.drop(startIndex).take(limit), all.size))
    }
}
