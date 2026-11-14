package io.github.kentprodan.mediatheq.provider.jellyfin

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.errorOrNull
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.ImageType
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.VideoFormat
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.model.VideoRange
import io.github.kentprodan.mediatheq.core.network.MediatheqJson
import io.github.kentprodan.mediatheq.core.network.mediatheqOkHttpClient
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import io.github.kentprodan.mediatheq.core.server.LibraryPage
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Contract tests against pages recorded from a Jellyfin 12.2.0 server. Jellyfin ignores unknown query values without an
 * error (R8), so each test pins the exact query we send.
 */
class JellyfinLibrarySourceTest {
    @get:Rule
    val serverRule = MockWebServerRule()
    private val mockServer get() = serverRule.server

    private val source =
        JellyfinLibrarySource(
            mediatheqOkHttpClient(),
            ClientInfo("mediatheQ", "Pixel 9", "0.1.0"),
            MediatheqJson,
            Dispatchers.Unconfined,
        )
    private val serverId = ServerId("0f1e2d3c4b5a69788796a5b4c3d2e1f0")
    private val server
        get() =
            SignedInServer(
                ServerConnection(mockServer.url("/").toString(), "device-1", accessToken = "token-1"),
                serverId,
                userId = "11111111222233334444555555555555",
            )

    private fun enqueue(path: String) =
        mockServer.enqueue(MockResponse(body = javaClass.getResource("/jellyfin/$path")!!.readText()))

    private fun RecordedRequest.query(): Map<String, String?> =
        url.queryParameterNames.associateWith {
            url.queryParameter(it)
        }

    private val paging =
        mapOf(
            "userId" to "11111111222233334444555555555555",
            "startIndex" to "500",
            "limit" to "500",
            "sortBy" to "SortName",
            "sortOrder" to "Ascending",
            "enableUserData" to "true",
            "enableImageTypes" to "Primary",
            "imageTypeLimit" to "1",
        )

    private fun assertAuthenticated(request: RecordedRequest) =
        assertThat(request.headers["Authorization"]).endsWith(", Token=\"token-1\"")

    @Test
    fun `album artists query and mapping`() =
        runTest {
            enqueue("artists_album_artists/page.json")

            val page = (source.albumArtists(server, 500, 500) as MqResult.Success).value

            val request = mockServer.takeRequest()
            assertThat(request.url.encodedPath).isEqualTo("/Artists/AlbumArtists")
            // R21: without Backdrop in the image types the server sends no backdrop tags at all.
            assertThat(request.query()).isEqualTo(
                paging + mapOf("enableImageTypes" to "Primary,Backdrop", "fields" to "SortName,Overview"),
            )
            assertAuthenticated(request)
            assertThat(page.totalCount).isEqualTo(582)
            val artist = page.items.first()
            assertThat(artist.name).isEqualTo("2 Chainz")
            assertThat(artist.sortName).isEqualTo("0000000002 chainz")
            assertThat(artist.artwork?.tag).isEqualTo("5f594a17b901a35024f46c1130dc005b")
            assertThat(artist.artwork?.blurHash).isNotNull()
            assertThat(artist.backdrop?.type).isEqualTo(ImageType.Backdrop)
            assertThat(artist.backdrop?.itemId).isEqualTo(artist.id.itemId)
            assertThat(artist.backdrop?.tag).isEqualTo("5c46bac5d2e3eb689d2765a5d23f8b00")
            assertThat(artist.backdrop?.blurHash).isNotNull()
            assertThat(artist.overview).startsWith("Tauheed Epps")
            // An artist without fanart.
            assertThat(page.items.last().backdrop).isNull()
        }

    @Test
    fun `R16 an album may credit a namesake the album artist list leaves out, and keeps its id`() =
        runTest {
            enqueue("artists_album_artists/same_name.json")
            enqueue("items_music_album/album_artist_other_id.json")

            val listed = (source.albumArtists(server, 0, 500) as MqResult.Success).value.items.single()
            val album = (source.albums(server, 0, 500) as MqResult.Success).value.items.single()

            assertThat(listed.name).isEqualTo("Satoshi")
            assertThat(album.albumArtists.single().name).isEqualTo("Satoshi")
            // The sync merges the two by name (SyncDao.mergeSameNameArtists); the provider passes the ids on as sent.
            assertThat(album.albumArtists.single().id).isEqualTo(MediaId(serverId, "460cbff0ed0a8976732c1d572331ad3a"))
            assertThat(listed.id).isEqualTo(MediaId(serverId, "39192fd809e2c039000d4f21bae32b6b"))
        }

    @Test
    fun `R7 albums query sends type and recursive`() =
        runTest {
            enqueue("items_music_album/page.json")

            val page = (source.albums(server, 500, 500) as MqResult.Success).value

            val request = mockServer.takeRequest()
            assertThat(request.url.encodedPath).isEqualTo("/Items")
            assertThat(request.query()).isEqualTo(
                paging +
                    mapOf(
                        "includeItemTypes" to "MusicAlbum",
                        "recursive" to "true",
                        "fields" to "SortName,DateCreated,ChildCount,Genres,Studios,Overview",
                    ),
            )
            assertAuthenticated(request)
            assertThat(page.totalCount).isEqualTo(989)
            val album = page.items.first()
            assertThat(album.albumArtists.map { it.name }).containsExactly("Ed Sheeran")
            assertThat(album.year).isEqualTo(2011)
            assertThat(album.duration).isEqualTo(788_546.milliseconds)
            assertThat(album.normalizationGainDb).isWithin(0.001).of(-5.6)
        }

    @Test
    fun `R7 tracks query sends type and recursive and maps the audio format`() =
        runTest {
            enqueue("items_audio/page.json")

            val page = (source.tracks(server, 500, 500) as MqResult.Success).value

            val request = mockServer.takeRequest()
            assertThat(request.query()).isEqualTo(
                paging +
                    mapOf(
                        "includeItemTypes" to "Audio",
                        "recursive" to "true",
                        "fields" to "SortName,DateCreated,MediaSources,Genres",
                    ),
            )
            assertThat(page.totalCount).isEqualTo(3)
            val track = page.items[1]
            assertThat(track.audioFormat?.codec).isEqualTo("mp3")
            // R19: with MediaSources the streams and the file size come in the source.
            assertThat(track.sizeBytes).isEqualTo(9_682_839)
            assertThat(page.items.map { it.audioFormat?.codec }).containsExactly("flac", "mp3", "flac").inOrder()
            assertThat(track.albumId).isEqualTo(MediaId(serverId, "8b7761b96bca5fb988f5b982e8ca02ad"))
            assertThat(track.trackNumber).isEqualTo(1)
            assertThat(track.discNumber).isEqualTo(1)
            assertThat(track.dateAdded).isEqualTo(Instant.parse("2026-07-11T13:55:28.287Z"))
            assertThat(track.normalizationGainDb).isEqualTo(-10.8)
            assertThat(track.artwork?.itemId).isEqualTo(track.id.itemId)
        }

    @Test
    fun `R17 albums and tracks carry their genres by id and name`() =
        runTest {
            enqueue("items_music_album/with_genres.json")
            enqueue("items_audio/with_genres.json")

            val albums = (source.albums(server, 0, 500) as MqResult.Success).value.items
            val tracks = (source.tracks(server, 0, 500) as MqResult.Success).value.items

            assertThat(
                albums.map { album -> album.genres.map { it.name } },
            ).containsExactly(listOf("Pop"), listOf("Rap"))
            assertThat(albums[0].genres.single().id).isEqualTo(MediaId(serverId, "f1f202f389018ad2c0766af4b0fcb155"))
            assertThat(
                tracks.map { track ->
                    track.genres.map { it.name }
                },
            ).containsExactly(listOf("Hip-Hop"), listOf("R&B"))
        }

    @Test
    fun `R18 albums carry their record labels, without the no-label placeholder`() =
        runTest {
            enqueue("items_music_album/with_studios.json")

            val albums = (source.albums(server, 0, 500) as MqResult.Success).value.items

            assertThat(albums.map { album -> album.labels.map { it.name } })
                .containsExactly(
                    listOf("Warner Music UK"),
                    listOf("Def Jam 2000", "BMG Direct Marketing, Inc.", "Ruff Ryders"),
                    emptyList<String>(),
                ).inOrder()
            assertThat(albums[0].labels.single().id).isEqualTo(MediaId(serverId, "0e5fc6ca81a04d12a9f5032922c69111"))
        }

    @Test
    fun `albums carry their overview and release date`() =
        runTest {
            enqueue("items_music_album/with_overview.json")

            val album = (source.albums(server, 0, 500) as MqResult.Success).value.items.single()

            assertThat(album.overview).startsWith("+ (pronounced Plus) is the debut studio album")
            assertThat(album.releaseDate).isEqualTo(Instant.parse("2011-01-01T00:00:00Z"))
        }

    @Test
    fun `R7 playlists query sends type and recursive`() =
        runTest {
            enqueue("items_playlist/page.json")

            val page = (source.playlists(server, 500, 500) as MqResult.Success).value

            assertThat(mockServer.takeRequest().query()).isEqualTo(
                paging +
                    mapOf(
                        "includeItemTypes" to "Playlist",
                        "recursive" to "true",
                        "fields" to "SortName,DateCreated,ChildCount",
                    ),
            )
            assertThat(page.totalCount).isEqualTo(11)
            assertThat(page.items.first().trackCount).isEqualTo(200)
        }

    @Test
    fun `playlist entries query and mapping`() =
        runTest {
            enqueue("playlists_items/page.json")

            val page = (source.playlistEntries(server, MediaId(serverId, "p1"), 0, 200) as MqResult.Success).value

            val request = mockServer.takeRequest()
            assertThat(request.url.encodedPath).isEqualTo("/Playlists/p1/Items")
            assertThat(request.query()).isEqualTo(
                mapOf(
                    "userId" to "11111111222233334444555555555555",
                    "startIndex" to "0",
                    "limit" to "200",
                    "enableUserData" to "false",
                    "enableImages" to "false",
                ),
            )
            assertAuthenticated(request)
            assertThat(page.totalCount).isEqualTo(43)
            assertThat(page.items).hasSize(3)
            assertThat(
                page.items
                    .first()
                    .trackId.serverId,
            ).isEqualTo(serverId)
        }

    @Test
    fun `R6 one playlist query reads the collage image tag`() =
        runTest {
            enqueue("items_playlist_by_id/collage.json")

            val playlist = (source.playlist(server, MediaId(serverId, "p1")) as MqResult.Success).value

            val request = mockServer.takeRequest()
            assertThat(request.url.encodedPath).isEqualTo("/Items")
            assertThat(request.query()).isEqualTo(
                paging +
                    mapOf(
                        "startIndex" to "0",
                        "limit" to "1",
                        "includeItemTypes" to "Playlist",
                        "recursive" to "true",
                        "fields" to "SortName,DateCreated,ChildCount",
                        "ids" to "p1",
                    ),
            )
            assertThat(playlist.trackCount).isEqualTo(4)
            assertThat(playlist.artwork?.tag).isEqualTo("8c9da4e9f38d0a1c376a9903282bb63e")
        }

    @Test
    fun `a playlist the server no longer has is not found`() =
        runTest {
            enqueue("items_playlist_by_id/missing.json")

            assertThat(source.playlist(server, MediaId(serverId, "gone")).errorOrNull()).isEqualTo(MqError.NotFound)
        }

    @Test
    fun `an image is fetched from its public url without a token`() =
        runTest {
            mockServer.enqueue(MockResponse(body = "jpeg"))

            val bytes = source.image(mockServer.url("/").toString(), Artwork("al1", "tag", null), 1024)

            assertThat((bytes as MqResult.Success).value.decodeToString()).isEqualTo("jpeg")
            val request = mockServer.takeRequest()
            assertThat(request.url.encodedPath).isEqualTo("/Items/al1/Images/Primary")
            assertThat(request.query()).isEqualTo(mapOf("tag" to "tag", "fillWidth" to "1024", "quality" to "90"))
            assertThat(request.headers["Authorization"]).isNull()
        }

    @Test
    fun `a missing image fails with its http error`() =
        runTest {
            mockServer.enqueue(MockResponse(code = 404))

            val result = source.image(mockServer.url("/").toString(), Artwork("al1", "tag", null), 1024)

            assertThat(result).isEqualTo(MqResult.Failure(MqError.NotFound))
        }

    @Test
    fun `image urls are public, sized and versioned by tag`() {
        val url = source.imageUrl("http://nas:8096/jellyfin", Artwork("al1", "t a/g", null), 512)

        assertThat(
            url,
        ).isEqualTo("http://nas:8096/jellyfin/Items/al1/Images/Primary?tag=t%20a%2Fg&fillWidth=512&quality=90")
        assertThat(source.imageUrl("http://nas:8096", Artwork("ar1", "b1", null, ImageType.Backdrop), 1024))
            .isEqualTo("http://nas:8096/Items/ar1/Images/Backdrop/0?tag=b1&fillWidth=1024&quality=90")
        assertThat(url).doesNotContain("api_key")
        assertThat(url.lowercase()).doesNotContain("token")
    }

    @Test
    fun `a 401 during a library read is Unauthorized`() =
        runTest {
            mockServer.enqueue(MockResponse(code = 401))

            assertThat(source.tracks(server, 0, 500).errorOrNull()).isEqualTo(MqError.Unauthorized)
        }

    @Test
    fun `items without id are skipped, not fatal`() =
        runTest {
            mockServer.enqueue(
                MockResponse(body = """{"Items":[{"Name":"no id"},{"Id":"a1","Name":"ok"}],"TotalRecordCount":2}"""),
            )

            val page = (source.albums(server, 0, 500) as MqResult.Success).value

            assertThat(page).isEqualTo(LibraryPage(page.items, 2))
            assertThat(page.items.map { it.title }).containsExactly("ok")
        }

    @Test
    fun `a track without its own image uses the album cover`() =
        runTest {
            mockServer.enqueue(
                MockResponse(
                    body =
                        """
                        {"Items":[{"Id":"t1","Name":"T","AlbumId":"al1","AlbumPrimaryImageTag":"tag1",
                        "ImageBlurHashes":{"Primary":{"tag1":"hash1"}},
                        "MediaSources":[{"Size":1000,"MediaStreams":[{"Type":"Video","Codec":"mjpeg"},{"Type":"Audio",
                        "Codec":"EAC3","SampleRate":48000,"Channels":6,"BitRate":768000,"AudioSpatialFormat":"DolbyAtmos"}]}]}],
                        "TotalRecordCount":1}
                        """.trimIndent(),
                ),
            )

            val track = (source.tracks(server, 0, 500) as MqResult.Success).value.items.single()

            assertThat(track.artwork).isEqualTo(Artwork("al1", "tag1", "hash1"))
            assertThat(track.audioFormat).isEqualTo(
                AudioFormat(
                    "eac3",
                    48_000,
                    null,
                    6,
                    768,
                    io.github.kentprodan.mediatheq.core.model.SpatialFormat.DolbyAtmos,
                ),
            )
        }

    private fun videoQuery(type: String) =
        paging +
            mapOf(
                "enableImageTypes" to "Primary,Backdrop,Thumb",
                "includeItemTypes" to type,
                "recursive" to "true",
                "fields" to "MediaSources,Overview,Genres,Studios,DateCreated,SortName,Taglines",
            )

    @Test
    fun `R7 movies query sends type and recursive and maps streams and format`() =
        runTest {
            enqueue("items_movie/page.json")

            val page = (source.movies(server, 500, 500) as MqResult.Success).value

            assertThat(mockServer.takeRequest().query()).isEqualTo(videoQuery("Movie"))
            assertThat(page.totalCount).isEqualTo(189)
            val movie = page.items[0]
            assertThat(movie.id).isEqualTo(MediaId(serverId, "b9a025c466b155918b43a48a0319ce5c"))
            assertThat(movie.kind).isEqualTo(VideoKind.Movie)
            assertThat(movie.title).isEqualTo("Movie 1")
            assertThat(movie.year).isEqualTo(2011)
            assertThat(movie.duration).isEqualTo(6_003_009.milliseconds)
            assertThat(movie.sizeBytes).isEqualTo(13_130_329_521)
            assertThat(movie.tagline).isEqualTo("The tagline of Movie 1.")
            assertThat(movie.officialRating).isEqualTo("R")
            assertThat(movie.genres).containsExactly("Comedy", "Drama").inOrder()
            assertThat(movie.dateAdded).isEqualTo(Instant.parse("2026-11-07T21:38:14.2409704Z"))
            assertThat(movie.format).isEqualTo(VideoFormat("h264", 1920, 1080, VideoRange.Sdr, 14_477))
            assertThat(movie.streams.map { Triple(it.index, it.type, it.language) })
                .containsExactly(Triple(1, StreamType.Audio, "rus"), Triple(2, StreamType.Audio, "eng"))
                .inOrder()
            assertThat(movie.streams[0].isDefault).isTrue()
            assertThat(movie.artwork?.tag).isEqualTo("8ca33aba0e9d30f57a103102ad0a4c6e")
            assertThat(movie.backdrop?.tag).isEqualTo("aeb2c91ac1404b12dcc4e7662e9540ba")
            assertThat(movie.seriesId).isNull()
        }

    @Test
    fun `a Dolby Vision movie with an HDR10 base layer is Dolby Vision`() =
        runTest {
            enqueue("items_movie/hdr_dovi.json")

            val movie = (source.movies(server, 0, 500) as MqResult.Success).value.items.single()

            assertThat(movie.format).isEqualTo(VideoFormat("hevc", 3840, 1604, VideoRange.DolbyVision, 23_946))
        }

    @Test
    fun `R7 series query and mapping with the unwatched count`() =
        runTest {
            enqueue("items_series/page.json")

            val page = (source.series(server, 500, 500) as MqResult.Success).value

            assertThat(mockServer.takeRequest().query()).isEqualTo(videoQuery("Series"))
            assertThat(page.totalCount).isEqualTo(23)
            val series = page.items[0]
            assertThat(series.id).isEqualTo(MediaId(serverId, "e112b89c2a4897f8ada7c423b43160c1"))
            assertThat(series.year).isEqualTo(2011)
            assertThat(series.userData.unplayedCount).isEqualTo(94)
            assertThat(series.userData.playedPercentage).isEqualTo(0.0)
        }

    @Test
    fun `R7 seasons query and mapping`() =
        runTest {
            enqueue("items_season/page.json")

            val page = (source.seasons(server, 500, 500) as MqResult.Success).value

            assertThat(mockServer.takeRequest().query()).isEqualTo(videoQuery("Season"))
            assertThat(page.totalCount).isEqualTo(87)
            val season = page.items[0]
            assertThat(season.id).isEqualTo(MediaId(serverId, "0686452ae87d985786b571742d290aa3"))
            assertThat(season.seriesId).isEqualTo(MediaId(serverId, "bd5eb544025a3d8a2212c3788cc99f38"))
            assertThat(season.number).isEqualTo(1)
            assertThat(season.artwork?.tag).isEqualTo("7e4b49b6cdca9216d468d2097e2404f0")
            assertThat(season.userData.unplayedCount).isEqualTo(24)
        }

    @Test
    fun `R7 episodes query and mapping with their series and season`() =
        runTest {
            enqueue("items_episode/page.json")

            val page = (source.episodes(server, 500, 500) as MqResult.Success).value

            assertThat(mockServer.takeRequest().query()).isEqualTo(videoQuery("Episode"))
            assertThat(page.totalCount).isEqualTo(1520)
            val episode = page.items[0]
            assertThat(episode.kind).isEqualTo(VideoKind.Episode)
            assertThat(episode.id).isEqualTo(MediaId(serverId, "4c0c3b39be5963afb73afc7899977cb6"))
            assertThat(episode.seriesId).isEqualTo(MediaId(serverId, "d58bc759a3e39f106cef2201fb12ad0b"))
            assertThat(episode.seasonId).isEqualTo(MediaId(serverId, "ae50ab1619c379f23841ba2cb2b35c51"))
            assertThat(episode.seriesName).isEqualTo("Series 4")
            assertThat(episode.seasonNumber).isEqualTo(1)
            assertThat(episode.episodeNumber).isEqualTo(1)
        }

    @Test
    fun `an episode's subtitle file beside it is an external stream`() =
        runTest {
            enqueue("items_episode/with_external_subtitles.json")

            val episode = (source.episodes(server, 0, 500) as MqResult.Success).value.items.single()

            val subtitles = episode.streams.filter { it.type == StreamType.Subtitle }
            assertThat(subtitles.map { Triple(it.index, it.language, it.isExternal) })
                .containsExactly(Triple(0, null, true), Triple(3, "eng", false), Triple(4, "fra", false))
                .inOrder()
        }

    @Test
    fun `R26 Continue watching asks UserItems Resume for videos and reads their positions`() =
        runTest {
            mockServer.enqueue(
                MockResponse(body = javaClass.getResource("/jellyfin/user_items_resume/page.json")!!.readText()),
            )

            val resume = (source.resumeVideos(server, 20) as MqResult.Success).value

            val request = mockServer.takeRequest()
            assertThat(request.url.encodedPath).isEqualTo("/UserItems/Resume")
            assertThat(request.url.queryParameter("mediaTypes")).isEqualTo("Video")
            assertThat(request.url.queryParameter("includeItemTypes")).isEqualTo("Movie,Episode")
            assertThat(request.url.queryParameter("limit")).isEqualTo("20")
            val movie = resume.getValue(MediaId(serverId, "cc4b4fdb31a8af38cfcb812d79ad565c"))
            assertThat(movie.playbackPosition).isEqualTo(20.minutes)
            assertThat(movie.playedPercentage).isEqualTo(20.4)
            assertThat(resume.getValue(MediaId(serverId, "dd5c5fec42b9b049dfdc923e80be676d")).isFavorite).isTrue()
        }
}
