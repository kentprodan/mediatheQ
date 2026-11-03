package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaylistRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class ListTracksTest {
    private val library = FakeLibraryRepository()
    private val playlists = FakePlaylistRepository()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private fun track(
        value: String,
        album: String? = null,
    ) = Track(id(value), value, emptyList(), 3.minutes, albumId = album?.let(::id))

    @Test
    fun `albums give their tracks in list order, each album in play order`() =
        runTest {
            library.tracks.value = listOf(track("a1", "al1"), track("b1", "al2"), track("a2", "al1"))

            val tracks = library.tracksOfAlbums(listOf(id("al2"), id("al1")))

            assertThat(tracks.map { it.title }).containsExactly("b1", "a1", "a2").inOrder()
        }

    @Test
    fun `artists give their tracks once, at the first place`() =
        runTest {
            library.artistTracks.value =
                mapOf(id("ar1") to listOf(track("t1"), track("t2")), id("ar2") to listOf(track("t2"), track("t3")))

            val tracks = library.tracksOfArtists(listOf(id("ar1"), id("ar2")))

            assertThat(tracks.map { it.title }).containsExactly("t1", "t2", "t3").inOrder()
        }

    @Test
    fun `playlists give their tracks in their own order, repeats kept`() =
        runTest {
            playlists.items.value =
                mapOf(
                    id("p1") to listOf(PlaylistItem("e1", 0, track("t2")), PlaylistItem("e2", 1, track("t1"))),
                    id("p2") to listOf(PlaylistItem("e3", 0, track("t2"))),
                )

            val tracks = playlists.tracksOfPlaylists(listOf(id("p1"), id("p2")))

            assertThat(tracks.map { it.title }).containsExactly("t2", "t1", "t2").inOrder()
        }
}
