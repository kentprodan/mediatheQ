package io.github.kentprodan.mediatheq.provider.jellyfin

import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.Chapter
import io.github.kentprodan.mediatheq.core.model.ImageType
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.MediaSegment
import io.github.kentprodan.mediatheq.core.model.PlayerExtras
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.PlaylistEntry
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.SegmentType
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.TrickplayInfo
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import io.github.kentprodan.mediatheq.core.server.LibraryPage
import io.github.kentprodan.mediatheq.core.server.LibrarySource
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.BaseItemDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.MediaSegmentsDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.QueryResultDto
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.ticksToDuration
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toAlbum
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toArtist
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toDomain
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toPlaylist
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toPlaylistEntry
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toSeason
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toSeries
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toTrack
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toVideo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

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
        // Backdrop tags come only when the image types name them (R21); the overview is the biography (#133).
        page(
            server,
            "Artists/AlbumArtists",
            pagedQuery(server, startIndex, limit, imageTypes = "Primary,Backdrop") + ("fields" to "SortName,Overview"),
        ) { it.toArtist(server.serverId) }

    override suspend fun albums(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Album>> =
        items(
            server,
            "MusicAlbum",
            "SortName,DateCreated,ChildCount,Genres,Studios,Overview",
            startIndex,
            limit,
        ) { it.toAlbum(server.serverId) }

    override suspend fun tracks(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Track>> =
        // MediaSources for the file size (#132, ~30% more data than MediaStreams alone); it carries the streams (R19).
        items(
            server,
            "Audio",
            "SortName,DateCreated,MediaSources,Genres",
            startIndex,
            limit,
        ) { it.toTrack(server.serverId) }

    override suspend fun playlists(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Playlist>> =
        items(server, "Playlist", PLAYLIST_FIELDS, startIndex, limit) { it.toPlaylist(server.serverId) }

    override suspend fun movies(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Video>> =
        video(server, "Movie", startIndex, limit) { it.toVideo(server.serverId, VideoKind.Movie) }

    override suspend fun series(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Series>> = video(server, "Series", startIndex, limit) { it.toSeries(server.serverId) }

    override suspend fun seasons(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Season>> = video(server, "Season", startIndex, limit) { it.toSeason(server.serverId) }

    override suspend fun episodes(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
    ): MqResult<LibraryPage<Video>> =
        video(server, "Episode", startIndex, limit) { it.toVideo(server.serverId, VideoKind.Episode) }

    /** R26: `GET /UserItems/Resume` with the video types; only the user data is read (the rows come from the sync). */
    override suspend fun resumeVideos(
        server: SignedInServer,
        limit: Int,
    ): MqResult<Map<MediaId, UserData>> =
        http.get(
            server.connection,
            "UserItems/Resume",
            QueryResultDto.serializer(),
            listOf(
                "userId" to server.userId,
                "mediaTypes" to "Video",
                "includeItemTypes" to "Movie,Episode",
                "limit" to limit.toString(),
                "enableUserData" to "true",
                "enableImages" to "false",
            ),
        ) { result ->
            result.items
                .mapNotNull { item -> item.id?.let { MediaId(server.serverId, it) to item.userData.toDomain() } }
                .toMap()
        }

    // R27 (#277): chapters and trickplay come with the item, one request; the tile width closest to 320 px.
    override suspend fun playerExtras(
        server: SignedInServer,
        videoId: MediaId,
    ): MqResult<PlayerExtras> =
        http.get(
            server.connection,
            "Items",
            QueryResultDto.serializer(),
            listOf("userId" to server.userId, "ids" to videoId.itemId, "fields" to "Chapters,Trickplay"),
        ) { result ->
            val item = result.items.firstOrNull()
            PlayerExtras(
                chapters =
                    item?.chapters.orEmpty().mapIndexed { index, chapter ->
                        Chapter(index, ticksToDuration(chapter.startPositionTicks), chapter.name, chapter.imageTag)
                    },
                trickplay =
                    item?.trickplay?.entries?.firstOrNull()?.let { (source, widths) ->
                        widths.values
                            .filter { it.width > 0 && it.tileWidth > 0 && it.tileHeight > 0 }
                            .minByOrNull { kotlin.math.abs(it.width - TRICKPLAY_WIDTH) }
                            ?.let {
                                TrickplayInfo(
                                    source,
                                    it.width,
                                    it.height,
                                    it.tileWidth,
                                    it.tileHeight,
                                    it.thumbnailCount,
                                    it.interval.milliseconds,
                                )
                            }
                    },
            )
        }

    // R28 (#277): Jellyfin's own media segments (Intro Skipper writes there; its credits are Outro), not plugin routes.
    override suspend fun segments(
        server: SignedInServer,
        videoId: MediaId,
    ): MqResult<List<MediaSegment>> =
        http.get(
            server.connection,
            "MediaSegments/${videoId.itemId}",
            MediaSegmentsDto.serializer(),
            listOf("includeSegmentTypes" to "Intro,Recap,Preview,Commercial,Outro"),
        ) { result ->
            result.items.mapNotNull { segment ->
                val type = SegmentType.entries.firstOrNull { it.name == segment.type } ?: return@mapNotNull null
                MediaSegment(type, ticksToDuration(segment.startTicks), ticksToDuration(segment.endTicks))
                    .takeIf { it.end > it.start }
            }
        }

    override fun trickplayTileUrl(
        baseUrl: String,
        videoId: MediaId,
        trickplay: TrickplayInfo,
        index: Int,
    ): String =
        baseUrl
            .toHttpUrl()
            .newBuilder()
            .addPathSegment("Videos")
            .addPathSegment(videoId.itemId)
            .addPathSegment("Trickplay")
            .addPathSegment(trickplay.width.toString())
            .addPathSegment("$index.jpg")
            .addQueryParameter("mediaSourceId", trickplay.mediaSourceId)
            .build()
            .toString()

    override fun chapterImageUrl(
        baseUrl: String,
        videoId: MediaId,
        chapter: Chapter,
        widthPx: Int,
    ): String? =
        chapter.imageTag?.let { tag ->
            baseUrl
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("Items")
                .addPathSegment(videoId.itemId)
                .addPathSegments("Images/Chapter/${chapter.index}")
                .addQueryParameter("tag", tag)
                .addQueryParameter("maxWidth", widthPx.toString())
                .build()
                .toString()
        }

    /** One query shape for the four video kinds (ADR 0048); backdrops come only when asked for (R21). */
    private suspend fun <T : Any> video(
        server: SignedInServer,
        type: String,
        startIndex: Int,
        limit: Int,
        map: (BaseItemDto) -> T?,
    ): MqResult<LibraryPage<T>> =
        page(
            server,
            "Items",
            pagedQuery(server, startIndex, limit, imageTypes = "Primary,Backdrop,Thumb") +
                listOf("includeItemTypes" to type, "recursive" to "true", "fields" to VIDEO_FIELDS),
            map,
        )

    override suspend fun playlist(
        server: SignedInServer,
        playlistId: MediaId,
    ): MqResult<Playlist> {
        val page =
            items(server, "Playlist", PLAYLIST_FIELDS, 0, 1, listOf("ids" to playlistId.itemId)) {
                it.toPlaylist(server.serverId)
            }
        val playlist =
            when (page) {
                is MqResult.Failure -> return page
                is MqResult.Success -> page.value.items.firstOrNull() ?: return MqResult.Failure(MqError.NotFound)
            }
        return MqResult.Success(playlist)
    }

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
            .addPathSegments(
                when (artwork.type) {
                    ImageType.Primary -> "Images/Primary"
                    ImageType.Backdrop -> "Images/Backdrop/0"
                },
            )
            // The tag versions the image, so the URL is immutable and caches forever (jellyfin skill, section 4).
            .addQueryParameter("tag", artwork.tag)
            .addQueryParameter("fillWidth", widthPx.toString())
            .addQueryParameter("quality", "90")
            .build()
            .toString()

    override suspend fun image(
        baseUrl: String,
        artwork: Artwork,
        widthPx: Int,
    ): MqResult<ByteArray> = http.bytes(imageUrl(baseUrl, artwork, widthPx))

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
        extra: List<Pair<String, String>> = emptyList(),
        map: (BaseItemDto) -> T?,
    ): MqResult<LibraryPage<T>> =
        page(
            server,
            "Items",
            pagedQuery(server, startIndex, limit) +
                listOf("includeItemTypes" to type, "recursive" to "true", "fields" to fields) + extra,
            map,
        )

    /** Paging in a stable order, with user data and one primary image (for its tag and BlurHash). */
    private fun pagedQuery(
        server: SignedInServer,
        startIndex: Int,
        limit: Int,
        imageTypes: String = "Primary",
    ) = listOf(
        "userId" to server.userId,
        "startIndex" to startIndex.toString(),
        "limit" to limit.toString(),
        "sortBy" to "SortName",
        "sortOrder" to "Ascending",
        "enableUserData" to "true",
        "enableImageTypes" to imageTypes,
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

    private companion object {
        const val PLAYLIST_FIELDS = "SortName,DateCreated,ChildCount"
        const val VIDEO_FIELDS = "MediaSources,Overview,Genres,Studios,DateCreated,SortName,Taglines"
    }
}

/** The trickplay width asked for (#277): about 100 KB a tile, enough for a preview bubble. */
private const val TRICKPLAY_WIDTH = 320
