package io.github.kentprodan.mediatheq.provider.jellyfin

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.errorOrNull
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
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
            assertThat(request.query()).isEqualTo(paging + ("fields" to "SortName"))
            assertAuthenticated(request)
            assertThat(page.totalCount).isEqualTo(455)
            val artist = page.items.first()
            assertThat(artist.name).isEqualTo("2 Chainz")
            assertThat(artist.sortName).isEqualTo("0000000002 chainz")
            assertThat(artist.artwork?.tag).isEqualTo("5f594a17b901a35024f46c1130dc005b")
            assertThat(artist.artwork?.blurHash).isNotNull()
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
                        "fields" to "SortName,DateCreated,ChildCount",
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
                        "fields" to "SortName,DateCreated,MediaStreams",
                    ),
            )
            assertThat(page.totalCount).isEqualTo(2729)
            val track = page.items[1]
            assertThat(track.audioFormat?.codec).isEqualTo("mp3")
            assertThat(track.albumId).isEqualTo(MediaId(serverId, "8b7761b96bca5fb988f5b982e8ca02ad"))
            assertThat(track.trackNumber).isEqualTo(1)
            assertThat(track.discNumber).isEqualTo(1)
            assertThat(track.dateAdded).isEqualTo(Instant.parse("2026-07-11T13:55:28.287Z"))
            assertThat(track.normalizationGainDb).isEqualTo(-10.8)
            assertThat(track.artwork?.itemId).isEqualTo(track.id.itemId)
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
    fun `image urls are public, sized and versioned by tag`() {
        val url = source.imageUrl("http://nas:8096/jellyfin", Artwork("al1", "t a/g", null), 512)

        assertThat(
            url,
        ).isEqualTo("http://nas:8096/jellyfin/Items/al1/Images/Primary?tag=t%20a%2Fg&fillWidth=512&quality=90")
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
                        "MediaStreams":[{"Type":"Video","Codec":"mjpeg"},{"Type":"Audio","Codec":"EAC3","SampleRate":48000,
                        "Channels":6,"BitRate":768000,"AudioSpatialFormat":"DolbyAtmos"}]}],"TotalRecordCount":1}
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
}
