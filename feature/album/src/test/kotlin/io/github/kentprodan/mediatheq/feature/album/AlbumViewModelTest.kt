package io.github.kentprodan.mediatheq.feature.album

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection.PlayCall
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class AlbumViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val playback = FakePlaybackConnection()
    private val albumId = MediaId(ServerId("s"), "al1")
    private val album = Album(albumId, "Satelit", emptyList(), year = 2016)

    private fun track(
        id: String,
        disc: Int?,
        number: Int,
    ) = Track(
        MediaId(ServerId("s"), id),
        id,
        emptyList(),
        3.minutes,
        albumId = albumId,
        discNumber = disc,
        trackNumber = number,
    )

    private val disc1 = listOf(track("d1t1", 1, 1), track("d1t2", 1, 2))
    private val disc2 = listOf(track("d2t1", 2, 1))

    private fun TestScope.viewModel(): AlbumViewModel {
        val viewModel = AlbumViewModel(albumId, library, playback)
        backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    @Test
    fun `starts loading`() {
        assertThat(AlbumViewModel(albumId, library, playback).uiState.value).isEqualTo(AlbumUiState.Loading)
    }

    @Test
    fun `an album groups its tracks by disc and counts them`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1 + disc2

            val state = viewModel().uiState.value as AlbumUiState.Content

            assertThat(state.discs).containsExactly(Disc(1, disc1), Disc(2, disc2)).inOrder()
            assertThat(state.showDiscHeaders).isTrue()
            assertThat(state.trackCount).isEqualTo(3)
            assertThat(state.duration).isEqualTo(9.minutes)
        }

    @Test
    fun `a single disc, or tracks without disc numbers, has no disc headers`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = listOf(track("a", null, 1), track("b", 1, 2))

            val state = viewModel().uiState.value as AlbumUiState.Content

            assertThat(state.discs.map { it.number }).containsExactly(1)
            assertThat(state.showDiscHeaders).isFalse()
        }

    @Test
    fun `an album missing from the library is not found`() =
        runTest(mainDispatcherRule.testDispatcher) {
            assertThat(viewModel().uiState.value).isEqualTo(AlbumUiState.NotFound)
        }

    @Test
    fun `play queues the whole album from the first track`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1 + disc2
            val viewModel = viewModel()

            viewModel.onAction(AlbumAction.Play)

            assertThat(playback.plays).containsExactly(PlayCall(disc1 + disc2, 0, false))
        }

    @Test
    fun `shuffle queues the album in order and lets the player shuffle`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1 + disc2
            val viewModel = viewModel()

            viewModel.onAction(AlbumAction.Shuffle)

            assertThat(playback.plays).containsExactly(PlayCall(disc1 + disc2, 0, true))
        }

    @Test
    fun `tapping the first track of disc 2 starts after all of disc 1`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1 + disc2
            val viewModel = viewModel()

            viewModel.onAction(AlbumAction.PlayTrack(disc2.first().id))

            assertThat(playback.plays).containsExactly(PlayCall(disc1 + disc2, disc1.size, false))
        }

    @Test
    fun `an album without tracks plays nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            val viewModel = viewModel()

            viewModel.onAction(AlbumAction.Play)
            viewModel.onAction(AlbumAction.Shuffle)

            assertThat((viewModel.uiState.value as AlbumUiState.Content).trackCount).isEqualTo(0)
            assertThat(playback.plays).isEmpty()
        }

    @Test
    fun `the playing track follows the player`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1 + disc2
            val viewModel = viewModel()
            assertThat((viewModel.uiState.value as AlbumUiState.Content).currentTrackId).isNull()

            playback.state.value = PlayerUiModel(item = NowPlayingItem(disc2.first().id, "d2t1", ""))

            assertThat((viewModel.uiState.value as AlbumUiState.Content).currentTrackId).isEqualTo(disc2.first().id)

            playback.state.value = PlayerUiModel()

            assertThat((viewModel.uiState.value as AlbumUiState.Content).currentTrackId).isNull()
        }
}
