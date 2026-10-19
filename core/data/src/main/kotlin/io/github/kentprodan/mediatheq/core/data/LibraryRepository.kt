package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.SearchHit
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.Flow

/** Reads of the local library. Screens add what they need in their own issues (#10, #11, #15-#17). */
interface LibraryRepository {
    /** All albums by sort name; emits again after each completed sync, never with a half-synced library. */
    fun albums(): Flow<List<Album>>

    /** One album, `null` when it is not (or no longer) in the library. */
    fun album(id: MediaId): Flow<Album?>

    /** The album's tracks in play order (disc, then track number). */
    fun albumTracks(albumId: MediaId): Flow<List<Track>>

    /** Album artists by sort name (the Library's Artists tab). */
    fun albumArtists(): Flow<List<Artist>>

    fun playlists(): Flow<List<Playlist>>

    /** Every track with its artists, by sort name. */
    fun tracks(): Flow<List<Track>>

    /** Diacritic-insensitive search over names; the last word matches as a prefix. Any typed text is safe. */
    suspend fun search(
        query: String,
        limit: Int = 50,
    ): List<SearchHit>
}
