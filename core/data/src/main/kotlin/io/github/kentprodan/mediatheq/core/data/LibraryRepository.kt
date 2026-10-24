package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.SearchResults
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.Flow

/** Reads of the local library. Screens add what they need in their own issues (#10, #11, #15-#17). */
interface LibraryRepository {
    /** All albums by sort name; emits again after each completed sync, never with a half-synced library. */
    fun albums(): Flow<List<Album>>

    /** One track with its artists, `null` when it is not (or no longer) in the library. */
    fun track(id: MediaId): Flow<Track?>

    /** One album, `null` when it is not (or no longer) in the library. */
    fun album(id: MediaId): Flow<Album?>

    /** The album's tracks in play order (disc, then track number). */
    fun albumTracks(albumId: MediaId): Flow<List<Track>>

    /** Album artists by sort name (the Library's Artists tab). */
    fun albumArtists(): Flow<List<Artist>>

    /** One artist (album artist or credit-only), `null` when it is not in the library. */
    fun artist(id: MediaId): Flow<Artist?>

    /** Albums the artist is an album artist of, newest year first, unknown year last. */
    fun artistAlbums(artistId: MediaId): Flow<List<Album>>

    /** Other albums with a track credited to the artist, in the same order. */
    fun appearsOn(artistId: MediaId): Flow<List<Album>>

    /** Every track of the artist once: own albums' tracks plus tracks crediting the artist, own albums first. */
    fun artistTracks(artistId: MediaId): Flow<List<Track>>

    fun playlists(): Flow<List<Playlist>>

    /** Every track with its artists, by sort name. */
    fun tracks(): Flow<List<Track>>

    /**
     * Diacritic-insensitive search over names, at most [limitPerKind] of each kind; the last word matches as a prefix.
     * Any typed text is safe; a blank query finds nothing.
     */
    suspend fun search(
        query: String,
        limitPerKind: Int = 20,
    ): MqResult<SearchResults>
}
