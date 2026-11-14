package io.github.kentprodan.mediatheq.core.server

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.Chapter
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.MediaSegment
import io.github.kentprodan.mediatheq.core.model.PlayerExtras
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.PlaylistEntry
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.TrickplayInfo
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video

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

    /** Movies with their audio and subtitle streams (#270); asked only when [ServerCapabilities.video]. */
    suspend fun movies(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Video>>

    suspend fun series(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Series>>

    suspend fun seasons(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Season>>

    /** Episodes of every series, with their streams. */
    suspend fun episodes(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Video>>

    /**
     * The user data of the movies and episodes left halfway, most recent first, at most [limit] (#275): Continue watching
     * is fresh when Home opens without a full sync.
     */
    suspend fun resumeVideos(
        server: SignedInServer,
        limit: Int,
    ): MqResult<Map<MediaId, UserData>>

    /** A video's chapters and trickplay previews (#277): one item query with `Chapters,Trickplay`. */
    suspend fun playerExtras(
        server: SignedInServer,
        videoId: MediaId,
    ): MqResult<PlayerExtras>

    /** A video's skippable segments (#277): intro, recap, preview, commercial and outro (credits). */
    suspend fun segments(
        server: SignedInServer,
        videoId: MediaId,
    ): MqResult<List<MediaSegment>>

    /** The trickplay tile image [index] of [videoId] at [width] (#277); fetched with the authenticated client. */
    fun trickplayTileUrl(
        baseUrl: String,
        videoId: MediaId,
        trickplay: TrickplayInfo,
        index: Int,
    ): String

    /** A chapter's picture (#277); public like the other images. */
    fun chapterImageUrl(
        baseUrl: String,
        videoId: MediaId,
        chapter: Chapter,
        widthPx: Int,
    ): String?

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

    /** The bytes of [artwork] at least [widthPx] wide, from [imageUrl] (no token), e.g. to keep with a download. */
    suspend fun image(
        baseUrl: String,
        artwork: Artwork,
        widthPx: Int,
    ): MqResult<ByteArray>

    /** The tracks of [playlistId] in playlist order. */
    suspend fun playlistEntries(
        server: SignedInServer,
        playlistId: MediaId,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<PlaylistEntry>>
}
