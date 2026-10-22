package io.github.kentprodan.mediatheq.core.server

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.PlaylistEntry
import io.github.kentprodan.mediatheq.core.model.Track

/**
 * Paged reads of a whole music library, in a stable order (by sort name) so offset paging works. Pages start at
 * [startIndex] and hold at most [limit] items.
 */
interface LibrarySource {
    suspend fun albumArtists(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Artist>>

    suspend fun albums(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Album>>

    suspend fun tracks(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Track>>

    suspend fun playlists(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Playlist>>

    /** One playlist with fresh counts and image; a missing playlist fails with `NotFound`. */
    suspend fun playlist(
        server: SignedInServer,
        playlistId: MediaId,
    ): MqResult<Playlist>

    /** URL of [artwork] at least [widthPx] wide. Public on Jellyfin 12, so no token (ADR 0015). */
    fun imageUrl(
        baseUrl: String,
        artwork: Artwork,
        widthPx: Int,
    ): String

    /** The tracks of [playlistId] in playlist order. */
    suspend fun playlistEntries(
        server: SignedInServer,
        playlistId: MediaId,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<PlaylistEntry>>
}
