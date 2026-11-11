package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.testing.FakeAudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaylistRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class FavoriteDownloadsTest {
    private val server = ServerId("s")

    private fun id(
        value: String,
        on: ServerId = server,
    ) = MediaId(on, value)

    private fun track(
        value: String,
        album: String = "A",
        favorite: Boolean = false,
        on: ServerId = server,
    ) = Track(
        id(value, on),
        value,
        emptyList(),
        3.minutes,
        albumId = id(album, on),
        userData = UserData(isFavorite = favorite),
    )

    private val t1 = track("t1", favorite = true)
    private val t2 = track("t2")
    private val t3 = track("t3", album = "B")
    private val albumB = DownloadOrigin.Album(id("B"), auto = true)
    private val playlistP = DownloadOrigin.Playlist(id("P"), auto = true)

    private fun favorites(
        tracks: List<Track> = listOf(t1),
        albums: Map<MediaId, List<Track>> = mapOf(id("B") to listOf(t3)),
        playlists: Map<MediaId, List<Track>> = mapOf(id("P") to listOf(t2)),
    ) = Favorites(tracks, albums, playlists)

    private fun plan(
        mode: AutoDownloads,
        favorites: Favorites = favorites(),
        origins: Map<MediaId, Set<DownloadOrigin>> = emptyMap(),
        remove: Boolean = true,
    ) = favoriteDownloadPlan(mode, remove, server, favorites, origins)

    @Test
    fun `each option downloads its favorites with automatic origins`() {
        assertThat(plan(AutoDownloads.Disabled)).isEqualTo(FavoriteDownloadPlan())
        assertThat(plan(AutoDownloads.Tracks).download).containsExactly(DownloadOrigin.Favorite, listOf(t1))
        assertThat(plan(AutoDownloads.TracksAndAlbums).download)
            .containsExactly(DownloadOrigin.Favorite, listOf(t1), albumB, listOf(t3))
        assertThat(plan(AutoDownloads.TracksAlbumsAndPlaylists).download)
            .containsExactly(DownloadOrigin.Favorite, listOf(t1), albumB, listOf(t3), playlistP, listOf(t2))
    }

    @Test
    fun `a track with its automatic origin is never asked again, a failed one included`() {
        val origins = mapOf(t1.id to setOf<DownloadOrigin>(DownloadOrigin.Favorite), t3.id to setOf(albumB))

        assertThat(plan(AutoDownloads.TracksAndAlbums, origins = origins).download).isEmpty()
    }

    @Test
    fun `a manual download gets the automatic origin too, so unfavoriting later keeps it`() {
        val origins = mapOf(t1.id to setOf<DownloadOrigin>(DownloadOrigin.Single))

        assertThat(plan(AutoDownloads.Tracks, origins = origins).download)
            .containsExactly(DownloadOrigin.Favorite, listOf(t1))
    }

    @Test
    fun `unfavorited items lose only their automatic origins`() {
        val manualAlbum = DownloadOrigin.Album(id("C"))
        val origins =
            mapOf(
                id("old") to setOf(DownloadOrigin.Favorite, DownloadOrigin.Single),
                id("x") to setOf(DownloadOrigin.Album(id("Z"), auto = true), manualAlbum),
                id("y") to setOf(DownloadOrigin.Playlist(id("Q"), auto = true)),
                t3.id to setOf(albumB),
            )

        val plan = plan(AutoDownloads.TracksAlbumsAndPlaylists, origins = origins)

        assertThat(plan.removeFavorites).containsExactly(id("old"))
        assertThat(plan.removeCollections)
            .containsExactly(DownloadOrigin.Album(id("Z"), auto = true), DownloadOrigin.Playlist(id("Q"), auto = true))
    }

    @Test
    fun `nothing goes with the switch off, while disabled, or on another server`() {
        val otherServer = ServerId("other")
        val origins =
            mapOf(
                id("old") to setOf<DownloadOrigin>(DownloadOrigin.Favorite),
                id("far", otherServer) to
                    setOf(DownloadOrigin.Favorite, DownloadOrigin.Album(id("Z", otherServer), true)),
            )

        assertThat(plan(AutoDownloads.Tracks, origins = origins, remove = false).removeFavorites).isEmpty()
        assertThat(plan(AutoDownloads.Disabled, origins = origins)).isEqualTo(FavoriteDownloadPlan())
        val plan = plan(AutoDownloads.TracksAndAlbums, origins = origins)
        assertThat(plan.removeFavorites).containsExactly(id("old"))
        assertThat(plan.removeCollections).isEmpty()
    }

    @Test
    fun `a lower option keeps the albums and playlists that are still favorites`() {
        val origins = mapOf(t3.id to setOf<DownloadOrigin>(albumB), t2.id to setOf(playlistP))

        val plan = plan(AutoDownloads.Tracks, origins = origins)

        assertThat(plan.removeCollections).isEmpty()
    }

    @Test
    fun `a favorite playlist whose items are not in the library yet keeps its tracks`() {
        val origins = mapOf(t2.id to setOf<DownloadOrigin>(playlistP))

        val plan =
            plan(AutoDownloads.TracksAlbumsAndPlaylists, favorites(playlists = mapOf(id("P") to emptyList())), origins)

        assertThat(plan.removeCollections).isEmpty()
        assertThat(plan.download.keys).doesNotContain(playlistP)
    }

    // The runner, with fakes.

    private val audio = FakeAudioPreferencesRepository()
    private val library = FakeLibraryRepository()
    private val playlists = FakePlaylistRepository()
    private val downloads = FakeDownloadRepository()

    // The runner waits for favorites to settle: background work moves with the clock only.
    private fun TestScope.settle() {
        advanceTimeBy(3_000)
        runCurrent()
    }

    private fun TestScope.runner() =
        FavoriteDownloads(audio, library, playlists, downloads, backgroundScope, StandardTestDispatcher(testScheduler))

    @Test
    fun `favoriting a track (as a live sync writes it) downloads it, unfavoriting removes it`() =
        runTest {
            audio.setAutoDownloads(AutoDownloads.Tracks)
            library.tracks.value = listOf(t2, t3)
            runner().start()
            settle()
            assertThat(downloads.downloads.value).isEmpty()

            library.tracks.value = listOf(t2.copy(userData = UserData(isFavorite = true)), t3)
            settle()
            assertThat(
                downloads.downloads.value
                    .single()
                    .origins,
            ).containsExactly(DownloadOrigin.Favorite)

            library.tracks.value = listOf(t2, t3)
            settle()
            assertThat(downloads.downloads.value).isEmpty()
        }

    @Test
    fun `a favorite album and a favorite playlist download their tracks`() =
        runTest {
            audio.setAutoDownloads(AutoDownloads.TracksAlbumsAndPlaylists)
            library.tracks.value = listOf(t2, t3)
            library.albums.value = listOf(Album(id("B"), "B", emptyList(), userData = UserData(isFavorite = true)))
            library.playlists.value = listOf(Playlist(id("P"), "P", userData = UserData(isFavorite = true)))
            playlists.items.value = mapOf(id("P") to listOf(PlaylistItem("e1", 0, t2)))
            runner().start()
            settle()

            assertThat(downloads.downloads.value.associate { it.track.id to it.origins })
                .containsExactly(t3.id, setOf(albumB), t2.id, setOf(playlistP))
        }

    @Test
    fun `disabled keeps the automatic downloads, and an empty library (signed out) does nothing`() =
        runTest {
            downloads.downloads.value =
                listOf(TrackDownload(t2, DownloadState.Completed, origins = setOf(DownloadOrigin.Favorite)))
            audio.setAutoDownloads(AutoDownloads.Tracks)
            runner().start()
            settle()
            assertThat(downloads.downloads.value).hasSize(1)

            library.tracks.value = listOf(t3)
            audio.setAutoDownloads(AutoDownloads.Disabled)
            settle()
            assertThat(downloads.downloads.value).hasSize(1)
        }
}
