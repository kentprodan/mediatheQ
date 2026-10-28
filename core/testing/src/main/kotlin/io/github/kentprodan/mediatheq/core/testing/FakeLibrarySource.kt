package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.PlaylistEntry
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.server.LibraryPage
import io.github.kentprodan.mediatheq.core.server.LibrarySource
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * [LibrarySource] over in-memory lists. [calls] records every request as "albums@500"; [failAt] fails the call with
 * that number (1-based) with [failure]; [throwAt] throws [thrown] there instead (a bug, not a server answer);
 * [pauseAt] suspends that call until [resume] completes, ignoring cancellation with [pauseIgnoresCancel] (like a blocking
 * HTTP call already sent); [holdAt] does the same for a second call, until [release].
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
    var throwAt: Int? = null
    var thrown: Exception = IllegalStateException("unexpected answer")
    var pauseAt: Int? = null
    var pauseIgnoresCancel = false
    var holdAt: Int? = null
    val held = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val paused = CompletableDeferred<Unit>()
    val resume = CompletableDeferred<Unit>()

    /** Playlist item id -> error its entries request answers with. */
    var playlistErrors: Map<String, MqError> = emptyMap()

    /** Listings (e.g. "albums") whose reported total is one more than they return, like an item lost between pages. */
    var shortListings: Set<String> = emptySet()

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

    /** Answers from [playlists]; a missing one is `NotFound`. Recorded as "playlist:<id>". */
    override suspend fun playlist(
        server: SignedInServer,
        playlistId: MediaId,
    ): MqResult<Playlist> {
        calls += "playlist:${playlistId.itemId}"
        val error = playlistErrors[playlistId.itemId]
        if (error != null) return MqResult.Failure(error)
        return playlists.firstOrNull { it.id == playlistId }?.let { MqResult.Success(it) }
            ?: MqResult.Failure(MqError.NotFound)
    }

    /** `baseUrl|itemId|tag|width`, easy to assert on. */
    override fun imageUrl(
        baseUrl: String,
        artwork: Artwork,
        widthPx: Int,
    ) = "$baseUrl|${artwork.itemId}|${artwork.tag}|$widthPx"

    /** Image requests as "itemId|tag|width"; [imageFailure] makes them fail. */
    val imageCalls = mutableListOf<String>()
    var imageFailure: MqError? = null

    override suspend fun image(
        baseUrl: String,
        artwork: Artwork,
        widthPx: Int,
    ): MqResult<ByteArray> {
        imageCalls += "${artwork.itemId}|${artwork.tag}|$widthPx"
        return imageFailure?.let { MqResult.Failure(it) } ?: MqResult.Success("cover ${artwork.itemId}".toByteArray())
    }

    override suspend fun playlistEntries(
        server: SignedInServer,
        playlistId: MediaId,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<PlaylistEntry>> {
        val error = playlistErrors[playlistId.itemId]
        if (error != null) return MqResult.Failure(error).also { calls += "entries:${playlistId.itemId}@$startIndex" }
        return page("entries:${playlistId.itemId}", entries[playlistId].orEmpty(), startIndex, limit)
    }

    private suspend fun <T> page(
        name: String,
        all: List<T>,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<T>> {
        calls += "$name@$startIndex"
        if (calls.size == pauseAt) {
            paused.complete(Unit)
            if (pauseIgnoresCancel) withContext(NonCancellable) { resume.await() } else resume.await()
        }
        if (calls.size == holdAt) {
            held.complete(Unit)
            release.await()
        }
        if (calls.size == throwAt) throw thrown
        if (calls.size == failAt) return MqResult.Failure(failure)
        val total = if (name in shortListings) all.size + 1 else all.size
        return MqResult.Success(LibraryPage(all.drop(startIndex).take(limit), total))
    }
}
