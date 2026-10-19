package io.github.kentprodan.mediatheq.provider.jellyfin

import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.PlaylistEntry
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import io.github.kentprodan.mediatheq.core.server.LibraryPage
import io.github.kentprodan.mediatheq.core.server.LibrarySource
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.BaseItemDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.QueryResultDto
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toAlbum
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toArtist
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toPlaylist
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toPlaylistEntry
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toTrack
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * [LibrarySource] for Jellyfin 12. Jellyfin 12.2 silently ignores unknown query values (R8), so every query shape here
 * has a contract test asserting the exact parameters; `/Items` always sends `includeItemTypes` and `recursive` (R7).
 */
class JellyfinLibrarySource internal constructor(
    private val http: JellyfinHttp,
) : LibrarySource {
    @Inject
    constructor(
        okHttpClient: OkHttpClient,
        clientInfo: ClientInfo,
        json: Json,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(JellyfinHttp(okHttpClient, clientInfo, json, ioDispatcher))

    override suspend fun albumArtists(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Artist>> =
        page(server, "Artists/AlbumArtists", pagedQuery(server, startIndex, limit) + ("fields" to "SortName")) {
            it.toArtist(server.serverId)
        }

    override suspend fun albums(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Album>> =
        items(
            server,
            "MusicAlbum",
            "SortName,DateCreated,ChildCount",
            startIndex,
            limit,
        ) { it.toAlbum(server.serverId) }

    override suspend fun tracks(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Track>> =
        // MediaStreams instead of MediaSources: the same audio format facts in 25% less data.
        items(server, "Audio", "SortName,DateCreated,MediaStreams", startIndex, limit) { it.toTrack(server.serverId) }

    override suspend fun playlists(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Playlist>> =
        items(
            server,
            "Playlist",
            "SortName,DateCreated,ChildCount",
            startIndex,
            limit,
        ) { it.toPlaylist(server.serverId) }

    override fun imageUrl(
        baseUrl: String,
        artwork: Artwork,
        widthPx: Int,
    ): String =
        baseUrl
            .toHttpUrl()
            .newBuilder()
            .addPathSegments("Items")
            .addPathSegment(artwork.itemId)
            .addPathSegments("Images/Primary")
            // The tag versions the image, so the URL is immutable and caches forever (jellyfin skill, section 4).
            .addQueryParameter("tag", artwork.tag)
            .addQueryParameter("fillWidth", widthPx.toString())
            .addQueryParameter("quality", "90")
            .build()
            .toString()

    override suspend fun playlistEntries(
        server: SignedInServer,
        playlistId: MediaId,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<PlaylistEntry>> =
        page(
            server,
            "Playlists/${playlistId.itemId}/Items",
            listOf(
                "userId" to server.userId,
                "startIndex" to startIndex.toString(),
                "limit" to limit.toString(),
                "enableUserData" to "false",
                "enableImages" to "false",
            ),
        ) { it.toPlaylistEntry(server.serverId) }

    private suspend fun <T : Any> items(
        server: SignedInServer,
        type: String,
        fields: String,
        startIndex: Int,
        limit: Int,
        map: (BaseItemDto) -> T?,
    ): MqResult<LibraryPage<T>> =
        page(
            server,
            "Items",
            pagedQuery(server, startIndex, limit) +
                listOf("includeItemTypes" to type, "recursive" to "true", "fields" to fields),
            map,
        )

    /** Paging in a stable order, with user data and one primary image (for its tag and BlurHash). */
    private fun pagedQuery(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ) = listOf(
        "userId" to server.userId,
        "startIndex" to startIndex.toString(),
        "limit" to limit.toString(),
        "sortBy" to "SortName",
        "sortOrder" to "Ascending",
        "enableUserData" to "true",
        "enableImageTypes" to "Primary",
        "imageTypeLimit" to "1",
    )

    private suspend fun <T : Any> page(
        server: SignedInServer,
        path: String,
        query: List<Pair<String, String>>,
        map: (BaseItemDto) -> T?,
    ): MqResult<LibraryPage<T>> =
        http.get(server.connection, path, QueryResultDto.serializer(), query) { result ->
            LibraryPage(result.items.mapNotNull(map), result.totalRecordCount)
        }
}
