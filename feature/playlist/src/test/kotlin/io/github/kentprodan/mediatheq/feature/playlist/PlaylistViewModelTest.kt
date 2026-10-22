package io.github.kentprodan.mediatheq.feature.playlist

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.PlaylistItem
import io.github.kentprodan.mediatheq.core.data.PlaylistItem.Companion.PENDING_ENTRY_ID
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection.PlayCall
import io.github.kentprodan.mediatheq.core.testing.FakePlaylistRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class PlaylistViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePlaylistRepository()
    private val playback = FakePlaybackConnection()
    private val playlistId = MediaId(ServerId("s"), "p1")

    private fun track(id: String) = Track(MediaId(ServerId("s"), id), id, emptyList(), 3.minutes)

    // Position 1 is an entry whose track is not in the library: positions skip it.
    private val items =
        listOf(
            PlaylistItem("e1", 0, track("t1")),
            PlaylistItem("e2", 2, track("t2")),
            PlaylistItem("e3", 3, track("t3")),
        )
    private val tracks = items.map { it.track }

    private fun TestScope.viewModel(): PlaylistViewModel {
        repository.playlists.value = listOf(Playlist(playlistId, "Drum"))
        repository.items.value = mapOf(playlistId to items)
        val viewModel = PlaylistViewModel(playlistId, repository, playback)
        backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    private val PlaylistViewModel.content get() = uiState.value as PlaylistUiState.Content

    @Test
    fun `starts loading`() {
        assertThat(PlaylistViewModel(playlistId, repository, playback).uiState.value)
            .isEqualTo(PlaylistUiState.Loading)
    }

    @Test
    fun `a playlist shows its items and total duration`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val state = viewModel().content

            assertThat(state.items).isEqualTo(items)
            assertThat(state.duration).isEqualTo(9.minutes)
            assertThat(state.canEdit).isTrue()
        }

    @Test
    fun `a playlist missing from the library is not found`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            repository.playlists.value = emptyList()

            assertThat(viewModel.uiState.value).isEqualTo(PlaylistUiState.NotFound)
        }

    @Test
    fun `play, shuffle and play item start the playlist`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.Play)
            viewModel.onAction(PlaylistAction.Shuffle)
            viewModel.onAction(PlaylistAction.PlayItem(position = 2))

            assertThat(playback.plays)
                .containsExactly(PlayCall(tracks, 0, false), PlayCall(tracks, 0, true), PlayCall(tracks, 1, false))
                .inOrder()
        }

    @Test
    fun `play next and add to queue take one item`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.PlayNext(3))
            viewModel.onAction(PlaylistAction.AddToQueue(0))

            assertThat(playback.playedNext).containsExactly(listOf(tracks[2]))
            assertThat(playback.addedToQueue).containsExactly(listOf(tracks[0]))
        }

    @Test
    fun `selecting toggles items and removing the selection sends their entry ids`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.ToggleSelection(0))
            viewModel.onAction(PlaylistAction.ToggleSelection(3))
            viewModel.onAction(PlaylistAction.ToggleSelection(2))
            viewModel.onAction(PlaylistAction.ToggleSelection(2))
            assertThat(viewModel.content.selected).containsExactly(0, 3)

            viewModel.onAction(PlaylistAction.RemoveSelected)

            assertThat(repository.calls).containsExactly("remove:p1:e1,e3")
            assertThat(viewModel.content.selected).isEmpty()
            assertThat(viewModel.content.items.map { it.entryId }).containsExactly("e2")
        }

    @Test
    fun `clear selection leaves selection mode`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            viewModel.onAction(PlaylistAction.ToggleSelection(0))

            viewModel.onAction(PlaylistAction.ClearSelection)

            assertThat(viewModel.content.isSelecting).isFalse()
        }

    @Test
    fun `move passes both items to the repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.Move(position = 3, targetPosition = 0))

            assertThat(repository.calls).containsExactly("move:p1:e3:0")
            assertThat(viewModel.content.items.map { it.entryId }).containsExactly("e3", "e1", "e2").inOrder()
        }

    @Test
    fun `pending items block moves, removals and selection`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            repository.items.value = mapOf(playlistId to items + PlaylistItem(PENDING_ENTRY_ID, 4, track("t4")))

            viewModel.onAction(PlaylistAction.Move(3, 0))
            viewModel.onAction(PlaylistAction.Remove(0))
            viewModel.onAction(PlaylistAction.ToggleSelection(4))

            assertThat(viewModel.content.canEdit).isFalse()
            assertThat(viewModel.content.selected).isEmpty()
            assertThat(repository.calls).isEmpty()
        }

    @Test
    fun `a failed edit shows the error until it is dismissed`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            repository.failure = MqError.Network

            viewModel.onAction(PlaylistAction.Remove(2))

            assertThat(viewModel.content.error).isEqualTo(MqError.Network)
            assertThat(viewModel.content.items).isEqualTo(items)
            viewModel.onAction(PlaylistAction.DismissError)
            assertThat(viewModel.content.error).isNull()
        }

    @Test
    fun `rename trims the name and skips an unchanged or blank one`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.Rename("  "))
            viewModel.onAction(PlaylistAction.Rename("Drum"))
            viewModel.onAction(PlaylistAction.Rename(" Seară "))

            assertThat(repository.calls).containsExactly("rename:p1:Seară")
            assertThat(viewModel.content.playlist.name).isEqualTo("Seară")
        }

    @Test
    fun `a deleted playlist closes the screen`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.Delete)

            assertThat(repository.calls).containsExactly("delete:p1")
            assertThat(viewModel.uiState.value).isEqualTo(PlaylistUiState.Deleted)
        }

    @Test
    fun `a failed delete keeps the screen and shows the error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            repository.failure = MqError.Forbidden

            viewModel.onAction(PlaylistAction.Delete)

            assertThat(viewModel.content.error).isEqualTo(MqError.Forbidden)
        }
}
