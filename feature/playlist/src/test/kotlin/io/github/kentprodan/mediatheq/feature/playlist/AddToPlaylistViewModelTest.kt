package io.github.kentprodan.mediatheq.feature.playlist

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaylistRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AddToPlaylistViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val repository = FakePlaylistRepository()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val playlists = listOf(Playlist(id("p1"), "Drum"), Playlist(id("p2"), "Seară"))

    private fun TestScope.viewModel(trackIds: List<MediaId> = listOf(id("t1"), id("t2"))): AddToPlaylistViewModel {
        library.playlists.value = playlists
        val viewModel = AddToPlaylistViewModel(trackIds, library, repository)
        backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    private val AddToPlaylistViewModel.content get() = uiState.value as AddToPlaylistUiState.Content

    @Test
    fun `starts loading`() {
        assertThat(AddToPlaylistViewModel(emptyList(), library, repository).uiState.value)
            .isEqualTo(AddToPlaylistUiState.Loading)
    }

    @Test
    fun `lists the playlists to pick from`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            assertThat(viewModel.content).isEqualTo(AddToPlaylistUiState.Content(playlists))
        }

    @Test
    fun `picking a playlist adds the tracks in order and closes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(AddToPlaylistAction.Pick(id("p2")))

            assertThat(repository.calls).containsExactly("add:p2:t1,t2")
            assertThat(viewModel.uiState.value).isEqualTo(AddToPlaylistUiState.Done)
        }

    @Test
    fun `a failed add stays open with the error and can be retried`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            repository.failure = MqError.Network

            viewModel.onAction(AddToPlaylistAction.Pick(id("p1")))
            assertThat(viewModel.content.error).isEqualTo(MqError.Network)
            assertThat(viewModel.content.busy).isFalse()

            repository.failure = null
            viewModel.onAction(AddToPlaylistAction.Pick(id("p1")))
            assertThat(viewModel.uiState.value).isEqualTo(AddToPlaylistUiState.Done)
        }

    @Test
    fun `new playlist creates it with the tracks`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.createdId = id("p9")
            val viewModel = viewModel()

            viewModel.onAction(AddToPlaylistAction.StartCreating)
            assertThat(viewModel.content.creating).isTrue()
            viewModel.onAction(AddToPlaylistAction.Create("  Nou "))

            assertThat(repository.calls).containsExactly("create:Nou:t1,t2")
            assertThat(viewModel.uiState.value).isEqualTo(AddToPlaylistUiState.Done)
        }

    @Test
    fun `without tracks it opens on naming a new playlist and ignores a blank name`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel(trackIds = emptyList())

            assertThat(viewModel.content.creating).isTrue()
            viewModel.onAction(AddToPlaylistAction.Create(" "))

            assertThat(repository.calls).isEmpty()
        }
}
